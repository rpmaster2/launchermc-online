package org.launchermc;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static org.launchermc.Messages.f;
import static org.launchermc.Messages.t;

/**
 * Сборка командной строки и запуск Minecraft.
 *
 * Поддерживаются обе схемы метаданных Mojang:
 * - {@code minecraftArguments} (≤ 1.12) и {@code arguments.game/jvm} (≥ 1.13);
 * - старые нативы в {@code downloads.classifiers} и новые отдельные
 *   библиотеки с суффиксом {@code natives-<os>}.
 *
 * mainClass всегда берётся из JSON версии, поэтому работают и legacy-версии
 * через {@code net.minecraft.launchwrapper.Launch}.
 */
public class LaunchMinecraft {

    /**
     * Требование к Java для версии: major-версия и нужна ли именно она.
     */
    public record JavaRequirement(int major, boolean exact) {
    }

    /**
     * Читает требование к Java из JSON версии. Для launchwrapper — строго Java 8.
     */
    public static JavaRequirement requirement(String versionId) {
        Path metaFile = Download.versionDir(versionId).resolve(versionId + ".json");
        if (!Files.isRegularFile(metaFile)) {
            return new JavaRequirement(-1, false);
        }
        try {
            JsonObject raw = JsonParser.parseString(
                    Files.readString(metaFile, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject merged = mergeWithParent(raw);
            return requirement(merged, getString(raw, "mainClass"));
        } catch (Exception e) {
            return new JavaRequirement(-1, false);
        }
    }

    /**
     * Правило выбора Java: launchwrapper — 8; иначе javaVersion.majorVersion;
     * для старых версий без javaVersion — Java 8; для новых без него — автовыбор.
     */
    private static JavaRequirement requirement(JsonObject details, String mainClass) {
        if (mainClass.contains("launchwrapper")) {
            return new JavaRequirement(8, true);
        }
        int major = getJavaMajor(details);
        if (major > 0) {
            return new JavaRequirement(major, false);
        }
        String releaseTime = getString(details, "releaseTime");
        if (releaseTime.isBlank() || releaseTime.compareTo("2021-06-01") < 0) {
            return new JavaRequirement(8, true);
        }
        return new JavaRequirement(-1, false);
    }

    /**
     * Запускает игру и ждёт её завершения. Блокирующий метод — вызывайте из фонового потока.
     *
     * @param javaPath путь к java/javaw или null/пустая строка для автовыбора
     * @return код завершения процесса Minecraft
     */
    /**
     * Запускает игру с указанным аккаунтом Microsoft.
     */
    public int launch(MinecraftAccount account, String versionId, int allocatedMemory,
                      String javaPath, LauncherListener listener) throws Exception {
        Path versionDir = Download.versionDir(versionId);
        Path metaFile = versionDir.resolve(versionId + ".json");
        Path clientJar = Download.clientJar(versionId);
        Path librariesDir = Download.LIBRARIES_DIR;
        Path assetsDir = Download.ASSETS_DIR;

        if (!Files.isRegularFile(metaFile) || clientJar == null) {
            throw new IOException(f("Версия %s не скачана. Нажмите «Скачать».", versionId));
        }

        JsonObject raw = JsonParser.parseString(
                Files.readString(metaFile, StandardCharsets.UTF_8)).getAsJsonObject();
        String parentId = getString(raw, "inheritsFrom");
        JsonObject details = mergeWithParent(raw);

        String mainClass = getString(details, "mainClass");
        if (mainClass.isBlank()) {
            throw new IOException(t("В метаданных версии нет mainClass"));
        }
        boolean legacyWrapper = mainClass.contains("launchwrapper");

        JavaRequirement requirement = requirement(details, mainClass);
        int requiredJava = requirement.major();
        Path javaExecutable = JavaRuntime.find(javaPath, requiredJava,
                requirement.exact(), listener);
        JavaRuntime.Info javaInfo = JavaRuntime.detect(javaExecutable);
        int actualJava = javaInfo.major();
        if (requiredJava > 0 && actualJava > 0 && actualJava < requiredJava) {
            throw new IOException(f("Для версии %s нужна Java %s или новее, а выбрана Java %s (%s). "
                            + "Укажите подходящую Java в настройках лаунчера.",
                    versionId, requiredJava, actualJava, javaExecutable));
        }
        if (!legacyWrapper && requirement.exact() && actualJava > 0 && actualJava != requiredJava) {
            throw new IOException(f("Для версии %s нужна Java %s, а выбрана Java %s (%s). "
                            + "Установите подходящую Java или укажите путь в настройках.",
                    versionId, requiredJava, actualJava, javaExecutable));
        }
        if (actualJava <= 0) {
            listener.log(f("Не удалось определить версию Java: %s", javaExecutable));
        }
        if (legacyWrapper && actualJava > 8) {
            throw new IOException(f("Версия %s использует launchwrapper и требует Java 8, "
                            + "а выбрана Java %s. Установите Java 8 (например, "
                            + "Adoptium Temurin 8) — лаунчер найдёт её автоматически — "
                            + "или укажите путь в настройках.",
                    versionId, actualJava));
        }

        String assetIndex = "legacy";
        if (details.has("assetIndex") && details.get("assetIndex").isJsonObject()) {
            assetIndex = getString(details.getAsJsonObject("assetIndex"), "id");
        }
        Path gameAssetsDir = resolveGameAssets(versionId, versionDir, assetIndex, listener);
        Path nativesDir = prepareNatives(versionDir, versionId, parentId, details, listener);
        List<String> classpath = buildClasspath(clientJar, versionId, parentId, details);
        String classpathString = String.join(java.io.File.pathSeparator, classpath);

        Map<String, String> variables = buildVariables(
                account, versionId, versionDir, assetsDir, gameAssetsDir,
                assetIndex, nativesDir, librariesDir, classpathString);
        variables.put("version_type", versionType(details));

        List<String> jvmArgs = new ArrayList<>();
        List<String> gameArgs = new ArrayList<>();
        readArguments(details, jvmArgs, gameArgs, variables);
        addLoggingArgument(details, jvmArgs, variables);

        int memory = Math.max(512, allocatedMemory);
        if (javaInfo.bits() == 32 && memory > 1024) {
            memory = 1024;
            listener.log(t("Выбрана 32-битная Java — память ограничена 1024 МБ, "
                    + "чтобы JVM не упала с OutOfMemoryError."));
        }
        List<String> command = new ArrayList<>();
        command.add(javaExecutable.toString());
        command.add("-Xmx" + memory + "M");
        command.add("-Xms" + Math.min(512, memory) + "M");
        command.addAll(jvmArgs);
        if (jvmArgs.stream().noneMatch("-cp"::equals)) {
            command.add("-cp");
            command.add(classpathString);
        }
        if (jvmArgs.stream().noneMatch(a -> a.startsWith("-Djava.library.path"))) {
            command.add("-Djava.library.path=" + nativesDir.toAbsolutePath());
        }
        command.add(mainClass);
        command.addAll(gameArgs);

        listener.status(f("Запуск Minecraft %s…", versionId));
        listener.log("Java: " + javaExecutable + " (" + javaInfo.display() + ")");
        listener.log(f("Main class: %s, память: %s МБ", mainClass, memory));

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(versionDir.toFile());
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();
        listener.gameStarted();
        listener.log(f("Процесс Minecraft запущен (pid %s)", process.pid()));

        try (InputStream input = process.getInputStream();
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                listener.log(line);
            }
        }

        int exitCode = process.waitFor();
        listener.log(f("Minecraft завершился с кодом %s", exitCode));
        return exitCode;
    }

    /**
     * Папка assets для аргументов игры. Для legacy-версий это virtual-папка
     * или папка resources (map_to_resources), куда Download копирует файлы.
     */
    private Path resolveGameAssets(String versionId, Path versionDir, String assetIndex,
                                   LauncherListener listener) {
        Path assetsRoot = Download.ASSETS_DIR;
        Path indexFile = assetsRoot.resolve("indexes").resolve(assetIndex + ".json");
        try {
            if (Files.isRegularFile(indexFile)) {
                JsonObject index = JsonParser.parseString(
                        Files.readString(indexFile, StandardCharsets.UTF_8)).getAsJsonObject();
                if (index.has("virtual") && index.get("virtual").getAsBoolean()) {
                    Path virtual = assetsRoot.resolve("virtual").resolve(assetIndex);
                    listener.log(f("Legacy-ассеты (virtual): %s", virtual));
                    return virtual;
                }
                if (index.has("map_to_resources") && index.get("map_to_resources").getAsBoolean()) {
                    Path resources = versionDir.resolve("resources");
                    listener.log(f("Legacy-ассеты (resources): %s", resources));
                    return resources;
                }
            }
        } catch (Exception e) {
            listener.log(f("Не удалось прочитать индекс ассетов: %s", e.getMessage()));
        }
        return assetsRoot;
    }

    /**
     * Распаковывает нативы текущей ОС в versions/&lt;id&gt;/natives.
     */
    private Path prepareNatives(Path versionDir, String versionId, String parentId,
                                JsonObject details, LauncherListener listener) throws IOException {
        Path nativesDir = versionDir.resolve("natives");
        deleteRecursively(nativesDir);
        Files.createDirectories(nativesDir);

        JsonArray libraries = getArray(details, "libraries");
        if (libraries == null) {
            return nativesDir;
        }
        int extracted = 0;
        for (JsonElement element : libraries) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject library = element.getAsJsonObject();
            if (!RuleEvaluator.isAllowed(library)) {
                continue;
            }
            Path jar = nativeJarPath(library, versionId, parentId);
            if (jar == null || !Files.isRegularFile(jar)) {
                continue;
            }
            extracted += extractNatives(jar, nativesDir, library);
        }
        listener.log(f("Нативы распакованы: %s файлов", extracted));
        if (extracted == 0) {
            listener.log(t("Внимание: нативные библиотеки не найдены — игра может не запуститься"));
        }
        return nativesDir;
    }

