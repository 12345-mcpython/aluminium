package com.laosun.aluminium.models.tests;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Skill;
import com.laosun.aluminium.models.SkillData;

import java.util.List;

public class TestSkillGroup1 {
    // 对指定敌方单体造成等同于<color=#f29e38ff><unbreak>#1[i]%</unbreak></color>攻击力的冰属性伤害。
    public static class TestSkill1 implements Skill {
        private final int level;
        private static final SkillData DATA = SkillData.init(1001, 1);

        public TestSkill1(int level) {
            this.level = level;
        }

        @Override
        public int getLevel() {
            return level;
        }

        @Override
        public SkillData getData() {
            return DATA;
        }

        @Override
        public void execute(Battle battle, CanHit user, List<CanHit> target) {
            CanHit c = target.getFirst();
            List<Double> params = getData().getSkills().get(level - 1);

            double multiplier = (params != null && !params.isEmpty()) ? params.getFirst() : 1.0;

            double attack = user.getAttribute(AttributeType.ATTACK).get();
            double baseDamage = attack * multiplier;

            double finalDamage = battle.calculateDamage(user, c, baseDamage, List.of());
            battle.applyDamage(c, finalDamage);
        }
    }
}
