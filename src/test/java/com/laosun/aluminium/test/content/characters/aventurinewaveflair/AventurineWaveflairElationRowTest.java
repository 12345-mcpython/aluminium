package com.laosun.aluminium.test.content.characters.aventurinewaveflair;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.trigger.TriggerTable;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 1513 砂金•戏浪's Elation skill row: 「对敌方全体造成 <b>60%</b>…并<b>额外造成 10 次</b>伤害，每次对随机敌方单体造成 <b>18.00%</b>…」
 *
 * <p><b>Why a curated row.</b> The engine's {@code ElationDamage} branch reads the layout of 8009/8010's rows
 * ({@code [hits, per-hit share, final share]}), while 1513's row is {@code [全体份额, 次数, 每段份额]} - the same KIND of
 * skill with a different column order. Read automatically, the whole cast settled off 0.6 and the ten instances vanished
 * silently. The curated entry names the two parts, so the row is read as the sentence is written.
 *
 * <p>The criterion counts INSTANCES (one area hit plus ten), which is what the sentence states and what a wrong column order
 * cannot fake: the old reading produced exactly one.
 */
public class AventurineWaveflairElationRowTest {
    private static final int AVENTURINE = 1513;
    private static final int LEVEL = 80;

    /**
     * 「全体那一段 + 额外 10 次」 = eleven settled instances.
     */
    @Test
    public void theRowSettlesTheAreaHitAndTenExtraInstances() {
        Assertions.assertEquals(11, instancesOf(20), "1 area hit + 10 extra instances (「额外造成 10 次」)");
    }

    /**
     * The enhanced row reads its own columns: 0.6 area + ten 0.18 instances, and NOT the 0.21 per-point share.
     */
    @Test
    public void theEnhancedRowAlsoSettlesElevenInstances() {
        Assertions.assertEquals(11, instancesOf(21), "the enhanced row's extra instances are its #3 / #4 columns");
    }

    // ==================================================================

    /** Counts the damage instances a cast settles, through a scratch rule on DEALING_DAMAGE. */
    private static int instancesOf(int slot) {
        Character aventurine = CharacterFactory.create(AVENTURINE, LEVEL);
        Enemy enemy = BattleFixture.dummy();
        Battle battle = new Battle(new ArrayList<>(List.of(aventurine)), new ArrayList<>(List.of(enemy)),
                BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        aventurine.setTriggerTable(new TriggerTable(AVENTURINE, List.of(countingRule())));
        battle.processRequests();
        battle.castImmediate(new com.laosun.aluminium.skill.DefaultSkill(AVENTURINE, slot, LEVEL), aventurine,
                List.of(enemy));
        battle.processRequests();
        return aventurine.getBuffManager().stacksOf("计数");
    }

    private static TriggerSpec countingRule() {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "ADD_STACK");
        TriggerSpecs.set(effect, "buff", "计数");
        TriggerSpecs.set(effect, "maxStacks", 99);
        TriggerSpecs.set(effect, "permanent", true);
        TriggerSpecs.set(effect, "target", "self");
        return TriggerSpecs.rule("DEALING_DAMAGE", List.of(), effect);
    }
}