    /**
     * Возвращает путь к нативному jar текущей ОС или null.
     * Сначала общий каталог библиотек, затем старая папка версии.
     */
    private Path nativeJarPath(JsonObject library, String versionId, String parentId) {
        String relative = null;
        JsonObject downloads = getObject(library, "downloads");
        if (downloads != null) {
            JsonObject classifiers = getObject(downloads, "classifiers");
            if (classifiers != null) {
                for (String key : nativeClassifierKeys(library)) {
                    JsonObject classifier = getObject(classifiers, key);
                    if (classifier != null && classifier.has("path")) {
                        relative = classifier.get("path").getAsString();
                        break;
                    }
                }
            }
        }
        String name = getString(library, "name").toLowerCase();
        if (relative == null && name.contains(":natives-") && matchesCurrentOs(name)) {
            JsonObject artifact = downloads == null ? null : getObject(downloads, "artifact");
            if (artifact != null && artifact.has("path")) {
                relative = artifact.get("path").getAsString();
            }
        }
        if (relative == null) {
            return null;
        }
        Path shared = Download.libraryPath(relative);
        if (Files.isRegularFile(shared)) {
            return shared;
        }
        Path legacy = Download.legacyLibraryPath(versionId, relative);
        if (Files.isRegularFile(legacy)) {
            return legacy;
        }
        if (parentId != null && !parentId.isBlank()) {
            Path parentLegacy = Download.legacyLibraryPath(parentId, relative);
            if (Files.isRegularFile(parentLegacy)) {
                return parentLegacy;
            }
        }
        return null;
    }

