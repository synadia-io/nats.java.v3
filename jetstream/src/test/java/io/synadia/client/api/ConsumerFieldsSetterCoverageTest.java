package io.synadia.client.api;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Reflection-based tests that ensure:
 * 1. Every protected void _* setter in ConsumerCreator has a corresponding public method
 *    in each leaf subclass (or is explicitly excluded).
 * 2. ConsumerCreator and ConsumerConfiguration have the same set of getters.
 */
class ConsumerFieldsSetterCoverageTest {

    // ----------------------------------------------------------------------------------------------------
    // Setter coverage: every _setter in ConsumerCreator must be public in leaf classes (or excluded)
    // ----------------------------------------------------------------------------------------------------

    private static final Map<Class<? extends ConsumerCreator<?>>, Set<String>> SUBCLASS_EXCLUSIONS = Map.of(
        // PullConsumerCreator: no push-specific
        PullConsumerCreator.class, Set.of(
            "deliverSubject", "deliverGroup"
        ),

        // PushConsumerCreator: no pull-specific, no priority
        PushConsumerCreator.class, Set.of(
            "maxExpires", "maxPullWaiting", "maxBatch", "maxBytes",
            "priorityGroups", "priorityPolicy", "priorityTimeout"
        ),

        // PullOrderedConsumerCreator: ConsumerCreator setters + pull-specific + priority
        PullOrderedConsumerCreator.class, Set.of(
            "durable", "name", "deliverSubject", "deliverGroup",
            "ackPolicy", "ackWait", "maxDeliver", "maxAckPending",
            "flowControl", "numReplicas", "pauseUntil", "memStorage",
            "backoff"
        ),

        // PushOrderedConsumerCreator: ConsumerCreator setters + push-specific (deliverSubject only)
        PushOrderedConsumerCreator.class, Set.of(
            "durable", "name", "deliverGroup",
            "ackPolicy", "ackWait", "maxDeliver", "maxAckPending",
            "flowControl", "maxExpires", "maxPullWaiting", "maxBatch", "maxBytes",
            "numReplicas", "pauseUntil", "memStorage",
            "backoff", "priorityGroups", "priorityPolicy", "priorityTimeout"
        )
    );

    @Test
    void allProtectedSettersExposedInSubclasses() {
        List<Method> baseMethods = new ArrayList<>();
        for (Method m : ConsumerCreator.class.getDeclaredMethods()) {
            if (Modifier.isProtected(m.getModifiers())
                && m.getReturnType() == void.class
                && m.getName().startsWith("_")
                && !m.getName().startsWith("__"))
            {
                baseMethods.add(m);
            }
        }

        if (baseMethods.isEmpty()) {
            fail("Found no protected void _* methods in ConsumerCreator — test setup is broken");
        }

        List<String> failures = new ArrayList<>();

        for (var entry : SUBCLASS_EXCLUSIONS.entrySet()) {
            Class<? extends ConsumerCreator<?>> subclass = entry.getKey();
            Set<String> exclusions = entry.getValue();

            for (Method baseMethod : baseMethods) {
                String publicName = baseMethod.getName().substring(1);

                if (exclusions.contains(publicName)) {
                    continue;
                }

                try {
                    Method found = subclass.getMethod(publicName, baseMethod.getParameterTypes());
                    if (!Modifier.isPublic(found.getModifiers())) {
                        failures.add(subclass.getSimpleName() + " has " + publicName
                            + "(" + paramTypesString(baseMethod) + ") but it is not public");
                    }
                }
                catch (NoSuchMethodException e) {
                    failures.add(subclass.getSimpleName() + " is missing public "
                        + publicName + "(" + paramTypesString(baseMethod) + ")");
                }
            }
        }

        if (!failures.isEmpty()) {
            fail("ConsumerCreator setter coverage gaps:\n  " + String.join("\n  ", failures));
        }
    }

    // ----------------------------------------------------------------------------------------------------
    // Getter coverage: ConsumerCreator and ConsumerConfiguration must have the same getters
    // ----------------------------------------------------------------------------------------------------

    /**
     * Getters that legitimately exist on ConsumerCreator only — they capture
     * builder-side state with no counterpart in the server response.
     */
    private static final Set<String> CREATOR_ONLY_GETTERS = Set.of(
        "getStream()",  // builder is created against a specific stream name
        "isPush()"      // push/pull flag is a builder-time choice
    );

    @Test
    void consumerCreatorAndConfigurationHaveSameGetters() {
        Set<String> creatorGetters = collectPublicGetters(ConsumerCreator.class);
        Set<String> configGetters = collectPublicGetters(ConsumerConfiguration.class);

        List<String> failures = new ArrayList<>();

        for (String sig : creatorGetters) {
            if (CREATOR_ONLY_GETTERS.contains(sig)) {
                continue;
            }
            if (!configGetters.contains(sig)) {
                failures.add("ConsumerConfiguration is missing getter: " + sig);
            }
        }

        for (String sig : configGetters) {
            if (!creatorGetters.contains(sig)) {
                failures.add("ConsumerCreator is missing getter: " + sig);
            }
        }

        if (!failures.isEmpty()) {
            fail("ConsumerCreator/ConsumerConfiguration getter mismatch:\n  " + String.join("\n  ", failures));
        }
    }

    // ----------------------------------------------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------------------------------------------

    private static Set<String> collectPublicGetters(Class<?> clazz) {
        Set<String> getters = new TreeSet<>();
        for (Method m : clazz.getMethods()) {
            if (m.getParameterCount() == 0
                && !Modifier.isStatic(m.getModifiers())
                && (m.getName().startsWith("get") || m.getName().startsWith("is") || m.getName().startsWith("has"))
                && m.getReturnType() != void.class
                && m.getDeclaringClass() != Object.class)
            {
                getters.add(m.getName() + "()");
            }
        }
        return getters;
    }

    private static String paramTypesString(Method m) {
        StringJoiner sj = new StringJoiner(", ");
        for (Class<?> p : m.getParameterTypes()) {
            sj.add(p.getSimpleName());
        }
        return sj.toString();
    }
}
