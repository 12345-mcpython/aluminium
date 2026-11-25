package com.laosun.aluminium.test.content.characters.archer;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.buff.AbstractBuff;
import com.laosun.aluminium.buff.StateBuff;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.skill.DefaultSkill;
import com.laosun.aluminium.skill.Skill;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.trigger.TriggerPayload;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 1015 Archer (Archer): his whole kit in one class.
 *
 * <p><b>天赋/秘技/行迹.</b> The talent's follow-up, the technique (秘技 千里眼) and the three traces. ⚠ Each case is written as a
 * <b>contrast</b>, because every one of these clauses has a "does nothing" twin that a one-sided assertion cannot see: the
 * follow-up needs Charge AND a <em>teammate</em>; the SP-cap trace moves a number that is 5 by default; the crit-damage trace
 * needs the pool at 4 (its cap, in the same fixture, is 7).
 *
 * <p><b>星魂 1 and 星魂 2.</b> Eidolon 1 — 「单个回合内施放 3 次战技后，为我方恢复 2 个战技点」 — keys on a <b>count of this
 * turn's casts</b>, which no per-turn limit can express (a limit refuses the fourth cast; this pays on the third). The counter
 * is a marker that the third cast clears and every turn start resets, so the contrasts are "third cast pays" and "a new turn
 * starts the count again". Eidolon 2 implants a Quantum weakness and marks the target as taking more Quantum damage.
 *
 * <p><b>星魂 3, 4, 5 and 6.</b> ⚠ Every case is stated at TWO ranks: a clause that is correctly gated and one that applies for
 * free differ only at the lower rank, so a case pinned at E6 alone cannot tell them apart. ⚠ Eidolon 5's clause is a LEVEL
 * raise, and the number it moves is the talent's follow-up share - so that case is written as a ratio between the two ranks
 * (2.2 / 2.0 = 1.1), which is the property that survives any change to the fixture's attack power.
 *
 * <p><b>战技 伪•螺旋剑 (101502) and its 【回路连接】 loop.</b> ⚠ <b>What is read, and why not the damage number.</b> Each cast
 * settles one instance whose {@code BoostArea} rate IS {@code 1 + Σ boost} - so the layer's contribution is visible exactly as
 * "+1.0 on that factor", and the entering cast can be pinned against the boost the character already has from her traces
 * (0.224 of quantum DMG boost). Asserting on the settled damage instead would mix in every other zone and, worse, a wrong
 * "one layer on the entering cast" would keep the per-cast RATIOS perfectly consistent. ⚠ Every number in the clause is a
 * place to be off by one: the entering cast must not be boosted, cast 2 must gain one layer, cast 3 must hit the ceiling,
 * cast 4 must stay there, and cast 5 must still carry the ceiling (the exit happens <em>after</em> it settles) while the
 * state, the layers and the counter are gone afterwards.
 *
 * <p><b>「本回合不会结束」.</b> There is no "the turn does not end" switch - the turn is closed by {@code afterMove}. What the
 * vocabulary does have is {@code EXTRA_TURN}: a <b>free</b> turn (no duration is spent, see
 * {@code FreeTurnDoesNotSpendDurationsTest}), the engine's reading of "he acts again without a turn passing". The two
 * registered differences (a new {@code TURN_START} is fired for him; DoTs still tick on the inserted turn) are what the two
 * {@code !self has_state 回路连接} gates in his own file are for - the first is asserted here, because without it his Eidolon 6
 * would pay one skill point per loop cast instead of one per turn.
 *
 * <p>This class consolidates five earlier per-area classes, so the fixtures are named after their area ({@code KitFixture},
 * {@code EidolonScene}, {@code LoopFixture}, {@code TurnRun}) and the marker names those areas shared ({@code 充能},
 * {@code internal:1015/loop_layers}) are declared once at the top.
 *
 * <p><b>L3:</b> the cases that read ONE clause on ONE fixture are folded into scenario tests, one
 * {@code Assertions.assertAll} per fixture and clause, so a failing clause still names itself: the talent's follow-up with
 * both of its silent twins (no Charge, her own attack), the Eidolon 1 cast count with its turn restart, the loop's 战技点 gate
 * at 0 and at 1, and the free turn together with the duration it does not spend. The cases that read a different fixture or a
 * different clause — the technique, the two traces, each Eidolon rank's own board, the five-cast loop, the wave-clear exit
 * and the manual exit — keep their own tests.
 */
public class ArcherTest {
    private static final int ARCHER = 1015;
    private static final int ALLY = 1204;
    private static final int LEVEL = 80;
    private static final String CHARGE = "char:1015/charge";
    private static final String LOOP = "char:1015/loop_link";
    private static final String CYCLES = "internal:1015/loop_count";
    private static final String LAYERS = "internal:1015/loop_layers";
    /** The manual exit button: {@code skills.json} 1015 slot 9 ({@code 101509}, effect {@code Enhance}). */
    private static final int EXIT_SLOT = 9;

