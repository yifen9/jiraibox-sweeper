package com.yifen9.jiraiboxsweeper.service;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.util.Duration;

public class Timer {
    private final Timeline timeline;
    private int seconds = 0;

    public Timer(Runnable tickCallback) {
        timeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            seconds++;
            tickCallback.run();
        }));
        timeline.setCycleCount(Timeline.INDEFINITE);
    }

    public void reset() {
        seconds = 0;
        timeline.playFromStart();
    }

    public void stop() {
        timeline.stop();
    }

    public int getSeconds() {
        return seconds;
    }
}