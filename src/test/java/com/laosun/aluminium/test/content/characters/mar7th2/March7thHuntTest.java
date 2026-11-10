package com.laosun.aluminium.test.content.characters.mar7th2;


import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.trigger.TriggerTable;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1224 Hunt March 7th, from her own file: the Shifu (师父) marker, the charge it enables, and the speed share.
 *
 * <p>The speed is asserted against a hand-built 20% reference in the SAME pipeline (ratio 0.5), because a share of a zero base compares nothing and a
 * bare "greater than before" lets any percentage pass.
 */
public class March7thHuntTest {
    private static final int MARCH = 1224;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;

    /**
     * Note: The Skill marks the ally AND speeds them by 10%, measured against a hand-built 20% reference.
     */
    @Test
    public void theSkillMarksTheShifuAndSpeedsThem() {
        double content = skillSpeedGain(0);
        double reference = skillSpeedGain(1);

        Assertions.assertTrue(reference > 0, "the reference must raise speed at all");
        Assertions.assertEquals(0.5, content / reference, 0.05,
                "content " + content + " vs reference " + reference);
    }

    /**
     * Note: The charge is granted by HER basic attack and by the SHIFU's attack, and by nobody else.
     */
    @Test
    public void theChargeComesFromHerBasicAndFromTheShifu() {
        Character march = CharacterFactory.create(MARCH, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(march, ally), List.of(enemy), fixed());
        battle.startBattle();

        int start = chargeOf(march);
        // 1) an ordinary ally attack: nobody is the Shifu yet, so nothing is granted
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ALLY_ATTACK, ally, enemy, 0, 0));
        Assertions.assertEquals(start, chargeOf(march),
                "「【师父】施放攻击」 (the Shifu casts an attack) -- before the Skill, no ally is the Shifu");

        // 2) her own basic attack: +1
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.BASIC_ATTACK, march, enemy, 0, 0));
        Assertions.assertEquals(start + 1, chargeOf(march),
                "「随后获得1点充能」 (then gains 1 point of charge)");

        // 3) mark the ally as the Shifu, then their attack: +1 again
        battle.castImmediate(march.getSkills().get(SkillType.SKILL), march, List.of(ally));
        Assertions.assertTrue(ally.getBuffManager().hasState("师父"),
                "「使除自身以外的我方指定单体成为【师父】」 (make a designated single ally other than herself the Shifu)");
        int beforeShifu = chargeOf(march);
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ALLY_ATTACK, ally, enemy, 0, 0));
        Assertions.assertEquals(beforeShifu + 1, chargeOf(march),
                "「【师父】施放攻击后，三月七每次获得最多1点充能」 (after the Shifu attacks, March 7th gains at most 1 point of charge each time)");
    }

    /**
     * 「充能大于等于 7 点时…造成的伤害提高 80%」 — the boost is gated on the RESOURCE, so it must disappear the moment the
     * tally drops below 7, without any rule "removing" it.
     *
     * <p>This is the criterion for {@code while_resource}: the buff manager's own sweep is what retires the modifier, so the
     * test has to (a) prove the boost is on at 7, (b) drop the tally to 6, and (c) fire a turn boundary so the sweep runs.
     * A gate that only "never fired" would pass (a) and (b) but never retire anything - so the negative half is the real test.
     */
    @Test
    public void sevenChargesRaiseHerDamageAndSixTakeItBack() {
        Character march = CharacterFactory.create(MARCH, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(march, ally), List.of(enemy), fixed());
        battle.startBattle();

        double base = march.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();

        // Reaching 7 fires the talent: the tally moves one point at a time, so the seventh point is what crosses the line.
        for (int i = 0; i < 7; i++) {
            battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(
                    TriggerEvent.BASIC_ATTACK, march, enemy, 0, 0));
        }
        Assertions.assertTrue(chargeOf(march) >= 7, "the seven basics must reach the threshold, was " + chargeOf(march));
        double raised = march.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();
        Assertions.assertTrue(raised > base, "「造成的伤害提高80%」: the boost must be on at 7 charges, base=" + base
                + " raised=" + raised);

        // Spend the charge by hand (the enhanced basic does this in play) and cross a turn boundary: the sweep must retire it.
        march.getResources().get("充能").setValue(6);
        // The gate is asked in the buff tick, i.e. at HER turn boundary - not on a bare TURN_START event.
        march.getBuffManager().beforeMove();
        double after = march.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();
        Assertions.assertEquals(base, after, 1e-9,
                "「充能大于等于7点时」: at 6 charges the +80% must be gone (the resource gate is what retires it)");
    }

    /**
     * The declared resource's value, read through the combatant's own manager.
     */
    private static int chargeOf(Character march) {
        return march.getResources().get("充能").getValue();
    }

    /**
     * mode 0 = the shipped file, 1 = a hand-built 20% reference. Returns the ally's speed gain after the Skill.
     */
    private static double skillSpeedGain(int mode) {
        Character march = CharacterFactory.create(MARCH, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        if (mode == 1) {
            EffectSpec effect = new EffectSpec();
            TriggerSpecs.set(effect, "op", "MODIFY_ATTR");
            TriggerSpecs.set(effect, "attribute", "SPEED");
            TriggerSpecs.set(effect, "percent", 0.2);
            TriggerSpecs.set(effect, "permanent", true);
            TriggerSpecs.set(effect, "target", "target");
            march.setTriggerTable(new TriggerTable(MARCH, List.of(TriggerSpecs.rule(
                    TriggerEvent.SKILL_CAST.name(), List.of("actor == self"), effect))));
        }
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(march, ally), List.of(enemy), fixed());
        battle.startBattle();
        double before = ally.getAttribute(AttributeType.SPEED).get();
        battle.castImmediate(march.getSkills().get(SkillType.SKILL), march, List.of(ally));
        return ally.getAttribute(AttributeType.SPEED).get() - before;
    }

    private static Random fixed() {
        return BattleFixture.alwaysRoll(0.0);
    }
}