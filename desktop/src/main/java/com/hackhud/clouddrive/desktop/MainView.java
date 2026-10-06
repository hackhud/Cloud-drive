package com.hackhud.clouddrive.desktop;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Separator;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToolBar;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import com.hackhud.clouddrive.common.FileFilter;
import com.hackhud.clouddrive.common.FileInfo;
import com.hackhud.clouddrive.common.FileListOps;
import com.hackhud.clouddrive.common.PreviewType;
import com.hackhud.clouddrive.common.SortOrder;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Головне вікно: віртуальний диск користувача.
 */
public class MainView {

    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss").withZone(ZoneId.systemDefault());

    private final ApiClient api;
    private final Runnable onLogout;
    private final BorderPane root = new BorderPane();
    private final TableView<FileInfo> table = new TableView<>();
    private final ObservableList<FileInfo> rows = FXCollections.observableArrayList();
    private final ComboBox<SortOrder> sort = new ComboBox<>(FXCollections.observableArrayList(SortOrder.values()));
    private final ComboBox<FileFilter> filter = new ComboBox<>(FXCollections.observableArrayList(FileFilter.values()));
    private final StackPane preview = new StackPane();
    private final Label status = new Label();
    private List<FileInfo> allFiles = List.of();

    public MainView(ApiClient api, Runnable onLogout) {
        this.api = api;
        this.onLogout = onLogout;
        buildTable();
        root.setTop(buildToolbar());
        SplitPane split = new SplitPane(table, preview);
        split.setDividerPositions(0.66);
        root.setCenter(split);
        status.setPadding(new Insets(4, 8, 4, 8));
        root.setBottom(status);
        showPreviewHint("Оберіть файл, щоб переглянути вміст (.html як текст, .png як зображення)");
    }

    public Parent root() {
        return root;
    }

    private VBox buildToolbar() {
        Button upload = new Button("Завантажити…");
        Button download = new Button("Скачати…");
        Button delete = new Button("Видалити");
        Button refresh = new Button("Оновити");
        Button syncFolder = new Button("Синхронізувати папку…");
        Button logout = new Button("Вийти");
        upload.setOnAction(e -> chooseAndUpload());
        download.setOnAction(e -> downloadSelected());
        delete.setOnAction(e -> deleteSelected());
        refresh.setOnAction(e -> refresh());
        syncFolder.setOnAction(e -> syncFolder());
        logout.setOnAction(e -> {
            try {
                api.logout();
            } catch (IOException ignored) {
                // сесія все одно завершується на клієнті
            }
            onLogout.run();
        });

        sort.setValue(SortOrder.NONE);
        filter.setValue(FileFilter.ALL);
        sort.setOnAction(e -> applyView());
        filter.setOnAction(e -> applyView());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        ToolBar actions = new ToolBar(upload, download, delete, refresh, new Separator(), syncFolder,
                spacer, new Label("Користувач: " + api.username()), logout);
        ToolBar view = new ToolBar(new Label("Сортування:"), sort, new Label("Фільтр:"), filter, columnsMenu());
        return new VBox(actions, view);
    }

    private void buildTable() {
        TableColumn<FileInfo, String> name = textColumn("Назва", FileInfo::name);
        name.setPrefWidth(200);
        table.getColumns().add(name);
        table.getColumns().add(timeColumn("Створено", FileInfo::createdAt));
        table.getColumns().add(timeColumn("Змінено", FileInfo::modifiedAt));
        table.getColumns().add(textColumn("Завантажив", FileInfo::uploadedBy));
        table.getColumns().add(textColumn("Редагував", FileInfo::editedBy));
        // сортування виконується індивідуальною операцією (за датою зміни), а не кліком по заголовку
        table.getColumns().forEach(c -> c.setSortable(false));
        table.setItems(rows);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        table.getSelectionModel().selectedItemProperty().addListener((obs, old, file) -> showPreview(file));
        table.setPlaceholder(new Label("Файлів немає. Перетягніть файли сюди, щоб завантажити."));
        setupDragAndDrop();
    }

    private static TableColumn<FileInfo, String> textColumn(String title, Function<FileInfo, String> getter) {
        TableColumn<FileInfo, String> column = new TableColumn<>(title);
        column.setCellValueFactory(c -> new SimpleStringProperty(getter.apply(c.getValue())));
        column.setPrefWidth(110);
        return column;
    }

    private static TableColumn<FileInfo, String> timeColumn(String title, Function<FileInfo, Instant> getter) {
        TableColumn<FileInfo, String> column = textColumn(title, f -> DATE_TIME.format(getter.apply(f)));
        column.setPrefWidth(150);
        return column;
    }

    /** Меню показу/приховування стовпців; стовпець з назвою приховати не можна. */
    private MenuButton columnsMenu() {
        MenuButton menu = new MenuButton("Стовпці");
        table.getColumns().stream().skip(1).forEach(column -> {
            CheckMenuItem item = new CheckMenuItem(column.getText());
            item.setSelected(true);
            column.visibleProperty().bind(item.selectedProperty());
            menu.getItems().add(item);
        });
        return menu;
    }

