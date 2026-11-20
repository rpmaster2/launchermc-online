package org.launchermc;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;

/**
 * Пишет System.out одновременно в консоль и в logs/log_yyyyMMdd_HHmmss.txt,
 * добавляя к строкам файла метку времени.
 */
public class Logger {

    private static final int MAX_LOG_FILES = 20;
    private static final int MAX_LOG_AGE_DAYS = 30;

    private final PrintStream originalOut;
    private final PrintStream filePrintStream;
    private final PrintStream dualPrintStream;

    public Logger(String logDir) {
        PrintStream out = System.out;
        PrintStream file = null;
        PrintStream dual = null;
        try {
            File directory = new File(logDir);
            if (!directory.exists() && !directory.mkdirs()) {
                throw new IOException(Messages.f("не удалось создать папку %s",
                        directory.getAbsolutePath()));
            }
            rotate(directory);

            String timestamp = LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            File logFile = new File(directory, "log_" + timestamp + ".txt");
            file = new PrintStream(new FileOutputStream(logFile, true), true, StandardCharsets.UTF_8);

            dual = new PrintStream(
                    new TeeOutputStream(file, out, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                    true, StandardCharsets.UTF_8);
            System.setOut(dual);
        } catch (Exception e) {
            out.println(Messages.f("Ошибка при создании лог-файла: %s", e.getMessage()));
        }
        this.originalOut = out;
        this.filePrintStream = file;
        this.dualPrintStream = dual;
    }

    /**
     * Удаляет старые логи, чтобы папка не разрасталась.
     */
    private static void rotate(File directory) {
        File[] files = directory.listFiles((dir, name) ->
                name.startsWith("log_") && name.endsWith(".txt"));
        if (files == null || files.length <= MAX_LOG_FILES) {
            return;
        }
        Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed());
        long cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(MAX_LOG_AGE_DAYS);
        for (int i = MAX_LOG_FILES; i < files.length; i++) {
            if (files[i].lastModified() < cutoff) {
                // старые файлы удаляем, свежие сверх лимита оставляем на всякий случай
                // (удаление не критично для работы лаунчера)
                if (!files[i].delete()) {
                    // игнорируем
                }
            }
        }
    }

    public void close() {
        if (dualPrintStream != null) {
            dualPrintStream.flush();
        }
        System.setOut(originalOut);
        if (filePrintStream != null) {
            filePrintStream.close();
        }
    }

    /**
     * Дублирует вывод в файл и консоль; в файл строка пишется с меткой времени.
     *
     * Байты сначала накапливаются (System.out кодирует текст в UTF-8, так как
     * dual PrintStream создан с UTF-8), а затем строка декодируется обратно
     * и пишется в лог уже в UTF-8. Так кириллица не превращается в мусор.
     */
    private static final class TeeOutputStream extends OutputStream {
        private final OutputStream file;
        private final PrintStream console;
        private final DateTimeFormatter format;
        private final java.io.ByteArrayOutputStream line = new java.io.ByteArrayOutputStream(256);

        TeeOutputStream(OutputStream file, PrintStream console, DateTimeFormatter format) {
            this.file = file;
            this.console = console;
            this.format = format;
        }

        @Override
        public synchronized void write(int b) throws IOException {
            if (b == '\n') {
                flushLine();
            } else if (b != '\r') {
                line.write(b);
                if (line.size() > 16_384) {
                    flushLine();
                }
            }
        }

        @Override
        public synchronized void write(byte[] bytes, int offset, int length) throws IOException {
            for (int i = offset; i < offset + length; i++) {
                write(bytes[i]);
            }
        }

        private void flushLine() throws IOException {
            String text = line.toString(StandardCharsets.UTF_8);
            line.reset();
            String stamped = format.format(LocalDateTime.now()) + " - " + text + "\n";
            file.write(stamped.getBytes(StandardCharsets.UTF_8));
            file.flush();
            console.println(text);
        }

        @Override
        public synchronized void flush() throws IOException {
            if (line.size() > 0) {
                flushLine();
            }
            console.flush();
            file.flush();
        }

        @Override
        public synchronized void close() throws IOException {
            flush();
            file.close();
        }
    }
}
