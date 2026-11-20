package org.launchermc;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;

import static org.launchermc.Messages.f;
import static org.launchermc.Messages.t;

/**
 * Скачивание клиента, библиотек и ассетов выбранной версии Minecraft.
 *
 * Раскладка файлов (совместима с предыдущими версиями лаунчера):
 * <pre>
 * versions/&lt;id&gt;/&lt;id&gt;.json
 * versions/&lt;id&gt;/&lt;id&gt;-client.jar
 * versions/&lt;id&gt;/libraries/...
 * versions/assets/indexes/&lt;index&gt;.json
 * versions/assets/objects/&lt;xx&gt;/&lt;hash&gt;
 * </pre>
 */
public class Download {

    public static final Path VERSIONS_DIR = Path.of("versions");
    public static final Path ASSETS_DIR = VERSIONS_DIR.resolve("assets");
    /** Общие библиотеки для всех версий (новые загрузки). */
    public static final Path LIBRARIES_DIR = VERSIONS_DIR.resolve("libraries");

    private static final String RESOURCES_URL = "https://resources.download.minecraft.net/";
    private static final int CONNECT_TIMEOUT_MS = 15_000;
    private static final int READ_TIMEOUT_MS = 60_000;
    private static final int MAX_ATTEMPTS = 3;

    /**
     * Проверяет, скачана ли версия (метаданные + клиентский jar).
     * Быстрая проверка для списка версий; полная — {@link #isComplete(String)}.
     */
    public static boolean isInstalled(String versionId) {
        Path dir = VERSIONS_DIR.resolve(versionId);
        return Files.isRegularFile(dir.resolve(versionId + ".json"))
                && clientJar(versionId) != null;
    }

    /**
     * Клиентский jar версии. Для профилей с {@code inheritsFrom} (Fabric)
     * используется клиент родительской версии.
     */
    public static Path clientJar(String versionId) {
        Path own = versionDir(versionId).resolve(versionId + "-client.jar");
        if (Files.isRegularFile(own)) {
            return own;
        }
        String parent = parentVersion(versionId);
        if (parent != null) {
            Path inherited = versionDir(parent).resolve(parent + "-client.jar");
            if (Files.isRegularFile(inherited)) {
                return inherited;
            }
        }
        return null;
    }

    /**
     * id родительской версии из {@code inheritsFrom} или {@code null}.
     */
    public static String parentVersion(String versionId) {
        Path metaFile = versionDir(versionId).resolve(versionId + ".json");
        if (!Files.isRegularFile(metaFile)) {
            return null;
        }
        try {
            JsonObject details = JsonParser.parseString(
                    Files.readString(metaFile, StandardCharsets.UTF_8)).getAsJsonObject();
            String parent = getString(details, "inheritsFrom");
            return parent.isBlank() ? null : parent;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Полная проверка установки: метаданные, клиент, библиотеки, индекс ассетов
     * и конфиг логирования (если он есть в метаданных). Сами ассеты не
     * пересчитываются — их слишком много, целостность проверяется при ремонте.
     */
    public static boolean isComplete(String versionId) {
        Path dir = versionDir(versionId);
        Path metaFile = dir.resolve(versionId + ".json");
        Path clientJar = clientJar(versionId);
        if (!Files.isRegularFile(metaFile) || clientJar == null) {
            return false;
        }
        String parent = parentVersion(versionId);
        if (parent != null && !isComplete(parent)) {
            return false;
        }
        try {
            JsonObject details = JsonParser.parseString(
                    Files.readString(metaFile, StandardCharsets.UTF_8)).getAsJsonObject();
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
                    if (downloads == null) {
                        JsonObject maven = mavenArtifact(library);
                        if (maven != null
                                && !libraryExists(versionId, maven.get("path").getAsString())) {
                            return false;
                        }
                        continue;
                    }
                    JsonObject artifact = getObject(downloads, "artifact");
                    if (artifact != null && artifact.has("path")
                            && !libraryExists(versionId, artifact.get("path").getAsString())) {
                        return false;
                    }
                    JsonObject classifier = selectNativeClassifier(library, downloads);
                    if (classifier != null && classifier.has("path")
                            && !libraryExists(versionId, classifier.get("path").getAsString())) {
                        return false;
                    }
                }
            }
            JsonObject assetIndex = getObject(details, "assetIndex");
            if (assetIndex != null && assetIndex.has("id")) {
                Path indexFile = ASSETS_DIR.resolve("indexes")
                        .resolve(assetIndex.get("id").getAsString() + ".json");
                if (!Files.isRegularFile(indexFile)) {
                    return false;
                }
            }
            Path logConfig = logConfigPath(details);
            return logConfig == null || Files.isRegularFile(logConfig);
        } catch (Exception e) {
            return false;
        }
    }

