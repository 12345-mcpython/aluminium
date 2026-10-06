package com.laosun.aluminium.test.engine;


import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * The pair the biggest remaining family needs: the <b>"弱点击破状态"</b> condition and the
 * <b>toughness to super-break conversion</b> ({@code SUPER_BREAK}).
 *
 * <p>"对处于<b>弱点击破状态</b>的敌方目标造成伤害后，会将本次伤害的<b>削韧值</b>转化为 1 次 X% 的超击破伤害" (1321's clauses, and the
 * half of 8006's ultimate that was registered) - neither half has a reader without the other, which is why they landed
 * together.
 *
 * <p><b>What is pinned here.</b> That breaking really flips the state on and off, that the conversion lands exactly on
 * broken targets and only there, and that the op refuses to attach anywhere but {@code DEALING_DAMAGE}.
 */
public class SuperBreakTest {
    private static final int CID = 1321;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;

    /** The state tracks the toughness bar: false while it stands, true once it is emptied. */
    @Test
    public void theBrokenStateFollowsTheToughnessBar() {
        Char enemy = strongEnemy();
        Assertions.assertFalse(enemy.enemy.getBuffManager().hasState("弱点击破"), "a healthy enemy is not broken");

        enemy.breakIt();

        Assertions.assertTrue(enemy.enemy.getBuffManager().hasState("弱点击破"),
                "「韧性被削减至 0」 is what the state means -- it is a fact about the field, not a buff anyone applied");
        Assertions.assertFalse(CharacterFactory.create(ALLY, LEVEL).getBuffManager().hasState("弱点击破"),
                "…and a character is never in it");
    }

    /** Note: The conversion lands on a broken target and nowhere else. */
    @Test
    public void theConversionLandsOnlyOnBrokenTargets() {
        double plain = damageWith(false, rule(2.0));
        double converted = damageWith(true, rule(2.0));
        double brokenWithoutRule = damageWith(true, null);

        Assertions.assertTrue(brokenWithoutRule > 0, "precondition: the attack lands");
        Assertions.assertEquals(brokenWithoutRule, plain, 1e-6,
                "the same rule against a HEALTHY target adds nothing: the condition is 「处于弱点击破状态」");
        Assertions.assertTrue(converted > brokenWithoutRule,
                "against a broken target the same attack is followed by the converted super-break instance");
    }

    /** Note: The magnitude is the instance's toughness reduction, not its damage: doubling the percentage doubles the extra. */
    @Test
    public void theConversionScalesWithTheStanceValue() {
        double once = damageWith(true, rule(1.0));
        double twice = damageWith(true, rule(2.0));
        double none = damageWith(true, null);

        Assertions.assertEquals(2 * (once - none), twice - none, 1.0,
                "「转化为 1 次 X% 的超击破伤害」: the extra damage is proportional to X (and to the 削韧值, which is the same "
                        + "in both runs)");
    }

    /** Note: The op is refused anywhere but DEALING_DAMAGE: it reads the instance being settled. */
    @Test
    public void theOpIsRefusedOnOtherEvents() {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "SUPER_BREAK");
        TriggerSpecs.set(effect, "percent", 2.0);
        TriggerSpecs.set(effect, "target", "target");
        TriggerSpec wrongEvent = TriggerSpecs.rule("BATTLE_START", List.of("hp_percent >= 0"), effect);

        IllegalArgumentException refused = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(CID, List.of(wrongEvent)));
        Assertions.assertTrue(refused.getMessage().contains("DEALING_DAMAGE"), refused.getMessage());
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private static TriggerSpec rule(double percent) {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "SUPER_BREAK");
        TriggerSpecs.set(effect, "percent", percent);
        TriggerSpecs.set(effect, "target", "target");
        return TriggerSpecs.rule("DEALING_DAMAGE", List.of("target has_state 弱点击破"), effect);
    }

    /** Settles her basic attack against a (optionally broken) weak enemy and reports the damage it did. */
    private static double damageWith(boolean broken, TriggerSpec extra) {
        Char c = strongEnemy();
        if (broken) {
            c.breakIt();
        }
        List<TriggerSpec> specs = extra == null ? List.of() : List.of(extra);
        c.hero.setTriggerTable(new TriggerTable(CID, specs));
        c.battle.currentMove = null;
        double before = c.enemy.getCurrentHp();
        c.battle.castImmediate(c.hero.getSkills().get(SkillType.COMMON), c.hero, List.of(c.enemy));
        return before - c.enemy.getCurrentHp();
    }

    /** A hero, a battle and an enemy that the hero's element can actually reduce. */
    private static Char strongEnemy() {
        Character hero = CharacterFactory.create(CID, LEVEL);
        DamageElement element = hero.getSkills().get(SkillType.COMMON).getData().getElement();
        // Note: The candidate is BROKEN by this fixture, so it has to survive its own break damage: an earlier version
        // took the first element-weak id and happened to survive by a narrow margin (break base 3103against 16498
        // Max HP), which made the test fail the moment anything raised that damage -- e.g. a 20% resistance reduction,
        // measured (the fixture's hero is 1321, whose aura reduces enemies' resistance). Picking the
        // element-weak candidate with the LARGEST Max HP is what makes the fixture say what it means.
        Enemy enemy = null;
        for (int id = 1002010; id < 1002100; id++) {
            try {
                Enemy candidate = EnemyFactory.create(id, 90, 1);
                if (candidate.isWeakTo(element) && (enemy == null || candidate.getMaxHp() > enemy.getMaxHp())) {
                    enemy = candidate;
                }
            } catch (RuntimeException ignored) {
                // not in the data
            }
        }
        Assertions.assertNotNull(enemy, "no enemy weak to " + element + " in the probed range");
        Battle battle = new Battle(List.of(hero, CharacterFactory.create(ALLY, LEVEL)), List.of(enemy), fixed());
        battle.startBattle();
        return new Char(hero, enemy, battle);
    }

    private record Char(Character hero, Enemy enemy, Battle battle) {
        /** Empties the toughness bar through the engine's own entry point. */
        private void breakIt() {
            DamageElement element = hero.getSkills().get(SkillType.COMMON).getData().getElement();
            battle.reduceToughness(hero, enemy, element, 999);
        }
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
