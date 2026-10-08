// Copyright (c) 2026 Synadia Communications Inc. All Rights Reserved.

package io.synadia.examples;

import io.nats.NatsServerRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.launch.Framework;
import org.osgi.framework.launch.FrameworkFactory;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Installs the built core, jetstream, kv, os and service jars in an OSGi framework (Apache Felix),
 * together with their dependencies as bundles: jspecify, jnats-json, commons-codec, nkeys core,
 * the nkeys lts provider and its Bouncy Castle.
 * jetstream is a fragment of core, so its classes in the packages it shares with core load in core's class loader
 * and may use core's package-private and protected members at runtime.
 * The nkeys providers are fragments of nkeys core for the same reason.
 */
public class OsgiBundleTests {

    @Test
    public void testBundlesResolveAndJetStreamRunsInOsgi(@TempDir Path storage) throws Exception {
        Map<String, String> config = new HashMap<>();
        config.put(Constants.FRAMEWORK_STORAGE, storage.toString());
        config.put(Constants.FRAMEWORK_STORAGE_CLEAN, Constants.FRAMEWORK_STORAGE_CLEAN_ONFIRSTINIT);

        Framework framework = ServiceLoader.load(FrameworkFactory.class).iterator().next().newFramework(config);
        framework.start();
        try {
            BundleContext context = framework.getBundleContext();
            List<Bundle> dependencies = install(context, "osgi.dependencies");
            List<Bundle> bundles = install(context, "osgi.bundles");
            assertEquals(6, dependencies.size(), "dependency bundles");

            List<Bundle> all = new ArrayList<>(dependencies);
            all.addAll(bundles);
            for (Bundle b : all) {
                if (b.getHeaders().get(Constants.FRAGMENT_HOST) == null) {
                    b.start();
                    assertEquals(Bundle.ACTIVE, b.getState(), b.getSymbolicName());
                }
            }
            for (Bundle b : all) {
                if (b.getHeaders().get(Constants.FRAGMENT_HOST) != null) {
                    assertEquals(Bundle.RESOLVED, b.getState(), b.getSymbolicName() + " fragment attached");
                }
            }

            Bundle core = bundles.get(0);
            Bundle kv = bundles.get(2);

            // a jetstream class and a core class in the same package share core's class loader
            ClassLoader coreLoader = core.loadClass("io.synadia.client.impl.NatsConnection").getClassLoader();
            assertSame(coreLoader, core.loadClass("io.synadia.client.impl.MessageManager").getClassLoader());
            assertSame(coreLoader, kv.loadClass("io.synadia.client.impl.JetStream").getClassLoader());
            assertTrue(kv.loadClass("io.synadia.client.kv.KeyValue").getDeclaredMethods().length > 0);

            // core's json and nkey packages are wired to their own bundles
            assertEquals("jnats-json", FrameworkUtil.getBundle(core.loadClass("io.nats.json.JsonValue")).getSymbolicName());
            Class<?> providerClass = core.loadClass("io.nats.nkey.NKeyProvider");
            assertEquals("io.nats.nkeys.core", FrameworkUtil.getBundle(providerClass).getSymbolicName());

            // NKeyProvider loads the lts provider, a fragment, by name through nkeys core's class loader
            Object provider = providerClass.getMethod("getProvider", String.class).invoke(null, "io.nats.nkey.LtsNKeyProvider");
            Object nkey = providerClass.getMethod("createUser").invoke(provider);
            char[] publicKey = (char[]) nkey.getClass().getMethod("getPublicKey").invoke(nkey);
            assertEquals('U', publicKey[0]);

            // JetStreamManagement's constructor calls protected NatsConnection members across the two jars
            try (NatsServerRunner server = new NatsServerRunner(false, true)) {
                Class<?> nats = kv.loadClass("io.synadia.client.Nats");
                Class<?> connClass = kv.loadClass("io.synadia.client.impl.NatsConnection");
                Object conn = nats.getMethod("connect", String.class).invoke(null, server.getNatsLocalhostUri());
                try {
                    Class<?> jsmClass = kv.loadClass("io.synadia.client.impl.JetStreamManagement");
                    Object jsm = jsmClass.getMethod("instance", connClass).invoke(null, conn);
                    assertNotNull(jsmClass.getMethod("getAccountStatistics").invoke(jsm));
                }
                finally {
                    connClass.getMethod("close").invoke(conn);
                }
            }
        }
        finally {
            framework.stop();
            framework.waitForStop(10_000);
        }
    }

    private static List<Bundle> install(BundleContext context, String property) throws Exception {
        List<Bundle> bundles = new ArrayList<>();
        for (String path : System.getProperty(property).split(File.pathSeparator)) {
            bundles.add(context.installBundle(new File(path).toURI().toString()));
        }
        return bundles;
    }
}
