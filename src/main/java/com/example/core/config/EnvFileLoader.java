package com.example.core.config;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Slf4j
public final class EnvFileLoader {

    private EnvFileLoader() {
    }

    public static void loadDotEnv() {
        Path envPath = Path.of(".env").toAbsolutePath().normalize();
        if (!Files.isRegularFile(envPath)) {
            return;
        }

        try {
            List<String> lines = Files.readAllLines(envPath);
            int applied = 0;
            for (String line : lines) {
                if (applyLine(line)) {
                    applied++;
                }
            }
            if (applied > 0) {
                log.info("Loaded {} properties from {}", applied, envPath);
            }
        } catch (IOException e) {
            log.warn("Failed to load .env file from {}: {}", envPath, e.getMessage());
        }
    }

    private static boolean applyLine(String line) {
        String raw = line == null ? "" : line.trim();
        if (raw.isEmpty() || raw.startsWith("#")) {
            return false;
        }

        int separator = raw.indexOf('=');
        if (separator <= 0) {
            return false;
        }

        String key = raw.substring(0, separator).trim();
        if (key.isEmpty()) {
            return false;
        }

        if (System.getenv(key) != null || System.getProperty(key) != null) {
            return false;
        }

        String value = raw.substring(separator + 1).trim();
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                value = value.substring(1, value.length() - 1);
            }
        }

        System.setProperty(key, value);
        return true;
    }
}
