package org.launchermc;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.StringConverter;
import oshi.SystemInfo;
import oshi.hardware.GlobalMemory;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.launchermc.Messages.f;
import static org.launchermc.Messages.t;

/**
 * Главное окно LauncherMC: выбор версии, скачивание с прогрессом,
 * запуск игры, настройки и живой лог.
 */
public class App extends Application {

    private static final DateTimeFormatter LOG_TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private static Logger logger;

    private ConfigManager config;
    private Stage stage;
    private String styleSheet;

    private final List<VersionInfo> allVersions = new ArrayList<>();

    private TextField searchField;
    private ComboBox<VersionInfo> versionSelector;
    private ComboBox<String> loaderBox;
    private CheckBox snapshotsBox;
    private CheckBox betaBox;
    private CheckBox alphaBox;
    private Button playButton;
    private Button downloadButton;
    private Button cancelButton;
    private Button refreshButton;
    private Button settingsButton;
    private ProgressBar progressBar;
    private Label statusLabel;
    private Label memoryLabel;
    private Label versionsLabel;
    private Label latestLabel;
    private Label installLabel;
    private Label errorLabel;
    private Label accountLabel;
    private Button accountButton;
    private TextArea consoleArea;
    private VBox consoleBox;
    private Button consoleToggle;

    private String latestRelease = "";
    private String latestSnapshot = "";
    private boolean consoleShown = true;
    private boolean busy = false;
    private boolean gameRunning = false;
    private boolean downloading = false;
    private volatile boolean cancelRequested = false;
    private volatile boolean loginCancelled = false;
    private volatile MinecraftAccount account;
    private volatile AccountStore.Saved savedAccount;
    private Stage loginDialog;

    private double dragX;
    private double dragY;

    @Override
    public void start(Stage primaryStage) {
        Messages.setLanguage(resolveLanguage(null));
        config = new ConfigManager();
        Messages.setLanguage(resolveLanguage(config.getLanguage()));
        savedAccount = AccountStore.load();
        if (savedAccount != null) {
            account = MinecraftAccount.microsoft(savedAccount.name(), savedAccount.uuid(),
                    "", savedAccount.xuid(), 0);
        }
        logger = new Logger("logs");
        System.out.println(t("LauncherMC запущен"));

        this.stage = primaryStage;
        this.styleSheet = resource("/style.css") != null
                ? getClass().getResource("/style.css").toExternalForm()
                : null;

        BorderPane root = new BorderPane();
        root.getStyleClass().add("app-root");
        root.setTop(createTitleBar());
        root.setCenter(createContent());
        root.setBottom(createStatusBar());

        Scene scene = new Scene(root, windowWidth(), windowHeight());
        if (styleSheet != null) {
            scene.getStylesheets().add(styleSheet);
        }

        primaryStage.setTitle("LauncherMC");
        primaryStage.initStyle(StageStyle.UNDECORATED);
        primaryStage.setResizable(false);
        primaryStage.getIcons().add(loadImage("/images/icon.png"));
        primaryStage.setScene(scene);
        primaryStage.setOnCloseRequest(event -> closeApp());
        primaryStage.show();
        primaryStage.centerOnScreen();

        restoreState();
        loadVersions();
    }

    // ------------------------------------------------------------------
    //  Разметка
    // ------------------------------------------------------------------

    private Node createTitleBar() {
        HBox bar = new HBox(10);
        bar.getStyleClass().add("title-bar");
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(10, 14, 10, 16));

        ImageView logo = icon("/images/icon.png", 22);
        Label name = new Label("LauncherMC");
        name.getStyleClass().add("title-text");
        Label version = new Label("v1.4");
        version.getStyleClass().add("version-chip");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button minimize = windowButton("—");
        minimize.setTooltip(new Tooltip(t("Свернуть")));
        minimize.setOnAction(e -> stage.setIconified(true));

        Button close = windowButton("✕");
        close.getStyleClass().add("window-close");
        close.setTooltip(new Tooltip(t("Закрыть")));
        close.setOnAction(e -> closeApp());

        bar.getChildren().addAll(logo, name, version, spacer, minimize, close);

