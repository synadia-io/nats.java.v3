package io.synadia.client.api;

import io.synadia.client.js.consumer.*;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Reflection-based test that ensures every protected void _* setter in ConsumerFields
 * has a corresponding public method in each leaf subclass that exposes setters.
 * <p>
 * When a new field is added to ConsumerFields, this test will fail if the
 * corresponding public setter is not added to each subclass (or explicitly excluded).
 */
class FieldsSetterCoverageTest {

    /**
     * Each entry: leaf subclass to check -> set of method names (without _) that are intentionally excluded.
     */
    private static final Map<Class<? extends ConsumerFields>, Set<String>> SUBCLASS_EXCLUSIONS = Map.of(
        // PullConsumerCreator: everything
        PullConsumerCreator.class, Set.of(),

        // PushConsumerCreator: no deliverSubject (set in constructor), no pull-specific, no priority
        PushConsumerCreator.class, Set.of(
            "deliverSubject",
            "maxExpires", "maxPullWaiting", "maxBatch", "maxBytes",
            "priorityGroups", "priorityPolicy", "priorityTimeout"
        ),

        // PullEphemeralConsumerCreator: no durable, no deliverSubject, no deliverGroup
        PullEphemeralConsumerCreator.class, Set.of(
            "durable", "deliverSubject", "deliverGroup"
        ),

        // PushEphemeralConsumerCreator: no durable, no deliverSubject (set in constructor), no pull-specific, no priority
        PushEphemeralConsumerCreator.class, Set.of(
            "durable", "deliverSubject",
            "maxExpires", "maxPullWaiting", "maxBatch", "maxBytes",
            "priorityGroups", "priorityPolicy", "priorityTimeout"
        ),

        // PullOrderedConsumerCreator: AbstractConsumerCreator setters + pull-specific + priority
        PullOrderedConsumerCreator.class, Set.of(
            "durable", "name", "deliverSubject", "deliverGroup",
            "ackPolicy", "ackWait", "maxDeliver", "maxAckPending",
            "flowControl", "numReplicas", "pauseUntil", "memStorage",
            "backoff"
        ),

        // PushOrderedConsumerCreator: AbstractConsumerCreator setters + deliverGroup
        PushOrderedConsumerCreator.class, Set.of(
            "durable", "name", "deliverSubject",
            "ackPolicy", "ackWait", "maxDeliver", "maxAckPending",
            "flowControl", "maxExpires", "maxPullWaiting", "maxBatch", "maxBytes",
            "numReplicas", "pauseUntil", "memStorage",
            "backoff", "priorityGroups", "priorityPolicy", "priorityTimeout"
        )
    );

    @Test
    void allProtectedSettersExposedInSubclasses() {
        // Collect all protected void _* methods from ConsumerFields
        List<Method> baseMethods = new ArrayList<>();
        for (Method m : ConsumerFields.class.getDeclaredMethods()) {
            if (Modifier.isProtected(m.getModifiers())
                && m.getReturnType() == void.class
                && m.getName().startsWith("_")
                && !m.getName().startsWith("__")) // skip private helpers like __backoff
            {
                baseMethods.add(m);
            }
        }

        if (baseMethods.isEmpty()) {
            fail("Found no protected void _* methods in ConsumerFields — test setup is broken");
        }

        List<String> failures = new ArrayList<>();

        for (var entry : SUBCLASS_EXCLUSIONS.entrySet()) {
            Class<? extends ConsumerFields> subclass = entry.getKey();
            Set<String> exclusions = entry.getValue();

            for (Method baseMethod : baseMethods) {
                String publicName = baseMethod.getName().substring(1); // strip leading _

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
            fail("ConsumerFields setter coverage gaps:\n  " + String.join("\n  ", failures));
        }
    }

    private static String paramTypesString(Method m) {
        StringJoiner sj = new StringJoiner(", ");
        for (Class<?> p : m.getParameterTypes()) {
            sj.add(p.getSimpleName());
        }
        return sj.toString();
    }
}
