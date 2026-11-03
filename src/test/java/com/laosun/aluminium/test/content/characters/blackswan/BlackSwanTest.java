package com.laosun.aluminium.test.content.characters.blackswan;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 130Black Swan, from her own file: the Arcana stack with its 50 cap, and the Skill's defence shred.
 */
public class BlackSwanTest {
    private static final int BLACK_SWAN = 1307;
    private static final int LEVEL = 80;

    /**
     * Note: One Arcana per Skill, stopped at the document's fifty, and exactly 20.80% of the enemy's own defence.
     */
    @Test
    public void theSkillStacksArcanaAndShredsDefence() {
        Character blackSwan = CharacterFactory.create(BLACK_SWAN, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(blackSwan), List.of(enemy), fixed());
        battle.startBattle();
        double defenceBefore = enemy.getAttribute(AttributeType.DEFENCE).get();
        Assertions.assertTrue(defenceBefore > 0, "the fixture must have defence to shred: " + defenceBefore);
        Assertions.assertEquals(0, enemy.getBuffManager().stacksOf("奥迹"), "nothing before the Skill");

        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.SKILL_CAST, blackSwan, enemy, 0, 0));
        Assertions.assertEquals(1, enemy.getBuffManager().stacksOf("奥迹"),
                "「使目标…陷入1层【奥迹】」 (puts the target ... into 1 stack of [奥迹])");
        Assertions.assertEquals(0.208, (defenceBefore - enemy.getAttribute(AttributeType.DEFENCE).get()) / defenceBefore, 1e-6,
                "「防御力降低20.80%」 (lowers DEF by 20.80%) of its own defence");

        for (int i = 0; i < 60; i++) {
            battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.SKILL_CAST, blackSwan, enemy, 0, 0));
        }
        Assertions.assertEquals(50, enemy.getBuffManager().stacksOf("奥迹"),
                "「【奥迹】最多叠加50层」 ([奥迹] stacks at most 50 times) -- sixty-one casts must still read fifty");
    }

    /**
     * 「使目标**与相邻目标**陷入 1 层【奥迹】…防御力降低 20.80%」 — the adjacent halves, which the file used to register as
     * unwritable because it thought adjacency was only a damage shape. It is a SELECTOR ({@code adjacent_enemies}), so the 1 layer
     * and the shred reach the neighbours of the aimed enemy, and nobody else.
     */
    @Test
    public void theSkillReachesTheNeighboursOfTheAimedEnemy() {
        Character blackSwan = CharacterFactory.create(BLACK_SWAN, LEVEL);
        Enemy left = BattleFixture.monster();
        Enemy aimed = BattleFixture.monster();
        Enemy right = BattleFixture.monster();
        Battle battle = new Battle(List.of(blackSwan), List.of(left, aimed, right), fixed());
        battle.startBattle();
        double aimedDefence = aimed.getAttribute(AttributeType.DEFENCE).get();
        double leftDefence = left.getAttribute(AttributeType.DEFENCE).get();

        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.SKILL_CAST, blackSwan, aimed, 0, 0));

        Assertions.assertEquals(1, aimed.getBuffManager().stacksOf("奥迹"), "the aimed enemy");
        Assertions.assertEquals(1, left.getBuffManager().stacksOf("奥迹"), "「与相邻目标」 — the neighbour too");
        Assertions.assertEquals(1, right.getBuffManager().stacksOf("奥迹"), "both neighbours");
        Assertions.assertEquals(0.208, (leftDefence - left.getAttribute(AttributeType.DEFENCE).get()) / leftDefence, 1e-6,
                "the neighbour's defence is lowered by the same 20.80%");
        Assertions.assertTrue(aimedDefence > 0, "the fixture must have defence to shred");
    }

    private static Random fixed() {
        return BattleFixture.alwaysRoll(0.0);
    }
}
