package com.laosun.aluminium.test.content.characters.lingsha;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.Signal;
import com.laosun.aluminium.buff.DotBuff;
import com.laosun.aluminium.buff.StateBuff;
import com.laosun.aluminium.buff.VulnerabilityBuff;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.entity.Summon;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.trigger.TriggerPayload;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 1222 灵砂's 【浮元】: her talent's <b>panel-less</b> summon.
 *
 * <p>「施放战技时召唤【浮元】，初始拥有 90 点速度以及 3 次行动次数。【浮元】行动时发动追加攻击，对敌方全体造成 75% 攻击力的火伤，
 * 额外对随机敌方单体造成 75% 攻击力的火伤。解除我方全体的 1 个负面效果，并回复 12% 攻击力 + 360 的生命值。【浮元】最多累计 5 次行动次数，
 * 当次数为 0 或灵砂陷入无法战斗状态时【浮元】消失。」
 *
 * <p>It has no skill row (it is not a memosprite), so its action point is its own turn - the same reading 1204's 神君 and 1112's 账账 use.
 * The judge drives that turn directly and measures each half against a control.
 */
public class LingshaFuyuanTest {
    private static final int LINGSHA = 1222;
    private static final int ALLY = 1001;
    private static final int LEVEL = 80;
    private static final String ACTIONS = "行动次数";

    @Test
    public void theSkillSummonsFuyuanAtNinetySpeedWithThreeActions() {
        Scene scene = new Scene(0, 1);
        Assertions.assertNull(scene.battle.summonOf(scene.lingsha),
                "control: she has nothing on the field before the Skill");
        scene.castSkill();
        Summon fuyuan = scene.fuyuan();
        Assertions.assertEquals("浮元", fuyuan.getName());
        Assertions.assertEquals(90, fuyuan.getAttribute(AttributeType.SPEED).get(), 1e-9,
                "「初始拥有 90 点速度」 - its own speed, not her 98");
        Assertions.assertEquals(3, fuyuan.getBuffManager().stacksOf(ACTIONS), "「以及 3 次行动次数」");
    }

    /**
     * 「【浮元】最多累计 5 次行动次数…【浮元】在场时，施放战技会增加 3 次【浮元】的行动次数」.
     *
     * <p>Two things at once: a second Skill does NOT summon a second 浮元 (the panel-less path is not idempotent, so the rule states
     * `self_summon_count == 0`), and the budget tops up by three, capped at five.
     */
    @Test
    public void aSecondSkillTopsTheBudgetUpToFive() {
        Scene scene = new Scene(0, 1);
        scene.castSkill();
        Summon fuyuan = scene.fuyuan();
        scene.castSkill();
        Assertions.assertEquals(1, scene.battle.summonsOf(scene.lingsha).size(),
                "still ONE 浮元 on the field - 「在场时」, not a second copy");
        Assertions.assertEquals(5, fuyuan.getBuffManager().stacksOf(ACTIONS),
                "3 + 3, and the talent's cap of five takes the rest");
        scene.castSkill();
        Assertions.assertEquals(5, fuyuan.getBuffManager().stacksOf(ACTIONS), "the cap holds on the third Skill");
    }

    /** 「每行动一次消耗 1 次；当次数为 0…时【浮元】消失」. */
    @Test
    public void everyActionSpendsOneAndTheThirdOneSendsItAway() {
        Scene scene = new Scene(0, 1);
        scene.castSkill();
        Summon fuyuan = scene.fuyuan();
        for (int action = 1; action <= 3; action++) {
            scene.act(fuyuan);
            Assertions.assertEquals(3 - action, fuyuan.getBuffManager().stacksOf(ACTIONS),
                    "action " + action + " spends one of the three");
        }
        Assertions.assertTrue(fuyuan.isDeath(), "「当次数为 0…时【浮元】消失」");
        Assertions.assertTrue(scene.battle.summonsOf(scene.lingsha).isEmpty(), "and it is off the field");
    }

    /**
     * 「对敌方全体造成…75% 攻击力的火属性伤害。额外对随机敌方单体造成…75% 攻击力的火属性伤害」.
     *
     * <p>Two equal instances, so one enemy takes twice what the other takes. A single all-enemy instance would leave the two equal -
     * which is exactly the confusion this pins. Both dummies have the same level, defence and 1,000,000 HP, and the roll is pinned to
     * always crit, so the ratio is a shape and not a coincidence of the draw.
     */
    @Test
    public void itsActionHitsEveryoneAndOneOfThemTwice() {
        Scene scene = new Scene(0, 2);
        scene.castSkill();
        Enemy first = scene.enemies.get(0);
        Enemy second = scene.enemies.get(1);
        double firstBefore = first.getCurrentHp();
        double secondBefore = second.getCurrentHp();
        scene.act(scene.fuyuan());
        double firstLost = firstBefore - first.getCurrentHp();
        double secondLost = secondBefore - second.getCurrentHp();
        double big = Math.max(firstLost, secondLost);
        double small = Math.min(firstLost, secondLost);
        System.out.println("[1222] fuyuan strike: " + firstLost + " / " + secondLost);
        Assertions.assertTrue(small > 0, "「对敌方全体造成…火属性伤害」 - both of them take the first instance");
        Assertions.assertEquals(2.0, big / small, 0.01,
                "「额外对随机敌方单体造成 75%…」 - one of them takes a second, equal instance");
    }

