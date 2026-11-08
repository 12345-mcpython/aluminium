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

    /**
     * 行迹 破阵:「若【神君】的攻击段数 >= 6 段，则其暴击伤害提高 25%」.
     *
     * <p>The Lord enters with THREE hits (the talent says so), which is below the threshold; two Skill casts take it to seven,
     * which is above it. So the same turn start does nothing at first and raises the crit on the second probe.
     */
    @Test
    public void theFormationTraceRaisesTheLordsCritDamage() {
        Battle battle = scene();
        Character jingYuan = battle.characters().getFirst();
        Summon lord = battle.summonsOf(jingYuan).getFirst();
        Enemy enemy = (Enemy) battle.enemies().getFirst();
        Assertions.assertEquals(3, lord.getBuffManager().stacksOf("攻击段数"), "the talent starts it at three");
        double critBefore = lord.getAttribute(com.laosun.aluminium.enums.AttributeType.CRIT_ATTACK).get();
        battle.fire(TriggerPayload.Simple.of(TriggerEvent.TURN_START, lord, lord, 1, 0));
        battle.processRequests();
        Assertions.assertEquals(critBefore, lord.getAttribute(com.laosun.aluminium.enums.AttributeType.CRIT_ATTACK).get(),
                1e-9, "three hits are below the threshold, so nothing happens");

        for (int i = 0; i < 2; i++) {
            battle.castImmediate(jingYuan.getSkills().get(com.laosun.aluminium.enums.SkillType.SKILL), jingYuan,
                    List.of(enemy));
            battle.processRequests();
        }
        Assertions.assertEquals(7, lord.getBuffManager().stacksOf("攻击段数"), "two casts add four more");
        double before = lord.getAttribute(com.laosun.aluminium.enums.AttributeType.CRIT_ATTACK).get();
        battle.fire(TriggerPayload.Simple.of(TriggerEvent.TURN_START, lord, lord, 1, 0));
        battle.processRequests();
        Assertions.assertTrue(lord.getAttribute(com.laosun.aluminium.enums.AttributeType.CRIT_ATTACK).get() > before,
                "seven hits are above the threshold, so the crit damage rises this turn");
    }

    /**
     * 星魂 4:「【神君】每段攻击后，景元恢复 2 点能量」 - the energy is paid PER INSTANCE, so N hits pay 2N.
     *
     * <p>This is why the strike states `attacker: "actor"`: rule-fired damage is otherwise credited to the RULE OWNER, and
     * `DEALING_DAMAGE` + `actor is_summon` could never be true.
     */
    @Test
    public void everyLordHitPaysJingYuanTwoEnergy() {
        Character jingYuan = CharacterFactory.create(JING_YUAN, LEVEL, false, null, null, 4);
        Enemy enemy = BattleFixture.monster();
        enemy.setMaxStance(100_000);
        Battle battle = new Battle(List.of(jingYuan), List.of(enemy), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        battle.processRequests();
        Summon lord = battle.summonsOf(jingYuan).getFirst();
        battle.castImmediate(jingYuan.getSkills().get(com.laosun.aluminium.enums.SkillType.SKILL), jingYuan,
                List.of(enemy));
        battle.processRequests();
        int hits = lord.getBuffManager().stacksOf("攻击段数");
        Assertions.assertTrue(hits >= 2, "precondition: hits are stored (" + hits + ")");
        jingYuan.setCurrentEnergy(0);

        battle.fire(TriggerPayload.Simple.of(TriggerEvent.TURN_START, lord, lord, 1, 0));
        battle.processRequests();

        Assertions.assertEquals(2.0 * hits, jingYuan.getCurrentEnergy(), 1e-6,
                "two energy per instance, and the strike settles " + hits + " of them");
    }

    /**
     * 星魂 6:「每段攻击后使目标额外陷入易伤…受到的伤害提高 12%，最多叠加 3 层，持续至本次攻击结束」.
     *
     * <p>The zone accumulates up to the stated cap, so the same strike hits harder at E6 than at E0 - and by less than three
     * full layers, because the first hit lands before any layer exists.
     */
    @Test
    public void theVulnerabilityStacksAmplifyTheRestOfTheStrike() {
        double plain = strikeDamage(0);
        double withEidolon6 = strikeDamage(6);
        double ratio = withEidolon6 / plain;
        System.out.println("[1204] lord strike total: E0=" + plain + " E6=" + withEidolon6 + " ratio=" + ratio);
        Assertions.assertTrue(ratio > 1.12,
                "「每段攻击后…受到的伤害提高 12%，最多叠加 3 层」 — the layers accumulate (" + ratio + ")");
        Assertions.assertTrue(ratio < 1.36, "…and they stop at the stated cap of three (" + ratio + ")");
    }

    /** Total damage the Lord deals with the same stored hit count, at the given eidolon level. */
    private static double strikeDamage(int eidolon) {
        Character jingYuan = CharacterFactory.create(JING_YUAN, LEVEL, false, null, null, eidolon);
        Enemy enemy = BattleFixture.monster();
        enemy.setMaxStance(100_000);
        Battle battle = new Battle(List.of(jingYuan), List.of(enemy), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        battle.processRequests();
        Summon lord = battle.summonsOf(jingYuan).getFirst();
        for (int i = 0; i < 2; i++) {
            battle.castImmediate(jingYuan.getSkills().get(com.laosun.aluminium.enums.SkillType.SKILL), jingYuan,
                    List.of(enemy));
            battle.processRequests();
        }
        Assertions.assertEquals(7, lord.getBuffManager().stacksOf("攻击段数"),
                "precondition: three initial hits plus two Skill casts");
        double before = enemy.getCurrentHp();
        battle.fire(TriggerPayload.Simple.of(TriggerEvent.TURN_START, lord, lord, 1, 0));
        battle.processRequests();
        return before - enemy.getCurrentHp();
    }

    /**
     * 天赋:「每增加 1 段攻击段数，速度提高 10 点」 - ten POINTS per extra hit, measured against the initial three.
     */
    @Test
    public void everyExtraHitAddsTenSpeed() {
        Battle battle = scene();
        Character jingYuan = battle.characters().getFirst();
        Summon lord = battle.summonsOf(jingYuan).getFirst();
        Enemy enemy = (Enemy) battle.enemies().getFirst();
        double base = lord.getAttribute(com.laosun.aluminium.enums.AttributeType.SPEED).get();
        battle.fire(TriggerPayload.Simple.of(TriggerEvent.TURN_START, lord, lord, 1, 0));
        battle.processRequests();
        // The reset rule put it back to three hits, and the speed rule then applied ten times zero extra hits.
        Assertions.assertEquals(3, lord.getBuffManager().stacksOf("攻击段数"), "the talent re-sets three hits");

        for (int i = 0; i < 2; i++) {
            battle.castImmediate(jingYuan.getSkills().get(com.laosun.aluminium.enums.SkillType.SKILL), jingYuan,
                    List.of(enemy));
            battle.processRequests();
        }
        Assertions.assertEquals(7, lord.getBuffManager().stacksOf("攻击段数"), "precondition: seven hits");
        double before = lord.getAttribute(com.laosun.aluminium.enums.AttributeType.SPEED).get();
        battle.fire(TriggerPayload.Simple.of(TriggerEvent.TURN_START, lord, lord, 1, 0));
        battle.processRequests();
        Assertions.assertEquals(before + 40, lord.getAttribute(com.laosun.aluminium.enums.AttributeType.SPEED).get(), 1e-6,
                "four extra hits at ten points each (" + before + " -> base " + base + ")");
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