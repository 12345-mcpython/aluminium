package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.buff.DotBuff;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * What a repeating DOT <b>already</b> does (2026-09-28), measured because the gap list needed the truth.
 *
 * <p>GAPS #1 says "DOT stacking is blocked". That is only half true, and the half that is false matters: `DotBuff`'s
 * {@code isSameKind} answers {@code false}, so a second application of the same state is <b>not</b> evicted — the DOTs
 * coexist and (since every DOT is ticked) their damage adds up. What is really missing is the <b>cap</b>: a sentence like
 * 1108's 「风化状态最多叠加 5 层」 states a ceiling, and today nothing enforces one — the loader even forbids the field
 * (`APPLY_DOT` calls `requireNoStackArguments`), because a *capped* stack needs a policy (refresh? extend? ignore past N?)
 * that no document fixes.
 *
 * <p>So this case pins the measured half (coexistence) and leaves the missing half (the cap) registered.
 */
public class DotCoexistenceTest {
    private static final int HERO = 1103;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /** Two applications of the same state leave two DOTs standing. */
    @Test
    public void aSecondDotIsNotEvicted() {
        Fixture f = fixture();
        f.hero.setTriggerTable(table(f.hero));

        f.battle.fireTriggers(TriggerEvent.KILL, f.hero, f.enemy, 0, 0);
        int afterOne = f.enemy.getBuffManager().allBuffsOf(DotBuff.class).size();
        f.battle.fireTriggers(TriggerEvent.KILL, f.hero, f.enemy, 0, 0);
        int afterTwo = f.enemy.getBuffManager().allBuffsOf(DotBuff.class).size();

        Assertions.assertEquals(1, afterOne, "precondition: one application, one DOT");
        Assertions.assertEquals(2, afterTwo,
                "⚠ `DotBuff.isSameKind` answers false, so a second application is not evicted: DOTs already coexist "
                        + "(what is missing is the CAP the sentences state, not the stacking)");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private static TriggerTable table(Character hero) {
        EffectSpec dot = new EffectSpec();
        TriggerSpecs.set(dot, "op", "APPLY_DOT");
        TriggerSpecs.set(dot, "element", "Thunder");
        TriggerSpecs.set(dot, "percent", 0.5);
        TriggerSpecs.set(dot, "scale", "self_attr:ATTACK");
        TriggerSpecs.set(dot, "turns", 3);
        TriggerSpecs.set(dot, "target", "target");
        return new TriggerTable(HERO, List.of(TriggerSpecs.rule("KILL", List.of("actor == self"), dot)));
    }

    private record Fixture(Character hero, Enemy enemy, Battle battle) {
    }

    private static Fixture fixture() {
        Character hero = CharacterFactory.create(HERO, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(hero), List.of(enemy), fixed());
        battle.startBattle();
        return new Fixture(hero, enemy, battle);
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
