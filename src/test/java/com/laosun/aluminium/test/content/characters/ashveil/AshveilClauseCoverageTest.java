package com.laosun.aluminium.test.content.characters.ashveil;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.trigger.TriggerPayload;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * 1504 不死途: the two readings this round pinned — the talent's layer COUNT and the Eidolon 2 cap.
 *
 * <p><b>What was wrong.</b> The talent's 「随后获得 2 层【婪酣】，最多叠加 12 层」 had been written {@code amount: 1} on the belief that
 * {@code ADD_STACK}'s amount is not the layer count. It is: {@code TriggerEffects.addStack} builds {@code wanted} from exactly that
 * field. So every trigger granted ONE layer where the document says two.
 */
public class AshveilClauseCoverageTest {

    private static final int ASHVEIL = 1504;
    private static final int ALLY = 1002;
    private static final String LAYERS = "婪酣";

    /**
     * 「随后获得 2 层【婪酣】」 — one trigger, two layers.
     */
    @Test
    public void theTalentGrantsTwoLayers() {
        Fixture f = new Fixture(0);
        Assertions.assertEquals(0, f.layers(), "precondition: no layers yet");
        f.triggerTalent();
        Assertions.assertEquals(2, f.layers(), "「随后获得 2 层【婪酣】」 — two, not one");
    }

    /**
     * 星魂 2: 「【婪酣】叠加上限提高至 18 层」 — the base cap is 12, and ten triggers stop at the Eidolon's 18.
     */
    @Test
    public void eidolon2RaisesTheLayerCapToEighteen() {
        Fixture base = new Fixture(0);
        for (int i = 0; i < 10; i++) {
            base.triggerTalent();
        }
        Assertions.assertEquals(12, base.layers(), "the base cap is 12");

        Fixture e2 = new Fixture(2);
        for (int i = 0; i < 10; i++) {
            e2.triggerTalent();
        }
        Assertions.assertEquals(18, e2.layers(), "「叠加上限提高至 18 层」");
    }

    // ==================================================================

    private static final class Fixture {
        private final Character ashveil;
        private final Character ally = CharacterFactory.create(ALLY, 80);
        private final Enemy enemy = BattleFixture.enemy(BattleFixture.MONSTER_ID, BattleFixture.MONSTER_LEVEL);
        private final Battle battle;

        private Fixture(int eidolon) {
            ashveil = CharacterFactory.create(ASHVEIL, 80, false, null, null, eidolon);
            battle = new Battle(List.of(ashveil, ally), List.of(enemy), BattleFixture.seeded());
            battle.startBattle();
            battle.processRequests();
            // the bait is what the talent reacts to
            ashveil.getBuffManager();
            battle.fire(TriggerPayload.Simple.of(TriggerEvent.ALLY_ATTACK, ally, enemy, 0, 0));
            battle.processRequests();
        }

        /**
         * Fires the talent's door once: an ALLY_ATTACK by a teammate on the baited enemy.
         *
         * <p>The bait state is put on the enemy through the engine's own rule: 秘技/终结技 mark it, so the fixture marks it directly
         * with the same state name the rules read.
         */
        private void triggerTalent() {
            // the talent spends one charge per trigger; top it up so the fixture can fire it repeatedly
            ashveil.getResources().get("充能").setValue(3);
            if (!enemy.getBuffManager().hasState("饲饵")) {
                enemy.getBuffManager().addBuff(new com.laosun.aluminium.buff.StateBuff("饲饵", 99, true));
            }
            battle.fire(TriggerPayload.Simple.of(TriggerEvent.ALLY_ATTACK, ally, enemy, 0, 0));
            battle.processRequests();
        }

        private int layers() {
            return ashveil.getBuffManager().stacksOf(LAYERS);
        }
    }
}

