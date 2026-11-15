package com.laosun.aluminium.test.content.characters.phainon;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.Constant;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.skill.DefaultSkill;
import com.laosun.aluminium.skill.Skill;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 1408 Phainon (白厄): the Eidolons that move a number - the level Eidolons (3 and 5), Eidolon 1's critical-damage gate and the
 * killing-blow heal beside it, Eidolon 2's RES PEN gate, Eidolon 6's three clauses, and the slot-5 row drift witness that stands
 * in for the row a level-raising Eidolon cannot yet name.
 *
 * <p>Consolidated (P2) from five sibling classes; each source's class doc is kept, as a plain comment, at the head of the group it
 * supplied. L3 then folded the cases that read one clause on one board into scenario tests ({@code Assertions.assertAll}), one
 * message per clause reading; every expectation, tolerance and message is unchanged.
 */
public class PhainonEidolonsTest {
    private static final int PHAINON = 1408;
    private static final int ALLY = 1204;
    private static final String TRANSFORMED = "char:1408/transformation";
    private static final String COREFLAME = "char:1408/ember";
    /**
     * 【支柱•死星天裁】's data slot in {@code skills.json} (140811). It has no {@code SkillType}: the data's slot 11 is
     * not one of the mapped slots, so the case builds the skill by {@code (cid, slot, level)}.
     */
    private static final int STARDEATH_VERDICT_SLOT = 11;
    private static final int BASIC_ATTACK_SLOT = 1;
    private static final int SLOT_FIVE = 5;
    private static final int COLUMN_ATK = 3;
    private static final int COLUMN_HP = 4;

    // ==================================================================
    // Former class: PhainonEidolonLevelsTest
    // ==================================================================
    /*
     * Character 1408 (Phainon 白厄): his Eidolons 3 and 5 raise skill levels, and until the 2026-10-19 audit <b>no rule did it</b> -
     * the silence was the symptom: 「终结技等级+2」 simply did nothing.
     *
     * <p>⚠ Both cases read a LEVEL, so they are written at all three ranks: E0 (the convention's row), E3 (ULTRA +2) and E5 (SKILL
     * and TALENT +2). The common attack is pinned too, because its raise is CLAMPED by its own 10-row table - "最多不超过 10 级"
     * is the data's doing, not a second op.
     */

    /**
     * The rows each Eidolon actually moves.
     */
    @Test
    public void theEidolonsRaiseTheSlotsTheyName() {
        int[] atZero = levelsAt(0);
        int[] atThree = levelsAt(3);
        int[] atFive = levelsAt(5);
        System.out.println("[1408] levels COMMON/SKILL/ULTRA/TALENT: E0 " + shown(atZero) + " ; E3 " + shown(atThree)
                + " ; E5 " + shown(atFive));
        Assertions.assertArrayEquals(new int[]{6, 10, 10, 10}, atZero, "等级口径：普攻钉第 6 行，其余三槽第 10 行");
        Assertions.assertArrayEquals(new int[]{7, 10, 12, 10}, atThree,
                "星魂 3：终结技+2 ⇒ 第 12 行；普攻 6 ⇒ 7");
        Assertions.assertArrayEquals(new int[]{7, 12, 12, 12}, atFive,
                "星魂 5：战技+2、天赋+2 ⇒ 各第 12 行（终结技的 +2 来自星魂 3，仍在）");
    }

