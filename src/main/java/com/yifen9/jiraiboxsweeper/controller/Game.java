package com.yifen9.jiraiboxsweeper.controller;

import com.yifen9.jiraiboxsweeper.model.Cell;
import com.yifen9.jiraiboxsweeper.model.Board;
import com.yifen9.jiraiboxsweeper.service.Timer;
import com.yifen9.jiraiboxsweeper.service.Firebase;

import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
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

    private final TableView<Stat> statsTable = new TableView<>();
    private final ObservableList<Stat> statsList = FXCollections.observableArrayList();

    Group boardGroup = new Group(gridPane);
    StackPane boardHolder = new StackPane(boardGroup);

    public Game() {
        board = new Board();
        timer = new Timer(() -> {
            this.updateTimer();   // 这会更新 timerLabel
            this.updateStats();   // 新增：把最新 time/mines 写入表格
        });
        firebase = new Firebase();
        timer.reset();
        loadAllRanking();
    }

    public Parent createContent() {
        // 1. 根 GridPane 三列一行，横向 20/60/20%，纵向 100%
        GridPane root = new GridPane();
        root.setPadding(new Insets(10));
        root.setHgap(10);
        ColumnConstraints c1 = new ColumnConstraints(); c1.setPercentWidth(20);
        ColumnConstraints c2 = new ColumnConstraints(); c2.setPercentWidth(60);
        ColumnConstraints c3 = new ColumnConstraints(); c3.setPercentWidth(20);
        root.getColumnConstraints().addAll(c1, c2, c3);
        RowConstraints r = new RowConstraints();
        r.setVgrow(Priority.ALWAYS);
        r.setPercentHeight(100);
        root.getRowConstraints().add(r);

        initStatsTable();
        updateStats();

        // 在 createContent() 里，替换左侧部分为：
        GridPane leftPane = new GridPane();
        leftPane.setVgap(10);
// 单列
        ColumnConstraints lc = new ColumnConstraints();
        lc.setPercentWidth(100);
        leftPane.getColumnConstraints().add(lc);
// 三行：row0、row2 固定为“宽度 = 左侧面板宽度”（正方形），row1 自由拉伸
        RowConstraints r0 = new RowConstraints();
        r0.setVgrow(Priority.NEVER);
        r0.prefHeightProperty().bind(leftPane.widthProperty());
        RowConstraints r1 = new RowConstraints();
        r1.setVgrow(Priority.ALWAYS);
        RowConstraints r2 = new RowConstraints();
        r2.setVgrow(Priority.NEVER);
        r2.prefHeightProperty().bind(leftPane.widthProperty());
        leftPane.getRowConstraints().addAll(r0, r1, r2);

        // 3. 中间棋盘：复用你的缩放逻辑
        initGrid();

        leftPane.add(statsTable, 0, 0);

        // 2) 在 createContent() 里，Reset 按钮绑定成宽度的 80%，高度绑定为宽度的 40%
        Button resetBtn = new Button("Reset");
        resetBtn.setOnAction(e -> {
            onReset();
            updateStats();
        });
// 宽度 80%
        resetBtn.prefWidthProperty().bind(leftPane.widthProperty().multiply(1));
// 高度 = 宽度 * 0.4（可根据你需要调整比例）
        resetBtn.prefHeightProperty().bind(resetBtn.prefWidthProperty().multiply(0.33));
// 2) 中间：Reset 按钮，居中，宽度 80% 左侧宽度
        resetBtn.setMaxWidth(Double.MAX_VALUE);
        resetBtn.setOnAction(e -> onReset());
        GridPane.setHalignment(resetBtn, HPos.CENTER);
        GridPane.setValignment(resetBtn, VPos.CENTER);
        leftPane.add(resetBtn, 0, 1);

// 3) 底部：动画区 Pane（正方形），放左下角
        Pane animPane = new Pane();
// 不需要背景
// 绑定宽高为正方形
        animPane.prefHeightProperty().bind(leftPane.widthProperty());
        animPane.prefWidthProperty().bind(animPane.prefHeightProperty());
        StackPane animHolder = new StackPane(animPane);
        animHolder.setAlignment(Pos.BOTTOM_LEFT);
        leftPane.add(animHolder, 0, 2);

// 把 leftPane 加入根布局
        root.add(leftPane, 0, 0);

        StackPane holder = new StackPane(boardGroup);
        holder.setAlignment(Pos.CENTER);
        holder.setMinSize(0, 0);
        boardGroup.getTransforms().add(new Scale(1, 1, boardWidth/2, boardHeight/2));
        holder.layoutBoundsProperty().addListener((o, oldB, newB) -> {
            double sX = newB.getWidth()  / boardWidth  * 0.75;
            double sY = newB.getHeight() / boardHeight * 0.75;
            double s  = Math.min(sX, sY);
            gridPane.setScaleX(s);
            gridPane.setScaleY(s);
        });
        root.add(holder, 1, 0);

        // 4. 右侧排行榜：列宽按比例，Name 列自动换行
        initRankingTable();
        rankingTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        // 假设 10% / 60% / 30%
        TableColumn<Score, ?> rankCol = rankingTable.getColumns().get(0);
        TableColumn<Score,String> nameCol =
                (TableColumn<Score,String>) rankingTable.getColumns().get(1);
        TableColumn<Score, ?> timeCol = rankingTable.getColumns().get(2);
        rankCol.prefWidthProperty().bind(rankingTable.widthProperty().multiply(0.10));
        nameCol.prefWidthProperty().bind(rankingTable.widthProperty().multiply(0.60));
        timeCol.prefWidthProperty().bind(rankingTable.widthProperty().multiply(0.30));
        // Name 列换行
        nameCol.setCellFactory(col -> new TableCell<Score, String>() {
            Label wrap = new Label();
            {
                wrap.setWrapText(true);
                wrap.prefWidthProperty().bind(col.widthProperty().subtract(10));
                setGraphic(wrap);
                setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            }
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                wrap.setText(empty ? null : item);
            }
        });

        // 4.1 绑定并排序
        SortedList<Score> sorted = new SortedList<>(rawRanking);
        sorted.comparatorProperty().bind(rankingTable.comparatorProperty());
        rankingTable.setItems(sorted);
        rankingTable.getSortOrder().setAll(timeCol);
        timeCol.setSortType(TableColumn.SortType.ASCENDING);

        VBox right = new VBox(10, rankingTable);
        right.setFillWidth(true);
        VBox.setVgrow(rankingTable, Priority.ALWAYS);
        root.add(right, 2, 0);

        return root;
    }

    private void initStatsTable() {
        statsTable.getColumns().clear();
        TableColumn<Stat, String> nameCol  = new TableColumn<>("Stat");
        TableColumn<Stat, String> valueCol = new TableColumn<>("Value");

        nameCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().name));
        valueCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().value));

        statsTable.getColumns().addAll(nameCol, valueCol);
        statsTable.setItems(statsList);

        statsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        nameCol.prefWidthProperty().bind(statsTable.widthProperty().multiply(0.5));
        valueCol.prefWidthProperty().bind(statsTable.widthProperty().multiply(0.5));
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

    private void updateStats() {
        Platform.runLater(() -> {
            statsList.setAll(
                    new Stat("Time",  timerLabel.getText()),
                    new Stat("Mines", "String.valueOf(board.getRemainingMines())")
            );
        });
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
            showAllMines();
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

    private static class Stat {
        final String name, value;
        Stat(String n, String v) { name = n; value = v; }
    }
}
