package com.laosun.aluminium.test.content.characters.jingyuan;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.entity.Summon;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.trigger.TriggerPayload;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * 1204 景元's 【神君】 strikes on ITS OWN turn: 「【神君】行动时…攻击段数越多伤害次数越多」.
 *
 * <p>The summon is a panel-less combatant, so its turn start is the action point. The repeat count rides the `攻击段数`
 * counter through `times_from: "steps:self_stacks:攻击段数/1"` — one instance per layer.
 */
public class JingYuanLordTurnTest {
    private static final int JING_YUAN = 1204;
    private static final int LEVEL = 80;

    @Test
    public void theLordStrikesOncePerHitCount() {
        Battle battle = scene();
        Summon lord = battle.summonsOf(battle.characters().getFirst()).getFirst();
        Character jingYuan = battle.characters().getFirst();
        battle.castImmediate(jingYuan.getSkills().get(com.laosun.aluminium.enums.SkillType.SKILL), jingYuan,
                List.of((Enemy) battle.enemies().getFirst()));
        battle.processRequests();
        int hits = lord.getBuffManager().stacksOf("攻击段数");
        Assertions.assertTrue(hits >= 2, "precondition: the Skill added two hits (" + hits + ")");

        Enemy enemy = (Enemy) battle.enemies().getFirst();
        double before = enemy.getCurrentHp();
        battle.fire(TriggerPayload.Simple.of(TriggerEvent.TURN_START, lord, lord, 1, 0));
        battle.processRequests();

        Assertions.assertTrue(enemy.getCurrentHp() < before,
                "「【神君】行动时…造成雷属性伤害」 — the enemy lost HP on the Lord's turn");
    }

    private static Battle scene() {
        Character jingYuan = CharacterFactory.create(JING_YUAN, LEVEL);
        Enemy enemy = BattleFixture.monster();
        enemy.setMaxStance(10_000);
        Battle battle = new Battle(List.of(jingYuan), List.of(enemy), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        battle.processRequests();
        return battle;
    }
}