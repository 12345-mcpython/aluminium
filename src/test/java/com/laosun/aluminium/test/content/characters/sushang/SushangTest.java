package com.laosun.aluminium.test.content.characters.sushang;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.buff.ReductionBuff;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.data.TriggerTables;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.skill.SkillExecutor;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.trigger.TriggerPayload;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 1206 Sushang (素裳): everything this character folder asserts, in one class.
 *
 * <p><b>Why one class.</b> This evidence used to live in six files - the talent's weakness-break clause, the
 * clause-coverage audit, the six Eidolons, the ultimate's 50% extra roll, the three level rows, and the ultimate/trace
 * pair. Nothing about the evidence changed when they merged: every case and every assertion is preserved verbatim; only
 * the class name, the duplicated constants, and the four private Fixtures were unified.
 *
 * <p><b>The talent is the interesting one.</b> It hangs on the engine's `BREAK` event, which nothing had used yet. Its
 * observable is her own SPEED, so no damage comparison (and no level-convention confound) is involved.
 *
 * <p><b>Clause coverage.</b> The ledger had already recorded every clause of {@code 1206_素裳.md} as done
 * ("仍登记：无") while the character file's {@code _gaps} still said 「见 CONTENT-STATUS.md 的逐角色登记」 - a
 * pointer, not a gap. The two audit cases close it: one line per section, and the two Eidolon readings that no other
 * case covered.
 *
 * <p><b>The Eidolons.</b> Each is reduced to the observable its sentence names. Two are about a CAP or a COUNT rather
 * than a number, and those are the ones worth writing: 「最多叠加2层」 is a ceiling, so one firing beyond it must
 * change nothing; 「立即获得1层」 is an initial condition, so rank 6 must start higher than rank 0 before any enemy
 * breaks at all.
 *
 * <p><b>The ultimate's rider.</b> 「通过额外判定发动的【剑势】伤害为<b>原伤害的 50%</b>」, where 「原伤害」 is the SKILL
 * ROW's 剑势 share, not 100% ATK: a literal {@code percent: 0.5} silently under-pays by 9% from E5 on, so the fix is
 * {@code percent: 0.5} <b>beside</b> {@code percent_from_skill_param: "SKILL:1"}. Both cases read a RATIO between two
 * Eidolon ranks, per instance, because the absolute number also depends on ATK, on the ultimate's own ATTACK buff and
 * on the trace that adds 2.5% per accumulated 剑势 - dividing each run's instance by that run's OWN attack power
 * cancels all of it.
 *
 * <p><b>The level rows.</b> Three numbers the file used to write as literals now follow the skill row, and each is
 * pinned at BOTH ends of its level - the only way to tell a row read from a literal. Her file's
 * {@code level_convention} is 「语料页引用 Lv10」 (+9), and her Eidolons raise the slots on top of it: E3 adds +2 to
 * ULTRA and TALENT, E5 adds +2 to SKILL.
 *
 * <p><b>The ultimate and the trace.</b> Each case drives the rule through the engine and asserts the <b>observable</b>
 * the clause names - an extra turn, a raised ATTACK, a stack count - rather than re-reading the JSON, because
 * "the rule is in the file" is not evidence that the clause does anything.
 */
public class SushangTest {
    private static final int SUSHANG = 1206;
    private static final int LEVEL = 80;

    /** The ally of the clause-coverage cases (1002). */
    private static final int ALLY_1002 = 1002;

    /** The ally of the Eidolon cases and of the ultimate/trace cases (1201). */
    private static final int ALLY_1201 = 1201;

    /** The ally of the extra-roll and level-row cases (1204). */
    private static final int ALLY_1204 = 1204;

    // ==================================================================
    // 1. The talent: a weakness break raises her SPEED.
    // ==================================================================