    private boolean matchesCurrentOs(String name) {
        String os = RuleEvaluator.currentOsName();
        boolean arm = RuleEvaluator.isArm();
        if ("windows".equals(os)) {
            return arm ? name.endsWith("natives-windows-arm64")
                    : name.endsWith("natives-windows");
        }
        if ("osx".equals(os)) {
            if (arm) {
                return name.endsWith("natives-macos-arm64");
            }
            return name.endsWith("natives-macos") || name.endsWith("natives-osx");
        }
        return arm ? name.endsWith("natives-linux-arm64") : name.endsWith("natives-linux");
    }

    private List<String> nativeClassifierKeys(JsonObject library) {
        List<String> keys = new ArrayList<>();
        JsonObject natives = getObject(library, "natives");
        if (natives != null && natives.has(RuleEvaluator.currentOsName())) {
            keys.add(natives.get(RuleEvaluator.currentOsName()).getAsString()
                    .replace("${arch}", RuleEvaluator.is64Bit() ? "64" : "32"));
        }
        String os = RuleEvaluator.currentOsName();
        String suffix = RuleEvaluator.isArm() ? "-arm64" : (RuleEvaluator.is64Bit() ? "-64" : "-32");
        keys.add("natives-" + os + suffix);
        keys.add("natives-" + os);
        if ("osx".equals(os)) {
            keys.add("natives-macos");
            keys.add("natives-osx");
        }
        return keys;
    }

