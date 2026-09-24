package com.laosun.aluminium.test;

import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.models.BuffManager;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.buffs.StatModifierBuff;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * P10-3: the generic stat buff / debuff.
 *
 * <p>The point of this class is that **one** buff class covers every "stat X becomes X ⊕ value for N
 * turns" effect, so 93 characters' buffs stay data instead of becoming 93 Java classes. What has to be
 * pinned is the part that is easy to get quietly wrong:
 *
 * <ul>
 *   <li><b>identity</b> — two different attributes must not evict each other;</li>
 *   <li><b>exactness</b> — expiry must restore the original value, not approximately;</li>
 *   <li><b>coexistence</b> — buff and debuff on the same attribute are separate effects;</li>
 *   <li><b>speed</b> — a speed change must be announced, or the action order keeps the old speed.</li>
 * </ul>
 */
public class BuffRuleTest {

    /** hp 1000 / def 200 / atk 300 / speed 100 — deliberately all different, so a mix-up shows. */
    private static Character hero() {
        return Character.fromAttributes("hero", 1000, 200, 300, 100);
    }

    private static double attackOf(Character c) {
        return c.getAttribute(AttributeType.ATTACK).get();
    }

    private static double defenceOf(Character c) {
        return c.getAttribute(AttributeType.DEFENCE).get();
    }

    private static long modifiersOf(Character c, AttributeType attribute,
                                    DoubleValue.Modifier.ModifierSource source) {
        return c.getAttribute(attribute).filterBySource(source).size();
    }

    // ==================================================================
    // The arithmetic
    // ==================================================================

    @Test
    public void percentBuffScalesTheAttribute() {
        Character c = hero();
        c.getBuffManager().addBuff(StatModifierBuff.percentBuff(AttributeType.ATTACK, 0.5, 2));

        Assertions.assertEquals(450, attackOf(c), 1e-9, "ATK 300 with +50% is 450");
    }

    @Test
    public void flatBuffIsAddedAfterThePercentages() {
        Character c = hero();
        c.getBuffManager().addBuff(StatModifierBuff.percentBuff(AttributeType.ATTACK, 0.5, 2));
        c.getBuffManager().addBuff(StatModifierBuff.flatBuff(AttributeType.ATTACK, 50, 2));

        // 300 * 1.5 + 50, not (300 + 50) * 1.5
        Assertions.assertEquals(500, attackOf(c), 1e-9);
    }

    @Test
    public void debuffReducesTheAttributeAndCoexistsWithABuffOnTheSameOne() {
        Character c = hero();
        c.getBuffManager().addBuff(StatModifierBuff.percentDebuff(AttributeType.DEFENCE, -0.25, 2));
        c.getBuffManager().addBuff(StatModifierBuff.percentBuff(AttributeType.DEFENCE, 0.5, 2));

        // both are ADD_PERCENT and sum: 200 * (1 - 0.25 + 0.5)
        Assertions.assertEquals(250, defenceOf(c), 1e-9);
        Assertions.assertEquals(1, modifiersOf(c, AttributeType.DEFENCE,
                DoubleValue.Modifier.ModifierSource.BUFF), "the buff is its own modifier");
        Assertions.assertEquals(1, modifiersOf(c, AttributeType.DEFENCE,
                DoubleValue.Modifier.ModifierSource.DEBUFF), "so is the debuff");
    }

    @Test
    public void expiryRestoresTheOriginalValueExactly() {
        Character c = hero();
        c.getBuffManager().addBuff(StatModifierBuff.percentBuff(AttributeType.ATTACK, 0.5, 1));

        c.getBuffManager().beforeMove();
        Assertions.assertEquals(450, attackOf(c), 1e-9, "a late buff must not tick on beforeMove");

        c.getBuffManager().afterMove();
        Assertions.assertEquals(300, attackOf(c), 1e-9,
                "the modifier must be removed by id, so the value returns exactly to base");
    }

    @Test
    public void earlyBuffsExpireOnBeforeMove() {
        Character c = hero();
        c.getBuffManager().addBuff(
                StatModifierBuff.of(AttributeType.ATTACK, "add_percent", 0.5, "buff", 1, true));

        c.getBuffManager().beforeMove();
        Assertions.assertEquals(300, attackOf(c), 1e-9);
    }

    // ==================================================================
    // Identity: this is the guard that stops stat buffs from evicting each other
    // ==================================================================

    @Test
    public void differentAttributesDoNotEvictEachOther() {
        Character c = hero();
        c.getBuffManager().addBuff(StatModifierBuff.percentBuff(AttributeType.ATTACK, 0.5, 2));
        c.getBuffManager().addBuff(StatModifierBuff.percentBuff(AttributeType.DEFENCE, 1.0, 2));

        Assertions.assertEquals(450, attackOf(c), 1e-9, "the ATK buff must survive the DEF buff");
        Assertions.assertEquals(400, defenceOf(c), 1e-9, "DEF 200 with +100% is 400");
    }

