package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1412 刻律德菈's 战技:「使指定我方单体角色获得【军功】」 — the first reader of the {@code Buff} shape in
 * {@code skill_effects.json} (2026-10-02).
 *
 * <p>Her skill is a {@code Support} one, so its effect is delivered by the engine rather than by a rule:
 * {@code SkillExecutor.dispatchNonDamaging} reads the row and attaches the named state to the cast's targets. The
 * claim is therefore about <b>which</b> unit ends up carrying it — the ally it was cast on, and not the other one.
 */
public class MilitaryMeritTest {
    private static final int CERYDRA = 1412;
    private static final int ALLY = 1002;
    private static final int OTHER = 1004;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final String MERIT = "军功";

    /** ⭐ Casting the skill marks the chosen ally — and the text states no duration, so it does not expire on its own. */
    @Test
    public void theSkillMarksTheChosenAlly() {
        Character cerydra = CharacterFactory.create(CERYDRA, LEVEL, false, null, null, 0);
        Character ally = CharacterFactory.create(ALLY, LEVEL, false, null, null, 0);
        ally.setTriggerTable(new TriggerTable(ALLY, List.of()));
        Character other = CharacterFactory.create(OTHER, LEVEL, false, null, null, 0);
        other.setTriggerTable(new TriggerTable(OTHER, List.of()));
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(cerydra, ally, other), List.of(enemy), new Random(0));
        battle.startBattle();

        Assertions.assertFalse(ally.getBuffManager().hasState(MERIT), "precondition: nothing marks the ally yet");
        cerydra.getSkills().get(SkillType.SKILL).execute(battle, cerydra, List.of(ally));
        battle.processRequests();

        Assertions.assertTrue(ally.getBuffManager().hasState(MERIT),
                "「使指定我方单体角色获得【军功】」: the cast's own target carries it");
        Assertions.assertFalse(other.getBuffManager().hasState(MERIT),
                "and the ally it was NOT cast on does not (the row is delivered to the cast's targets, not to everyone)");
    }
}
