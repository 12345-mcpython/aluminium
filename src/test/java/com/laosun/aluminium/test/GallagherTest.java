package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.data.TriggerTables;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 加拉赫 (1301), from his own file (2026-09-28): 【酩酊】 twice over, 行迹 天然酵母's advance, 星魂 1, and the talent's heal.
 *
 * <p><b>What it needed.</b> Nothing new — and one wrong assumption corrected: `attacker` is a legal target selector
 * (`TriggerInterpreter.TARGET_SELECTORS`), so 「回复**攻击者** 640 点生命值」 is expressible. The file's earlier claim that no
 * spelling names the attacker came from reading `TargetSelector.java`, which is a different class.
 */
public class GallagherTest {
    private static final int GALLAGHER = 1301;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /** The 秘技's in-battle half puts 【酩酊】 on the whole enemy side at battle start. */
    @Test
    public void theStateComesFromBattleStartToo() {
        Fixture f = new Fixture();
        Assertions.assertTrue(f.enemy.getBuffManager().hasState("酩酊"),
                "「进入战斗后使敌方全体陷入【酩酊】状态，持续2回合」");
    }

    /** Census: his file carries the two ULT_CAST rules, the talent's heal and the trace. */
    @Test
    public void hisFileCarriesTheClauses() {
        Assertions.assertEquals(2, TriggerTables.of(GALLAGHER).ruleCount(TriggerEvent.ULT_CAST),
                "「使敌方全体陷入【酩酊】状态」 and 行迹 天然酵母's 「行动提前100%」");
        Assertions.assertEquals(1, TriggerTables.of(GALLAGHER).ruleCount(TriggerEvent.ALLY_ATTACK),
                "the talent's 「每次受到我方角色攻击后」 heal");
    }

    /** ⚠ The heal's target is the ATTACKER: an ally's attack heals the ally, not Gallagher. */
    @Test
    public void hisFirstEidolonIsGated() {
        Fixture atE0 = new Fixture(0);
        Fixture atE1 = new Fixture(1);

        Assertions.assertTrue(atE1.gallagher.getAttribute(AttributeType.EFFECT_RESISTANCE).get()
                        > atE0.gallagher.getAttribute(AttributeType.EFFECT_RESISTANCE).get(),
                "「效果抵抗提高50%」 -- 星魂 1 adds it");
    }

    /** ⚠ The shipped clause really swaps the slot, and ending the swap puts the original back. */
    @Test
    public void theShippedSwapReplacesAndRestores() {
        Fixture f = new Fixture();
        com.laosun.aluminium.models.skill.Skill before =
                f.gallagher.getSkills().get(com.laosun.aluminium.enums.SkillType.COMMON);

        f.battle.castImmediate(f.gallagher.getSkills().get(com.laosun.aluminium.enums.SkillType.ULTRA),
                f.gallagher, List.of(f.enemy));

        com.laosun.aluminium.models.skill.Skill after =
                f.gallagher.getSkills().get(com.laosun.aluminium.enums.SkillType.COMMON);
        Assertions.assertNotSame(before, after,
                "「并将下一次普攻强化为【酒花奔涌】」 — the file's skill_id mapped and the slot was swapped");
        Assertions.assertEquals(130108, after.getSkillSlot(), "…for the enhanced attack's own data row");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private static final class Fixture {
        private final Character gallagher;
        private final Character ally = CharacterFactory.create(ALLY, LEVEL);
        private final Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        private final Battle battle;

        private Fixture() {
            this(0);
        }

        private Fixture(int eidolon) {
            gallagher = CharacterFactory.create(GALLAGHER, LEVEL, true, null, null, eidolon);
            battle = new Battle(List.of(gallagher, ally), List.of(enemy), fixed());
            battle.startBattle();
        }
    }

    private static Random fixed() {
        return new Random() {
            @Override
            public double nextDouble() {
                return 0.0;
            }
        };
    }
}
