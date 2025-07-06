package com.laosun.aluminium.models;

import com.laosun.aluminium.beans.Translate;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class Weapon {
    private Translate name;
    private String description;
    private double health;
    private double attack;
    private double defense;
    private String type;
}
