package com.yifen9.jiraiboxsweeper.model;

import java.util.Random;

public class Board {
    public boolean isInit;

    private static final int ROWS = 9;
    private static final int COLS = 9;
    private static final int MINES = 10;

    private Cell[][] grid;

    public Board() {
        initBoard();
    }

    private void initBoard() {
        isInit = false;
        grid = new Cell[ROWS][COLS];
        initCells();
        initMines();
        calAdjs();
    }

    private void initCells() {
        for (int i = 0; i < ROWS; i++)
            for (int j = 0; j < COLS; j++)
                grid[i][j] = new Cell();
    }

    private void initMines() {
        Random rnd = new Random();
        int placed = 0;
        while (placed < MINES) {
            int r = rnd.nextInt(ROWS), c = rnd.nextInt(COLS);
            if (!getCell(r, c).isMine) {
                getCell(r, c).isMine = true;
                placed++;
            }
        }
    }

    private void calAdjs() {
        for (int i = 0; i < ROWS; i++) {
            for (int j = 0; j < COLS; j++) {
                if (!getCell(i, j).isMine) {
                    int count = 0;
                    for (int dr = -1; dr <=1; dr++) {
                        for (int dc = -1; dc <= 1; dc++) {
                            int nr = i + dr, nc = j + dc;
                            if (inBounds(nr, nc) && getCell(nr, nc).isMine) count++;
                        }
                    }
                    getCell(i, j).adjMines = count;
                }
            }
        }
    }

    private boolean inBounds(int r, int c) {
        return r >= 0 && r < ROWS && c >= 0 && c < COLS;
    }

    public boolean isEmpty(int r, int c) {
        Cell cell = getCell(r, c);
        return cell.adjMines == 0 && !cell.isMine;
    }

    public boolean reveal(int r, int c) {
        isInit = true;
        if (!inBounds(r, c)) return false;
        Cell cell = getCell(r, c);
        if (cell.isSeen || cell.isFlag) return false;

        cell.isSeen = true;

        if (cell.isMine) return true;

        if (cell.adjMines == 0)
            for (int dr = -1; dr <= 1; dr++)
                for (int dc = -1; dc <= 1; dc++)
                    if (dr != 0 || dc != 0)
                        reveal (r + dr, c + dc);

        return false;
    }

    public boolean flag(int r, int c) {
        if (!inBounds(r, c)) return false;
        Cell cell = getCell(r, c);
        if (cell.isSeen) return false;
        cell.isFlag = !cell.isFlag;
        return true;
    }

    public boolean expand(int r, int c) {
        if (!inBounds(r, c)) return false;
        Cell cell = getCell(r, c);
        if (!cell.isSeen || cell.isMine || cell.adjMines == 0) return false;
        for (int dr = -1; dr <= 1; dr++)
            for (int dc = -1; dc <= 1; dc++)
                if ((dr != 0 || dc != 0) && !cell.isFlag)
                    reveal (r + dr, c + dc);
        return true;
    }

    public boolean isCleared() {
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                Cell cell = getCell(r, c);
                if (!cell.isMine && !cell.isSeen) return false;
            }
        }
        return true;
    }

    public void revealAll() {
        for (int r = 0; r < ROWS; r++)
            for (int c = 0; c < COLS; c++)
                getCell(r, c).isSeen = true;
    }

    public void reset() {
        initBoard();
    }

    public Cell getCell (int r, int c) {
        if (!inBounds(r, c)) return null;
        return grid[r][c];
    }
}
