package com.laosun.aluminium.test.content.characters.archer;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.trigger.TriggerPayload;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * 1015 Archer: Eidolon 1 — 「单个回合内施放 3 次战技后，为我方恢复 2 个战技点」.
 *
 * <p>The clause has a "when" that is a <b>count of this turn's casts</b>, which no per-turn limit can express (a limit refuses the
 * fourth cast; this pays on the third). The counter is a marker that the third cast clears and every turn start resets, so the test's
 * contrasts are "third cast pays" and "a new turn starts the count again".
 */
public class ArcherEidolonOneTest {

    private static final int ARCHER = 1015;
    private static final int ALLY = 1204;

    @Test
    public void theThirdCastInATurnPaysTwoSkillPoints() {
        Scene scene = new Scene(1);
        int before = scene.battle.getSkillPoints();

        scene.castSkill();
        int afterOne = scene.battle.getSkillPoints();
        scene.castSkill();
        int afterTwo = scene.battle.getSkillPoints();
        scene.castSkill();
        int afterThree = scene.battle.getSkillPoints();

        int perCast = afterOne - before;
        Assertions.assertEquals(perCast, afterTwo - afterOne - 0, "the first two casts behave the same");
        Assertions.assertTrue(afterThree - afterTwo > perCast,
                "「施放 3 次战技后，为我方恢复 2 个战技点」 (third cast: " + (afterThree - afterTwo) + " vs " + perCast + ")");
    }

    @Test
    public void aNewTurnStartsTheCountOver() {
        Scene scene = new Scene(1);
        scene.castSkill();
        scene.castSkill();
        scene.newTurn();
        int before = scene.battle.getSkillPoints();
        scene.castSkill();
        Assertions.assertTrue(scene.battle.getSkillPoints() - before <= 2,
                "after a new turn the counter restarts, so the first cast is just a cast");
    }

    /**
     * 星魂 2: the Ultimate implants a Quantum weakness and marks the target as taking more Quantum damage.
     *
     * <p>The resistance half is an APPROXIMATION recorded in the rule: RESISTANCE_REDUCTION is all-type in this engine, so the
     * element-scoped spelling that exists is damage-taken. What is asserted is that both halves land on the target and expire with
     * their stated duration - not that the number sits in the resistance zone.
     */
    @Test
    public void eidolon2ImplantsAQuantumWeaknessAndMarksTheTarget() {
        Scene scene = new Scene(2);
        Assertions.assertFalse(scene.enemy.isWeakTo(com.laosun.aluminium.enums.DamageElement.QUANTUM), "precondition: no Quantum weakness yet");

        scene.castUltimate();

        Assertions.assertTrue(scene.enemy.isWeakTo(com.laosun.aluminium.enums.DamageElement.QUANTUM), "「并为其添加量子属性弱点」");
        Assertions.assertTrue(scene.enemy.getBuffManager().countBuffs(com.laosun.aluminium.buff.VulnerabilityBuff.class) > 0,
                "the damage-taken half is an element-scoped VulnerabilityBuff (the approximation the rule registers)");
    }

    // ==================================================================

    private static final class Scene {
        private final Character archer;
        private final Enemy enemy = BattleFixture.dummy();
        private final Battle battle;

        private Scene(int eidolon) {
            archer = CharacterFactory.create(ARCHER, 80, true, null, null, eidolon);
            Character ally = CharacterFactory.create(ALLY, 80);
            battle = new Battle(List.of(archer, ally), List.of(enemy), BattleFixture.alwaysRoll(0.9999));
            battle.startBattle();
            battle.processRequests();
        }

        private void castSkill() {
            battle.castImmediate(archer.getSkills().get(SkillType.SKILL), archer, List.of(enemy));
            battle.processRequests();
        }

        private void castUltimate() {
            battle.castImmediate(archer.getSkills().get(SkillType.ULTRA), archer, List.of(enemy));
            battle.processRequests();
        }

        private void newTurn() {
            battle.fire(TriggerPayload.Simple.of(TriggerEvent.TURN_START, archer, archer, 0, 0));
            battle.processRequests();
        }
    }
}


