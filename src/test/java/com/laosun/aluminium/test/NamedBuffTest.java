package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * A modifier (or a resistance) that carries <b>the name the data gave it</b>, so a state's own effect can be removed
 * with the state (2026-09-28).
 *
 * <p><b>The sentence that needs it.</b> 知更鸟's [协奏] grants the party an ATK boost and a crowd-control immunity
 * that last "处于[协奏]状态时" - <b>as long as the state does</b>, and that state ends when a countdown's turn
 * arrives, which is not a turn count at all. Removal by name existed only for <b>states</b>, so those two effects could
 * only be written as {@code permanent}: a buff that never comes off, i.e. a wrong number with no symptom.
 *
 * <p><b>What is pinned here.</b> That a named modifier goes away when its name is removed, that an <b>unnamed</b> one is
 * untouched by that same statement (the field must not change the lifetime of anything that predates it), and that the
 * state and its named effects can share one name and come off together.
 */
public class NamedBuffTest {
    private static final int MARCH = 1001;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /** "协奏" as a state, plus an ATTACK boost and an immunity that carry the same name. */
    @Test
    public void removingTheStateTakesItsNamedEffectsWithIt() {
        Fixture f = new Fixture();
        f.fire(stateAndEffects("协奏"));

        double boosted = f.attack();
        Assertions.assertTrue(boosted > f.baseAttack, "precondition: the named modifier is on (" + boosted + ")");
        Assertions.assertTrue(f.hero.getBuffManager().hasState("协奏"), "…and so is the state");

        f.fire(List.of(removeState("协奏")));

        Assertions.assertFalse(f.hero.getBuffManager().hasState("协奏"), "the state is off");
        Assertions.assertEquals(f.baseAttack, f.attack(), 1e-6,
                "…and the ATK boost came off with it, because it carried the same name (before this field it could "
                        + "only be `permanent` and would have stayed forever)");
    }

    /** Note: An UNNAMED modifier is not touched by any name: the field must not change existing content's lifetime. */
    @Test
    public void anUnnamedModifierIsNotTouched() {
        Fixture f = new Fixture();
        f.fire(stateAndEffects(null));
        double before = f.attack();

        f.fire(List.of(removeState("协奏")));

        Assertions.assertEquals(before, f.attack(), 1e-6,
                "an unnamed modifier survives 「移除协奏」 -- only what states a name can be removed by one");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private static final class Fixture {
        private final Character hero;
        private final Battle battle;
        private final double baseAttack;

        private Fixture() {
            hero = CharacterFactory.create(MARCH, LEVEL);
            hero.setTriggerTable(new TriggerTable(MARCH, List.of()));
            battle = new Battle(List.of(hero), List.of(EnemyFactory.create(MONSTER, 90, 1)), fixed());
            battle.startBattle();
            baseAttack = attack();
        }

        private double attack() {
            return hero.getAttribute(AttributeType.ATTACK).get();
        }

        private void fire(List<TriggerSpec> rules) {
            hero.setTriggerTable(new TriggerTable(MARCH, rules));
            battle.fireTriggers(TriggerEvent.BATTLE_START, hero, hero, 0, 0);
        }
    }

    /** A state "协奏" plus an ATTACK boost and a control immunity, all naming {@code name} (null = unnamed). */
    private static List<TriggerSpec> stateAndEffects(String name) {
        EffectSpec state = new EffectSpec();
        TriggerSpecs.set(state, "op", "APPLY_BUFF");
        TriggerSpecs.set(state, "buff", "协奏");
        TriggerSpecs.set(state, "permanent", true);
        TriggerSpecs.set(state, "target", "self");

        EffectSpec boost = new EffectSpec();
        TriggerSpecs.set(boost, "op", "MODIFY_ATTR");
        TriggerSpecs.set(boost, "attribute", "ATTACK");
        TriggerSpecs.set(boost, "percent", 0.5);
        TriggerSpecs.set(boost, "permanent", true);
        TriggerSpecs.set(boost, "target", "self");
        if (name != null) {
            TriggerSpecs.set(boost, "buff", name);
        }

        EffectSpec immunity = new EffectSpec();
        TriggerSpecs.set(immunity, "op", "RESIST_DEBUFF");
        TriggerSpecs.set(immunity, "kind", "control");
        TriggerSpecs.set(immunity, "percent", 1.0);
        TriggerSpecs.set(immunity, "permanent", true);
        TriggerSpecs.set(immunity, "target", "self");
        if (name != null) {
            TriggerSpecs.set(immunity, "buff", name);
        }

        return List.of(TriggerSpecs.rule("BATTLE_START", null, state),
                TriggerSpecs.rule("BATTLE_START", null, boost),
                TriggerSpecs.rule("BATTLE_START", null, immunity));
    }

    private static TriggerSpec removeState(String name) {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "REMOVE_STATE");
        TriggerSpecs.set(effect, "buff", name);
        TriggerSpecs.set(effect, "target", "self");
        return TriggerSpecs.rule("BATTLE_START", null, effect);
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
