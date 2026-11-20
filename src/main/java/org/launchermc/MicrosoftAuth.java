package org.launchermc;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import static org.launchermc.Messages.f;
import static org.launchermc.Messages.t;

/**
 * Вход через Microsoft (device code flow) и обмен токенов до Minecraft Java.
 *
 * <p>Схема: Microsoft OAuth → Xbox Live → XSTS → Minecraft Services →
 * проверка лицензии → профиль (имя и UUID).</p>
 */
public final class MicrosoftAuth {

    /**
     * Client ID собственного приложения LauncherMC в Microsoft Entra.
     * Public client: это не секрет, его можно встраивать в desktop-приложение.
     */
    public static final String DEFAULT_CLIENT_ID = "c04cda15-4029-4d8f-babc-b247767d9670";

    private static final String DEVICE_URL =
            "https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode";
    private static final String TOKEN_URL =
            "https://login.microsoftonline.com/consumers/oauth2/v2.0/token";
    private static final String SCOPE = "XboxLive.signin offline_access";
    private static final String XBL_URL = "https://user.auth.xboxlive.com/user/authenticate";
    private static final String XSTS_URL = "https://xsts.auth.xboxlive.com/xsts/authorize";
    private static final String MC_LOGIN_URL =
            "https://api.minecraftservices.com/authentication/login_with_xbox";
    private static final String ENTITLEMENTS_URL =
            "https://api.minecraftservices.com/entitlements/mcstore";
    private static final String PROFILE_URL = "https://api.minecraftservices.com/minecraft/profile";

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    public record DeviceCode(String userCode, String verificationUri, String deviceCode,
                             int expiresIn, int interval) {
    }

    public record Login(MinecraftAccount account, String refreshToken) {
    }

    public static final class AuthException extends Exception {
        private final boolean appRegistration;

        public AuthException(String message) {
            this(message, false);
        }

        public AuthException(String message, boolean appRegistration) {
            super(message);
            this.appRegistration = appRegistration;
        }

        public boolean appRegistration() {
            return appRegistration;
        }
    }

    private record Response(int status, String body) {
    }

    private MicrosoftAuth() {
    }

    /**
     * Запрашивает код для входа на microsoft.com/link.
     */
    public static DeviceCode requestDeviceCode(String clientId) throws Exception {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("client_id", clientId);
        form.put("scope", SCOPE);
        Response response = postForm(DEVICE_URL, form);
        if (response.status() != 200) {
            throw new AuthException(errorMessage(response, t("Не удалось начать вход Microsoft")));
        }
        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        return new DeviceCode(
                getString(json, "user_code"),
                getString(json, "verification_uri"),
                getString(json, "device_code"),
                getInt(json, "expires_in", 900),
                Math.max(1, getInt(json, "interval", 5))
        );
    }

    /**
     * Ждёт подтверждения кода и выполняет полный обмен токенов.
     */
    public static Login complete(DeviceCode code, String clientId,
                                 BooleanSupplier cancelled, Consumer<String> status)
            throws Exception {
        long deadline = System.currentTimeMillis() + code.expiresIn() * 1000L;
        int interval = code.interval();
        while (System.currentTimeMillis() < deadline) {
            sleep(interval * 1000L, cancelled);
            Map<String, String> form = new LinkedHashMap<>();
            form.put("grant_type", "urn:ietf:params:oauth:grant-type:device_code");
            form.put("client_id", clientId);
            form.put("device_code", code.deviceCode());
            Response response = postForm(TOKEN_URL, form);
            if (response.status() == 200) {
                JsonObject tokens = JsonParser.parseString(response.body()).getAsJsonObject();
                String accessToken = getString(tokens, "access_token");
                String refreshToken = getString(tokens, "refresh_token");
                if (accessToken.isBlank()) {
                    throw new AuthException(t("Microsoft не вернул access token"));
                }
                status.accept(t("Вход подтверждён, получаем профиль Minecraft…"));
                return exchange(accessToken, refreshToken);
            }
            JsonObject error = parse(response.body());
            String codeError = getString(error, "error");
            switch (codeError) {
                case "authorization_pending" -> {
                    // пользователь ещё вводит код
                }
                case "slow_down" -> interval += 5;
                case "expired_token" -> throw new AuthException(
                        t("Код входа истёк. Начните вход Microsoft заново."));
                case "authorization_declined" -> throw new AuthException(
                        t("Вход Microsoft отклонён."));
                default -> throw new AuthException(errorMessage(response,
                        t("Не удалось получить токен Microsoft")));
            }
        }
        throw new AuthException(t("Код входа истёк. Начните вход Microsoft заново."));
    }

