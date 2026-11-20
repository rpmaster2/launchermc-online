package org.launchermc;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Загружает список версий Minecraft.
 *
 * Основной источник — piston-meta.mojang.com (version_manifest_v2.json),
 * резервный — launchermeta.mojang.com. Если сети нет, используется
 * локальный кэш version_manifest.json.
 */
public class LoadAvailableVersion {

    /** Актуальный эндпоинт Mojang (содержит sha1 и size). */
    public static final String MANIFEST_V2 =
            "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
    /** Резервное зеркало того же v2-манифеста. */
    public static final String MANIFEST_V2_FALLBACK =
            "https://launchermeta.mojang.com/mc/game/version_manifest_v2.json";
    /** Совсем старый эндпоинт — оставлен на случай проблем с v2. */
    public static final String MANIFEST_LEGACY =
            "https://launchermeta.mojang.com/mc/game/version_manifest.json";

    private static final Path LOCAL_FILE = Path.of("version_manifest.json");

    private final int connectTimeoutMs;
    private final int readTimeoutMs;

    private volatile String latestRelease = "";
    private volatile String latestSnapshot = "";
    private volatile boolean fromCache;

    private static List<VersionInfo> cachedVersions = Collections.emptyList();

    public LoadAvailableVersion() {
        this(15000, 30000);
    }

    public LoadAvailableVersion(int connectTimeoutMs, int readTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
    }

    /**
     * Загружает (или отдаёт закэшированный) список версий.
     * Никогда не возвращает null.
     */
    public static List<VersionInfo> getVersions() {
        return cachedVersions;
    }

    /**
     * Ищет версию по id в уже загруженном списке.
     */
    public static VersionInfo findCached(String versionId) {
        for (VersionInfo info : cachedVersions) {
            if (info.getId().equals(versionId)) {
                return info;
            }
        }
        return null;
    }

    /**
     * Загружает список версий, при неудаче пробует зеркала и локальный кэш.
     */
    public List<VersionInfo> getAvailableVersions() {
        List<VersionInfo> versions = new ArrayList<>();
        String source = null;

        for (String url : new String[]{MANIFEST_V2, MANIFEST_V2_FALLBACK, MANIFEST_LEGACY}) {
            try {
                JsonObject manifest = readJson(fromUrl(url));
                if (manifest == null || !manifest.has("versions")) {
                    continue;
                }
                fillLatest(manifest);
                parseVersions(manifest, versions);
                source = url;
                writeCache(manifest);
                break;
            } catch (Exception e) {
                System.out.println(Messages.f("Не удалось загрузить манифест %s: %s", url, e.getMessage()));
            }
        }

        if (versions.isEmpty()) {
            versions = readCache();
            fromCache = !versions.isEmpty();
        }

        cachedVersions = Collections.unmodifiableList(versions);
        System.out.println(Messages.f("Загружено версий: %s", versions.size())
                + (source != null ? " (" + source + ")"
                : fromCache ? " (" + Messages.t("из кэша") + ")" : ""));
        return cachedVersions;
    }

    public String getLatestRelease() {
        return latestRelease;
    }

    public String getLatestSnapshot() {
        return latestSnapshot;
    }

    public boolean isFromCache() {
        return fromCache;
    }

    private void fillLatest(JsonObject manifest) {
        if (manifest.has("latest") && manifest.get("latest").isJsonObject()) {
            JsonObject latest = manifest.getAsJsonObject("latest");
            latestRelease = latest.has("release") ? latest.get("release").getAsString() : "";
            latestSnapshot = latest.has("snapshot") ? latest.get("snapshot").getAsString() : "";
        }
    }

    private void parseVersions(JsonObject manifest, List<VersionInfo> versions) {
        JsonArray array = manifest.getAsJsonArray("versions");
        for (int i = 0; i < array.size(); i++) {
            JsonObject object = array.get(i).getAsJsonObject();
            String id = object.has("id") ? object.get("id").getAsString() : null;
            if (id == null) {
                continue;
            }
            versions.add(new VersionInfo(
                    id,
                    object.has("type") ? object.get("type").getAsString() : "",
                    object.has("url") ? object.get("url").getAsString() : "",
                    object.has("releaseTime") ? object.get("releaseTime").getAsString() : ""
            ));
        }
    }

    private List<VersionInfo> readCache() {
        try {
            if (!Files.exists(LOCAL_FILE)) {
                return new ArrayList<>();
            }
            JsonObject manifest = readJson(Files.readString(LOCAL_FILE, StandardCharsets.UTF_8));
            if (manifest == null) {
                return new ArrayList<>();
            }
            fillLatest(manifest);
            List<VersionInfo> versions = new ArrayList<>();
            parseVersions(manifest, versions);
            System.out.println(Messages.t("Использован локальный кэш version_manifest.json"));
            return versions;
        } catch (Exception e) {
            System.out.println(Messages.f("Не удалось прочитать локальный кэш версий: %s", e.getMessage()));
            return new ArrayList<>();
        }
    }

    private void writeCache(JsonObject manifest) {
        try {
            Path tmp = Path.of(LOCAL_FILE + ".tmp");
            Files.writeString(tmp, manifest.toString(), StandardCharsets.UTF_8);
            Files.move(tmp, LOCAL_FILE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            System.out.println(Messages.f("Не удалось сохранить кэш версий: %s", e.getMessage()));
        }
    }

    private String fromUrl(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(connectTimeoutMs);
        connection.setReadTimeout(readTimeoutMs);
        connection.setRequestProperty("User-Agent", "LauncherMC/1.4");
        try {
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                throw new IOException(Messages.f("HTTP %s для %s", connection.getResponseCode(), url));
            }
            try (var stream = connection.getInputStream()) {
                return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            }
        } finally {
            connection.disconnect();
        }
    }

    private static JsonObject readJson(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return JsonParser.parseString(text).getAsJsonObject();
    }
}
