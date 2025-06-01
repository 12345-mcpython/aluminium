package com.laosun.aluminium.models;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class Weapon {
    public record Name(String chinese, String english) {
    }

    private Name name;
    private String description;
    private double health;
    private double attack;
    private double defense;
    private String type;
}
