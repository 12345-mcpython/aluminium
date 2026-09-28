package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.Signal;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.models.skill.DefaultSkill;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 寒鸦 (1215), from her own file (2026-09-28): the ultimate, 星魂 1/2/3/5.
 *
 * <p><b>What her kit needed from the engine.</b> The SPD share is a <b>derived</b> magnitude read off her own panel
 * (`scale: self_attr:SPEED`), the ATK boost and the level raises are ordinary content, and 星魂 1's 「持有<b>终结技效果</b>
 * 的我方目标消灭敌方目标时」 is readable only because the ultimate also plants a marker <b>state</b> — a named modifier
 * is invisible to `has_state`.
 *
 * <p><b>What is registered instead of approximated</b> (see the file's own note): the whole 【承负】 family — it needs a
 * state counter with a threshold (「每 2 次…恢复1个战技点」/「触发 2 次后自动解除」) — plus 星魂 4 and 6, which need a way to
 * raise another rule's effect value or duration.
 */
public class HanyaKitTest {
    private static final int HANYA = 1215;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final double EPS = 1e-6;

    /** One cast: the ally's SPD grows by 20% of <b>her</b> SPD, the ATK by 60%, and the marker state lands. */
    @Test
    public void herUltimateBuffsTheAimedAllyNotHerself() {
        Fixture f = new Fixture(0);
        double herSpeed = f.hanya.getAttribute(AttributeType.SPEED).get();
        double allySpeedBefore = f.ally.getAttribute(AttributeType.SPEED).get();
        double allyAttackBefore = f.ally.getAttribute(AttributeType.ATTACK).get();
        double herAttackBefore = f.hanya.getAttribute(AttributeType.ATTACK).get();

        f.castUltimateOn(f.ally);

        Assertions.assertEquals(allySpeedBefore + 0.2 * herSpeed, f.ally.getAttribute(AttributeType.SPEED).get(), 1.0,
                "「提高数值等同于寒鸦速度的20%」: a derived share of HER panel, not a percentage of the ally's speed");
        Assertions.assertTrue(f.ally.getAttribute(AttributeType.ATTACK).get() > allyAttackBefore, "…plus a flat 60% ATK");
        Assertions.assertTrue(f.ally.getBuffManager().hasState("敕令"),
                "and the effect is also a STATE, which is the only way 星魂 1's 「持有终结技效果的我方目标」 is readable");
        Assertions.assertEquals(herAttackBefore, f.hanya.getAttribute(AttributeType.ATTACK).get(), EPS,
                "「指定我方单体」: a self-cast is not a target (target != self)");
    }

    /** 星魂 1: the ally who carries her ultimate gets a kill → she advances; once per turn. */
    @Test
    public void herFirstEidolonAdvancesHerWhenTheCarrierGetsAKill() {
        Fixture f = new Fixture(1);
        f.castUltimateOn(f.ally);
        double before = remainingWait(f, f.hanya);

        f.allyKills();

        double after = remainingWait(f, f.hanya);
        Assertions.assertTrue(after < before,
                "「持有终结技效果的我方目标消灭敌方目标时，寒鸦行动提前15%」 (" + before + " -> " + after + ")");

        double afterFirst = after;
        f.allyKills();
        Assertions.assertEquals(afterFirst, remainingWait(f, f.hanya), 1.0,
                "「该效果每回合只能触发1次」: per_turn: 1");
    }

    /** 星魂 2: her own Skill raises her speed for a turn. */
    @Test
    public void herSecondEidolonSpeedsHerUpAfterASkill() {
        Fixture f = new Fixture(2);
        double before = f.hanya.getAttribute(AttributeType.SPEED).get();

        f.castSkill();

        Assertions.assertTrue(f.hanya.getAttribute(AttributeType.SPEED).get() > before,
                "「施放战技后，速度提高20%，持续1回合」");
    }

    /** 星魂 3 / 5: the level raises, and the talent's base level composes with them (M-32). */
    @Test
    public void herEidolonLevelRaisesAreStated() {
        Fixture atThree = new Fixture(3);
        Assertions.assertEquals(2, atThree.hanya.skillLevelBonus(SkillType.SKILL), "星魂 3「战技等级+2」");
        Assertions.assertEquals(1, atThree.hanya.skillLevelBonus(SkillType.COMMON), "…「普攻等级+1」");

        Fixture atFive = new Fixture(5);
        Assertions.assertEquals(2, atFive.hanya.skillLevelBonus(SkillType.ULTRA), "星魂 5「终结技等级+2」");
        Assertions.assertEquals(11, atFive.hanya.skillLevelBonus(SkillType.TALENT),
                "…「天赋等级+2」 on top of the +9 base level the file states (10 + 2)");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private static final class Fixture {
        private final Character hanya;
        private final Character ally;
        private final Enemy enemy;
        private final Battle battle;

        private Fixture(int eidolon) {
            hanya = CharacterFactory.create(HANYA, LEVEL, true, null, null, eidolon);
            ally = CharacterFactory.create(ALLY, LEVEL);
            enemy = EnemyFactory.create(MONSTER, 90, 1);
            battle = new Battle(List.of(hanya, ally), List.of(enemy), fixed());
            battle.startBattle();
        }

        private void castUltimateOn(Character target) {
            battle.castImmediate(hanya.getSkills().get(SkillType.ULTRA), hanya, List.of(target));
        }

        private void castSkill() {
            battle.castImmediate(hanya.getSkills().get(SkillType.SKILL), hanya, List.of(enemy));
        }

        /** The ally lands the killing blow: a KILL event whose actor is the ally. */
        private void allyKills() {
            enemy.takeDamage(enemy.getMaxHp() - 1);
            battle.applyDamage(enemy, new Damage(ally, enemy, com.laosun.aluminium.enums.DamageElement.PHYSICAL,
                    com.laosun.aluminium.enums.DamageType.NORMAL, 1_000,
                    com.laosun.aluminium.enums.SkillCategory.NORMAL));
            Assertions.assertTrue(enemy.isDeath(), "precondition: the ally's hit killed it");
        }
    }

    private static double remainingWait(Fixture f, Character unit) {
        Signal signal = f.battle.queue.snapshot().stream()
                .filter(s -> s.getCanHit() == unit)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no signal"));
        return f.battle.queue.getTimeRemaining(signal);
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
