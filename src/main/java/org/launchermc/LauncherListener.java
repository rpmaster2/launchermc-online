package org.launchermc;

/**
 * Колбэки для фоновых задач лаунчера: скачивание и запуск игры.
 */
public interface LauncherListener {

    /**
     * Текстовый статус текущего шага.
     */
    void status(String message);

    /**
     * Прогресс от 0.0 до 1.0; отрицательное значение — «неопределённый» прогресс.
     */
    void progress(double value);

    /**
     * Строка лога (попадает в окно лога и в файл).
     */
    void log(String message);

    /**
     * Игра успешно стартовала (процесс запущен).
     */
    default void gameStarted() {
    }
}
