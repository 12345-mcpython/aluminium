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
        Assertions.assertEquals(1, table.ruleCount(TriggerEvent.ULT_CAST), "the Shock plus its immediate tick");
        Assertions.assertEquals(1, table.ruleCount(TriggerEvent.BATTLE_START), "the level convention");
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
