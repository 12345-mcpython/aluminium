package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
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
 * Light cone 23006: a hit inflicts [游丝] -- a NAMED thunder damage-over-time -- and a target in [游丝]
 * "也会被视为陷入了触电状态".
 *
 * <p>The alias is not a second stored fact: a NAMED thunder DOT answers its own name AND the element table's 触电. The
 * readings below make that attributable -- including the mirror case of a plain, unnamed thunder DOT.
 */
public class Cone23006Test {
    private static final int CONE = 23006;
    private static final int WEARER = 1205;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    private Character wearer;
    private Enemy enemy;

    private Battle battle(boolean withCone) {
        wearer = withCone
                ? CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, 1))
                : CharacterFactory.create(WEARER, LEVEL);
        enemy = EnemyFactory.create(MONSTER, 90, 1);
        // Note: A base chance of 1.0 still ROLLS against the victim's effect RES (the project's own rule), so the applier
        // has to hold EFFECT_HIT_RATE or the DOT never lands and the reading is a silent zero (measured this round).
        wearer.getAttribute(com.laosun.aluminium.enums.AttributeType.EFFECT_HIT_RATE)
                .addModifier(com.laosun.aluminium.models.DoubleValue.Modifier.pure(
                        1.0, com.laosun.aluminium.models.DoubleValue.Modifier.ModifierSource.BUFF, 230600));
        Battle battle = new Battle(List.of(wearer), List.of(enemy), new Random(0));
        battle.startBattle();
        return battle;
    }

    private void attack(Battle battle) {
        battle.applyDamage(enemy, new Damage(wearer, enemy, DamageElement.FIRE, DamageType.NORMAL, 100));
    }

    @Test
    public void theConeInflictsThreadAndThreadCountsAsShock() {
        Battle battle = battle(true);
        attack(battle);
        boolean thread = enemy.getBuffManager().hasState("游丝");
        boolean shock = enemy.getBuffManager().hasState("触电");
        System.out.println("[23006] after one hit: has 游丝=" + thread + " has 触电=" + shock);
        Assertions.assertTrue(thread, "the named DOT answers to its own name");
        Assertions.assertTrue(shock, "and to 触电 through the element table -- the alias costs nothing");
    }

    @Test
    public void aPlainThunderDotAnswersShockButNotThread() {
        wearer = CharacterFactory.create(WEARER, LEVEL);
        enemy = EnemyFactory.create(MONSTER, 90, 1);
        // The UNNAMED thunder DOT, built directly: the element alone, which is everything this engine could express
        // before today. (An event-driven fixture needs an event that carries a target, and BATTLE_START does not -- measured.)
        enemy.getBuffManager().addBuff(new com.laosun.aluminium.models.buff.DotBuff(
                wearer, DamageElement.THUNDER, 20, 2));
        boolean thread = enemy.getBuffManager().hasState("游丝");
        boolean shock = enemy.getBuffManager().hasState("触电");
        System.out.println("[23006] unnamed thunder DOT: has 游丝=" + thread + " has 触电=" + shock);
        Assertions.assertTrue(shock, "an unnamed thunder DOT is still 触电 -- the element path is untouched");
        Assertions.assertFalse(thread, "but it is NOT 游丝: the name is what distinguishes them");
    }

    @Test
    public void withoutTheConeTheEnemyIsNotInflicted() {
        Battle battle = battle(false);
        attack(battle);
        System.out.println("[23006] without the cone: has 游丝=" + enemy.getBuffManager().hasState("游丝"));
        Assertions.assertFalse(enemy.getBuffManager().hasState("游丝"), "no cone, no state (false case)");
    }

    @Test
    public void theSpecPinsTheNameTheElementAndTheNumbers() {
        Battle battle = battle(true);
        int pinned = 0;
        Damage instance = new Damage(wearer, enemy, DamageElement.FIRE, DamageType.NORMAL, 100);
        for (var rule : wearer.getTriggerTable().matching(TriggerEvent.DEALING_DAMAGE,
                new TriggerTable.TriggerContext(wearer, wearer, enemy, 0, 0, instance, battle, null))) {
            for (var effect : rule.effects()) {
                if ("APPLY_DOT".equals(effect.getOp())) {
                    pinned++;
                    System.out.println("[23006] spec element=" + effect.getElement() + " name=" + effect.getBuff()
                            + " percent=" + effect.getPercent() + " turns=" + effect.getTurns());
                    Assertions.assertEquals("Thunder", effect.getElement(), "thunder is what makes 触电 true");
                    Assertions.assertEquals("游丝", effect.getBuff(), "the document's name for the state");
                    Assertions.assertEquals(1, effect.getTurns(), "one turn at rank 1");
                }
            }
        }
        Assertions.assertEquals(1, pinned, "exactly one named-DOT rule from this cone");
    }
}
