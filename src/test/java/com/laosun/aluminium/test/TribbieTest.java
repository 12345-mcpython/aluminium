package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.data.TriggerTables;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1403 Tribbie, from her own file (2026-09-29, round 190): an 18%-Max-HP follow-up on a TEAMMATE's ultimate, and the Numinosity state.
 *
 * <p>The follow-up needs two things that did not exist together before: `actor is_other_ally` (round 14) so her own ultimate does not trigger it, and the
 * Max HP scale (round 189) so 18% of HER Max HP can be stated at all.
 */
public class TribbieTest {
    private static final int TRIBBIE = 1403;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /** Note: A teammate's ultimate triggers it; her own does not. */
    @Test
    public void onlyATeammatesUltimateTriggersTheFollowUp() {
        double fromAlly = lossWhenTheEventComesFrom(true);
        double fromSelf = lossWhenTheEventComesFrom(false);

        Assertions.assertTrue(fromAlly > 0, "「我方其他角色施放终结技后」 -- a teammate's ultimate must trigger it: " + fromAlly);
        Assertions.assertEquals(0.0, fromSelf, 1e-9,
                "「我方**其他**角色」 -- her own ultimate is excluded, and firing the event by hand means no ultimate damage is mixed in: " + fromSelf);
    }

    /** Note: "进入战斗时获得[神启]，持续3回合", and the trace's own battle-start energy. */
    @Test
    public void theTechniqueGrantsNuminosity() {
        Character tribbie = CharacterFactory.create(TRIBBIE, LEVEL);
        Enemy enemy = com.laosun.aluminium.models.enemy.EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(tribbie), List.of(enemy), fixed());
        battle.markTechniqueUsed(tribbie);

        battle.startBattle();

        Assertions.assertTrue(tribbie.getBuffManager().hasState("神启"),
                "「使用秘技后，进入战斗时获得【神启】」");
    }

    /** Census: the ultimate, the skill, the technique, two traces and the convention all live on BATTLE_START/other events as stated. */
    @Test
    public void herFileCarriesTheClauses() {
        var table = TriggerTables.of(TRIBBIE);
        // 4 since 2026-10-02: `ult_zone_state` joins the three -- "结界持续期间" had no state to name,
        // and 1415's ode of passage needs one ("缇宝的结界的附加伤害").
        Assertions.assertEquals(4, table.ruleCount(TriggerEvent.ULT_CAST),
                "the follow-up trigger, and (2026-09-29) the zone's 「敌方目标受到的伤害提高30%」" + " with `ticks_on: self` for the zone's own clock");
        Assertions.assertEquals(1, table.ruleCount(TriggerEvent.SKILL_CAST), "Numinosity");
        Assertions.assertEquals(1, table.ruleCount(TriggerEvent.FOLLOW_UP), "the damage boost");
        Assertions.assertEquals(3, table.ruleCount(TriggerEvent.BATTLE_START),
                "the energy trace (pre-existing), the technique and the level convention");
    }

    /**
     * Fires ULT_CAST with either the teammate or Tribbie as the actor, and returns what the enemy lost.
     *
     * <p>Hand-firing the event is the point: a real ultimate would deal its own Max-HP damage (measured 131.for 30%) and the reading would be that number
     * instead of the follow-up's. With the event alone, whatever the enemy loses IS the rule's reaction.
     */
    private static double lossWhenTheEventComesFrom(boolean teammate) {
        Character tribbie = CharacterFactory.create(TRIBBIE, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = com.laosun.aluminium.models.enemy.EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(tribbie, ally), List.of(enemy), fixed());
        battle.startBattle();
        double before = enemy.getCurrentHp();
        battle.fireTriggers(TriggerEvent.ULT_CAST, teammate ? ally : tribbie, tribbie, 0, 0);
        return before - enemy.getCurrentHp();
    }


    /** Note: The number itself: 18% of her Max HP must be 0.36 of a hand-built 50% in the same pipeline. */
    @Test
    public void theFollowUpDealsEighteenPercentOfHerMaxHp() {
        double content = followUpLoss(false);
        double reference = followUpLoss(true);

        Assertions.assertTrue(reference > 0, "the reference must deal damage");
        Assertions.assertEquals(0.18 / 0.50, content / reference, 0.05,
                "content " + content + " vs reference " + reference + " (expected " + (0.18 / 0.50) + ")");
    }

    /** Fires one teammate ULT_CAST, optionally replacing her table with a single 50%-Max-HP reference rule. */
    private static double followUpLoss(boolean useReference) {
        Character tribbie = CharacterFactory.create(TRIBBIE, LEVEL);
        if (useReference) {
            com.laosun.aluminium.beans.EffectSpec effect = new com.laosun.aluminium.beans.EffectSpec();
            TriggerSpecs.set(effect, "op", "DAMAGE");
            TriggerSpecs.set(effect, "scale", "owner_max_hp");
            TriggerSpecs.set(effect, "percent", 0.50);
            TriggerSpecs.set(effect, "element", "Quantum");
            TriggerSpecs.set(effect, "target", "all_enemies");
            tribbie.setTriggerTable(new com.laosun.aluminium.models.TriggerTable(TRIBBIE,
                    List.of(TriggerSpecs.rule(TriggerEvent.ULT_CAST.name(), List.of("actor is_other_ally"), effect))));
        }
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = com.laosun.aluminium.models.enemy.EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(tribbie, ally), List.of(enemy), fixed());
        battle.startBattle();
        double before = enemy.getCurrentHp();
        battle.fireTriggers(TriggerEvent.ULT_CAST, ally, tribbie, 0, 0);
        return before - enemy.getCurrentHp();
    }

    private static Random fixed() {
        return new Random() {
            @Override
            public double nextDouble() {
                return 1.0;
            }
        };
    }
}
