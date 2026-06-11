package io.synadia.client.api;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Reflection-based tests that ensure Creator and Configuration/read-only
 * classes have the same set of public getters.
 */
class StreamCreatorCoverageTest {

    // Methods to ignore — inherited from Object, not real getters
    private static final Set<String> IGNORED = Set.of("hashCode()", "getClass()");

    @Test
    void streamCreatorAndConfigurationHaveSameGetters() {
        verifyGetterParity(StreamCreator.class, StreamConfiguration.class);
    }

    @Test
    void mirrorCreatorAndMirrorHaveSameGetters() {
        verifyGetterParity(MirrorCreator.class, Mirror.class);
    }

    @Test
    void sourceCreatorAndSourceHaveSameGetters() {
        verifyGetterParity(SourceCreator.class, Source.class);
    }

    @Test
    void subjectTransformCreatorAndSubjectTransformHaveSameGetters() {
        verifyGetterParity(SubjectTransformCreator.class, SubjectTransform.class);
    }

    @Test
    void externalCreatorAndExternalHaveSameGetters() {
        verifyGetterParity(ExternalCreator.class, External.class);
    }

    @Test
    void consumerLimitsCreatorAndConsumerLimitsHaveSameGetters() {
        verifyGetterParity(ConsumerLimitsCreator.class, ConsumerLimits.class);
    }

    @Test
    void placementCreatorAndPlacementHaveSameGetters() {
        verifyGetterParity(PlacementCreator.class, Placement.class);
    }

    @Test
    void republishCreatorAndRepublishHaveSameGetters() {
        verifyGetterParity(RepublishCreator.class, Republish.class);
    }

    private static void verifyGetterParity(Class<?> creatorClass, Class<?> configClass) {
        Set<String> creatorGetters = normalize(collectPublicGetters(creatorClass));
        Set<String> configGetters = normalize(collectPublicGetters(configClass));

        List<String> failures = new ArrayList<>();

        for (String sig : creatorGetters) {
            if (!configGetters.contains(sig)) {
                failures.add(configClass.getSimpleName() + " is missing getter: " + sig);
            }
        }

        for (String sig : configGetters) {
            if (!creatorGetters.contains(sig)) {
                failures.add(creatorClass.getSimpleName() + " is missing getter: " + sig);
            }
        }

        if (!failures.isEmpty()) {
            fail(creatorClass.getSimpleName() + "/" + configClass.getSimpleName()
                + " getter mismatch:\n  " + String.join("\n  ", failures));
        }
    }

    // Creator-side getters intentionally end in "Creator"/"Creators" to indicate
    // they return mutable Creator types; strip that suffix so parity comparison
    // matches the read-only side (e.g. getPlacementCreator <-> getPlacement,
    // getSourceCreators <-> getSources).
    private static Set<String> normalize(Set<String> sigs) {
        Set<String> out = new TreeSet<>();
        for (String sig : sigs) {
            String name = sig.substring(0, sig.length() - 2);
            if (name.endsWith("Creators")) {
                name = name.substring(0, name.length() - "Creators".length()) + "s";
            }
            else if (name.endsWith("Creator")) {
                name = name.substring(0, name.length() - "Creator".length());
            }
            out.add(name + "()");
        }
        return out;
    }

    private static Set<String> collectPublicGetters(Class<?> clazz) {
        Set<String> getters = new TreeSet<>();
        for (Method m : clazz.getMethods()) {
            String sig = m.getName() + "()";
            if (Modifier.isPublic(m.getModifiers())
                && m.getParameterCount() == 0
                && (m.getName().startsWith("get") || m.getName().startsWith("is") || m.getName().startsWith("has"))
                && !m.getName().endsWith("AsBigInteger") // read-only unsigned companion; no Creator counterpart
                && m.getReturnType() != void.class
                && !IGNORED.contains(sig))
            {
                getters.add(sig);
            }
        }
        return getters;
    }
}
