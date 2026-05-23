package com.oolshik.backend.config.bootstrap;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DotenvEnvironmentPostProcessorTest {

    @Test
    void detectsSurefireGradleAndIdeTestMarkers() {
        assertThat(DotenvEnvironmentPostProcessor.isTestRuntime(
                "",
                Map.of("surefire.real.class.path", "x")::get
        )).isTrue();

        assertThat(DotenvEnvironmentPostProcessor.isTestRuntime(
                "",
                Map.of("org.gradle.test.worker", "1")::get
        )).isTrue();

        assertThat(DotenvEnvironmentPostProcessor.isTestRuntime(
                "com.intellij.rt.junit.JUnitStarter -ideVersion5",
                key -> null
        )).isTrue();
    }

    @Test
    void ignoresNormalApplicationStartup() {
        assertThat(DotenvEnvironmentPostProcessor.isTestRuntime(
                "com.oolshik.backend.OolshikApplication",
                key -> null
        )).isFalse();
    }
}
