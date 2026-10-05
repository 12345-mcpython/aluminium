package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.data.TriggerTables;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
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
 * The first <b>content</b> readers of the super-break pair, from the characters' own files.
 *
 * <p>"持有[伴舞]的我方目标...攻击处于弱点击破状态下的敌方目标后,会将本次攻击的削韧值转化为 1 次超击破伤害" (8006) and "结界持续期间,我方全体的
 * 弱点击破效率提高 50%" (1321). The engine's own behaviour is pinned in {@link SuperBreakTest} and
 * {@link ToughnessBoostTest}; what is pinned here is that the <b>files</b> say it.
 *
 * <p>Note: <b>The discipline this file exists to demonstrate</b>: never hard-code a monster id to mean "it is weak to X" -
 * a hard-coded enemy is simply not weak to the element a sentence needs,
 * and the toughness reduction is then zero with no error). Every enemy here is chosen by <b>asking it</b>
 * ({@code hasToughnessBar()} / {@code getStanceWeak()}), and a break uses the element the enemy itself declares.
 */
public class SuperBreakContentTest {
    private static final int HARMONY = 8006;
    private static final int BLOOM = 1321;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;

    /** Note: Both gates are on the rule: the attacker carries [伴舞] and the target is "处于弱点击破状态". */
    @Test
    public void bothGatesAreOnTheRule() {
        Character harmony = CharacterFactory.create(HARMONY, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = enemyWithAToughnessBar();
        Battle battle = new Battle(List.of(harmony, ally), List.of(enemy), fixed());
        battle.startBattle();
        battle.castImmediate(harmony.getSkills().get(SkillType.ULTRA), harmony, List.of(ally, harmony));
        breakIt(battle, harmony, enemy);
        Assertions.assertTrue(ally.getBuffManager().hasState("伴舞"), "precondition: the ally carries 【伴舞】");
        Assertions.assertTrue(enemy.getBuffManager().hasState("弱点击破"), "precondition: the target is broken");

        TriggerTable table = TriggerTables.of(HARMONY);
        Assertions.assertFalse(table.matching(TriggerEvent.DEALING_DAMAGE, ctx(harmony, ally, enemy, battle)).isEmpty(),
                "「持有【伴舞】的我方目标…攻击处于弱点击破状态下的敌方目标后」 -- a carrier attacking a broken target matches");

        Character plain = CharacterFactory.create(ALLY, LEVEL);
        Assertions.assertTrue(table.matching(TriggerEvent.DEALING_DAMAGE, ctx(harmony, plain, enemy, battle)).isEmpty(),
                "⚠ 「**持有【伴舞】的**我方目标」: an attacker without the state does not match");
    }

    /** Her file carries the clause now (and the tally says so). */
    @Test
    public void herFileCarriesTheConversion() {
        Assertions.assertEquals(2, TriggerTables.of(HARMONY).ruleCount(TriggerEvent.DEALING_DAMAGE),
                "the conversion rule is one of the file's DEALING_DAMAGE rules (2 since the skill extra-hits clause landed)");
    }

    /** 1321's Skill hands the whole side the toughness boost - "弱点击破效率提高 50%". */
    @Test
    public void herSkillRaisesThePartysToughnessEfficiency() {
        Character bloom = CharacterFactory.create(BLOOM, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = enemyWithAToughnessBar();
        Battle battle = new Battle(List.of(bloom, ally), List.of(enemy), fixed());
        battle.startBattle();
        Assertions.assertEquals(0.0, ally.getBuffManager().toughnessBoost(), 1e-9, "nothing before the cast");

        battle.castImmediate(bloom.getSkills().get(SkillType.SKILL), bloom, List.of(enemy));

        Assertions.assertEquals(0.5, ally.getBuffManager().toughnessBoost(), 1e-9,
                "「我方全体的弱点击破效率提高50%」 -- the boost reaches an ally, not just her");
        Assertions.assertEquals(0.5, bloom.getBuffManager().toughnessBoost(), 1e-9, "…and her (「我方全体」 includes her)");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private static TriggerTable.TriggerContext ctx(Character owner, Character actor, Enemy target, Battle battle) {
        return new TriggerTable.TriggerContext(owner, actor, target, 0, 0, null, battle);
    }

    /** Note: An enemy that HAS a toughness bar and a weakness, found by asking rather than assumed. */
    private static Enemy enemyWithAToughnessBar() {
        for (int id = 1002010; id < 1002100; id++) {
            try {
                Enemy candidate = EnemyFactory.create(id, 90, 1);
                if (candidate.hasToughnessBar() && !candidate.getStanceWeak().isEmpty()) {
                    return candidate;
                }
            } catch (RuntimeException ignored) {
                // not in the data
            }
        }
        throw new AssertionError("no enemy with a toughness bar in the probed range");
    }

    /** Breaks an enemy through the engine, using <b>its own declared weakness</b> (never a hard-coded element). */
    private static void breakIt(Battle battle, Character breaker, Enemy enemy) {
        DamageElement element = enemy.getStanceWeak().iterator().next();
        battle.reduceToughness(breaker, enemy, element, 999);
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
