package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.data.TriggerTables;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.buff.DotBuff;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1005 Kafka, from her own file (2026-09-28, round 145): the ultimate's Shock, and the immediate tick that follows it.
 *
 * <p>Both halves are capabilities the engine already had — `APPLY_DOT` (with the per-turn magnitude) and `TICK_DOT` (round 105) —
 * so this file is also a check that they compose in one rule. The target is hand-made and unresisting, because a 100% BASE
 * chance still rolls (round 131).
 */
public class KafkaTest {
    private static final int KAFKA = 1005;
    private static final int LEVEL = 80;

    /** ⚠ The Shock lands with its own per-turn damage, and the same sentence makes it settle once more. */
    @Test
    public void herUltimateAppliesShockAndMakesItSettleOnce() {
        Fixture f = new Fixture();
        double before = f.enemy.getCurrentHp();

        f.battle.castImmediate(f.kafka.getSkills().get(SkillType.ULTRA), f.kafka, List.of(f.enemy));

        Assertions.assertTrue(f.enemy.getBuffManager().hasState("触电"),
                "「有100%的基础概率使受到攻击的敌方目标陷入触电状态」");
        Assertions.assertFalse(f.enemy.getBuffManager().allBuffsOf(DotBuff.class).isEmpty(), "…as a damage-over-time state");
        Assertions.assertTrue(f.enemy.getCurrentHp() < before,
                "the ultimate's own damage plus the extra instance 「立即产生相当于原伤害 100% 的伤害」");
    }

    /** Census: the composed ultimate and the level convention are where the notes say. */
    @Test
    public void herFileCarriesTheClauses() {
        var table = TriggerTables.of(KAFKA);
        Assertions.assertEquals(2, table.ruleCount(TriggerEvent.ULT_CAST),
                "the Shock plus its immediate tick, and (2026-09-29) the trace that lengthens the shock by a turn");
        Assertions.assertEquals(1, table.ruleCount(TriggerEvent.BATTLE_START), "the level convention");
        Assertions.assertEquals(1, table.ruleCount(TriggerEvent.ALLY_ATTACK),
                "the talent follow-up (moved to the once-per-cast event, which now carries the aim)");
    }

    /** ⚠ An ALLY's basic attack makes Kafka strike again; her own must not. */
    @Test
    public void anAllysBasicAttackTriggersHerFollowUp() {
        Character kafka = CharacterFactory.create(KAFKA, LEVEL);
        Character ally = CharacterFactory.create(1002, LEVEL);
        Enemy enemy = Enemy.fromAttributes("Test Dummy", 200000, 100, 100, 90);
        Battle battle = new Battle(List.of(kafka, ally), List.of(enemy), new Random() {
            @Override
            public double nextDouble() {
                return 0.0;
            }
        });
        battle.startBattle();
        double before = enemy.getCurrentHp();
        battle.castImmediate(ally.getSkills().get(SkillType.COMMON), ally, List.of(enemy));
        double afterAlly = enemy.getCurrentHp();

        Assertions.assertTrue(afterAlly < before,
                "the ally's basic attack lands and Kafka's follow-up adds to it: " + before + " -> " + afterAlly);
        Assertions.assertTrue(enemy.getBuffManager().hasState("触电"),
                "「并有100%的基础概率使受到攻击的敌方目标陷入与终结技相同的触电状态」 — the follow-up really fired");
    }

    /** ⚠ Her OWN basic attack must not grant the follow-up: observable is the Shock it would apply. */
    @Test
    public void herOwnBasicAttackDoesNotTriggerTheFollowUp() {
        Fixture f = new Fixture();
        Assertions.assertFalse(f.enemy.getBuffManager().hasState("触电"), "precondition: no Shock");

        f.battle.castImmediate(f.kafka.getSkills().get(SkillType.COMMON), f.kafka, List.of(f.enemy));

        Assertions.assertFalse(f.enemy.getBuffManager().hasState("触电"),
                "「当卡芙卡的**队友**对敌方目标施放普攻后」 -- HER OWN attack is not an ally's, so no follow-up and no Shock");
    }

    private static final class Fixture {
        private final Character kafka = CharacterFactory.create(KAFKA, LEVEL);
        private final Enemy enemy = Enemy.fromAttributes("Test Dummy", 20000, 100, 100, 90);
        private final Battle battle = new Battle(List.of(kafka), List.of(enemy), new Random() {
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
