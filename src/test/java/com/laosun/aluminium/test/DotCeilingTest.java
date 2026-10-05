package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.buff.DotBuff;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * A DOT whose magnitude is a share of the <b>victim's</b> Max HP, with a <b>derived ceiling</b> (2026-09-28).
 *
 * <p>Reader: 1111 Luka's Skill - "裂伤状态下…受到等同于<b>自身 24.00% 生命上限</b>的物理属性持续伤害，<b>最多不超过卢卡攻击力的
 * 338%</b>". That is a {@code min_of_two}: the engine takes the smaller of the two derived values, which is why a low-HP victim keeps
 * its small share and only an excessive one is brought down to the ceiling.
 *
 * <p>Note: The observation is the buff's own <b>base damage</b> ({@code DotBuff.getBaseDamage()}), not the settled damage: settlement runs
 * the defence/resistance zones, so a settled number would mix the question being asked with the defender's stats.
 */
public class DotCeilingTest {
    /** Himeko: no shipped rule file, so the table under test is the only one. */
    private static final int OWNER = 1003;
    private static final int LEVEL = 80;
    private static final double EPS = 1e-6;

    /** Note: A high-HP victim is capped by the owner's attack, not by its own enormous bar. */
    @Test
    public void aBigVictimIsCappedByTheOwnerSideValue() {
        Character owner = CharacterFactory.create(OWNER, LEVEL);
        Enemy boss = Enemy.fromAttributes("boss", 1_000_000, 0, 0, 100);

        double base = baseDamageOn(owner, boss);

        Assertions.assertEquals(owner.getAttribute(AttributeType.ATTACK).get() * 3.38, base, 1e-3,
                "「最多不超过卢卡攻击力的 338%」 -- the ceiling wins for a victim whose 24% share would be larger");
    }

    /** Note: A small victim keeps its own small share: the ceiling is a ceiling, not a replacement. */
    @Test
    public void aSmallVictimKeepsItsOwnShare() {
        Character owner = CharacterFactory.create(OWNER, LEVEL);
        Enemy weakling = Enemy.fromAttributes("weakling", 1_000, 0, 0, 100);

        double base = baseDamageOn(owner, weakling);

        Assertions.assertEquals(240, base, 1e-3,
                "24% of 1,000 HP is far below the ceiling, so the victim-side value stands (a floor/replacement reading "
                        + "would have paid the ceiling here)");
    }

    /** Note: Only the DOT reads a ceiling today; every other op refuses it instead of ignoring it. */
    @Test
    public void otherOpsRefuseACeiling() {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "HEAL");
        TriggerSpecs.set(effect, "amount", 100.0);
        TriggerSpecs.set(effect, "capScale", "self_attr:ATTACK");
        TriggerSpecs.set(effect, "capPercent", 1.0);
        TriggerSpecs.set(effect, "target", "self");
        TriggerSpec wrong = TriggerSpecs.rule("KILL", List.of("actor == self"), effect);

        IllegalArgumentException refused = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(OWNER, List.of(wrong)));
        Assertions.assertTrue(refused.getMessage().contains("cap_scale"), refused.getMessage());
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    /** Applies the rule's DOT once and reports the base damage the buff carries. */
    private static double baseDamageOn(Character owner, Enemy victim) {
        EffectSpec dot = new EffectSpec();
        TriggerSpecs.set(dot, "op", "APPLY_DOT");
        TriggerSpecs.set(dot, "element", "Physical");
        TriggerSpecs.set(dot, "scale", "target_max_hp");
        TriggerSpecs.set(dot, "percent", 0.24);
        TriggerSpecs.set(dot, "capScale", "self_attr:ATTACK");
        TriggerSpecs.set(dot, "capPercent", 3.38);
        TriggerSpecs.set(dot, "turns", 3);
        TriggerSpecs.set(dot, "target", "target");
        owner.setTriggerTable(new TriggerTable(OWNER, List.of(TriggerSpecs.rule("KILL", List.of("actor == self"), dot))));

        Battle battle = new Battle(List.of(owner), List.of(victim), new Random() {
            @Override
            public double nextDouble() {
                return 0.0;
            }
        });
        battle.fireTriggers(TriggerEvent.KILL, owner, victim, 0, 0);

        List<DotBuff> dots = victim.getBuffManager().allBuffsOf(DotBuff.class);
        Assertions.assertEquals(1, dots.size(), "precondition: one DOT landed");
        return dots.get(0).getBaseDamage();
    }
}
