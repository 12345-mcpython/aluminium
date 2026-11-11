package com.laosun.aluminium.test.content.characters.lingso1222;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.trigger.TriggerPayload;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * 1222 灵砂's Eidolon 1, second half: 「当有敌方单位的弱点被击破时，使其防御力降低 20%」.
 *
 * <p><b>Why the lifetime is the interesting half.</b> The document and the parameter row state no duration, so `turns`
 * would be invented and `permanent` would be plainly wrong. The clause is tied to the break STATE instead
 * ({@code while_holder_state: 弱点击破}, the engine's own {@code BuffManager.BROKEN_STATE}): the reduction lives exactly as
 * long as the target is broken, and it needs no number at all.
 */
public class LingsoBreakDefenceTest {
    private static final int LINGSO = 1222;
    private static final int LEVEL = 80;

    /**
     * A weakness break lowers the broken enemy's DEFENCE by 20%, and the reduction leaves with the break.
     */
    @Test
    public void aBreakLowersTheDefenceAndItLeavesWithTheBreak() {
        Run run = new Run(1);
        double bare = run.enemy.getAttribute(AttributeType.DEFENCE).get();

        run.breakTheEnemy();
        double broken = run.enemy.getAttribute(AttributeType.DEFENCE).get();
        Assertions.assertEquals(0.2 * bare, bare - broken, 1e-6,
                "「使其防御力降低 20%」 - 20% of the base DEF");

        run.enemy.getBuffManager().removeState(Run.BROKEN);
        run.battle.setCurrentMove(new com.laosun.aluminium.Signal(run.enemy));
        run.battle.beforeMove();
        run.battle.processRequests();

        Assertions.assertEquals(bare, run.enemy.getAttribute(AttributeType.DEFENCE).get(), 1e-6,
                "the gate is `while_holder_state: 弱点击破`, so recovering takes the reduction with it");
    }

    /**
     * The control: at E0 a break changes nothing.
     */
    @Test
    public void withoutTheFirstEidolonABreakChangesNothing() {
        Run run = new Run(0);
        double bare = run.enemy.getAttribute(AttributeType.DEFENCE).get();
        run.breakTheEnemy();
        Assertions.assertEquals(bare, run.enemy.getAttribute(AttributeType.DEFENCE).get(), 1e-6,
                "E0 states no such reduction");
    }

    // ==================================================================

    private static final class Run {
        static final String BROKEN = "弱点击破";
        private final Character lingso;
        private final Enemy enemy;
        private final Battle battle;
        private final double enemyBareDefence;

        Run(int eidolon) {
            lingso = CharacterFactory.create(LINGSO, LEVEL);
            lingso.setEidolonRank(eidolon);
            enemy = BattleFixture.monster();
            enemyBareDefence = enemy.getAttribute(AttributeType.DEFENCE).baseValue();
            battle = new Battle(List.of(lingso), List.of(enemy), BattleFixture.seeded());
            battle.startBattle();
            battle.processRequests();
        }

        /** The break itself: the engine announces BREAK with the attacker and the broken enemy. */
        void breakTheEnemy() {
            enemy.getBuffManager().addBuff(new com.laosun.aluminium.buff.StateBuff(BROKEN, 2));
            battle.fire(TriggerPayload.Simple.of(TriggerEvent.BREAK, lingso, enemy, 0, 0));
            battle.processRequests();
        }
    }
}
