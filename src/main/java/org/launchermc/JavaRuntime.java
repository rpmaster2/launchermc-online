package org.launchermc;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Поиск установленной Java и определение её версии/разрядности.
 *
 * <p>Лаунчеру нужна не «какая-нибудь» Java, а подходящая версии игры:
 * старые версии запускаются через launchwrapper и требуют Java 8,
 * Minecraft 26.x требует Java 25. Поэтому перед запуском мы ищем
 * установленную Java с нужной major-версией, а не просто берём встроенную.</p>
 */
public final class JavaRuntime {

    /** Результат определения версии Java. */
    public record Info(int major, int bits, boolean detected) {
        public static final Info UNKNOWN = new Info(-1, 0, false);

        public String display() {
            if (!detected) {
                return "Java ?";
            }
            return bits > 0 ? "Java " + major + " (" + bits + "-bit)" : "Java " + major;
        }
    }

    private static final Pattern VERSION_LINE =
            Pattern.compile("\\bversion\\s+\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern LEADING_NUMBER = Pattern.compile("^(\\d+)");
    private static final Pattern PROPERTY_LINE =
            Pattern.compile("^\\s*([\\w.]+)\\s*=\\s*(.+?)\\s*$");

    private static final Map<String, Info> CACHE = new ConcurrentHashMap<>();
    private static volatile List<Path> discoveredHomes;

    private JavaRuntime() {
    }

    /**
     * Сбрасывает кэш после установки новой Java.
     */
    public static void invalidate() {
        CACHE.clear();
        discoveredHomes = null;
    }

    /**
     * Возвращает java/javaw для запуска игры.
     *
     * <p>Порядок: явно указанный путь → установленная Java нужной версии →
     * встроенная Java (java.home) → java из PATH.</p>
     */
    public static Path find(String configuredPath, int requiredMajor, LauncherListener listener) {
        return find(configuredPath, requiredMajor, false, listener);
    }

    /**
     * @param exactOnly для launchwrapper нужна именно Java 8, более новые не подходят
     */
    public static Path find(String configuredPath, int requiredMajor, boolean exactOnly,
                            LauncherListener listener) {
        if (configuredPath != null && !configuredPath.isBlank()) {
            Path candidate = executableFor(Path.of(configuredPath.trim()));
            if (candidate == null) {
                listener.log(Messages.f("Указанный путь к Java не найден: %s", configuredPath));
            } else {
                Info info = detect(candidate);
                boolean mismatch = requiredMajor > 0 && info.major() > 0
                        && (info.major() < requiredMajor
                        || (exactOnly && info.major() != requiredMajor));
                if (mismatch) {
                    listener.log(Messages.f("Выбранная %s не подходит: нужна Java %s — "
                            + "ищем подходящую установленную…", info.display(), requiredMajor));
                    Path better = findMatching(requiredMajor, exactOnly, listener);
                    if (better != null) {
                        return better;
                    }
                } else {
                    listener.log(Messages.f("Java из настроек: %s (%s)", candidate, info.display()));
                }
                return candidate;
            }
        }

        Path matching = requiredMajor > 0 ? findMatching(requiredMajor, exactOnly, listener) : null;
        if (matching != null) {
            return matching;
        }

        String home = System.getProperty("java.home");
        if (home != null && !home.isBlank()) {
            Path embedded = executableFor(Path.of(home));
            if (embedded != null) {
                return embedded;
            }
        }
        return Path.of(isWindows() ? "javaw.exe" : "java");
    }

    /**
     * Ищет установленную Java с подходящей major-версией.
     * Предпочитает точное совпадение, иначе — ближайшую более новую.
     */
    private static Path findMatching(int requiredMajor, boolean exactOnly,
                                     LauncherListener listener) {
        Path exact = null;
        Path newer = null;
        int newerMajor = Integer.MAX_VALUE;
        for (Path home : candidates()) {
            Path executable = executableFor(home);
            if (executable == null) {
                continue;
            }
            Info info = detect(executable);
            if (!info.detected() || info.major() <= 0) {
                continue;
            }
            if (info.major() == requiredMajor) {
                if (exact == null || prefer(executable, exact)) {
                    exact = executable;
                }
            } else if (!exactOnly && info.major() > requiredMajor && info.major() < newerMajor) {
                newer = executable;
                newerMajor = info.major();
            }
        }
        Path chosen = exact != null ? exact : newer;
        if (chosen != null && listener != null) {
            Info info = detect(chosen);
            listener.log(Messages.f("Для игры найдена %s: %s", info.display(), chosen));
        }
        return chosen;
    }

    /**
     * Возвращает установленную Java нужной версии или {@code null}.
     * Без резервного варианта «встроенная Java» — используется для проверки
     * перед автоскачиванием.
     */
    public static Path findInstalled(int requiredMajor, boolean exactOnly) {
        return requiredMajor > 0 ? findMatching(requiredMajor, exactOnly, null) : null;
    }

    /**
     * Есть ли уже подходящая Java (настройка, установленные/скачанные JRE,
     * встроенная Java или PATH).
     */
    public static boolean hasSuitable(String configuredPath, int requiredMajor,
                                      boolean exactOnly) {
        if (requiredMajor <= 0) {
            return true;
        }
        if (configuredPath != null && !configuredPath.isBlank()) {
            Path executable = executableFor(Path.of(configuredPath.trim()));
            if (executable != null && fits(detect(executable), requiredMajor, exactOnly)) {
                return true;
            }
        }
        return findInstalled(requiredMajor, exactOnly) != null;
    }

    private static boolean fits(Info info, int requiredMajor, boolean exactOnly) {
        if (info == null || !info.detected() || info.major() <= 0) {
            return false;
        }
        return exactOnly ? info.major() == requiredMajor : info.major() >= requiredMajor;
    }

    private static boolean prefer(Path candidate, Path current) {
        // при равной версии выбираем 64-битную
        Info a = detect(candidate);
        Info b = detect(current);
        return a.bits() == 64 && b.bits() != 64;
    }

    /**
     * Определяет major-версию и разрядность Java.
     * Сначала читает файл {@code release} рядом с bin (быстро, без запуска процесса),
     * затем запускает {@code -XshowSettings:properties -version}.
     */
    public static Info detect(Path path) {
        Path executable = executableFor(path);
        if (executable == null) {
            return Info.UNKNOWN;
        }
        String key = executable.toAbsolutePath().normalize().toString();
        Info cached = CACHE.get(key);
        if (cached != null) {
            return cached;
        }

        Info fromRelease = readReleaseFile(executable);
        int major = fromRelease != null ? fromRelease.major() : -1;
        int bits = fromRelease != null ? fromRelease.bits() : 0;

        if (major <= 0 || bits <= 0) {
            Info probed = probe(executable);
            if (major <= 0) {
                major = probed.major();
            }
            if (bits <= 0) {
                bits = probed.bits();
            }
        }

        Info info = major > 0 ? new Info(major, bits, true) : Info.UNKNOWN;
        CACHE.put(key, info);
        return info;
    }

    private static Info readReleaseFile(Path executable) {
        Path bin = executable.getParent();
        Path home = bin == null ? null : bin.getParent();
        if (home == null) {
            return null;
        }
        Path release = home.resolve("release");
        if (!Files.isRegularFile(release)) {
            return null;
        }
        String version = null;
        String arch = "";
        try (BufferedReader reader = Files.newBufferedReader(release, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                int eq = line.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                String name = line.substring(0, eq).trim();
                String value = unquote(line.substring(eq + 1).trim());
                if ("JAVA_VERSION".equals(name)) {
                    version = value;
                } else if ("OS_ARCH".equals(name)) {
                    arch = value;
                }
            }
        } catch (IOException e) {
            return null;
        }
        if (version == null) {
            return null;
        }
        int major = parseMajor(version);
        return major > 0 ? new Info(major, bitsFromArch(arch), true) : null;
    }

    private static Info probe(Path executable) {
        // javaw.exe не всегда пишет в перенаправленный поток — для проверки
        // используем java.exe из той же папки.
        Path launcher = executable;
        if (isWindows() && "javaw.exe".equalsIgnoreCase(executable.getFileName().toString())) {
            Path console = executable.resolveSibling("java.exe");
            if (Files.isRegularFile(console)) {
                launcher = console;
            }
        }
        Info info = run(launcher, "-XshowSettings:properties", "-version");
        if (info.detected()) {
            return info;
        }
        return run(launcher, "-version");
    }

    private static Info run(Path executable, String... arguments) {
        List<String> command = new ArrayList<>();
        command.add(executable.toString());
        command.addAll(List.of(arguments));
        Process process = null;
        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.redirectErrorStream(true);
            process = builder.start();
            StringBuilder output = new StringBuilder();
            try (InputStream input = process.getInputStream();
                 BufferedReader reader = new BufferedReader(
                         new InputStreamReader(input, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append('\n');
                }
            }
            if (!process.waitFor(20, TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
            return parseProbeOutput(output.toString());
        } catch (Exception e) {
            return Info.UNKNOWN;
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    /**
     * Разбирает вывод {@code java -XshowSettings:properties -version}
     * (или обычного {@code java -version}) — отдельный метод для тестов.
     */
    static Info parseProbeOutput(String output) {
        if (output == null || output.isBlank()) {
            return Info.UNKNOWN;
        }
        String version = null;
        int bits = 0;
        for (String line : output.split("\\R")) {
            Matcher property = PROPERTY_LINE.matcher(line);
            if (property.matches()) {
                String name = property.group(1);
                String value = property.group(2).trim();
                if ("java.version".equals(name)) {
                    version = value;
                } else if ("sun.arch.data.model".equals(name)) {
                    bits = parseBits(value);
                }
            }
            if (version == null) {
                Matcher matcher = VERSION_LINE.matcher(line);
                if (matcher.find()) {
                    version = matcher.group(1);
                }
            }
            if (bits == 0) {
                bits = bitsFromRuntimeLine(line);
            }
        }
        int major = parseMajor(version);
        return major > 0 ? new Info(major, bits, true) : Info.UNKNOWN;
    }

    /**
     * Возвращает major-версию из строки вида {@code 1.8.0_491}, {@code 21.0.13.1+2},
     * {@code 25-ea}. Для неизвестного формата — -1.
     */
    public static int parseMajor(String raw) {
        if (raw == null) {
            return -1;
        }
        String value = unquote(raw.trim());
        if (value.isEmpty()) {
            return -1;
        }
        if (value.startsWith("1.")) {
            value = value.substring(2);
        }
        Matcher matcher = LEADING_NUMBER.matcher(value);
        if (!matcher.find()) {
            return -1;
        }
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static int parseBits(String value) {
        try {
            int bits = Integer.parseInt(value.trim());
            return bits == 32 || bits == 64 ? bits : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static int bitsFromRuntimeLine(String line) {
        String lower = line.toLowerCase(Locale.ROOT);
        if (lower.contains("64-bit") || lower.contains("server vm")) {
            return 64;
        }
        if (lower.contains("32-bit") || lower.contains("client vm")) {
            return 32;
        }
        return 0;
    }

    private static int bitsFromArch(String arch) {
        if (arch == null || arch.isBlank()) {
            return 0;
        }
        String value = arch.toLowerCase(Locale.ROOT);
        if (value.contains("64") || value.contains("amd64")) {
            return 64;
        }
        if (value.contains("x86") || value.contains("i386") || value.contains("i486")
                || value.contains("i586") || value.contains("i686")) {
            return 32;
        }
        return 0;
    }

    private static String unquote(String value) {
        if (value != null && value.length() >= 2
                && value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1);
        }
        return value == null ? "" : value;
    }

    /**
     * Возвращает путь к java/javaw. Принимает и папку Java, и конкретный exe.
     * На Windows предпочитает javaw.exe (без лишнего консольного окна).
     */
    public static Path executableFor(Path path) {
        if (path == null) {
            return null;
        }
        if (Files.isRegularFile(path)) {
            return path;
        }
        Path bin = path.resolve("bin");
        Path base = Files.isDirectory(bin) ? bin : path;
        if (isWindows()) {
            Path javaw = base.resolve("javaw.exe");
            if (Files.isRegularFile(javaw)) {
                return javaw;
            }
            Path java = base.resolve("java.exe");
            if (Files.isRegularFile(java)) {
                return java;
            }
        } else {
            Path java = base.resolve("java");
            if (Files.isExecutable(java)) {
                return java;
            }
        }
        return null;
    }

    /**
     * Список известных папок с Java (JAVA_HOME и типовые каталоги установки).
     */
    private static List<Path> candidates() {
        List<Path> cached = discoveredHomes;
        if (cached != null) {
            return cached;
        }
        synchronized (JavaRuntime.class) {
            if (discoveredHomes != null) {
                return discoveredHomes;
            }
            Set<Path> homes = new LinkedHashSet<>();
            addHome(homes, System.getenv("JAVA_HOME"));
            String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);

            if (os.contains("win")) {
                List<String> roots = new ArrayList<>();
                addIfPresent(roots, System.getenv("ProgramFiles"));
                addIfPresent(roots, System.getenv("ProgramFiles(x86)"));
                String localAppData = System.getenv("LOCALAPPDATA");
                if (localAppData != null && !localAppData.isBlank()) {
                    roots.add(localAppData + "\\Programs");
                }
                List<String> vendors = List.of(
                        "Java", "Eclipse Adoptium", "AdoptOpenJDK", "Microsoft", "Zulu",
                        "BellSoft", "Amazon Corretto", "Semeru", "Temurin", "OpenJDK", "Oracle"
                );
                for (String root : roots) {
                    for (String vendor : vendors) {
                        addChildHomes(homes, Path.of(root, vendor));
                    }
                }
            } else if (os.contains("mac")) {
                addChildHomes(homes, Path.of("/Library/Java/JavaVirtualMachines"));
                addChildHomes(homes, Path.of("/System/Library/Java/JavaVirtualMachines"));
                addChildHomes(homes, Path.of("/opt/homebrew/opt"));
                addHomesUnder(homes, Path.of("/Library/Java/JavaVirtualMachines"), "Contents/Home");
            } else {
                addChildHomes(homes, Path.of("/usr/lib/jvm"));
                addChildHomes(homes, Path.of("/usr/java"));
            }

            String current = System.getProperty("java.home");
            addHome(homes, current);
            // портативные JRE, скачанные самим лаунчером
            addChildHomes(homes, JavaInstaller.JAVA_DIR.toAbsolutePath());
            addPathHomes(homes);
            discoveredHomes = List.copyOf(homes);
            return discoveredHomes;
        }
    }

    /**
     * Java может быть прописана только в PATH (например, shim Oracle или
     * распакованная сборка). Добавляем найденные там исполняемые файлы.
     */
    private static void addPathHomes(Set<Path> homes) {
        String path = System.getenv("PATH");
        if (path == null || path.isBlank()) {
            return;
        }
        for (String entry : path.split(java.io.File.pathSeparator)) {
            if (entry.isBlank()) {
                continue;
            }
            try {
                Path dir = Path.of(entry);
                Path java = dir.resolve(isWindows() ? "java.exe" : "java");
                if (Files.isRegularFile(java)) {
                    homes.add(java.toAbsolutePath().normalize());
                }
            } catch (Exception ignored) {
                // некорректная запись PATH — пропускаем
            }
        }
    }

    private static void addIfPresent(List<String> list, String value) {
        if (value != null && !value.isBlank()) {
            list.add(value);
        }
    }

    private static void addHome(Set<Path> homes, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        Path path = Path.of(value);
        if (Files.isDirectory(path)) {
            homes.add(path.toAbsolutePath().normalize());
        }
    }

    private static void addChildHomes(Set<Path> homes, Path base) {
        if (!Files.isDirectory(base)) {
            return;
        }
        try (var stream = Files.list(base)) {
            stream.filter(Files::isDirectory)
                    .sorted()
                    .forEach(path -> homes.add(path.toAbsolutePath().normalize()));
        } catch (IOException ignored) {
            // нет доступа — просто пропускаем каталог
        }
    }

    private static void addHomesUnder(Set<Path> homes, Path base, String suffix) {
        if (!Files.isDirectory(base)) {
            return;
        }
        try (var stream = Files.list(base)) {
            stream.filter(Files::isDirectory)
                    .map(path -> path.resolve(suffix))
                    .filter(Files::isDirectory)
                    .forEach(path -> homes.add(path.toAbsolutePath().normalize()));
        } catch (IOException ignored) {
            // нет доступа — просто пропускаем каталог
        }
    }

    public static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }
}
