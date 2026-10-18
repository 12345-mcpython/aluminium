package com.laosun.aluminium.test.content.characters.hyacine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.skill.SkillExecutor;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * 1409 风堇's trace 【暴风停歇】 (1409103) and Rank 4, which strengthens it.
 *
 * <pre>
 * trace  : 「风堇的速度大于 200 时，她与小伊卡的生命上限提高 20%，之后每超出 1 点速度，
 *           风堇与小伊卡提供的治疗量提高 1%，最多计入 200 点超出的速度。」
 * Rank 4 : 「行迹【暴风停歇】获得强化，每超过 1 点速度，风堇和小伊卡的暴击伤害额外提高 2%。」
 * </pre>
 *
 * <p>Four things are being separated, and each has bitten this repository in another coat:
 * <ul>
 *   <li>the gate is <b>strictly</b> above 200 (「大于」), so 200 exactly must give nothing;</li>
 *   <li>the healing share is bounded by <b>points</b> (「最多计入 200 点超出的速度」), so 400 and 500 read the
 *       same -- dropping the bound would give 300% where the document says 200%;</li>
 *   <li>小伊卡 must <b>not</b> get a second +20% Max HP: its panel is 50% of the master's <em>resolved</em> sheet, so
 *       her raise already flows through, and a second modifier would double-count it;</li>
 *   <li>the +20% must not displace <b>another</b> HEALTH clause: the identifier for "same kind" is (attribute,
 *       modifierType, sourceRole) and does not include the name, so two {@code BATTLE_START} HEALTH modifiers from the
 *       same owner would evict each other. Measured: with {@code coexist} the technique's 20% and the trace's 20% add
 *       up; without it the trace wins and the technique's is gone. The <em>ultimate</em> is a different case -- it
 *       carries {@code max_stacks: 2}, so it stacks either way.</li>
 * </ul>
 */
public class HyacineWindstormTraceTest {

    private static final int HYACINE = 1409;
    private static final int ALLY = 1001;
    private static final double EPS = 1e-9;

    @Test
    public void theTraceRaisesHerMaxHpOnlyStrictlyAboveTwoHundredSpeed() {
        Character slow = hyacine(0, 200.0);      // 「大于 200」 ⇒ 200 exactly is NOT enough
        Character fast = hyacine(0, 250.0);
        double slowMax = maxHpAfterStart(slow);
        double fastMax = maxHpAfterStart(fast);
        double base = fast.getAttribute(AttributeType.HEALTH).baseValue();

        Assertions.assertEquals(slowMax, slow.getAttribute(AttributeType.HEALTH).get(), EPS,
                "precondition: at exactly 200 the gate is closed");
        Assertions.assertEquals(slowMax + base * 0.20, fastMax, Math.max(EPS, base * 1e-12),
                "「速度大于 200 时…生命上限提高 20%」 (slow=" + slowMax + ", fast=" + fastMax + ", base=" + base + ")");
    }

    @Test
    public void theHealingShareIsOnePercentPerPointCappedAtTwoHundredPoints() {
        Assertions.assertEquals(0.0, healBoost(200.0), EPS, "「之后每超出 1 点」: 200 exactly is still nothing");
        Assertions.assertEquals(0.5, healBoost(250.0), EPS, "50 points above 200 ⇒ 50%");
        Assertions.assertEquals(2.0, healBoost(400.0), EPS, "200 points above 200 ⇒ 200%");
        Assertions.assertEquals(2.0, healBoost(500.0), EPS,
                "「最多计入 200 点超出的速度」 -- 300 points above reads the SAME as 200");
    }

    @Test
    public void ikachiInheritsTheRaisedMaxHpAndTheHealingShareButIsNotRaisedTwice() {
        Character hyacine = hyacine(0, 250.0);
        Battle battle = started(hyacine);
        battle.summonMemosprite(hyacine);
        battle.processRequests();

        CanHit ika = battle.memospriteOf(hyacine);
        Assertions.assertNotNull(ika, "precondition: 小伊卡 is out");
        double hers = hyacine.getAttribute(AttributeType.HEALTH).get();
        Assertions.assertEquals(hers * 0.5, ika.getAttribute(AttributeType.HEALTH).get(), Math.max(EPS, hers * 1e-12),
                "「等同于风堇 50% 生命上限」 is read off her RESOLVED sheet, so the +20% arrives through the inherit "
                        + "(a second +20% here would be a double count; hers=" + hers + ", its="
                        + ika.getAttribute(AttributeType.HEALTH).get() + ")");
        Assertions.assertEquals(0.5, ika.getAttribute(AttributeType.OUTGOING_HEALING_BOOST).get(), EPS,
                "「风堇与**小伊卡**提供的治疗量提高」 -- the share has to be written on the memosprite too, "
                        + "because a modifier on an attribute is not inherited");
    }

