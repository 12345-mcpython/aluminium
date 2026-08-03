package com.laosun.aluminium.models.tests;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Skill;
import com.laosun.aluminium.models.SkillData;

import java.util.List;

public class TestSkillGroup1 {
    // 对指定敌方单体造成等同于#1%攻击力的冰属性伤害。
    public static class TestSkill1 extends Skill {
        private final int level;
        private static final SkillData DATA = SkillData.init(1001, 3);

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
        public void execute(Battle battle, CanHit user, List<? extends CanHit> target) {
            CanHit c = target.getFirst();
            List<Double> params = getData().getSkills().get(level - 1);

            double multiplier = (params != null && !params.isEmpty()) ? params.getFirst() : 1.0;

            double attack = user.getAttribute(AttributeType.ATTACK).get();
            double baseDamage = attack * multiplier;

            double finalDamage = battle.calculateDamage(user, c, baseDamage, List.of());
            battle.applyDamage(target.getFirst(), finalDamage);
        }
    }

    // 为指定我方单体提供能够抵消等同于三月七 #1 %防御力 + #4 伤害的护盾，
    // 持续 #2 回合。若该目标当前生命值百分比大于等于 #3，被敌方攻击的概率提高 #5 %。
}
