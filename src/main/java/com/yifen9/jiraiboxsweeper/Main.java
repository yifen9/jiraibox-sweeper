package com.yifen9.jiraiboxsweeper;

import com.yifen9.jiraiboxsweeper.controller.Game;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class Main extends Application {
    public void start(Stage stage) {
        Game game = new Game();
        Scene scene = new Scene(game.createContent(), 950, 650);

        stage.setTitle("Jiraibox Sweeper");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}