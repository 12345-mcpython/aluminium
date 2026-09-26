package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1101 布洛妮娅 (Bronya): her 阵地 trace and her first <b>Eidolon</b>.
 *
 * <p>星魂 1 养精蓄锐 is the case {@code engine.md} §9 and {@code SkillPointGameParityTest} had carried as "not
 * implemented" for as long as the trigger table has existed — 「施放战技时，有 50% 的固定概率恢复 1 个战技点，该效果
 * 有 1 回合的触发冷却」. The missing part was never one hook: it is a rule-level probability, a cooldown, and a
 * way to say "this belongs to an Eidolon", all three of which arrived together, which is why the rule can now be
 * written where every other mechanic lives — in her file.
 *
 * <p>Her 阵地 is in the same file (a battle-start party DEF buff), so this also keeps pinning "one character,
 * several mechanics, one table" — and in this file the three mechanics have three different natures: a trace, an
 * Eidolon, and the relic-style party buff shape.
 */
public class BronyaEidolonTest {
    private static final int BRONYA = 1101;
    /** Tingyun: a real ally with no rule file of her own, so only Bronya's table can act. */
    private static final int ALLY = 1202;
    private static final int LEVEL = 80;
    private static final double DEFENCE_TRACE = 0.2;

    // ==================================================================
    // 阵地: 「战斗开始时，我方全体的防御力提高20%，持续2回合」
    // ==================================================================

    @Test
    public void herDefenceTraceBuffsTheWholePartyAtBattleStart() {
        Battle battle = battleWith(0, 0.5);
        Character bronya = battle.characters.getFirst();
        Character ally = battle.characters.get(1);

        Assertions.assertEquals(List.of(DEFENCE_TRACE), defenceBuffsOn(bronya), "20% DEF on the wearer");
        Assertions.assertEquals(List.of(DEFENCE_TRACE), defenceBuffsOn(ally),
                "and on 我方全体 -- without an explicit target the effect would have buffed only Bronya");
    }

    @Test
    public void theDefenceTraceExpiresAfterItsTwoTurns() {
        Battle battle = battleWith(0, 0.5);
        Character bronya = battle.characters.getFirst();
        Assertions.assertEquals(1, defenceBuffsOn(bronya).size(), "precondition: it is on");

        for (int turn = 0; turn < 6 && !battle.isOver(); turn++) {
            battle.stepForward();
            battle.beforeMove();
            battle.afterMove();
        }

        Assertions.assertEquals(0, defenceBuffsOn(bronya).size(), "「持续2回合」, counted on her own turns");
    }

    // ==================================================================
    // 星魂 1 养精蓄锐
    // ==================================================================

    @Test
    public void herFirstEidolonIsGatedOnTheRank() {
        Assertions.assertEquals(0, skillPointsFromOneSkillCast(0, 0.1),
                "rank 0: the rule exists but its gate is not satisfied");
        Assertions.assertEquals(1, skillPointsFromOneSkillCast(1, 0.1),
                "rank 1, with the roll passing (0.1 < 0.5)");
    }

    @Test
    public void theEidolonRollsItsFiftyPercent() {
        Assertions.assertEquals(0, skillPointsFromOneSkillCast(1, 0.9),
                "0.9 is not below 0.5, so nothing is restored -- and the roll is the battle's own generator, "
                        + "which is what lets this be asserted instead of gambled");
    }

    @Test
    public void itsOneTurnCooldownStopsASecondCastInTheSameTurn() {
        Battle battle = battleWith(1, 0.1);
        Character bronya = battle.characters.getFirst();
        drainSkillPoints(battle);

        castSkill(battle, bronya);
        Assertions.assertEquals(1, battle.getSkillPoints(), "the first cast fires");

        castSkill(battle, bronya);
        Assertions.assertEquals(1, battle.getSkillPoints(),
                "「该效果有 1 回合的触发冷却」: a second cast in the same turn changes nothing");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private static int skillPointsFromOneSkillCast(int rank, double roll) {
        Battle battle = battleWith(rank, roll);
        Character bronya = battle.characters.getFirst();
        drainSkillPoints(battle);
        castSkill(battle, bronya);
        return battle.getSkillPoints();
    }

    private static void castSkill(Battle battle, Character hero) {
        battle.castImmediate(hero.getSkills().get(SkillType.SKILL), hero,
                List.of(battle.enemyUnits().getFirst()));
    }

    private static void drainSkillPoints(Battle battle) {
        while (battle.spendSkillPoint()) {
            // drain to zero
        }
    }

    /** The BUFF-sourced modifiers on DEFENCE, as values — the idiom the relic tests use for a set's bonus. */
    private static List<Double> defenceBuffsOn(Character who) {
        return who.getAttribute(AttributeType.DEFENCE)
                .filterBySource(DoubleValue.Modifier.ModifierSource.BUFF)
                .stream()
                .map(DoubleValue.Modifier::getValue)
                .toList();
    }

    private static Battle battleWith(int rank, double roll) {
        Character bronya = CharacterFactory.create(BRONYA, LEVEL, true, null, null, rank);
        Battle battle = new Battle(List.of(bronya, CharacterFactory.create(ALLY, LEVEL)),
                List.of(dummy()), fixed(roll));
        battle.startBattle();
        return battle;
    }

    /** A generator that always answers the same value, so the 50% becomes an assertion. */
    private static Random fixed(double value) {
        return new Random() {
            @Override
            public double nextDouble() {
                return value;
            }
        };
    }

    private static Enemy dummy() {
        return Enemy.fromAttributes("dummy", 1_000_000, 0, 100, 100);
    }
}