    // ==================================================================
    // 天赋/秘技/行迹: the follow-up, the technique, and the three traces
    // ==================================================================

    /**
     * 「当Archer的队友对敌方目标施放攻击后，Archer消耗1点充能…恢复1个战技点」 — together with the two "does nothing" twins of
     * the same clause: no Charge in the pool, and an attack that is her own rather than a teammate's.
     *
     * <p>Each twin is its own board of the same {@link KitFixture}, because the one that spends the Charge can no longer be
     * the one that has none; every value below is read once and stated inside the single {@code assertAll}.
     */
    @Test
    public void theFollowUpNeedsATeammatesAttackAndAHeldCharge() {
        KitFixture withCharge = new KitFixture(1);
        // Note: the battle already opens with 1 Charge - that is trace 1015102, pinned by its own case below.
        int chargeAtStart = withCharge.archer.getResources().value(CHARGE);
        int skillsPointsBefore = withCharge.battle.getSkillPoints();

        int followUps = withCharge.allyAttacks();

        int chargeAfter = withCharge.archer.getResources().value(CHARGE);
        int skillPointsAfter = withCharge.battle.getSkillPoints();
        System.out.println("[1015] 队友攻击后：追击 " + followUps + " 次 ; 充能 " + chargeAfter
                + " ; 战技点 " + skillsPointsBefore + " -> " + skillPointsAfter);

        KitFixture withoutCharge = new KitFixture(1);
        withoutCharge.archer.getResources().spend(CHARGE, 1);
        int chargeAfterSpend = withoutCharge.archer.getResources().value(CHARGE);
        int pointsBeforeNoCharge = withoutCharge.battle.getSkillPoints();
        int followUpsWithoutCharge = withoutCharge.allyAttacks();
        int pointsAfterNoCharge = withoutCharge.battle.getSkillPoints();
        System.out.println("[1015] 没有充能时队友攻击：追击 " + followUpsWithoutCharge + " 次 ; 战技点 "
                + pointsBeforeNoCharge + " -> " + pointsAfterNoCharge);

        KitFixture ownAttack = new KitFixture(1);
        int chargeBeforeOwnAttack = ownAttack.archer.getResources().value(CHARGE);
        List<Damage> settled = new ArrayList<>();
        ownAttack.battle.addDamageObserver((damage, applied, hpLoss, shieldAbsorbed, died) -> settled.add(damage));
        ownAttack.battle.castImmediate(ownAttack.archer.getSkills().get(SkillType.COMMON), ownAttack.archer,
                List.of(ownAttack.battle.enemyUnits().getFirst()));
        ownAttack.battle.processRequests();
        long ownFollowUps = settled.stream().filter(d -> d.getType() == DamageType.ADDITIONAL).count();
        int chargeAfterOwnAttack = ownAttack.archer.getResources().value(CHARGE);
        System.out.println("[1015] 她自己攻击后：追击 " + ownFollowUps + " 次 ; 充能 " + chargeAfterOwnAttack);

        Assertions.assertAll("天赋「队友对敌方目标施放攻击后」：有充能 ⇒ 追击并回 1 战技点；没充能或自己攻击 ⇒ 什么都不发生",
                () -> Assertions.assertEquals(1, chargeAtStart, "precondition: 行迹给了 1 点开场充能"),
                () -> Assertions.assertEquals(1, followUps, "一次队友攻击 ⇒ 一次追加攻击"),
                () -> Assertions.assertEquals(0, chargeAfter, "消耗 1 点充能"),
                () -> Assertions.assertEquals(skillsPointsBefore + 1, skillPointsAfter, "并恢复 1 个战技点"),
                () -> Assertions.assertEquals(0, chargeAfterSpend, "precondition: 充能花光"),
                () -> Assertions.assertEquals(0, followUpsWithoutCharge, "没有充能 ⇒ 不发动追加攻击"),
                () -> Assertions.assertEquals(pointsBeforeNoCharge, pointsAfterNoCharge, "也就不恢复战技点"),
                () -> Assertions.assertEquals(1, chargeBeforeOwnAttack, "precondition: 有 1 点充能"),
                () -> Assertions.assertEquals(0, ownFollowUps, "「**队友**对敌方目标施放攻击后」—— 她自己那一下不算"),
                () -> Assertions.assertEquals(1, chargeAfterOwnAttack, "充能也没有被扣"));
    }

    /**
     * 秘技 千里眼: entering battle hits every enemy and grants 1 Charge - and only when the technique was declared.
     */
    @Test
    public void theTechniqueHitsEveryEnemyAndGrantsOneCharge() {
        Battle withTechnique = kitBattle(2, true);
        double hp = 0;
        for (Enemy enemy : withTechnique.enemyUnits()) {
            hp += enemy.getMaxHp() - enemy.getCurrentHp();
        }
        System.out.println("[1015] 秘技开局: 敌方合计掉血 " + hp + " ; 充能 "
                + withTechnique.characters().getFirst().getResources().value(CHARGE));
        Assertions.assertTrue(hp > 0, "「对敌方全体造成…伤害」");
        Assertions.assertEquals(withoutTechniqueCharge() + 1,
                withTechnique.characters().getFirst().getResources().value(CHARGE),
                "「并获得 1 点充能」—— 与同一场没声明秘技相比**正好多 1 点**（开场那 1 点来自行迹，不算进来）");

        Battle withoutTechnique = kitBattle(2, false);
        double untouched = 0;
        for (Enemy enemy : withoutTechnique.enemyUnits()) {
            untouched += enemy.getMaxHp() - enemy.getCurrentHp();
        }
        Assertions.assertEquals(0.0, untouched, 1e-9, "没声明秘技 ⇒ 什么都不发生（这是门那一半）");
    }

