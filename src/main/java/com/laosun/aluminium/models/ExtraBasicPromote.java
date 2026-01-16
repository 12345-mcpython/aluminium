package com.laosun.aluminium.models;


public record ExtraBasicPromote(double health, double defence, double attack, double speed, double healthPercent,
                                double defencePercent, double attackPercent, double speedPercent) {
    public ExtraBasicPromote() {
        this(0, 0, 0, 0, 0, 0, 0, 0);
    }
}
