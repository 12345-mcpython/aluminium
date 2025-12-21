package com.laosun.aluminium.models;

import com.laosun.aluminium.enums.SkillAttackType;
import com.laosun.aluminium.enums.SkillEffectType;
import com.laosun.aluminium.enums.SkillType;
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

    private SkillType skillType;
    private SkillAttackType skillAttackType;
    private SkillEffectType skillEffectType;

    public abstract boolean performSkill(CanHit performer, List<CanHit> accepter);
}
