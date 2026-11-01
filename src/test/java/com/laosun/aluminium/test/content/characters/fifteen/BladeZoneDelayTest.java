package com.laosun.aluminium.test.content.characters.fifteen;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.entity.Countdown;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.SkillCategory;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.trigger.TriggerPayload;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 1507 千冶•刃's Eidolon 1, second half: 「施放天赋的额外战技后使【无量忿怒】倒计时行动延后 15%」.
 *
 * <p>Reads the countdown's remaining time off the action queue: the delay is 15% of its period (10000 / speed).
 */
public class BladeZoneDelayTest {

    private static final int BLADE = 1507;
    private static final int LEVEL = 80;
    /** The Talent's extra skill row. */
    private static final int TALENT_CAST = 150705;

    @Test
    public void theTalentExtraSkillDelaysTheZoneCountdown() {
        Character blade = CharacterFactory.create(BLADE, LEVEL, true, null, null, 1);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(new ArrayList<>(List.of(blade, CharacterFactory.create(1001, LEVEL))),
                new ArrayList<>(List.of(enemy)), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        battle.castImmediate(blade.getSkills().get(SkillType.ULTRA), blade, List.of(enemy));
        Countdown zone = battle.countdowns().get(battle.countdowns().size() - 1);
        double before = remaining(battle, zone);
        Assertions.assertTrue(before > 0, "precondition: the zone countdown is on the action order");

        battle.fire(new TriggerPayload.Cast(TriggerEvent.SKILL_CAST, blade, enemy, 1, 0.0,
                SkillCategory.BPSKILL, TALENT_CAST, 0, List.of(enemy)));
        double after = remaining(battle, zone);
        System.out.println("[1507] countdown remaining " + before + " -> " + after);
        Assertions.assertEquals(before + 10000.0 / zone.getAttribute(com.laosun.aluminium.enums.AttributeType.SPEED).get() * 0.15,
                after, 1e-6, "「倒计时行动延后 15%」");
    }

    private static double remaining(Battle battle, Countdown zone) {
        for (var signal : battle.queue().snapshot()) {
            if (signal.getCanHit() == zone) {
                return battle.queue().getTimeRemaining(signal);
            }
        }
        return -1;
    }
}