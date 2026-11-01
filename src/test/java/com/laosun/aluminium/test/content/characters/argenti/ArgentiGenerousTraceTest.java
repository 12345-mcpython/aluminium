package com.laosun.aluminium.test.content.characters.argenti;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 1302 银枝's trace 慷慨: 「在敌方目标进入战斗时，自身立即恢复 2 点能量」 - the reader of the {@code ENEMY_ENTERED} event.
 *
 * <p>What the event promises: an enemy that <b>joins the enemy roster after the battle started</b> raises it once, with the
 * newcomer as actor; adding the same unit twice raises nothing the second time; an ally joining is not an enemy entering;
 * and a roster that was laid out before {@code startBattle} has not "entered" (nothing fires then).
 */
public class ArgentiGenerousTraceTest {

    private static final int LEVEL = 80;
    private static final int ARGENTI = 1302;
    private static final int ALLY = 1001;
    private static final double TRACE_ENERGY = 2.0;

    /**
     * A mid-battle enemy, through either door, pays exactly the trace's 2 energy.
     */
    @Test
    public void anEnemyEnteringPaysTheTrace() {
        Fixture f = new Fixture();
        double before = f.argenti.getCurrentEnergy();
        f.battle.addEnemy(BattleFixture.monster());
        Assertions.assertEquals(before + TRACE_ENERGY, f.argenti.getCurrentEnergy(), 1e-9,
                "addEnemy: 「在敌方目标进入战斗时，立即恢复 2 点能量」");

        double afterFirst = f.argenti.getCurrentEnergy();
        f.battle.addCombatant(BattleFixture.monster());
        Assertions.assertEquals(afterFirst + TRACE_ENERGY, f.argenti.getCurrentEnergy(), 1e-9,
                "addCombatant: the other door into the enemy roster");
    }

    /**
     * The same unit twice pays once; an ally joining is not an enemy entering.
     */
    @Test
    public void aRepeatOrAnAllyPaysNothing() {
        Fixture f = new Fixture();
        Enemy once = BattleFixture.monster();
        f.battle.addEnemy(once);
        double afterFirst = f.argenti.getCurrentEnergy();
        f.battle.addEnemy(once);
        Assertions.assertEquals(afterFirst, f.argenti.getCurrentEnergy(), 1e-9, "the same enemy twice pays once");

        f.battle.addCombatant(CharacterFactory.create(ALLY, LEVEL));
        Assertions.assertEquals(afterFirst, f.argenti.getCurrentEnergy(), 1e-9, "an ally is not an entering enemy");
    }

    /**
     * A roster laid out before the battle starts has not "entered": the opening pays nothing for it.
     */
    @Test
    public void anEnemyAddedBeforeTheBattlePaysNothing() {
        Fixture f = new Fixture();
        double baselineAfterStart = f.argenti.getCurrentEnergy();

        Fixture pre = new Fixture(false);
        pre.battle.addEnemy(BattleFixture.monster());
        pre.battle.startBattle();
        Assertions.assertEquals(baselineAfterStart, pre.argenti.getCurrentEnergy(), 1e-9,
                "added before startBattle: no ENEMY_ENTERED, so no trace energy");
    }

    // ==================================================================

    private static final class Fixture {
        private final Character argenti;
        private final Battle battle;

        private Fixture() {
            this(true);
        }

        private Fixture(boolean start) {
            argenti = CharacterFactory.create(ARGENTI, LEVEL);
            battle = new Battle(new ArrayList<>(List.of(argenti, CharacterFactory.create(ALLY, LEVEL))),
                    new ArrayList<>(List.of(BattleFixture.monster())), BattleFixture.seeded());
            if (start) {
                battle.startBattle();
            }
        }
    }
}