    /**
     * Обновляет access token по refresh token и заново получает профиль.
     */
    public static Login refresh(String clientId, String refreshToken) throws Exception {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("grant_type", "refresh_token");
        form.put("client_id", clientId);
        form.put("refresh_token", refreshToken);
        form.put("scope", SCOPE);
        Response response = postForm(TOKEN_URL, form);
        if (response.status() != 200) {
            throw new AuthException(t("Сессия Microsoft истекла. Войдите заново."));
        }
        JsonObject tokens = JsonParser.parseString(response.body()).getAsJsonObject();
        String accessToken = getString(tokens, "access_token");
        String newRefresh = getString(tokens, "refresh_token");
        if (newRefresh.isBlank()) {
            newRefresh = refreshToken;
        }
        return exchange(accessToken, newRefresh);
    }

    private static Login exchange(String microsoftAccessToken, String refreshToken)
            throws Exception {
        String xblToken = xboxLive(microsoftAccessToken);
        JsonObject xsts = xsts(xblToken);
        String xstsToken = getString(xsts, "Token");
        String uhs = displayClaim(xsts, "uhs");
        String xuid = displayClaim(xsts, "xid");
        if (xstsToken.isBlank() || uhs.isBlank()) {
            throw new AuthException(t("Xbox не вернул токен"));
        }

        JsonObject body = new JsonObject();
        body.addProperty("identityToken", "XBL3.0 x=" + uhs + ";" + xstsToken);
        Response login = postJson(MC_LOGIN_URL, body, null);
        if (login.status() != 200) {
            if (login.body() != null && login.body().contains("Invalid app registration")) {
                throw new AuthException(t("Приложение не одобрено Mojang для Minecraft API. "
                        + "Новые client ID должны пройти allow-list; подайте заявку и повторите вход."),
                        true);
            }
            throw new AuthException(errorMessage(login,
                    t("Minecraft отклонил вход через Xbox")));
        }
        JsonObject minecraft = JsonParser.parseString(login.body()).getAsJsonObject();
        String minecraftToken = getString(minecraft, "access_token");
        long expiresIn = getLong(minecraft, "expires_in", 3600);

        if (!ownsMinecraft(minecraftToken)) {
            throw new AuthException(t("У аккаунта нет лицензии Minecraft Java Edition."));
        }

        Response profileResponse = get(PROFILE_URL, minecraftToken);
        if (profileResponse.status() == 404) {
            throw new AuthException(t("У аккаунта нет профиля Minecraft Java."));
        }
        if (profileResponse.status() != 200) {
            throw new AuthException(errorMessage(profileResponse,
                    t("Не удалось получить профиль Minecraft")));
        }
        JsonObject profile = JsonParser.parseString(profileResponse.body()).getAsJsonObject();
        String name = getString(profile, "name");
        String uuid = getString(profile, "id");
        if (name.isBlank() || uuid.isBlank()) {
            throw new AuthException(t("Профиль Minecraft пустой"));
        }

        MinecraftAccount account = MinecraftAccount.microsoft(name, uuid, minecraftToken, xuid,
                System.currentTimeMillis() + expiresIn * 1000L);
        return new Login(account, refreshToken);
    }

    private static String xboxLive(String microsoftAccessToken) throws Exception {
        JsonObject properties = new JsonObject();
        properties.addProperty("AuthMethod", "RPS");
        properties.addProperty("SiteName", "user.auth.xboxlive.com");
        properties.addProperty("RpsTicket", "d=" + microsoftAccessToken);
        JsonObject body = new JsonObject();
        body.add("Properties", properties);
        body.addProperty("RelyingParty", "http://auth.xboxlive.com");
        body.addProperty("TokenType", "JWT");
        Response response = postJson(XBL_URL, body, null);
        if (response.status() != 200) {
            throw new AuthException(errorMessage(response,
                    t("Xbox Live отклонил вход Microsoft")));
        }
        String token = getString(JsonParser.parseString(response.body()).getAsJsonObject(), "Token");
        if (token.isBlank()) {
            throw new AuthException(t("Xbox Live не вернул токен"));
        }
        return token;
    }

