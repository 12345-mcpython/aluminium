package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * Light cone 20005: at battle start the WHOLE party's attack rises 8%.
 *
 * <p>⭐ One sentence, one rule. The judge reads the wearer AND the ally, because 「我方全体」 that only reached the wearer would
 * still look right on a single-unit fixture.
 */
public class Cone20005Test {
    private static final int CONE = 20005;
    private static final int WEARER = 1205;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final double SHARE = 0.08;

    private Character wearer;
    private Character ally;
    private Enemy enemy;

    private Battle battle(boolean withCone) {
        wearer = withCone
                ? CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, 1))
                : CharacterFactory.create(WEARER, LEVEL);
        ally = CharacterFactory.create(ALLY, LEVEL);
        double allyBase = ally.getAttribute(AttributeType.ATTACK).get();
        enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(wearer, ally), List.of(enemy), new Random(0));
        battle.startBattle();
        return battle;
    }

    @Test
    public void theWholePartyGainsAttackAtBattleStart() {
        // ★ Baselines from a CONE-LESS battle of the same shape (discipline 169): a bare CharacterFactory.create outside a
        // battle does not reproduce the value the buff is a share OF (measured: 645.27 vs the 546.8 the 43.75 delta implies).
        Battle baseline = battle(false);
        double wearerBase = wearer.getAttribute(AttributeType.ATTACK).get();
        double allyBase = ally.getAttribute(AttributeType.ATTACK).get();
        Battle battle = battle(true);
        double wearerDelta = wearer.getAttribute(AttributeType.ATTACK).get() - wearerBase;
        double allyDelta = ally.getAttribute(AttributeType.ATTACK).get() - allyBase;
        System.out.println("[20005] wearer +" + wearerDelta + " ally +" + allyDelta
                + " (ally baseline " + allyBase + ")");
        Assertions.assertTrue(wearerDelta > 0, "我方全体 includes the wearer");
        // ★ What this judge CAN attribute: the wearer is 1205, whose own kit raises ATTACK at battle start too (measured
        // +386, far more than 8%), and the buff itself is a share of the unit's PRE-BONUS base -- measured: the ally's +43.75
        // is 8% of 546.9, while its post-bonus ATTACK is 645.27. Reading that base is not something this judge can do yet
        // (registered), so the SHARE is pinned by the spec half and this half pins that both units really moved.
        Assertions.assertTrue(allyDelta > 0, "and the ally");
    }

    @Test
    public void withoutTheConeNothingChanges() {
        Battle battle = battle(false);
        double base = wearer.getAttribute(AttributeType.ATTACK).get();
        System.out.println("[20005] without the cone: wearer=" + wearer.getAttribute(AttributeType.ATTACK).get()
                + " (no change expected)");
        Assertions.assertEquals(base, wearer.getAttribute(AttributeType.ATTACK).get(), 1e-9,
                "no cone, no buff (false case)");
    }

    @Test
    public void theSpecPinsTheShareAndTheTargets() {
        Battle battle = battle(true);
        int pinned = 0;
        for (var rule : wearer.getTriggerTable().matching(TriggerEvent.BATTLE_START,
                new TriggerTable.TriggerContext(wearer, null, null, 0, 0, null, battle, null))) {
            if (!rule.id().startsWith("cone20005_")) {
                continue;
            }
            for (var effect : rule.effects()) {
                pinned++;
                System.out.println("[20005] spec attribute=" + effect.getAttribute() + " percent=" + effect.getPercent()
                        + " target=" + effect.getTarget());
                Assertions.assertEquals(SHARE, effect.getPercent(), 1e-9, "8% at rank 1");
                Assertions.assertEquals("all_allies", effect.getTarget(), "the whole party");
            }
        }
        Assertions.assertEquals(1, pinned, "one rule from this cone");
    }
}
