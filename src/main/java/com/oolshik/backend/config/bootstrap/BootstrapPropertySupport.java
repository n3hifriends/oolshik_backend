package com.oolshik.backend.config.bootstrap;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

final class BootstrapPropertySupport {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Pattern VALID_DOTENV_KEY = Pattern.compile("^[A-Za-z_][A-Za-z0-9_.-]*$");

    private BootstrapPropertySupport() {
    }

    static Map<String, Object> parseDotenv(Path path) throws IOException {
        Map<String, Object> properties = new LinkedHashMap<>();
        int lineNumber = 0;
        for (String rawLine : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            lineNumber++;
            String line = rawLine.trim();
            if (!StringUtils.hasText(line) || line.startsWith("#")) {
                continue;
            }
            if (line.startsWith("export ")) {
                line = line.substring(7).trim();
            }

            int separator = line.indexOf('=');
            if (separator <= 0) {
                continue;
            }

            String key = line.substring(0, separator).trim();
            if (!VALID_DOTENV_KEY.matcher(key).matches()) {
                throw new IllegalStateException("Invalid .env key on line " + lineNumber + ": " + key);
            }

            String value = normalizeDotenvValue(line.substring(separator + 1).trim());
            putWithAliases(properties, key, value);
        }
        return properties;
    }

    static Map<String, Object> parseSecretPayload(String payload) {
        if (!StringUtils.hasText(payload)) {
            throw new IllegalStateException("AWS secret payload is empty");
        }

        try {
            JsonNode root = OBJECT_MAPPER.readTree(payload);
            if (!root.isObject()) {
                throw new IllegalStateException("AWS secret payload must be a JSON object");
            }

            Map<String, Object> properties = new LinkedHashMap<>();
            flattenJson("", root, properties);
            return properties;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("AWS secret payload must be valid JSON", e);
        }
    }

    static void addAfterSystemEnvironment(ConfigurableEnvironment environment, String name, Map<String, Object> values) {
        if (values.isEmpty()) {
            return;
        }

        PropertySource<?> propertySource = new MapPropertySource(name, values);
        MutablePropertySources sources = environment.getPropertySources();

        if (sources.contains(name)) {
            sources.replace(name, propertySource);
            return;
        }

        if (sources.contains(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME)) {
            sources.addAfter(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, propertySource);
            return;
        }

        if (sources.contains(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME)) {
            sources.addAfter(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME, propertySource);
            return;
        }

        sources.addFirst(propertySource);
    }

    static void addBeforeSystemEnvironment(ConfigurableEnvironment environment, String name, Map<String, Object> values) {
        if (values.isEmpty()) {
            return;
        }

        PropertySource<?> propertySource = new MapPropertySource(name, values);
        MutablePropertySources sources = environment.getPropertySources();

        if (sources.contains(name)) {
            sources.replace(name, propertySource);
            return;
        }

        if (sources.contains(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME)) {
            sources.addAfter(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME, propertySource);
            return;
        }

        if (sources.contains(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME)) {
            sources.addBefore(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, propertySource);
            return;
        }

        sources.addFirst(propertySource);
    }

    private static void flattenJson(String prefix, JsonNode node, Map<String, Object> properties) {
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String nextPrefix = prefix.isEmpty() ? field.getKey() : prefix + "." + field.getKey();
                flattenJson(nextPrefix, field.getValue(), properties);
            }
            return;
        }

        putWithAliases(properties, prefix, toPropertyValue(node));
    }

    private static String toPropertyValue(JsonNode node) {
        if (node.isNull()) {
            return "";
        }
        if (node.isArray()) {
            return StreamSupport.stream(node.spliterator(), false)
                    .map(BootstrapPropertySupport::toPropertyValue)
                    .collect(Collectors.joining(","));
        }
        if (node.isValueNode()) {
            return node.asText();
        }
        return node.toString();
    }

    private static String normalizeDotenvValue(String value) {
        if (value.length() < 2) {
            return value;
        }

        char first = value.charAt(0);
        char last = value.charAt(value.length() - 1);
        if (first != last || (first != '"' && first != '\'')) {
            return value;
        }

        String unwrapped = value.substring(1, value.length() - 1);
        if (first == '\'') {
            return unwrapped;
        }

        return unwrapped
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }

    private static void putWithAliases(Map<String, Object> properties, String key, String value) {
        if (!StringUtils.hasText(key)) {
            return;
        }

        String trimmedKey = key.trim();
        properties.put(trimmedKey, value);

        String dottedKey = toDottedKey(trimmedKey);
        if (!trimmedKey.equals(dottedKey)) {
            properties.putIfAbsent(dottedKey, value);
        }

        String envKey = toEnvKey(trimmedKey);
        if (!trimmedKey.equals(envKey)) {
            properties.putIfAbsent(envKey, value);
        }
    }

    private static String toDottedKey(String key) {
        return key.toLowerCase(Locale.ROOT).replace('_', '.').replace('-', '.');
    }

    private static String toEnvKey(String key) {
        StringBuilder builder = new StringBuilder(key.length() + 8);
        for (int i = 0; i < key.length(); i++) {
            char current = key.charAt(i);
            if (current == '.' || current == '-') {
                builder.append('_');
                continue;
            }
            if (Character.isUpperCase(current) && i > 0) {
                char previous = key.charAt(i - 1);
                if (Character.isLowerCase(previous) || Character.isDigit(previous)) {
                    builder.append('_');
                }
            }
            builder.append(Character.toUpperCase(current));
        }
        return builder.toString();
    }
}
