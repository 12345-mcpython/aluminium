package com.laosun.aluminium.test.trigger;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.entity.Character;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.buff.ShieldBuff;
import com.laosun.aluminium.models.trigger.TriggerTable;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * {@code has_shield from_provider self}: the shield must have been created by the RULE OWNER, not merely exist.
 *
 * <p>Reader: relic set 128's 4-piece - 「我方目标持有<b>装备者提供的</b>护盾时，暴击伤害提高 15%」. The
 * older {@code has_shield} only asked whether the ally held any shield, so a teammate's shield paid the
 * wearer's bonus too.
 */
public class ShieldProviderConditionTest {
    private static final int OWNER = 1402;
    private static final int ALLY = 1002;
    private static final int OTHER = 1001;
    private static final int LEVEL = 80;

    /**
     * The owner's shield qualifies; the same shield with a different provider does not.
     */
    @Test
    public void onlyTheOwnersShieldQualifies() {
        Assertions.assertTrue(healFiresWithOwnerShield(),
                "the shield was created by the rule owner, so `from_provider self` holds");
        Assertions.assertFalse(healFiresWithSomeoneElsesShield(),
                "the ally has a shield, but not one the owner provided");
    }

    /**
     * The qualifier is parsed at load time, and an unknown provider name is refused.
     */
    @Test
    public void anUnknownProviderNameIsRefused() {
        Character owner = CharacterFactory.create(OWNER, LEVEL);
        IllegalArgumentException refused = Assertions.assertThrows(IllegalArgumentException.class,
                () -> owner.setTriggerTable(new TriggerTable(OWNER,
                        List.of(healRule("target has_shield from_provider nobody")))));
        Assertions.assertTrue(refused.getMessage().contains("nobody"), refused.getMessage());
    }

    private static boolean healFiresWithOwnerShield() {
        Character owner = CharacterFactory.create(OWNER, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        return heal(owner, ally, owner);
    }

    private static boolean healFiresWithSomeoneElsesShield() {
        Character owner = CharacterFactory.create(OWNER, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Character other = CharacterFactory.create(OTHER, LEVEL);
        return heal(owner, ally, other);
    }

    private static boolean heal(Character owner, Character ally, Character provider) {
        owner.setTriggerTable(new TriggerTable(OWNER,
                List.of(healRule("target has_shield from_provider self"))));
        Battle battle = new Battle(List.of(owner, ally), List.of(monster()), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();

        ally.takeDamage(ally.getMaxHp() * 0.5);
        ally.getBuffManager().addBuff(new ShieldBuff(provider, 700, 3));

        double before = ally.getCurrentHp();
        battle.fire(com.laosun.aluminium.models.trigger.TriggerPayload.Simple.of(
                TriggerEvent.TURN_START, ally, ally, 0, 0));
        return ally.getCurrentHp() > before;
    }

    private static TriggerSpec healRule(String condition) {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "HEAL");
        TriggerSpecs.set(effect, "amount", 10.0);
        TriggerSpecs.set(effect, "target", "target");
        return TriggerSpecs.rule("TURN_START", List.of(condition), effect);
    }

    private static Enemy monster() {
        return BattleFixture.monster();
    }
}