    @Test
    public void differentModifierTypesDoNotEvictEachOther() {
        Character c = hero();
        c.getBuffManager().addBuff(StatModifierBuff.percentBuff(AttributeType.ATTACK, 0.5, 2));
        c.getBuffManager().addBuff(StatModifierBuff.flatBuff(AttributeType.ATTACK, 50, 2));

        Assertions.assertEquals(2, c.getAttribute(AttributeType.ATTACK)
                        .filterBySource(DoubleValue.Modifier.ModifierSource.BUFF).size(),
                "a percent buff and a flat buff are different kinds");
    }

    @Test
    public void theSameBuffAgainRefreshesInsteadOfStacking() {
        Character c = hero();
        c.getBuffManager().addBuff(StatModifierBuff.percentBuff(AttributeType.ATTACK, 0.5, 2));
        c.getBuffManager().addBuff(StatModifierBuff.percentBuff(AttributeType.ATTACK, 0.2, 5));

        Assertions.assertEquals(1, modifiersOf(c, AttributeType.ATTACK,
                DoubleValue.Modifier.ModifierSource.BUFF), "same kind replaces, it does not stack");
        Assertions.assertEquals(360, attackOf(c), 1e-9, "300 * (1 + 0.2) — the new value wins");
    }

    @Test
    public void removingOneBuffLeavesTheOtherUntouched() {
        Character c = hero();
        BuffManager manager = c.getBuffManager();
        StatModifierBuff atk = StatModifierBuff.percentBuff(AttributeType.ATTACK, 0.5, 3);
        StatModifierBuff def = StatModifierBuff.percentBuff(AttributeType.DEFENCE, 1.0, 3);
        manager.addBuff(atk);
        manager.addBuff(def);

        manager.removeBuff(atk);

        Assertions.assertEquals(300, attackOf(c), 1e-9);
        Assertions.assertEquals(400, defenceOf(c), 1e-9, "removal is by modifier id, not by attribute");
    }

    // ==================================================================
    // Speed is the one attribute with a side effect
    // ==================================================================

    @Test
    public void speedBuffsAnnounceTheChangeSoTheActionOrderCanFollow() {
        Character c = hero();
        AtomicInteger notifications = new AtomicInteger();
        c.setSpeedChangeListener(unit -> notifications.incrementAndGet());

        StatModifierBuff speed = StatModifierBuff.flatBuff(AttributeType.SPEED, 36, 2);
        c.getBuffManager().addBuff(speed);
        Assertions.assertEquals(136, c.getAttribute(AttributeType.SPEED).get(), 1e-9);
        Assertions.assertEquals(1, notifications.get(), "applying a speed buff must announce it");

        c.getBuffManager().removeBuff(speed);
        Assertions.assertEquals(2, notifications.get(), "and so must removing it");
    }

    @Test
    public void nonSpeedBuffsDoNotAnnounceAnything() {
        Character c = hero();
        AtomicInteger notifications = new AtomicInteger();
        c.setSpeedChangeListener(unit -> notifications.incrementAndGet());

        c.getBuffManager().addBuff(StatModifierBuff.percentBuff(AttributeType.ATTACK, 0.5, 2));

        Assertions.assertEquals(0, notifications.get(),
                "only SPEED reorders the action bar; announcing every buff would be wasted work");
    }

    // ==================================================================
    // Bad input must fail loudly, not become "no modifier at all"
    // ==================================================================

    @Test
    public void percentageAttributesAreRejected() {
        // ATTACK_PERCENT is merged into ATTACK's modifier list by the builder, so a buff must not
        // target it: doing so would look like it worked and change nothing.
        IllegalArgumentException error = Assertions.assertThrows(IllegalArgumentException.class,
                () -> StatModifierBuff.percentBuff(AttributeType.ATTACK_PERCENT, 0.5, 2));
        Assertions.assertTrue(error.getMessage().contains("ATTACK_PERCENT"),
                "the message should name the offending attribute, got: " + error.getMessage());
    }

    @Test
    public void unknownModifierTypeOrSourceIsRejected() {
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> StatModifierBuff.of(AttributeType.ATTACK, "add_percent_typo", 0.5, "buff", 2, false));
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> StatModifierBuff.of(AttributeType.ATTACK, "add_percent", 0.5, "banana", 2, false));
    }

    @Test
    public void nullAttributeIsRejected() {
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> StatModifierBuff.percentBuff(null, 0.5, 2));
    }
}