    @Test
    public void rankFourAddsTwoPercentCritDamagePerPointOfSpeedUpToTheTracesBound() {
        Assertions.assertEquals(1.0, critGain(4, 250.0), EPS, "50 points above 200 ⇒ 2% × 50");
        Assertions.assertEquals(4.0, critGain(4, 500.0), EPS,
                "the strengthened trace keeps the bound: 200 points ⇒ 4.0, not 6.0");
        Assertions.assertEquals(0.0, critGain(0, 250.0), EPS, "the trace itself gives no CRIT DMG -- that is Rank 4");
    }

    @Test
    public void theRaisedMaxHpSurvivesTheUltimateWhichWritesItsOwnHealthModifier() {
        Character hyacine = hyacine(0, 250.0);
        Battle battle = started(hyacine);
        double base = hyacine.getAttribute(AttributeType.HEALTH).baseValue();
        double before = hyacine.getAttribute(AttributeType.HEALTH).get();

        SkillExecutor.execute(battle, hyacine.getSkills().get(SkillType.ULTRA), hyacine,
                List.of(battle.enemies.getFirst()));
        battle.processRequests();

        Assertions.assertEquals(base * 0.30 + 600.0, hyacine.getAttribute(AttributeType.HEALTH).get() - before,
                Math.max(EPS, base * 1e-12),
                "the ultimate's 30% + 600 is ADDED to the trace's 20%: the ultimate carries max_stacks 2, so it stacks "
                        + "rather than evicting (this holds with or without the trace's coexist)");
    }

    @Test
    public void theTraceStacksWithTheTechniquesOwnMaxHpRaise() {
        Character hyacine = hyacine(0, 250.0);
        Battle battle = new Battle(List.of(hyacine, CharacterFactory.create(ALLY, 80)),
                List.of(BattleFixture.dummy()), BattleFixture.seeded());
        battle.markTechniqueUsed(hyacine);
        double before = hyacine.getAttribute(AttributeType.HEALTH).get();
        battle.startBattle();
        battle.processRequests();

        double base = hyacine.getAttribute(AttributeType.HEALTH).baseValue();
        Assertions.assertEquals(before + base * (0.20 + 0.20), hyacine.getAttribute(AttributeType.HEALTH).get(),
                Math.max(EPS, base * 1e-12),
                "the technique's 20% and the trace's 20% are two different sentences on the same attribute: "
                        + "「生命上限提高 20%」(秘技) and 「生命上限提高 20%」(暴风停歇). Only one of them counting "
                        + "would be a silent wrong number, so this pins the SUM.");
    }

    private static Character hyacine(int eidolon, double speed) {
        Character c = CharacterFactory.create(HYACINE, 80, true, null, null, eidolon);
        // The trace reads the RESOLVED Speed, and no light cone in the fixture reaches 200; setting it here is the
        // honest way to reach the gate.
        c.setAttribute(AttributeType.SPEED, new DoubleValue(speed));
        return c;
    }

    private static Battle started(Character hyacine) {
        Battle battle = new Battle(List.of(hyacine, CharacterFactory.create(ALLY, 80)),
                List.of(BattleFixture.dummy()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        return battle;
    }

    private static double healBoost(double speed) {
        Character c = hyacine(0, speed);
        started(c);
        return c.getAttribute(AttributeType.OUTGOING_HEALING_BOOST).get();
    }

    private static double maxHpAfterStart(Character c) {
        started(c);
        return c.getAttribute(AttributeType.HEALTH).get();
    }

    private static double critGain(int eidolon, double speed) {
        Character base = hyacine(0, speed);
        Character ranked = hyacine(eidolon, speed);
        started(base);
        started(ranked);
        return ranked.getAttribute(AttributeType.CRIT_ATTACK).get() - base.getAttribute(AttributeType.CRIT_ATTACK).get();
    }
}
