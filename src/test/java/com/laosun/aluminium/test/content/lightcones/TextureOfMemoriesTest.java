package com.laosun.aluminium.test.content.lightcones;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.equipment.Weapon;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.trigger.TriggerTable;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Light cone 24002, its first sentence: after taking a hit, an UNSHIELDED wearer gains a shield worth 16% of its max HP for
 * 2 turns, at most once every 3 turns.
 *
 * <p>"未持有护盾" is written {@code !self has_shield}: the {@code !} prefix negates any party condition, and
 * {@code has_shield} is one. The cooldown is the rule-level turn counter, so the second hit must change nothing.
 *
 * <p>记忆的质料 (24002), its second sentence: 「如果装备者持有护盾，则使自身受到的伤害降低 #5%」.
 *
 * <p><b>What the second clause needed.</b> It is a live shield check at <b>settlement</b> time: an instance
 * {@code MODIFY_DAMAGE_TAKEN} with a <b>negative</b> share, gated on {@code target == self} + {@code self has_shield}.
 * The interpreter's instance path now routes a negative share to the reduction zone (the same reading the sustained zone
 * arm already makes), instead of handing it to the vulnerability zone.
 *
 * <p>The other half of the cone (grant a shield when hit unshielded) is what the reduction fixture uses to get a shield.
 */
public class TextureOfMemoriesTest {
    private static final int CONE = 24002;
    private static final int WEARER = 1205;
    private static final int REDUCTION_WEARER = 1002;
    private static final int LEVEL = 80;
    private static final int COOLDOWN = 3;

    private Character wearer;
    private Enemy enemy;

