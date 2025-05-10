package com.yifen9.jiraiboxsweeper.controller;

import com.yifen9.jiraiboxsweeper.model.Cell;
import com.yifen9.jiraiboxsweeper.model.Board;
import com.yifen9.jiraiboxsweeper.service.Timer;

import com.yifen9.jiraiboxsweeper.service.Firebase;
import javafx.application.Platform;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.transform.Scale;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class Game {
    private static final int ROWS = 9, COLS = 9;

    private final double cellSize = 30 + 2;
    private final double boardWidth = COLS * cellSize;
    private final double boardHeight = ROWS * cellSize;

    private final Board board;
    private final Timer timer;

    private final Firebase firebase;

    private final GridPane gridPane = new GridPane();
    private final Label timerLabel = new Label("00:00");
    private final TableView<Score> rankingTable = new TableView<>();
    private final ObservableList<Score> rawRanking = FXCollections.observableArrayList();

    Group boardGroup = new Group(gridPane);
    StackPane boardHolder = new StackPane(boardGroup);

    public Game() {
        board = new Board();
        timer = new Timer(this::updateTimer);
        firebase = new Firebase();
        timer.reset();
        loadAllRanking();
    }

    public Parent createContent() {
        BorderPane root = new BorderPane();

        HBox topBar = new HBox(10, new Label("Time: "), timerLabel);
        topBar.setPadding(new Insets(10));
        Button resetBtn = new Button("Reset");
        resetBtn.setOnAction(e -> onReset());
        topBar.getChildren().add(resetBtn);
        root.setTop(topBar);

        initGrid();

        boardHolder.setAlignment(Pos.CENTER);
        boardHolder.setMinSize(0, 0);

        Scale scale = new Scale(1, 1, boardWidth/2, boardHeight/2);
        boardGroup.getTransforms().add(scale);

        boardHolder.layoutBoundsProperty().addListener((obs, oldB, newB) -> {
            double sX = newB.getWidth() / boardWidth * 0.75;
            double sY = newB.getHeight() / boardHeight * 0.75;
            double s = Math.min(sX, sY);
            gridPane.setScaleX(s);
            gridPane.setScaleY(s);
        });

        root.setCenter(boardHolder);

        initRankingTable();

        SortedList<Score> sorted = new SortedList<>(rawRanking);
        sorted.comparatorProperty().bind(rankingTable.comparatorProperty());
        rankingTable.setItems(sorted);

        TableColumn<Score, ?> timeCol = rankingTable.getColumns().get(2);
        rankingTable.getSortOrder().setAll(timeCol);
        timeCol.setSortType(TableColumn.SortType.ASCENDING);

        VBox side = new VBox(10, rankingTable);
        side.setPadding(new Insets(10));
        side.setPrefWidth(200);
        root.setRight(side);

        return root;
    }

    private void initGrid() {
        gridPane.setHgap(2);
        gridPane.setVgap(2);
        gridPane.setPadding(new Insets(2));
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                Button cb = new Button();
                cb.setPrefSize(30, 30);
                cb.setFocusTraversable(false);
                final int rr = r, cc = c;
                cb.setOnMouseClicked(e -> {
                    if (e.getButton() == MouseButton.PRIMARY) {
                        if (!board.isInit && !board.isEmpty(rr, cc)){
                            do {board.reset();}
                            while (!board.isEmpty(rr, cc));
                        }
                        board.isInit = true;
                        reveal(rr, cc);
                    }
                    else if (e.getButton() == MouseButton.SECONDARY) flag(rr, cc);
                });
                gridPane.add(cb, c, r);
            }
        }
        refreshGrid();
    }

    private void initRankingTable() {
        rankingTable.getColumns().clear();

        TableColumn<Score, Number> rankCol = new TableColumn<>("#");
        rankCol.setCellValueFactory(cell ->
                new SimpleIntegerProperty(rankingTable.getItems().indexOf(cell.getValue()) + 1)
        );

        TableColumn<Score, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().name)
        );

        TableColumn<Score, Number> timeCol = new TableColumn<>("Time");
        timeCol.setCellValueFactory(cell ->
                new SimpleIntegerProperty(cell.getValue().time)
        );

        rankingTable.getColumns().addAll(rankCol, nameCol, timeCol);
        rankingTable.setPrefHeight(500);
    }

    private void loadAllRanking() {
        new Thread(() -> {
            try {
                List<Firebase.Score> all = firebase.fetchAllScores();
                List<Score> data = all.stream()
                        .map(fs -> new Score(fs.name, fs.time))
                        .collect(Collectors.toList());

                Platform.runLater(() -> {
                    rawRanking.setAll(data);
                });
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void reveal(int r, int c) {
        boolean hit = board.reveal(r, c);
        refreshGrid();
        if (hit) {
            timer.stop();
            showAllMines();
        } else if (board.isCleared()) {
            timer.stop();
            int t = timer.getSeconds();
            TextInputDialog dlg = new TextInputDialog();
            dlg.setHeaderText("Finnished with " + t + " seconds! Now leave your name");
            Optional<String> name = dlg.showAndWait();
            name.ifPresent(n -> {
                addScore(n, t);
                loadAllRanking();
            });
        }
    }

    private void flag(int r, int c) {
        if (board.flag(r, c)) refreshGrid();
    }

    private void expand(int r, int c) {
        if (board.expand(r, c)) refreshGrid();
    }

    private void refreshGrid() {
        for (javafx.scene.Node node : gridPane.getChildren()) {
            if (!(node instanceof Button)) continue;
            int r = GridPane.getRowIndex(node), c = GridPane.getColumnIndex(node);
            Cell cell = board.getCell(r, c);
            Button cb = (Button) node;
            if (cell.isSeen) {
                cb.setDisable(true);
                if (cell.isMine) cb.setText("✹");
                else if (cell.adjMines > 0) cb.setText(String.valueOf(cell.adjMines));
                else cb.setText("");
            } else {
                cb.setDisable(false);
                cb.setText(cell.isFlag ? "⚑" : "");
            }
        }
    }

    private void showAllMines() {
        board.revealAll();
        refreshGrid();
    }

    private void onReset() {
        board.reset();
        timer.reset();
        refreshGrid();
    }

    private void updateTimer() {
        int s = timer.getSeconds();
        timerLabel.setText(String.format("%02d:%02d", s/60, s%60));
    }

    private void addScore(String name, int time) {
        new Thread(() -> {
            try {
                firebase.postScore(name, time);
                List<Firebase.Score> all = firebase.fetchAllScores();
                List<Score> data = all.stream()
                        .map(fs -> new Score(fs.name, fs.time))
                        .collect(Collectors.toList());

                Platform.runLater(() -> {
                    rawRanking.setAll(data);
                });
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    public static class Score {
        public final String name;
        public final int time;
        public Score(String name, int time) {
            this.name = name;
            this.time = time;
        }
    }
}
