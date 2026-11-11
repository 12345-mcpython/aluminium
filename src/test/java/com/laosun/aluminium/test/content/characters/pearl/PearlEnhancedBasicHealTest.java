package com.laosun.aluminium.test.content.characters.pearl;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.SkillCategory;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.EnemyFactory;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Character 1503 (Pearl): an enhanced basic heals the party and the lowest-HP ally a second time.
 *
 * <p>Note: The enhanced form has to be <b>installed</b> first, and that is not decoration - the heal reads its numbers off
 * the row of the skill in the COMMON slot ({@code COMMON:1} / {@code COMMON:3}), so firing the slot's event while the
 * ordinary basic still sits there would read a one-column row. The install is driven the real way: her Ultimate
 * REPLACE_SKILLs the form in.
 */
public class PearlEnhancedBasicHealTest {
    private static final int PEARL = 1503;
    private static final int ALLY = 1204;
    private static final int ELATION = 1502;
    private static final int HUNTER_SLOT = 1;   // the ordinary basic, which must heal nobody

    @Test
    public void theEnhancedBasicHealsTwiceAndAnOrdinaryOneDoesNot() {
        double[] eight = gains(8);
        double[] one = gains(HUNTER_SLOT);
        System.out.println("[pearl_eb] slot 8 -> pearl gained " + eight[0] + " ; the lowest ally gained "
                + eight[1] + " | ordinary slot 1 -> " + one[0] + " / " + one[1]);

        Assertions.assertTrue(eight[0] > 0, "the enhanced basic heals Pearl too");
        Assertions.assertTrue(eight[1] > eight[0], "and the lowest ally is healed a SECOND time");
        Assertions.assertEquals(0.0, one[0], 1e-9, "an ordinary basic hits no heal rule");
        Assertions.assertEquals(0.0, one[1], 1e-9, "for anyone");
    }

    /**
     * Fires a basic attack at the given slot and returns Pearl's and the lowest ally's HP gains.
     *
     * <p>For slot 8 the Elation ally is the one her Ultimate is aimed at, so the form installed into COMMON is the slot-8
     * one - asserted, because a fixture that failed to install would measure the ordinary row instead.
     */
    private static double[] gains(int slot) {
        Battle battle = new Battle(
                List.of(CharacterFactory.create(PEARL, 80), CharacterFactory.create(ALLY, 80),
                        CharacterFactory.create(ELATION, 80)),
                List.of(EnemyFactory.create(1002011, 100, 1)), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        Character pearl = battle.characters().get(0);
        Character other = battle.characters().get(1);
        if (slot != HUNTER_SLOT) {
            battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(
                    TriggerEvent.ULT_CAST, pearl, battle.characters().get(2), 0, 0));
            battle.processRequests();
            Assertions.assertEquals(slot, pearl.getSkills().get(SkillType.COMMON).getSkillSlot(),
                    "precondition: the Ultimate installed the enhanced form this case is about");
        }
        pearl.takeDamage(pearl.getMaxHp() * 0.6);
        other.takeDamage(other.getMaxHp() * 0.8);

        double pearlBefore = pearl.getCurrentHp();
        double otherBefore = other.getCurrentHp();
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Cast.of(TriggerEvent.BASIC_ATTACK, pearl, battle.enemies().getFirst(), 1, 0,
                (SkillCategory) null, slot, 0, java.util.List.of()));
        return new double[]{pearl.getCurrentHp() - pearlBefore, other.getCurrentHp() - otherBefore};
    }
}