    private int extractNatives(Path jarPath, Path nativesDir, JsonObject library)
            throws IOException {
        List<String> excludes = new ArrayList<>();
        JsonObject extract = getObject(library, "extract");
        if (extract != null) {
            JsonArray excludeArray = getArray(extract, "exclude");
            if (excludeArray != null) {
                for (JsonElement element : excludeArray) {
                    excludes.add(element.getAsString());
                }
            }
        }
        int count = 0;
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (entry.isDirectory() || name.startsWith("META-INF/")
                        || excludes.stream().anyMatch(name::startsWith)) {
                    continue;
                }
                Path target = nativesDir.resolve(name).normalize();
                if (!target.startsWith(nativesDir)) {
                    continue; // защита от path traversal
                }
                Files.createDirectories(target.getParent());
                try (InputStream in = jar.getInputStream(entry);
                     OutputStream out = Files.newOutputStream(target)) {
                    in.transferTo(out);
                }
                count++;
            }
        }
        return count;
    }

    private List<String> buildClasspath(Path clientJar, String versionId, String parentId,
                                        JsonObject details) {
        Set<String> classpath = new LinkedHashSet<>();
        classpath.add(clientJar.toAbsolutePath().toString());

        JsonArray libraries = getArray(details, "libraries");
        if (libraries != null) {
            for (JsonElement element : libraries) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject library = element.getAsJsonObject();
                if (!RuleEvaluator.isAllowed(library)) {
                    continue;
                }
                JsonObject downloads = getObject(library, "downloads");
                JsonObject artifact = downloads == null ? null : getObject(downloads, "artifact");
                if (artifact == null && downloads == null) {
                    artifact = Download.mavenArtifact(library);
                }
                if (artifact != null && artifact.has("path")) {
                    String relative = artifact.get("path").getAsString();
                    Path file = Download.libraryPath(relative);
                    if (!Files.isRegularFile(file)) {
                        file = Download.legacyLibraryPath(versionId, relative);
                    }
                    if (!Files.isRegularFile(file) && parentId != null && !parentId.isBlank()) {
                        file = Download.legacyLibraryPath(parentId, relative);
                    }
                    if (Files.isRegularFile(file)) {
                        classpath.add(file.toAbsolutePath().toString());
                    }
                }
            }
        }
        return new ArrayList<>(classpath);
    }

    private Map<String, String> buildVariables(MinecraftAccount account, String versionId,
                                               Path versionDir, Path assetsRoot,
                                               Path gameAssetsDir, String assetIndex,
                                               Path nativesDir, Path librariesDir,
                                               String classpath) {
        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("auth_player_name", account.name());
        vars.put("auth_uuid", account.uuid());
        vars.put("auth_access_token", account.accessToken());
        vars.put("auth_session", account.accessToken());
        vars.put("auth_xuid", account.xuid());
        vars.put("clientid", "launchermc");
        vars.put("user_type", account.userType());
        vars.put("user_properties", "{}");
        vars.put("version_name", versionId);
        vars.put("version_type", "release");
        vars.put("game_directory", versionDir.toAbsolutePath().toString());
        vars.put("assets_root", assetsRoot.toAbsolutePath().toString());
        vars.put("game_assets", gameAssetsDir.toAbsolutePath().toString());
        vars.put("assets_index_name", assetIndex);
        vars.put("natives_directory", nativesDir.toAbsolutePath().toString());
        vars.put("library_directory", librariesDir.toAbsolutePath().toString());
        vars.put("classpath", classpath);
        vars.put("launcher_name", "LauncherMC");
        vars.put("launcher_version", "1.4");
        vars.put("resolution_width", "854");
        vars.put("resolution_height", "480");
        return vars;
    }

    private void readArguments(JsonObject details, List<String> jvmArgs,
                               List<String> gameArgs, Map<String, String> variables) {
        JsonObject arguments = getObject(details, "arguments");
        if (arguments != null) {
            appendArgumentArray(getArray(arguments, "jvm"), jvmArgs, variables);
            appendArgumentArray(getArray(arguments, "game"), gameArgs, variables);
        }

        if (gameArgs.isEmpty() && details.has("minecraftArguments")) {
            String legacy = getString(details, "minecraftArguments");
            for (String token : legacy.trim().split("\\s+")) {
                if (!token.isBlank()) {
                    gameArgs.add(substitute(token, variables));
                }
            }
        }
    }

    /**
     * Добавляет {@code -Dlog4j.configurationFile=...}, если версия его объявляет
     * и конфиг уже скачан. Раньше этот аргумент терялся.
     */
    private void addLoggingArgument(JsonObject details, List<String> jvmArgs,
                                    Map<String, String> variables) {
        Path logConfig = Download.logConfigPath(details);
        if (logConfig == null || !Files.isRegularFile(logConfig)) {
            return;
        }
        JsonObject logging = getObject(details, "logging");
        JsonObject client = logging == null ? null : getObject(logging, "client");
        if (client == null) {
            return;
        }
        String argument = getString(client, "argument");
        if (argument.isBlank()) {
            return;
        }
        Map<String, String> vars = new LinkedHashMap<>(variables);
        vars.put("path", logConfig.toAbsolutePath().toString());
        jvmArgs.add(substitute(argument, vars));
    }

    /**
     * Тип версии из метаданных (release/snapshot/old_beta/old_alpha) —
     * попадает в {@code --versionType}.
     */
    private String versionType(JsonObject details) {
        String type = getString(details, "type");
        return type.isBlank() ? "release" : type;
    }

    private void appendArgumentArray(JsonArray array, List<String> target,
                                     Map<String, String> variables) {
        if (array == null) {
            return;
        }
        for (JsonElement element : array) {
            if (element.isJsonPrimitive()) {
                target.add(substitute(element.getAsString(), variables));
                continue;
            }
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject object = element.getAsJsonObject();
            if (!RuleEvaluator.isAllowed(object)) {
                continue;
            }
            JsonElement value = object.get("value");
            if (value == null) {
                continue;
            }
            if (value.isJsonArray()) {
                for (JsonElement item : value.getAsJsonArray()) {
                    target.add(substitute(item.getAsString(), variables));
                }
            } else {
                target.add(substitute(value.getAsString(), variables));
            }
        }
    }

    private String substitute(String value, Map<String, String> variables) {
        String result = value;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            result = result.replace("${" + entry.getKey() + "}", entry.getValue());
        }
        // неизвестные плейсхолдеры не должны ломать аргументы
        return result.replaceAll("\\$\\{[^}]+}", "");
    }

    private static int getJavaMajor(JsonObject details) {
        JsonObject javaVersion = getObject(details, "javaVersion");
        if (javaVersion != null && javaVersion.has("majorVersion")) {
            try {
                return javaVersion.get("majorVersion").getAsInt();
            } catch (Exception ignored) {
                return -1;
            }
        }
        return -1;
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (var stream = Files.walk(path)) {
            stream.sorted((a, b) -> b.getNameCount() - a.getNameCount())
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException ignored) {
                        }
                    });
        }
    }

    /**
     * Сливает дочерний профиль (Fabric) с родительской ванильной версией:
     * библиотеки и аргументы объединяются, остальные поля наследуются.
     */
    private static JsonObject mergeWithParent(JsonObject raw) {
        String parentId = getString(raw, "inheritsFrom");
        if (parentId.isBlank()) {
            return raw;
        }
        Path parentFile = Download.versionDir(parentId).resolve(parentId + ".json");
        if (!Files.isRegularFile(parentFile)) {
            return raw;
        }
        try {
            JsonObject parent = JsonParser.parseString(
                    Files.readString(parentFile, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject merged = parent.deepCopy();
            for (String key : List.of("mainClass", "assets", "assetIndex", "downloads",
                    "javaVersion", "logging", "type", "releaseTime",
                    "minimumLauncherVersion", "complianceLevel")) {
                if (raw.has(key)) {
                    merged.add(key, raw.get(key).deepCopy());
                }
            }
            JsonArray libraries = new JsonArray();
            appendAll(parent.getAsJsonArray("libraries"), libraries);
            appendAll(raw.getAsJsonArray("libraries"), libraries);
            merged.add("libraries", libraries);

            JsonObject arguments = mergeArguments(parent, raw);
            if (arguments != null) {
                merged.add("arguments", arguments);
            }
            if (raw.has("minecraftArguments")) {
                merged.add("minecraftArguments", raw.get("minecraftArguments").deepCopy());
            }
            merged.addProperty("id", getString(raw, "id"));
            merged.addProperty("inheritsFrom", parentId);
            return merged;
        } catch (Exception e) {
            return raw;
        }
    }

    private static void appendAll(JsonArray source, JsonArray target) {
        if (source == null) {
            return;
        }
        for (JsonElement element : source) {
            target.add(element.deepCopy());
        }
    }

    private static JsonObject mergeArguments(JsonObject parent, JsonObject child) {
        JsonObject parentArguments = getObject(parent, "arguments");
        JsonObject childArguments = getObject(child, "arguments");
        if (parentArguments == null && childArguments == null) {
            return null;
        }
        JsonObject merged = parentArguments != null ? parentArguments.deepCopy() : new JsonObject();
        if (childArguments != null) {
            for (String key : childArguments.keySet()) {
                JsonElement value = childArguments.get(key);
                if (value.isJsonArray()) {
                    JsonArray array = merged.has(key) && merged.get(key).isJsonArray()
                            ? merged.getAsJsonArray(key) : new JsonArray();
                    appendAll(value.getAsJsonArray(), array);
                    merged.add(key, array);
                } else {
                    merged.add(key, value.deepCopy());
                }
            }
        }
        return merged;
    }

    private static JsonObject getObject(JsonObject object, String key) {
        return object != null && object.has(key) && object.get(key).isJsonObject()
                ? object.getAsJsonObject(key) : null;
    }

    private static JsonArray getArray(JsonObject object, String key) {
        return object != null && object.has(key) && object.get(key).isJsonArray()
                ? object.getAsJsonArray(key) : null;
    }

    private static String getString(JsonObject object, String key) {
        return object != null && object.has(key) && object.get(key).isJsonPrimitive()
                ? object.get(key).getAsString() : "";
    }
}

