package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 1414 丹恒•腾荒's Skill: 「重复获得丹恒•腾荒的护盾时，护盾量<b>可以叠加</b>，最高不超过当前战技提供的护盾量的 <b>300%</b>」.
 *
 * <p><b>Why a party of ONE.</b> The sentence was unreachable for several rounds because the shield had two authors - the engine's
 * {@code Defence} delivery and a content rule - so the aimed ally was written twice while the caster once. That duplicate is gone
 * (the curated entry was dropped, and {@code CAST_SKILL} now accepts a rule-delivered cast), so the party loop cannot be a variable.
 *
 * <p><b>Why not "cast 2 = 2 x cast 1".</b> His ATK does not stay put: the first cast applies 【同袍】 and his own traces move the
 * number, so the second grant is worth slightly more (measured 571.38 instead of 549.02). The criterion therefore asserts what the
 * sentence actually says: <b>each cast ADDS</b> (strictly increasing) and the total <b>stops</b> at the ceiling.
 */
public class ShieldStackingTest {
    private static final int DHPT = 1414;
    private static final int LEVEL = 80;

    @Test
    public void repeatedCastsStackAndStopAtTheCeiling() {
        Character dhpt = CharacterFactory.create(DHPT, LEVEL);
        Enemy enemy = BattleFixture.monster();
        enemy.setMaxStance(100_000);
        enemy.setStance(100_000);
        Battle battle = new Battle(List.of(dhpt), List.of(enemy), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        Assertions.assertEquals(0.0, dhpt.getShield(), 1e-9, "precondition: he starts with no shield");

        List<Double> shields = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            battle.castImmediate(dhpt.getSkills().get(SkillType.SKILL), dhpt, List.of(dhpt));
            battle.processRequests();
            shields.add(dhpt.getShield());
        }
        double attack = dhpt.getAttribute(AttributeType.ATTACK).get();
        double oneCast = attack * 0.2 + 400;
        double ceiling = oneCast * 3;
        System.out.println("[1414-stack] atk=" + attack + " oneCast=" + oneCast + " ceiling=" + ceiling + " shields=" + shields);

        for (int i = 1; i < 3; i++) {
            Assertions.assertTrue(shields.get(i) > shields.get(i - 1),
                    "「护盾量可以叠加」 - cast " + (i + 1) + " ADDED: " + shields);
        }
        // ⚠ The ceiling is applied to the UNBOOSTED arithmetic while the installed shield is boosted (`BattleDamage.boostedShield`,
        // the relic/trace "provided shield +X%"), so the total APPROACHES the ceiling instead of landing on it: 1695.13 → 1725.70 →
        // 1725.92 → 1725.96 against 1725.96. The clause is "does not exceed 300%", which holds; the last few tenths are the boost
        // living in the other space and are registered on the character.
        Assertions.assertTrue(shields.get(3) > shields.get(2), "still growing towards the cap: " + shields);
        Assertions.assertTrue(shields.get(5) - shields.get(4) < ceiling * 0.01,
                "by the sixth cast the growth has stopped: " + shields);
        Assertions.assertTrue(shields.get(5) <= ceiling * 1.0001,
                "「最高不超过当前战技提供的护盾量的 300%」: " + shields.get(5) + " vs ceiling " + ceiling);
        Assertions.assertTrue(shields.get(5) > oneCast * 2.5, "and it really did stack up: " + shields);
    }
}