    /** 「解除我方全体的 1 个负面效果，并回复等同于灵砂 12.00% 攻击力 + 360 的生命值」. */
    @Test
    public void itsActionCleansesOneDebuffAndHealsTheParty() {
        Scene scene = new Scene(0, 1);
        scene.castSkill();
        Character ally = scene.ally;
        // Two DIFFERENT negative effects: two vulnerability zones of the same scope collapse into one layer (they share a stack group),
        // so the second one would not be a second effect at all. A burn plus a vulnerability are two.
        ally.getBuffManager().addBuff(new DotBuff(scene.enemies.getFirst(), DamageElement.FIRE, 100, 3));
        ally.getBuffManager().addBuff(new VulnerabilityBuff(3, 0.1));
        ally.takeDamage(ally.getCurrentHp() * 0.6);
        double before = ally.getCurrentHp();
        int debuffsBefore = ally.getBuffManager().debuffCount();
        Assertions.assertEquals(2, debuffsBefore, "precondition: the ally carries two negative effects");

        scene.act(scene.fuyuan());

        Assertions.assertEquals(debuffsBefore - 1, ally.getBuffManager().debuffCount(),
                "「解除我方全体的 1 个负面效果」 - one, and only one");
        double healed = ally.getCurrentHp() - before;
        double expected = 0.12 * scene.lingsha.getAttribute(AttributeType.ATTACK).get() + 360;
        Assertions.assertEquals(expected, healed, Math.max(2.0, 0.02 * expected),
                "12% of her ATK plus 360, on the summon's action: " + healed);
    }

    /**
     * 星魂 4:「【浮元】行动时，为当前生命值最低的我方目标回复等同于灵砂 40% 攻击力的生命值」.
     *
     * <p>The control is <b>E3, not E0</b>: 星魂 3 raises the TALENT by two levels, and the talent's own party heal
     * (12% / 360 at Lv10) reads those rows - at E0 it is 0.12 and 360, at E3+ it is 0.128 and 400.5. Comparing E0 with E4
     * would therefore charge that level difference to this Eidolon (measured: 345.3 instead of 298.8). E3 and E4 share the
     * talent level, so the difference between them is exactly this heal.
     *
     * <p>灵砂 fights alone on purpose: the "weakest ally" is then unambiguous, and her bar is deep enough that neither heal
     * is capped by the max-HP ceiling (a capped heal would look like a smaller Eidolon).
     */
    @Test
    public void eidolonFourMendsTheWeakestOnItsAction() {
        double plain = healOnItsAction(3);
        double withEidolon4 = healOnItsAction(4);
        double extra = withEidolon4 - plain;
        double expected = 0.4 * CharacterFactory.create(LINGSHA, LEVEL).getAttribute(AttributeType.ATTACK).get();
        System.out.println("[1222] heal on fuyuan's action: E3=" + plain + " E4=" + withEidolon4 + " extra=" + extra);
        Assertions.assertTrue(extra > 0, "「为当前生命值最低的我方目标回复…」 - the Eidolon adds a heal");
        Assertions.assertEquals(expected, extra, Math.max(2.0, 0.02 * expected),
                "40% of her ATK for the weakest ally: " + extra);
    }

    /**
     * 「使【浮元】行动提前 20%」 (Skill) - read off the action bar, which is the only place an advance is visible.
     *
     * <p>Control: the summoning cast itself advances nothing, because the unit is placed on the roster but not yet scheduled
     * (`TriggerEvent.SUMMONED`'s own note). The measurement is therefore taken on the SECOND cast, against the same signal.
     */
    @Test
    public void theSkillAdvancesItByAFifth() {
        Scene scene = new Scene(0, 1);
        scene.castSkill();
        Summon fuyuan = scene.fuyuan();
        double before = scene.timeRemaining(fuyuan);
        Assertions.assertTrue(before > 0, "precondition: it has time left to skip (" + before + ")");
        scene.castSkill();
        double after = scene.timeRemaining(fuyuan);
        Assertions.assertEquals(before * 0.8, after, 1e-6,
                "「使【浮元】行动提前 20%」 - a fifth of the remaining wait is skipped (" + before + " -> " + after + ")");
    }

