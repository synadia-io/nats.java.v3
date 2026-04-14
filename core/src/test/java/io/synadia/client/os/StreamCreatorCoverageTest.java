package io.synadia.client.os;

import io.synadia.client.jsapi.*;
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
        Set<String> creatorGetters = collectPublicGetters(creatorClass);
        Set<String> configGetters = collectPublicGetters(configClass);

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

    private static Set<String> collectPublicGetters(Class<?> clazz) {
        Set<String> getters = new TreeSet<>();
        for (Method m : clazz.getMethods()) {
            String sig = m.getName() + "()";
            if (Modifier.isPublic(m.getModifiers())
                && m.getParameterCount() == 0
                && (m.getName().startsWith("get") || m.getName().startsWith("is") || m.getName().startsWith("has"))
                && m.getReturnType() != void.class
                && !IGNORED.contains(sig))
            {
                getters.add(sig);
            }
        }
        return getters;
    }
}
