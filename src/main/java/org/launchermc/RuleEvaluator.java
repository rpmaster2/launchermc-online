package org.launchermc;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Проверка правил (rules) из version JSON: подходит ли библиотека/аргумент
 * текущей операционной системе и архитектуре.
 */
public final class RuleEvaluator {

    private static final String OS_NAME = System.getProperty("os.name", "").toLowerCase();
    private static final String OS_ARCH = System.getProperty("os.arch", "").toLowerCase();

    private RuleEvaluator() {
    }

    /**
     * Возвращает true, если объект с полем rules разрешён на этой машине.
     * Объекты без rules считаются разрешёнными.
     */
    public static boolean isAllowed(JsonObject object) {
        if (object == null || !object.has("rules") || !object.get("rules").isJsonArray()) {
            return true;
        }
        JsonArray rules = object.getAsJsonArray("rules");
        boolean allowed = false;
        for (JsonElement element : rules) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject rule = element.getAsJsonObject();
            String action = getString(rule, "action");
            if (matches(rule)) {
                allowed = "allow".equals(action);
            }
        }
        return allowed;
    }

    private static boolean matches(JsonObject rule) {
        if (rule.has("os") && rule.get("os").isJsonObject()) {
            JsonObject os = rule.getAsJsonObject("os");
            String name = getString(os, "name");
            if (!name.isEmpty() && !name.equals(currentOsName())) {
                return false;
            }
            String arch = getString(os, "arch");
            if (!arch.isEmpty() && !archMatches(arch)) {
                return false;
            }
            String version = getString(os, "version");
            if (!version.isEmpty() && !System.getProperty("os.version", "").matches(version)) {
                return false;
            }
        }
        // features (has_custom_resolution, is_demo_user, ...) у нас всегда выключены
        if (rule.has("features") && rule.get("features").isJsonObject()) {
            for (String feature : rule.getAsJsonObject("features").keySet()) {
                if (rule.getAsJsonObject("features").get(feature).getAsBoolean()) {
                    return false;
                }
            }
        }
        return true;
    }

    public static String currentOsName() {
        if (OS_NAME.contains("win")) {
            return "windows";
        }
        if (OS_NAME.contains("mac") || OS_NAME.contains("darwin")) {
            return "osx";
        }
        return "linux";
    }

    public static String currentArch() {
        return is64Bit() ? "x86_64" : "x86";
    }

    public static boolean isArm() {
        return OS_ARCH.contains("aarch64") || OS_ARCH.contains("arm");
    }

    public static boolean is64Bit() {
        return OS_ARCH.contains("64") || OS_ARCH.contains("aarch64");
    }

    private static boolean archMatches(String arch) {
        return switch (arch) {
            case "x86" -> !is64Bit() && !isArm();
            case "x86_64", "amd64" -> is64Bit() && !isArm();
            case "arm64", "aarch64" -> isArm() && is64Bit();
            case "arm32" -> isArm() && !is64Bit();
            default -> arch.equals(OS_ARCH);
        };
    }

    private static String getString(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonPrimitive()
                ? object.get(key).getAsString()
                : "";
    }
}
