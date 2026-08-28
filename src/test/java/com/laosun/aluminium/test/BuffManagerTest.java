package com.laosun.aluminium.test;

import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.models.BuffManager;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.buffs.BoostDamageBuff;
import com.laosun.aluminium.models.buffs.StunBuff;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

public class BuffManagerTest {

    private static Character character() {
        return Character.fromAttributes("c", 1000, 100, 100, 100);
    }

    private static List<DoubleValue.Modifier> boostModifiers(Character c) {
        return c.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST)
                .filterBySource(DoubleValue.Modifier.ModifierSource.BUFF);
    }

    @Test
    public void addBuffAppliesEffect() {
        Character c = character();
        c.getBuffManager().addBuff(new BoostDamageBuff(2, .5));

        Assertions.assertEquals(1, boostModifiers(c).size());
        Assertions.assertEquals(.5, boostModifiers(c).getFirst().getValue(), 1e-9);
    }

    @Test
    public void lateBuffTicksOnlyOnAfterMove() {
        Character c = character();
        c.getBuffManager().addBuff(new BoostDamageBuff(1, .5));

        c.getBuffManager().beforeMove();
        Assertions.assertEquals(1, boostModifiers(c).size(), "late buff must not tick on beforeMove");

        c.getBuffManager().afterMove();
        Assertions.assertTrue(boostModifiers(c).isEmpty(), "late buff should expire and remove effect");
    }

    @Test
    public void stunExpiryStillBlocksTheCurrentTurn() {
        Character c = character();
        BuffManager manager = c.getBuffManager();
        manager.addBuff(new StunBuff(1));

        manager.beforeMove();
        Assertions.assertFalse(manager.canAct(), "the turn a stun expires should still be blocked");
        manager.afterMove();

        manager.beforeMove();
        Assertions.assertTrue(manager.canAct(), "stun should not block after full expiry");
    }

    @Test
    public void removeBuffRemovesEffect() {
        Character c = character();
        BoostDamageBuff buff = new BoostDamageBuff(2, .5);
        c.getBuffManager().addBuff(buff);

        c.getBuffManager().removeBuff(buff);

        Assertions.assertTrue(boostModifiers(c).isEmpty());
    }

    @Test
    public void sameKindBuffRefreshesInsteadOfStacking() {
        Character c = character();
        c.getBuffManager().addBuff(new BoostDamageBuff(2, .5));

        c.getBuffManager().addBuff(new BoostDamageBuff(1, .7));

        List<DoubleValue.Modifier> buffMods = boostModifiers(c);
        Assertions.assertEquals(1, buffMods.size(), "same-kind buff should replace, not stack");
        Assertions.assertEquals(.7, buffMods.getFirst().getValue(), 1e-9);
    }

    @Test
    public void differentKindsCoexist() {
        Character c = character();
        c.getBuffManager().addBuff(new StunBuff(2));
        c.getBuffManager().addBuff(new BoostDamageBuff(3, .5));

        Assertions.assertFalse(c.getBuffManager().canAct(), "stun should block action");
        Assertions.assertEquals(1, boostModifiers(c).size());
    }

    @Test
    public void clearAllRemovesEverything() {
        Character c = character();
        c.getBuffManager().addBuff(new StunBuff(2));
        c.getBuffManager().addBuff(new BoostDamageBuff(3, .5));

        c.getBuffManager().clearAll();

        Assertions.assertTrue(c.getBuffManager().canAct());
        Assertions.assertTrue(boostModifiers(c).isEmpty());
    }
}
