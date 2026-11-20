package org.launchermc;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Простая локализация RU/EN. Ключом служит русская строка (или формат-строка),
 * поэтому непереведённые сообщения безопасно остаются на русском.
 */
public final class Messages {

    private static final Map<String, String> EN = new LinkedHashMap<>();
    private static volatile boolean english;

    static {
        // окно и заголовок
        EN.put("Свернуть", "Minimize");
        EN.put("Закрыть", "Close");
        EN.put("неофициальный лаунчер", "unofficial launcher");
        EN.put("Выберите версию, скачайте файлы и запускайте игру. "
                        + "Прогресс загрузки и логи — прямо в этом окне.",
                "Pick a version, download the files and play. "
                        + "Download progress and logs are right in this window.");
        EN.put("Параллельная загрузка с докачкой", "Parallel downloads with resume");
        EN.put("Проверка файлов по SHA-1", "SHA-1 file verification");
        EN.put("Свой путь к java.exe в настройках", "Custom java.exe path in Settings");
        EN.put("Последний релиз", "Latest release");
        EN.put("Версий в манифесте", "Versions in manifest");
        EN.put("Профиль", "Profile");
        EN.put("Версия Minecraft", "Minecraft version");
        EN.put("Поиск версии: 1.21, 26.3, snapshot…", "Search versions: 1.21, 26.3, snapshot…");
        EN.put("Обновить список версий", "Refresh version list");
        EN.put("Выберите версию", "Select a version");
        EN.put("Снапшоты", "Snapshots");
        EN.put("Бета", "Beta");
        EN.put("Альфа", "Alpha");
        EN.put("версия не выбрана", "no version selected");
        EN.put("Играть", "Play");
        EN.put("Скачать", "Download");
        EN.put("Скачать и играть", "Download and play");
        EN.put("Отмена", "Cancel");
        EN.put("Отмена загрузки…", "Cancelling download…");
        EN.put("Загрузка списка версий…", "Loading version list…");
        EN.put("Скрыть лог", "Hide log");
        EN.put("Показать лог", "Show log");
        EN.put("LauncherMC 1.4 • логи загрузки и игры", "LauncherMC 1.4 • download and game logs");
        EN.put("Память: —", "Memory: —");
        EN.put("Память: %s МБ", "Memory: %s MB");
        EN.put("Логи", "Logs");
        EN.put("Открыть папку с логами", "Open logs folder");
        EN.put("Настройки", "Settings");

        // статусы и ошибки
        EN.put("Обновление списка версий…", "Refreshing version list…");
        EN.put("Не удалось загрузить список версий. Проверьте подключение к интернету — "
                        + "без него доступен только локальный кэш.",
                "Could not load the version list. Check your internet connection — "
                        + "only the local cache is available without a connection.");
        EN.put("Нет данных о версиях", "No version data");
        EN.put("Список версий загружен из локального кэша", "Version list loaded from local cache");
        EN.put("Список версий обновлён", "Version list updated");
        EN.put("Нет доступных версий", "No versions available");
        EN.put("Ничего не найдено", "Nothing found");
        EN.put("не скачана", "not installed");
        EN.put("не хватает файлов", "files missing");
        EN.put("✔ уже скачана", "✔ installed");
        EN.put("Исправить", "Repair");
        EN.put("Переустановить", "Reinstall");
        EN.put("Файлы версии неполные", "Version files are incomplete");
        EN.put("Версия ещё не скачана", "Version is not downloaded");
        EN.put("Проверить файлы Minecraft %s и докачать повреждённые, а затем сразу запустить игру?",
                "Verify Minecraft %s files, download damaged ones and start the game?");
        EN.put("Скачать Minecraft %s сейчас, а затем сразу запустить игру?",
                "Download Minecraft %s now and start the game?");
        EN.put("Сначала выберите версию Minecraft.", "Select a Minecraft version first.");
        EN.put("Ник должен состоять из 3–16 символов: A–Z, a–z, 0–9 или _.",
                "Nickname must be 3–16 characters: A–Z, a–z, 0–9 or _.");
        EN.put("Выберите версию Minecraft из списка.", "Choose a Minecraft version from the list.");
        EN.put("Проверка и ремонт %s…", "Checking and repairing %s…");
        EN.put("Скачивание %s…", "Downloading %s…");
        EN.put("Проверка и ремонт версии %s", "Checking and repairing %s");
        EN.put("Скачивание версии %s", "Downloading %s");
        EN.put("Версия %s готова к запуску", "Version %s is ready to launch");
        EN.put("Загрузка отменена", "Download cancelled");
        EN.put("Загрузка отменена пользователем", "Download cancelled by the user");
        EN.put("Не удалось скачать версию: %s", "Could not download the version: %s");
        EN.put("Ошибка загрузки", "Download error");
        EN.put("ОШИБКА загрузки: %s", "DOWNLOAD ERROR: %s");
        EN.put("Файлы версии неполные — нажмите «Скачать» или «Исправить», "
                        + "лаунчер проверит их по SHA-1.",
                "Version files are incomplete — press Download or Repair "
                        + "and the launcher will verify them by SHA-1.");
        EN.put("Подготовка к запуску %s…", "Preparing to launch %s…");
        EN.put("Запуск %s (ник: %s)", "Launching %s (nickname: %s)");
        EN.put("Minecraft %s запущен", "Minecraft %s is running");
        EN.put("Игра завершена", "Game closed");
        EN.put("Игра завершилась с кодом %s", "Game exited with code %s");
        EN.put("Не удалось запустить игру: %s", "Could not start the game: %s");
        EN.put("Ошибка запуска", "Launch error");
        EN.put("ОШИБКА запуска: %s", "LAUNCH ERROR: %s");

        // настройки
        EN.put("Настройки LauncherMC", "LauncherMC Settings");
        EN.put("Изменения вступят в силу сразу после сохранения",
                "Changes take effect after saving");
        EN.put("Оперативная память", "Memory");
        EN.put("%s МБ", "%s MB");
        EN.put("Всего ОЗУ: %s ГБ. Рекомендуется 4096 МБ для сборок с модами.",
                "Total RAM: %s GB. 4096 MB is recommended for modpacks.");
        EN.put("Java для запуска игры", "Java for the game");
        EN.put("Авто: встроенная Java 21", "Auto: bundled Java 21");
        EN.put("Обзор…", "Browse…");
        EN.put("Выберите java.exe или папку с Java", "Choose java.exe or a Java folder");
        EN.put("Пустое поле — лаунчер сам подберёт установленную Java "
                        + "под версию игры (8 для старых, 21/25 для новых) или возьмёт встроенную Java 21. "
                        + "Свой путь имеет приоритет, но для legacy-версий лаунчер может выбрать Java 8.",
                "Leave empty — the launcher picks an installed Java matching the game version "
                        + "(8 for old, 21/25 for new) or uses the bundled Java 21. "
                        + "A custom path has priority, but for legacy versions the launcher may pick Java 8.");
        EN.put("Показывать в списке версий", "Show in the version list");
        EN.put("Старые бета-версии", "Old beta versions");
        EN.put("Старые альфа-версии", "Old alpha versions");
        EN.put("Папка лаунчера", "Launcher folder");
        EN.put("Сохранить", "Save");
        EN.put("Настройки сохранены", "Settings saved");
        EN.put("Настройки сохранены — перезапустите лаунчер для смены языка",
                "Settings saved — restart the launcher to change the language");
        EN.put("Язык интерфейса", "Interface language");
        EN.put("Авто (системный)", "Auto (system)");
        EN.put("Изменения языка применяются после перезапуска лаунчера.",
                "Language changes take effect after restarting the launcher.");
        EN.put("Не удалось открыть папку: %s", "Could not open folder: %s");
        EN.put("Открытие папок не поддерживается на этой системе: %s",
                "Opening folders is not supported on this system: %s");

        // загрузка
        EN.put("Получение данных версии %s…", "Fetching version data for %s…");
        EN.put("Ассеты для этой версии не требуются", "No assets required for this version");
        EN.put("Проверка существующих файлов по SHA-1 (режим ремонта)…",
                "Verifying existing files by SHA-1 (repair mode)…");
        EN.put("Скачивание отменено", "Download cancelled");
        EN.put("Скачивание не завершилось за 30 минут", "Download did not finish within 30 minutes");
        EN.put("Не удалось скачать файлов: %s. Подробности — в окне лога.",
                "Failed to download files: %s. See the log window for details.");
        EN.put("Версия %s успешно скачана (файлов: %s)",
                "Version %s downloaded successfully (%s files)");
        EN.put("Не удалось обновить метаданные версии: %s",
                "Could not refresh version metadata: %s");
        EN.put("Используются локальные метаданные %s", "Using local metadata %s");
        EN.put("Не удалось получить метаданные версии %s. Проверьте подключение к интернету.",
                "Could not fetch metadata for version %s. Check your internet connection.");
        EN.put("В метаданных версии %s нет клиентского jar",
                "Version %s metadata has no client jar");
        EN.put("клиент", "client");
        EN.put("библиотека %s", "library %s");
        EN.put("индекс ассетов", "asset index");
        EN.put("конфиг логирования", "logging config");
        EN.put("ассет", "asset");
        EN.put("Повреждён индекс ассетов %s: %s", "Corrupted asset index %s: %s");
        EN.put("Подготовка legacy-ассетов…", "Preparing legacy assets…");
        EN.put("Legacy-ассеты подготовлены: %s файлов в %s",
                "Legacy assets prepared: %s files in %s");
        EN.put("Не удалось подготовить legacy-ассеты: %s",
                "Could not prepare legacy assets: %s");
        EN.put("Несовпадение SHA-1: %s", "SHA-1 mismatch: %s");
        EN.put("Не удалось скачать %s", "Could not download %s");
        EN.put("HTTP %s для %s", "HTTP %s for %s");
        EN.put("Не удалось прочитать %s", "Could not read %s");
        EN.put("Скачано %s из %s • %s", "Downloaded %s of %s • %s");
        EN.put("Ошибка загрузки %s: %s", "Download error %s: %s");
        EN.put("ошибка: %s", "error: %s");

        // запуск
        EN.put("Версия %s не скачана. Нажмите «Скачать».", "Version %s is not downloaded. Press Download.");
        EN.put("В метаданных версии нет mainClass", "Version metadata has no mainClass");
        EN.put("Для версии %s нужна Java %s или новее, а выбрана Java %s (%s). "
                        + "Укажите подходящую Java в настройках лаунчера.",
                "Version %s requires Java %s or newer, but Java %s is selected (%s). "
                        + "Choose a suitable Java in Settings.");
        EN.put("Для версии %s нужна Java %s, а выбрана Java %s (%s). "
                        + "Установите подходящую Java или укажите путь в настройках.",
                "Version %s requires Java %s, but Java %s is selected (%s). "
                        + "Install a suitable Java or set the path in Settings.");
        EN.put("Не удалось определить версию Java: %s", "Could not detect the Java version: %s");
        EN.put("Версия %s использует launchwrapper и требует Java 8, "
                        + "а выбрана Java %s. Установите Java 8 (например, "
                        + "Adoptium Temurin 8) — лаунчер найдёт её автоматически — "
                        + "или укажите путь в настройках.",
                "Version %s uses launchwrapper and requires Java 8, but Java %s is selected. "
                        + "Install Java 8 (for example Adoptium Temurin 8) — the launcher "
                        + "will find it automatically — or set the path in Settings.");
        EN.put("Выбрана 32-битная Java — память ограничена 1024 МБ, "
                        + "чтобы JVM не упала с OutOfMemoryError.",
                "32-bit Java selected — memory is limited to 1024 MB to avoid OutOfMemoryError.");
        EN.put("Запуск Minecraft %s…", "Launching Minecraft %s…");
        EN.put("Main class: %s, память: %s МБ", "Main class: %s, memory: %s MB");
        EN.put("Процесс Minecraft запущен (pid %s)", "Minecraft process started (pid %s)");
        EN.put("Minecraft завершился с кодом %s", "Minecraft exited with code %s");
        EN.put("Legacy-ассеты (virtual): %s", "Legacy assets (virtual): %s");
        EN.put("Legacy-ассеты (resources): %s", "Legacy assets (resources): %s");
        EN.put("Не удалось прочитать индекс ассетов: %s", "Could not read the asset index: %s");
        EN.put("Нативы распакованы: %s файлов", "Natives extracted: %s files");
        EN.put("Внимание: нативные библиотеки не найдены — игра может не запуститься",
                "Warning: native libraries not found — the game may not start");

        // JavaRuntime
        EN.put("Указанный путь к Java не найден: %s", "The configured Java path was not found: %s");
        EN.put("Выбранная %s не подходит: нужна Java %s — ищем подходящую установленную…",
                "The selected %s does not fit: Java %s is required — looking for an installed one…");
        EN.put("Java из настроек: %s (%s)", "Java from Settings: %s (%s)");
        EN.put("Для игры найдена %s: %s", "Java selected for the game: %s: %s");

        // служебные сообщения
        EN.put("LauncherMC запущен", "LauncherMC started");
        EN.put("LauncherMC закрывается", "LauncherMC is closing");
        EN.put("Конфигурационный файл не найден, создаём новый",
                "Configuration file not found, creating a new one");
        EN.put("Ошибка при загрузке конфигурации: %s", "Configuration load error: %s");
        EN.put("Ошибка при сохранении конфигурации: %s", "Configuration save error: %s");
        EN.put("Ошибка: ключ или значение не могут быть пустыми",
                "Error: key and value cannot be empty");
        EN.put("из кэша", "from cache");
        EN.put("Загружено версий: %s", "Versions loaded: %s");
        EN.put("Не удалось загрузить манифест %s: %s", "Could not load manifest %s: %s");
        EN.put("Использован локальный кэш version_manifest.json",
                "Using local version_manifest.json cache");
        EN.put("Не удалось прочитать локальный кэш версий: %s",
                "Could not read the local version cache: %s");
        EN.put("Не удалось сохранить кэш версий: %s", "Could not save the version cache: %s");
        EN.put("Ошибка при создании лог-файла: %s", "Could not create the log file: %s");
        EN.put("не удалось создать папку %s", "could not create directory %s");

        // автоскачивание Java
        EN.put("Java не найдена", "Java not found");
        EN.put("Для версии %s нужна Java %s. Скачать Adoptium Temurin JRE %s (~%s МБ) автоматически?",
                "Version %s requires Java %s. Download Adoptium Temurin JRE %s (~%s MB) automatically?");
        EN.put("Установка Java %s", "Installing Java %s");
        EN.put("Загрузка Java %s…", "Downloading Java %s…");
        EN.put("Скачивание Java %s: %s", "Downloading Java %s: %s");
        EN.put("Распаковка Java %s…", "Extracting Java %s…");
        EN.put("Java %s установлена в %s", "Java %s installed to %s");
        EN.put("Java %s установлена", "Java %s installed");
        EN.put("Не удалось установить Java: %s", "Could not install Java: %s");
        EN.put("Ошибка установки Java", "Java installation error");
        EN.put("ОШИБКА установки Java: %s", "JAVA INSTALL ERROR: %s");
        EN.put("Не удалось определить требуемую версию Java",
                "Could not determine the required Java version");
        EN.put("Автоскачивание Java не поддерживается на этой платформе",
                "Java auto-download is not supported on this platform");
        EN.put("В архиве Java не найден bin/java", "The Java archive has no bin/java");
        EN.put("HTTP %s при загрузке Java %s", "HTTP %s while downloading Java %s");
        EN.put("Загрузка Java %s: %s%%", "Downloading Java %s: %s%%");
        EN.put("Загрузка Java %s: %s МБ", "Downloading Java %s: %s MB");
        EN.put("Архив Java пустой или повреждён", "The Java archive is empty or corrupted");
        EN.put("Размер архива Java не совпал: %s из %s байт",
                "Java archive size mismatch: %s of %s bytes");
        EN.put("Архив Java %s скачан: %s МБ", "Java %s archive downloaded: %s MB");
        EN.put("Небезопасный путь в архиве Java: %s", "Unsafe path in the Java archive: %s");
        EN.put("Обрезанный tar-заголовок", "Truncated tar header");
        EN.put("Обрезанные данные tar", "Truncated tar data");
        EN.put("Слишком длинное имя в tar-архиве Java", "Tar entry name is too long");

        // Fabric
        EN.put("Загрузчик", "Loader");
        EN.put("Установить Fabric", "Install Fabric");
        EN.put("Установка Fabric для %s…", "Installing Fabric for %s…");
        EN.put("Установка Fabric для %s", "Installing Fabric for %s");
        EN.put("Fabric готов: %s", "Fabric ready: %s");
        EN.put("Не удалось установить Fabric: %s", "Could not install Fabric: %s");
        EN.put("Ошибка установки Fabric", "Fabric installation error");
        EN.put("ОШИБКА установки Fabric: %s", "FABRIC INSTALL ERROR: %s");
        EN.put("Fabric не поддерживает Minecraft %s", "Fabric does not support Minecraft %s");
        EN.put("Скачивание Minecraft %s для Fabric…", "Downloading Minecraft %s for Fabric…");
        EN.put("Установка Fabric %s…", "Installing Fabric %s…");
        EN.put("Fabric-профиль сохранён: %s", "Fabric profile saved: %s");
        EN.put("Fabric уже установлен: %s", "Fabric is already installed: %s");
        EN.put("Fabric установлен: %s", "Fabric installed: %s");

        // Microsoft
        EN.put("Microsoft: %s", "Microsoft: %s");
        EN.put("Войти через Microsoft", "Sign in with Microsoft");
        EN.put("Выйти", "Sign out");
        EN.put("Вход Microsoft", "Microsoft sign-in");
        EN.put("Вход через Microsoft", "Signing in with Microsoft");
        EN.put("Запрос кода Microsoft…", "Requesting a Microsoft code…");
        EN.put("Откройте %s и введите код:", "Open %s and enter the code:");
        EN.put("Открыть браузер", "Open browser");
        EN.put("Копировать код", "Copy code");
        EN.put("Код скопирован", "Code copied");
        EN.put("Ожидание подтверждения…", "Waiting for confirmation…");
        EN.put("Вход выполнен: %s", "Signed in: %s");
        EN.put("Не удалось войти Microsoft: %s", "Microsoft sign-in failed: %s");
        EN.put("Ошибка входа Microsoft", "Microsoft sign-in error");
        EN.put("ОШИБКА входа Microsoft: %s", "MICROSOFT SIGN-IN ERROR: %s");
        EN.put("Вы вышли из аккаунта Microsoft", "Signed out of Microsoft");
        EN.put("Не удалось открыть браузер: %s", "Could not open the browser: %s");
        EN.put("Открытие ссылок не поддерживается", "Opening links is not supported");
        EN.put("Обновление сессии Microsoft…", "Refreshing the Microsoft session…");
        EN.put("Сессия Microsoft истекла — войдите заново.",
                "The Microsoft session has expired — sign in again.");
        EN.put("Не удалось начать вход Microsoft", "Could not start Microsoft sign-in");
        EN.put("Microsoft не вернул access token", "Microsoft did not return an access token");
        EN.put("Код входа истёк. Начните вход Microsoft заново.",
                "The sign-in code has expired. Start Microsoft sign-in again.");
        EN.put("Вход Microsoft отклонён.", "Microsoft sign-in was declined.");
        EN.put("Не удалось получить токен Microsoft", "Could not get a Microsoft token");
        EN.put("Сессия Microsoft истекла. Войдите заново.",
                "The Microsoft session has expired. Sign in again.");
        EN.put("Xbox не вернул токен", "Xbox did not return a token");
        EN.put("Minecraft отклонил вход через Xbox",
                "Minecraft rejected the Xbox sign-in");
        EN.put("У аккаунта нет лицензии Minecraft Java Edition.",
                "This account does not own Minecraft Java Edition.");
        EN.put("У аккаунта нет профиля Minecraft Java.",
                "This account has no Minecraft Java profile.");
        EN.put("Не удалось получить профиль Minecraft", "Could not get the Minecraft profile");
        EN.put("Профиль Minecraft пустой", "The Minecraft profile is empty");
        EN.put("Xbox Live отклонил вход Microsoft", "Xbox Live rejected the Microsoft sign-in");
        EN.put("Xbox Live не вернул токен", "Xbox Live did not return a token");
        EN.put("У аккаунта нет профиля Xbox. Создайте его на xbox.com и войдите снова.",
                "This account has no Xbox profile. Create one on xbox.com and sign in again.");
        EN.put("Xbox Live недоступен в регионе аккаунта.",
                "Xbox Live is not available in the account's region.");
        EN.put("Аккаунту нужна проверка возраста Xbox Live.",
                "The account needs Xbox Live age verification.");
        EN.put("Детский аккаунт должен быть добавлен в семейную группу Microsoft.",
                "A child account must be added to a Microsoft family group.");
        EN.put("Xbox Live отклонил вход (XErr %s).", "Xbox Live rejected the sign-in (XErr %s).");
        EN.put("Xbox Live не выдал XSTS-токен", "Xbox Live did not issue an XSTS token");
        EN.put("Не удалось проверить лицензию Minecraft",
                "Could not verify the Minecraft licence");
        EN.put("Вход отменён", "Sign-in cancelled");
        EN.put("Microsoft account", "Microsoft account");
        EN.put("Microsoft", "Microsoft");
        EN.put("Не выполнен вход", "Not signed in");
        EN.put("Войдите через Microsoft, чтобы играть.", "Sign in with Microsoft to play.");
        EN.put("Нужен аккаунт Microsoft с лицензией Minecraft Java Edition.",
                "A Microsoft account with a Minecraft Java Edition licence is required.");
        EN.put("Войдите через Microsoft, выберите версию, "
                        + "скачайте файлы и запускайте игру. Для игры нужна лицензия Java Edition.",
                "Sign in with Microsoft, pick a version, download the files and play. "
                        + "A Java Edition licence is required.");
        EN.put("Mojang CDN · piston-meta · лицензия Java Edition",
                "Mojang CDN · piston-meta · Java Edition licence");
        EN.put("Нужен Client ID Microsoft", "Microsoft Client ID required");
        EN.put("Создайте своё приложение Microsoft Entra: "
                        + "App registrations → New registration, тип «Personal Microsoft accounts only», "
                        + "Authentication → Allow public client flows = Yes. "
                        + "Скопируйте Application (client) ID и укажите его в настройках лаунчера.",
                "Create your own Microsoft Entra application: App registrations → New registration, "
                        + "account type “Personal Microsoft accounts only”, Authentication → "
                        + "Allow public client flows = Yes. Copy the Application (client) ID "
                        + "and enter it in the launcher settings.");
        EN.put("Открыть Azure Portal", "Open Azure Portal");
        EN.put("Закрыть", "Close");
        EN.put("Создайте своё приложение Microsoft Entra "
                        + "(Personal Microsoft accounts only, Allow public client flows = Yes) "
                        + "и вставьте Application (client) ID. Без своего Client ID вход недоступен.",
                "Create your own Microsoft Entra application (Personal Microsoft accounts only, "
                        + "Allow public client flows = Yes) and paste the Application (client) ID. "
                        + "Sign-in is unavailable without your own Client ID.");
        EN.put("По умолчанию используется встроенный client ID "
                        + "приложения LauncherMC. Можно указать свой Application (client) ID "
                        + "из Microsoft Entra; это публичный идентификатор, не секрет.",
                "The built-in LauncherMC client ID is used by default. You can enter your own "
                        + "Application (client) ID from Microsoft Entra; it is a public identifier, "
                        + "not a secret.");
        EN.put("По умолчанию используется встроенный client ID "
                        + "приложения LauncherMC. Mojang принимает новые client ID только после "
                        + "allow-list: при ошибке Invalid app registration подайте заявку.",
                "The built-in LauncherMC client ID is used by default. Mojang accepts new "
                        + "client IDs only after allow-listing: if you see Invalid app registration, "
                        + "submit an application.");
        EN.put("Приложение не одобрено Mojang для Minecraft API. "
                        + "Новые client ID должны пройти allow-list; подайте заявку и повторите вход.",
                "The application is not approved by Mojang for the Minecraft API. "
                        + "New client IDs must be allow-listed; submit an application and try again.");
        EN.put("Mojang не одобрил приложение", "Mojang has not approved the application");
        EN.put("Minecraft API: client ID не в allow-list Mojang",
                "Minecraft API: client ID is not on Mojang's allow-list");
        EN.put("Minecraft Services принимает только client ID из allow-list Mojang. "
                        + "Новые приложения нужно подать на проверку; после одобрения вход заработает "
                        + "без изменений.",
                "Minecraft Services only accepts client IDs from Mojang's allow-list. "
                        + "New applications must be submitted for review; after approval sign-in "
                        + "will work without changes.");
        EN.put("Открыть страницу Mojang", "Open Mojang page");
    }

    private Messages() {
    }

    /**
     * Включает английский язык, если код начинается с {@code en}.
     */
    public static void setLanguage(String code) {
        english = code != null && code.toLowerCase(Locale.ROOT).startsWith("en");
    }

    public static boolean isEnglish() {
        return english;
    }

    /**
     * Переводит строку (ключ — русский текст). Неизвестные строки возвращаются как есть.
     */
    public static String t(String russian) {
        if (!english || russian == null) {
            return russian;
        }
        return EN.getOrDefault(russian, russian);
    }

    /**
     * Переводит формат-строку и подставляет аргументы.
     */
    public static String f(String russianFormat, Object... arguments) {
        return String.format(t(russianFormat), arguments);
    }

    /**
     * Для тестов: проверяет, что все английские ключи непустые.
     */
    static Map<String, String> translations() {
        return Map.copyOf(EN);
    }
}