        bar.setOnMousePressed(event -> {
            if (!isButtonTarget(event)) {
                dragX = event.getSceneX();
                dragY = event.getSceneY();
            }
        });
        bar.setOnMouseDragged(event -> {
            if (!isButtonTarget(event)) {
                stage.setX(event.getScreenX() - dragX);
                stage.setY(event.getScreenY() - dragY);
            }
        });
        return bar;
    }

    private Node createContent() {
        HBox content = new HBox(20);
        content.getStyleClass().add("content");
        content.setPadding(new Insets(14, 20, 10, 20));

        VBox hero = createHero();
        VBox card = createCard();
        HBox.setHgrow(card, Priority.ALWAYS);

        content.getChildren().addAll(hero, card);
        return content;
    }

    private VBox createHero() {
        VBox hero = new VBox(12);
        hero.getStyleClass().add("hero");
        hero.setPrefWidth(318);
        hero.setMinWidth(318);
        hero.setMaxWidth(318);
        hero.setPadding(new Insets(26));

        ImageView logo = icon("/images/icon.png", 84);

        Label title = new Label("Minecraft");
        title.getStyleClass().add("hero-title");
        Label subtitle = new Label(t("неофициальный лаунчер"));
        subtitle.getStyleClass().add("hero-subtitle");

        HBox chips = new HBox(8, chip(t("Microsoft account")), chip("Java 21+"));
        chips.setAlignment(Pos.CENTER_LEFT);

        Label description = new Label(t("Войдите через Microsoft, выберите версию, "
                + "скачайте файлы и запускайте игру. Для игры нужна лицензия Java Edition."));
        description.getStyleClass().add("hint");
        description.setWrapText(true);
        description.setMaxWidth(266);

        VBox bullets = new VBox(6,
                bullet(t("Параллельная загрузка с докачкой")),
                bullet(t("Проверка файлов по SHA-1")),
                bullet(t("Свой путь к java.exe в настройках")));
        VBox.setMargin(bullets, new Insets(6, 0, 0, 0));

        Region grow = new Region();
        VBox.setVgrow(grow, Priority.ALWAYS);

        Label releaseCaption = caption(t("Последний релиз"));
        latestLabel = new Label("—");
        latestLabel.getStyleClass().add("stat-value");

        Label versionsCaption = caption(t("Версий в манифесте"));
        versionsLabel = new Label("—");
        versionsLabel.getStyleClass().add("stat-value");

        HBox stats = new HBox(32,
                new VBox(2, releaseCaption, latestLabel),
                new VBox(2, versionsCaption, versionsLabel));

        Label footnote = new Label(t("Mojang CDN · piston-meta · лицензия Java Edition"));
        footnote.getStyleClass().add("footnote");
        footnote.setWrapText(true);
        footnote.setMaxWidth(266);

        hero.getChildren().addAll(logo, title, subtitle, chips, description, bullets, grow, stats, footnote);
        return hero;
    }

    private VBox createCard() {
        VBox card = new VBox(10);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(22, 24, 20, 24));

        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label(t("Профиль"));
        title.getStyleClass().add("section-title");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        accountLabel = chip(t("Не выполнен вход"));
        header.getChildren().addAll(title, spacer, accountLabel);

        accountButton = new Button(t("Войти через Microsoft"));
        accountButton.getStyleClass().add("button-secondary");
        accountButton.setOnAction(e -> onAccountAction());
        HBox accountRow = new HBox(8, accountButton);
        accountRow.setAlignment(Pos.CENTER_LEFT);
        Label accountHint = new Label(t("Нужен аккаунт Microsoft с лицензией Minecraft Java Edition."));
        accountHint.getStyleClass().add("hint");

        Label versionTitle = new Label(t("Версия Minecraft"));
        versionTitle.getStyleClass().add("section-title");
        VBox.setMargin(versionTitle, new Insets(6, 0, 0, 0));

        searchField = new TextField();
        searchField.setPromptText(t("Поиск версии: 1.21, 26.3, snapshot…"));
        searchField.getStyleClass().add("text-field");
        searchField.textProperty().addListener((obs, old, value) -> updateVersionList());

        refreshButton = new Button("⟳");
        refreshButton.getStyleClass().add("button-icon");
        refreshButton.setTooltip(new Tooltip(t("Обновить список версий")));
        refreshButton.setOnAction(e -> loadVersions());

        HBox searchRow = new HBox(8, searchField, refreshButton);
        HBox.setHgrow(searchField, Priority.ALWAYS);

        versionSelector = new ComboBox<>();
        versionSelector.setPromptText(t("Выберите версию"));
        versionSelector.setMaxWidth(Double.MAX_VALUE);
        versionSelector.setVisibleRowCount(12);
        versionSelector.getStyleClass().add("version-selector");
        versionSelector.setCellFactory(list -> versionCell());
        versionSelector.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(VersionInfo item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getId());
            }
        });
        versionSelector.valueProperty().addListener((obs, old, value) -> {
            updateButtons();
            updateInstallHint();
        });

        Label loaderCaption = new Label(t("Загрузчик"));
        loaderCaption.getStyleClass().add("section-title");
        loaderBox = new ComboBox<>();
        loaderBox.getItems().addAll("vanilla", "fabric");
        loaderBox.setValue("fabric".equals(config.getLoader()) ? "fabric" : "vanilla");
        loaderBox.setMaxWidth(Double.MAX_VALUE);
        loaderBox.getStyleClass().add("version-selector");
        loaderBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(String code) {
                return loaderName(code);
            }

            @Override
            public String fromString(String text) {
                return "vanilla";
            }
        });
        loaderBox.valueProperty().addListener((obs, old, value) -> {
            config.saveLoader(value);
            updateInstallHint();
        });
        HBox loaderRow = new HBox(10, loaderCaption, loaderBox);
        loaderRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(loaderBox, Priority.ALWAYS);

        snapshotsBox = filterBox(t("Снапшоты"), config.isShowSnapshots(), "showSnapshots");
        betaBox = filterBox(t("Бета"), config.isShowBeta(), "showBeta");
        alphaBox = filterBox(t("Альфа"), config.isShowAlpha(), "showAlpha");

        installLabel = new Label(t("версия не выбрана"));
        installLabel.getStyleClass().add("install-label");

        Region filterSpacer = new Region();
        HBox.setHgrow(filterSpacer, Priority.ALWAYS);

        HBox filters = new HBox(12, snapshotsBox, betaBox, alphaBox, filterSpacer, installLabel);
        filters.setAlignment(Pos.CENTER_LEFT);

        errorLabel = new Label();
        errorLabel.getStyleClass().add("error-label");
        errorLabel.setWrapText(true);
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);

        playButton = new Button(t("Играть"));
        playButton.getStyleClass().add("button-primary");
        playButton.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(playButton, Priority.ALWAYS);
        playButton.setOnAction(e -> onPlay());

        downloadButton = new Button(t("Скачать"));
        downloadButton.getStyleClass().add("button-secondary");
        downloadButton.setOnAction(e -> onDownload());

        cancelButton = new Button(t("Отмена"));
        cancelButton.getStyleClass().add("button-secondary");
        cancelButton.setVisible(false);
        cancelButton.setManaged(false);
        cancelButton.setOnAction(e -> {
            cancelRequested = true;
            cancelButton.setDisable(true);
            setStatus(t("Отмена загрузки…"));
        });

        HBox actions = new HBox(10, playButton, downloadButton, cancelButton);
        actions.setAlignment(Pos.CENTER_LEFT);

        progressBar = new ProgressBar(0);
        progressBar.getStyleClass().add("progress-bar");
        progressBar.setMaxWidth(Double.MAX_VALUE);
        progressBar.setVisible(false);
        progressBar.setManaged(false);

        statusLabel = new Label(t("Загрузка списка версий…"));
        statusLabel.getStyleClass().add("status-text");
        statusLabel.setWrapText(true);

        consoleToggle = new Button(t("Скрыть лог"));
        consoleToggle.getStyleClass().add("button-ghost");
        consoleToggle.setOnAction(e -> toggleConsole());
        HBox consoleHeader = new HBox(consoleToggle);
        consoleHeader.setAlignment(Pos.CENTER_RIGHT);

        consoleArea = new TextArea();
        consoleArea.setEditable(false);
        consoleArea.setWrapText(false);
        consoleArea.getStyleClass().add("console-area");
        consoleArea.setPrefRowCount(5);
        appendLog(t("LauncherMC 1.4 • логи загрузки и игры"));

        consoleBox = new VBox(consoleArea);
        consoleBox.setVisible(true);
        consoleBox.setManaged(true);
        VBox.setVgrow(consoleArea, Priority.ALWAYS);

        card.getChildren().addAll(
                header,
                accountRow,
                accountHint,
                versionTitle,
                searchRow,
                versionSelector,
                loaderRow,
                filters,
                errorLabel,
                progressBar,
                actions,
                statusLabel,
                consoleHeader,
                consoleBox
        );
        return card;
    }

    private Node createStatusBar() {
        HBox bar = new HBox(14);
        bar.getStyleClass().add("status-bar");
        bar.setPadding(new Insets(10, 20, 10, 20));
        bar.setAlignment(Pos.CENTER_LEFT);

        Label dot = new Label("●");
        dot.getStyleClass().add("status-dot");
        Label caption = new Label("LauncherMC 1.4");
        caption.getStyleClass().add("status-caption");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        memoryLabel = new Label(t("Память: —"));
        memoryLabel.getStyleClass().add("status-caption");
        updateMemoryLabel();

        Button logFolder = new Button(t("Логи"));
        logFolder.getStyleClass().add("button-ghost");
        logFolder.setTooltip(new Tooltip(t("Открыть папку с логами")));
        logFolder.setOnAction(e -> openFolder(Path.of("logs")));

        settingsButton = new Button(t("Настройки"), icon("/images/settings.png", 16));
        settingsButton.getStyleClass().add("button-ghost");
        settingsButton.setOnAction(e -> openSettings());

        bar.getChildren().addAll(dot, caption, spacer, memoryLabel, logFolder, settingsButton);
        return bar;
    }

    // ------------------------------------------------------------------
    //  Состояние и версии
    // ------------------------------------------------------------------

    private void restoreState() {
        updateAccountUi();
        applyFiltersToUi();
        updateButtons();
        updateInstallHint();
    }

    private void applyFiltersToUi() {
        snapshotsBox.setSelected(config.isShowSnapshots());
        betaBox.setSelected(config.isShowBeta());
        alphaBox.setSelected(config.isShowAlpha());
    }

    private void loadVersions() {
        busy = true;
        updateButtons();
        hideError();
        setStatus(t("Обновление списка версий…"));
        setProgress(-1);

        Thread thread = new Thread(() -> {
            LoadAvailableVersion loader = new LoadAvailableVersion();
            List<VersionInfo> versions = loader.getAvailableVersions();
            String release = loader.getLatestRelease();
            String snapshot = loader.getLatestSnapshot();
            boolean fromCache = loader.isFromCache();

            Platform.runLater(() -> {
                allVersions.clear();
                allVersions.addAll(versions);
                latestRelease = release;
                latestSnapshot = snapshot;
                latestLabel.setText(release.isBlank() ? "—" : release);
                versionsLabel.setText(String.valueOf(versions.size()));

                updateVersionList();
                selectSavedVersion();

                busy = false;
                setProgress(0);
                updateButtons();
                if (versions.isEmpty()) {
                    showError(t("Не удалось загрузить список версий. Проверьте подключение к интернету — "
                            + "без него доступен только локальный кэш."));
                    setStatus(t("Нет данных о версиях"));
                } else {
                    setStatus(fromCache
                            ? t("Список версий загружен из локального кэша")
                            : t("Список версий обновлён"));
                }
            });
        }, "version-loader");
        thread.setDaemon(true);
        thread.start();
    }

    private void updateVersionList() {
        if (versionSelector == null) {
            return;
        }
        VersionInfo selected = versionSelector.getValue();
        String query = searchField.getText() == null
                ? "" : searchField.getText().trim().toLowerCase(Locale.ROOT);

        List<VersionInfo> filtered = new ArrayList<>();
        for (VersionInfo info : allVersions) {
            if (!isTypeVisible(info) || !info.getId().toLowerCase(Locale.ROOT).contains(query)) {
                continue;
            }
            filtered.add(info);
        }
        versionSelector.getItems().setAll(filtered);

        if (selected != null && filtered.contains(selected)) {
            versionSelector.setValue(selected);
        } else if (selected != null && !filtered.isEmpty()) {
            versionSelector.setValue(null);
        }

        if (filtered.isEmpty()) {
            versionSelector.setPromptText(allVersions.isEmpty()
                    ? t("Нет доступных версий") : t("Ничего не найдено"));
        } else {
            versionSelector.setPromptText(t("Выберите версию"));
        }
        updateInstallHint();
        updateButtons();
    }

    private void selectSavedVersion() {
        String saved = config.getVersion();
        if (saved == null || saved.isBlank()) {
            return;
        }
        for (VersionInfo info : versionSelector.getItems()) {
            if (info.getId().equals(saved)) {
                versionSelector.setValue(info);
                return;
            }
        }
    }

    private boolean isTypeVisible(VersionInfo info) {
        return switch (info.getType()) {
            case "snapshot" -> snapshotsBox.isSelected();
            case "old_beta" -> betaBox.isSelected();
            case "old_alpha" -> alphaBox.isSelected();
            default -> true;
        };
    }

    private void updateInstallHint() {
        if (installLabel == null) {
            return;
        }
        VersionInfo version = versionSelector == null ? null : versionSelector.getValue();
        if (version == null) {
            installLabel.setText(t("версия не выбрана"));
            installLabel.getStyleClass().setAll("install-label");
            return;
        }
        boolean installed = Download.isInstalled(version.getId());
        boolean complete = installed && Download.isComplete(version.getId());
        if (isFabricSelected()) {
            installLabel.setText(t("Fabric"));
            installLabel.getStyleClass().setAll("install-label");
            downloadButton.setText(t("Установить Fabric"));
            return;
        }
        if (!installed) {
            installLabel.setText(t("не скачана"));
            installLabel.getStyleClass().setAll("install-label", "install-missing");
            downloadButton.setText(t("Скачать"));
        } else if (!complete) {
            installLabel.setText(t("не хватает файлов"));
            installLabel.getStyleClass().setAll("install-label", "install-missing");
            downloadButton.setText(t("Исправить"));
        } else {
            installLabel.setText(t("✔ уже скачана"));
            installLabel.getStyleClass().setAll("install-label", "install-ok");
            downloadButton.setText(t("Переустановить"));
        }
    }

    // ------------------------------------------------------------------
    //  Действия
    // ------------------------------------------------------------------

    private void updateAccountUi() {
        boolean microsoft = account != null && account.microsoft();
        if (accountLabel != null) {
            accountLabel.setText(microsoft
                    ? f("Microsoft: %s", account.name())
                    : t("Не выполнен вход"));
        }
        if (accountButton != null) {
            accountButton.setText(microsoft ? t("Выйти") : t("Войти через Microsoft"));
        }
        updateButtons();
    }

    private void onAccountAction() {
        if (account != null && account.microsoft()) {
            logoutMicrosoft();
        } else {
            loginMicrosoft();
        }
    }

    private void logoutMicrosoft() {
        account = null;
        savedAccount = null;
        AccountStore.clear();
        updateAccountUi();
        setStatus(t("Вы вышли из аккаунта Microsoft"));
        appendLog(t("Вы вышли из аккаунта Microsoft"));
    }

    private void loginMicrosoft() {
        if (busy) {
            return;
        }
        String clientId = config.getMicrosoftClientId();
        if (clientId.isBlank()) {
            showMicrosoftSetup();
            return;
        }
        loginCancelled = false;
        accountButton.setDisable(true);
        setStatus(t("Запрос кода Microsoft…"));
        appendLog(t("Вход через Microsoft"));

        Thread thread = new Thread(() -> {
            try {
                MicrosoftAuth.DeviceCode code = MicrosoftAuth.requestDeviceCode(clientId);
                Platform.runLater(() -> showLoginDialog(code));
                MicrosoftAuth.Login login = MicrosoftAuth.complete(code, clientId,
                        () -> loginCancelled,
                        message -> Platform.runLater(() -> setStatus(message)));
                Platform.runLater(() -> {
                    account = login.account();
                    savedAccount = new AccountStore.Saved(login.refreshToken(), clientId,
                            account.name(), account.uuid(), account.xuid());
                    AccountStore.save(savedAccount);
                    closeLoginDialog();
                    accountButton.setDisable(false);
                    updateAccountUi();
                    setStatus(f("Вход выполнен: %s", account.name()));
                    appendLog(f("Вход выполнен: %s", account.name()));
                });
            } catch (Exception e) {
                boolean cancelled = loginCancelled;
                Platform.runLater(() -> {
                    closeLoginDialog();
                    accountButton.setDisable(false);
                    if (cancelled) {
                        setStatus(t("Вход отменён"));
                    } else if (e instanceof MicrosoftAuth.AuthException auth
                            && auth.appRegistration()) {
                        showAppRegistrationHelp();
                    } else {
                        showError(f("Не удалось войти Microsoft: %s", e.getMessage()));
                        setStatus(t("Ошибка входа Microsoft"));
                        appendLog(f("ОШИБКА входа Microsoft: %s", e.getMessage()));
                    }
                });
            }
        }, "microsoft-login");
        thread.setDaemon(true);
        thread.start();
    }

    private void showMicrosoftSetup() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.initOwner(stage);
        alert.setTitle("LauncherMC");
        alert.setHeaderText(t("Нужен Client ID Microsoft"));
        alert.setContentText(t("Создайте своё приложение Microsoft Entra: "
                + "App registrations → New registration, тип «Personal Microsoft accounts only», "
                + "Authentication → Allow public client flows = Yes. "
                + "Скопируйте Application (client) ID и укажите его в настройках лаунчера."));
        ButtonType portal = new ButtonType(t("Открыть Azure Portal"), ButtonBar.ButtonData.OK_DONE);
        ButtonType settings = new ButtonType(t("Настройки"), ButtonBar.ButtonData.OTHER);
        ButtonType close = new ButtonType(t("Закрыть"), ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(portal, settings, close);
        applyDialogStyle(alert);
        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == portal) {
            openBrowser("https://portal.azure.com/#view/Microsoft_AAD_RegisteredApps/ApplicationsListBlade");
        } else if (result.isPresent() && result.get() == settings) {
            openSettings();
        }
    }

    private void showAppRegistrationHelp() {
        setStatus(t("Mojang не одобрил приложение"));
        appendLog(t("Minecraft API: client ID не в allow-list Mojang"));
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.initOwner(stage);
        alert.setTitle("LauncherMC");
        alert.setHeaderText(t("Mojang не одобрил приложение"));
        alert.setContentText(t("Minecraft Services принимает только client ID из allow-list Mojang. "
                + "Новые приложения нужно подать на проверку; после одобрения вход заработает "
                + "без изменений."));
        ButtonType page = new ButtonType(t("Открыть страницу Mojang"), ButtonBar.ButtonData.OK_DONE);
        ButtonType close = new ButtonType(t("Закрыть"), ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(page, close);
        applyDialogStyle(alert);
        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == page) {
            openBrowser("https://aka.ms/AppRegInfo");
        }
    }

    private void showLoginDialog(MicrosoftAuth.DeviceCode code) {
        closeLoginDialog();
        Stage dialog = new Stage();
        dialog.initOwner(stage);
        dialog.setTitle(t("Вход Microsoft"));
        dialog.getIcons().add(loadImage("/images/icon.png"));

        VBox root = new VBox(12);
        root.getStyleClass().add("settings-root");
        root.setPadding(new Insets(24));

        Label title = new Label(t("Вход Microsoft"));
        title.getStyleClass().add("dialog-title");
        Label instructions = new Label(f("Откройте %s и введите код:", code.verificationUri()));
        instructions.setWrapText(true);
        Label codeLabel = new Label(code.userCode());
        codeLabel.getStyleClass().add("auth-code");

        Button open = new Button(t("Открыть браузер"));
        open.getStyleClass().add("button-primary");
        open.setOnAction(e -> openBrowser(code.verificationUri()));
        Button copy = new Button(t("Копировать код"));
        copy.getStyleClass().add("button-secondary");
        copy.setOnAction(e -> {
            ClipboardContent content = new ClipboardContent();
            content.putString(code.userCode());
            Clipboard.getSystemClipboard().setContent(content);
            setStatus(t("Код скопирован"));
        });
        Button cancel = new Button(t("Отмена"));
        cancel.getStyleClass().add("button-secondary");
        cancel.setOnAction(e -> {
            loginCancelled = true;
            accountButton.setDisable(false);
            closeLoginDialog();
        });

        Label waiting = new Label(t("Ожидание подтверждения…"));
        waiting.getStyleClass().add("hint");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox actions = new HBox(8, open, copy, spacer, cancel);
        actions.setAlignment(Pos.CENTER_LEFT);

        root.getChildren().addAll(title, instructions, codeLabel, actions, waiting);
        Scene scene = new Scene(root, 520, 280);
        if (styleSheet != null) {
            scene.getStylesheets().add(styleSheet);
        }
        dialog.setScene(scene);
        dialog.setOnCloseRequest(e -> {
            loginCancelled = true;
            accountButton.setDisable(false);
            loginDialog = null;
        });
        dialog.show();
        loginDialog = dialog;
    }

    private void closeLoginDialog() {
        Stage dialog = loginDialog;
        loginDialog = null;
        if (dialog != null) {
            dialog.close();
        }
    }

    private void openBrowser(String url) {
        try {
            if (!Desktop.isDesktopSupported()) {
                throw new IOException(t("Открытие ссылок не поддерживается"));
            }
            Desktop.getDesktop().browse(URI.create(url));
        } catch (Exception e) {
            showError(f("Не удалось открыть браузер: %s", e.getMessage()));
        }
    }

    private void onPlay() {
        VersionInfo version = versionSelector.getValue();
        if (version == null) {
            showError(t("Выберите версию Minecraft из списка."));
            versionSelector.requestFocus();
            return;
        }
        if (account == null || !account.microsoft()) {
            showError(t("Войдите через Microsoft, чтобы играть."));
            loginMicrosoft();
            return;
        }
        config.save("version", version.getId());
        MinecraftAccount launchAccount = account;

        if (isFabricSelected()) {
            startFabricInstall(version, launchAccount, true);
            return;
        }

        if (!Download.isComplete(version.getId())) {
            boolean installed = Download.isInstalled(version.getId());
            boolean accepted = confirm(
                    installed ? t("Файлы версии неполные") : t("Версия ещё не скачана"),
                    installed
                            ? f("Проверить файлы Minecraft %s и докачать повреждённые, а затем сразу запустить игру?",
                            version.getId())
                            : f("Скачать Minecraft %s сейчас, а затем сразу запустить игру?",
                            version.getId()));
            if (accepted) {
                startDownload(version, launchAccount, true, installed);
            }
            return;
        }
        startGame(version, launchAccount);
    }

    private void onDownload() {
        VersionInfo version = versionSelector.getValue();
        if (version == null) {
            showError(t("Сначала выберите версию Minecraft."));
            return;
        }
        boolean installed = Download.isInstalled(version.getId());
        if (isFabricSelected()) {
            startFabricInstall(version, null, false);
            return;
        }
        startDownload(version, null, false, installed);
    }

    private void startDownload(VersionInfo version, MinecraftAccount launchAccount,
                               boolean launchAfter, boolean repair) {
        busy = true;
        downloading = true;
        cancelRequested = false;
        updateButtons();
        hideError();
        setProgress(0);
        setStatus(repair ? f("Проверка и ремонт %s…", version.getId())
                : f("Скачивание %s…", version.getId()));
        appendLog(repair ? f("Проверка и ремонт версии %s", version.getId())
                : f("Скачивание версии %s", version.getId()));

        UiListener listener = new UiListener();
        Thread thread = new Thread(() -> {
            try {
                new Download().download(version.getId(), listener, repair, () -> cancelRequested);
                Platform.runLater(() -> {
                    downloading = false;
                    busy = false;
                    setProgress(1);
                    updateButtons();
                    updateInstallHint();
                    setStatus(f("Версия %s готова к запуску", version.getId()));
                    if (launchAfter && launchAccount != null) {
                        startGame(version, launchAccount);
                    }
                });
            } catch (Exception e) {
                boolean cancelled = cancelRequested;
                Platform.runLater(() -> {
                    downloading = false;
                    busy = false;
                    setProgress(0);
                    updateButtons();
                    updateInstallHint();
                    if (cancelled) {
                        setStatus(t("Загрузка отменена"));
                        appendLog(t("Загрузка отменена пользователем"));
                    } else {
                        showError(f("Не удалось скачать версию: %s", e.getMessage()));
                        setStatus(t("Ошибка загрузки"));
                        appendLog(f("ОШИБКА загрузки: %s", e.getMessage()));
                    }
                });
            }
        }, "download-" + version.getId());
        thread.setDaemon(true);
        thread.start();
    }

    private void startGame(VersionInfo version, MinecraftAccount launchAccount) {
        if (!Download.isComplete(version.getId())) {
            showError(t("Файлы версии неполные — нажмите «Скачать» или «Исправить», "
                    + "лаунчер проверит их по SHA-1."));
            updateInstallHint();
            return;
        }

        LaunchMinecraft.JavaRequirement requirement = LaunchMinecraft.requirement(version.getId());
        if (requirement.major() > 0
                && !JavaRuntime.hasSuitable(config.getJavaPath(), requirement.major(),
                requirement.exact())) {
            long size = JavaInstaller.estimatedSizeMb(requirement.major());
            boolean install = confirm(t("Java не найдена"),
                    f("Для версии %s нужна Java %s. Скачать Adoptium Temurin JRE %s (~%s МБ) автоматически?",
                            version.getId(), requirement.major(), requirement.major(), size),
                    t("Скачать"));
            if (install) {
                startJavaInstall(requirement, version, launchAccount);
            }
            return;
        }

        busy = true;
        gameRunning = true;
        updateButtons();
        hideError();
        setProgress(-1);
        setStatus(f("Подготовка к запуску %s…", version.getId()));
        appendLog(f("Запуск %s (ник: %s)", version.getId(), launchAccount.name()));

        UiListener listener = new UiListener() {
            @Override
            public void gameStarted() {
                Platform.runLater(() -> {
                    busy = false;
                    updateButtons();
                    setProgress(0);
                    setStatus(f("Minecraft %s запущен", version.getId()));
                });
            }
        };

        Thread thread = new Thread(() -> {
            try {
                MinecraftAccount active = launchAccount;
                if (active.microsoft()
                        && (active.accessToken().isBlank() || active.expired())) {
                    AccountStore.Saved saved = savedAccount;
                    if (saved == null || saved.refreshToken().isBlank()) {
                        throw new IOException(t("Сессия Microsoft истекла — войдите заново."));
                    }
                    listener.status(t("Обновление сессии Microsoft…"));
                    String clientId = saved.clientId().isBlank()
                            ? config.getMicrosoftClientId() : saved.clientId();
                    MicrosoftAuth.Login login = MicrosoftAuth.refresh(clientId,
                            saved.refreshToken());
                    active = login.account();
                    AccountStore.Saved refreshed = new AccountStore.Saved(login.refreshToken(),
                            clientId, active.name(), active.uuid(), active.xuid());
                    AccountStore.save(refreshed);
                    savedAccount = refreshed;
                    MinecraftAccount updated = active;
                    Platform.runLater(() -> {
                        account = updated;
                        updateAccountUi();
                    });
                }
                int exitCode = new LaunchMinecraft().launch(
                        active,
                        version.getId(),
                        config.getAllocatedMemory(),
                        config.getJavaPath(),
                        listener
                );
                Platform.runLater(() -> {
                    gameRunning = false;
                    busy = false;
                    setProgress(0);
                    updateButtons();
                    setStatus(exitCode == 0
                            ? t("Игра завершена")
                            : f("Игра завершилась с кодом %s", exitCode));
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    gameRunning = false;
                    busy = false;
                    setProgress(0);
                    updateButtons();
                    showError(f("Не удалось запустить игру: %s", e.getMessage()));
                    setStatus(t("Ошибка запуска"));
                    appendLog(f("ОШИБКА запуска: %s", e.getMessage()));
                });
            }
        }, "game-launcher");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Скачивает и устанавливает портативную JRE, затем повторяет запуск.
     */
    private void startJavaInstall(LaunchMinecraft.JavaRequirement requirement,
                                  VersionInfo version, MinecraftAccount launchAccount) {
        busy = true;
        downloading = true;
        cancelRequested = false;
        updateButtons();
        hideError();
        setProgress(0);
        setStatus(f("Загрузка Java %s…", requirement.major()));
        appendLog(f("Установка Java %s", requirement.major()));

        UiListener listener = new UiListener();
        Thread thread = new Thread(() -> {
            try {
                java.nio.file.Path installed = JavaInstaller.install(
                        requirement.major(), listener, () -> cancelRequested);
                Platform.runLater(() -> {
                    downloading = false;
                    busy = false;
                    setProgress(0);
                    updateButtons();
                    setStatus(f("Java %s установлена", requirement.major()));
                    appendLog(f("Java %s установлена: %s", requirement.major(), installed));
                    startGame(version, launchAccount);
                });
            } catch (Exception e) {
                boolean cancelled = cancelRequested;
                Platform.runLater(() -> {
                    downloading = false;
                    busy = false;
                    setProgress(0);
                    updateButtons();
                    if (cancelled) {
                        setStatus(t("Загрузка отменена"));
                        appendLog(t("Загрузка отменена пользователем"));
                    } else {
                        showError(f("Не удалось установить Java: %s", e.getMessage()));
                        setStatus(t("Ошибка установки Java"));
                        appendLog(f("ОШИБКА установки Java: %s", e.getMessage()));
                    }
                });
            }
        }, "java-installer");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Устанавливает Fabric Loader для выбранной ванильной версии и (опционально)
     * сразу запускает игру.
     */
    private void startFabricInstall(VersionInfo game, MinecraftAccount launchAccount,
                                    boolean launchAfter) {
        busy = true;
        downloading = true;
        cancelRequested = false;
        updateButtons();
        hideError();
        setProgress(0);
        setStatus(f("Установка Fabric для %s…", game.getId()));
        appendLog(f("Установка Fabric для %s", game.getId()));

        UiListener listener = new UiListener();
        Thread thread = new Thread(() -> {
            try {
                String loaderId = FabricInstaller.install(game.getId(), listener,
                        () -> cancelRequested);
                Platform.runLater(() -> {
                    downloading = false;
                    busy = false;
                    setProgress(0);
                    updateButtons();
                    updateInstallHint();
                    setStatus(f("Fabric готов: %s", loaderId));
                    appendLog(f("Fabric готов: %s", loaderId));
                    if (launchAfter && launchAccount != null) {
                        startGame(new VersionInfo(loaderId, "release", "", ""), launchAccount);
                    }
                });
            } catch (Exception e) {
                boolean cancelled = cancelRequested;
                Platform.runLater(() -> {
                    downloading = false;
                    busy = false;
                    setProgress(0);
                    updateButtons();
                    if (cancelled) {
                        setStatus(t("Загрузка отменена"));
                        appendLog(t("Загрузка отменена пользователем"));
                    } else {
                        showError(f("Не удалось установить Fabric: %s", e.getMessage()));
                        setStatus(t("Ошибка установки Fabric"));
                        appendLog(f("ОШИБКА установки Fabric: %s", e.getMessage()));
                    }
                });
            }
        }, "fabric-installer");
        thread.setDaemon(true);
        thread.start();
    }

    private void toggleConsole() {
        consoleShown = !consoleShown;
        consoleBox.setVisible(consoleShown);
        consoleBox.setManaged(consoleShown);
        consoleToggle.setText(consoleShown ? t("Скрыть лог") : t("Показать лог"));
    }

    private void openSettings() {
        Stage dialog = new Stage();
        dialog.initOwner(stage);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle(t("Настройки LauncherMC"));
        dialog.getIcons().add(loadImage("/images/settings.png"));

        VBox root = new VBox(12);
        root.getStyleClass().add("settings-root");
        root.setPadding(new Insets(24));

        Label title = new Label(t("Настройки"));
        title.getStyleClass().add("dialog-title");
        Label subtitle = new Label(t("Изменения вступят в силу сразу после сохранения"));
        subtitle.getStyleClass().add("hint");

        // --- память ---
        long totalMb = totalMemoryMb();
        long maxMb = Math.max(1024, totalMb - 1024);
        int current = Math.min(Math.max(512, config.getAllocatedMemory()), (int) maxMb);

        Label memoryTitle = new Label(t("Оперативная память"));
        memoryTitle.getStyleClass().add("section-title");

        Slider slider = new Slider(512, maxMb, current);
        slider.setBlockIncrement(256);
        slider.setMajorTickUnit(1024);
        slider.setMinorTickCount(3);
        slider.setShowTickMarks(true);
        slider.getStyleClass().add("memory-slider");

        Label memoryValue = new Label(f("%s МБ", current));
        memoryValue.getStyleClass().add("stat-value");
        Region memorySpacer = new Region();
        HBox.setHgrow(memorySpacer, Priority.ALWAYS);
        HBox memoryHeader = new HBox(memoryTitle, memorySpacer, memoryValue);
        memoryHeader.setAlignment(Pos.CENTER_LEFT);
        slider.valueProperty().addListener((obs, old, value) ->
                memoryValue.setText(f("%s МБ", (int) Math.round(value.doubleValue() / 64) * 64)));
        slider.valueProperty().addListener((obs, old, value) ->
                slider.setValue(Math.round(value.doubleValue() / 64) * 64));

        Label memoryHint = new Label(f("Всего ОЗУ: %s ГБ. Рекомендуется 4096 МБ для сборок с модами.",
                totalMb / 1024));
        memoryHint.getStyleClass().add("hint");
        memoryHint.setWrapText(true);

        // --- java ---
        Label javaTitle = new Label(t("Java для запуска игры"));
        javaTitle.getStyleClass().add("section-title");
        TextField javaField = new TextField(config.getJavaPath());
        javaField.setPromptText(t("Авто: встроенная Java 21"));
        javaField.getStyleClass().add("text-field");
        Button browse = new Button(t("Обзор…"));
        browse.getStyleClass().add("button-secondary");
        browse.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle(t("Выберите java.exe или папку с Java"));
            chooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("Java", "java.exe", "javaw.exe", "java"));
            File initial = new File(System.getProperty("java.home", "."));
            if (initial.isDirectory()) {
                chooser.setInitialDirectory(initial);
            }
            File selected = chooser.showOpenDialog(dialog);
            if (selected != null) {
                javaField.setText(selected.getAbsolutePath());
            }
        });
        HBox javaRow = new HBox(8, javaField, browse);
        HBox.setHgrow(javaField, Priority.ALWAYS);

        Label javaHint = new Label(t("Пустое поле — лаунчер сам подберёт установленную Java "
                + "под версию игры (8 для старых, 21/25 для новых) или возьмёт встроенную Java 21. "
                + "Свой путь имеет приоритет, но для legacy-версий лаунчер может выбрать Java 8."));
        javaHint.getStyleClass().add("hint");
        javaHint.setWrapText(true);

        // --- Microsoft ---
        Label microsoftTitle = new Label(t("Microsoft"));
        microsoftTitle.getStyleClass().add("section-title");
        TextField microsoftClient = new TextField(config.getMicrosoftClientId());
        microsoftClient.setPromptText("client id");
        microsoftClient.getStyleClass().add("text-field");
        Button microsoftPortal = new Button(t("Открыть Azure Portal"));
        microsoftPortal.getStyleClass().add("button-secondary");
        microsoftPortal.setOnAction(e -> openBrowser(
                "https://portal.azure.com/#view/Microsoft_AAD_RegisteredApps/ApplicationsListBlade"));
        HBox microsoftRow = new HBox(8, microsoftClient, microsoftPortal);
        HBox.setHgrow(microsoftClient, Priority.ALWAYS);
        Label microsoftHint = new Label(t("По умолчанию используется встроенный client ID "
                + "приложения LauncherMC. Mojang принимает новые client ID только после "
                + "allow-list: при ошибке Invalid app registration подайте заявку."));
        microsoftHint.getStyleClass().add("hint");
        microsoftHint.setWrapText(true);

        // --- язык ---
        Label languageTitle = new Label(t("Язык интерфейса"));
        languageTitle.getStyleClass().add("section-title");
        ComboBox<String> languageBox = new ComboBox<>();
        languageBox.getItems().addAll("auto", "ru", "en");
        languageBox.setValue(config.getLanguage().isBlank() ? "auto" : config.getLanguage());
        languageBox.setMaxWidth(Double.MAX_VALUE);
        languageBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(String code) {
                return languageLabel(code);
            }

            @Override
            public String fromString(String text) {
                return "auto";
            }
        });
        Label languageHint = new Label(t("Изменения языка применяются после перезапуска лаунчера."));
        languageHint.getStyleClass().add("hint");
        languageHint.setWrapText(true);

        // --- фильтры ---
        Label filtersTitle = new Label(t("Показывать в списке версий"));
        filtersTitle.getStyleClass().add("section-title");
        CheckBox snapshots = new CheckBox(t("Снапшоты"));
        snapshots.setSelected(config.isShowSnapshots());
        CheckBox beta = new CheckBox(t("Старые бета-версии"));
        beta.setSelected(config.isShowBeta());
        CheckBox alpha = new CheckBox(t("Старые альфа-версии"));
        alpha.setSelected(config.isShowAlpha());
        HBox filterRow = new HBox(16, snapshots, beta, alpha);

        Button openFolder = new Button(t("Папка лаунчера"));
        openFolder.getStyleClass().add("button-ghost");
        openFolder.setOnAction(e -> openFolder(Path.of(".")));

        Button cancel = new Button(t("Отмена"));
        cancel.getStyleClass().add("button-secondary");
        cancel.setOnAction(e -> dialog.close());

        Button save = new Button(t("Сохранить"));
        save.getStyleClass().add("button-primary");
        save.setDefaultButton(true);
        save.setOnAction(e -> {
            String chosenLanguage = languageBox.getValue();
            boolean languageChanged = chosenLanguage != null
                    && !chosenLanguage.equals(config.getLanguage());
            config.saveAllocatedMemory((int) Math.round(slider.getValue() / 64) * 64);
            config.saveJavaPath(javaField.getText());
            config.saveMicrosoftClientId(microsoftClient.getText());
            config.saveLanguage(chosenLanguage);
            config.save("showSnapshots", snapshots.isSelected());
            config.save("showBeta", beta.isSelected());
            config.save("showAlpha", alpha.isSelected());
            snapshotsBox.setSelected(snapshots.isSelected());
            betaBox.setSelected(beta.isSelected());
            alphaBox.setSelected(alpha.isSelected());
            updateMemoryLabel();
            updateVersionList();
            setStatus(languageChanged
                    ? t("Настройки сохранены — перезапустите лаунчер для смены языка")
                    : t("Настройки сохранены"));
            dialog.close();
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox actions = new HBox(8, openFolder, spacer, cancel, save);
        actions.setAlignment(Pos.CENTER_LEFT);

        root.getChildren().addAll(
                title, subtitle,
                memoryHeader, slider, memoryHint,
                javaTitle, javaRow, javaHint,
                microsoftTitle, microsoftRow, microsoftHint,
                languageTitle, languageBox, languageHint,
                filtersTitle, filterRow,
                actions
        );

        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("settings-scroll");
        Scene scene = new Scene(scroll, 540, Math.min(650,
                Math.max(480, Screen.getPrimary().getVisualBounds().getHeight() - 80)));
        if (styleSheet != null) {
            scene.getStylesheets().add(styleSheet);
        }
        dialog.setResizable(true);
        dialog.setScene(scene);
        dialog.showAndWait();
    }

    // ------------------------------------------------------------------
    //  Вспомогательное
    // ------------------------------------------------------------------

    private CheckBox filterBox(String text, boolean selected, String key) {
        CheckBox box = new CheckBox(text);
        box.setSelected(selected);
        box.setOnAction(e -> {
            config.save(key, box.isSelected());
            updateVersionList();
        });
        return box;
    }

    private Button windowButton(String text) {
        Button button = new Button(text);
        button.getStyleClass().add("window-button");
        button.setFocusTraversable(false);
        return button;
    }

    private Label chip(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("chip");
        return label;
    }

    private Label caption(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("stat-caption");
        return label;
    }

    private Label bullet(String text) {
        Label label = new Label("•  " + text);
        label.getStyleClass().add("bullet");
        return label;
    }

    private ListCell<VersionInfo> versionCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(VersionInfo item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                Label name = new Label(item.getId());
                name.getStyleClass().add("version-name");
                Label badge = new Label(item.getDisplayType());
                badge.getStyleClass().addAll("type-badge", item.getTypeStyleClass());
                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);
                HBox row = new HBox(8, name, spacer, badge);
                row.setAlignment(Pos.CENTER_LEFT);
                setText(null);
                setGraphic(row);
            }
        };
    }

    private boolean isButtonTarget(MouseEvent event) {
        Object target = event.getTarget();
        Node node = target instanceof Node ? (Node) target : null;
        while (node != null) {
            if (node instanceof Button) {
                return true;
            }
            node = node.getParent();
        }
        return false;
    }

    private ImageView icon(String path, double size) {
        ImageView view = new ImageView(loadImage(path));
        view.setFitWidth(size);
        view.setFitHeight(size);
        view.setPreserveRatio(true);
        view.setSmooth(true);
        return view;
    }

    private Image loadImage(String path) {
        try (InputStream stream = getClass().getResourceAsStream(path)) {
            if (stream == null) {
                return new Image(App.class.getResourceAsStream("/images/icon.png"));
            }
            return new Image(stream);
        } catch (Exception e) {
            return new Image(App.class.getResourceAsStream("/images/icon.png"));
        }
    }

    private InputStream resource(String path) {
        return getClass().getResourceAsStream(path);
    }

    private void updateButtons() {
        boolean noSelection = versionSelector == null || versionSelector.getValue() == null;
        if (playButton != null) {
            boolean signedIn = account != null && account.microsoft();
            playButton.setDisable(busy || gameRunning || noSelection
                    || allVersions.isEmpty() || !signedIn);
        }
        if (downloadButton != null) {
            downloadButton.setDisable(busy || gameRunning || noSelection);
        }
        if (refreshButton != null) {
            refreshButton.setDisable(busy);
        }
        if (settingsButton != null) {
            settingsButton.setDisable(busy);
        }
        if (accountButton != null) {
            accountButton.setDisable(busy);
        }
        if (cancelButton != null) {
            boolean show = busy && downloading;
            cancelButton.setVisible(show);
            cancelButton.setManaged(show);
            cancelButton.setDisable(!show || cancelRequested);
        }
        if (progressBar != null) {
            progressBar.setVisible(busy);
            progressBar.setManaged(busy);
        }
    }

    private void setStatus(String text) {
        if (statusLabel != null) {
            statusLabel.setText(text);
        }
        System.out.println(text);
    }

    private void setProgress(double value) {
        if (progressBar == null) {
            return;
        }
        progressBar.setProgress(value);
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    private void hideError() {
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
        errorLabel.setText("");
    }

    private void appendLog(String message) {
        if (consoleArea == null) {
            return;
        }
        consoleArea.appendText("[" + LocalTime.now().format(LOG_TIME) + "] " + message + "\n");
        if (consoleArea.getText().length() > 200_000) {
            consoleArea.deleteText(0, 50_000);
        }
        consoleArea.setScrollTop(Double.MAX_VALUE);
    }

    private boolean confirm(String header, String text) {
        return confirm(header, text, t("Скачать и играть"));
    }

    private boolean confirm(String header, String text, String okText) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.initOwner(stage);
        alert.setTitle("LauncherMC");
        alert.setHeaderText(header);
        alert.setContentText(text);
        ButtonType accept = new ButtonType(okText, ButtonBar.ButtonData.OK_DONE);
        alert.getButtonTypes().setAll(accept, ButtonType.CANCEL);
        applyDialogStyle(alert);
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == accept;
    }

    private void applyDialogStyle(Alert alert) {
        if (styleSheet != null) {
            alert.getDialogPane().getStylesheets().add(styleSheet);
            alert.getDialogPane().getStyleClass().add("dialog-pane");
        }
    }

    private long totalMemoryMb() {
        try {
            SystemInfo systemInfo = new SystemInfo();
            GlobalMemory memory = systemInfo.getHardware().getMemory();
            return memory.getTotal() / (1024 * 1024);
        } catch (Throwable e) {
            return 8192;
        }
    }

    /**
     * Размер окна не больше рабочей области экрана — иначе на ноутбуках
     * с масштабированием 125–150 % нижняя панель уезжает за край.
     */
    private double windowWidth() {
        Rectangle2D bounds = Screen.getPrimary().getVisualBounds();
        return Math.min(1000, Math.max(860, bounds.getWidth() - 40));
    }

    private double windowHeight() {
        Rectangle2D bounds = Screen.getPrimary().getVisualBounds();
        return Math.min(690, Math.max(600, bounds.getHeight() - 80));
    }

    /**
     * Язык: системное свойство {@code -Dlaunchermc.lang} → настройка
     * {@code language} → язык системы. Неизвестные значения дают русский.
     */
    private String resolveLanguage(String configured) {
        String forced = System.getProperty("launchermc.lang");
        String code = forced != null && !forced.isBlank() ? forced : configured;
        if (code == null || code.isBlank() || "auto".equalsIgnoreCase(code)) {
            code = Locale.getDefault().getLanguage();
        }
        return code.toLowerCase(Locale.ROOT).startsWith("en") ? "en" : "ru";
    }

    private String languageLabel(String code) {
        if (code == null) {
            return t("Авто (системный)");
        }
        return switch (code) {
            case "ru" -> "Русский";
            case "en" -> "English";
            default -> t("Авто (системный)");
        };
    }

    private String loaderName(String code) {
        return "fabric".equals(code) ? "Fabric" : "Vanilla";
    }

    private boolean isFabricSelected() {
        return loaderBox != null && "fabric".equals(loaderBox.getValue());
    }

    private void updateMemoryLabel() {
        if (memoryLabel != null) {
            memoryLabel.setText(f("Память: %s МБ", config.getAllocatedMemory()));
        }
    }

    private void openFolder(Path path) {
        try {
            File folder = path.toAbsolutePath().toFile();
            if (!folder.exists() && !folder.mkdirs()) {
                showError(f("Не удалось открыть папку: %s", folder));
                return;
            }
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(folder);
            } else {
                showError(f("Открытие папок не поддерживается на этой системе: %s", folder));
            }
        } catch (IOException | UnsupportedOperationException e) {
            showError(f("Не удалось открыть папку: %s", e.getMessage()));
        }
    }

    private void closeApp() {
        if (versionSelector != null && versionSelector.getValue() != null) {
            config.save("version", versionSelector.getValue().getId());
        }
        System.out.println(t("LauncherMC закрывается"));
        if (logger != null) {
            logger.close();
        }
        Platform.exit();
    }

    private class UiListener implements LauncherListener {
        @Override
        public void status(String message) {
            Platform.runLater(() -> setStatus(message));
        }

        @Override
        public void progress(double value) {
            Platform.runLater(() -> setProgress(value));
        }

        @Override
        public void log(String message) {
            System.out.println(message);
            Platform.runLater(() -> appendLog(message));
        }
    }

    public static void main(String[] args) {
        launch();
    }
}

