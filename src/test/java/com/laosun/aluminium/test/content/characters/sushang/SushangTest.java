package com.laosun.aluminium.test.content.characters.sushang;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.data.TriggerTables;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1206 Sushang, from her own file: the talent that answers a weakness break, and the Sword Stance pair.
 *
 * <p>The talent is the interesting one: it hangs on the engine's `BREAK` event, which nothing had used yet. Its observable is her own
 * SPEED, so no damage comparison (and no level-convention confound) is involved.
 */
public class SushangTest {
    private static final int SUSHANG = 1206;
    private static final int LEVEL = 80;

    /**
     * Note: "当场上有敌方目标的弱点被击破": the BREAK event, which fires with the broken unit as its target.
     */
    @Test
    public void aWeaknessBreakRaisesHerSpeed() {
        Fixture f = new Fixture();
        double before = f.sushang.getAttribute(AttributeType.SPEED).get();

        f.battle.fire(com.laosun.aluminium.models.trigger.TriggerPayload.Simple.of(TriggerEvent.BREAK, f.sushang, f.enemy, 0, 0));

        Assertions.assertTrue(f.sushang.getAttribute(AttributeType.SPEED).get() > before,
                "\"when an enemy target's Weakness is Broken on the field, Sushang's SPD is raised by 20% for 2 turns\" (「当场上有敌方目标的弱点被击破，素裳的速度提高20%，持续2回合」): "
                        + before + " -> " + f.sushang.getAttribute(AttributeType.SPEED).get());
    }

    /**
     * Census: the talent, the Sword Stance pair, and the level convention.
     */
    @Test
    public void herFileCarriesTheClauses() {
        var table = TriggerTables.of(SUSHANG);
        Assertions.assertEquals(1, table.ruleCount(TriggerEvent.BREAK), "the talent");
        // Four: the Sword Stance pair, and trace 破敌's two halves - "普攻**或**战技" has no single spelling in a
        // DSL without `or`, so it is one rule per cast slot and the count moves by two when that trace lands.
        Assertions.assertEquals(6, table.ruleCount(TriggerEvent.ALLY_ATTACK),
                "the Sword Stance pair and trace 破敌 for COMMON / SKILL");
        // Six: the technique damage, the level convention, and the four eidolons that declare at battle start
        // (3/5 raise skill levels, 4 raises BREAKING_EFFECT, 6 amends the talent's cap). `ruleCount` counts the
        // WHOLE table, eidolon gating included - it is a census of what the file states, not of what is active
        // at rank 0, so these numbers move when a clause is added.
        Assertions.assertEquals(6, table.ruleCount(TriggerEvent.BATTLE_START),
                "the technique damage, the level convention and eidolons 3/4/5/6");
    }

    private static final class Fixture {
        private final Character sushang = CharacterFactory.create(SUSHANG, LEVEL);
        private final Enemy enemy = BattleFixture.monster();
        private final Battle battle = new Battle(List.of(sushang), List.of(enemy), new Random() {
            @Override
            public double nextDouble() {
                return 0.0;
            }
        });

        private Fixture() {
            battle.startBattle();
        }
    }
}
