package com.hackhud.clouddrive.desktop;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import java.io.IOException;

/**
 * Десктоп-клієнт «Cloud Drive»: вікно входу, після якого відкривається віртуальний диск.
 */
public class DesktopApp extends Application {

    private Stage stage;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        stage.setTitle("Cloud Drive");
        showLogin();
        stage.show();
    }

    void showLogin() {
        TextField server = new TextField(getParameters() == null || getParameters().getUnnamed().isEmpty()
                ? "http://localhost:8080" : getParameters().getUnnamed().getFirst());
        TextField user = new TextField();
        PasswordField password = new PasswordField();
        Label error = new Label();
        error.setStyle("-fx-text-fill: #c62828;");
        error.setWrapText(true);

        Button login = new Button("Увійти");
        Button register = new Button("Зареєструватися");
        login.setDefaultButton(true);
        login.setOnAction(e -> auth(server.getText(), user.getText(), password.getText(), false, error));
        register.setOnAction(e -> auth(server.getText(), user.getText(), password.getText(), true, error));

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(10);
        form.setPadding(new Insets(24));
        form.setAlignment(Pos.CENTER);
        Label title = new Label("Cloud Drive");
        title.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");
        form.add(title, 0, 0, 2, 1);
        form.addRow(1, new Label("Сервер:"), server);
        form.addRow(2, new Label("Логін:"), user);
        form.addRow(3, new Label("Пароль:"), password);
        HBox buttons = new HBox(10, login, register);
        form.add(buttons, 1, 4);
        form.add(error, 0, 5, 2, 1);
        server.setPrefColumnCount(22);

        stage.setScene(new Scene(form, 440, 280));
    }

    private void auth(String server, String user, String password, boolean register, Label error) {
        ApiClient api = new ApiClient(server.trim());
        try {
            if (register) {
                api.register(user.trim(), password);
            } else {
                api.login(user.trim(), password);
            }
            MainView main = new MainView(api, this::showLogin);
            stage.setScene(new Scene(main.root(), 1100, 620));
            main.refresh();
        } catch (IOException | IllegalArgumentException e) {
            error.setText(e.getMessage() == null ? "Сервер недоступний" : e.getMessage());
        }
    }
}