    private void setupDragAndDrop() {
        table.setOnDragOver(e -> {
            if (e.getGestureSource() != table && e.getDragboard().hasFiles()) {
                e.acceptTransferModes(TransferMode.COPY);
            }
            e.consume();
        });
        table.setOnDragDropped(e -> {
            Dragboard board = e.getDragboard();
            boolean ok = board.hasFiles();
            if (ok) {
                uploadAll(board.getFiles().stream().filter(File::isFile).map(File::toPath).toList());
            }
            e.setDropCompleted(ok);
            e.consume();
        });
        // перетягування рядків з таблиці у провідник: файли спершу скачуються у тимчасову папку
        table.setOnDragDetected(e -> {
            List<FileInfo> selected = new ArrayList<>(table.getSelectionModel().getSelectedItems());
            if (selected.isEmpty()) {
                return;
            }
            try {
                Path dir = Files.createTempDirectory("cloud-drive");
                List<File> files = new ArrayList<>();
                for (FileInfo f : selected) {
                    Path target = dir.resolve(f.name());
                    api.download(f, target);
                    files.add(target.toFile());
                }
                ClipboardContent content = new ClipboardContent();
                content.putFiles(files);
                table.startDragAndDrop(TransferMode.COPY).setContent(content);
            } catch (IOException ex) {
                error(ex);
            }
            e.consume();
        });
    }

    public void refresh() {
        run("Оновлення списку…", api::list, files -> {
            allFiles = files;
            applyView();
            status.setText("Файлів на диску: " + files.size());
        });
    }

    /** Застосовує індивідуальну операцію: фільтр і сортування за датою зміни. */
    private void applyView() {
        FileInfo selected = table.getSelectionModel().getSelectedItem();
        rows.setAll(FileListOps.apply(allFiles, filter.getValue(), sort.getValue()));
        if (selected != null) {
            rows.stream().filter(f -> f.id() == selected.id()).findFirst().ifPresentOrElse(
                    f -> table.getSelectionModel().select(f),
                    () -> showPreviewHint("Оберіть файл, щоб переглянути вміст (.html як текст, .png як зображення)"));
        }
    }

    private void chooseAndUpload() {
        List<File> files = new FileChooser().showOpenMultipleDialog(window());
        if (files != null) {
            uploadAll(files.stream().map(File::toPath).toList());
        }
    }

    private void uploadAll(List<Path> files) {
        run("Завантаження…", () -> {
            for (Path f : files) {
                api.upload(f);
            }
            return files.size();
        }, n -> {
            refresh();
            status.setText("Завантажено файлів: " + n);
        });
    }

    private void downloadSelected() {
        List<FileInfo> selected = new ArrayList<>(table.getSelectionModel().getSelectedItems());
        if (selected.isEmpty()) {
            return;
        }
        File dir = new DirectoryChooser().showDialog(window());
        if (dir == null) {
            return;
        }
        run("Скачування…", () -> {
            for (FileInfo f : selected) {
                api.download(f, dir.toPath().resolve(f.name()));
            }
            return selected.size();
        }, n -> status.setText("Скачано файлів: " + n + " у " + dir));
    }

    private void deleteSelected() {
        List<FileInfo> selected = new ArrayList<>(table.getSelectionModel().getSelectedItems());
        if (selected.isEmpty()) {
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Видалити файлів: " + selected.size() + "?", ButtonType.YES, ButtonType.NO);
        confirm.initOwner(window());
        if (confirm.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) {
            return;
        }
        run("Видалення…", () -> {
            for (FileInfo f : selected) {
                api.delete(f.id());
            }
            return selected.size();
        }, n -> refresh());
    }

    private void syncFolder() {
        File dir = new DirectoryChooser().showDialog(window());
        if (dir == null) {
            return;
        }
        SyncService sync = new SyncService(api);
        run("Синхронізація…", () -> sync.sync(dir.toPath()), result -> {
            refresh();
            status.setText("Синхронізовано " + dir + ": на сервер " + result.uploaded()
                    + ", з сервера " + result.downloaded());
        });
    }

    private void showPreview(FileInfo file) {
        if (file == null) {
            return;
        }
        PreviewType type = PreviewType.of(file.name());
        if (type == PreviewType.NONE) {
            showPreviewHint("Перегляд вмісту доступний для файлів .html і .png");
            return;
        }
        run("Завантаження вмісту…", () -> api.content(file.id()), bytes -> {
            if (type == PreviewType.TEXT) {
                TextArea text = new TextArea(new String(bytes, StandardCharsets.UTF_8));
                text.setEditable(false);
                text.setStyle("-fx-font-family: monospace;");
                preview.getChildren().setAll(text);
            } else {
                ImageView image = new ImageView(new Image(new ByteArrayInputStream(bytes)));
                image.setPreserveRatio(true);
                image.fitWidthProperty().bind(preview.widthProperty().subtract(20));
                ScrollPane scroll = new ScrollPane(image);
                scroll.setFitToWidth(true);
                preview.getChildren().setAll(scroll);
            }
            status.setText(file.name() + " (" + file.size() + " байт)");
        });
    }

    private void showPreviewHint(String text) {
        Label hint = new Label(text);
        hint.setWrapText(true);
        hint.setPadding(new Insets(16));
        preview.getChildren().setAll(hint);
    }

    /** Виконує мережеву дію у фоновому потоці, результат обробляє в потоці GUI. */
    private <T> void run(String message, Callable<T> action, Consumer<T> onSuccess) {
        status.setText(message);
        Task<T> task = new Task<>() {
            @Override
            protected T call() throws Exception {
                return action.call();
            }
        };
        task.setOnSucceeded(e -> onSuccess.accept(task.getValue()));
        task.setOnFailed(e -> error(task.getException()));
        Thread thread = new Thread(task, "cloud-drive-io");
        thread.setDaemon(true);
        thread.start();
    }

    private void error(Throwable e) {
        status.setText("Помилка: " + e.getMessage());
        Alert alert = new Alert(Alert.AlertType.ERROR, e.getMessage() == null ? e.toString() : e.getMessage());
        alert.initOwner(window());
        alert.show();
    }

    private Window window() {
        return root.getScene() == null ? null : root.getScene().getWindow();
    }
}
