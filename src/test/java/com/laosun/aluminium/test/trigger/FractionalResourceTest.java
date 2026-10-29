package com.laosun.aluminium.test.trigger;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.Constant;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.trigger.TriggerTable;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * A <b>fractional counter</b>: the engine stores a resource at the precision the game uses, and a magnitude that reads
 * it sees the fraction.
 *
 * <p><b>Why this is a judge and not a detail.</b> 1409's 小伊卡 prices its damage off 「本场战斗累计治疗数值」 - a sum of
 * heals, 36907.2364 in the captured fight. While {@code Resource} was an integer counter that tally was floored on
 * every gain, so the rule computed a base nothing could reproduce, and no capture comparison could tell whether the
 * rule or the tally was wrong. This pins the two halves that make it reproducible: the value keeps its fraction, and
 * the {@code self_resource:} reader returns it without rounding.
 */
public class FractionalResourceTest {

    private static final int OWNER = 1409;
    private static final String TALLY = "小伊卡累计治疗";

    /**
     * The stored value keeps its fraction, while the counter view still answers a whole number.
     */
    @Test
    public void theValueKeepsItsFractionAndTheCountDoesNot() {
        Character owner = CharacterFactory.create(OWNER, 80);
        var tally = owner.getResources().get(TALLY);
        Assertions.assertNotNull(tally, "1409 declares the tally");

        tally.setExactValue(36907.2364);

        Assertions.assertEquals(36907.2364, tally.exactValue(), 1e-9, "the magnitude reader sees the fraction");
        Assertions.assertEquals(36907, tally.value(), "the counter reader still answers the whole part");
    }

/**
     * Fractional gains ACCUMULATE: 0.4 + 0.4 is 0.8, where the integer view would have stored 0 both times.
     */
    @Test
    public void fractionalGainsAccumulate() {
        Character owner = CharacterFactory.create(OWNER, 80);
        var tally = owner.getResources().get(TALLY);
        tally.setExactValue(0);

        tally.gainClampedExact(0.4);
        tally.gainClampedExact(0.4);
        tally.gainExact(0.2);

        Assertions.assertEquals(1.0, tally.exactValue(), 1e-12, "the fraction is kept across gains");
        Assertions.assertEquals(1, tally.value(), "and the counter view sees the whole once it is reached");
    }
}
