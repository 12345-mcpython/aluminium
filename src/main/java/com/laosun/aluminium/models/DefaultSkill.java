package com.laosun.aluminium.models;

import com.laosun.aluminium.Battle;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;


// TODO: DELETE IT BECAUSE OF EVERY CHARACTERS HAS EVERY SKILLS
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
