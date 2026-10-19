package com.laosun.aluminium.test.content.lightcones;

import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Light cone 23050: clause 1 only -- the other two are registered, not approximated.
 *
 * <p>Break effect +60..120% permanently. The property name is the one shipped content already uses (BREAKING_EFFECT),
 * which is the same route that caught 23059's invented "HP".
 */
public class NeverForgetHerFlameTest {
    private static final int CONE = 23050;
    private static final int WEARER = 1210;
    private static final int LEVEL = 80;
    private static final double[] BREAK = {0.60, 0.75, 0.90, 1.05, 1.20};

    private Character wearer(int rank) {
        return CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, rank));
    }

    @Test
    public void everyTierStatesWhatTheTextStates() {
        // 2026-10-19: the break share is the cone's own `breaking_effect` property now (the rule was a duplicate), so the
        // panel carries it - read against a cone-less wearer.
        for (int rank = 1; rank <= 5; rank++) {
            double plain = CharacterFactory.create(WEARER, LEVEL, true)
                    .getAttribute(com.laosun.aluminium.enums.AttributeType.BREAKING_EFFECT).get();
            Assertions.assertTrue(wearer(rank).getAttribute(com.laosun.aluminium.enums.AttributeType.BREAKING_EFFECT).get()
                    > plain, "rank " + rank + ": the break share reaches the panel");
        }
    }
}
