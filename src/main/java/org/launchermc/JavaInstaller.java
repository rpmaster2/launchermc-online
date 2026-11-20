package org.launchermc;

import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.launchermc.Messages.f;
import static org.launchermc.Messages.t;

/**
 * Скачивание и установка портативной JRE от Adoptium (Temurin).
 *
 * <p>Java кладётся рядом с лаунчером в {@code java/jre-<major>} и затем
 * находится {@link JavaRuntime} как обычная установленная Java. Поддерживаются
 * Windows (zip), Linux и macOS (tar.gz), x86/x64/ARM64.</p>
 */
public final class JavaInstaller {

    public static final Path JAVA_DIR = Path.of("java");
    private static final String API = "https://api.adoptium.net/v3/binary/latest/";
    private static final int CONNECT_TIMEOUT_SECONDS = 20;
    private static final int BUFFER = 64 * 1024;

    private JavaInstaller() {
    }

    /**
     * Каталог, куда ставится Java указанной major-версии.
     */
    public static Path installDir(int major) {
        return JAVA_DIR.resolve("jre-" + major);
    }

    public static boolean isSupported() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        return os.contains("win") || os.contains("mac") || os.contains("linux");
    }

    /**
     * URL архива Adoptium для текущей ОС/архитектуры.
     */
    public static String downloadUrl(int major) {
        String os = "linux";
        String name = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (name.contains("win")) {
            os = "windows";
        } else if (name.contains("mac")) {
            os = "mac";
        }
        String arch;
        if (RuleEvaluator.isArm()) {
            arch = "aarch64";
        } else if ("windows".equals(os)) {
            arch = RuleEvaluator.is64Bit() ? "x64" : "x86";
        } else {
            arch = RuleEvaluator.is64Bit() ? "x64" : "x86";
        }
        return API + major + "/ga/" + os + "/" + arch + "/jre/hotspot/normal/eclipse";
    }

    public static long estimatedSizeMb(int major) {
        return switch (major) {
            case 8 -> 40;
            case 11, 17 -> 45;
            default -> 55;
        };
    }

    /**
     * Скачивает и устанавливает JRE. Блокирующий метод.
     *
     * @return путь к java/javaw установленной JRE
     */
    public static Path install(int major, LauncherListener listener,
                               BooleanSupplier cancelled) throws Exception {
        if (major <= 0) {
            throw new IOException(t("Не удалось определить требуемую версию Java"));
        }
        if (!isSupported()) {
            throw new IOException(t("Автоскачивание Java не поддерживается на этой платформе"));
        }

        Files.createDirectories(JAVA_DIR);
        Path target = installDir(major);
        Path work = JAVA_DIR.resolve(".tmp-" + major);
        Path archive = JAVA_DIR.resolve("jre-" + major + (isWindows() ? ".zip" : ".tar.gz"));
        deleteRecursively(work);
        Files.createDirectories(work);

        try {
            listener.status(f("Загрузка Java %s…", major));
            listener.log(f("Скачивание Java %s: %s", major, downloadUrl(major)));
            downloadArchive(major, archive, listener, cancelled);
            if (cancelled.getAsBoolean()) {
                throw new IOException(t("Загрузка отменена"));
            }

            listener.status(f("Распаковка Java %s…", major));
            listener.progress(-1);
            if (isWindows()) {
                extractZip(archive, work);
            } else {
                extractTarGz(archive, work);
            }
            flattenIfNeeded(work);
            if (JavaRuntime.executableFor(work) == null) {
                throw new IOException(t("В архиве Java не найден bin/java"));
            }

            deleteRecursively(target);
            moveDirectory(work, target);
            JavaRuntime.invalidate();

            listener.progress(1);
            Path installed = JavaRuntime.executableFor(target);
            listener.log(f("Java %s установлена в %s", major, target.toAbsolutePath()));
            return installed != null ? installed : target;
        } finally {
            Files.deleteIfExists(archive);
            deleteRecursively(work);
        }
    }

    private static void downloadArchive(int major, Path archive, LauncherListener listener,
                                        BooleanSupplier cancelled) throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
                .build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(downloadUrl(major)))
                .header("User-Agent", "LauncherMC/1.4")
                .GET()
                .build();

        HttpResponse<InputStream> response = client.send(request,
                HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            throw new IOException(f("HTTP %s при загрузке Java %s", response.statusCode(), major));
        }
        long total = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
        long written = 0;
        long lastReport = 0;
        int lastPercent = -1;
        try (InputStream in = response.body();
             OutputStream out = Files.newOutputStream(archive)) {
            byte[] buffer = new byte[BUFFER];
            int read;
            while ((read = in.read(buffer)) != -1) {
                if (cancelled.getAsBoolean()) {
                    throw new IOException(t("Загрузка отменена"));
                }
                out.write(buffer, 0, read);
                written += read;
                long now = System.nanoTime();
                if (total > 0) {
                    int percent = (int) Math.min(100, written * 100 / total);
                    if (percent != lastPercent && (percent % 2 == 0 || now - lastReport > 500_000_000L)) {
                        lastPercent = percent;
                        lastReport = now;
                        listener.progress(written / (double) total);
                        listener.status(f("Загрузка Java %s: %s%%", major, percent));
                    }
                } else if (now - lastReport > 500_000_000L) {
                    lastReport = now;
                    listener.status(f("Загрузка Java %s: %s МБ", major,
                            written / (1024 * 1024)));
                    listener.progress(-1);
                }
            }
        }
        long size = Files.size(archive);
        if (size < 1024) {
            throw new IOException(t("Архив Java пустой или повреждён"));
        }
        if (total > 0 && size != total) {
            throw new IOException(f("Размер архива Java не совпал: %s из %s байт", size, total));
        }
        listener.log(f("Архив Java %s скачан: %s МБ", major, size / (1024 * 1024)));
    }

    /**
     * Распаковка zip (Windows). Первый компонент пути (корень архива) отбрасывается.
     */
    static void extractZip(Path archive, Path target) throws IOException {
        Files.createDirectories(target);
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = stripRoot(entry.getName());
                if (name.isBlank()) {
                    continue;
                }
                Path out = safeResolve(target, name);
                if (entry.isDirectory()) {
                    Files.createDirectories(out);
                } else {
                    Files.createDirectories(out.getParent());
                    try (OutputStream stream = Files.newOutputStream(out)) {
                        zip.transferTo(stream);
                    }
                }
            }
        }
    }

    /**
     * Распаковка tar.gz (Linux/macOS) с сохранением прав и символических ссылок.
     */
    static void extractTarGz(Path archive, Path target) throws IOException {
        Files.createDirectories(target);
        try (InputStream raw = Files.newInputStream(archive);
             GZIPInputStream gzip = new GZIPInputStream(raw, BUFFER);
             BufferedInputStream in = new BufferedInputStream(gzip, BUFFER)) {
            byte[] header = new byte[512];
            String longName = null;
            while (readBlock(in, header)) {
                if (isZero(header)) {
                    break;
                }
                String name = longName != null ? longName : readString(header, 0, 100);
                longName = null;
                String prefix = readString(header, 345, 155);
                if (!prefix.isBlank() && !name.startsWith("/")) {
                    name = prefix + "/" + name;
                }
                long size = parseOctal(header, 124, 12);
                int mode = (int) parseOctal(header, 100, 8);
                char type = (char) header[156];
                String link = readString(header, 157, 100);
                long padded = (size + 511) / 512 * 512;

                if (type == 'L') {
                    byte[] data = readData(in, size, padded);
                    longName = new String(data, StandardCharsets.UTF_8).replace("\0", "").trim();
                    continue;
                }
                if (type == 'x' || type == 'g' || type == 'K') {
                    skipFully(in, padded);
                    continue;
                }

                String relative = stripRoot(name);
                if (relative.isBlank()) {
                    skipFully(in, padded);
                    continue;
                }
                Path out = safeResolve(target, relative);
                switch (type) {
                    case '5' -> {
                        Files.createDirectories(out);
                        skipFully(in, padded);
                    }
                    case '2' -> {
                        Files.createDirectories(out.getParent());
                        Files.deleteIfExists(out);
                        try {
                            Files.createSymbolicLink(out, Path.of(link));
                        } catch (UnsupportedOperationException | IOException ignored) {
                            // на Windows/без прав символические ссылки не нужны
                        }
                        skipFully(in, padded);
                    }
                    case '0', 0 -> {
                        Files.createDirectories(out.getParent());
                        try (OutputStream stream = Files.newOutputStream(out)) {
                            copyExact(in, stream, size);
                        }
                        skipFully(in, padded - size);
                        applyPermissions(out, mode);
                    }
                    default -> skipFully(in, padded);
                }
            }
        }
    }

    private static void applyPermissions(Path file, int mode) {
        if (mode <= 0) {
            return;
        }
        try {
            Set<PosixFilePermission> permissions = PosixFilePermissions.fromString(modeString(mode));
            Files.setPosixFilePermissions(file, permissions);
        } catch (Exception ignored) {
            // Windows или ФС без POSIX — ничего страшного
        }
    }

    private static String modeString(int mode) {
        StringBuilder result = new StringBuilder(9);
        int[] masks = {0400, 0200, 0100, 0040, 0020, 0010, 0004, 0002, 0001};
        char[] chars = {'r', 'w', 'x', 'r', 'w', 'x', 'r', 'w', 'x'};
        for (int i = 0; i < masks.length; i++) {
            result.append((mode & masks[i]) != 0 ? chars[i] : '-');
        }
        return result.toString();
    }

    /**
     * Если архив содержал вложенный корневой каталог (не отбросился), поднимает его.
     */
    private static void flattenIfNeeded(Path work) throws IOException {
        if (Files.isDirectory(work.resolve("bin"))) {
            return;
        }
        List<Path> directories = new ArrayList<>();
        try (var stream = Files.list(work)) {
            stream.filter(Files::isDirectory).forEach(directories::add);
        }
        if (directories.size() != 1) {
            return;
        }
        Path nested = directories.get(0);
        Path staging = work.resolveSibling(work.getFileName() + ".flat");
        deleteRecursively(staging);
        Files.move(nested, staging, StandardCopyOption.REPLACE_EXISTING);
        deleteRecursively(work);
        Files.move(staging, work, StandardCopyOption.REPLACE_EXISTING);
    }

    private static String stripRoot(String name) {
        String value = name.replace('\\', '/');
        while (value.startsWith("./")) {
            value = value.substring(2);
        }
        int slash = value.indexOf('/');
        if (slash >= 0) {
            value = value.substring(slash + 1);
        }
        return value;
    }

    private static Path safeResolve(Path target, String name) throws IOException {
        Path resolved = target.resolve(name).normalize();
        if (!resolved.startsWith(target.normalize())) {
            throw new IOException("Небезопасный путь в архиве Java: " + name);
        }
        return resolved;
    }

    private static boolean readBlock(InputStream in, byte[] block) throws IOException {
        int offset = 0;
        while (offset < block.length) {
            int read = in.read(block, offset, block.length - offset);
            if (read == -1) {
                if (offset == 0) {
                    return false;
                }
                throw new EOFException("Обрезанный tar-заголовок");
            }
            offset += read;
        }
        return true;
    }

    private static byte[] readData(InputStream in, long size, long padded) throws IOException {
        if (size > 64 * 1024 * 1024) {
            throw new IOException("Слишком длинное имя в tar-архиве Java");
        }
        byte[] data = new byte[(int) size];
        int offset = 0;
        while (offset < data.length) {
            int read = in.read(data, offset, data.length - offset);
            if (read == -1) {
                throw new EOFException("Обрезанные данные tar");
            }
            offset += read;
        }
        skipFully(in, padded - size);
        return data;
    }

    private static void copyExact(InputStream in, OutputStream out, long size) throws IOException {
        byte[] buffer = new byte[BUFFER];
        long left = size;
        while (left > 0) {
            int read = in.read(buffer, 0, (int) Math.min(buffer.length, left));
            if (read == -1) {
                throw new EOFException("Обрезанные данные tar");
            }
            out.write(buffer, 0, read);
            left -= read;
        }
    }

    private static void skipFully(InputStream in, long count) throws IOException {
        long left = count;
        while (left > 0) {
            long skipped = in.skip(left);
            if (skipped <= 0) {
                if (in.read() == -1) {
                    return;
                }
                left--;
            } else {
                left -= skipped;
            }
        }
    }

    private static String readString(byte[] block, int offset, int length) {
        int end = offset;
        int limit = Math.min(block.length, offset + length);
        while (end < limit && block[end] != 0) {
            end++;
        }
        return new String(block, offset, end - offset, StandardCharsets.UTF_8).trim();
    }

    private static long parseOctal(byte[] block, int offset, int length) {
        long value = 0;
        int end = Math.min(block.length, offset + length);
        int i = offset;
        while (i < end && (block[i] == ' ' || block[i] == 0)) {
            i++;
        }
        for (; i < end; i++) {
            byte b = block[i];
            if (b < '0' || b > '7') {
                break;
            }
            value = value * 8 + (b - '0');
        }
        return value;
    }

    private static boolean isZero(byte[] block) {
        for (byte b : block) {
            if (b != 0) {
                return false;
            }
        }
        return true;
    }

    private static void moveDirectory(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException moveFailed) {
            copyDirectory(source, target);
            deleteRecursively(source);
        }
    }

    private static void copyDirectory(Path source, Path target) throws IOException {
        try (var stream = Files.walk(source)) {
            for (Path path : stream.toList()) {
                Path destination = target.resolve(source.relativize(path).toString());
                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination);
                } else {
                    Files.createDirectories(destination.getParent());
                    Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try (var stream = Files.walk(path)) {
            List<Path> paths = stream.sorted((a, b) -> b.getNameCount() - a.getNameCount()).toList();
            for (Path item : paths) {
                try {
                    Files.deleteIfExists(item);
                } catch (IOException ignored) {
                    // занятый файл — пропускаем
                }
            }
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }
}
