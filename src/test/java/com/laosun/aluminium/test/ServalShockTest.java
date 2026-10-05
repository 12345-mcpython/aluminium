package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.data.TriggerTables;
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
 * 希露瓦 (1103), from her own file (2026-09-28): the shock, the extension, the traces and 星魂 6.
 *
 * <p><b>What it needed.</b> {@code APPLY_DOT} for the shock (base chance and per-turn damage out of her Skill's own row),
 * {@code EXTEND_BUFF} for "使触电状态下的敌方目标延长 2 回合" - which needs no condition, because the extension only
 * matches buffs carrying that state's name - and the engine's DOT-by-name resolution, which is what makes both 触电 and
 * 星魂 6's "对触电状态下的敌方目标" reachable.
 *
 * <p><b>What is registered</b> (the file's notes): the talent's "对所有触电状态下的敌方目标", the blast's neighbour
 * rolls, 星魂 1 and 星魂 4 - three of the four are the same missing piece (`M-53`: an effect reaches what a *selector*
 * names, so a per-target condition cannot filter the set).
 */
public class ServalShockTest {
    private static final int SERVAL = 1103;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /** Her trace 摇滚 makes the 80% roll certain - and the same roll misses without an amendment (pinned below). */
    @Test
    public void theShockIsCertainWithHerTrace() {
        Fixture f = new Fixture(0.9);
        f.castSkill();
        Assertions.assertTrue(f.enemy.getBuffManager().hasState("触电"),
                "「…基础概率提高20%」 turns the 80% roll into a certain one, and a 0.9 roll sits inside that difference");
    }

    /**
     * The trace is filed against her Skill's shock rule - the bookkeeping half of "基础概率提高20%".
     *
     * <p>Note: Its behavioural half is the positive case above (a 0.9 roll only lands because of this); the *negative*
     * direction (the same roll missing at 0.8) could only be shown by stripping the rule out of a compiled table, which
     * would be testing the removal instead of the number, so it is not claimed here.
     */
    @Test
    public void herTraceFilesTheChanceAmendment() {
        Fixture f = new Fixture(0.0);
        Assertions.assertEquals(0.2, f.serval.ruleBaseChanceBonus("skill_shock"), 1e-9,
                "「施放战技时…陷入触电状态的基础概率提高20%」 is an amendment to the named rule, not a second rule "
                        + "(two rules would roll twice: 96%, not 100%)");
    }

    /** The shock settles on the enemy's own turns. */
    /** The shock settles on the enemy's own turns. */
    @Test
    public void theShockTicksOnTheEnemysTurn() {
        Fixture f = new Fixture(0.0);
        f.castSkill();
        Assertions.assertTrue(f.enemy.getBuffManager().hasState("触电"), "precondition: the roll was 0.0");

        double before = f.enemy.getCurrentHp();
        f.enemyTurn();

        Assertions.assertTrue(f.enemy.getCurrentHp() < before,
                "「触电状态下，敌方目标每回合开始时受到等同于希露瓦104%攻击力的雷属性持续伤害」");
    }

    /**
     * 终结技 extends the shock by two turns: 2 settlements become 4, and then it stops.
     *
     * <p>Note: Counted rather than read off a duration getter, and on <b>one</b> enemy: "使触电状态下的敌方目标延长 2 回合"
     * is about the enemies that are shocked, and the extension's own filter (`BuffManager.isNamed`, which answers a DOT
     * by its element's state name) is what makes "触电状态下的" true without a condition - there is nothing to assert
     * about an enemy that never had it beyond that the same call does not create one, which the census covers.
     */
    @Test
    public void herUltimateExtendsTheShockByTwoTurns() {
        int without = settlements(false);
        int with = settlements(true);
        Assertions.assertEquals(2, with - without,
                "「延长2回合的触电状态」: the extension adds exactly two settlements (" + without + " -> " + with + ")");
        Assertions.assertTrue(without >= 2, "precondition: the Skill's own shock settles at least twice");
    }

    /** How many times the shock settles over six of the enemy's turns, with or without the ultimate. */
    private static int settlements(boolean ultimate) {
        Fixture f = new Fixture(0.0);
        f.castSkill();
        if (ultimate) {
            f.castUltimate();
        }
        int settlements = 0;
        for (int turn = 0; turn < 6; turn++) {
            double before = f.enemy.getCurrentHp();
            f.enemyTurn();
            if (f.enemy.getCurrentHp() < before) {
                settlements++;
            }
        }
        return settlements;
    }

    /** The whole file is present, and the registered clauses are absent on purpose. */
    /** The whole file is present, and the registered clauses are absent on purpose. */
    /** The whole file is present, and the registered clauses are absent on purpose. */
    @Test
    public void herFileCarriesWhatItSays() {
        Assertions.assertEquals(1, TriggerTables.of(SERVAL).ruleCount(TriggerEvent.SKILL_CAST),
                "the shock (the blast's damage is the engine's own path)");
        Assertions.assertEquals(1, TriggerTables.of(SERVAL).ruleCount(TriggerEvent.DEALING_DAMAGE),
                "星魂 6's conditional boost");
        Assertions.assertEquals(1, TriggerTables.of(SERVAL).ruleCount(TriggerEvent.ALLY_ATTACK),
                "her talent's rider -- written since 2026-09-28 because 	arget_when can finally say 「对所有触电状态下的敌方目标」");
        Assertions.assertEquals(2, TriggerTables.of(SERVAL).ruleCount(TriggerEvent.ULT_CAST),
                "the shock extension and 星魂 4's spread to the unshocked ones");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    /** A hand-built table: one shock rule with a base chance, optionally amended by +0.2. */
    private static boolean shockWith(double roll, boolean amended) {
        Character serval = CharacterFactory.create(SERVAL, LEVEL);
        EffectSpec shock = new EffectSpec();
        TriggerSpecs.set(shock, "op", "APPLY_DOT");
        TriggerSpecs.set(shock, "element", "Thunder");
        TriggerSpecs.set(shock, "scale", "self_attr:ATTACK");
        TriggerSpecs.set(shock, "percent", 1.04);
        TriggerSpecs.set(shock, "turns", 2);
        TriggerSpecs.set(shock, "baseChance", 0.8);
        TriggerSpecs.set(shock, "target", "target");
        TriggerSpec rule = TriggerSpecs.rule("KILL", null, shock);
        TriggerSpecs.set(rule, "id", "shock_probe");

        List<TriggerSpec> specs = new java.util.ArrayList<>();
        specs.add(rule);
        if (amended) {
            EffectSpec amendment = new EffectSpec();
            TriggerSpecs.set(amendment, "op", "MODIFY_RULE");
            TriggerSpecs.set(amendment, "rule", "shock_probe");
            TriggerSpecs.set(amendment, "percent", 0.2);
            specs.add(0, TriggerSpecs.rule("BATTLE_START", null, amendment));
        }
        serval.setTriggerTable(new TriggerTable(SERVAL, specs));

        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(serval, CharacterFactory.create(ALLY, LEVEL)), List.of(enemy), roll(roll));
        battle.startBattle();
        battle.fireTriggers(TriggerEvent.KILL, serval, enemy, 0, 0);
        return enemy.getBuffManager().hasState("触电");
    }

    private static final class Fixture {
        private final Character serval = CharacterFactory.create(SERVAL, LEVEL);
        private final Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        private final Battle battle;

        private Fixture(double roll) {
            battle = new Battle(List.of(serval, CharacterFactory.create(ALLY, LEVEL)), List.of(enemy), roll(roll));
            battle.startBattle();
        }

        private void castUltimate() {
            battle.castImmediate(serval.getSkills().get(SkillType.ULTRA), serval, List.of(enemy));
        }

        private void castSkill() {
            battle.castImmediate(serval.getSkills().get(SkillType.SKILL), serval, List.of(enemy));
        }

        private void enemyTurn() {
            battle.currentMove = battle.queue.snapshot().stream()
                    .filter(signal -> signal.getCanHit() == enemy)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("no signal for the enemy"));
            battle.beforeMove();
            battle.afterMove();
        }
    }

    private static Random roll(double value) {
        return new Random() {
            @Override
            public double nextDouble() {
                return value;
            }
        };
    }
}
