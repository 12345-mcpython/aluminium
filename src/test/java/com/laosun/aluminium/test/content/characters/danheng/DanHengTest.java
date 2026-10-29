package com.laosun.aluminium.test.content.characters.danheng;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.data.TriggerTables;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1002 Dan Heng, from his own file: the talent that answers "an ally aimed a skill at me".
 *
 * <p>Everything it needs already existed: `SKILL_CAST` carries the aim (so `target == self` means "I was the one aimed at"),
 * `actor is_ally` says who cast it, `DAMAGE_PENETRATION` is the engine's name for RES PEN (抗性穿透), `until: next_attack` is the duration the
 * sentence asks for, and `cooldown` is the rule-level field for "2回合后可再次触发".
 */
public class DanHengTest {
    private static final int DANHENG = 1002;
    private static final int ALLY = 1001;
    private static final int LEVEL = 80;

    /**
     * Note: An ally's skill aimed at him raises his penetration; a skill aimed elsewhere does not.
     */
    @Test
    public void anAllysSkillAimedAtHimRaisesHisPenetration() {
        Fixture f = new Fixture();
        double before = f.danheng.getAttribute(AttributeType.DAMAGE_PENETRATION).get();

        f.battle.castImmediate(f.ally.getSkills().get(SkillType.SKILL), f.ally, List.of(f.danheng));

        Assertions.assertTrue(f.danheng.getAttribute(AttributeType.DAMAGE_PENETRATION).get() > before,
                "「当丹恒成为我方技能的施放目标时，下一次攻击的风属性抗性穿透提高36%」 (when Dan Heng becomes the target of one of our Skills, his next attack has 36% more Wind RES PEN): "
                        + before + " -> " + f.danheng.getAttribute(AttributeType.DAMAGE_PENETRATION).get());
    }

    /**
     * Note: The control: an ally's skill aimed at someone ELSE must leave his penetration alone.
     */
    @Test
    public void anAllysSkillAimedElsewhereLeavesItAlone() {
        Fixture f = new Fixture();
        double before = f.danheng.getAttribute(AttributeType.DAMAGE_PENETRATION).get();

        f.battle.castImmediate(f.ally.getSkills().get(SkillType.SKILL), f.ally, List.of(f.ally));

        Assertions.assertEquals(before, f.danheng.getAttribute(AttributeType.DAMAGE_PENETRATION).get(), 1e-9,
                "「成为我方技能的施放目标」 (becomes the target of one of our Skills) -- being on the field is not enough");
    }

    /**
     * Census: every event his file states a clause on, with the number of rules on each.
     *
     * <p>⚠ `ruleCount` counts the WHOLE table, eidolon gating included - it is a census of what the file states,
     * not of what is active at rank 0, so these numbers move when a clause is added.
     */
    @Test
    public void hisFileCarriesTheClauses() {
        var table = TriggerTables.of(DANHENG);
        Assertions.assertEquals(1, table.ruleCount(TriggerEvent.SKILL_CAST), "the talent");
        Assertions.assertEquals(5, table.ruleCount(TriggerEvent.BATTLE_START),
                "the technique opening ATK, the level convention, the two eidolon level statements (3 and 5), and "
                        + "eidolon 2's cooldown amendment");
        Assertions.assertEquals(2, table.ruleCount(TriggerEvent.CRIT_DEALT),
                "the Skill's crit-slow, and eidolon 6's deeper version of it (mutually exclusive on rank)");
        Assertions.assertEquals(1, table.ruleCount(TriggerEvent.ALLY_ATTACK), "trace 绝影");
        Assertions.assertEquals(1, table.ruleCount(TriggerEvent.KILL), "eidolon 4's extra turn");
        Assertions.assertEquals(3, table.ruleCount(TriggerEvent.DEALING_DAMAGE),
                "eidolon 1's crit chance, the ultimate's extra multiplier against a slowed target, and trace 罡风's "
                        + "basic-attack boost");
    }

    private static final class Fixture {
        private final Character danheng = CharacterFactory.create(DANHENG, LEVEL);
        private final Character ally = CharacterFactory.create(ALLY, LEVEL);
        private final Enemy enemy = BattleFixture.monster();
        private final Battle battle = new Battle(List.of(danheng, ally), List.of(enemy), new Random() {
            @Override
            public double nextDouble() {
                return 0.0;
            }
        });

        private Fixture() {
            battle.startBattle();
        }
    }
}
