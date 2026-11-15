package com.laosun.aluminium.test.content.lightcones;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.DoubleValue;
import com.laosun.aluminium.buff.StateBuff;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.equipment.Weapon;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Light cone 23059: three of five clauses -- and the judge that caught two real errors the suite could not.
 *
 * <p>A rule that is never SELECTED is never validated, so a green suite said nothing: the HP rule named an attribute
 * the engine does not have (it is HEALTH), and the state rule hung a category test on SKILL_CAST, which carries none,
 * so it could never fire. Both are fixed here, and this test selects them.
 *
 * <p>灼尽炼狱的新骸 (23059): 「【炼狱】状态下目标受到的暴击伤害提高 #4%，受到来自装备者的暴击伤害额外提高 #5%」.
 *
 * <p><b>What it needed.</b> Nothing new: an instance {@code MODIFY_ATTR CRIT_ATTACK} adds to that hit's
 * {@code extraCritDamage}, and {@code CritArea.rate()} only reads it when the hit <b>crits</b> - so "crit damage taken"
 * needs no new event, keyword or zone. The first rule has no attacker gate (anyone's crit on a 【炼狱】 target), the second
 * adds the wearer's own share on top ({@code actor == self}); instance shares accumulate, so the two add.
 */
public class ReforgedInHellfireTest {
    private static final int CONE = 23059;
    private static final int WEARER = 1210;
    private static final int LEVEL = 80;
    private static final double[] HP = {0.30, 0.375, 0.45, 0.525, 0.60};
    private static final String MARKER = "internal:23059/wave_energy";
    private static final String STATE = "char:23059/purgatory";
    // From the merged ReforgedInHellfireConeTest: its own wearer id plus the ally that stands in for "an attacker who is not
    // the wearer". CONE (23059) and LEVEL (80) are shared with the clauses above, so they are not redeclared.
    private static final int CONE_WEARER = 1002;
    private static final int ALLY = 1001;

    private Character wearer(int rank) {
        return CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, rank));
    }

    @Test
    public void everyTierStatesWhatTheTextStates() {
        for (int rank = 1; rank <= 5; rank++) {
            var table = wearer(rank).getTriggerTable();

            // 2026-10-19: the HP share is the cone's own health_percent property now (the rule was a duplicate).
            double plainHp = CharacterFactory.create(WEARER, LEVEL, true).getAttribute(AttributeType.HEALTH).get();
            Assertions.assertTrue(wearer(rank).getAttribute(AttributeType.HEALTH).get() > plainHp,
                    "rank " + rank + ": the HP share reaches the panel");
            var energy = table.rulesFor(TriggerEvent.TURN_START).stream()
                    .filter(r -> r.id().equals("cone23059_wave_energy")).toList();
            Assertions.assertEquals(1, energy.size(), "rank " + rank + ": the energy rule");
            Assertions.assertEquals(List.of("!self has_state " + MARKER),
                    energy.getFirst().conditions().stream().map(c -> c.source()).toList(),
                    "rank " + rank + ": only while the wave marker is absent");
            var e = energy.getFirst().effects();
            Assertions.assertEquals(20.0, e.get(0).getAmount(), 1e-9, "rank " + rank + ": a flat 20");
            Assertions.assertEquals("APPLY_BUFF", e.get(1).getOp(), "rank " + rank + ": it then marks");
            Assertions.assertEquals(MARKER, e.get(1).getBuff(),
                    "rank " + rank + ": the marker it applies -- the assertion the 7-way sweep demanded");

            var reset = table.rulesFor(TriggerEvent.WAVE_START).stream()
                    .filter(r -> r.id().equals("cone23059_wave_reset")).toList();
            Assertions.assertEquals(1, reset.size(), "rank " + rank + ": the per-wave reset");
            Assertions.assertEquals(MARKER, reset.getFirst().effects().getFirst().getBuff(),
                    "rank " + rank + ": clears exactly that marker");

            var state = table.rulesFor(TriggerEvent.ATTACK_FINISHED).stream()
                    .filter(r -> r.id().equals("cone23059_purgatory")).toList();
            Assertions.assertEquals(1, state.size(), "rank " + rank + ": the state rule");
            Assertions.assertEquals(List.of("from_category BPSKILL"),
                    state.getFirst().conditions().stream().map(c -> c.source()).toList(),
                    "rank " + rank + ": only a skill, on an event that CARRIES a category");
            var s = state.getFirst().effects().getFirst();
            Assertions.assertEquals(STATE, s.getBuff(), "rank " + rank + ": the state's name");
            Assertions.assertEquals(2, s.getTurns(), "rank " + rank + ": two turns");
            System.out.println("[23059] rank=" + rank + " ok");
        }
    }

    // ==================================================================
    // Merged from ReforgedInHellfireConeTest.

    /**
     * The wearer gets #4 + #5 on a 【炼狱】 target, another attacker gets #4 only, and nobody gets anything without the state.
     */
    @Test
    public void thePurgatorySharesApply() {
        Fixture f = new Fixture(1, true);
        Assertions.assertEquals(0.6, f.extraCritDamage(f.wearer), 1e-9,
                "the wearer: 30% from the state + 30% from herself");
        Assertions.assertEquals(0.3, f.extraCritDamage(f.ally), 1e-9,
                "an ally: only the state's 30%");

        Fixture bare = new Fixture(1, false);
        Assertions.assertEquals(0.0, bare.extraCritDamage(bare.wearer), 1e-9,
                "no 【char:23059/purgatory】: nothing");
    }

    /**
     * The share only matters when the hit crits: a non-crit instance keeps a 1.0 multiplier, a forced crit reads it.
     */
    @Test
    public void theShareOnlyCountsOnACrit() {
        Fixture nonCrit = new Fixture(1, true);
        nonCrit.hit(nonCrit.wearer, true);
        Assertions.assertEquals(2.1, nonCrit.critRate, 1e-9,
                "a crit multiplies by 1 + (0.5 base crit damage + 0.6) - the【char:23059/purgatory】shares are inside the crit");

        Fixture flat = new Fixture(1, true);
        flat.hit(flat.wearer, false);
        Assertions.assertEquals(1.0, flat.critRate, 1e-9,
                "a hit that did not crit is untouched: the same shares wait inside the crit multiplier");
    }

    /**
     * Both shares are per rank (30 / 37.5 / 45 / 52.5 / 60%).
     */
    @Test
    public void theSharesFollowTheRank() {
        double[] expected = {0.3, 0.375, 0.45, 0.525, 0.6};
        for (int rank = 1; rank <= 5; rank++) {
            Fixture f = new Fixture(rank, true);
            Assertions.assertEquals(2 * expected[rank - 1], f.extraCritDamage(f.wearer), 1e-9,
                    "rank " + rank + " wearer");
            Assertions.assertEquals(expected[rank - 1], f.extraCritDamage(f.ally), 1e-9, "rank " + rank + " ally");
        }
    }

    // ==================================================================

    private static final class Fixture {
        private final Character wearer;
        private final Character ally;
        private final Enemy enemy = BattleFixture.monster();
        private final Battle battle;
        /** The crit multiplier of the last settled hit (read after settlement). */
        private double critRate;

        private Fixture(int rank, boolean purgatory) {
            wearer = CharacterFactory.create(CONE_WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, rank));
            ally = CharacterFactory.create(ALLY, LEVEL);
            battle = new Battle(new ArrayList<>(List.of(wearer, ally)),
                    new ArrayList<>(List.of(enemy)), BattleFixture.seeded());
            battle.startBattle();
            if (purgatory) {
                enemy.getBuffManager().addBuff(new StateBuff("char:23059/purgatory", 2));
            }
        }

        /** Settles one hit from {@code attacker} and returns the crit-damage term it collected. */
        private double extraCritDamage(Character attacker) {
            boolean crit = attacker.getAttribute(AttributeType.CRIT_CHANCE).get() == 0;
            Damage damage = new Damage(attacker, enemy, DamageElement.FIRE, DamageType.NORMAL, 100);
            battle.applyDamage(enemy, damage);
            critRate = damage.critArea().getRate();
            return damage.getExtraCritDamage();
        }

        /** Settles one forced-crit or forced-non-crit hit, keeping its crit multiplier. */
        private void hit(Character attacker, boolean crit) {
            DoubleValue chance = attacker.getAttribute(AttributeType.CRIT_CHANCE);
            chance.addModifier(DoubleValue.Modifier.pure(crit ? 10 : -10));
            Damage damage = new Damage(attacker, enemy, DamageElement.FIRE, DamageType.NORMAL, 100);
            battle.applyDamage(enemy, damage);
            critRate = damage.critArea().getRate();
        }
    }
}
