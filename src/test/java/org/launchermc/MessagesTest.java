package org.launchermc;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessagesTest {

    @AfterEach
    void reset() {
        Messages.setLanguage("ru");
    }

    @Test
    void englishTranslatesKnownKeys() {
        Messages.setLanguage("en");
        assertEquals("Play", Messages.t("Играть"));
        assertEquals("Version 1.21 is ready to launch",
                Messages.f("Версия %s готова к запуску", "1.21"));
    }

    @Test
    void unknownKeysFallBackToRussian() {
        Messages.setLanguage("en");
        assertEquals("Неизвестная строка", Messages.t("Неизвестная строка"));
    }

    @Test
    void russianModeKeepsSourceText() {
        Messages.setLanguage("ru");
        assertEquals("Играть", Messages.t("Играть"));
        assertFalse(Messages.isEnglish());
    }

    @Test
    void allTranslationsAreNonEmpty() {
        Map<String, String> translations = Messages.translations();
        assertFalse(translations.isEmpty());
        translations.forEach((key, value) -> {
            assertFalse(key.isBlank(), "blank key");
            assertFalse(value.isBlank(), "blank translation for: " + key);
        });
    }

    @Test
    void languageCodeIsCaseInsensitive() {
        Messages.setLanguage("EN");
        assertTrue(Messages.isEnglish());
        Messages.setLanguage("en-US");
        assertTrue(Messages.isEnglish());
        Messages.setLanguage("auto");
        assertFalse(Messages.isEnglish());
    }
}
