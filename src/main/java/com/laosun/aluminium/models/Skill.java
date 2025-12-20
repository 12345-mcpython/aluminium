package com.laosun.aluminium.models;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@AllArgsConstructor
@Getter
@Setter
public abstract class Skill {
    private int level;
    private String name;
    private String description;

    protected abstract boolean performSkill(List<CanHit> performer);
}