    private static JsonObject xsts(String xblToken) throws Exception {
        JsonObject properties = new JsonObject();
        properties.addProperty("SandboxId", "RETAIL");
        JsonArray tokens = new JsonArray();
        tokens.add(xblToken);
        properties.add("UserTokens", tokens);
        JsonObject body = new JsonObject();
        body.add("Properties", properties);
        body.addProperty("RelyingParty", "rp://api.minecraftservices.com/");
        body.addProperty("TokenType", "JWT");
        Response response = postJson(XSTS_URL, body, null);
        if (response.status() == 401) {
            JsonObject error = parse(response.body());
            long xerr = getLong(error, "XErr", 0);
            String message;
            if (xerr == 2148916233L) {
                message = t("У аккаунта нет профиля Xbox. Создайте его на xbox.com и войдите снова.");
            } else if (xerr == 2148916235L) {
                message = t("Xbox Live недоступен в регионе аккаунта.");
            } else if (xerr == 2148916236L || xerr == 2148916237L) {
                message = t("Аккаунту нужна проверка возраста Xbox Live.");
            } else if (xerr == 2148916238L) {
                message = t("Детский аккаунт должен быть добавлен в семейную группу Microsoft.");
            } else {
                message = f("Xbox Live отклонил вход (XErr %s).", xerr);
            }
            throw new AuthException(message);
        }
        if (response.status() != 200) {
            throw new AuthException(errorMessage(response,
                    t("Xbox Live не выдал XSTS-токен")));
        }
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }

    private static boolean ownsMinecraft(String token) throws Exception {
        Response response = get(ENTITLEMENTS_URL, token);
        if (response.status() != 200) {
            throw new AuthException(errorMessage(response,
                    t("Не удалось проверить лицензию Minecraft")));
        }
        JsonObject json = parse(response.body());
        JsonArray items = json.has("items") && json.get("items").isJsonArray()
                ? json.getAsJsonArray("items") : new JsonArray();
        boolean product = false;
        boolean game = false;
        for (JsonElement element : items) {
            if (!element.isJsonObject()) {
                continue;
            }
            String name = getString(element.getAsJsonObject(), "name");
            product |= "product_minecraft".equals(name);
            game |= "game_minecraft".equals(name);
        }
        return product && game;
    }

    private static String displayClaim(JsonObject json, String key) {
        if (!json.has("DisplayClaims") || !json.get("DisplayClaims").isJsonObject()) {
            return "";
        }
        JsonObject claims = json.getAsJsonObject("DisplayClaims");
        if (!claims.has("xui") || !claims.get("xui").isJsonArray()) {
            return "";
        }
        JsonArray xui = claims.getAsJsonArray("xui");
        if (xui.isEmpty() || !xui.get(0).isJsonObject()) {
            return "";
        }
        return getString(xui.get(0).getAsJsonObject(), key);
    }

    private static Response postForm(String url, Map<String, String> form) throws Exception {
        StringBuilder body = new StringBuilder();
        for (Map.Entry<String, String> entry : form.entrySet()) {
            if (body.length() > 0) {
                body.append('&');
            }
            body.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8));
            body.append('=');
            body.append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .header("User-Agent", "LauncherMC/1.4")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();
        return send(request);
    }

    private static Response postJson(String url, JsonObject body, String bearer) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("User-Agent", "LauncherMC/1.4")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8));
        if (bearer != null) {
            builder.header("Authorization", "Bearer " + bearer);
        }
        return send(builder.build());
    }

    private static Response get(String url, String bearer) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("Accept", "application/json")
                .header("User-Agent", "LauncherMC/1.4")
                .header("Authorization", "Bearer " + bearer)
                .GET()
                .build();
        return send(request);
    }

    private static Response send(HttpRequest request) throws Exception {
        HttpResponse<String> response = HTTP.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return new Response(response.statusCode(), response.body());
    }

    private static JsonObject parse(String body) {
        try {
            return JsonParser.parseString(body == null ? "{}" : body).getAsJsonObject();
        } catch (Exception e) {
            return new JsonObject();
        }
    }

    private static String errorMessage(Response response, String fallback) {
        JsonObject json = parse(response.body());
        String description = getString(json, "error_description");
        if (description.isBlank()) {
            description = getString(json, "errorMessage");
        }
        if (description.isBlank()) {
            description = getString(json, "error");
        }
        if (description.isBlank()) {
            return fallback + " (HTTP " + response.status() + ")";
        }
        return fallback + ": " + description;
    }

    private static void sleep(long millis, BooleanSupplier cancelled) throws Exception {
        long left = millis;
        while (left > 0) {
            if (cancelled.getAsBoolean()) {
                throw new AuthException(t("Вход отменён"));
            }
            long chunk = Math.min(500, left);
            Thread.sleep(chunk);
            left -= chunk;
        }
    }

    private static String getString(JsonObject json, String key) {
        return json != null && json.has(key) && json.get(key).isJsonPrimitive()
                ? json.get(key).getAsString() : "";
    }

    private static int getInt(JsonObject json, String key, int fallback) {
        try {
            return json != null && json.has(key) ? json.get(key).getAsInt() : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    private static long getLong(JsonObject json, String key, long fallback) {
        try {
            return json != null && json.has(key) ? json.get(key).getAsLong() : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }
}
