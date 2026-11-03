package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.buff.StateBuff;
import com.laosun.aluminium.buff.VulnerabilityBuff;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * {@code while_holder_state}: a modifier that lives while the unit CARRYING it holds a state, as opposed to {@code while_state},
 * whose gate asks the RULE OWNER.
 *
 * <p>This is the engine half on its own, with no content file involved — the point is to tell a broken gate apart from a broken
 * DSL wiring, which is exactly the question the first attempt could not answer.
 */
public class HolderStateGateTest {

    @Test
    public void aModifierGatedOnItsHoldersStateIsDroppedWithIt() {
        Character ally = CharacterFactory.create(1002, 80);
        Enemy enemy = BattleFixture.enemy(BattleFixture.MONSTER_ID, BattleFixture.MONSTER_LEVEL);
        Battle battle = new Battle(List.of(ally), List.of(enemy), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();

        VulnerabilityBuff bonus = new VulnerabilityBuff(2, 0.25, true, null, null, null);
        bonus.gateHolder("揭露");
        enemy.getBuffManager().addBuff(bonus);
        enemy.getBuffManager().addBuff(new StateBuff("揭露", 2));
        Assertions.assertEquals(1, enemy.getBuffManager().allBuffsOf(VulnerabilityBuff.class).size(),
                "precondition: the modifier is on the victim");

        // Still gated in: the state is there, so a turn boundary leaves the modifier alone.
        enemy.getBuffManager().beforeMove();
        Assertions.assertEquals(1, enemy.getBuffManager().allBuffsOf(VulnerabilityBuff.class).size(),
                "the state is still held, so the modifier stays");

        enemy.getBuffManager().removeState("揭露");
        enemy.getBuffManager().beforeMove();

        Assertions.assertEquals(0, enemy.getBuffManager().allBuffsOf(VulnerabilityBuff.class).size(),
                "the gate drops the modifier when its holder loses the state");
    }
}
