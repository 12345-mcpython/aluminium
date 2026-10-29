package com.laosun.aluminium.test.content.characters.silverwolflv999;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.entity.Character;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * Character 1506 (Silver Wolf LV.999), 欢愉技·强化 (崩坏级伤害演示, slot 21): 「造成 <b>6</b> 次伤害，每次对敌方随机单体造成
 * <b>90%</b> 的虚数属性欢愉伤害」 - and her own row puts those two numbers in the opposite order to the rows the engine reads.
 *
 * <p>⚠⚠ <b>This is a GAP WITNESS, not a pass.</b> {@code SkillExecutor}'s Elation-damage branch reads a row as
 * {@code [hits, per-hit share, final share]}, which is 8009/8010's layout ({@code [8, 0.1, 0.3]}). Her row is
 * {@code [1.125, 6.0]} - share first, count second - so the branch takes {@code round(1.125) = 1} for the count and
 * {@code 6.0} for the per-hit share: <b>one instance of 600%</b> instead of six of 112.5%. Measured on this fixture: 878.17,
 * where the text's six hits would be 987.94 (6.75 x ATK against 6.0 x ATK before zones).
 *
 * <p>Why a witness rather than a fix: the count is not identifiable from the row alone - 1513's slot 20 is a THIRD layout
 * ({@code [0.3, 10, 0.09]}, an AoE share then count then per-hit share) and is registered as such in its own file, while
 * 8009/8010's rows are read correctly today. Making the branch guess would break the rows that work. The capability this
 * needs - "which column is the count", or the layout written into {@code skill_effects.json} - is registered in AGENT.md
 * §4.3; when it lands, THIS case is what goes red and points at the file to update.
 */
public class SilverWolfLV999ElationRowTest {
    private static final int WOLF = 1506;
    /**
     * The reading the engine produces today, measured with a source that never crits (so the number is the row's, not a
     * roll's). The text's six hits would settle 987.9408235224 on the same fixture.
     */
    private static final double TODAY_ONE_INSTANCE_OF_SIX_HUNDRED = 878.1696209088004;

    /**
     * Her row is {@code [share, count]}, and the engine reads the first column as a count.
     */
    @Test
    public void herRowIsShareFirstAndTheElationBranchReadsItAsACount() {
        // Note: Through a started battle, because her level comes from her own `level_convention` rule (a table read at level
        // 1 would answer [0.45, 6.0] and hide which column is which - measured).
        Battle battle = new Battle(List.of(CharacterFactory.create(WOLF, 80)), List.of(BattleFixture.monster()),
                new NoCrit());
        battle.startBattle();
        battle.processRequests();
        Character wolf = battle.characters().getFirst();
        var skill = wolf.getSkills().get(SkillType.ELATION_EXTRA);
        int level = wolf.skillLevel(skill);
        var row = skill.getData().getSkills().get(level - 1);
        System.out.println("[1506] slot 21 at her level " + level + " reads " + row
                + " ; the engine takes round(row[0]) = " + Math.round(row.getFirst()) + " for the count"
                + " and row[1] = " + row.get(1) + " for the per-hit share");
        Assertions.assertEquals(1.125, row.getFirst(), 1e-9, "第一列是**份额**（Lv15 的 112.5%）");
        Assertions.assertEquals(6.0, row.get(1), 1e-9, "第二列才是**次数**（正文的 6）");
        Assertions.assertEquals(6, (int) Math.round(row.get(1)), "「造成 6 次伤害」说的是第二列");
        Assertions.assertEquals(1, (int) Math.round(row.getFirst()),
                "而欢愉行分支把第一列当次数 ⇒ 今天只结算 1 次（这就是缺口）");
    }

    /**
     * And the consequence, measured: one 600% instance rather than six 112.5% ones.
     */
    @Test
    public void theCastSettlesOneBigInstanceInsteadOfSix() {
        Battle battle = new Battle(List.of(CharacterFactory.create(WOLF, 80)), List.of(BattleFixture.monster()),
                new NoCrit());
        battle.startBattle();
        battle.processRequests();
        Character wolf = battle.characters().getFirst();
        var enemy = battle.enemyUnits().getFirst();
        double before = enemy.getCurrentHp();
        battle.castImmediate(wolf.getSkills().get(SkillType.ELATION_EXTRA), wolf, List.of(enemy));
        battle.processRequests();
        double dealt = before - enemy.getCurrentHp();
        System.out.println("[1506] 崩坏级伤害演示 settled " + dealt + " (today's reading; six hits would be "
                + (dealt * 6.75 / 6.0) + ")");
        Assertions.assertEquals(TODAY_ONE_INSTANCE_OF_SIX_HUNDRED, dealt, 1e-6,
                "今天的读数 = 1 次 × 600%：这是被登记的缺口，不是期望的行为");
    }

    /**
     * A source that never crits, so the reading is the row's number rather than a roll's.
     */
    private static final class NoCrit extends Random {
        @Override
        public double nextDouble() {
            return 0.9999;
        }
    }
}
