package org.launchermc;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;

/**
 * Хранит refresh-токен Microsoft между запусками.
 *
 * <p>Access token не сохраняется — он короткоживущий. Файл лежит рядом с
 * лаунчером; на POSIX-системах ему выставляются права 600. Это не шифрование:
 * refresh-токен — секрет, папку лаунчера не стоит публиковать.</p>
 */
public final class AccountStore {

    private static final Path FILE = Path.of("launcher_accounts.json");

    public record Saved(String refreshToken, String clientId, String name,
                        String uuid, String xuid) {
    }

    private AccountStore() {
    }

    public static Saved load() {
        if (!Files.isRegularFile(FILE)) {
            return null;
        }
        try {
            JsonObject json = JsonParser.parseString(
                    Files.readString(FILE, StandardCharsets.UTF_8)).getAsJsonObject();
            String refresh = string(json, "refresh_token");
            String name = string(json, "name");
            if (refresh.isBlank() || name.isBlank()) {
                return null;
            }
            return new Saved(refresh, string(json, "client_id"), name,
                    string(json, "uuid"), string(json, "xuid"));
        } catch (Exception e) {
            return null;
        }
    }

    public static void save(Saved saved) {
        try {
            JsonObject json = new JsonObject();
            json.addProperty("refresh_token", saved.refreshToken());
            json.addProperty("client_id", saved.clientId());
            json.addProperty("name", saved.name());
            json.addProperty("uuid", saved.uuid());
            json.addProperty("xuid", saved.xuid());
            Path tmp = Path.of(FILE + ".tmp");
            Files.writeString(tmp, json.toString(), StandardCharsets.UTF_8);
            Files.move(tmp, FILE, StandardCopyOption.REPLACE_EXISTING);
            restrictPermissions(FILE);
        } catch (IOException ignored) {
            // аккаунт останется в памяти до конца сеанса
        }
    }

    public static void clear() {
        try {
            Files.deleteIfExists(FILE);
        } catch (IOException ignored) {
            // ничего страшного
        }
    }

    public static boolean exists() {
        return Files.isRegularFile(FILE);
    }

    private static void restrictPermissions(Path file) {
        try {
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
        } catch (Exception ignored) {
            // Windows или ФС без POSIX
        }
    }

    private static String string(JsonObject json, String key) {
        return json.has(key) && json.get(key).isJsonPrimitive()
                ? json.get(key).getAsString() : "";
    }
}
