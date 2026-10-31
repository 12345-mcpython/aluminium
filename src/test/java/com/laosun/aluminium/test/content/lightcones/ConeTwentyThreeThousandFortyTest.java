package com.laosun.aluminium.test.content.lightcones;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.equipment.Weapon;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 让告别，更美一些 (23040): 「当装备者的忆灵消失时，使装备者行动提前 <b>12%</b>。该效果最多触发 <b>1</b> 次，装备者每次施放终结技时
 * <b>重置</b>触发次数」.
 *
 * <p><b>The 「可复位旗标」 that was called missing.</b> It is two rules: a one-stack marker gates the advance, and an ultimate takes
 * that marker off again - "reset the count" is "remove the flag".
 */
public class ConeTwentyThreeThousandFortyTest {

    private static final int LEVEL = 80;
    // A wearer WITH a memosprite: the clause is 「装备者的忆灵消失时」, so the fixture needs one to lose.
    private static final int WEARER = 1415;

    private static final class Run {
        private final Character wearer;
        private final Enemy enemy;
        private final Battle battle;

        Run() {
            wearer = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(23040, LEVEL, false, 1));
            enemy = BattleFixture.monster();
            battle = new Battle(new ArrayList<>(List.of(wearer)), new ArrayList<>(List.of(enemy)),
                    BattleFixture.seeded());
            battle.startBattle();
        }

        void summonLeaves() {
            // `actor is_summon` is the condition; a plain fire with the wearer's own memosprite is the shape the rule reads.
            battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(
                    TriggerEvent.SUMMON_LEFT, battle.summonMemosprite(wearer), wearer, 0, 0));
            battle.processRequests();
        }

        boolean armed() {
            return wearer.getBuffManager().stacksOf("送别的花") >= 1;
        }
    }

    /**
     * One departure arms the advance; a second one does not, until an ultimate resets it.
     */
    @Test
    public void oneDeparturePaysAndAnUltimateRearmsIt() {
        Run run = new Run();
        Assertions.assertFalse(run.armed(), "nothing spent yet");
        run.summonLeaves();
        Assertions.assertTrue(run.armed(), "the first departure spends the one allowed trigger");

        run.summonLeaves();
        Assertions.assertTrue(run.armed(), "a second departure does not spend another - 「最多触发 1 次」");

        run.wearer.setCurrentEnergy(run.wearer.getMaxEnergy());
        run.battle.castImmediate(run.wearer.getSkills().get(com.laosun.aluminium.enums.SkillType.ULTRA),
                run.wearer, List.of(run.enemy));
        run.battle.processRequests();
        Assertions.assertFalse(run.armed(), "「每次施放终结技时重置触发次数」 - the flag is off again");
    }
}
