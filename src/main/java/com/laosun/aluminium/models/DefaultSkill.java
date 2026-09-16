package com.laosun.aluminium.models;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;

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

        DamageElement element = getData().getElement();
        if (element == null) {
            // 非伤害技能（治疗/护盾/上 buff…）：不构造 Damage
            // 全局约定：有伤害要结算 ⇒ 构造 Damage；不造 Damage ⇒ 无伤害
            // TODO P1-8：按 SkillEffectType.isDamaging() 正经分派
            return;
        }
        // TODO P1-8：伤害类型先一律 NORMAL，之后按技能槽位映射 普攻/战技/终结技
        battle.applyDamage(c, new Damage(user, c, element, DamageType.NORMAL, baseDamage));
    }
}
