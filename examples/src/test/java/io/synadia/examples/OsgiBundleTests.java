// Copyright (c) 2026 Synadia Communications Inc. All Rights Reserved.

package io.synadia.examples;

import io.nats.NatsServerRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
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
 * Installs the built core, jetstream, kv, os and service jars in an OSGi framework (Apache Felix).
 * jetstream is a fragment of core, so its classes in the packages it shares with core load in core's class loader
 * and may use core's package-private and protected members at runtime.
 * The dependencies that are not OSGi bundles with exports (jnats-json, nkeys) are provided by the system bundle.
 */
public class OsgiBundleTests {

    @Test
    public void testBundlesResolveAndJetStreamRunsInOsgi(@TempDir Path storage) throws Exception {
        Map<String, String> config = new HashMap<>();
        config.put(Constants.FRAMEWORK_STORAGE, storage.toString());
        config.put(Constants.FRAMEWORK_STORAGE_CLEAN, Constants.FRAMEWORK_STORAGE_CLEAN_ONFIRSTINIT);
        config.put(Constants.FRAMEWORK_SYSTEMPACKAGES_EXTRA, "io.nats.json,io.nats.nkey,org.jspecify.annotations;version=1.0.0");

        Framework framework = ServiceLoader.load(FrameworkFactory.class).iterator().next().newFramework(config);
        framework.start();
        try {
            BundleContext context = framework.getBundleContext();
            List<Bundle> bundles = new ArrayList<>();
            for (String path : System.getProperty("osgi.bundles").split(File.pathSeparator)) {
                bundles.add(context.installBundle(new File(path).toURI().toString()));
            }
            Bundle core = bundles.get(0);
            Bundle jetstream = bundles.get(1);
            Bundle kv = bundles.get(2);
            for (Bundle b : bundles) {
                if (b != jetstream) {
                    b.start();
                    assertEquals(Bundle.ACTIVE, b.getState(), b.getSymbolicName());
                }
            }
            assertEquals(Bundle.RESOLVED, jetstream.getState(), "fragment attached to core");

            // a jetstream class and a core class in the same package share core's class loader
            ClassLoader coreLoader = core.loadClass("io.synadia.client.impl.NatsConnection").getClassLoader();
            assertSame(coreLoader, core.loadClass("io.synadia.client.impl.MessageManager").getClassLoader());
            assertSame(coreLoader, kv.loadClass("io.synadia.client.impl.JetStream").getClassLoader());
            assertTrue(kv.loadClass("io.synadia.client.kv.KeyValue").getDeclaredMethods().length > 0);

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
}
