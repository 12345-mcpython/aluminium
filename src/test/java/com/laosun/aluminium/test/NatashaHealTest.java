package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 娜塔莎 (1105), from her own file (2026-09-28): the two heals, the cleanse and the outgoing-heal trace.
 *
 * <p><b>What it needed.</b> A derived magnitude read off the healer's <b>settled max HP</b> ({@code scale: self_max_hp}) —
 * 「回复等同于娜塔莎生命上限的 10.50% + 280」. ⚠ Not {@code self_attr:HEALTH}: that is the attribute table's HEALTH,
 * while 「生命上限」 is {@code getMaxHp()}, which every +HP% effect moves; the two agree until one does.
 *
 * <p><b>What is registered instead</b> (the file's own notes): the regeneration and everything that rides on it, plus the
 * talent's 「为生命百分比 ≤ 30% 的我方目标提供治疗时，治疗量提高 50%」 — a heal-over-time has no spelling yet, and a
 * boost that must be in place <i>before</i> a heal is computed has no hook ({@code HEALED} fires after).
 */
public class NatashaHealTest {
    private static final int NATASHA = 1105;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /** The heal really is a share of her max HP plus the constant, and it goes to the aimed ally. */
    @Test
    public void herSkillHealsTheAimedAlly() {
        Fixture f = new Fixture();
        // ⚠ A fraction of max HP, not a flat 3000: a level-80 ally has far less than that, and the first version of this
        // test killed the ally it was about to heal (measured: the 命中 assertion read 0.0).
        f.ally.takeDamage(f.ally.getMaxHp() * 0.5);
        double before = f.ally.getCurrentHp();
        // 「医者」 (+10% outgoing healing) is on the same file, so the settled heal is the row times 1.1.
        // ⚠ The ENGINE’s skill execution is at level 1 (params [0.07, 0.048, 2, 70, 48]), not at the Lv10 row the
        // document quotes -- a *rule* states its own magnitude, which is why 1105’s file needed no rule for this heal at
        // all. The +10% is 行迹 医者, which is live on this character.
        double expected = (0.07 * f.natasha.getMaxHp() + 70) * 1.1;

        double herBefore = f.natasha.getCurrentHp();
        f.castSkillOn(f.ally);

        Assertions.assertEquals(Math.min(f.ally.getMaxHp(), before + expected), f.ally.getCurrentHp(), 1.0,
                "「立即为指定我方单体回复等同于娜塔莎10.50%生命上限+280的生命值」");
        Assertions.assertEquals(herBefore, f.natasha.getCurrentHp(), 1.0,
                "「指定我方单体」 is the ally, not her -- ⚠ the first version compared the ALLY's HP with hers");
    }

    /** Her ultimate heals the whole side. */
    @Test
    public void herUltimateHealsEveryAlly() {
        Fixture f = new Fixture();
        f.natasha.takeDamage(f.natasha.getMaxHp() * 0.5);
        f.ally.takeDamage(f.ally.getMaxHp() * 0.5);   // ⚠ or 「我方全体」 has nothing to restore on the ally
        double allyBefore = f.ally.getCurrentHp();
        double herBefore = f.natasha.getCurrentHp();

        // ⚠ The target set is the CALLER’s: the engine heals the units it is handed (the skill’s own AoE shape is
        // resolved upstream), so both allies are named here.
        f.battle.castImmediate(f.natasha.getSkills().get(SkillType.ULTRA), f.natasha, List.of(f.ally, f.natasha));

        double expected = (0.092 * f.natasha.getMaxHp() + 92) * 1.1;   // Lv1 row [0.092, 92], plus 行迹 医者
        Assertions.assertEquals(Math.min(f.ally.getMaxHp(), allyBefore + expected), f.ally.getCurrentHp(), 1.0,
                "「立即为我方全体回复等同于娜塔莎13.80%生命上限+368」 (the Lv10 row the prose quotes; the engine runs Lv1)");
        Assertions.assertEquals(Math.min(f.natasha.getMaxHp(), herBefore + expected), f.natasha.getCurrentHp(), 1.0,
                "…and 「我方全体」 includes her");
    }

    /** 行迹 医者 raises the outgoing healing, which is read from the healer while the amount is computed. */
    @Test
    public void herTraceRaisesHerOutgoingHealing() {
        Fixture f = new Fixture();
        double boost = f.natasha.getAttribute(com.laosun.aluminium.enums.AttributeType.OUTGOING_HEALING_BOOST).get();
        Assertions.assertEquals(0.1, boost, 1e-6,
                "「娜塔莎提供的治疗量提高10%」 -- the first shipped content to grant this attribute");
    }

    /**
     * The regeneration settles on each of the carrier's turns, and then stops (2026-09-28).
     *
     * <p>⚠ This is the case that used to be impossible to write at all: 「同时目标每回合开始时为其回复…持续2回合」 cannot be a
     * {@code TURN_START} rule, because buffs are counted down by the early tick that runs <b>before</b> that event — a
     * {@code turns: 2} state would heal once where the document says twice. `APPLY_REGEN` settles beside the DOT pass,
     * which runs before the countdown, so two turns really are two heals (three here: 行迹 调理 adds a turn).
     */
    @Test
    public void theRegenerationTicksOnEachOfTheCarriersTurns() {
        Fixture f = new Fixture();
        f.ally.takeDamage(f.ally.getMaxHp() * 0.5);
        f.castSkillOn(f.ally);
        double perTick = (0.072 * f.natasha.getMaxHp() + 192) * 1.1;    // Lv10 row #2/#5, plus 行迹 医者
        double afterCast = f.ally.getCurrentHp();

        f.allyTurn();
        Assertions.assertEquals(Math.min(f.ally.getMaxHp(), afterCast + perTick), f.ally.getCurrentHp(), 1.0,
                "first turn start: 「目标每回合开始时为其回复…」");

        double afterFirst = f.ally.getCurrentHp();
        f.allyTurn();
        Assertions.assertEquals(Math.min(f.ally.getMaxHp(), afterFirst + perTick), f.ally.getCurrentHp(), 1.0,
                "second turn start: a `turns: 2` regeneration settles twice, which is the whole point of settling before "
                        + "the duration is counted down");

        double afterThird = f.ally.getCurrentHp();
        f.allyTurn();
        Assertions.assertEquals(Math.min(f.ally.getMaxHp(), afterThird + perTick), f.ally.getCurrentHp(), 1.0,
                "…and a third time, because 行迹 调理 lengthens it by one turn");
        double afterFourth = f.ally.getCurrentHp();
        f.allyTurn();
        Assertions.assertEquals(afterFourth, f.ally.getCurrentHp(), 1.0, "…and then it is over");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private static final class Fixture {
        private final Character natasha = CharacterFactory.create(NATASHA, LEVEL);
        private final Character ally = CharacterFactory.create(ALLY, LEVEL);
        private final Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        private final Battle battle = new Battle(List.of(natasha, ally), List.of(enemy), fixed());

        private Fixture() {
            battle.startBattle();
        }

        private void castSkillOn(Character target) {
            battle.castImmediate(natasha.getSkills().get(SkillType.SKILL), natasha, List.of(target));
        }

        /** One full turn of the ally: the moment 「目标每回合开始时」 would name. */
        private void allyTurn() {
            battle.currentMove = battle.queue.snapshot().stream()
                    .filter(signal -> signal.getCanHit() == ally)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("no signal for the ally"));
            battle.beforeMove();
            battle.afterMove();
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
