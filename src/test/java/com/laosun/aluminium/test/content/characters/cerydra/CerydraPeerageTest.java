package com.laosun.aluminium.test.content.characters.cerydra;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.skill.Skill;
import com.laosun.aluminium.skill.SkillExecutor;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.trigger.TriggerTable;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1412 Cerydra (刻律德菈): the 爵位 (Peerage) tier that 【军功】 (Military Merit) upgrades into at six Charge - the promotion
 * itself, and the sentences that only a peer gets: +10.00% All-Type RES PEN, +2% skill CRIT DMG, an extra 20% DEF ignored
 * on Skill DMG, and the removal of the holder's control-class negative states.
 *
 * <p>This file merges the former {@code PeerageUpgradeTest}, {@code PeerageResPenTest}, {@code PeerageCritDamageTest},
 * {@code PeeragePierceDamageTest} and {@code PeerageDispelsControlTest}: every case, every assertion and every helper
 * moved unchanged. The duplicated constants were unified (the former {@code OWNER} is now {@code CERYDRA}), and each
 * former class note sits on the case it explains. The mark before it becomes a peerage is {@link CerydraTest}.
 *
 * <p>L3 consolidation: cases that share one fixture, one clause and one observed object are now one scenario test, and
 * their assertions sit inside {@link Assertions#assertAll} with a message naming the clause. Nothing was dropped: every
 * expected value, tolerance and message is the one the separate case carried, and a rejection path was never merged
 * with a positive one.
 */
public class CerydraPeerageTest {
    private static final int CERYDRA = 1412;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final double EPS = 1e-9;
    private static final String MERIT = "军功";
    private static final String PEERAGE = "爵位";
    private static final String FREEZE = "ctrl:frozen";
    private static final String SHOCK = "电触";

    // ==================================================================
    // The promotion: six Charge upgrades 【军功】 into 【爵位】
    // ==================================================================

    /**
     * 1412 Cerydra (刻律德菈)'s Skill, second half: "when charge reaches 6 points, automatically upgrade the character's [军功] to [爵位]".
     *
     * <p>Nothing new was needed for this: {@code RESOURCE_CHANGED} + {@code self_resource:充能 >= 6} + {@code holder_of:军功}
     * all ship already. Her skill grants 1 charge per cast, so six casts cross the threshold - and five must not, which is
     * the whole point of the case.
     *
     * <p>Note: "a character holding [爵位] is considered to also hold [军功]" is why the rule ADDS Peerage (爵位) and does not remove Merit (军功): the sentence's
     * "counts as holding both" then holds by construction. Note: The same sentence's clause "and removes its control-class negative states" is registered,
     * not written: the engine has no "clear a class of debuffs" capability.
     *
     * <p>Five charges is not six: the state appears exactly on the cast that crosses the threshold.
     */
    @Test
    public void theUpgradeHappensAtSixChargeAndNotBefore() {
        Character cerydra = CharacterFactory.create(CERYDRA, LEVEL, false, null, null, 0);
        Character ally = CharacterFactory.create(ALLY, LEVEL, false, null, null, 0);
        ally.setTriggerTable(new TriggerTable(ALLY, List.of()));
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(cerydra, ally), List.of(enemy), BattleFixture.seeded());
        battle.startBattle();

        for (int cast = 1; cast <= 5; cast++) {
            cerydra.getSkills().get(SkillType.SKILL).execute(battle, cerydra, List.of(ally));
            battle.processRequests();
        }
        boolean meritAfterFive = ally.getBuffManager().hasState(MERIT);
        boolean peerageAfterFive = ally.getBuffManager().hasState(PEERAGE);
        Assertions.assertAll("five charges is below the threshold",
                () -> Assertions.assertTrue(meritAfterFive, "precondition: the ally carries [军功] (Military Merit)"),
                () -> Assertions.assertFalse(peerageAfterFive,
                        "five charges is below the threshold, so no [爵位] (Peerage) yet"));

        cerydra.getSkills().get(SkillType.SKILL).execute(battle, cerydra, List.of(ally));
        battle.processRequests();

        boolean peerageAfterSix = ally.getBuffManager().hasState(PEERAGE);
        boolean meritAfterSix = ally.getBuffManager().hasState(MERIT);
        Assertions.assertAll("the sixth charge promotes the holder",
                () -> Assertions.assertTrue(peerageAfterSix,
                        "the sixth charge upgrades [军功] (Military Merit) to [爵位] (Peerage)"),
                () -> Assertions.assertTrue(meritAfterSix,
                        "and [爵位] (Peerage) COUNTS AS [军功] (Military Merit), so both are on (the rule adds, it does not replace)"));
    }

    // ==================================================================
    // 「持有[爵位]的角色…全属性抗性穿透提高 10.00%」
    // ==================================================================

    /**
     * 1412："持有[爵位]的角色…全属性抗性穿透提高 10.00%".
     *
     * <p>TWO-WAY, file-driven: reaching six Charge promotes the merit holder, and the promotion is what carries the 10%. One
     * cast gives the merit but not the peerage, so the control is the same sentence one step earlier; five Charge is still
     * short of the threshold, so the sentence has not started yet.
     *
     * <p>At six Charge the holder is a peer, and being a peer is worth exactly 10% more All-Type RES PEN.
     */
    @Test
    public void thePeerHoldsTenPercentMoreResPen() {
        double oneCast = resPenAfter(1);
        double fiveCasts = resPenAfter(5);
        double sixCasts = resPenAfter(6);
        Assertions.assertAll("「持有[爵位]的角色…全属性抗性穿透提高 10.00%」",
                () -> Assertions.assertEquals(0.10, sixCasts - oneCast, EPS,
                        "\"a character holding Peerage (【爵位】) ... gains 10.00% All-Type RES PEN\" (「持有【爵位】的角色…全属性抗性穿透提高 10.00%」)"),
                () -> Assertions.assertEquals(oneCast, fiveCasts, EPS,
                        "below six Charge the sentence has not started"));
    }

    // ==================================================================
    // 「持有[爵位]的角色…造成的战技伤害的暴击伤害提高 2%」
    // ==================================================================

    /**
     * 1412："持有[爵位]的角色…造成的战技伤害的暴击伤害提高 2%".
     *
     * <p>The assertion is about the CRIT MULTIPLIER, not about raw damage: the peerage also carries +16% DEF ignore, +10%
     * All-Type RES PEN and +20% pierce on skill damage, and all three raise crit and non-crit hits alike -- so dividing them out
     * leaves exactly what a +2% CRIT DMG changes. Both runs make the same six casts; the control removes only [爵位].
     *
     * <p>Note: The crit is forced, not hoped for: the Random returns 0.0 for a crit and 1.0 for "never crits" (the idiom
     * `AnchorDeathTest` and `FeixiaoFollowUpCritTest` use).
     *
     * <p>The peer's skill crits harder than the same skill without the peerage.
     */
    @Test
    public void thePeerCritsHarderOnSkillDamage() {
        double withPeer = critRatio(true);
        double without = critRatio(false);
        Assertions.assertAll("「造成的战技伤害的暴击伤害提高 2%」",
                () -> Assertions.assertTrue(without > 1.0, "precondition: a crit really is bigger than a non-crit (" + without + ")"),
                () -> Assertions.assertTrue(withPeer > without,
                        "「战技伤害的暴击伤害提高 72%」 (Skill DMG's CRIT DMG is raised by 72%) (crit/non-crit: with=" + withPeer + ", without=" + without + ")"));
    }

    // ==================================================================
    // 「若已升为[爵位]…该角色造成战技伤害时额外无视 20% 防御」
    // ==================================================================

    /**
     * 1412："若已升为[爵位]…该角色造成战技伤害时额外无视 20% 防御".
     *
     * <p>SAME SCENE, ONE VARIABLE: both runs reach six Charge (so the merit holder is a peer), cast the same skill at the same
     * enemy; the control then removes [爵位]. What is compared is the DAMAGE -- because `self_attr:` reads the sheet, and a
     * sentence about "when dealing Skill DMG" lives in one settled instance.
     *
     * <p>A peer's skill hits harder than the same skill without the peerage.
     */
    @Test
    public void thePeerPiercesMoreOnSkillDamage() {
        double withPeer = damageDealt(true);
        double without = damageDealt(false);
        Assertions.assertAll("「造成战技伤害时额外无视 20% 防御」",
                () -> Assertions.assertTrue(without > 0, "precondition: the control deals damage (" + without + ")"),
                () -> Assertions.assertTrue(withPeer > without,
                        "「额外无视 20% 防御」 (ignores an extra 20% DEF) (with=" + withPeer + ", without=" + without + ")"));
    }

    // ==================================================================
    // The upgrade strips the holder's control-class negative states
    // ==================================================================

    /**
     * 1412: "when charge reaches 6 points, automatically upgrade the character's [军功] to [爵位] and remove their control-class negative states".
     *
     * <p>THE TWO-WAY THAT PROVES "control class" AND NOT "any debuff": the ally carries Frozen (a control, per the document's
     * own glossary) AND Shock (dot:shock) (a DOT, not a control). Promotion must remove the first and LEAVE THE SECOND -- a `DISPEL` would
     * have wiped both, which is exactly the approximation this judge exists to catch. Below the threshold there is no
     * promotion, so the control stays.
     *
     * <p>Note: The ally's table is rebuilt here on purpose. GAPS records that a rebuilt table drops `level_convention` and once caused a
     * factor-2 misreading -- but that trap is about DAMAGE numbers, and this judge reads STATES only.
     *
     * <p>Promotion strips the control and keeps everything else.
     */
    @Test
    public void promotionDispelsTheControlOnly() {
        boolean frozenAtSix = stateAfter(FREEZE, 6);
        boolean shockAtSix = stateAfter(SHOCK, 6);
        boolean frozenAtOne = stateAfter(FREEZE, 1);
        Assertions.assertAll("「remove their control-class negative states」, and only those",
                () -> Assertions.assertFalse(frozenAtSix,
                        "「remove their control-class negative states」 -- Frozen must be gone"),
                () -> Assertions.assertTrue(shockAtSix, "Note: a DOT is not a control, so it must survive"),
                () -> Assertions.assertTrue(frozenAtOne, "one cast is not a promotion"));
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    /**
     * The merit holder's All-Type RES PEN after {@code casts} Skill casts; the preconditions pin the promotion to cast six.
     */
    private static double resPenAfter(int casts) {
        Character owner = CharacterFactory.create(CERYDRA, 80);
        Character ally = CharacterFactory.create(ALLY, 80);
        Battle battle = new Battle(List.of(owner, ally),
                List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();

        Skill skill = owner.getSkills().get(SkillType.SKILL);
        Assertions.assertNotNull(skill, "precondition: she has a skill");
        for (int i = 0; i < casts; i++) {
            SkillExecutor.execute(battle, skill, owner, List.of(ally));
            battle.processRequests();
        }
        Assertions.assertTrue(ally.getBuffManager().hasState("军功"),
                "precondition: the ally holds the merit");
        Assertions.assertEquals(casts >= 6, ally.getBuffManager().hasState("爵位"),
                "precondition: six casts is what promotes the holder");
        return ally.getAttribute(AttributeType.DAMAGE_PENETRATION).get();
    }

    /**
     * forced-crit damage divided by never-crit damage, same scene, same six casts.
     */
    private static double critRatio(boolean keepPeerage) {
        double crit = damage(keepPeerage, true);
        double plain = damage(keepPeerage, false);
        Assertions.assertTrue(plain > 0, "precondition: the never-crit run deals damage");
        return crit / plain;
    }

    private static double damage(boolean keepPeerage, boolean forceCrit) {
        Character owner = CharacterFactory.create(CERYDRA, 80);
        Character ally = CharacterFactory.create(ALLY, 80);
        Battle battle = new Battle(List.of(owner, ally),
                List.of(BattleFixture.monster()), new Random() {
            @Override
            public double nextDouble() {
                return forceCrit ? 0.0 : 1.0;
            }
        });
        battle.startBattle();
        battle.processRequests();

        Skill hers = owner.getSkills().get(SkillType.SKILL);
        for (int i = 0; i < 6; i++) {
            SkillExecutor.execute(battle, hers, owner, List.of(ally));
            battle.processRequests();
        }
        Assertions.assertTrue(ally.getBuffManager().hasState(PEERAGE), "precondition: six casts promote");
        if (!keepPeerage) {
            ally.getBuffManager().removeState(PEERAGE);
            battle.processRequests();
        }
        double before = battle.enemies().get(0).getCurrentHp();
        Skill his = ally.getSkills().get(SkillType.SKILL);
        SkillExecutor.execute(battle, his, ally, List.of(battle.enemies().get(0)));
        battle.processRequests();
        return before - battle.enemies().get(0).getCurrentHp();
    }

    private static double damageDealt(boolean keepPeerage) {
        Character owner = CharacterFactory.create(CERYDRA, 80);
        Character ally = CharacterFactory.create(ALLY, 80);
        Battle battle = new Battle(List.of(owner, ally),
                List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();

        Skill hers = owner.getSkills().get(SkillType.SKILL);
        for (int i = 0; i < 6; i++) {
            SkillExecutor.execute(battle, hers, owner, List.of(ally));
            battle.processRequests();
        }
        Assertions.assertTrue(ally.getBuffManager().hasState("军功"),
                "precondition: the ally holds the merit");
        Assertions.assertTrue(ally.getBuffManager().hasState(PEERAGE),
                "precondition: six casts promote the holder");
        if (!keepPeerage) {
            ally.getBuffManager().removeState(PEERAGE);
            battle.processRequests();
        }

        double before = battle.enemies().get(0).getCurrentHp();
        Skill his = ally.getSkills().get(SkillType.SKILL);
        Assertions.assertNotNull(his, "precondition: the ally has a skill");
        SkillExecutor.execute(battle, his, ally, List.of(battle.enemies().get(0)));
        battle.processRequests();
        return before - battle.enemies().get(0).getCurrentHp();
    }

    private static boolean stateAfter(String state, int casts) {
        Character owner = CharacterFactory.create(CERYDRA, 80);
        Character ally = CharacterFactory.create(ALLY, 80);
        Battle battle = new Battle(List.of(owner, ally),
                List.of(BattleFixture.monster()), BattleFixture.seeded());

        // Note: The ally's own table is replaced: this judge needs the ally to CARRY a control and a DOT, and no shipped file
        // gives it one. The state names are the document's (Frozen from the control list, Shocked as a non-control debuff).
        ally.setTriggerTable(new TriggerTable(ALLY, List.of(
                TriggerSpecs.rule(TriggerEvent.BATTLE_START.name(), List.of(),
                        permanent(FREEZE), permanent(SHOCK)))));

        battle.startBattle();
        battle.processRequests();
        Assertions.assertTrue(ally.getBuffManager().hasState(FREEZE), "precondition: the control landed");
        Assertions.assertTrue(ally.getBuffManager().hasState(SHOCK), "precondition: the DOT landed");

        Skill skill = owner.getSkills().get(SkillType.SKILL);
        for (int i = 0; i < casts; i++) {
            SkillExecutor.execute(battle, skill, owner, List.of(ally));
            battle.processRequests();
        }
        if (casts >= 6) {
            Assertions.assertTrue(ally.getBuffManager().hasState(PEERAGE), "precondition: six casts promote");
        }
        return ally.getBuffManager().hasState(state);
    }

    private static EffectSpec permanent(String state) {
        EffectSpec e = new EffectSpec();
        TriggerSpecs.set(e, "op", "APPLY_BUFF");
        TriggerSpecs.set(e, "buff", state);
        TriggerSpecs.set(e, "permanent", true);
        TriggerSpecs.set(e, "target", "self");
        return e;
    }
}
