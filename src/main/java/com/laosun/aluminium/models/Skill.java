package com.laosun.aluminium.models;

import com.laosun.aluminium.Battle;

import java.util.List;

public abstract class Skill {
    public abstract int getLevel();

    public abstract SkillData getData();

    public abstract void execute(Battle battle, CanHit user, List<? extends CanHit> target);
}
