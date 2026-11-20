package org.launchermc;

/**
 * Описание одной версии Minecraft из version_manifest_v2.json.
 */
public class VersionInfo {

    private final String id;
    private final String type;
    private final String url;
    private final String releaseTime;

    public VersionInfo(String id, String type, String url, String releaseTime) {
        this.id = id;
        this.type = type;
        this.url = url;
        this.releaseTime = releaseTime;
    }

    public String getId() {
        return id;
    }

    /**
     * Тип версии в терминах Mojang: release, snapshot, old_beta, old_alpha.
     */
    public String getType() {
        return type;
    }

    public String getUrl() {
        return url;
    }

    public String getReleaseTime() {
        return releaseTime;
    }

    /**
     * Человекочитаемый тип версии для интерфейса.
     */
    public String getDisplayType() {
        return switch (type == null ? "" : type) {
            case "release" -> "Release";
            case "snapshot" -> "Snapshot";
            case "old_beta" -> "Beta";
            case "old_alpha" -> "Alpha";
            default -> type == null || type.isBlank() ? "Unknown" : type;
        };
    }

    /**
     * CSS-класс бейджа типа версии.
     */
    public String getTypeStyleClass() {
        return switch (type == null ? "" : type) {
            case "release" -> "type-release";
            case "snapshot" -> "type-snapshot";
            case "old_beta" -> "type-beta";
            case "old_alpha" -> "type-alpha";
            default -> "type-unknown";
        };
    }

    @Override
    public String toString() {
        return id;
    }
}
