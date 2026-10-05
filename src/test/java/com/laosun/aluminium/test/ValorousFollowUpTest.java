package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Signal;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import com.laosun.aluminium.utils.RelicFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * Relic set 315 (2 pieces): an ally's follow-up grants a stack of Merit (功勋) (capped), each stack raises follow-up damage, and a FULL stack adds crit damage.
 *
 * <p>The ally is played by a second character, and the event is fired with that ally as the actor, so the `actor is_other_ally` filter is exercised: firing it from
 * the wearer must grant nothing.
 */
public class ValorousFollowUpTest {
    private static final int WEARER = 1001;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final int RELIC_LEVEL = 15;
    private static final int CAP = 5;

    @Test
    public void anAllysFollowUpGrantsAStackButTheWearersOwnDoesNot() {
        Character wearer = wearer();
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(wearer, ally), List.of(enemy), new Random(0));
        battle.startBattle();
        battle.currentMove = new Signal(wearer);
        battle.beforeMove();
        double baseline = wearer.getAttribute(AttributeType.FOLLOW_UP_DAMAGE_BOOST).get();
        battle.fireTriggers(TriggerEvent.FOLLOW_UP, wearer, enemy, 0, 0);
        Assertions.assertEquals(baseline, wearer.getAttribute(AttributeType.FOLLOW_UP_DAMAGE_BOOST).get(), 1e-6,
                "the wearer's own follow-up must not mark 功勋");
        battle.fireTriggers(TriggerEvent.FOLLOW_UP, ally, enemy, 0, 0);
        Assertions.assertTrue(wearer.getAttribute(AttributeType.FOLLOW_UP_DAMAGE_BOOST).get() > baseline,
                "an ally's follow-up does: " + baseline + " -> " + wearer.getAttribute(AttributeType.FOLLOW_UP_DAMAGE_BOOST).get());
    }

    @Test
    public void followUpDamageStacksOnlyUpToTheCapAndTheThresholdAddsCritDamage() {
        Character wearer = wearer();
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(wearer, ally), List.of(enemy), new Random(0));
        battle.startBattle();
        battle.currentMove = new Signal(wearer);
        battle.beforeMove();
        double boostBefore = wearer.getAttribute(AttributeType.FOLLOW_UP_DAMAGE_BOOST).get();
        double critDamageBefore = wearer.getAttribute(AttributeType.CRIT_ATTACK).get();
        int firings = CAP + 2;
        for (int i = 0; i < firings; i++) {
            battle.fireTriggers(TriggerEvent.FOLLOW_UP, ally, enemy, 0, 0);
        }
        Assertions.assertEquals(0.05 * CAP, wearer.getAttribute(AttributeType.FOLLOW_UP_DAMAGE_BOOST).get() - boostBefore, 1e-6,
                "of " + firings + " follow-ups only " + CAP + " may stack");
        Assertions.assertEquals(0.25, wearer.getAttribute(AttributeType.CRIT_ATTACK).get() - critDamageBefore, 1e-6,
                "the full-stack threshold adds crit damage exactly once");
    }

    private static Character wearer() {
        return CharacterFactory.create(WEARER, LEVEL, true, null, RelicFactory.suit(315, 2, RELIC_LEVEL));
    }
}
