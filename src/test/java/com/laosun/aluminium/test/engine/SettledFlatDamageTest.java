package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.SkillCategory;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.trigger.TriggerTable;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * The two insertion points of an absolute addend: the base layer and the settled value.
 *
 * <p><b>Why the distinction is the whole subject.</b> The documents state both, and they are different numbers unless every
 * zone happens to multiply to 1:
 * <ul>
 *   <li>「伤害<b>值</b>提高，提高数值等同于…」 - a <i>value</i> that is part of the base, so it crits and is boosted
 *       ({@code Damage.addFlat});</li>
 *   <li>「终结技对指定敌方单体造成的<b>伤害额外提高</b>，提高数值等同于…」 (1205 Blade (刃)'s Eidolon 1) - a raise of the
 *       <b>settled</b> number, which must not crit and must not be multiplied ({@code Damage.addSettledFlat}).</li>
 * </ul>
 * The last case pins the other half: the flag is only legal on {@code ADD_DAMAGE}, so it cannot look like a placement on an
 * op that would ignore it.
 */
public class SettledFlatDamageTest {
    private static final int OWNER = 1001;
    private static final int LEVEL = 80;
    /** Deliberately not 100: with a base of 100 an addend of +X and a boost of +X% coincide. */
    private static final double BASE = 400;
    private static final double ADDEND = 100;

    /**
     * The primitive: the same number, two insertion points, two different results.
     */
    @Test
    public void theAddendEntersAfterTheZonesOnlyWhenItSaysSo() {
        Character owner = CharacterFactory.create(OWNER, LEVEL);
        Enemy enemy = BattleFixture.dummy();

        Damage inBase = new Damage(owner, enemy, DamageElement.FIRE, DamageType.NORMAL, BASE);
        inBase.addBoost(0.5);
        inBase.addFlat(ADDEND);

        Damage atEnd = new Damage(owner, enemy, DamageElement.FIRE, DamageType.NORMAL, BASE);
        atEnd.addBoost(0.5);
        atEnd.addSettledFlat(ADDEND);

        // The base-layer reading: (400 + 100) x 1.5.
        Assertions.assertEquals(1.5 * (BASE + ADDEND), inBase.toValue(), 1e-9,
                "an addend in the BASE is multiplied by the boost zone");
        // The settled reading: 400 x 1.5 + 100.
        Assertions.assertEquals(1.5 * BASE + ADDEND, atEnd.toValue(), 1e-9,
                "an addend AFTER the zones is not multiplied by them");
        Assertions.assertNotEquals(inBase.toValue(), atEnd.toValue(),
                "if the two readings coincided, this criterion could not tell them apart");
    }

    /**
     * Through the DSL: {@code ADD_DAMAGE { after_zones: true }} raises the settled instance by exactly the derived amount,
     * while the same rule without the flag raises it by the amount times the instance's own zone factor.
     */
    @Test
    public void theOpLandsWhereItStates() {
        Fixture plain = new Fixture();
        double untouched = plain.damage(null);
        double inBase = plain.damage(armed(false));
        double atEnd = plain.damage(armed(true));

        double addend = 0.30 * plain.owner.getAttribute(com.laosun.aluminium.enums.AttributeType.DEFENCE).get();
        System.out.println("[settled_flat] plain=" + untouched + " baseLayer=" + inBase + " afterZones=" + atEnd
                + " addend=" + addend);

        Assertions.assertEquals(addend, atEnd - untouched, 1e-6,
                "after_zones adds the amount itself, not a multiple of it");
        Assertions.assertNotEquals(addend, inBase - untouched, Math.max(1e-6, addend * 0.01),
                "without the flag the amount is multiplied by the instance's zones, which is the other number");
    }

    /**
     * The flag belongs to one op, and the main-target predicate to the events that carry an instance. A misspelling that
     * would silently do nothing is refused at load time.
     */
    @Test
    public void theShapeIsRefusedAtLoad() {
        EffectSpec wrongOp = TriggerSpecs.modifyAttr("ATTACK", 0.1, 1, null, null, null, "self");
        TriggerSpecs.set(wrongOp, "afterZones", true);
        IllegalArgumentException refused = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(OWNER, List.of(TriggerSpecs.rule("TURN_START", null, wrongOp))));
        Assertions.assertTrue(refused.getMessage().contains("after_zones"), refused.getMessage());

        EffectSpec add = addDamage();
        TriggerSpecs.set(add, "percent", 0.30);
        TriggerSpecs.set(add, "afterZones", true);
        IllegalArgumentException wrongEvent = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(OWNER, List.of(
                        TriggerSpecs.rule("TURN_START", List.of("damage_is_main_target"), add))));
        Assertions.assertTrue(wrongEvent.getMessage().contains("carries no damage instance"),
                wrongEvent.getMessage());
    }

    // ==================================================================

    private static final class Fixture {
        private final Character owner = CharacterFactory.create(OWNER, LEVEL);
        private final Enemy enemy = BattleFixture.dummy();
        private final Battle battle = new Battle(List.of(owner), List.of(enemy), fixed());

        Fixture() {
            // An empty table: the criterion measures the op, not whatever 1001's own kit does on the way past.
            owner.setTriggerTable(new TriggerTable(OWNER, List.of()));
            battle.startBattle();
        }

        double damage(TriggerSpec rule) {
            if (rule != null) {
                owner.setTriggerTable(new TriggerTable(OWNER, List.of(rule)));
            }
            enemy.heal(enemy.getMaxHp());
            double before = enemy.getCurrentHp();
            battle.applyDamage(enemy, new Damage(owner, enemy, DamageElement.FIRE, DamageType.NORMAL, BASE,
                    SkillCategory.ULTRA));
            return before - enemy.getCurrentHp();
        }
    }

    private static TriggerSpec armed(boolean afterZones) {
        EffectSpec effect = addDamage();
        TriggerSpecs.set(effect, "percent", 0.30);
        if (afterZones) {
            TriggerSpecs.set(effect, "afterZones", true);
        }
        return TriggerSpecs.rule("DEALING_DAMAGE", null, effect);
    }

    private static EffectSpec addDamage() {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "ADD_DAMAGE");
        TriggerSpecs.set(effect, "scale", "self_attr:DEFENCE");
        return effect;
    }

    private static Random fixed() {
        return BattleFixture.alwaysRoll(0.0);
    }
}
