package com.oolshik.backend.config.bootstrap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;

public class DotenvEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    static final String PROPERTY_SOURCE_NAME = "oolshikDotenv";

    private static final Logger log = LoggerFactory.getLogger(DotenvEnvironmentPostProcessor.class);

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (isTestRuntime()) {
            return;
        }
        if (environment.getProperty("app.secrets.aws.enabled", Boolean.class, false)) {
            return;
        }

        Path dotenvPath = Path.of(".env").toAbsolutePath().normalize();
        if (!Files.isRegularFile(dotenvPath) || !Files.isReadable(dotenvPath)) {
            return;
        }

        try {
            BootstrapPropertySupport.addAfterSystemEnvironment(
                    environment,
                    PROPERTY_SOURCE_NAME,
                    BootstrapPropertySupport.parseDotenv(dotenvPath)
            );
            log.info("Loaded local configuration from {}", dotenvPath);
        } catch (IOException | IllegalStateException e) {
            throw new IllegalStateException("Failed to load local .env from " + dotenvPath, e);
        }
    }

    @Override
    public int getOrder() {
        return ConfigDataEnvironmentPostProcessor.ORDER - 1;
    }

    private boolean isTestRuntime() {
        return isTestRuntime(System.getProperty("sun.java.command", ""), System::getProperty);
    }

    static boolean isTestRuntime(String sunCommand, Function<String, String> propertyLookup) {
        return hasAnyProperty(propertyLookup,
                "surefire.real.class.path",
                "surefire.test.class.path",
                "org.gradle.test.worker",
                "idea.test.cyclic.buffer.size"
        ) || containsAny(sunCommand,
                "org.apache.maven.surefire",
                "junit-platform-console",
                "com.intellij.rt.junit.JUnitStarter",
                "worker.org.gradle.process.internal.worker.GradleWorkerMain",
                "org.gradle.launcher.GradleMain",
                "org.eclipse.jdt.internal.junit.runner.RemoteTestRunner"
        );
    }

    private static boolean hasAnyProperty(Function<String, String> propertyLookup, String... keys) {
        for (String key : keys) {
            if (propertyLookup.apply(key) != null) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) {
                return true;
            }
        }
        return false;
    }
}
