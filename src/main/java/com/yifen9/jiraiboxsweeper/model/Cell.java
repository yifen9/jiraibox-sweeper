package com.yifen9.jiraiboxsweeper.model;

public class Cell {
    public boolean isMine = false;
    public boolean isSeen = false;
    public boolean isFlag = false;
    public int adjMines = 0;
}