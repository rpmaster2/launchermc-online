package org.launchermc;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * Простой конфиг лаунчера (launcher_config.ini).
 * Файл лежит рядом с лаунчером — сборка остаётся переносимой.
 */
public class ConfigManager {

    private static final String CONFIG_FILE = "launcher_config.ini";
    private final Properties properties = new Properties();

    public ConfigManager() {
        load();
        ensureDefaults();
    }

    public synchronized void load() {
        File configFile = new File(CONFIG_FILE);
        if (!configFile.exists()) {
            System.out.println(Messages.t("Конфигурационный файл не найден, создаём новый"));
            return;
        }
        try (FileInputStream fis = new FileInputStream(configFile)) {
            properties.load(fis);
        } catch (IOException e) {
            System.out.println(Messages.f("Ошибка при загрузке конфигурации: %s", e.getMessage()));
        }
    }

    private void ensureDefaults() {
        boolean changed = false;
        changed |= putIfAbsent("version", "");
        changed |= putIfAbsent("allocatedMemory", "2048");
        changed |= putIfAbsent("showSnapshots", "false");
        changed |= putIfAbsent("showBeta", "true");
        changed |= putIfAbsent("showAlpha", "true");
        changed |= putIfAbsent("javaPath", "");
        changed |= putIfAbsent("language", "auto");
        changed |= putIfAbsent("loader", "vanilla");
        changed |= putIfAbsent("microsoftClientId", MicrosoftAuth.DEFAULT_CLIENT_ID);
        if (changed) {
            saveToDisk();
        }
    }

    private boolean putIfAbsent(String key, String value) {
        if (properties.containsKey(key)) {
            return false;
        }
        properties.setProperty(key, value);
        return true;
    }

    private synchronized void saveToDisk() {
        try (FileOutputStream fos = new FileOutputStream(CONFIG_FILE)) {
            properties.store(fos, "LauncherMC configuration");
        } catch (IOException e) {
            System.out.println(Messages.f("Ошибка при сохранении конфигурации: %s", e.getMessage()));
        }
    }

    public synchronized void save(String key, String value) {
        if (key == null || value == null) {
            System.out.println(Messages.t("Ошибка: ключ или значение не могут быть пустыми"));
            return;
        }
        properties.setProperty(key, value);
        saveToDisk();
    }

    public void save(String key, boolean value) {
        save(key, Boolean.toString(value));
    }

    public void save(String key, int value) {
        save(key, Integer.toString(value));
    }

    public synchronized String getString(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        String value = properties.getProperty(key);
        return value == null ? defaultValue : Boolean.parseBoolean(value.trim());
    }

    public int getInt(String key, int defaultValue) {
        String value = properties.getProperty(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public String getVersion() {
        return getString("version", "");
    }

    public int getAllocatedMemory() {
        int value = getInt("allocatedMemory", 2048);
        return value < 512 ? 2048 : value;
    }

    public void saveAllocatedMemory(int memory) {
        save("allocatedMemory", Math.max(512, memory));
    }

    /**
     * Путь к java/javaw; пустая строка — автовыбор (встроенная JRE).
     */
    public String getJavaPath() {
        return getString("javaPath", "");
    }

    public void saveJavaPath(String path) {
        save("javaPath", path == null ? "" : path.trim());
    }

    /**
     * Язык интерфейса: auto / ru / en.
     */
    public String getLanguage() {
        return getString("language", "auto");
    }

    public void saveLanguage(String code) {
        save("language", code == null || code.isBlank() ? "auto" : code.trim());
    }

    /**
     * Загрузчик: vanilla или fabric.
     */
    public String getLoader() {
        return getString("loader", "vanilla");
    }

    public void saveLoader(String loader) {
        save("loader", loader == null || loader.isBlank() ? "vanilla" : loader.trim());
    }

    public String getMicrosoftClientId() {
        String value = getString("microsoftClientId", MicrosoftAuth.DEFAULT_CLIENT_ID);
        return value == null || value.isBlank() ? MicrosoftAuth.DEFAULT_CLIENT_ID : value.trim();
    }

    public void saveMicrosoftClientId(String clientId) {
        save("microsoftClientId", clientId == null || clientId.isBlank()
                ? MicrosoftAuth.DEFAULT_CLIENT_ID : clientId.trim());
    }

    public boolean isShowSnapshots() {
        return getBoolean("showSnapshots", false);
    }

    public boolean isShowBeta() {
        return getBoolean("showBeta", true);
    }

    public boolean isShowAlpha() {
        return getBoolean("showAlpha", true);
    }
}