    /**
     * 「若施放者为白厄的队友，还会使白厄的暴击伤害提高 30%」 - the share is the TALENT row's #1, and E5 moves that row.
     */
    @Test
    public void theTalentCritDamageFollowsTheTalentRow() {
        double atZero = critDamageFromAnAlliesAbility(0);
        double atFive = critDamageFromAnAlliesAbility(5);
        System.out.println("[1408] CRIT_ATTACK from an ally's ability: E0 +" + atZero + " ; E5 +" + atFive
                + " (the Lv10 literal would read +0.3 at both)");
        Assertions.assertEquals(0.3, atZero, 1e-9, "天赋第 10 行的 #1 = 30%");
        Assertions.assertEquals(0.33, atFive, 1e-9, "星魂 5 给天赋 +2 级 ⇒ 第 12 行的 #1 = 33%");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private static String shown(int[] levels) {
        return "C=" + levels[0] + " S=" + levels[1] + " U=" + levels[2] + " T=" + levels[3];
    }

    /**
     * COMMON, SKILL, ULTRA and TALENT's EFFECTIVE levels (base plus every raise), in that order.
     */
    private static int[] levelsAt(int eidolonRank) {
        Character phainon = newBattle(eidolonRank).characters().getFirst();
        return new int[]{
                phainon.skillLevel(phainon.getSkills().get(SkillType.COMMON)),
                phainon.skillLevel(phainon.getSkills().get(SkillType.SKILL)),
                phainon.skillLevel(phainon.getSkills().get(SkillType.ULTRA)),
                phainon.skillLevel(phainon.getSkills().get(SkillType.TALENT))};
    }

    /**
     * Her CRIT DMG gain when a TEAMMATE's ability targets him - the event the talent's second half hangs on.
     */
    private static double critDamageFromAnAlliesAbility(int eidolonRank) {
        Battle battle = newBattle(eidolonRank);
        Character phainon = battle.characters().getFirst();
        Character ally = battle.characters().get(1);
        double before = phainon.getAttribute(AttributeType.CRIT_ATTACK).get();
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.SKILL_CAST, ally, phainon, 0, 0));
        battle.processRequests();
        return phainon.getAttribute(AttributeType.CRIT_ATTACK).get() - before;
    }

    /**
     * Note: Shared with the Eidolon-1 group below - both former classes built the very same board (Phainon at the given rank
     * beside 1204, one monster, `alwaysRoll(0.9999)`), so the two copies are one method here.
     */
    private static Battle newBattle(int eidolonRank) {
        Battle battle = new Battle(
                List.of(CharacterFactory.create(PHAINON, 80, false, null, null, eidolonRank),
                        CharacterFactory.create(ALLY, 80)),
                List.of(BattleFixture.monster()), BattleFixture.alwaysRoll(0.9999));
        battle.startBattle();
        battle.processRequests();
        return battle;
    }

    // ==================================================================
    // Former class: PhainonEidolonOneTest
    // ==================================================================
    /*
     * Character 1408 (Phainon 白厄): the two numbers the 2026-10-19 audit corrected - Eidolon 1's CRIT DMG needed its gate, and
     * Khaslana's killing-blow heal is 25% of Max HP and not 20%.
     *
     * <p>⚠ Both cases are written so the defect they fix can go red: the CRIT DMG is asserted at BOTH Eidolon ranks (a clause that
     * applies for free and one that is correctly gated differ only at E0), and the heal is asserted against the share the
     * document states rather than against "it healed".
     */

    /**
     * 「施放终结技时，暴击伤害提高 50%，持续 3 回合」 is <b>Eidolon 1</b>'s second half - so E0 must not have it.
     */
    @Test
    public void theCriticalDamageIsGatedByEidolonOne() {
        double atZero = criticalDamageGainFromTheUltimate(0);
        double atOne = criticalDamageGainFromTheUltimate(1);
        System.out.println("[1408] CRIT_ATTACK gain from the Ultimate: E0 " + atZero + " ; E1 " + atOne);
        Assertions.assertEquals(0.0, atZero, 1e-9,
                "星魂 1 的后半句在 0 星魂时不该生效 - 它此前被记成行迹，于是白拿 +50%");
        Assertions.assertEquals(0.5, atOne, 1e-9, "星魂 1：施放终结技时暴击伤害提高 50%");
    }

    /**
     * 「受到致命攻击时不会陷入无法战斗状态，而是回复等同于自身生命上限 25% 的生命值」.
     *
     * <p>⚠ The number, not the event: the sentence's own data row agrees (the enhanced talent's #2 = 0.25 while #7 = 0.2 is the
     * <em>other</em> clause - 「施放攻击后回复 20%」, which is a different rule). Paying 0.2 here is what the audit corrected,
     * because that row's two numbers had been swapped.
     */
    @Test
    public void theKillingBlowHealsAQuarterOfMaxHp() {
        Battle battle = newBattle();
        Character phainon = battle.characters().getFirst();
        phainon.setCurrentEnergy(phainon.getMaxEnergy());
        battle.castUltra(phainon, List.of(battle.enemyUnits().getFirst()));
        battle.processRequests();
        Assertions.assertTrue(phainon.getBuffManager().hasState(TRANSFORMED), "precondition: the Ultimate transforms him");

        double maxHp = phainon.getMaxHp();
        // Note: He is brought to half HP rather than to 0, and the reading is the DELTA: a heal is truncated at Max HP, so a
        // full-HP fixture would read 0 whatever the share is - and a raw "set HP to 0" write is not an API this engine has.
        phainon.consumeHp(maxHp * 0.5);
        double before = phainon.getCurrentHp();
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.LETHAL_DAMAGE, phainon, phainon, 0, 0));
        battle.processRequests();
        double healed = phainon.getCurrentHp() - before;
        System.out.println("[1408] a killing blow during 【char:1408/transformation】 heals " + healed + " of " + maxHp
                + " (" + (healed / maxHp) + ")");
        Assertions.assertEquals(0.25, healed / maxHp, 1e-9,
                "文档 `:189`：回复等同于自身生命上限 25% 的生命值（数据同一行的 #2 = 0.25）");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private static double criticalDamageGainFromTheUltimate(int eidolonRank) {
        Battle battle = newBattle(eidolonRank);
        Character phainon = battle.characters().getFirst();
        double before = phainon.getAttribute(AttributeType.CRIT_ATTACK).get();
        phainon.setCurrentEnergy(phainon.getMaxEnergy());
        battle.castUltra(phainon, List.of(battle.enemyUnits().getFirst()));
        battle.processRequests();
        return phainon.getAttribute(AttributeType.CRIT_ATTACK).get() - before;
    }

    private static Battle newBattle() {
        return newBattle(0);
    }

    // ==================================================================
    // Former class: PhainonEidolonTwoTest
    // ==================================================================
    /*
     * Character 1408 (Phainon 白厄): Eidolon 2's first half - 「卡厄斯兰那的物理属性<b>抗性穿透</b>提高 20%」.
     *
     * <p>⚠⚠ <b>This clause was in the file already, attributed to a TRACE and therefore ungated</b> - so a 0-Eidolon Phainon
     * carried +20% physical RES PEN. Two independent sources say it is Eidolon 2's: {@code eidolons.json} 140806's sibling
     * rank 140802 has {@code param = [4, 0.2]} with that exact text ({@code #2 = 0.2}), and his kit has only <b>three</b>
     * traces ({@code 1408101} 火种 / {@code 1408102} 受伤增伤 / {@code 1408103} 攻击力) - none of them mentions RES PEN.
     *
     * <p>⚠ The case is written at BOTH ranks: a clause that applies for free and one that is correctly gated differ only at
     * E0. E2's <b>second</b> half (「单次施放【支柱•死星天裁】消耗【毁伤】点数达到 4 点时，获得 1 个额外回合」) is NOT here -
     * the engine has no "how much 【毁伤】 did this cast spend" fact at all; it is registered in the file's
     * {@code ult_transformation} note and in {@code level_convention}'s per-Eidolon table.
     */

    /**
     * E0 must not have the penetration; E2 must have exactly the 20% the data's {@code #2} states.
     */
    @Test
    public void thePhysicalResistancePenetrationIsGatedByEidolonTwo() {
        double atZero = penetrationWhileTransformed(0);
        double atTwo = penetrationWhileTransformed(2);
        System.out.println("[1408] DAMAGE_PENETRATION while transformed: E0 " + atZero + " ; E2 " + atTwo);
        Assertions.assertEquals(0.0, atZero, 1e-9,
                "星魂 2 的前半句在 0 星魂时不该生效 - 它此前被记成行迹，于是白拿 +20% 物理抗性穿透");
        Assertions.assertEquals(0.2, atTwo, 1e-9, "星魂 2：卡厄斯兰那的物理属性抗性穿透提高 20%");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    /**
     * His RES PEN read <b>while transformed</b>, which is the clause's scope (卡厄斯兰那 is the transformed form).
     */
    private static double penetrationWhileTransformed(int eidolonRank) {
        Battle battle = new Battle(
                List.of(CharacterFactory.create(PHAINON, 80, false, null, null, eidolonRank),
                        CharacterFactory.create(ALLY, 80)),
                List.of(BattleFixture.monster()), BattleFixture.alwaysRoll(0.9999));
        battle.startBattle();
        battle.processRequests();
        Character phainon = battle.characters().getFirst();
        phainon.setCurrentEnergy(phainon.getMaxEnergy());
        battle.castUltra(phainon, List.of(battle.enemyUnits().getFirst()));
        battle.processRequests();
        Assertions.assertTrue(phainon.getBuffManager().hasState(TRANSFORMED),
                "precondition: the Ultimate transforms him, which is the clause's scope");
        return phainon.getAttribute(AttributeType.DAMAGE_PENETRATION).get();
    }

    // ==================================================================
    // Former class: PhainonEidolonSixTest
    // ==================================================================
    /*
     * Character 1408 (Phainon 白厄), <b>Eidolon 6</b> 「亘古长升，蚀火残阳」 - three clauses, and before this batch none of them
     * existed ({@code level_convention}'s registry said 「E2~E6 整段未读」):
     *
     * <ol>
     *   <li>「【火种】的可溢出点数不再拥有上限」;</li>
     *   <li>「战斗开始时，获得 6 点【火种】」;</li>
     *   <li>「施放【支柱•死星天裁】的攻击后，对生命值最高的敌方目标额外造成等同于本次攻击总伤害值 36% 的真实伤害」.</li>
     * </ol>
     *
     * <p>⚠ Every case is pinned at BOTH ranks (E0 / E6): each of the three is a clause that must be silent below the Eidolon.
     * ⚠ The third one is also pinned against the WRONG SKILL - the same rider must not appear after the basic attack, which is
     * the only way to see that the gate is the slot and not "some attack happened".
     */

    /**
     * Eidolon 6's two 【火种】 clauses on one board: 「战斗开始时，获得 6 点【火种】」 - on top of the trace's own 1, a different
     * clause on the same event - and 「【火种】的可溢出点数不再拥有上限」, while the declaration's own allowance is 3 over a cap
     * of 12, so E0 stops at 15.
     */
    @Test
    public void theCoreflameStartAndItsUnboundedOverflowComeWithEidolonSix() {
        int startAtZero = coreflameAtBattleStart(0);
        int startAtSix = coreflameAtBattleStart(6);
        int overflowAtZero = coreflameAfterGaining(0, 100);
        int overflowAtSix = coreflameAfterGaining(6, 100);
        System.out.println("[1408] char:1408/ember at battle start: E0 " + startAtZero + " ; E6 " + startAtSix
                + " (行迹【行向世界终点】给 1 点，星魂 6 再加 6 点)");
        System.out.println("[1408] char:1408/ember after gaining 100: E0 " + overflowAtZero + " (cap 12 + overflow 3) ; E6 " + overflowAtSix);
        Assertions.assertAll("星魂 6 的两条【char:1408/ember】句",
                () -> Assertions.assertEquals(1, startAtZero, "行迹：战斗开始时获得 1 点【char:1408/ember】"),
                () -> Assertions.assertEquals(7, startAtSix, "星魂 6：战斗开始时获得 6 点，与行迹那 1 点相加"),
                () -> Assertions.assertEquals(15, overflowAtZero, "E0：溢出额度是声明里的 3 ⇒ 12 + 3 = 15 就停"),
                () -> Assertions.assertEquals(107, overflowAtSix,
                        "星魂 6：可溢出点数不再拥有上限 ⇒ 开场的 7 点再加上这 100 点，一点不丢"));
    }

    /**
     * 「施放【支柱•死星天裁】的攻击后，对生命值最高的敌方目标额外造成等同于本次攻击总伤害值 36% 的真实伤害」 - paid at 星魂 6
     * and not at E0; and the gate is the SLOT, not "an attack happened", so the basic attack must not pay the rider.
     *
     * <p>⚠ The assertion is the RATIO to what the same cast actually settled, not a predicted number: the cast's own
     * damage comes from the data row (and from a bounce, so its per-hit target is a roll), and the sentence is about
     * <em>that</em> total.
     */
    @Test
    public void theStardeathVerdictRiderIsGatedByTheEidolonAndTheSlot() {
        double atZero = trueDamageAfter(STARDEATH_VERDICT_SLOT, 0);
        double atSix = trueDamageAfter(STARDEATH_VERDICT_SLOT, 6);
        double afterBasicAttack = trueDamageAfter(BASIC_ATTACK_SLOT, 6);
        System.out.println("[1408] 真实伤害 after 支柱•死星天裁: E0 " + atZero + " ; E6 " + atSix);
        Assertions.assertAll("星魂 6 的真实伤害附加",
                () -> Assertions.assertEquals(0.0, atZero, 1e-9, "星魂 6 的那一句在 0 星魂时不该生效"),
                () -> Assertions.assertTrue(atSix > 0, "precondition: 星魂 6 之后这一发要真的多出一笔真伤 (" + atSix + ")"),
                () -> Assertions.assertEquals(0.0, afterBasicAttack, 1e-9,
                        "「施放【支柱•死星天裁】的攻击后」 - 普攻不是那一招"));
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    /**
     * The share the rider actually paid, measured through the damage observer as
     * {@code true instances / every other instance of the same cast}.
     */
    private static double trueDamageAfter(int slot, int eidolonRank) {
        // Note: ONE enemy. With several, a `BOUNCE` re-draws its target from an unseeded `Random` (`alwaysRoll` pins only
        // `nextDouble`), so which victim takes how much varies between runs - and with a single target it cannot.
        Battle battle = newDummyBattle(eidolonRank, 1);
        Character phainon = battle.characters().getFirst();
        List<Damage> settled = new ArrayList<>();
        battle.addDamageObserver((damage, applied, hpLoss, shieldAbsorbed, died) -> settled.add(damage));
        Skill skill = slot == BASIC_ATTACK_SLOT
                ? phainon.getSkills().get(com.laosun.aluminium.enums.SkillType.COMMON)
                : new DefaultSkill(PHAINON, slot, 12);
        // Note: 一律瞄准第一个敌人；`BOUNCE` 每一发重新随机目标，而判据只看**合计**，不看落在谁身上。
        battle.castImmediate(skill, phainon, List.of(battle.enemyUnits().getFirst()));
        battle.processRequests();
        double total = settled.stream().filter(d -> d.getType() != DamageType.TRUE)
                .mapToDouble(d -> d.breakdown().get("final")).sum();
        double trues = settled.stream().filter(d -> d.getType() == DamageType.TRUE)
                .mapToDouble(d -> d.breakdown().get("final")).sum();
        if (trues == 0) {
            return 0;
        }
        System.out.println("[1408] slot " + slot + " settled " + total + " of ordinary damage and " + trues
                + " of true damage (" + (trues / total) + " of the total)");
        // 36% of the settled total, and the true instance is exact (it skips every zone), so the tolerance is the sum's.
        // Note: the per-instance breakdown rides in the message on purpose - when this case failed in a full-suite run
        // (order-dependent, not reproducible alone), the sums alone could not say WHICH instance had drifted.
        String instances = settled.stream()
                .map(d -> d.getType() + " " + d.breakdown())
                .reduce("", (a, b) -> a + "\n    " + b);
        Assertions.assertEquals(0.36, trues / total, Math.max(1e-9, 1e-6 / total),
                "「等同于本次攻击总伤害值 36% 的真实伤害」: total=" + total + " true=" + trues + instances);
        return trues / total;
    }

    private static int coreflameAtBattleStart(int eidolonRank) {
        return newDummyBattle(eidolonRank, 1).characters().getFirst().getResources().value(COREFLAME);
    }

    private static int coreflameAfterGaining(int eidolonRank, int amount) {
        Character phainon = newDummyBattle(eidolonRank, 1).characters().getFirst();
        phainon.getResources().gain(COREFLAME, amount);
        return phainon.getResources().value(COREFLAME);
    }

    /**
     * Note: Renamed from the second `newBattle` of this group so the two boards (monster versus dummy) cannot be confused:
     * this one is the dummy board the ratio and the Kindling readings need, with the enemy count made explicit.
     */
    private static Battle newDummyBattle(int eidolonRank, int enemies) {
        List<com.laosun.aluminium.enemy.Enemy> foes = new ArrayList<>();
        for (int i = 0; i < enemies; i++) {
            // Note: a 1,000,000-HP, 0-DEF dummy, and no crits. Both are load-bearing for the ratio this class asserts:
            //   * a REAL monster can be over-killed, and the engine accumulates what the victim TOOK while the observer
            //     reports what the instance was WORTH - two different numbers on the killing blow;
            //   * a crit multiplies one instance of the cast but not the true rider (true damage never crits), so the
            //     sum of the instances stops being the number the rider was computed from.
            // Measured with both in play: 0.308 and 0.324 instead of 0.36. With a dummy and `alwaysRoll`, the sum and the
            // engine's total are the same number by construction, so the case is also fully deterministic.
            foes.add(BattleFixture.dummy());
        }
        Battle battle = new Battle(
                List.of(CharacterFactory.create(PHAINON, 80, false, null, null, eidolonRank),
                        CharacterFactory.create(ALLY, 80)),
                foes, BattleFixture.alwaysRoll(0.9999));
        battle.startBattle();
        battle.processRequests();
        return battle;
    }

    // ==================================================================
    // Former class: PhainonSlotFiveRowDriftTest
    // ==================================================================
    /*
     * 1408: the transformation's ATK / Max HP block is written with the row's Lv10 numbers ({@code 0.8} / {@code 2.7}), and
     * <b>Eidolon 5 raises that very row by two levels</b>.
     *
     * <p>⚠⚠ <b>This is a GAP WITNESS, not a pass.</b> The block lives in data slot <b>5</b> ({@code 140805}, 卡厄斯兰那's
     * talent), and slot 5 has no {@code SkillType} - the mapped slots are 1 / 2 / 3 / 4 / 6 / 7 / 20 / 21 (see
     * {@code SkillSlotMappingTest}). So {@code percent_from_skill_param} / {@code percent_from_cast_param} cannot name it, and
     * the rule keeps the Lv10 literal while the effective level becomes 12: row 10 says {@code [4, 0.25, 0.125, 0.8, 2.7, ...]}
     * and row 12 says {@code [4, 0.25, 0.125, 0.88, 2.97, ...]}.
     *
     * <p>Why a witness rather than a fix: reading "the row of a slot that has no SkillType" is a capability, not a number, and
     * the three other slots in the same position ({@code 140808} 强化普攻, {@code 140809} / {@code 140811} 强化战技) need it too.
     * It is registered in {@code AGENT.md} §4.3; when it lands, THIS case is what goes red and points at the file to update.
     *
     * <p>⚠ What is measured is the DELTA the block adds, at E0 and at E5 with the same starting board - so the comparison
     * needs no assumption about the base attribute or about the trace that also raises ATK. Measured: the two deltas are equal,
     * which is exactly the defect: a rule reading the row would have gained 10% (0.88 / 0.8).
     */

    /**
     * At E5 the slot-5 row moves to 0.88 / 2.97, and the applied block is still the Lv10 literal 0.8 / 2.7 - because the slot
     * cannot be named at all, which is the reason: no {@link SkillType} maps to 5.
     */
    @Test
    public void theSlotFiveRowDriftIsInvisibleBecauseTheSlotHasNoSkillType() {
        // Note: the row is read at the level the engine would use if slot 5 could be named: 10 (the convention) + 2 (E5).
        List<Double> rowTwelve = new DefaultSkill(PHAINON, SLOT_FIVE, 12).getData().getSkills().get(11);
        System.out.println("[1408] slot 5 row 12 = " + rowTwelve);

        double[] atZero = appliedBlock(0);
        double[] atFive = appliedBlock(5);
        System.out.println("[1408] char:1408/transformation块落下的增量: E0 ATK +" + atZero[0] + " / HEALTH +" + atZero[1]
                + " ; E5 ATK +" + atFive[0] + " / HEALTH +" + atFive[1]
                + " (第 12 行说 0.88 / 2.97，字面量是 0.8 / 2.7)");
        Assertions.assertAll("槽 5 的行漂移见证",
                () -> Assertions.assertEquals(0.88, rowTwelve.get(COLUMN_ATK), 1e-9,
                        "星魂 5 给槽 5（140805）+2 级 ⇒ 第 12 行的 #4 = 0.88"),
                () -> Assertions.assertEquals(2.97, rowTwelve.get(COLUMN_HP), 1e-9, "同一行的 #5 = 2.97"),
                () -> Assertions.assertEquals(atZero[0], atFive[0], 1e-9,
                        "今天落下的是 Lv10 的字面量 ⇒ 两档的增量一样（缺口：第 12 行是 0.88，应多 10%）"),
                () -> Assertions.assertEquals(atZero[1], atFive[1], 1e-9,
                        "生命上限同理：字面量 2.7，而第 12 行是 2.97"),
                () -> {
                    for (SkillType type : SkillType.values()) {
                        Assertions.assertNotEquals(SLOT_FIVE, Constant.SKILL_SLOT.get(type),
                                "谁把槽 5 映射进 SkillType，这条见证就该改成真判据");
                    }
                });
    }

    // ==================================================================

    /**
     * The ATK and Max HP the transformation's block adds, in points, on an identical starting board.
     */
    private static double[] appliedBlock(int eidolonRank) {
        Battle battle = new Battle(
                List.of(CharacterFactory.create(PHAINON, 80, false, null, null, eidolonRank),
                        CharacterFactory.create(ALLY, 80)),
                List.of(BattleFixture.monster()), BattleFixture.alwaysRoll(0.9999));
        battle.startBattle();
        battle.processRequests();
        Character phainon = battle.characters().getFirst();
        double atkBefore = phainon.getAttribute(AttributeType.ATTACK).get();
        double hpBefore = phainon.getAttribute(AttributeType.HEALTH).get();
        phainon.setCurrentEnergy(phainon.getMaxEnergy());
        battle.castUltra(phainon, List.of(battle.enemyUnits().getFirst()));
        battle.processRequests();
        Assertions.assertTrue(phainon.getBuffManager().hasState("char:1408/transformation"), "precondition: the transformation is on");
        return new double[]{
                phainon.getAttribute(AttributeType.ATTACK).get() - atkBefore,
                phainon.getAttribute(AttributeType.HEALTH).get() - hpBefore};
    }
}
