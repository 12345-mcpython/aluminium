package com.laosun.aluminium.models.skill;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.data.SkillData;
import com.laosun.aluminium.models.CanHit;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;


/**
 * The **generic** implementation that resolves a skill from the data by
 * {@code (cid, slot, level)}.
 *
 * <p>⚠ There used to be a `// TODO: DELETE IT BECAUSE OF EVERY CHARACTERS HAS EVERY SKILLS` here —
 * that comment is **misleading** and has been deleted. What it probably meant to say was "a skill should
 * not be a Java class each", but that is **the other direction** (the P8-0 three-way split (三分法):
 * mechanics go through data/triggers, only escape-hatch (逃生舱) cases get a class), which in fact shows
 * that this class is right: a skill is **data**, locatable with the three keys {@code (cid, slot, level)},
 * with no need for 93 characters × 6 skills = 558 classes.
 *
 * <p>The claim "every character has every skill" does not hold either: a character only equips the
 * **always-on** slots in {@link com.laosun.aluminium.Constant#SKILL_SLOT}
 * (the overworld basic attack / technique are attached by {@code Battle.startBattle()}, and the summon slot
 * belongs to P9-4).
 */
public class DefaultSkill extends Skill {
    private final int cid;
    private final int skillId;
    private final int level;

    private static final ConcurrentMap<String, SkillData> DATA_CACHE = new ConcurrentHashMap<>();

    public DefaultSkill(int cid, int skillId, int level) {
        this.cid = cid;
        this.skillId = skillId;
        this.level = level;
    }

    @Override
    public int getLevel() {
        return level;
    }

    @Override
    public int getCid() {
        return cid;
    }

    /**
     * ⚠ Despite the field name, {@code skillId} here is the <b>slot</b> — see the constructor call.
     */
    @Override
    public int getSkillSlot() {
        return skillId;
    }

    @Override
    public SkillData getData() {
        String key = cid + "_" + skillId;
        return DATA_CACHE.computeIfAbsent(key, k -> SkillData.init(cid, skillId));
    }

    // Default behavior: apply a target to the damage
    @Override
    public void execute(Battle battle, CanHit user, List<? extends CanHit> target) {
        SkillExecutor.execute(battle, this, user, target);
    }
}
