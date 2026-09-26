package com.laosun.aluminium.models.tests;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.skill.Skill;
import com.laosun.aluminium.models.skill.SkillData;
import com.laosun.aluminium.models.skill.SkillExecutor;

import java.util.List;

public class TestSkillGroup1 {
    // Deals Ice DMG equal to #1% of ATK to a single enemy target.
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
            SkillExecutor.execute(battle, this, user, target);
        }
    }

    // Provides a single ally with a shield that can offset damage equal to March 7th's
    // #1% DEF + #4, lasting #2 turn(s). If that target's current HP percentage is greater than or
    // equal to #3, the chance of being attacked by enemies increases by #5%.
}