    /**
     * 秘技:「使用秘技后，下一次战斗开始时立即召唤【浮元】，并使敌方全体陷入【醇醉】状态，持续 2 回合」.
     *
     * <p>The same fight without the technique is the control: no summon, no mark.
     */
    @Test
    public void theTechniqueSummonsItAndBefogs() {
        Scene with = new Scene(0, 1, true);
        Scene without = new Scene(0, 1, false);
        Assertions.assertNotNull(with.battle.summonOf(with.lingsha), "「立即召唤【浮元】」");
        Assertions.assertEquals(3, with.fuyuan().getBuffManager().stacksOf(ACTIONS), "it enters with its three actions");
        Assertions.assertTrue(with.enemies.getFirst().getBuffManager().countBuffs(VulnerabilityBuff.class) >= 1,
                "「使敌方全体陷入【醇醉】状态」");
        Assertions.assertNull(without.battle.summonOf(without.lingsha), "control: no technique, no 浮元");
        Assertions.assertEquals(0, without.enemies.getFirst().getBuffManager().countBuffs(VulnerabilityBuff.class),
                "control: no technique, no 【醇醉】");
    }

    /** What one 浮元 action heals the weakest ally at the given eidolon level. */
    private static double healOnItsAction(int eidolon) {
        Character lingsha = CharacterFactory.create(LINGSHA, LEVEL, false, null, null, eidolon);
        Enemy enemy = BattleFixture.dummy();
        Battle battle = new Battle(new ArrayList<>(List.of(lingsha)), new ArrayList<>(List.of(enemy)),
                BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        battle.processRequests();
        battle.castImmediate(lingsha.getSkills().get(SkillType.SKILL), lingsha, List.of(enemy));
        battle.processRequests();
        Summon fuyuan = battle.summonsOf(lingsha).getFirst();
        // Down to about a third: deep enough that the party heal plus the Eidolon's share still fits under her max HP.
        lingsha.takeDamage(lingsha.getCurrentHp() * 0.5);
        lingsha.takeDamage(lingsha.getCurrentHp() * 0.4);
        double headroom = lingsha.getMaxHp() - lingsha.getCurrentHp();
        Assertions.assertTrue(headroom > 900, "precondition: room for both heals, not a capped one (" + headroom + ")");
        double before = lingsha.getCurrentHp();
        battle.fire(TriggerPayload.Simple.of(TriggerEvent.TURN_START, fuyuan, fuyuan, 1, 0));
        battle.processRequests();
        battle.endTurnOf(fuyuan);
        battle.processRequests();
        return lingsha.getCurrentHp() - before;
    }

    // ==================================================================

    private static final class Scene {
        private final Character lingsha;
        private final Character ally;
        private final List<Enemy> enemies = new ArrayList<>();
        private final Battle battle;

        private Scene(int eidolon, int enemyCount) {
            this(eidolon, enemyCount, false);
        }

        private Scene(int eidolon, int enemyCount, boolean technique) {
            lingsha = CharacterFactory.create(LINGSHA, LEVEL, false, null, null, eidolon);
            ally = CharacterFactory.create(ALLY, LEVEL);
            if (technique) {
                // The engine stamps this before any BATTLE_START rule runs; it is the gate her technique rule reads.
                lingsha.getBuffManager().addBuff(new StateBuff("秘技", 1));
            }
            // Dummies survive every hit (1,000,000 HP), so a routine opening cannot decide the measurement.
            for (int i = 0; i < enemyCount; i++) {
                enemies.add(BattleFixture.dummy());
            }
            battle = new Battle(new ArrayList<>(List.of(lingsha, ally)), new ArrayList<>(enemies),
                    BattleFixture.alwaysRoll(0.0));
            battle.startBattle();
            battle.processRequests();
        }

        private void castSkill() {
            battle.castImmediate(lingsha.getSkills().get(SkillType.SKILL), lingsha, List.copyOf(enemies));
            battle.processRequests();
        }

        private Summon fuyuan() {
            return battle.summonsOf(lingsha).getFirst();
        }

        /** The summon's turn: its action point is the turn start, and the budget is spent when the turn ends. */
        private void act(Summon fuyuan) {
            battle.fire(TriggerPayload.Simple.of(TriggerEvent.TURN_START, fuyuan, fuyuan, 1, 0));
            battle.processRequests();
            battle.endTurnOf(fuyuan);
            battle.processRequests();
        }

        private double timeRemaining(Summon fuyuan) {
            for (Signal signal : battle.queue().snapshot()) {
                if (signal.getCanHit() == fuyuan) {
                    return battle.queue().getTimeRemaining(signal);
                }
            }
            throw new IllegalStateException("浮元 is not in the action order");
        }
    }
}
