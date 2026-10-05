package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * Op {@code ADD_DAMAGE}: "raise the damage value the counter deals, the raise being equal to 30% of 三月七's DEF" (ROADMAP M-55).
 *
 * <p>It is the absolute sibling of {@code BOOST_DAMAGE}: that one adds a percentage of the instance, this one adds a
 * <b>value</b> derived from the rule owner's own attribute, into the instance's <b>base layer</b>
 * ({@code Damage.addFlat}) - so it crits and is boosted like the skill multiplier. The difference is the whole point of
 * the op, and the last case here is the one that would go red if it were spelled as a percentage instead.
 */
public class AddDamageOpTest {
    private static final int OWNER = 1001;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    /**
     * The instance base every case uses.
     *
     * <p>Note: Deliberately NOT 100: with a base of 100, "+X" and "+X%" are the same number, so the trap this op
     * exists to avoid would be invisible to the tests (measured: a mutation that spelled the addend as a boost of
     * {@code value / 100} kept every case green). Any base other than 100 separates them.
     */
    private static final double BASE = 400;

    /** The addend is 30% of the owner's DEFENCE, added to the settled instance. */
    @Test
    public void theAddendIsDerivedFromTheOwnersAttribute() {
        Fixture f = new Fixture(0.30);

        double plain = f.damageTaken(null);
        f.battle.startBattle();
        double withOp = f.damageTaken(armed(0.30));

        double addend = 0.30 * f.owner.getAttribute(AttributeType.DEFENCE).get();
        // Note: Compared as a RATIO: the addend goes through the same zones the base does, so the settled ratio is what
        // proves where it entered (an absolute growth would depend on the zone factors of this particular fight).
        Assertions.assertEquals((BASE + addend) / BASE, withOp / plain, 0.02,
                "the settled damage equals a base of \"100 + 30% DEF\" run through the same zones");
    }

    /** Note: The trap: a percentage of the instance and a value equal to 30% DEF are different numbers unless they coincide. */
    @Test
    public void itIsNotAPercentageOfTheInstance() {
        Fixture f = new Fixture(0.30);
        double plain = f.damageTaken(null);
        f.battle.startBattle();

        double asValue = f.damageTaken(armed(0.30));
        double addend = 0.30 * f.owner.getAttribute(AttributeType.DEFENCE).get();
        Assertions.assertEquals(plain * (BASE + addend) / BASE, asValue, Math.max(0.5, plain * 0.02));
        Assertions.assertNotEquals(plain * 1.30, asValue,
                "\"the raise is equal to 30% of DEF\" is not \"the damage dealt is raised by 30%\" -- a base of " + BASE + " against a settled " + plain
                        + " against an addend of " + addend);
    }

    /** A rule may not state it anywhere but on the instance's own event, and it reads no other field. */
    @Test
    public void theShapeIsRefusedAtLoad() {
        IllegalArgumentException wrongEvent = Assertions.assertThrows(IllegalArgumentException.class,
                () -> table(TriggerSpecs.rule("TURN_START", null, addDamage(0.30))));
        Assertions.assertTrue(wrongEvent.getMessage().contains("TURN_START"), wrongEvent.getMessage());

        EffectSpec noScale = addDamage(0.30);
        TriggerSpecs.set(noScale, "scale", null);
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> table(TriggerSpecs.rule("DEALING_DAMAGE", null, noScale)), "a derived value needs its scale");
    }

    /** The shipped sentence: her Eidolon 4 states the addend on the counter's own damage instance. */
    @Test
    public void theShippedEidolonStatesTheAddend() {
        Character owner = CharacterFactory.create(OWNER, LEVEL, true, null, null, 4);
        var rules = com.laosun.aluminium.data.TriggerTables.of(OWNER)
                .matching(com.laosun.aluminium.enums.TriggerEvent.DEALING_DAMAGE,
                        new TriggerTable.TriggerContext(owner, owner, null, 0, 0, null, null,
                                com.laosun.aluminium.enums.SkillCategory.UNSPECIFIED))
                .stream()
                .filter(rule -> rule.minEidolon() == 4)
                .toList();
        Assertions.assertEquals(1, rules.size(), "Eidolon 4's damage sentence is gone from characters/1001.json");
        Assertions.assertEquals(List.of("actor == self", "from_skill TALENT"),
                rules.getFirst().conditions().stream().map(TriggerTable.Condition::source).toList());
        var effect = rules.getFirst().effects().getFirst();
        Assertions.assertEquals("ADD_DAMAGE", effect.getOp());
        Assertions.assertEquals("self_attr:DEFENCE", effect.getScale());
        Assertions.assertEquals(0.3, effect.getPercent(), 1e-9);
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private static final class Fixture {
        private final Character owner;
        private final Enemy enemy;
        private final Battle battle;
        private final TriggerSpec rule;

        private Fixture(double ignored) {
            this.owner = CharacterFactory.create(OWNER, LEVEL);
            this.enemy = EnemyFactory.create(MONSTER, 90, 1);
            this.battle = new Battle(List.of(owner), List.of(enemy), fixed());
            this.rule = null;
        }

        /** Runs one damage instance of a fixed base, optionally with the op armed on DEALING_DAMAGE. */
        private double damageTaken(TriggerSpec armed) {
            if (armed != null) {
                owner.setTriggerTable(new TriggerTable(OWNER, List.of(armed)));
            }
            enemy.heal(enemy.getMaxHp());
            double before = enemy.getCurrentHp();
            battle.applyDamage(enemy, new Damage(owner, enemy, com.laosun.aluminium.enums.DamageElement.FIRE,
                    com.laosun.aluminium.enums.DamageType.NORMAL, BASE,
                    com.laosun.aluminium.enums.SkillCategory.ULTRA));
            return before - enemy.getCurrentHp();
        }
    }

    /** The op as a rule on the instance's own event. */
    private static TriggerSpec armed(double percent) {
        return TriggerSpecs.rule("DEALING_DAMAGE", null, addDamage(percent));
    }

    private static EffectSpec addDamage(double percent) {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "ADD_DAMAGE");
        TriggerSpecs.set(effect, "scale", "self_attr:DEFENCE");
        TriggerSpecs.set(effect, "percent", percent);
        return effect;
    }

    private static TriggerTable table(TriggerSpec rule) {
        return new TriggerTable(OWNER, List.of(rule));
    }

    private static Random fixed() {
        return new Random() {
            @Override
            public double nextDouble() {
                return 0.0;
            }
        };
    }
}
