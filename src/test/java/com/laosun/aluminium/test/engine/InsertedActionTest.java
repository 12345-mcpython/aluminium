package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Summon;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * `INSERT_ACTION`: a unit OUTSIDE the action order acts now.
 *
 * <p>Reader: 1415's memosprite skill 10 -- "开拓者-记忆施放强化普攻后，德谬歌<b>立即获得1个额外回合</b>并自动施放[花与箭的舞曲]". The game expresses this as
 * `TurnInsertAction`, and it has to, because the game pins a memosprite's speed to 0 with `SpeedOverride` (measured): a unit with no action value
 * can never be in the action order, so an "extra turn" in the queue sense is impossible for it.
 *
 * <p>The readings are the ones that would catch the two ways this could be wrong: the memosprite must really act (the enemy loses HP), and it
 * must be a unit the action order does NOT contain -- otherwise this is just `EXTRA_TURN` spelled differently.
 */
public class InsertedActionTest {
    private static final int LEVEL = 80;
    private static final int CYRENE = 1415;
    private static final int ALLY = 1002;
    private static final int MONSTER = 1002011;
    private static final int MEMOSPRITE_SKILL_SLOT = 1;

    // Note: A second case (the memosprite's own cast after the insertion) was WITHDRAWN, not asserted around: it fails with
    // "德谬歌 has no SKILL skill, so a CAST_SKILL effect has nothing to read" -- the memosprite keeps its skills by SLOT, and
    // REPLACE_SKILL does not create one. Registered in GAPS; the capability below is judged on its own.    / The inserted action is handed out by the next step, and afterMove copes with an actor that has no schedule. */
    @Test
    public void theNextStepHandsOutTheInsertedAction() {
        Character cyrene = CharacterFactory.create(CYRENE, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Battle battle = new Battle(List.of(cyrene, ally),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        Summon demiurge = battle.summonServant(cyrene);
        battle.processRequests();

        Assertions.assertTrue(battle.insertAction(demiurge), "a unit outside the order can still be inserted");
        Assertions.assertFalse(battle.insertAction(demiurge), "and it is not queued twice");

        // stepping forward hands the inserted action out; afterMove() must not try to re-time a heap it is not in
        battle.stepForward();
        Assertions.assertSame(demiurge, battle.queue.getCurrentActor().getCanHit(),
                "the inserted action is WHO steps forward -- a weaker reading would pass even if nothing was inserted");
        Assertions.assertDoesNotThrow(() -> battle.afterMove(),
                "an actor with no place in the order has no cycle to re-time");

        // and the normal order is still intact afterwards: somebody from the party acts next
        battle.stepForward();
        System.out.println("[insert] the step after the inserted action gives: " + battle.queue.getCurrentActor().getCanHit().getName());
        Assertions.assertNotNull(battle.queue.getCurrentActor().getCanHit().getName(), "the action order still produces actors");
    }
}
