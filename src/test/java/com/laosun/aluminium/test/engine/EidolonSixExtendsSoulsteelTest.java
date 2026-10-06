package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.models.skill.Skill;
import com.laosun.aluminium.models.skill.SkillExecutor;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1209："消灭敌方目标时，如果当前持有[智剑连心]…则使这些增益效果的持续时间全部延长 1 回合".
 *
 * <p>ONE VARIABLE: the eidolon rank. Everything else -- her skill (which applies [智剑连心] for one turn), the kill, the tick --
 * is identical, so the survival of the state after one tick is exactly what eidolon six buys.
 */
public class EidolonSixExtendsSoulsteelTest {
    private static final int YANQING = 1209;
    private static final int MONSTER = 1002011;
    private static final String STATE = "智剑连心";

    /** At E6 the kill lengthens it by a turn, so it outlives the tick that would have ended it. */
    @Test
    public void atEidolonSixTheKillLengthensIt() {
        Assertions.assertTrue(survivesTheTick(6), "「使这些增益效果的持续时间全部延长 1 回合」");
    }

    /** Note: Below E6 the rule is off, so one tick ends it. */
    @Test
    public void belowEidolonSixItEnds() {
        Assertions.assertFalse(survivesTheTick(0), "星魂 6 才有这一条");
    }

    // ==================================================================

    private static boolean survivesTheTick(int eidolon) {
        Character her = CharacterFactory.create(YANQING, 80, false, null, null, eidolon);
        Battle battle = new Battle(List.of(her),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();

        // Her skill applies [智剑连心] for one turn.
        Skill skill = her.getSkills().get(SkillType.SKILL);
        Assertions.assertNotNull(skill, "precondition: she has a skill");
        SkillExecutor.execute(battle, skill, her, List.of(battle.enemies.getFirst()));
        battle.processRequests();
        Assertions.assertTrue(her.getBuffManager().hasState(STATE), "precondition: the skill applied it");

        // She lands the kill, so the EVENT's actor is her -- which the rule requires ("actor == self").
        battle.applyTrueDamage(her, battle.enemies.getFirst(), DamageElement.ICE,
                battle.enemies.getFirst().getCurrentHp());
        battle.processRequests();
        Assertions.assertTrue(battle.enemies.getFirst().isDeath(), "precondition: the enemy died");

        // Note: The decrement site measured in round 1619: `BuffManager.beforeMove()` -> `processBuffTick`.
        her.getBuffManager().beforeMove();
        her.getBuffManager().afterMove();
        return her.getBuffManager().hasState(STATE);
    }
}
