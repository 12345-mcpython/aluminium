package com.laosun.aluminium.test.content.lightcones;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.equipment.Weapon;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.trigger.TriggerPayload;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 不息的演算 (24004): 「施放攻击后，每击中一名敌方目标，使攻击力额外提高 #2%，最多叠加 5 次，持续至下次攻击后」.
 *
 * <p><b>What it needed.</b> {@code times_from: "hit_count"} on {@code ADD_STACK} stacks once per enemy hit, and
 * {@code per_stack_live} makes the ATK modifier read that counter on every attribute computation (a snapshot would freeze
 * at zero). The window 「持续至下次攻击后」 is the event order itself: {@code ATTACK_FINISHED} fires after the attack has
 * settled, so clearing the old layers and adding the new ones there keeps the old ones alive <b>during</b> the next
 * attack.
 */
public class ConeTwentyFourThousandFourTest {

    private static final int LEVEL = 80;
    private static final int WEARER = 1002;
    private static final int CONE = 24004;

    /**
     * Each enemy hit adds 4% of base ATK (S1), the next attack replaces the layers instead of adding to them, and five is
     * the ceiling.
     */
    @Test
    public void eachHitAddsAShareAndTheNextAttackReplacesTheLayers() {
        Fixture f = new Fixture(1);
        double step = 0.04 * f.base;
        Assertions.assertEquals(0.0, f.attack() - f.start, 1e-9, "no attack yet, no layer");

        f.attackFinished(3);
        Assertions.assertEquals(3, f.stacks(), "three enemies hit: three layers");
        Assertions.assertEquals(3 * step, f.attack() - f.start, 1e-9,
                "each layer is #2 of BASE attack (3 x 4%)");

        f.attackFinished(2);
        Assertions.assertEquals(2, f.stacks(), "the next attack replaces the layers (持续至下次攻击后)");
        Assertions.assertEquals(2 * step, f.attack() - f.start, 1e-9, "two layers, not five");

        f.attackFinished(6);
        Assertions.assertEquals(5, f.stacks(), "「最多叠加 5 次」");
        Assertions.assertEquals(5 * step, f.attack() - f.start, 1e-9);

        f.attackFinished(0);
        Assertions.assertEquals(0, f.stacks(), "an attack that hit nobody clears the layers");
        Assertions.assertEquals(0.0, f.attack() - f.start, 1e-9);
    }

    /**
     * The per-layer share is per rank (#2 = 4 / 5 / 6 / 7 / 8%).
     */
    @Test
    public void theShareFollowsTheRank() {
        double[] expected = {0.04, 0.05, 0.06, 0.07, 0.08};
        for (int rank = 1; rank <= 5; rank++) {
            Fixture f = new Fixture(rank);
            f.attackFinished(2);
            Assertions.assertEquals(2 * expected[rank - 1] * f.base, f.attack() - f.start, 1e-9,
                    "rank " + rank + ": #2 = " + expected[rank - 1]);
        }
    }

    // ==================================================================

    private static final class Fixture {
        private final Character wearer;
        private final Battle battle;
        private final double base;
        private final double start;

        private Fixture(int rank) {
            wearer = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, rank));
            battle = new Battle(new ArrayList<>(List.of(wearer, CharacterFactory.create(1001, LEVEL))),
                    new ArrayList<>(List.of(BattleFixture.monster())), BattleFixture.seeded());
            battle.startBattle();
            base = wearer.getAttribute(AttributeType.ATTACK).baseValue();
            start = wearer.getAttribute(AttributeType.ATTACK).get();
        }

        private void attackFinished(int hits) {
            battle.fire(TriggerPayload.Simple.of(TriggerEvent.ATTACK_FINISHED, wearer,
                    battle.enemies().getFirst(), hits, 0));
        }

        private int stacks() {
            return wearer.getBuffManager().stacksOf("演算");
        }

        private double attack() {
            return wearer.getAttribute(AttributeType.ATTACK).get();
        }
    }
}
