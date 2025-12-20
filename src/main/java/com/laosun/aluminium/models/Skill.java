package com.laosun.aluminium.models;

import com.laosun.aluminium.models.event.MoveableEvent;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@AllArgsConstructor
@Getter
@Setter
public abstract class Skill implements MoveableEvent {
    private int level;
    private String name;
    private String description;
    private double[][] skillValue;

    public abstract boolean performSkill(CanHit performer, List<CanHit> accepter);
}
