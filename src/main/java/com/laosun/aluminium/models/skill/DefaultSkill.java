package com.laosun.aluminium.models.skill;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.data.SkillData;
import com.laosun.aluminium.models.CanHit;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;


/**
 * The generic implementation that resolves a skill from the data by
 * {@code (cid, slot, level)}.
 *
 * <p>Note: There used to be a `// TODO: DELETE IT BECAUSE OF EVERY CHARACTERS HAS EVERY SKILLS` here -
 * that comment is misleading and has been deleted. What it probably meant to say was "a skill should
 * not be a Java class each", but that is the other direction (the three-way split (三分法, the three-way split):
 * mechanics go through data/triggers, only escape-hatch (逃生舱, the escape hatch) cases get a class), which in fact shows
 * that this class is right: a skill is data, locatable with the three keys {@code (cid, slot, level)},
 * with no need for 93 characters  x  6 skills = 558 classes.
 *
 * <p>The claim "every character has every skill" does not hold either: a character only equips the
 * always-on slots in {@link com.laosun.aluminium.Constant#SKILL_SLOT}
 * (the overworld basic attack / technique are attached by {@code Battle.startBattle()}, and the summon slot
 * is not implemented yet).
 */
public class DefaultSkill extends Skill {
    private final int cid;
    private final int skillId;
    private final int level;
    /**
     * A skill table supplied by the caller; {@code null} = the static {@code Constant.SKILLS}.
     */
    private final Map<Integer, Map<Integer, com.laosun.aluminium.beans.Skill>> skills;
    private volatile SkillData dataFromTable;

    private static final ConcurrentMap<String, SkillData> DATA_CACHE = new ConcurrentHashMap<>();

    public DefaultSkill(int cid, int skillId, int level) {
        this(cid, skillId, level, null);
    }

    /**
     * @param cid     character id
     * @param skillId skill slot id
     * @param level   skill level
     * @param skills  the skill table, or {@code null} for the static default
     */
    public DefaultSkill(int cid, int skillId, int level,
                        Map<Integer, Map<Integer, com.laosun.aluminium.beans.Skill>> skills) {
        this.cid = cid;
        this.skillId = skillId;
        this.level = level;
        this.skills = skills;
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
     * Note: Despite the field name, {@code skillId} here is the <b>slot</b> - see the constructor call.
     */
    @Override
    public int getSkillSlot() {
        return skillId;
    }

    @Override
    public SkillData getData() {
        if (skills != null) {
            SkillData resolved = dataFromTable;
            if (resolved == null) {
                resolved = SkillData.init(skills, cid, skillId);
                dataFromTable = resolved;
            }
            return resolved;
        }
        String key = cid + "_" + skillId;
        return DATA_CACHE.computeIfAbsent(key, k -> SkillData.init(cid, skillId));
    }

    // Default behavior: apply a target to the damage
    @Override
    public void execute(Battle battle, CanHit user, List<? extends CanHit> target) {
        SkillExecutor.execute(battle, this, user, target);
    }
}
