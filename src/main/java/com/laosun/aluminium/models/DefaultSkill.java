package com.laosun.aluminium.models;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

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

    @Override
    public void execute(Battle battle, CanHit user, List<? extends CanHit> target) {
        if (target == null || target.isEmpty()) {
            return;
        }
        List<List<Double>> skillParams = getData().getSkills();
        if (skillParams.isEmpty() || level < 1 || level > skillParams.size()) {
            return;
        }
        CanHit c = target.getFirst();
        List<Double> params = skillParams.get(level - 1);

        double multiplier = (params != null && !params.isEmpty()) ? params.getFirst() : 1.0;

        double attack = user.getAttribute(AttributeType.ATTACK).get();
        double baseDamage = attack * multiplier;

        double finalDamage = battle.calculateDamage(user, c, baseDamage, List.of());
        battle.applyDamage(c, finalDamage);
    }
}