    public static Path versionDir(String versionId) {
        return VERSIONS_DIR.resolve(versionId);
    }

    /**
     * Путь к библиотеке в общем каталоге (новые установки).
     */
    public static Path libraryPath(String relativePath) {
        return LIBRARIES_DIR.resolve(relativePath);
    }

    /**
     * Старый путь — библиотеки внутри папки версии (совместимость с v1.0–1.2).
     */
    public static Path legacyLibraryPath(String versionId, String relativePath) {
        return versionDir(versionId).resolve("libraries").resolve(relativePath);
    }

    /**
     * Библиотека есть в общем каталоге или в старой папке версии.
     */
    public static boolean libraryExists(String versionId, String relativePath) {
        return Files.isRegularFile(libraryPath(relativePath))
                || Files.isRegularFile(legacyLibraryPath(versionId, relativePath));
    }

    /**
     * Путь к конфигу логирования Mojang (блок {@code logging.client.file}).
     * {@code null}, если версия его не использует.
     */
    public static Path logConfigPath(JsonObject details) {
        JsonObject logging = getObject(details, "logging");
        JsonObject client = logging == null ? null : getObject(logging, "client");
        JsonObject file = client == null ? null : getObject(client, "file");
        if (file == null || !file.has("id")) {
            return null;
        }
        return ASSETS_DIR.resolve("log_configs").resolve(file.get("id").getAsString());
    }

    /**
     * Скачивает версию. Метод блокирующий — вызывайте его из фонового потока.
     */
    public void download(String versionId, LauncherListener listener) throws Exception {
        download(versionId, listener, false, () -> false);
    }