    /**
     * The Charge a battle opens with when the technique was NOT declared: trace 1015102's 1 point.
     */
    private static int withoutTechniqueCharge() {
        return kitBattle(1, false).characters().getFirst().getResources().value(CHARGE);
    }

    /**
     * 行迹 正义伙伴 (1 Charge at battle start) and 投影魔术 (skill point cap +2).
     */
    @Test
    public void theTracesRaiseTheChargeAndTheSkillPointCap() {
        KitFixture f = new KitFixture(1);
        System.out.println("[1015] 开场充能 " + f.archer.getResources().value(CHARGE)
                + " ; 战技点上限 " + f.battle.getSkillPointMax());
        Assertions.assertEquals(1, f.archer.getResources().value(CHARGE), "「进入战斗时获得 1 点充能」");
        Assertions.assertEquals(5 + 2, f.battle.getSkillPointMax(), "「战技点上限额外增加 2 点」（标准上限 5）");
    }

    /**
     * 行迹 守护者: 「若战技点大于等于 4 点，暴击伤害提高 120%，持续 1 回合」 - the count, not the cap (the cap is 7 here).
     */
    @Test
    public void theCritDamageTraceReadsThePoolNotTheCap() {
        double atThree = critDamageGainFromASkillPointGain(3);
        double atFour = critDamageGainFromASkillPointGain(4);
        System.out.println("[1015] 获得战技点后的暴伤: 手里 3 点 +" + atThree + " ; 手里 4 点 +" + atFour
                + "（上限是 7 点）");
        Assertions.assertEquals(0.0, atThree, 1e-9,
                "手里 3 点 < 4 ⇒ 不触发（拿**上限** 7 冒充会在这里红）");
        Assertions.assertEquals(1.2, atFour, 1e-9, "手里 4 点 ⇒ 暴击伤害 +120%");
    }

    // ==================================================================

