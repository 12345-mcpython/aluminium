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

    /** \u26a0 The Shock lands with its own per-turn damage, and the same sentence makes it settle once more. */
    @Test
    public void herUltimateAppliesShockAndMakesItSettleOnce() {
        Fixture f = new Fixture();
        double before = f.enemy.getCurrentHp();

        f.battle.castImmediate(f.kafka.getSkills().get(SkillType.ULTRA), f.kafka, List.of(f.enemy));

        Assertions.assertTrue(f.enemy.getBuffManager().hasState("触电"),
                "\u300c\u6709100%\u7684\u57fa\u7840\u6982\u7387\u4f7f\u53d7\u5230\u653b\u51fb\u7684\u654c\u65b9\u76ee\u6807\u9677\u5165\u89e6\u7535\u72b6\u6001\u300d");
        Assertions.assertFalse(f.enemy.getBuffManager().allBuffsOf(DotBuff.class).isEmpty(), "\u2026as a damage-over-time state");
        Assertions.assertTrue(f.enemy.getCurrentHp() < before,
                "the ultimate's own damage plus the extra instance \u300c\u7acb\u5373\u4ea7\u751f\u76f8\u5f53\u4e8e\u539f\u4f24\u5bb3 100% \u7684\u4f24\u5bb3\u300d");
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

    /** \u26a0 An ALLY's basic attack makes Kafka strike again; her own must not. */
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
                "\u300c\u5e76\u6709100%\u7684\u57fa\u7840\u6982\u7387\u4f7f\u53d7\u5230\u653b\u51fb\u7684\u654c\u65b9\u76ee\u6807\u9677\u5165\u4e0e\u7ec8\u7ed3\u6280\u76f8\u540c\u7684\u89e6\u7535\u72b6\u6001\u300d \u2014 the follow-up really fired");
    }

    /** \u26a0 Her OWN basic attack must not grant the follow-up: observable is the Shock it would apply. */
    @Test
    public void herOwnBasicAttackDoesNotTriggerTheFollowUp() {
        Fixture f = new Fixture();
        Assertions.assertFalse(f.enemy.getBuffManager().hasState("触电"), "precondition: no Shock");

        f.battle.castImmediate(f.kafka.getSkills().get(SkillType.COMMON), f.kafka, List.of(f.enemy));

        Assertions.assertFalse(f.enemy.getBuffManager().hasState("触电"),
                "\u300c\u5f53\u5361\u8299\u5361\u7684**\u961f\u53cb**\u5bf9\u654c\u65b9\u76ee\u6807\u65bd\u653e\u666e\u653b\u540e\u300d -- HER OWN attack is not an ally's, so no follow-up and no Shock");
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
