package com.laosun.aluminium.models;

import java.util.ArrayList;

public class Relic {
    public Attribute mainAttribute;
    public ArrayList<Attribute> subAttributes;

    public record Attribute(String name, double base, double bonus) {
    }
}
