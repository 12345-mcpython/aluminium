package com.laosun.aluminium.test.content.characters.lingsha;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.Signal;
import com.laosun.aluminium.buff.DotBuff;
import com.laosun.aluminium.buff.StateBuff;
import com.laosun.aluminium.buff.VulnerabilityBuff;
import com.laosun.aluminium.entity.CanHit;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.entity.Summon;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enemy.EnemyFactory;
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
 * 1222 灵砂 (Lingsha): her Skill's and her Ultimate's party heals, the Ultimate's 【醇醉】 mark, and 【浮元】 - the talent's
 * panel-less summon, its action budget, its two strike instances, 星魂 4, 星魂 6's two halves, the 秘技, the Skill's 20%
 * advance and 行迹 遗爇 - plus 星魂 1's defence reduction on a break and 星魂 2's party-wide break effect.
 *
 * <p><b>L3.</b> The single-clause cases that read one board are folded into scenario tests ({@code Assertions.assertAll}),
 * one message per clause reading: 【浮元】's arrival (90 speed, three actions), its five-action cap and the Skill's 20%
 * advance - one fresh board and three Skill casts; its action's cleanse and heal together with the three charges that
 * action spends until 【浮元】 leaves; and 星魂 1's defence reduction with its break-state lifetime beside the E0 control.
 * Every assertion, expected value, tolerance and message is unchanged. The two party heals (the Skill's and the
 * Ultimate's, different rows), the Ultimate's 【醇醉】 mark, the two 星魂 6 halves, 星魂 4, the 秘技 and the trace's
 * trigger gates keep their own tests: each reads a different slot, a different board or a different quantity, and an
 * {@code assertAll} is not where such a reading belongs.
 *
 * <p>This file merges the former {@code LingshaHealTest}, {@code LingshaUltimateTest}, {@code LingshaFuyuanTest},
 * {@code LingshaEidolonBreakEffectTest} and {@code LingsoBreakDefenceTest} (the last one lived in the misspelled
 * {@code lingso1222} folder): every case and every assertion moved unchanged, and each former class note now sits on the
 * case it explains. Nothing collided: {@code LINGSHA} (also spelled {@code LINGSO} there), {@code ALLY} and {@code LEVEL}
 * were declared with the same values (1222, 1001, 80), so they are declared once here, and no helper name, nested class or
 * test method name was used twice.
 */
public class LingshaTest {

    private static final int LINGSHA = 1222;
    private static final int ALLY = 1001;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final int OTHER_MONSTER = 1002012;
    private static final String STATE = "醇醉";
    private static final String ACTIONS = "行动次数";

    // ==================================================================
    // The Skill: 14% of her ATK + 420 to all of our side
    // ==================================================================

    /**
     * 1222 Lingsha (灵砂)'s Skill: "at the same time restore HP equal to 14.00% of Lingsha (灵砂)'s ATK + 420 to all of our side" -- the first clause of her file.
     *
     * <p>Judged as the AMOUNT healed on a wounded teammate, against 14% of her ATK plus 420 (her own Lv10 row), so the assertion checks
     * the scale, the percentage and the flat part together.
     */
    @Test
    public void herSkillHealsThePartyForHerOwnNumbers() {
        Character lingsha = CharacterFactory.create(LINGSHA, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(lingsha, ally), List.of(enemy), BattleFixture.seeded());
        battle.startBattle();
        // Wound the ally first: a heal on a full bar measures nothing.
        ally.takeDamage(ally.getCurrentHp() * 0.5);
        double before = ally.getCurrentHp();
        battle.castImmediate(lingsha.getSkills().get(SkillType.SKILL), lingsha, List.of(enemy));
        double healed = ally.getCurrentHp() - before;
        double expected = 0.14 * lingsha.getAttribute(AttributeType.ATTACK).get() + 420;
        Assertions.assertTrue(healed > 0, "precondition: the skill healed the ally: " + healed);
        Assertions.assertEquals(expected, healed, Math.max(2.0, 0.02 * expected),
                "14% of her ATK (" + lingsha.getAttribute(AttributeType.ATTACK).get() + ") plus 420: " + healed);
    }

    // ==================================================================
    // The Ultimate 「幔亭缭霞」: the second party heal and the 【醇醉】 mark
    // ==================================================================

    /**
     * 1222 Lingsha (灵砂)'s Ultimate "幔亭缭霞" (Pavilion in Rosy Clouds): the [醇醉] break-damage mark on every enemy, and the second party heal.
     *
     * <p>The heal is judged numerically -- 12% of her ATK plus 360, her own Lv10 row. The mark is judged by the CLASS the op attaches:
     * a `MODIFY_DAMAGE_TAKEN` carrying a `buff` name lands as a VulnerabilityBuff, not a StateBuff (`hasState` asks a different
     * registry and answers false), and `AbstractBuff` exposes no name getter, so the count is what can be observed. The NAME is what a
     * `REMOVE_STATE` by name matches, which the interpreter documents; noted rather than asserted.
     *
     * <p>1301's own test already pins what a break-damage increase does to a hit, so this one does not duplicate that measurement.
     */
    @Test
    public void theUltimateHealsThePartyForHerOwnNumbers() {
        Character lingsha = CharacterFactory.create(LINGSHA, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(lingsha, ally), List.of(enemy), BattleFixture.seeded());
        battle.startBattle();
        ally.takeDamage(ally.getCurrentHp() * 0.5);
        double before = ally.getCurrentHp();
        battle.castImmediate(lingsha.getSkills().get(SkillType.ULTRA), lingsha, List.of(enemy));
        double healed = ally.getCurrentHp() - before;
        double expected = 0.12 * lingsha.getAttribute(AttributeType.ATTACK).get() + 360;
        Assertions.assertTrue(healed > 0, "precondition: the ultimate healed the ally: " + healed);
        Assertions.assertEquals(expected, healed, Math.max(2.0, 0.02 * expected),
                "12% of her ATK (" + lingsha.getAttribute(AttributeType.ATTACK).get() + ") plus 360: " + healed);
    }

    @Test
    public void theUltimateMarksEveryEnemy() {
        Character lingsha = CharacterFactory.create(LINGSHA, LEVEL);
        Enemy first = EnemyFactory.create(MONSTER, 90, 1);
        Enemy second = EnemyFactory.create(OTHER_MONSTER, 90, 1);
        Battle battle = new Battle(List.of(lingsha), List.of(first, second), BattleFixture.seeded());
        battle.startBattle();
        battle.castImmediate(lingsha.getSkills().get(SkillType.ULTRA), lingsha, List.of(first));
        for (Enemy enemy : List.of(first, second)) {
            Assertions.assertTrue(enemy.getBuffManager().countBuffs(VulnerabilityBuff.class) >= 1,
                    "the mark from 【" + STATE + "】 on " + enemy.getName());
        }
    }

    // ==================================================================
    // 浮元: the talent's panel-less summon
    // ==================================================================

    /**
     * 1222 灵砂's 【浮元】: her talent's <b>panel-less</b> summon, its action budget, and the Skill's 20% advance.
     *
     * <p>「施放战技时召唤【浮元】，初始拥有 90 点速度以及 3 次行动次数。【浮元】行动时发动追加攻击，对敌方全体造成 75% 攻击力的火伤，
     * 额外对随机敌方单体造成 75% 攻击力的火伤。解除我方全体的 1 个负面效果，并回复 12% 攻击力 + 360 的生命值。【浮元】最多累计 5 次行动次数，
     * 当次数为 0 或灵砂陷入无法战斗状态时【浮元】消失。」
     *
     * <p>It has no skill row (it is not a memosprite), so its action point is its own turn - the same reading 1204's 神君 and 1112's 账账 use.
     * The judge drives that turn directly and measures each half against a control.
     *
     * <p>「【浮元】最多累计 5 次行动次数…【浮元】在场时，施放战技会增加 3 次【浮元】的行动次数」: a second Skill does NOT summon
     * a second 浮元 (the panel-less path is not idempotent, so the rule states `self_summon_count == 0`), and the budget tops up
     * by three, capped at five.
     *
     * <p>「使【浮元】行动提前 20%」 (Skill) - read off the action bar, which is the only place an advance is visible.
     * Control: the summoning cast itself advances nothing, because the unit is placed on the roster but not yet scheduled
     * (`TriggerEvent.SUMMONED`'s own note). The measurement is therefore taken on the SECOND cast, against the same signal.
     */
    @Test
    public void theSkillSummonsFuyuanTopsItsBudgetUpToFiveAndAdvancesIt() {
        Scene scene = new Scene(0, 1);
        Summon beforeTheSkill = scene.battle.summonOf(scene.lingsha);
        scene.castSkill();
        Summon fuyuan = scene.fuyuan();
        String name = fuyuan.getName();
        double speed = fuyuan.getAttribute(AttributeType.SPEED).get();
        int openingActions = fuyuan.getBuffManager().stacksOf(ACTIONS);
        double before = scene.timeRemaining(fuyuan);
        scene.castSkill();
        double after = scene.timeRemaining(fuyuan);
        int copies = scene.battle.summonsOf(scene.lingsha).size();
        int toppedUp = fuyuan.getBuffManager().stacksOf(ACTIONS);
        scene.castSkill();
        int capped = fuyuan.getBuffManager().stacksOf(ACTIONS);

        Assertions.assertAll(
                "「施放战技时召唤【浮元】…初始拥有 90 点速度以及 3 次行动次数」, the five-action cap, and 「使【浮元】行动提前 20%」",
                () -> Assertions.assertNull(beforeTheSkill, "control: she has nothing on the field before the Skill"),
                () -> Assertions.assertEquals("浮元", name, "「施放战技时召唤【浮元】」 - the summon's own name"),
                () -> Assertions.assertEquals(90, speed, 1e-9,
                        "「初始拥有 90 点速度」 - its own speed, not her 98"),
                () -> Assertions.assertEquals(3, openingActions, "「以及 3 次行动次数」"),
                () -> Assertions.assertTrue(before > 0, "precondition: it has time left to skip (" + before + ")"),
                () -> Assertions.assertEquals(before * 0.8, after, 1e-6,
                        "「使【浮元】行动提前 20%」 - a fifth of the remaining wait is skipped (" + before + " -> " + after + ")"),
                () -> Assertions.assertEquals(1, copies,
                        "still ONE 浮元 on the field - 「在场时」, not a second copy"),
                () -> Assertions.assertEquals(5, toppedUp, "3 + 3, and the talent's cap of five takes the rest"),
                () -> Assertions.assertEquals(5, capped, "the cap holds on the third Skill"));
    }

    /**
     * 「每行动一次消耗 1 次；当次数为 0…时【浮元】消失」 and 「解除我方全体的 1 个负面效果，并回复等同于灵砂 12.00% 攻击力 + 360 的生命值」.
     *
     * <p>One board and one summon: its first action cleanses exactly one of the ally's two negative effects, pays the
     * party heal, and spends one of the three charges; the next two actions empty the budget and take 浮元 off the field.
     */
    @Test
    public void itsActionCleansesHealsAndSpendsOneChargeAtATime() {
        Scene scene = new Scene(0, 1);
        scene.castSkill();
        Summon fuyuan = scene.fuyuan();
        Character ally = scene.ally;
        // Two DIFFERENT negative effects: two vulnerability zones of the same scope collapse into one layer (they share a stack group),
        // so the second one would not be a second effect at all. A burn plus a vulnerability are two.
        ally.getBuffManager().addBuff(new DotBuff(scene.enemies.getFirst(), DamageElement.FIRE, 100, 3));
        ally.getBuffManager().addBuff(new VulnerabilityBuff(3, 0.1));
        ally.takeDamage(ally.getCurrentHp() * 0.6);
        double before = ally.getCurrentHp();
        int debuffsBefore = ally.getBuffManager().debuffCount();

        scene.act(fuyuan);
        int afterFirstAction = fuyuan.getBuffManager().stacksOf(ACTIONS);
        int debuffsAfter = ally.getBuffManager().debuffCount();
        double healed = ally.getCurrentHp() - before;
        double expected = 0.12 * scene.lingsha.getAttribute(AttributeType.ATTACK).get() + 360;

        scene.act(fuyuan);
        int afterSecondAction = fuyuan.getBuffManager().stacksOf(ACTIONS);
        scene.act(fuyuan);
        int afterThirdAction = fuyuan.getBuffManager().stacksOf(ACTIONS);
        boolean gone = fuyuan.isDeath();
        boolean offTheField = scene.battle.summonsOf(scene.lingsha).isEmpty();

        Assertions.assertAll(
                "「【浮元】行动时…解除我方全体的 1 个负面效果，并回复 12% 攻击力 + 360」 and 「每行动一次消耗 1 次…当次数为 0…时【浮元】消失」",
                () -> Assertions.assertEquals(2, debuffsBefore, "precondition: the ally carries two negative effects"),
                () -> Assertions.assertEquals(2, afterFirstAction, "action 1 spends one of the three"),
                () -> Assertions.assertEquals(debuffsBefore - 1, debuffsAfter,
                        "「解除我方全体的 1 个负面效果」 - one, and only one"),
                () -> Assertions.assertEquals(expected, healed, Math.max(2.0, 0.02 * expected),
                        "12% of her ATK plus 360, on the summon's action: " + healed),
                () -> Assertions.assertEquals(1, afterSecondAction, "action 2 spends one of the three"),
                () -> Assertions.assertEquals(0, afterThirdAction, "action 3 spends one of the three"),
                () -> Assertions.assertTrue(gone, "「当次数为 0…时【浮元】消失」"),
                () -> Assertions.assertTrue(offTheField, "and it is off the field"));
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

    /**
     * 星魂 6, first half:「【浮元】在场时，使敌方全体全属性抗性降低 20%」 - a LIVE gate on the summon being on the field.
     *
     * <p>Applied when 浮元 enters (`SUMMONED`), kept while it stands (the gate is asked at the enemy's own turn boundary),
     * and taken off once its three actions are spent and it leaves. 星魂 6 is compared against 星魂 5 so the "before" side is
     * the same kit minus this one Eidolon.
     */
    @Test
    public void eidolonSixLowersResistanceOnlyWhileFuyuanStands() {
        Scene scene = new Scene(6, 1);
        scene.castSkill();
        Summon fuyuan = scene.fuyuan();
        Enemy enemy = scene.enemies.getFirst();
        Assertions.assertEquals(0.2, enemy.getAttribute(AttributeType.RESISTANCE_REDUCTION).get(), 1e-9,
                "「【浮元】在场时，使敌方全体全属性抗性降低 20%」");

        enemy.getBuffManager().beforeMove();
        Assertions.assertEquals(0.2, enemy.getAttribute(AttributeType.RESISTANCE_REDUCTION).get(), 1e-9,
                "it is still on the field, so the gate holds");

        for (int action = 0; action < 3; action++) {
            scene.act(fuyuan);
        }
        Assertions.assertTrue(fuyuan.isDeath(), "precondition: the three actions are spent");
        enemy.getBuffManager().beforeMove();
        Assertions.assertEquals(0.0, enemy.getAttribute(AttributeType.RESISTANCE_REDUCTION).get(), 1e-9,
                "「在场时」 - once it is gone, the reduction goes with it");

        Assertions.assertEquals(0.0, resistanceAt(5), 1e-9, "before 星魂 6 there is none at all");
    }

    /**
     * 星魂 6, second half:「【浮元】攻击时，额外造成 4 次伤害，每次伤害对随机敌方单体造成等同于灵砂 50% 攻击力的火属性伤害」.
     *
     * <p>Measured as one action's total on a lone dummy, 星魂 5 against 星魂 6.
     *
     * <p>⚠ The strike's shares are NOT 0.75 here: 星魂 3 is active from E3, and it raises the TALENT by two levels, so the strike
     * reads its Lv12 row (#2 = 0.825) while the Eidolon's 50% is a literal from the Eidolon's own params. A judge that expected
     * 0.75 + 0.75 would therefore "prove" the wrong number (measured: it reads 2.6545, not 2.8).
     *
     * <p>The two halves of 星魂 6 are both in this one action: two strike instances (2 x 0.825) plus four more (4 x 0.5), all of
     * them multiplied by the -20% resistance the same Eidolon applies, so E6/E5 = (1.65 + 2.0)/1.65 x 1.2.
     */
    @Test
    public void eidolonSixAddsFourMoreInstancesOnItsAction() {
        double plain = actionDamage(5);
        double withSix = actionDamage(6);
        double ratio = withSix / plain;
        System.out.println("[1222] fuyuan action damage: E5=" + plain + " E6=" + withSix + " ratio=" + ratio);
        Assertions.assertTrue(plain > 0, "precondition: the strike landed (" + plain + ")");
        Assertions.assertEquals((2 * 0.825 + 4 * 0.5) / (2 * 0.825) * 1.2, ratio, 0.01,
                "「额外造成 4 次伤害，每次 50% 攻击力」 plus the -20% resistance: " + ratio);
    }

    /** The victims' all-type-resistance reduction with a 浮元 on the field, at the given eidolon level. */
    private static double resistanceAt(int eidolon) {
        Scene scene = new Scene(eidolon, 1);
        scene.castSkill();
        return scene.enemies.getFirst().getAttribute(AttributeType.RESISTANCE_REDUCTION).get();
    }

    /** Total HP one 浮元 action takes off a lone dummy, at the given eidolon level. */
    private static double actionDamage(int eidolon) {
        Scene scene = new Scene(eidolon, 1);
        scene.castSkill();
        Enemy enemy = scene.enemies.getFirst();
        double before = enemy.getCurrentHp();
        scene.act(scene.fuyuan());
        return before - enemy.getCurrentHp();
    }

    /**
     * 行迹 遗爇 (1222103):「【浮元】在场时，我方任意角色受到伤害**或**消耗生命值时，若队伍中有存在当前生命值百分比小于等于 60%
     * 的角色，【浮元】会立即对敌人发动天赋的追加攻击，此次触发不消耗【浮元】行动次数，该效果在 2 回合后可再次触发」.
     *
     * <p>Four facts are pinned at once, and each has a state in which it would be silently wrong: the party condition (nobody
     * wounded ⇒ shut), the two events of 「或」 (a hit, and the HP cost), the action budget (an answering strike does NOT spend
     * one of 浮元's three - the budget is only charged at its own turn end), and the cooldown.
     */
    @Test
    public void theTraceAnswersOnlyWhileSomebodyIsWounded() {
        Scene scene = new Scene(0, 1);
        scene.castSkill();
        Summon fuyuan = scene.fuyuan();
        Character ally = scene.ally;
        Enemy enemy = scene.enemies.getFirst();

        double before = enemy.getCurrentHp();
        scene.fire(TriggerEvent.TAKING_HIT, enemy, ally);
        Assertions.assertEquals(before, enemy.getCurrentHp(), 1e-9,
                "nobody is at or below 60%: 「若队伍中有存在当前生命值百分比 ≤60% 的角色」 is false");

        wound(ally);
        Assertions.assertEquals(3, fuyuan.getBuffManager().stacksOf(ACTIONS), "precondition: the budget is untouched");
        before = enemy.getCurrentHp();
        scene.fire(TriggerEvent.TAKING_HIT, enemy, ally);
        Assertions.assertTrue(enemy.getCurrentHp() < before, "「【浮元】会立即对敌人发动天赋的追加攻击」");
        Assertions.assertEquals(3, fuyuan.getBuffManager().stacksOf(ACTIONS),
                "「此次触发不消耗【浮元】行动次数」 - the budget is charged at the summon's own turn end, not here");

        before = enemy.getCurrentHp();
        scene.fire(TriggerEvent.TAKING_HIT, enemy, ally);
        Assertions.assertEquals(before, enemy.getCurrentHp(), 1e-9,
                "「该效果在 2 回合后可再次触发」 - the very next hit is refused");

        // The OTHER half of 「或」 has its own event and therefore its own cooldown: paying HP still answers.
        before = enemy.getCurrentHp();
        scene.fireHpConsumed(ally, 500);
        Assertions.assertTrue(enemy.getCurrentHp() < before, "「…或消耗生命值时」 - the HP cost answers too");

        // Two of HER turns later the damage half is armed again.
        scene.lingsha.tickTriggerCooldowns();
        scene.lingsha.tickTriggerCooldowns();
        before = enemy.getCurrentHp();
        scene.fire(TriggerEvent.TAKING_HIT, enemy, ally);
        Assertions.assertTrue(enemy.getCurrentHp() < before, "「2 回合后可再次触发」 - the cooldown has run out");
    }

    /**
     * 天赋:「本次伤害**优先选择**韧性值大于0且有火属性弱点的目标」 - the random instance follows the WEAKNESS.
     *
     * <p>Two identical dummies, one of them fire-weak; the area instance hits both, the random one must land on the weak one. The
     * mirror case (the weak one on the other side) is what a roster-order bias could not pass: a rule that simply picked the
     * first enemy would satisfy one arrangement and fail the other.
     */
    @Test
    public void theExtraInstancePrefersAFireWeakTargetWithToughness() {
        double[] leftWeak = oneActionSplit(true);
        System.out.println("[1222] preferred draw (left weak): " + leftWeak[0] + " / " + leftWeak[1]);
        Assertions.assertEquals(2.0, leftWeak[0] / leftWeak[1], 0.01,
                "「额外对随机敌方单体…」 - the second instance lands on the fire-weak enemy, so it loses twice the other's share");

        double[] rightWeak = oneActionSplit(false);
        System.out.println("[1222] preferred draw (right weak): " + rightWeak[0] + " / " + rightWeak[1]);
        Assertions.assertEquals(2.0, rightWeak[1] / rightWeak[0], 0.01,
                "…and it follows the weakness, not the roster order (the mirrored arrangement)");
    }

    /**
     * One 浮元 action against two identical dummies, exactly one of them fire-weak and both with a toughness bar.
     *
     * @return each one's HP loss, left first
     */
    private static double[] oneActionSplit(boolean leftIsWeak) {
        Character lingsha = CharacterFactory.create(LINGSHA, LEVEL);
        Enemy left = BattleFixture.dummy();
        Enemy right = BattleFixture.dummy();
        for (Enemy each : List.of(left, right)) {
            // 「韧性值大于0」: the bar has to be there AND be full - `setMaxStance` alone leaves it empty (0 / max = 0).
            each.setMaxStance(10_000);
            each.setStance(10_000);
        }
        (leftIsWeak ? left : right).addWeakness(DamageElement.FIRE);
        Battle battle = new Battle(new ArrayList<>(List.of(lingsha)), new ArrayList<>(List.of(left, right)),
                BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        battle.processRequests();
        battle.castImmediate(lingsha.getSkills().get(SkillType.SKILL), lingsha, List.of(left));
        battle.processRequests();
        Summon fuyuan = battle.summonsOf(lingsha).getFirst();

        double leftBefore = left.getCurrentHp();
        double rightBefore = right.getCurrentHp();
        battle.fire(TriggerPayload.Simple.of(TriggerEvent.TURN_START, fuyuan, fuyuan, 1, 0));
        battle.processRequests();
        return new double[]{leftBefore - left.getCurrentHp(), rightBefore - right.getCurrentHp()};
    }

    /** Drive a unit below 60% HP without killing it. */
    private static void wound(Character unit) {
        while (unit.getCurrentHp() / unit.getMaxHp() > 0.6) {
            unit.takeDamage(unit.getCurrentHp() * 0.3);
        }
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
                lingsha.getBuffManager().addBuff(new StateBuff("sys:technique", 1));
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

        /** One event, announced the way the engine announces it (actor, target, hit count, amount). */
        private void fire(TriggerEvent event, CanHit actor, CanHit target) {
            battle.fire(TriggerPayload.Simple.of(event, actor, target, 1, 0));
            battle.processRequests();
        }

        private void fireHpConsumed(CanHit payer, double amount) {
            battle.fire(TriggerPayload.Simple.of(TriggerEvent.HP_CONSUMED, payer, payer, 0, amount));
            battle.processRequests();
        }
    }

    // ==================================================================
    // 星魂 1, second half: a weakness break lowers the target's defence
    // ==================================================================

    /**
     * 1222 灵砂's Eidolon 1, second half: 「当有敌方单位的弱点被击破时，使其防御力降低 20%」 - and the E0 control beside it.
     *
     * <p>A weakness break lowers the broken enemy's DEFENCE by 20%, and the reduction leaves with the break. At E0 the same
     * break changes nothing.
     *
     * <p><b>Why the lifetime is the interesting half.</b> The document and the parameter row state no duration, so `turns`
     * would be invented and `permanent` would be plainly wrong. The clause is tied to the break STATE instead
     * ({@code while_holder_state: 弱点击破}, the engine's own {@code BuffManager.BROKEN_STATE}): the reduction lives exactly as
     * long as the target is broken, and it needs no number at all.
     */
    @Test
    public void aBreakLowersTheDefenceAndOnlyWithTheFirstEidolon() {
        Run withEidolon = new Run(1);
        double bare = withEidolon.enemy.getAttribute(AttributeType.DEFENCE).get();

        withEidolon.breakTheEnemy();
        double broken = withEidolon.enemy.getAttribute(AttributeType.DEFENCE).get();

        withEidolon.enemy.getBuffManager().removeState(Run.BROKEN);
        withEidolon.battle.setCurrentMove(new Signal(withEidolon.enemy));
        withEidolon.battle.beforeMove();
        withEidolon.battle.processRequests();
        double recovered = withEidolon.enemy.getAttribute(AttributeType.DEFENCE).get();

        Run control = new Run(0);
        double bareWithoutIt = control.enemy.getAttribute(AttributeType.DEFENCE).get();
        control.breakTheEnemy();
        double brokenWithoutIt = control.enemy.getAttribute(AttributeType.DEFENCE).get();

        Assertions.assertAll(
                "星魂 1 的后半句: 「当有敌方单位的弱点被击破时，使其防御力降低 20%」, its lifetime, and the E0 control",
                () -> Assertions.assertEquals(0.2 * bare, bare - broken, 1e-6,
                        "「使其防御力降低 20%」 - 20% of the base DEF"),
                () -> Assertions.assertEquals(bare, recovered, 1e-6,
                        "the gate is `while_holder_state: 弱点击破`, so recovering takes the reduction with it"),
                () -> Assertions.assertEquals(bareWithoutIt, brokenWithoutIt, 1e-6, "E0 states no such reduction"));
    }

    private static final class Run {
        static final String BROKEN = "break:weakness";
        private final Character lingsha;
        private final Enemy enemy;
        private final Battle battle;
        private final double enemyBareDefence;

        Run(int eidolon) {
            lingsha = CharacterFactory.create(LINGSHA, LEVEL);
            lingsha.setEidolonRank(eidolon);
            enemy = BattleFixture.monster();
            enemyBareDefence = enemy.getAttribute(AttributeType.DEFENCE).baseValue();
            battle = new Battle(List.of(lingsha), List.of(enemy), BattleFixture.seeded());
            battle.startBattle();
            battle.processRequests();
        }

        /** The break itself: the engine announces BREAK with the attacker and the broken enemy. */
        void breakTheEnemy() {
            enemy.getBuffManager().addBuff(new StateBuff(BROKEN, 2));
            battle.fire(TriggerPayload.Simple.of(TriggerEvent.BREAK, lingsha, enemy, 0, 0));
            battle.processRequests();
        }
    }

    // ==================================================================
    // 星魂 2: the Ultimate raises the whole party's break effect
    // ==================================================================

    /**
     * 1222 Lingsha (灵砂)'s Eidolon (星魂) 2: "施放终结技后,使我方全体击破特攻提高40%,持续3回合" (after casting the ultimate, all of our side's break effect is raised by 40% for 3 turns).
     * <p>Judged on a TEAMMATE ("我方全体", all of our side) as an ABSOLUTE +0.40, because BREAKING_EFFECT is a ratio attribute and a share lands as points on those (the
     * flat/ratio distinction).
     */
    @Test
    public void theUltimateRaisesThePartysBreakEffect() {
        Character lingsha = CharacterFactory.create(LINGSHA, LEVEL);
        lingsha.setEidolonRank(2);   // 星魂 gated: the rule carries min_eidolon
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(lingsha, ally), List.of(enemy), BattleFixture.seeded());
        battle.startBattle();
        double before = ally.getAttribute(AttributeType.BREAKING_EFFECT).get();
        battle.castImmediate(lingsha.getSkills().get(SkillType.ULTRA), lingsha, List.of(enemy));
        double after = ally.getAttribute(AttributeType.BREAKING_EFFECT).get();
        Assertions.assertEquals(0.40, after - before, 1e-6,
                "forty points of break effect on the teammate: " + before + " -> " + after);
    }
}
