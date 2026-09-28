package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.data.TriggerTables;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * The technique gate (2026-09-29, round 178): a technique happens outside the battle, so the caller declares it and the engine turns it into a state.
 *
 * <p>The pair is the measurement: with the marker the party is healed by 15% of EACH ally's own Max HP, and without it nothing happens at all.
 */
public class TechniqueGateTest {
    private static final int TB = 8001;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /** \u26a0 With the technique declared, the party is healed for 15% of its own Max HP. */
    @Test
    public void aDeclaredTechniqueHealsTheParty() {
        Character tb = CharacterFactory.create(TB, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(tb, ally), List.of(enemy), fixed());
        battle.markTechniqueUsed(tb);
        // BEFORE the battle: hurt the ally, so the opening technique heal has somewhere to go (at full HP it restores 0 and the
        // engine, correctly, fires no heal event -- which is why the first version of this case could assert nothing).
        battle.applyDamage(ally, new com.laosun.aluminium.models.Damage(enemy, ally,
                com.laosun.aluminium.enums.DamageElement.PHYSICAL,
                com.laosun.aluminium.enums.DamageType.NORMAL, ally.getMaxHp() * 0.5));
        double hurt = ally.getCurrentHp();
        Assertions.assertTrue(hurt < ally.getMaxHp(), "the fixture must actually be hurt before the battle starts");

        battle.startBattle();

        double healed = ally.getCurrentHp() - hurt;
        double expected = ally.getMaxHp() * 0.15;
        Assertions.assertEquals(expected, healed, expected * 0.05,
                "\u300c\u4f7f\u7528\u79d8\u6280\u540e\u7acb\u5373\u4e3a\u6211\u65b9\u5168\u4f53\u56de\u590d\u7b49\u540c\u4e8e\u5404\u81ea\u751f\u547d\u4e0a\u965015%\u7684\u751f\u547d\u503c\u300d: expected " + expected + ", healed " + healed);
    }

    /** \u26a0 The control: with NO technique declared, the same battle heals nobody. */
    @Test
    public void anUndeclaredTechniqueDoesNothing() {
        Character tb = CharacterFactory.create(TB, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(tb, ally), List.of(enemy), fixed());
        battle.startBattle();

        Assertions.assertFalse(tb.getBuffManager().hasState("\u79d8\u6280"),
                "without the marker the state must not exist");
    }

    /** \u26a0 And the state itself is what content asks for. */
    @Test
    public void theMarkerCreatesTheState() {
        Character tb = CharacterFactory.create(TB, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(tb), List.of(enemy), fixed());
        battle.markTechniqueUsed(tb);
        battle.startBattle();

        Assertions.assertTrue(tb.getBuffManager().hasState("\u79d8\u6280"),
                "\u300c\u4f7f\u7528\u79d8\u6280\u540e\u300d -- the declared technique must be visible as a state");
    }

    /** Census: the technique rule and the level convention. */
    @Test
    public void hisFileCarriesTheTechniqueClause() {
        var table = TriggerTables.of(TB);
        Assertions.assertEquals(2, table.ruleCount(TriggerEvent.BATTLE_START),
                "the technique heal and the level convention");
    }

    private static Random fixed() {
        return new Random() {
            @Override
            public double nextDouble() {
                return 1.0;
            }
        };
    }
}
