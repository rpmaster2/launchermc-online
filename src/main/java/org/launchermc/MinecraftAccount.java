package org.launchermc;

/**
 * Аккаунт Microsoft/Xbox для запуска игры.
 */
public record MinecraftAccount(String name, String uuid, String accessToken,
                               String userType, String xuid, long expiresAt) {

    public static MinecraftAccount microsoft(String name, String uuid, String accessToken,
                                             String xuid, long expiresAt) {
        return new MinecraftAccount(name, uuid, accessToken, "msa", xuid == null ? "" : xuid,
                expiresAt);
    }

    public boolean microsoft() {
        return "msa".equals(userType);
    }

    public boolean expired() {
        return microsoft() && System.currentTimeMillis() >= expiresAt;
    }

    public MinecraftAccount withToken(String token, long newExpiresAt) {
        return new MinecraftAccount(name, uuid, token, userType, xuid, newExpiresAt);
    }
}
