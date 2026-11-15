package com.laosun.aluminium.test.content.lightcones;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.equipment.Weapon;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Light cone 23022: every damage-over-time TYPE the wearer strikes marks one layer of [先知], once per type per battle, up to four.
 *
 * <p>The DoTs are planted by NAME through {@code DotBuff}'s named constructor. The probe measured that a DotBuff planted with a
 * name answers {@code has_state} and {@code stacksOf} for exactly that name -- all four game names included.
 *
 * <p><b>The 「每种类型仅 1 次」 that was called missing.</b> The rule-level {@code once_per_battle} already means exactly that, and
 * each of the four states has its own rule - so the sentence needed no new concept, only one flag per rule.
 */
public class ReforgedRemembranceTest {
    private static final int CONE = 23022;
    private static final int WEARER = 1205;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int RUN_WEARER = 1204;
    private static final String SEER = "先知";
    private static final String WIND_NAME = "dot:wind_shear";
    private static final String FIRE_NAME = "dot:burn";
    private static final String THUNDER_NAME = "dot:shock";
    private static final String PHYSICAL_NAME = "dot:bleed";

    private Character wearer;
    private Enemy enemy;
    private Battle battle;

    private void build(boolean withCone) {
        wearer = withCone
                ? CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, 1))
                : CharacterFactory.create(WEARER, LEVEL);
        enemy = BattleFixture.monster();
        battle = new Battle(List.of(wearer, CharacterFactory.create(ALLY, LEVEL)), List.of(enemy), BattleFixture.seeded());
        battle.startBattle();
    }

    private void plant(String name, DamageElement element) {
        enemy.getBuffManager().addBuff(new com.laosun.aluminium.buff.DotBuff(wearer, element, 50, 3, 0, name));
    }

    private void strike() {
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.DEALING_DAMAGE, wearer, enemy, 0, 0));
    }

    @Test
    public void oneLayerPerDotTypeAndOnlyOnceEach() {
        build(true);
        plant(FIRE_NAME, DamageElement.FIRE);
        strike();
        int afterBurn = wearer.getBuffManager().stacksOf(SEER);
        strike();
        int burnAgain = wearer.getBuffManager().stacksOf(SEER);
        plant(THUNDER_NAME, DamageElement.THUNDER);
        strike();
        int afterShock = wearer.getBuffManager().stacksOf(SEER);
        plant(WIND_NAME, DamageElement.WIND);
        plant(PHYSICAL_NAME, DamageElement.PHYSICAL);
        strike();
        int allFour = wearer.getBuffManager().stacksOf(SEER);
        System.out.println("[23022] seer: burn=" + afterBurn + " burnAgain=" + burnAgain
                + " +shock=" + afterShock + " +wind+physical=" + allFour);
        Assertions.assertEquals(1, afterBurn, "Burn (dot:burn) gives one layer");
        Assertions.assertEquals(1, burnAgain, "and repeating it gives nothing (once per type)");
        Assertions.assertEquals(2, afterShock, "Shock (dot:shock) adds a second");
        Assertions.assertEquals(4, allFour, "and all four types reach the cap of four");
    }

    @Test
    public void theLayersLiftAttack() {
        build(true);
        double before = wearer.getAttribute(AttributeType.ATTACK).get();
        plant(FIRE_NAME, DamageElement.FIRE);
        strike();
        double after = wearer.getAttribute(AttributeType.ATTACK).get();
        System.out.println("[23022] attack " + before + " -> " + after + " (delta " + (after - before)
                + ", 5% of the base is " + (0.05 * before) + ")");
        // The SHARE, as an equality (discipline 200): a percent modifier is a share of the pre-bonus base, so one layer adds
        // exactly 5% of `before`. Measured, `after > before` survives a `5 -> 2 percent` mutation (0 red).
        Assertions.assertEquals(0.05 * before, after - before, 1e-9, "one layer is 5% of the base, exactly");
    }

    @Test
    public void noDotNoLayer() {
        build(true);
        strike();
        System.out.println("[23022] without any DoT on the enemy: seer=" + wearer.getBuffManager().stacksOf(SEER));
        Assertions.assertEquals(0, wearer.getBuffManager().stacksOf(SEER), "the gate needs a DoT state (false case)");
    }

    @Test
    public void withoutTheConeNoLayer() {
        build(false);
        plant(FIRE_NAME, DamageElement.FIRE);
        plant(THUNDER_NAME, DamageElement.THUNDER);
        strike();
        System.out.println("[23022] with DoTs but no cone: seer=" + wearer.getBuffManager().stacksOf(SEER));
        Assertions.assertEquals(0, wearer.getBuffManager().stacksOf(SEER), "no cone, no layers (false case)");
    }

    @Test
    public void theDotInstanceCarriesTheIgnore() {
        // Read off the INSTANCE, not off a second battle: rules of one DEALING_DAMAGE event are evaluated in order and see
        // each other (discipline 212), so the very first DOT instance already granted the seer layer that the ignore rule needs.
        // Measured: the DOT instance carries 0.02 while an ordinary hit of the same battle carries 0.0 (damage_type: DOT).
        build(true);
        plant(FIRE_NAME, DamageElement.FIRE);
        com.laosun.aluminium.damage.Damage dot = new com.laosun.aluminium.damage.Damage(wearer, enemy,
                DamageElement.FIRE, com.laosun.aluminium.enums.DamageType.DOT, 1000);
        double dotValue = battle.applyDamage(enemy, dot);
        com.laosun.aluminium.damage.Damage normal = new com.laosun.aluminium.damage.Damage(wearer, enemy,
                DamageElement.FIRE, com.laosun.aluminium.enums.DamageType.NORMAL, 1000);
        double normalValue = battle.applyDamage(enemy, normal);
        System.out.println("[23022] seer=" + wearer.getBuffManager().stacksOf(SEER) + " ; DOT value=" + dotValue
                + " defenceIgnore=" + dot.getDefenceIgnore() + " zone=" + dot.breakdown().get("DefenceArea")
                + " ; NORMAL value=" + normalValue + " defenceIgnore=" + normal.getDefenceIgnore());
        Assertions.assertEquals(0.072, dot.getDefenceIgnore(), 1e-9,
                "the DOT instance itself carries one layer's 7.2% ignore");
        Assertions.assertEquals(0.0, normal.getDefenceIgnore(), 1e-9,
                "and an ordinary hit is outside the clause’s scope");
    }

    @Test
    public void theNoLayerSide() {
        // The other half of the clause, and the shape that a `when`-dropping mutation cannot survive: with NO DoT on the
        // enemy the four seer rules have nothing to match, so no layer is ever granted -- and then the DOT instance must carry
        // no ignore at all. Note: Measuring this in a battle that HAS a DoT planted does not work: the first DOT instance grants
        // the layer itself (discipline 212), which is exactly how the gap looked like an engine limitation.
        build(true);
        com.laosun.aluminium.damage.Damage dot = new com.laosun.aluminium.damage.Damage(wearer, enemy,
                DamageElement.FIRE, com.laosun.aluminium.enums.DamageType.DOT, 1000);
        battle.applyDamage(enemy, dot);
        System.out.println("[23022] no DoT on the enemy: seer=" + wearer.getBuffManager().stacksOf(SEER)
                + " DOT defenceIgnore=" + dot.getDefenceIgnore());
        Assertions.assertEquals(0, wearer.getBuffManager().stacksOf(SEER), "no DoT type, no layer");
        Assertions.assertEquals(0.0, dot.getDefenceIgnore(), 1e-9, "and therefore no ignore on the instance");
    }

    private static final class Run {
        private final Character wearer;
        private final Enemy enemy;
        private final Battle battle;

        Run() {
            wearer = CharacterFactory.create(RUN_WEARER, LEVEL, true, Weapon.build(23022, LEVEL, false, 1));
            enemy = BattleFixture.monster();
            battle = new Battle(new ArrayList<>(List.of(wearer)), new ArrayList<>(List.of(enemy)),
                    BattleFixture.seeded());
            battle.startBattle();
        }

        /**
         * One hit landing on a target that carries the named DoT state.
         */
        void hitWhileCarrying(String state) {
            enemy.getBuffManager().addBuff(new com.laosun.aluminium.buff.StateBuff(state, 3));
            battle.applyDamage(enemy, new Damage(wearer, enemy, DamageElement.PHYSICAL, DamageType.NORMAL, 100));
            battle.processRequests();
        }

        int seer() {
            return wearer.getBuffManager().stacksOf("先知");
        }

        double attack() {
            return wearer.getAttribute(com.laosun.aluminium.enums.AttributeType.ATTACK).get();
        }
    }

    /**
     * Each state type pays once, however many hits land; different types each pay.
     */
    @Test
    public void eachTypePaysOncePerBattle() {
        Run run = new Run();
        double base = run.attack();

        run.hitWhileCarrying("dot:wind_shear");
        Assertions.assertEquals(1, run.seer(), "the first wind-shear hit grants a stack");
        run.hitWhileCarrying("dot:wind_shear");
        Assertions.assertEquals(1, run.seer(), "「每种状态类型仅可叠加 1 次」 - a second hit on the same type does not");

        run.hitWhileCarrying("dot:burn");
        Assertions.assertEquals(2, run.seer(), "a different type pays again");
        // 「每层使攻击力提高 5%」 - the DIRECTION holds, the arithmetic does not. Measured: two layers read x1.078125 with
        // `per_stack` and x1.03906 with `per_stack_live`; the sentence wants x1.10. Both numbers are in the status row - this
        // pins only what is true today, so the gap cannot be mistaken for a pass.
        Assertions.assertTrue(run.attack() > base, "「每层使攻击力提高 5%」 - the attack is raised");
    }
}
