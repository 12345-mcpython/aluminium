package com.laosun.aluminium.test.content.characters.phainon;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.skill.DefaultSkill;
import com.laosun.aluminium.skill.Skill;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
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
public class PhainonEidolonSixTest {
    private static final int PHAINON = 1408;
    private static final int ALLY = 1204;
    private static final String COREFLAME = "火种";
    /**
     * 【支柱•死星天裁】's data slot in {@code skills.json} (140811). It has no {@code SkillType}: the data's slot 11 is
     * not one of the mapped slots, so the case builds the skill by {@code (cid, slot, level)}.
     */
    private static final int STARDEATH_VERDICT_SLOT = 11;
    private static final int BASIC_ATTACK_SLOT = 1;

    /**
     * 「战斗开始时，获得 6 点【火种】」 - on top of the trace's own 1, which is a different clause on the same event.
     */
    @Test
    public void sixPointsOfCoreflameAtBattleStart() {
        int atZero = coreflameAtBattleStart(0);
        int atSix = coreflameAtBattleStart(6);
        System.out.println("[1408] 火种 at battle start: E0 " + atZero + " ; E6 " + atSix
                + " (行迹【行向世界终点】给 1 点，星魂 6 再加 6 点)");
        Assertions.assertEquals(1, atZero, "行迹：战斗开始时获得 1 点【火种】");
        Assertions.assertEquals(7, atSix, "星魂 6：战斗开始时获得 6 点，与行迹那 1 点相加");
    }

    /**
     * 「【火种】的可溢出点数不再拥有上限」 - the declaration's own allowance is 3 over a cap of 12, so E0 stops at 15.
     */
    @Test
    public void theCoreflameOverflowHasNoUpperLimitAtEidolonSix() {
        int atZero = coreflameAfterGaining(0, 100);
        int atSix = coreflameAfterGaining(6, 100);
        System.out.println("[1408] 火种 after gaining 100: E0 " + atZero + " (cap 12 + overflow 3) ; E6 " + atSix);
        Assertions.assertEquals(15, atZero, "E0：溢出额度是声明里的 3 ⇒ 12 + 3 = 15 就停");
        Assertions.assertEquals(107, atSix,
                "星魂 6：可溢出点数不再拥有上限 ⇒ 开场的 7 点再加上这 100 点，一点不丢");
    }

    /**
     * 「施放【支柱•死星天裁】的攻击后，对生命值最高的敌方目标额外造成等同于本次攻击总伤害值 36% 的真实伤害」.
     *
     * <p>⚠ The assertion is the RATIO to what the same cast actually settled, not a predicted number: the cast's own
     * damage comes from the data row (and from a bounce, so its per-hit target is a roll), and the sentence is about
     * <em>that</em> total.
     */
    @Test
    public void theStardeathVerdictPaysAThirdOfItsOwnTotalAsTrueDamage() {
        double atZero = trueDamageAfter(STARDEATH_VERDICT_SLOT, 0);
        double atSix = trueDamageAfter(STARDEATH_VERDICT_SLOT, 6);
        System.out.println("[1408] 真实伤害 after 支柱•死星天裁: E0 " + atZero + " ; E6 " + atSix);
        Assertions.assertEquals(0.0, atZero, 1e-9, "星魂 6 的那一句在 0 星魂时不该生效");
        Assertions.assertTrue(atSix > 0, "precondition: 星魂 6 之后这一发要真的多出一笔真伤 (" + atSix + ")");
    }

    /**
     * And the gate is the SLOT, not "an attack happened": the basic attack must not pay the rider.
     */
    @Test
    public void anotherAttackDoesNotPayTheRider() {
        Assertions.assertEquals(0.0, trueDamageAfter(BASIC_ATTACK_SLOT, 6), 1e-9,
                "「施放【支柱•死星天裁】的攻击后」 - 普攻不是那一招");
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
        Battle battle = newBattle(eidolonRank, 1);
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
        return newBattle(eidolonRank, 1).characters().getFirst().getResources().value(COREFLAME);
    }

    private static int coreflameAfterGaining(int eidolonRank, int amount) {
        Character phainon = newBattle(eidolonRank, 1).characters().getFirst();
        phainon.getResources().gain(COREFLAME, amount);
        return phainon.getResources().value(COREFLAME);
    }

    private static Battle newBattle(int eidolonRank, int enemies) {
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
}
