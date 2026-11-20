package org.launchermc;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.BooleanSupplier;

import static org.launchermc.Messages.f;
import static org.launchermc.Messages.t;

/**
 * Установка Fabric Loader через официальное meta API Fabric.
 *
 * <p>Fabric-профиль ссылается на ванильную версию через {@code inheritsFrom};
 * его библиотеки заданы Maven-координатами, поэтому {@link Download} умеет
 * скачивать и такие артефакты.</p>
 */
public final class FabricInstaller {

    private static final String META = "https://meta.fabricmc.net/v2";
    private static final int TIMEOUT_MS = 15_000;

    private FabricInstaller() {
    }

    /**
     * Возвращает последнюю stable-версию Fabric Loader для версии игры.
     */
    public static String latestLoader(String gameVersion) throws IOException {
        JsonArray versions = readJsonArray(
                META + "/versions/loader/" + gameVersion);
        String fallback = null;
        for (JsonElement element : versions) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject entry = element.getAsJsonObject();
            JsonObject loader = entry.has("loader") && entry.get("loader").isJsonObject()
                    ? entry.getAsJsonObject("loader") : null;
            if (loader == null || !loader.has("version")) {
                continue;
            }
            String version = loader.get("version").getAsString();
            if (fallback == null) {
                fallback = version;
            }
            boolean stable = loader.has("stable") && loader.get("stable").getAsBoolean();
            if (stable) {
                return version;
            }
        }
        if (fallback == null) {
            throw new IOException(f("Fabric не поддерживает Minecraft %s", gameVersion));
        }
        return fallback;
    }

    /**
     * Ставит Fabric для указанной ванильной версии и возвращает id профиля.
     * При необходимости сначала скачивает саму ванильную версию.
     */
    public static String install(String gameVersion, LauncherListener listener,
                                 BooleanSupplier cancelled) throws Exception {
        String loader = latestLoader(gameVersion);
        String id = "fabric-loader-" + loader + "-" + gameVersion;
        Path versionDir = Download.versionDir(id);

        if (Download.isComplete(id) && Files.isRegularFile(versionDir.resolve(id + ".json"))) {
            listener.log(f("Fabric уже установлен: %s", id));
            return id;
        }

        if (!Download.isComplete(gameVersion)) {
            listener.status(f("Скачивание Minecraft %s для Fabric…", gameVersion));
            new Download().download(gameVersion, listener,
                    Download.isInstalled(gameVersion), cancelled);
        }
        if (cancelled.getAsBoolean()) {
            throw new IOException(t("Загрузка отменена"));
        }

        listener.status(f("Установка Fabric %s…", loader));
        JsonObject profile = readJsonObject(META + "/versions/loader/"
                + gameVersion + "/" + loader + "/profile/json");
        profile.addProperty("id", id);
        profile.addProperty("inheritsFrom", gameVersion);
        if (!profile.has("type")) {
            profile.addProperty("type", "release");
        }

        Files.createDirectories(versionDir);
        Files.writeString(versionDir.resolve(id + ".json"), profile.toString(),
                StandardCharsets.UTF_8);
        listener.log(f("Fabric-профиль сохранён: %s", id));

        new Download().download(id, listener, false, cancelled);
        listener.log(f("Fabric установлен: %s", id));
        return id;
    }

    private static JsonObject readJsonObject(String url) throws IOException {
        return JsonParser.parseString(read(url)).getAsJsonObject();
    }

    private static JsonArray readJsonArray(String url) throws IOException {
        return JsonParser.parseString(read(url)).getAsJsonArray();
    }

    private static String read(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
        connection.setConnectTimeout(TIMEOUT_MS);
        connection.setReadTimeout(TIMEOUT_MS);
        connection.setRequestProperty("User-Agent", "LauncherMC/1.4");
        try {
            int code = connection.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                throw new IOException(f("HTTP %s для %s", code, url));
            }
            try (InputStream input = connection.getInputStream()) {
                return new String(input.readAllBytes(), StandardCharsets.UTF_8);
            }
        } finally {
            connection.disconnect();
        }
    }
}