    /**
     * Note: "当场上有敌方目标的弱点被击破": the BREAK event, which fires with the broken unit as its target.
     */
    @Test
    public void aWeaknessBreakRaisesHerSpeed() {
        SingleBreakFixture f = new SingleBreakFixture();
        double before = f.sushang.getAttribute(AttributeType.SPEED).get();

        f.battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.BREAK, f.sushang, f.enemy, 0, 0));

        Assertions.assertTrue(f.sushang.getAttribute(AttributeType.SPEED).get() > before,
                "\"when an enemy target's Weakness is Broken on the field, Sushang's SPD is raised by 20% for 2 turns\" (「当场上有敌方目标的弱点被击破，素裳的速度提高20%，持续2回合」): "
                        + before + " -> " + f.sushang.getAttribute(AttributeType.SPEED).get());
    }

    private static final class SingleBreakFixture {
        private final Character sushang = CharacterFactory.create(SUSHANG, LEVEL);
        private final Enemy enemy = BattleFixture.monster();
        private final Battle battle = new Battle(List.of(sushang), List.of(enemy), new Random() {
            @Override
            public double nextDouble() {
                return 0.0;
            }
        });

        private SingleBreakFixture() {
            battle.startBattle();
        }
    }

    // ==================================================================
    // 2. Clause coverage: the two Eidolon readings no other case pinned.
    // ==================================================================

    /**
     * 星魂 4: 「素裳的击破特攻提高 40%」. Measured: BREAKING_EFFECT <b>0</b> at E0, <b>0.4</b> at E4.
     */
    @Test
    public void eidolon4RaisesTheBreakingEffect() {
        Assertions.assertEquals(0.0, breakingEffectOf(0), 1e-9, "control: nothing at E0");
        Assertions.assertEquals(0.4, breakingEffectOf(4), 1e-9, "「击破特攻提高 40%」");
    }

    /**
     * 星魂 6: 「天赋的加速效果可以叠加，最多叠加2层」 — the same two breaks move the speed at E6 and only refresh at E0.
     *
     * <p>Measured over two breaks in one battle: E0 <b>107 → 128.4 → 128.4</b> (the second refreshes); E6
     * <b>129.47 → 151.94 → 174.41</b> (it stacks). The point pinned here is the <b>difference between the two Eidolons</b>, not
     * the three numbers: one stack versus two.
     */
    @Test
    public void eidolon6LetsTheSpeedStackTwice() {
        double[] e0 = speedsAfterTwoBreaks(0);
        Assertions.assertEquals(e0[1], e0[2], 1e-6, "E0: 「最多叠加2层」 does not apply — the second break only refreshes");

        double[] e6 = speedsAfterTwoBreaks(6);
        Assertions.assertTrue(e6[2] > e6[1], "E6: the second break stacks (" + e6[1] + " -> " + e6[2] + ")");
        Assertions.assertEquals(e6[1] + (e6[1] - e6[0]), e6[2], 1e-6,
                "and it stacks by the SAME amount as the first one");
    }

    private static double breakingEffectOf(int eidolon) {
        return fixture(eidolon).sushang.getAttribute(AttributeType.BREAKING_EFFECT).get();
    }

    /**
     * Speeds at [before any break, after one, after two].
     */
    private static double[] speedsAfterTwoBreaks(int eidolon) {
        ClauseFixture f = fixture(eidolon);
        double before = f.sushang.getAttribute(AttributeType.SPEED).get();
        f.breakOnce();
        double one = f.sushang.getAttribute(AttributeType.SPEED).get();
        f.breakOnce();
        double two = f.sushang.getAttribute(AttributeType.SPEED).get();
        return new double[]{before, one, two};
    }

    private static ClauseFixture fixture(int eidolon) {
        return new ClauseFixture(eidolon);
    }

    private static final class ClauseFixture {
        private final Character sushang;
        private final Enemy enemy = BattleFixture.enemy(BattleFixture.MONSTER_ID, BattleFixture.MONSTER_LEVEL);
        private final Battle battle;

        private ClauseFixture(int eidolon) {
            sushang = CharacterFactory.create(SUSHANG, 80, false, null, null, eidolon);
            battle = new Battle(List.of(sushang, CharacterFactory.create(ALLY_1002, 80)), List.of(enemy),
                    BattleFixture.seeded());
            battle.startBattle();
            battle.processRequests();
        }

        private void breakOnce() {
            battle.fire(TriggerPayload.Simple.of(TriggerEvent.BREAK, sushang, enemy, 0, 0));
            battle.processRequests();
        }
    }

    // ==================================================================
    // 3. The six Eidolons, each on the observable its sentence names.
    // ==================================================================

    /**
     * 星魂 1 「对陷入弱点击破状态的敌方目标施放战技后，恢复1个战技点」.
     *
     * <p>⚠ Run in BOTH states: the same cast must pay a point against a broken enemy and nothing against a healthy
     * one. Asserting only the paying half would pass for a rule that forgot its condition. And the break is driven
     * through the engine's own toughness path (two Skill casts empty the dummy's bar), not fired as an event - the
     * condition reads a STATE, so only a real break can put it there.
     */
    @Test
    public void eidolon1PaysASkillPointOnlyAgainstABrokenTarget() {
        EidolonFixture healthy = new EidolonFixture(1, BattleFixture.monster());
        int before = healthy.battle.getSkillPoints();
        healthy.castSkill();
        Assertions.assertEquals(before, healthy.battle.getSkillPoints(),
                "「对陷入弱点击破状态的敌方目标…」 - a healthy target pays nothing");

        EidolonFixture breaking = new EidolonFixture(1, BattleFixture.dummy());
        Enemy target = (Enemy) breaking.battle.enemies().getFirst();
        Assertions.assertFalse(target.isBroken(), "precondition: not broken yet");
        // ⚠ The broken state is ENTERED directly through the enemy's own entry point rather than by emptying a
        // toughness bar: `BattleFixture.dummy()` is built by `fromAttributes` and has no bar at all, and which
        // element a target is weak to decides whether a hit reduces toughness - neither is what this eidolon is
        // about. `breakEnemy` is the same call the engine's break path makes, so the state is the real one.
        target.breakEnemy(com.laosun.aluminium.enums.DamageElement.PHYSICAL);
        Assertions.assertTrue(target.isBroken(),
                "precondition: the target is in the broken state");

        int beforeBroken = breaking.battle.getSkillPoints();
        breaking.castSkill();
        Assertions.assertEquals(beforeBroken + 1, breaking.battle.getSkillPoints(),
                "「…施放战技后，恢复1个战技点」 - exactly one point once it is broken");
    }

    /**
     * 星魂 2 「触发【剑势】后，素裳受到的伤害降低20%，持续1回合」.
     *
     * <p>⚠ Counted through {@code ReductionBuff}, the class the engine lands a negative {@code MODIFY_DAMAGE_TAKEN}
     * as. Asserted against rank 0 rather than against a boolean, so a rule that fired on every hit would still be
     * caught by the baseline it is compared with.
     */
    @Test
    public void eidolon2LowersTheDamageSheTakesAfterASwordStance() {
        EidolonFixture base = new EidolonFixture(0, BattleFixture.monster());
        base.castSkill();
        int plain = reductions(base.sushang);

        EidolonFixture withEidolon = new EidolonFixture(2, BattleFixture.monster());
        withEidolon.castSkill();
        int reduced = reductions(withEidolon.sushang);

        Assertions.assertTrue(reduced > plain,
                "「触发【剑势】后…受到的伤害降低20%」 must land a reduction: rank 0 = " + plain
                        + ", rank 2 = " + reduced);
    }

    private static int reductions(Character unit) {
        return unit.getBuffManager().allBuffsOf(ReductionBuff.class).size();
    }

    /**
     * 星魂 3 / 5: the two level statements, each on the skill it names.
     *
     * <p>⚠ Compared against rank 0 rather than against a literal: the file already raises everything to Lv10 by
     * its level convention, so an absolute number here would restate that choice and rot when it changed.
     */
    @Test
    public void eidolon3And5RaiseTheLevelsTheyName() {
        EidolonFixture base = new EidolonFixture(0, BattleFixture.monster());
        EidolonFixture atThree = new EidolonFixture(3, BattleFixture.monster());
        EidolonFixture atFive = new EidolonFixture(5, BattleFixture.monster());

        Assertions.assertEquals(base.sushang.skillLevelBonus(SkillType.ULTRA) + 2,
                atThree.sushang.skillLevelBonus(SkillType.ULTRA), "星魂3 「终结技等级+2」");
        Assertions.assertEquals(base.sushang.skillLevelBonus(SkillType.TALENT) + 2,
                atThree.sushang.skillLevelBonus(SkillType.TALENT), "星魂3 「天赋等级+2」");

        Assertions.assertEquals(base.sushang.skillLevelBonus(SkillType.SKILL) + 2,
                atFive.sushang.skillLevelBonus(SkillType.SKILL), "星魂5 「战技等级+2」");
        Assertions.assertEquals(base.sushang.skillLevelBonus(SkillType.COMMON) + 1,
                atFive.sushang.skillLevelBonus(SkillType.COMMON), "星魂5 「普攻等级+1」");
    }

    /**
     * 星魂 4 「素裳的击破特攻提高40%」 - a permanent, ratio-type attribute.
     */
    @Test
    public void eidolon4RaisesHerBreakingEffect() {
        EidolonFixture base = new EidolonFixture(0, BattleFixture.monster());
        EidolonFixture atFour = new EidolonFixture(4, BattleFixture.monster());

        double plain = base.sushang.getAttribute(AttributeType.BREAKING_EFFECT).get();
        double raised = atFour.sushang.getAttribute(AttributeType.BREAKING_EFFECT).get();

        Assertions.assertEquals(plain + 0.4, raised, 1e-9,
                "「击破特攻提高40%」 is a RATIO attribute, so percent IS the value: " + plain + " -> " + raised);
    }

    /**
     * 星魂 6: 「天赋的加速效果可以叠加，最多叠加2层，且进入战斗后素裳立即获得1层」.
     *
     * <p>⚠ Three facts that can fail independently, so all three are stated: she STARTS one layer up, a second break
     * takes her to two, and a third does NOT take her to three.
     */
    @Test
    public void eidolon6StartsHerWithOneTalentStackAndCapsAtTwo() {
        EidolonFixture rankZero = new EidolonFixture(0, BattleFixture.monster());
        EidolonFixture atSix = new EidolonFixture(6, BattleFixture.monster());

        Assertions.assertTrue(atSix.sushang.getAttribute(AttributeType.SPEED).get()
                        > rankZero.sushang.getAttribute(AttributeType.SPEED).get(),
                "「进入战斗后素裳立即获得1层天赋的加速效果」 - rank 6 starts faster before anything breaks");

        atSix.battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.BREAK, atSix.sushang, atSix.enemy, 0, 0));
        double afterOne = atSix.sushang.getAttribute(AttributeType.SPEED).get();
        atSix.battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.BREAK, atSix.sushang, atSix.enemy, 0, 0));
        double afterTwo = atSix.sushang.getAttribute(AttributeType.SPEED).get();
        Assertions.assertTrue(afterTwo > afterOne,
                "「天赋的加速效果可以叠加」 - a second break stacks: " + afterOne + " -> " + afterTwo);

        atSix.battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.BREAK, atSix.sushang, atSix.enemy, 0, 0));
        Assertions.assertEquals(afterTwo, atSix.sushang.getAttribute(AttributeType.SPEED).get(), 1e-9,
                "「最多叠加2层」 - a third break changes nothing");
    }

    private static final class EidolonFixture {
        private final Character sushang;
        private final Character ally = CharacterFactory.create(ALLY_1201, LEVEL);
        private final Enemy enemy;
        private final Battle battle;

        private EidolonFixture(int eidolon, Enemy enemy) {
            this.enemy = enemy;
            this.sushang = CharacterFactory.create(SUSHANG, LEVEL, true, null, null, eidolon);
            this.battle = new Battle(new ArrayList<>(List.of(sushang, ally)), new ArrayList<>(List.of(enemy)),
                    BattleFixture.alwaysRoll(0.0));
            battle.startBattle();
        }

        private void castSkill() {
            battle.castImmediate(sushang.getSkills().get(SkillType.SKILL), sushang, List.of(enemy));
        }
    }

    // ==================================================================
    // 4. The ultimate's rider: every extra 剑势 follows the SKILL row.
    // ==================================================================

    /**
     * Every 【剑势】instance of one skill cast, as a share of the attacker's own ATK, in settlement order.
     */
    @Test
    public void everySwordStanceInstanceFollowsTheSkillRow() {
        List<Double> atZero = swordStancePerAttack(0);
        List<Double> atFive = swordStancePerAttack(5);
        System.out.println("[1206] 剑势每一发 ÷ 攻击力: E0 " + atZero + " ; E5 " + atFive);
        Assertions.assertEquals(3, atZero.size(),
                "precondition: 1 次基础判定 + 2 次额外判定（大招标记在场）");
        Assertions.assertEquals(atZero.size(), atFive.size(), "两档的段数必须一致，否则比不了");
        for (int i = 0; i < atZero.size(); i++) {
            Assertions.assertEquals(1.1, atFive.get(i) / atZero.get(i), 1e-9,
                    "第 " + i + " 发剑势也必须随战技那一行走：E5 之后该行是 1.1 倍"
                            + "（E0=" + atZero + " / E5=" + atFive + "）");
        }
    }

    /**
     * One battle: the ultimate opens the 2-extra-roll marker, then one skill cast settles 剑势.
     */
    private static List<Double> swordStancePerAttack(int eidolonRank) {
        Battle battle = new Battle(
                List.of(CharacterFactory.create(SUSHANG, 80, false, null, null, eidolonRank),
                        CharacterFactory.create(ALLY_1204, 80)),
                List.of(BattleFixture.dummy()), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        battle.processRequests();
        Character sushang = battle.characters().getFirst();

        // "施放战技时额外增加 2 次【剑势】的发动判定": the ultimate installs it for 2 turns.
        sushang.setCurrentEnergy(sushang.getMaxEnergy());
        battle.castUltra(sushang, List.of(battle.enemyUnits().getFirst()));
        battle.processRequests();
        double atk = sushang.getAttribute(com.laosun.aluminium.enums.AttributeType.ATTACK).get();

        List<Damage> settled = new ArrayList<>();
        battle.addDamageObserver((damage, applied, hpLoss, shieldAbsorbed, died) -> settled.add(damage));
        SkillExecutor.execute(battle, sushang.getSkills().get(SkillType.SKILL), sushang,
                List.of(battle.enemyUnits().getFirst()));
        battle.processRequests();

        return settled.stream()
                .filter(d -> d.getType() == DamageType.ADDITIONAL)
                .map(d -> d.breakdown().get("final") / atk)
                .toList();
    }

    // ==================================================================
    // 5. The three level rows, each pinned at BOTH ends of its level.
    // ==================================================================

    /**
     * 「最后一击后有 33% 概率发动【剑势】，对目标造成等同于素裳 100% 攻击力的物理附加伤害」 - the share is the SKILL row's #2.
     *
     * <p>⚠ The observable is a RATIO between two Eidolon ranks, so no zone is pinned: one Skill cast settles the skill's own
     * damage (row #1) plus one Sword Stance (row #2), and the two rows scale together - at E0 by 1.0, at E5 (SKILL +2) by
     * (2.31 + 1.1) / (2.1 + 1.0) = <b>1.1</b> exactly. With the Lv10 literal in the stance it would read (2.31 + 1.0) / 3.1 =
     * 1.0677 instead, which is the red this case owes.
     */
    @Test
    public void theSwordStanceShareFollowsTheSkillRowAtBothEnds() {
        double atZero = oneSkillCastAgainstOneEnemy(0);
        double atFive = oneSkillCastAgainstOneEnemy(5);
        System.out.println("[1206] one Skill cast (skill row #1 + one 剑势 row #2) deals " + atZero + " at E0 and "
                + atFive + " at E5 (x" + (atFive / atZero) + "; with the Lv10 literal it would be "
                + ((2.31 + 1.0) / (2.1 + 1.0)) + ")");
        Assertions.assertEquals(1.1, atFive / atZero, 1e-9,
                "战技行的 #2 随等级走（E0 1.0、E5 1.1），所以这一发「本体 + 剑势」的比正好是 1.1");
    }

    /**
     * 「同时使其攻击力提高 30%…持续 2 回合」 - the boost is the ULTRA row's #4, which E3 raises.
     *
     * <p>⚠ The multiple is not 1.3, and the reason is worth keeping: the boost is a SHARE of her base ATK, and her stat traces
     * already add <b>28%</b> (4 + 4 + 6 + 6 + 8 across her attribute traces, from the data). So the total is
     * {@code (1 + 0.28 + boost) / 1.28} - measured 1.234375 at E0 and 1.253125 at E3, i.e. exactly the two rows. A Lv10 literal
     * would read 1.234375 at BOTH ends, which is the red the E3 half owes.
     */
    @Test
    public void theUltimatesAttackBoostFollowsTheUltraRowAtBothEnds() {
        double atZero = attackGainFromTheUltimate(0);
        double atThree = attackGainFromTheUltimate(3);
        System.out.println("[1206] the Ultimate's ATK boost: E0 x" + atZero + " ; E3 x" + atThree
                + " (traces +28% ⇒ the rows read as shares of the base; the Lv10 literal would read x1.234375 at both)");
        Assertions.assertEquals(1.234375, atZero, 1e-9, "(1 + 0.28 + 0.30) / 1.28 - the Lv10 row's 30%");
        Assertions.assertEquals(1.253125, atThree, 1e-9, "(1 + 0.28 + 0.324) / 1.28 - E3 raises ULTRA to level 12");
    }

    /**
     * 「当场上有敌方目标的弱点被击破，素裳的速度提高 20%，持续 2 回合」 - E3 raises the TALENT that states this number.
     */
    @Test
    public void theTalentSpeedFollowsTheTalentRowAtBothEnds() {
        double atZero = speedGainAfterABreak(0);
        double atThree = speedGainAfterABreak(3);
        System.out.println("[1206] the talent's SPD boost after a break: E0 x" + atZero + " ; E3 x" + atThree
                + " (the Lv10 literal would read x1.2 at both)");
        Assertions.assertEquals(1.2, atZero, 1e-9, "Lv10 行 = 20% ⇒ E0 读到的就是它");
        Assertions.assertEquals(1.21, atThree, 1e-9, "E3 给天赋 +2 级 ⇒ 第 12 行的 #1 = 21%");
    }

    /**
     * The enemy HP one Skill cast takes: the skill's own hit plus the Sword Stance that the 33% roll (made certain by the
     * fixture's source) triggers.
     */
    private static double oneSkillCastAgainstOneEnemy(int eidolonRank) {
        Battle battle = newBattle(eidolonRank, RNG_ALWAYS_PASSES);
        Character sushang = battle.characters().getFirst();
        Enemy victim = battle.enemyUnits().getFirst();
        double before = victim.getCurrentHp();
        battle.requestSkill(sushang.getSkills().get(SkillType.SKILL), sushang, List.of(victim));
        battle.processRequests();
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ATTACK_FINISHED, sushang, victim, 1, 0));
        battle.processRequests();
        double dealt = before - victim.getCurrentHp();
        Assertions.assertTrue(dealt > 0, "premise: the cast really settles damage");
        return dealt;
    }

    /**
     * How much her ATK moves, as a multiple, when the Ultimate lands.
     */
    private static double attackGainFromTheUltimate(int eidolonRank) {
        Battle battle = newBattle(eidolonRank, BattleFixture.alwaysRoll(0.9999));
        Character sushang = battle.characters().getFirst();
        double before = sushang.getAttribute(AttributeType.ATTACK).get();
        castUltimate(battle, sushang);
        double after = sushang.getAttribute(AttributeType.ATTACK).get();
        return after / before;
    }

    /**
     * How much her SPD moves, as a multiple, when an enemy's weakness breaks.
     */
    private static double speedGainAfterABreak(int eidolonRank) {
        Battle battle = newBattle(eidolonRank, BattleFixture.alwaysRoll(0.9999));
        Character sushang = battle.characters().getFirst();
        Enemy victim = battle.enemyUnits().getFirst();
        double before = sushang.getAttribute(AttributeType.SPEED).get();
        // Note: the BREAK event's actor is the attacker and its target is the enemy whose toughness broke (Battle's own
        // shape), which is exactly what the talent's rule reads.
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.BREAK, sushang, victim, 0, 0));
        battle.processRequests();
        double after = sushang.getAttribute(AttributeType.SPEED).get();
        Assertions.assertTrue(after > before, "premise: the break really raises her SPD");
        return after / before;
    }

    private static void castUltimate(Battle battle, Character sushang) {
        sushang.setCurrentEnergy(sushang.getMaxEnergy());
        battle.castUltra(sushang, List.of(battle.enemyUnits().getFirst()));
        battle.processRequests();
    }

    /**
     * A source whose double always answers {@code 0.0}: the 33% Sword Stance chance passes (measured - it also makes every
     * crit land, which is a CONSTANT factor and therefore cancels in the ratio this case asserts).
     */
    private static final java.util.Random RNG_ALWAYS_PASSES = BattleFixture.alwaysRoll(0.0);

    private static Battle newBattle(int eidolonRank, java.util.Random rng) {
        Battle battle = new Battle(
                List.of(CharacterFactory.create(SUSHANG, 80, false, null, null, eidolonRank),
                        CharacterFactory.create(ALLY_1204, 80)),
                List.of(BattleFixture.monster()), rng);
        battle.startBattle();
        battle.processRequests();
        return battle;
    }

    // ==================================================================
    // 6. The ultimate's two clauses, and the trace that counts 剑势.
    // ==================================================================

    /**
     * ⭐ 「并使素裳立即行动」: casting her ultimate clears her <b>wait</b>, so she is the next to act.
     *
     * <p>⚠ <b>CORRECTED (2026-10-19).</b> This case used to assert {@code getExtraTurnActor() == sushang} - i.e. it pinned
     * `EXTRA_TURN` as the reading of 「立即行动」. The game's own ability data decides it against that: the ultimate's phase
     * ({@code Avatar_Sushang_00_Skill03_Phase02}) ends with {@code SetActionDelay} on the Caster, {@code Value = 0} (her
     * delay is zeroed), and it never uses {@code RPG.GameCore.TurnInsertAbility} - the game's inserted-turn primitive
     * (measured: 1780 uses corpus-wide, none here). So the sentence is "clear the wait", and the two readings differ
     * observably: an extra turn keeps the turn she already had, zeroing the wait consumes it.
     *
     * <p>⚠ Both sides are asserted, or the reading is not pinned: the remaining wait must become <b>0</b>, and <b>no</b>
     * extra turn may be scheduled.
     */
    @Test
    public void herUltimateMakesHerActImmediately() {
        UltimateFixture f = new UltimateFixture();
        Assertions.assertNull(f.battle.getExtraTurnActor(), "precondition: nothing is scheduled yet");
        double before = f.remainingWaitOf(f.sushang);
        Assertions.assertTrue(before > 0, "precondition: she is waiting her turn (" + before + ")");

        f.battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ULT_CAST, f.sushang, f.enemy, 0, 0));

        double after = f.remainingWaitOf(f.sushang);
        System.out.println("[1206] 大招前后的剩余等待: " + before + " -> " + after
                + " ; extraTurnActor = " + f.battle.getExtraTurnActor());
        Assertions.assertEquals(0.0, after, 1e-9, "「立即行动」= 等待清零（数据：`SetActionDelay` 的 `Value = 0`）");
        Assertions.assertNull(f.battle.getExtraTurnActor(),
                "「立即行动」≠「额外回合」：后者会插入一个回合（数据里那用的是 `TurnInsertAbility`）");
    }

    /**
     * ⭐ The same sentence's other clause: 「攻击力提高30%…持续2回合」.
     *
     * <p>⚠ This is a SEPARATE rule from the extra turn on purpose - one clause each - so a case can say which
     * half is missing. Asserting on ATTACK rather than on damage keeps the level convention out of it.
     */
    @Test
    public void herUltimateRaisesHerOwnAttack() {
        UltimateFixture f = new UltimateFixture();
        double before = f.sushang.getAttribute(AttributeType.ATTACK).get();
        double allyBefore = f.ally.getAttribute(AttributeType.ATTACK).get();

        f.battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ULT_CAST, f.sushang, f.enemy, 0, 0));

        Assertions.assertTrue(f.sushang.getAttribute(AttributeType.ATTACK).get() > before,
                "「攻击力提高30%」 must raise her ATTACK: " + before + " -> "
                        + f.sushang.getAttribute(AttributeType.ATTACK).get());
        Assertions.assertEquals(allyBefore, f.ally.getAttribute(AttributeType.ATTACK).get(), 1e-9,
                "and it is HER attack (「其」 = Sushang, the subject of 「使素裳立即行动」), not the whole party's");
    }

    /**
     * ⭐ 行迹 逐寇: each Sword Stance that lands is counted, up to the 10-layer ceiling.
     *
     * <p>⚠ Driven by a REAL Skill cast, not by a hand-fired {@code DAMAGE_SETTLED}. Measured: the synthetic
     * event carries no damage instance, so {@code damage_is_additional} cannot be evaluated and the rule
     * correctly does not fire - the case read 0. The discriminator this clause rests on is a property of the
     * instance, so only a real hit can exercise it.
     *
     * <p>⚠ The dice are pinned to 0.0 so the 33% Sword Stance always triggers; and the count is asserted at
     * the cap as well, because a ceiling that is declared but not enforced is the failure mode this pins.
     */
    @Test
    public void theTraceCountsSwordStanceLayersUpToTen() {
        UltimateFixture f = new UltimateFixture();
        Assertions.assertEquals(0, f.sushang.getBuffManager().stacksOf("剑势"),
                "precondition: nothing counted yet");

        // One cast: the Sword Stance lands (the dice are pinned so it always does) and must be counted once.
        // ⚠ The hit is verified by the enemy's HP rather than assumed, so a count of 0 would mean the
        // counter is broken and not that the Sword Stance never happened.
        double before = f.enemy.getCurrentHp();
        f.castSkill();
        Assertions.assertTrue(f.enemy.getCurrentHp() < before, "precondition: the Skill hit");
        Assertions.assertEquals(1, f.sushang.getBuffManager().stacksOf("剑势"),
                "「每发动1次【剑势】」 counts one layer per Sword Stance that lands");

        for (int i = 0; i < 15; i++) {
            f.castSkill();
        }
        Assertions.assertEquals(10, f.sushang.getBuffManager().stacksOf("剑势"),
                "「最多叠加10层」 must hold: the cap is declared on ADD_STACK and must be enforced there");
    }

    /**
     * Census of the clauses this class asserts, so a rule that disappears is named rather than silently
     * shrinking the file.
     */
    @Test
    public void theFileCarriesTheUltimateAndTraceClauses() {
        var table = TriggerTables.of(SUSHANG);
        Assertions.assertEquals(3, table.ruleCount(TriggerEvent.ULT_CAST),
                "the extra turn and the attack raise - one clause each, so a missing half is visible");
        // Two: the Sword Stance counter, and eidolon 2's damage reduction - both keyed on "this instance is
        // additional damage", which is how the engine names a Sword Stance. The pair sharing one discriminator
        // is the point, so the count is stated rather than left implicit.
        Assertions.assertEquals(2, table.ruleCount(TriggerEvent.DAMAGE_SETTLED),
                "the Sword Stance counter and eidolon 2");
        Assertions.assertEquals(1, table.ruleCount(TriggerEvent.DEALING_DAMAGE), "the per-stack boost");
    }

    private static final class UltimateFixture {
        private final Character sushang = CharacterFactory.create(SUSHANG, LEVEL);
        private final Character ally = CharacterFactory.create(ALLY_1201, LEVEL);
        private final Enemy enemy = BattleFixture.monster();
        private final Battle battle = new Battle(List.of(sushang, ally), List.of(enemy),
                BattleFixture.alwaysRoll(0.0));

        private UltimateFixture() {
            battle.startBattle();
        }

        /**
         * Her Skill at the enemy, through the engine's own immediate-cast entry point.
         */
        private void castSkill() {
            battle.castImmediate(sushang.getSkills().get(SkillType.SKILL), sushang, List.of(enemy));
        }

        /**
         * A unit's remaining wait, read off the action bar's own snapshot (the battle clock does not move).
         */
        private double remainingWaitOf(com.laosun.aluminium.entity.CanHit unit) {
            for (var signal : battle.queue().snapshot()) {
                if (signal.getCanHit() == unit) {
                    return battle.queue().getTimeRemaining(signal);
                }
            }
            throw new AssertionError(unit.getName() + " is not on the action bar");
        }
    }
}