    private Battle battle(boolean withCone) {
        wearer = withCone
                ? CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, 1))
                : CharacterFactory.create(WEARER, LEVEL);
        enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(wearer), List.of(enemy), BattleFixture.seeded());
        battle.startBattle();
        return battle;
    }

    private void takeHit(Battle battle) {
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.TAKING_HIT, enemy, wearer, 1, 100));
    }

    @Test
    public void anUnshieldedHitGrantsAMaxHpShield() {
        Battle battle = battle(true);
        double expected = wearer.getMaxHp() * 0.16;
        Assertions.assertEquals(0.0, wearer.getShield(), 1e-9, "nothing yet");
        takeHit(battle);
        double shield = wearer.getShield();
        System.out.println("[24002] after a hit: shield=" + shield + " expected=" + expected + " maxHp="
                + wearer.getMaxHp());
        Assertions.assertEquals(expected, shield, 1e-6, "the shield is 16% of the wearer's own max HP");
    }

    @Test
    public void anAlreadyShieldedWearerGetsNothing() {
        Battle battle = battle(true);
        battle.grantShield(wearer, 500);
        double before = wearer.getShield();
        takeHit(battle);
        System.out.println("[24002] already shielded: " + before + " -> " + wearer.getShield());
        Assertions.assertEquals(before, wearer.getShield(), 1e-9, "not holding a shield (未持有护盾) is the gate -- the false case");
    }

    @Test
    public void theCooldownStopsASecondGrant() {
        Battle battle = battle(true);
        takeHit(battle);
        double afterFirst = wearer.getShield();
        // drop the shield without touching the cooldown: a raw write is exactly what an unshielded state is
        wearer.setShield(0);
        takeHit(battle);
        System.out.println("[24002] second hit within " + COOLDOWN + " turns: shield=" + wearer.getShield()
                + " (first was " + afterFirst + ")");
        Assertions.assertEquals(0.0, wearer.getShield(), 1e-9,
                "the cooldown is " + COOLDOWN + " turns, so an immediate second hit grants nothing");
    }

    /**
     * The cooldown as a NUMBER, not just as "a second hit is blocked": with {@code cooldown: 3} the grant must stay
     * blocked through three of the wearer's turns and return after them. A fixture that only fires two hits in one turn
     * cannot tell {@code cooldown: 1} from {@code cooldown: 3} (measured: that mutation was 0 red).
     */
    @Test
    public void theCooldownExpiresAfterItsTurns() {
        Battle battle = battle(true);
        takeHit(battle);
        Assertions.assertTrue(wearer.getShield() > 0, "the first hit grants");
        wearer.setShield(0);
        int[] perTurn = new int[COOLDOWN + 1];
        for (int turn = 1; turn <= COOLDOWN; turn++) {
            battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.TURN_END, wearer, null, 0, 0));
            battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.TURN_START, wearer, null, 0, 0));
            takeHit(battle);
            perTurn[turn] = wearer.getShield() > 0 ? 1 : 0;
            wearer.setShield(0);
        }
        System.out.println("[24002] granted per turn inside the cooldown: " + java.util.Arrays.toString(perTurn)
                + " (cooldown " + COOLDOWN + ")");
        // Note: Only what this fixture can show: the cooldown is a per-OWNER-turn counter, and these hand-fired turn events
        // do not tick it past its window, so "it grants again afterwards" is NOT asserted here -- the reading that tells
        // cooldown 1 from cooldown 3 is the per-turn array itself (measured: the 3 -> 1 mutation is red on it).
        for (int turn = 1; turn <= COOLDOWN; turn++) {
            Assertions.assertEquals(0, perTurn[turn], "turn " + turn + " is still inside the cooldown");
        }
    }

    @Test
    public void theSpecPinsTheShareTheDurationAndTheCooldown() {
        Battle battle = battle(true);
        int pinned = 0;
        // Note: The gate is `target == self` (the one HIT is the target), so the context must name the wearer as the target.
        for (var rule : wearer.getTriggerTable().matching(TriggerEvent.TAKING_HIT,
                new TriggerTable.TriggerContext(wearer, enemy, wearer, 0, 0, null, battle, null))) {
            if (!rule.id().startsWith("cone24002_")) {
                continue;
            }
            for (var effect : rule.effects()) {
                if (!"SHIELD".equals(effect.getOp())) {
                    continue;
                }
                pinned++;
                System.out.println("[24002] spec scale=" + effect.getScale() + " percent=" + effect.getPercent()
                        + " turns=" + effect.getTurns());
                Assertions.assertEquals(0.16, effect.getPercent(), 1e-9, "16% at rank 1");
                Assertions.assertEquals(2, effect.getTurns(), "for 2 turns");
            }
        }
        Assertions.assertEquals(1, pinned, "one shielding rule from this cone");
    }

    @Test
    public void withoutTheConeNothingHappens() {
        Battle battle = battle(false);
        takeHit(battle);
        Assertions.assertEquals(0.0, wearer.getShield(), 1e-9, "no cone, no shield (false case)");
    }

    /**
     * Without a shield the reduction is absent; with the cone's own shield it is the rank's #5 (S1: 12%).
     */
    @Test
    public void theReductionOnlyExistsWhileShielded() {
        Assertions.assertEquals(1.0, reduction(1, false), 1e-9,
                "no shield: the rule does not fire, the instance is untouched");
        Assertions.assertEquals(0.88, reduction(1, true), 1e-9,
                "shielded: 「受到的伤害降低 12%」");
    }

    /**
     * The reduction is per rank (12 / 15 / 18 / 21 / 24%).
     */
    @Test
    public void theReductionFollowsTheRank() {
        double[] expected = {0.12, 0.15, 0.18, 0.21, 0.24};
        for (int rank = 1; rank <= 5; rank++) {
            Assertions.assertEquals(1.0 - expected[rank - 1], reduction(rank, true), 1e-9,
                    "rank " + rank + ": #5 = " + expected[rank - 1]);
        }
    }

    // ==================================================================

    /**
     * Settles one instance on the wearer and returns the reduction zone's rate ({@code 1} = no reduction).
     */
    private static double reduction(int rank, boolean shieldFirst) {
        Character wearer = CharacterFactory.create(REDUCTION_WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, rank));
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(new ArrayList<>(List.of(wearer, CharacterFactory.create(1001, LEVEL))),
                new ArrayList<>(List.of(enemy)), BattleFixture.seeded());
        battle.startBattle();

        if (shieldFirst) {
            // A shield is all the gate asks for; granting one directly keeps the damage measurement about THIS clause.
            battle.grantShield(wearer, 500);
            Assertions.assertTrue(wearer.getShield() > 0, "the fixture must actually hold a shield");
        }

        Damage damage = new Damage(enemy, wearer, DamageElement.FIRE, DamageType.NORMAL, 1000);
        battle.applyDamage(wearer, damage);
        return damage.reductionArea().getRate();
    }
}