    /**
     * Скачивает версию.
     *
     * @param verifyExisting {@code true} — проверить SHA-1 уже существующих файлов
     *                       и перекачать повреждённые (режим «Исправить»/«Переустановить»);
     *                       {@code false} — пропускать файлы, совпадающие по размеру
     * @param cancelled      проверка отмены пользователем
     */
    public void download(String versionId, LauncherListener listener, boolean verifyExisting,
                         BooleanSupplier cancelled) throws Exception {
        Path versionDir = VERSIONS_DIR.resolve(versionId);
        Files.createDirectories(versionDir);
        cleanupPartFiles(versionDir);
        cleanupPartFiles(ASSETS_DIR);

        listener.status(f("Получение данных версии %s…", versionId));
        listener.progress(-1);
        JsonObject details = loadVersionDetails(versionId, versionDir, listener);

        List<FileTask> tasks = new ArrayList<>();
        collectClientTask(versionId, versionDir, details, tasks);
        collectLibraryTasks(versionDir, details, tasks);
        collectLoggingTask(details, tasks);
        List<FileTask> assetTasks = collectAssetTasks(details, tasks);
        if (assetTasks.isEmpty()) {
            listener.log(t("Ассеты для этой версии не требуются"));
        }
        if (verifyExisting) {
            listener.log(t("Проверка существующих файлов по SHA-1 (режим ремонта)…"));
        }

        ProgressTracker tracker = new ProgressTracker(listener, tasks.size());
        ExecutorService executor = Executors.newFixedThreadPool(
                Math.max(4, Math.min(16, Runtime.getRuntime().availableProcessors() * 2)));
        long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(30);

        try {
            // Метаданные версии сохраняем рядом с клиентом.
            Files.writeString(versionDir.resolve(versionId + ".json"),
                    details.toString(), StandardCharsets.UTF_8);

            for (FileTask task : tasks) {
                executor.submit(() -> {
                    if (cancelled.getAsBoolean()) {
                        return;
                    }
                    try {
                        if (needsDownload(task, verifyExisting)) {
                            downloadFile(task.url(), task.target(), task.sha1());
                        }
                        tracker.done(task.label());
                    } catch (Exception e) {
                        tracker.failed(task, e);
                    }
                });
            }
            executor.shutdown();
            while (!executor.awaitTermination(1, TimeUnit.SECONDS)) {
                if (cancelled.getAsBoolean()) {
                    throw new IOException(t("Скачивание отменено"));
                }
                if (System.nanoTime() > deadline) {
                    throw new IOException(t("Скачивание не завершилось за 30 минут"));
                }
            }
        } finally {
            executor.shutdownNow();
            try {
                executor.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        if (cancelled.getAsBoolean()) {
            throw new IOException(t("Скачивание отменено"));
        }
        if (tracker.failures() > 0) {
            throw new IOException(f("Не удалось скачать файлов: %s. Подробности — в окне лога.",
                    tracker.failures()));
        }

        buildLegacyAssets(versionDir, details, listener);

        listener.progress(1);
        listener.status(f("Версия %s готова к запуску", versionId));
        listener.log(f("Версия %s успешно скачана (файлов: %s)", versionId, tasks.size()));
    }

    /**
     * Удаляет недокачанные {@code *.part} после аварийного завершения.
     */
    private void cleanupPartFiles(Path root) {
        if (!Files.isDirectory(root)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(path -> path.getFileName().toString().endsWith(".part"))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                            // файл занят или уже удалён — не критично
                        }
                    });
        } catch (IOException ignored) {
            // не смогли обойти каталог — пропускаем
        }
    }

    private JsonObject loadVersionDetails(String versionId, Path versionDir,
                                          LauncherListener listener) throws IOException {
        String metaUrl = null;
        VersionInfo cached = LoadAvailableVersion.findCached(versionId);
        if (cached != null && cached.getUrl() != null && !cached.getUrl().isBlank()) {
            metaUrl = cached.getUrl();
        }

        Path localJson = versionDir.resolve(versionId + ".json");
        if (metaUrl != null) {
            try {
                String json = readUrl(metaUrl, "application/json", MAX_ATTEMPTS);
                if (json != null && !json.isBlank()) {
                    return JsonParser.parseString(json).getAsJsonObject();
                }
            } catch (Exception e) {
                listener.log(f("Не удалось обновить метаданные версии: %s", e.getMessage()));
            }
        }

        if (Files.isRegularFile(localJson)) {
            listener.log(f("Используются локальные метаданные %s", localJson));
            return JsonParser.parseString(Files.readString(localJson, StandardCharsets.UTF_8))
                    .getAsJsonObject();
        }
        throw new IOException(f("Не удалось получить метаданные версии %s. Проверьте подключение к интернету.",
                versionId));
    }

    private void collectClientTask(String versionId, Path versionDir, JsonObject details,
                                   List<FileTask> tasks) throws IOException {
        if (!getString(details, "inheritsFrom").isBlank()) {
            // Fabric-профиль использует клиент родительской версии.
            return;
        }
        JsonObject downloads = getObject(details, "downloads");
        JsonObject client = downloads == null ? null : getObject(downloads, "client");
        if (client == null || !client.has("url")) {
            throw new IOException(f("В метаданных версии %s нет клиентского jar", versionId));
        }
        tasks.add(new FileTask(
                versionDir.resolve(versionId + "-client.jar"),
                client.get("url").getAsString(),
                getString(client, "sha1"),
                getLong(client, "size"),
                t("клиент")
        ));
    }

    private void collectLibraryTasks(Path versionDir, JsonObject details, List<FileTask> tasks) {
        JsonArray libraries = getArray(details, "libraries");
        if (libraries == null) {
            return;
        }
        Path librariesDir = LIBRARIES_DIR;
        for (JsonElement element : libraries) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject library = element.getAsJsonObject();
            if (!RuleEvaluator.isAllowed(library)) {
                continue;
            }
            JsonObject downloads = getObject(library, "downloads");
            if (downloads == null) {
                JsonObject maven = mavenArtifact(library);
                if (maven != null) {
                    addLibraryTask(librariesDir, maven, tasks);
                }
                continue;
            }
            JsonObject artifact = getObject(downloads, "artifact");
            if (artifact != null && artifact.has("path") && artifact.has("url")) {
                addLibraryTask(librariesDir, artifact, tasks);
            }
            JsonObject classifier = selectNativeClassifier(library, downloads);
            if (classifier != null && classifier.has("path") && classifier.has("url")) {
                addLibraryTask(librariesDir, classifier, tasks);
            }
        }
    }

    private void addLibraryTask(Path librariesDir, JsonObject artifact, List<FileTask> tasks) {
        String path = artifact.get("path").getAsString();
        tasks.add(new FileTask(
                librariesDir.resolve(path),
                artifact.get("url").getAsString(),
                getString(artifact, "sha1"),
                getLong(artifact, "size"),
                f("библиотека %s", Path.of(path).getFileName())
        ));
    }

    /**
     * Превращает Maven-координаты Fabric ({@code group:artifact:version[:classifier]})
     * в обычный artifact с path/url.
     */
    static JsonObject mavenArtifact(JsonObject library) {        String name = getString(library, "name");
        String[] parts = name.split(":");
        if (parts.length < 3) {
            return null;
        }
        String group = parts[0];
        String artifactId = parts[1];
        String version = parts[2];
        String classifier = parts.length > 3 ? parts[3] : "";
        String path = group.replace('.', '/') + "/" + artifactId + "/" + version + "/"
                + artifactId + "-" + version
                + (classifier.isBlank() ? "" : "-" + classifier) + ".jar";
        String base = getString(library, "url");
        if (base.isBlank()) {
            base = "https://libraries.minecraft.net/";
        }
        if (!base.endsWith("/")) {
            base += "/";
        }
        JsonObject artifact = new JsonObject();
        artifact.addProperty("path", path);
        artifact.addProperty("url", base + path);
        String sha1 = getString(library, "sha1");
        if (!sha1.isBlank()) {
            artifact.addProperty("sha1", sha1);
        }
        long size = getLong(library, "size");
        if (size > 0) {
            artifact.addProperty("size", size);
        }
        return artifact;
    }

    /**
     * Конфиг логирования Mojang (log4j) — небольшой файл, на который ссылается
     * аргумент {@code -Dlog4j.configurationFile=${path}}.
     */
    private void collectLoggingTask(JsonObject details, List<FileTask> tasks) {
        Path target = logConfigPath(details);
        if (target == null) {
            return;
        }
        JsonObject logging = getObject(details, "logging");
        JsonObject client = logging == null ? null : getObject(logging, "client");
        JsonObject file = client == null ? null : getObject(client, "file");
        if (file == null || !file.has("url")) {
            return;
        }
        tasks.add(new FileTask(
                target,
                file.get("url").getAsString(),
                getString(file, "sha1"),
                getLong(file, "size"),
                t("конфиг логирования")
        ));
    }

    /**
     * Для старых манифестов нативы лежат в downloads.classifiers, для новых —
     * отдельными библиотеками с суффиксом natives-&lt;os&gt;.
     */
    private static JsonObject selectNativeClassifier(JsonObject library, JsonObject downloads) {
        JsonObject classifiers = getObject(downloads, "classifiers");
        if (classifiers == null) {
            return null;
        }
        List<String> candidates = new ArrayList<>();
        JsonObject natives = getObject(library, "natives");
        if (natives != null && natives.has(RuleEvaluator.currentOsName())) {
            String key = natives.get(RuleEvaluator.currentOsName()).getAsString()
                    .replace("${arch}", RuleEvaluator.is64Bit() ? "64" : "32");
            candidates.add(key);
        }
        String os = RuleEvaluator.currentOsName();
        String arch = RuleEvaluator.isArm() ? "arm64" : (RuleEvaluator.is64Bit() ? "64" : "32");
        candidates.add("natives-" + os + "-" + arch);
        candidates.add("natives-" + os);
        if ("osx".equals(os)) {
            candidates.add("natives-macos");
            candidates.add("natives-osx");
        }
        for (String candidate : candidates) {
            JsonObject object = getObject(classifiers, candidate);
            if (object != null) {
                return object;
            }
        }
        return null;
    }

    private List<FileTask> collectAssetTasks(JsonObject details, List<FileTask> tasks)
            throws IOException {
        List<FileTask> assetTasks = new ArrayList<>();
        JsonObject assetIndex = getObject(details, "assetIndex");
        if (assetIndex == null || !assetIndex.has("url") || !assetIndex.has("id")) {
            return assetTasks;
        }
        String indexId = assetIndex.get("id").getAsString();
        Path indexFile = ASSETS_DIR.resolve("indexes").resolve(indexId + ".json");
        tasks.add(new FileTask(
                indexFile,
                assetIndex.get("url").getAsString(),
                getString(assetIndex, "sha1"),
                getLong(assetIndex, "size"),
                t("индекс ассетов")
        ));

        // Скачиваем индекс сразу: из него строится список файлов.
        if (!Files.isRegularFile(indexFile) || !verify(indexFile, getString(assetIndex, "sha1"))) {
            downloadFile(assetIndex.get("url").getAsString(), indexFile, getString(assetIndex, "sha1"));
        }

        JsonObject index;
        try {
            index = JsonParser.parseString(Files.readString(indexFile, StandardCharsets.UTF_8))
                    .getAsJsonObject();
        } catch (Exception e) {
            throw new IOException(f("Повреждён индекс ассетов %s: %s", indexId, e.getMessage()));
        }

        JsonObject objects = getObject(index, "objects");
        if (objects == null) {
            return assetTasks;
        }
        for (String name : objects.keySet()) {
            JsonObject object = objects.getAsJsonObject(name);
            if (!object.has("hash")) {
                continue;
            }
            String hash = object.get("hash").getAsString();
            String relative = hash.substring(0, 2) + "/" + hash;
            Path target = ASSETS_DIR.resolve("objects").resolve(relative);
            FileTask task = new FileTask(
                    target,
                    RESOURCES_URL + relative,
                    hash,
                    getLong(object, "size"),
                    t("ассет")
            );
            assetTasks.add(task);
            tasks.add(task);
        }
        return assetTasks;
    }

    /**
     * Для версий ≤ 1.6 индекс ассетов имеет флаг virtual: файлы должны лежать
     * в одной папке с исходными именами. Делаем копии, чтобы игра нашла звуки.
     */
    private void buildLegacyAssets(Path versionDir, JsonObject details,
                                   LauncherListener listener) {
        JsonObject assetIndex = getObject(details, "assetIndex");
        if (assetIndex == null || !assetIndex.has("id")) {
            return;
        }
        String indexId = assetIndex.get("id").getAsString();
        Path indexFile = ASSETS_DIR.resolve("indexes").resolve(indexId + ".json");
        try {
            JsonObject index = JsonParser.parseString(Files.readString(indexFile, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            boolean virtual = index.has("virtual") && index.get("virtual").getAsBoolean();
            boolean mapToResources = index.has("map_to_resources")
                    && index.get("map_to_resources").getAsBoolean();
            if (!virtual && !mapToResources) {
                return;
            }
            JsonObject objects = getObject(index, "objects");
            if (objects == null) {
                return;
            }
            Path targetDir = virtual
                    ? ASSETS_DIR.resolve("virtual").resolve(indexId)
                    : versionDir.resolve("resources");
            listener.status(t("Подготовка legacy-ассетов…"));
            int copied = 0;
            for (String name : objects.keySet()) {
                String hash = objects.getAsJsonObject(name).get("hash").getAsString();
                Path source = ASSETS_DIR.resolve("objects").resolve(hash.substring(0, 2)).resolve(hash);
                Path target = targetDir.resolve(name);
                if (!Files.isRegularFile(target) && Files.isRegularFile(source)) {
                    Files.createDirectories(target.getParent());
                    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                    copied++;
                }
            }
            if (copied > 0) {
                listener.log(f("Legacy-ассеты подготовлены: %s файлов в %s", copied, targetDir));
            }
        } catch (Exception e) {
            listener.log(f("Не удалось подготовить legacy-ассеты: %s", e.getMessage()));
        }
    }

    private boolean needsDownload(FileTask task, boolean verifyExisting) {
        if (!Files.isRegularFile(task.target())) {
            return true;
        }
        try {
            if (verifyExisting) {
                if (task.sha1() != null && !task.sha1().isBlank()) {
                    return !verify(task.target(), task.sha1());
                }
                return false;
            }
            return task.size() > 0 && Files.size(task.target()) != task.size();
        } catch (IOException ignored) {
            return true;
        }
    }

    private boolean verify(Path file, String sha1) {
        if (sha1 == null || sha1.isBlank() || !Files.isRegularFile(file)) {
            return true;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            try (InputStream in = Files.newInputStream(file)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
            String actual = HexFormat.of().formatHex(digest.digest());
            return actual.equalsIgnoreCase(sha1);
        } catch (Exception e) {
            // не смогли прочитать файл — считаем повреждённым и перекачаем
            return false;
        }
    }

    private void downloadFile(String url, Path target, String sha1) throws IOException {
        IOException last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            Path tmp = null;
            try {
                Files.createDirectories(target.getParent());
                tmp = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".part");
                try (InputStream in = openStream(url);
                     OutputStream out = Files.newOutputStream(tmp)) {
                    in.transferTo(out);
                }
                if (sha1 != null && !sha1.isBlank() && !verify(tmp, sha1)) {
                    throw new IOException(f("Несовпадение SHA-1: %s", url));
                }
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
                return;
            } catch (IOException e) {
                last = e;
                if (tmp != null) {
                    Files.deleteIfExists(tmp);
                }
                if (attempt < MAX_ATTEMPTS) {
                    sleep(400L * attempt);
                }
            }
        }
        throw last != null ? last : new IOException(f("Не удалось скачать %s", url));
    }

    private InputStream openStream(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setRequestProperty("User-Agent", "LauncherMC/1.4");
        int code = connection.getResponseCode();
        if (code != HttpURLConnection.HTTP_OK) {
            connection.disconnect();
            throw new IOException(f("HTTP %s для %s", code, url));
        }
        return connection.getInputStream();
    }

    private String readUrl(String url, String contentType, int attempts) throws IOException {
        IOException last = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
                connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
                connection.setReadTimeout(READ_TIMEOUT_MS);
                connection.setRequestProperty("User-Agent", "LauncherMC/1.4");
                connection.setRequestProperty("Accept", contentType);
                if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    throw new IOException("HTTP " + connection.getResponseCode());
                }
                try (InputStream in = connection.getInputStream()) {
                    return new String(in.readAllBytes(), StandardCharsets.UTF_8);
                }
            } catch (IOException e) {
                last = e;
                if (attempt < attempts) {
                    sleep(400L * attempt);
                }
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }
        throw last != null ? last : new IOException(f("Не удалось прочитать %s", url));
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
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

    private static long getLong(JsonObject object, String key) {
        try {
            return object != null && object.has(key) ? object.get(key).getAsLong() : 0L;
        } catch (Exception e) {
            return 0L;
        }
    }

    private record FileTask(Path target, String url, String sha1, long size, String label) {
    }

    /**
     * Считает общий прогресс и складывает ошибки. Колбэки вызываются из
     * рабочих потоков, поэтому UI обязан сам переключаться в FX-поток.
     */
    private static final class ProgressTracker {
        private final LauncherListener listener;
        private final int total;
        private final AtomicInteger completed = new AtomicInteger();
        private final AtomicInteger failures = new AtomicInteger();
        private final AtomicInteger lastPercent = new AtomicInteger(-1);
        private final String[] lastLabel = {""};

        ProgressTracker(LauncherListener listener, int total) {
            this.listener = listener;
            this.total = Math.max(1, total);
        }

        void done(String label) {
            int value = completed.incrementAndGet();
            int percent = (int) Math.min(100, value * 100L / total);
            synchronized (this) {
                if (percent != lastPercent.get() || !label.equals(lastLabel[0])) {
                    lastPercent.set(percent);
                    lastLabel[0] = label;
                    listener.progress(Math.min(1.0, value / (double) total));
                    listener.status(f("Скачано %s из %s • %s", value, total, label));
                }
            }
        }

        void failed(FileTask task, Exception error) {
            failures.incrementAndGet();
            listener.log(f("Ошибка загрузки %s: %s", task.target().getFileName(), error.getMessage()));
            done(f("ошибка: %s", task.target().getFileName()));
        }

        int failures() {
            return failures.get();
        }
    }
}