    /**
     * How much CRIT DMG the trace grants when a skill point is gained while the pool holds {@code skillPoints}.
     */
    private static double critDamageGainFromASkillPointGain(int skillPoints) {
        KitFixture f = new KitFixture(1);
        while (f.battle.getSkillPoints() > skillPoints) {
            f.battle.getSkillPointPolicy().spend();
        }
        while (f.battle.getSkillPoints() < skillPoints) {
            f.battle.getSkillPointPolicy().gain(1);
        }
        double before = f.archer.getAttribute(AttributeType.CRIT_ATTACK).get();
        f.battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.SKILL_POINT_GAINED, f.archer, f.archer, 0, 1));
        f.battle.processRequests();
        return f.archer.getAttribute(AttributeType.CRIT_ATTACK).get() - before;
    }

    private static Battle kitBattle(int enemies, boolean technique) {
        List<Enemy> foes = new ArrayList<>();
        for (int i = 0; i < enemies; i++) {
            foes.add(BattleFixture.dummy());
        }
        Battle battle = new Battle(List.of(CharacterFactory.create(ARCHER, 80), CharacterFactory.create(ALLY, 80)),
                foes, BattleFixture.alwaysRoll(0.9999));
        if (technique) {
            battle.markTechniqueUsed(battle.characters().getFirst());
        }
        battle.startBattle();
        battle.processRequests();
        return battle;
    }

    private static final class KitFixture {
        private final Character archer;
        private final Character ally;
        private final Battle battle;

        private KitFixture(int enemies) {
            this.battle = kitBattle(enemies, false);
            this.archer = battle.characters().getFirst();
            this.ally = battle.characters().get(1);
        }

        /**
         * The teammate attacks the first enemy; returns how many ADDITIONAL instances it settled (i.e. the follow-ups).
         */
        private int allyAttacks() {
            List<Damage> settled = new ArrayList<>();
            battle.addDamageObserver((damage, applied, hpLoss, shieldAbsorbed, died) -> settled.add(damage));
            battle.castImmediate(ally.getSkills().get(SkillType.COMMON), ally,
                    List.of(battle.enemyUnits().getFirst()));
            battle.processRequests();
            return (int) settled.stream().filter(d -> d.getType() == DamageType.ADDITIONAL).count();
        }
    }

    // ==================================================================
    // 星魂 1 and 星魂 2
    // ==================================================================

    /**
     * 星魂 1: 「单个回合内施放 3 次战技后，为我方恢复 2 个战技点」 — the count pays on the third cast, and a new turn starts
     * the count over.
     *
     * <p>The restart needs a board that has <em>not</em> already cleared the counter, so it is a second
     * {@link EidolonScene}; the three-cast reading stays on the one it always drove.
     */
    @Test
    public void theThirdCastInATurnPaysTwoSkillPointsAndANewTurnStartsTheCountOver() {
        EidolonScene counting = new EidolonScene(1);
        int before = counting.battle.getSkillPoints();

        counting.castSkill();
        int afterOne = counting.battle.getSkillPoints();
        counting.castSkill();
        int afterTwo = counting.battle.getSkillPoints();
        counting.castSkill();
        int afterThree = counting.battle.getSkillPoints();

        int perCast = afterOne - before;

        EidolonScene acrossTurns = new EidolonScene(1);
        acrossTurns.castSkill();
        acrossTurns.castSkill();
        acrossTurns.newTurn();
        int beforeRestart = acrossTurns.battle.getSkillPoints();
        acrossTurns.castSkill();
        int gainAfterRestart = acrossTurns.battle.getSkillPoints() - beforeRestart;

        Assertions.assertAll("星魂 1：本回合第 3 次施放才付 2 点；新回合重新数",
                () -> Assertions.assertEquals(perCast, afterTwo - afterOne - 0, "the first two casts behave the same"),
                () -> Assertions.assertTrue(afterThree - afterTwo > perCast,
                        "「施放 3 次战技后，为我方恢复 2 个战技点」 (third cast: " + (afterThree - afterTwo) + " vs " + perCast + ")"),
                () -> Assertions.assertTrue(gainAfterRestart <= 2,
                        "after a new turn the counter restarts, so the first cast is just a cast"));
    }

    /**
     * 星魂 2: the Ultimate implants a Quantum weakness and marks the target as taking more Quantum damage.
     *
     * <p>The resistance half is an APPROXIMATION recorded in the rule: RESISTANCE_REDUCTION is all-type in this engine, so the
     * element-scoped spelling that exists is damage-taken. What is asserted is that both halves land on the target and expire with
     * their stated duration - not that the number sits in the resistance zone.
     */
    @Test
    public void eidolon2ImplantsAQuantumWeaknessAndMarksTheTarget() {
        EidolonScene scene = new EidolonScene(2);
        Assertions.assertFalse(scene.enemy.isWeakTo(com.laosun.aluminium.enums.DamageElement.QUANTUM), "precondition: no Quantum weakness yet");

        scene.castUltimate();

        Assertions.assertTrue(scene.enemy.isWeakTo(com.laosun.aluminium.enums.DamageElement.QUANTUM), "「并为其添加量子属性弱点」");
        Assertions.assertTrue(scene.enemy.getBuffManager().countBuffs(com.laosun.aluminium.buff.VulnerabilityBuff.class) > 0,
                "the damage-taken half is an element-scoped VulnerabilityBuff (the approximation the rule registers)");
    }

    // ==================================================================

    private static final class EidolonScene {
        private final Character archer;
        private final Enemy enemy = BattleFixture.dummy();
        private final Battle battle;

        private EidolonScene(int eidolon) {
            archer = CharacterFactory.create(ARCHER, 80, true, null, null, eidolon);
            Character ally = CharacterFactory.create(ALLY, 80);
            battle = new Battle(List.of(archer, ally), List.of(enemy), BattleFixture.alwaysRoll(0.9999));
            battle.startBattle();
            battle.processRequests();
        }

        private void castSkill() {
            battle.castImmediate(archer.getSkills().get(SkillType.SKILL), archer, List.of(enemy));
            battle.processRequests();
        }

        private void castUltimate() {
            battle.castImmediate(archer.getSkills().get(SkillType.ULTRA), archer, List.of(enemy));
            battle.processRequests();
        }

        private void newTurn() {
            battle.fire(TriggerPayload.Simple.of(TriggerEvent.TURN_START, archer, archer, 0, 0));
            battle.processRequests();
        }
    }

    // ==================================================================
    // 星魂 3, 4, 5 and 6
    // ==================================================================

    /**
     * Eidolon 3: 战技 +2, 普攻 +1 (clamped by its own 9-row table). Eidolon 5: 终结技 +2, 天赋 +2.
     */
    @Test
    public void theEidolonsRaiseTheSlotsTheyName() {
        int[] atZero = levelsAt(0);
        int[] atThree = levelsAt(3);
        int[] atFive = levelsAt(5);
        System.out.println("[1015] COMMON/SKILL/ULTRA/TALENT: E0 " + shown(atZero) + " ; E3 " + shown(atThree)
                + " ; E5 " + shown(atFive));
        Assertions.assertArrayEquals(new int[]{6, 10, 10, 10}, atZero,
                "等级口径：普攻钉第 6 行（文档引 100%），其余三槽第 10 行");
        Assertions.assertArrayEquals(new int[]{7, 12, 10, 10}, atThree,
                "星魂 3：战技+2 ⇒ 第 12 行；普攻 6 ⇒ 7");
        Assertions.assertArrayEquals(new int[]{7, 12, 12, 12}, atFive,
                "星魂 5：终结技+2、天赋+2 ⇒ 各第 12 行（星魂 3 的战技 +2 仍在）");
    }

    /**
     * Eidolon 5 moves the talent's row: the follow-up share goes 2.0 → 2.2.
     */
    @Test
    public void theFollowUpFollowsTheTalentRow() {
        double atZero = followUpPerAttack(0);
        double atFive = followUpPerAttack(5);
        System.out.println("[1015] relic:115/follow_up ÷ 攻击力: E0 " + atZero + " ; E5 " + atFive
                + "（写死 2.0 的话两档一样）");
        Assertions.assertEquals(1.1, atFive / atZero, 1e-9, "星魂 5 给天赋 +2 级 ⇒ 第 12 行是 2.2（E0 是 2.0）");
    }

    /**
     * Eidolon 4: 「造成的终结技伤害提高 50%」 - the Ultimate's own boost zone gains 0.5.
     */
    @Test
    public void theEidolonFourRaisesOnlyTheUltimatesBoostZone() {
        double atZero = boostZoneOfTheUltimate(0);
        double atFour = boostZoneOfTheUltimate(4);
        System.out.println("[1015] 终结技的增伤区: E0 ×" + atZero + " ; E4 ×" + atFour);
        Assertions.assertEquals(atZero + 0.5, atFour, 1e-9, "星魂 4：终结技那一类的增伤 +50%");
    }

    /**
     * Eidolon 6's three halves: a skill point each turn, one more layer, and 20% defence ignored on skill damage.
     */
    @Test
    public void theEidolonSixHasAllThreeHalves() {
        Assertions.assertEquals(0, skillPointGainOnHerTurn(0), "E0：回合开始不给战技点");
        Assertions.assertEquals(1, skillPointGainOnHerTurn(6), "星魂 6：回合开始时恢复 1 个战技点");

        Assertions.assertEquals(2, layersAtEidolon(0), "E0：层数上限 2");
        Assertions.assertEquals(3, layersAtEidolon(6), "星魂 6：上限提高 1 层");

        double atZero = defenceFactorOfASkillHit(0);
        double atSix = defenceFactorOfASkillHit(6);
        System.out.println("[1015] 战技那发的防御区: E0 ×" + atZero + " ; E6 ×" + atSix);
        Assertions.assertTrue(atSix > atZero + 1e-9,
                "星魂 6：「战技伤害无视 20% 的防御力」⇒ 防御区变大（" + atZero + " -> " + atSix + "）");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private static String shown(int[] levels) {
        return "C=" + levels[0] + " S=" + levels[1] + " U=" + levels[2] + " T=" + levels[3];
    }

    private static int[] levelsAt(int eidolonRank) {
        Character archer = newBattle(eidolonRank, 1);
        return new int[]{
                archer.skillLevel(archer.getSkills().get(SkillType.COMMON)),
                archer.skillLevel(archer.getSkills().get(SkillType.SKILL)),
                archer.skillLevel(archer.getSkills().get(SkillType.ULTRA)),
                archer.skillLevel(archer.getSkills().get(SkillType.TALENT))};
    }

    /**
     * The follow-up instance's settled damage per point of attack power (so the zones cancel between ranks).
     */
    private static double followUpPerAttack(int eidolonRank) {
        Battle battle = eidolonBattle(eidolonRank);
        Character archer = battle.characters().getFirst();
        Character ally = battle.characters().get(1);
        double atk = archer.getAttribute(com.laosun.aluminium.enums.AttributeType.ATTACK).get();
        List<Damage> settled = new ArrayList<>();
        battle.addDamageObserver((damage, applied, hpLoss, shieldAbsorbed, died) -> settled.add(damage));
        battle.castImmediate(ally.getSkills().get(SkillType.COMMON), ally, List.of(battle.enemyUnits().getFirst()));
        battle.processRequests();
        return settled.stream().filter(d -> d.getType() == DamageType.ADDITIONAL)
                .mapToDouble(d -> d.breakdown().get("final")).sum() / atk;
    }

    /**
     * The {@code BoostArea} factor of the Ultimate's own instance.
     */
    private static double boostZoneOfTheUltimate(int eidolonRank) {
        Battle battle = eidolonBattle(eidolonRank);
        Character archer = battle.characters().getFirst();
        archer.setCurrentEnergy(archer.getMaxEnergy());
        List<Damage> settled = new ArrayList<>();
        battle.addDamageObserver((damage, applied, hpLoss, shieldAbsorbed, died) -> settled.add(damage));
        battle.castUltra(archer, List.of(battle.enemyUnits().getFirst()));
        battle.processRequests();
        return settled.stream().filter(d -> d.getType() == DamageType.NORMAL)
                .map(d -> d.breakdown().get("BoostArea"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("the Ultimate settled no ordinary instance"));
    }

    /**
     * How many skill points her own turn start hands the team.
     */
    private static int skillPointGainOnHerTurn(int eidolonRank) {
        Battle battle = eidolonBattle(eidolonRank);
        Character archer = battle.characters().getFirst();
        int before = battle.getSkillPoints();
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.TURN_START, archer, archer, 0, 0));
        battle.processRequests();
        return battle.getSkillPoints() - before;
    }

    /**
     * The largest layer count the loop reaches after enough casts (the cap, which Eidolon 6 raises by one).
     */
    private static int layersAtEidolon(int eidolonRank) {
        Battle battle = eidolonBattle(eidolonRank);
        Character archer = battle.characters().getFirst();
        Skill skill = archer.getSkills().get(SkillType.SKILL);
        for (int cast = 0; cast < 4; cast++) {
            battle.castImmediate(skill, archer, List.of(battle.enemyUnits().getFirst()));
        }
        battle.processRequests();
        return archer.getBuffManager().stacksOf(LAYERS);
    }

    /**
     * The {@code DefenceArea} factor of a plain Skill hit.
     */
    private static double defenceFactorOfASkillHit(int eidolonRank) {
        Battle battle = eidolonBattle(eidolonRank);
        Character archer = battle.characters().getFirst();
        List<Damage> settled = new ArrayList<>();
        battle.addDamageObserver((damage, applied, hpLoss, shieldAbsorbed, died) -> settled.add(damage));
        battle.castImmediate(archer.getSkills().get(SkillType.SKILL), archer,
                List.of(battle.enemyUnits().getFirst()));
        battle.processRequests();
        return settled.stream().filter(d -> d.getType() == DamageType.NORMAL)
                .map(d -> d.breakdown().getOrDefault("DefenceArea", 1.0))
                .findFirst()
                .orElseThrow(() -> new AssertionError("the Skill settled no ordinary instance"));
    }

    private static Character newBattle(int eidolonRank, int enemies) {
        return eidolonBattle(eidolonRank, enemies, true).characters().getFirst();
    }

    private static Battle eidolonBattle(int eidolonRank) {
        return eidolonBattle(eidolonRank, 1, false);
    }

    /**
     * A battle whose Charge pool is topped up, so the talent's follow-up is available whatever the Eidolon rank.
     */
    private static Battle eidolonBattle(int eidolonRank, int enemies, boolean topUpCharge) {
        List<com.laosun.aluminium.enemy.Enemy> foes = new ArrayList<>();
        for (int i = 0; i < enemies; i++) {
            // Note: a REAL monster rather than the 0-DEF dummy: the defence-ignore reading needs a defence factor that is
            // not 1.0 (measured: with the dummy it reads 1.0 -> 1.0 and proves nothing).
            foes.add(BattleFixture.enemy(BattleFixture.MONSTER_ID, BattleFixture.MONSTER_LEVEL));
        }
        Battle battle = new Battle(
                List.of(CharacterFactory.create(ARCHER, 80, false, null, null, eidolonRank),
                        CharacterFactory.create(ALLY, 80)),
                foes, BattleFixture.alwaysRoll(0.9999));
        battle.startBattle();
        battle.processRequests();
        battle.characters().getFirst().getResources().get(CHARGE).setValue(4);
        return battle;
    }

    // ==================================================================
    // 战技 伪•螺旋剑 (101502) and its 【回路连接】loop
    // ==================================================================

    /**
     * Five casts: no layer, +1, +2 (ceiling), +2, +2 - then the state, the layers and the counter are cleared.
     */
    @Test
    public void theLoopRaisesSkillDamageOneLayerPerCastAndEndsAfterFive() {
        LoopFixture f = new LoopFixture();
        double traceBoost = f.archer.getAttribute(AttributeType.QUANTUM_DAMAGE_BOOST).get();
        List<Double> factors = new ArrayList<>();
        List<Integer> layers = new ArrayList<>();
        for (int cast = 0; cast < 5; cast++) {
            factors.add(f.castSkillAndReadBoostFactor());
            layers.add(f.archer.getBuffManager().stacksOf(LAYERS));
            System.out.println("[1015] 第 " + (cast + 1) + " 发: 增伤区 ×" + factors.get(cast)
                    + " | 层数 " + layers.get(cast)
                    + " | 次数 " + f.archer.getResources().value(CYCLES)
                    + " | 状态 " + f.archer.getBuffManager().hasState(LOOP));
        }
        Assertions.assertEquals(1 + traceBoost, factors.get(0), 1e-9,
                "进状态那一发**不吃**加成：增伤区只有行迹那 " + traceBoost + "（多一层就会是 +1.0 以上）");
        Assertions.assertEquals(factors.get(0) + 1.0, factors.get(1), 1e-9, "第 2 发：+100%（1 层）");
        Assertions.assertEquals(factors.get(1) + 1.0, factors.get(2), 1e-9, "第 3 发：再 +100%（2 层）");
        Assertions.assertEquals(factors.get(2), factors.get(3), 1e-9, "第 4 发：2 层封顶，不再叠");
        Assertions.assertEquals(factors.get(2), factors.get(4), 1e-9,
                "第 5 发**照样**带满加成 —— 退出发生在它结算之后");

        Assertions.assertEquals(List.of(1, 2, 2, 2, 0), layers, "层数逐发 +1、封顶 2、退出时清空");
        Assertions.assertFalse(f.archer.getBuffManager().hasState(LOOP), "「主动施放 5 次战技后…退出」");
        Assertions.assertEquals(0, f.archer.getResources().value(CYCLES), "退出时次数计数器被清空");
    }

    /**
     * 「或战技点不足以再次施放战技则退出」 — and its boundary: 0 点 is insufficient, 1 点 is still enough.
     *
     * <p>The two readings need separate pools (one spends the last point, the other keeps it), so each is a board of its own
     * {@link LoopFixture}; both observations are stated in the one {@code assertAll}.
     */
    @Test
    public void theLoopEndsWhenTheSkillPointsRunOutButNotAtOnePoint() {
        LoopFixture emptyPool = new LoopFixture();
        emptyPool.castSkillAndReadBoostFactor();
        boolean enteredWithEmptyPool = emptyPool.archer.getBuffManager().hasState(LOOP);

        while (emptyPool.battle.getSkillPoints() > 0) {
            emptyPool.battle.getSkillPointPolicy().spend();
        }
        emptyPool.castSkillAndReadBoostFactor();
        boolean stillOnAfterEmptyPool = emptyPool.archer.getBuffManager().hasState(LOOP);
        System.out.println("[1015] 手里 0 点之后再施放一次战技: 状态 " + stillOnAfterEmptyPool);

        LoopFixture onePoint = new LoopFixture();
        onePoint.castSkillAndReadBoostFactor();
        while (onePoint.battle.getSkillPoints() > 1) {
            onePoint.battle.getSkillPointPolicy().spend();
        }
        int pointsLeft = onePoint.battle.getSkillPoints();
        onePoint.castSkillAndReadBoostFactor();
        boolean stillOnAfterOnePoint = onePoint.archer.getBuffManager().hasState(LOOP);

        Assertions.assertAll("「或战技点不足以再次施放战技则退出」：0 点退出，1 点仍够",
                () -> Assertions.assertTrue(enteredWithEmptyPool, "precondition: the first cast entered the loop"),
                () -> Assertions.assertFalse(stillOnAfterEmptyPool, "手里 0 点 ⇒ 不足以再次施放 ⇒ 退出"),
                () -> Assertions.assertEquals(1, pointsLeft, "precondition: exactly one point left"),
                () -> Assertions.assertTrue(stillOnAfterOnePoint,
                        "手里的 1 点**还够**再放一次 ⇒ 不退出（写成 `skill_point <= 1` 会在这里红）"));
    }

    /**
     * 「每个波次中的所有敌方目标被消灭后会退出」 - read through the next wave's start.
     */
    @Test
    public void theLoopEndsWhenTheWaveClears() {
        LoopFixture f = new LoopFixture();
        f.castSkillAndReadBoostFactor();
        Assertions.assertTrue(f.archer.getBuffManager().hasState(LOOP), "precondition: the loop is on");

        f.battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.WAVE_START, null, null, 0, 0));
        f.battle.processRequests();
        Assertions.assertFalse(f.archer.getBuffManager().hasState(LOOP), "波次清空 ⇒ 退出");
        Assertions.assertEquals(0, f.archer.getResources().value(CYCLES), "并清空计数器");
        Assertions.assertEquals(0, f.archer.getBuffManager().stacksOf(LAYERS), "并清空层数");
    }

    /**
     * 【结束】 (slot 9) is the manual exit: casting it leaves the loop.
     */
    @Test
    public void theManualExitLeavesTheLoop() {
        LoopFixture f = new LoopFixture();
        f.castSkillAndReadBoostFactor();
        Assertions.assertTrue(f.archer.getBuffManager().hasState(LOOP), "precondition: the loop is on");

        f.battle.castImmediate(new DefaultSkill(ARCHER, EXIT_SLOT, 1), f.archer,
                List.of(f.battle.enemyUnits().getFirst()));
        f.battle.processRequests();
        Assertions.assertFalse(f.archer.getBuffManager().hasState(LOOP), "【结束】= 退出【char:1015/loop_link】");
    }

    // ==================================================================

    private static final class LoopFixture {
        private final Character archer = CharacterFactory.create(ARCHER, 80);
        private final Character ally = CharacterFactory.create(ALLY, 80);
        private final Battle battle = new Battle(List.of(archer, ally), List.of(BattleFixture.dummy()),
                BattleFixture.alwaysRoll(0.9999));
        private final List<Damage> settled = new ArrayList<>();

        private LoopFixture() {
            battle.startBattle();
            battle.processRequests();
            battle.addDamageObserver((damage, applied, hpLoss, shieldAbsorbed, died) -> settled.add(damage));
        }

        /**
         * Cast the Skill once and return the {@code BoostArea} factor of the instance it settled.
         *
         * <p>⚠ The instance is typed {@code NORMAL}: the engine types every non-Elation skill's own damage as NORMAL, which
         * is exactly why the loop's bonus cannot be spelled as a damage type (see the rule's note) - it is spelled as the
         * CONDITION {@code from_skill SKILL}.
         */
        private double castSkillAndReadBoostFactor() {
            int mark = settled.size();
            battle.castImmediate(archer.getSkills().get(SkillType.SKILL), archer,
                    List.of(battle.enemyUnits().getFirst()));
            battle.processRequests();
            return settled.subList(mark, settled.size()).stream()
                    .filter(d -> d.getType() == DamageType.NORMAL)
                    .map(d -> d.breakdown().get("BoostArea"))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("the Skill settled no ordinary instance"));
        }
    }

    // ==================================================================
    // 【回路连接】: 「本回合不会结束」 (the EXTRA_TURN reading)
    // ==================================================================

    /**
     * Inside the loop a Skill cast hands him the next action immediately, the loop's own counters survive it, and that free
     * turn spends no duration: a three-turn state on him is still on three turns after it.
     *
     * <p>Both readings are one {@link TurnRun}: the inserted turn is read once, at the points each case always read it (the
     * pending extra turn right after the cast, the actor and the layer right after the step, the state's clock after
     * {@code beforeMove}).
     */
    @Test
    public void aLoopCastDoesNotEndTheTurnAndItsFreeTurnSpendsNoDuration() {
        TurnRun run = new TurnRun(0);
        boolean inTheLoopAtStart = run.archer.getBuffManager().hasState(LOOP);

        run.castSkill();
        boolean inTheLoopAfterTheCast = run.archer.getBuffManager().hasState(LOOP);
        boolean pendingExtraTurn = run.battle.queue().hasPendingExtraTurn();
        run.archer.getBuffManager().addBuff(new StateBuff("测试", 3));

        // The turn ends; the next step must be his again (a free turn), not the enemy's.
        run.battle.afterMove();
        run.battle.stepForward();
        Character nextActor = (Character) run.battle.queue().getCurrentActor().getCanHit();
        int layers = run.layers();
        run.battle.beforeMove();
        int remainingTurns = run.throwawayTurns();

        Assertions.assertAll("「本回合不会结束」：战技后立刻再行动，且这个额外回合不消耗回合数",
                () -> Assertions.assertFalse(inTheLoopAtStart, "precondition: not in the loop yet"),
                () -> Assertions.assertTrue(inTheLoopAfterTheCast, "precondition: the cast entered the loop"),
                () -> Assertions.assertTrue(pendingExtraTurn,
                        "the cast scheduled a FREE turn for her (the clause is EXTRA_TURN, not a queue accident)"),
                () -> Assertions.assertSame(run.archer, nextActor, "「本回合不会结束」 — he acts again at once"),
                () -> Assertions.assertEquals(1, layers, "and the loop counted this cast"),
                () -> Assertions.assertEquals(3, remainingTurns, "「不消耗回合数」 — the free turn spends nothing"));
    }

    /**
     * {@code TURN_START} fires on the free turn too, so his own per-turn clauses must be gated by the loop state.
     *
     * <p>Eidolon 6 pays one skill point at the start of his turn; inside the loop, one turn is five casts.
     */
    @Test
    public void theEidolonSixPayoutIsNotPaidOncePerLoopCast() {
        TurnRun run = new TurnRun(6);
        double before = run.skillPoints();
        run.castSkill();
        run.battle.afterMove();
        run.battle.stepForward();
        run.battle.beforeMove();
        run.battle.processRequests();
        double gainedInTheLoop = run.skillPoints() - before;
        // The Skill itself costs a point; the Eidolon's turn-start point must not be paid again inside the loop.
        Assertions.assertTrue(gainedInTheLoop <= 0,
                "the loop's inserted turn must not pay the 回合开始 point again (gained " + gainedInTheLoop + ")");
    }

    // ==================================================================

    private static final class TurnRun {
        private final Character archer;
        private final Enemy enemy;
        private final Battle battle;

        private TurnRun(int eidolon) {
            archer = CharacterFactory.create(ARCHER, LEVEL, false, null, null, eidolon);
            enemy = BattleFixture.monster();
            battle = new Battle(List.of(archer), List.of(enemy), BattleFixture.alwaysRoll(0.0));
            battle.startBattle();
            battle.processRequests();
        }

        private void castSkill() {
            battle.requestSkill(archer.getSkills().get(SkillType.SKILL), archer, List.of(enemy));
            battle.processRequests();
        }

        private int throwawayTurns() {
            return archer.getBuffManager().allBuffsOf(StateBuff.class).stream()
                    .filter(buff -> "测试".equals(buff.getState()))
                    .mapToInt(AbstractBuff::remainingTurns)
                    .findFirst().orElse(-1);
        }

        private int layers() {
            return archer.getBuffManager().stacksOf(LAYERS);
        }

        private double skillPoints() {
            return battle.getSkillPointPolicy().getValue();
        }
    }
}
