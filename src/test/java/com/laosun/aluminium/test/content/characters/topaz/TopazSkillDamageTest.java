package com.laosun.aluminium.test.content.characters.topaz;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.entity.Summon;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.trigger.TriggerPayload;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * 1112 托帕&账账's Skill: 「使账账对该目标造成等同于托帕 150% 攻击力的火属性伤害。施放此战技造成伤害时，被视为发动了追加攻击。」
 *
 * <p><b>Why this file exists.</b> Until 2026-11-08 the cast settled <b>twice</b>: the Skill row's own 7 segments (data) and the rule's one instance
 * (content) both landed - measured 11424.77 against her 620.93 ATK whose 150% is 931.39, i.e. an order of magnitude. No "the damage exists" assertion
 * could see it; only the number against the base could. The fix is a {@code CAST_SETUP} {@code DELEGATE_DAMAGE} (the row no longer swings) plus
 * {@code attacker: "summon"} on the rule (the swing is Numby's, which is also what lets 【涨幅惊人！】 reach it).
 *
 * <p>So this judge pins two things that are both invisible to a smoke test: the Skill's total equals <b>one</b> instance of the same 150%, and that
 * instance belongs to the summon - hence the Ultimate's boost raises it.
 *
 * <p><b>L3:</b> the two single-clause cases that read the same {@code Scene} fixture are now ONE scenario test, and its
 * assertions sit inside {@link Assertions#assertAll} with a message naming the clause: <b>one</b> instance, and
 * 【涨幅惊人！】's reach into it. Every assertion, expected value, tolerance and message is unchanged, and each half keeps
 * its own {@code Scene} - the buffed one needs the Ultimate, the plain one is the ratio's baseline.
 */
public class TopazSkillDamageTest {
    private static final int TOPAZ = 1112;
    private static final int LEVEL = 80;
    private static final double EPS = 1e-9;

    /**
     * The Skill settles <b>one</b> instance - and that instance is the one the mark and 【涨幅惊人！】then amplify.
     *
     * <p>⚠ The comparison is {@code strike / skill == 1.5}, not equality: the Skill applies 【负债证明】(「受到的追加攻击伤害提高 50%」)
     * in the SAME rule, <b>after</b> its damage, so the strike that follows rides the 50% while the Skill's own instance does not.
     * Equality was the first draft and it failed on 2026-11-09 - for the right reason, once the toughness bar was also pinned
     * (a 60-point skill breaks the stock monster, and the break's 9688 would have swamped everything).
     *
     * <p>战技的伤害是账账的 ⇒ 【涨幅惊人！】的两条修饰能加到它上面（记在托帕头上时加不到）; the same plain settlement is the
     * baseline of that ratio.
     *
     * <p>One scenario, because both readings are the same object (the Skill's settlement on the same {@code Scene}) and both
     * halves use that same fixture shape - only the buffed half adds the Ultimate.
     */
    @Test
    public void theSkillsDamageIsOneInstanceOfNumbysAndWindfallReachesIt() {
        Scene plain = new Scene();
        double skill = plain.skillDamage();
        double strike = plain.strikeDamage();
        double attack = plain.attack();

        Scene buffed = new Scene();
        double fireBoost = buffed.summon().getAttribute(AttributeType.FIRE_DAMAGE_BOOST).get();
        double critBefore = buffed.summon().getAttribute(AttributeType.CRIT_ATTACK).get();
        buffed.ultimate();
        double critAfter = buffed.summon().getAttribute(AttributeType.CRIT_ATTACK).get();
        double with = buffed.skillDamage();

        double ratio = strike / skill;
        double expected = (1 + fireBoost + 1.5) / (1 + fireBoost) * (1 + critAfter) / (1 + critBefore);
        System.out.println("[1112] skill=" + skill + " strike=" + strike + " 1.5atk=" + 1.5 * attack);
        System.out.println("[1112] skill: plain=" + skill + " windfall=" + with
                + " ratio=" + (with / skill) + " expected=" + expected);
        Assertions.assertAll("战技: one instance of the 150%, and 【char:1112/staggering_rise！】's reach into it",
                () -> Assertions.assertTrue(skill > 0, "precondition: the Skill dealt damage"),
                () -> Assertions.assertEquals(1.5, ratio, 0.01,
                        "one instance, and the 50% the Skill applies on top: " + ratio),
                () -> Assertions.assertTrue(skill < 4 * 1.5 * attack,
                        "the Skill's own instance stays within one hit's zones (a second settlement would be an order of magnitude bigger): " + skill),
                () -> Assertions.assertEquals(expected, with / skill, 0.01,
                        "「伤害倍率提高 150%」 reaches the Skill's instance only because its attacker is the summon"));
    }

    private static final class Scene {
        private final Character topaz;
        private final Enemy enemy;
        private final Battle battle;

        private Scene() {
            topaz = CharacterFactory.create(TOPAZ, LEVEL);
            enemy = BattleFixture.monster();
            // Both halves: `setMaxStance` alone leaves the bar EMPTY, and an empty bar breaks on the first 60-point hit
            // (measured: the break settled 9688 and swamped the measurement).
            enemy.setMaxStance(100_000);
            enemy.setStance(100_000);
            battle = new Battle(List.of(topaz), List.of(enemy), BattleFixture.alwaysRoll(0.0));
            battle.startBattle();
            battle.processRequests();
        }

        private Summon summon() {
            return battle.summonsOf(topaz).getFirst();
        }

        private double attack() {
            return topaz.getAttribute(AttributeType.ATTACK).get();
        }

        private void ultimate() {
            battle.castImmediate(topaz.getSkills().get(SkillType.ULTRA), topaz, List.of(enemy));
            battle.processRequests();
        }

        /** The Skill's own settlement, measured on the enemy's HP. */
        private double skillDamage() {
            double before = enemy.getCurrentHp();
            battle.castImmediate(topaz.getSkills().get(SkillType.SKILL), topaz, List.of(enemy));
            battle.processRequests();
            return before - enemy.getCurrentHp();
        }

        /** One Numby strike on the (now marked) target, for comparison with the Skill's instance. */
        private double strikeDamage() {
            Assertions.assertTrue(enemy.getBuffManager().hasState("char:1112/debt_proof"),
                    "precondition: the Skill marked the target, so Numby has something to hit");
            double before = enemy.getCurrentHp();
            battle.fire(TriggerPayload.Simple.of(TriggerEvent.TURN_START, summon(), summon(), 1, 0));
            battle.processRequests();
            return before - enemy.getCurrentHp();
        }
    }
}
