package com.laosun.aluminium.models;

import com.laosun.aluminium.Battle;

import java.util.List;

public interface Skill {
    int getLevel();

    SkillData getData();

    void execute(Battle battle, CanHit user, List<? extends CanHit> target);
}
