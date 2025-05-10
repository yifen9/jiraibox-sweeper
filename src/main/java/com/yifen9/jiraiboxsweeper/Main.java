package com.yifen9.jiraiboxsweeper;

import com.yifen9.jiraiboxsweeper.controller.Game;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.stage.Stage;

public class Main extends Application {
    public void start(Stage stage) {
        try {
            Game game = new Game();
            Scene scene = new Scene(game.createContent(), 950, 650);

            stage.setTitle("Jiraibox Sweeper");
            stage.setScene(scene);
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
            new Alert(Alert.AlertType.ERROR, e.toString()).showAndWait();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}