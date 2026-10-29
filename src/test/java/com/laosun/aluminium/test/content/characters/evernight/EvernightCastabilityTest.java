package com.laosun.aluminium.test.content.characters.evernight;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.entity.Summon;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * The last clause of 1413's 忆灵技「迷梦，流失，如露」(1141307): 「长夜月的【忆质】大于等于 #3 点…时施放」.
 *
 * <p><b>Why this needs a judge of its own.</b> Before the engine had a castability spelling, the sentence was simply
 * not enforced: the servant cast anyway and, because the row prices the hit <i>per point</i>, a short counter produced a
 * smaller number (0 points produced 0) - a wrong answer with no symptom. The gate is now stated by the row itself
 * ({@code castable_when} in {@code skill_effects.json}) and checked at the one cast entry, so the failure is a
 * <b>refusal</b>: nothing settles, and the counter is not spent.
 *
 * <p>16 is the row's #3, the same at every level ({@code [0.06, 0.03, 16]} … {@code [0.168, 0.084, 16]}).
 */
public class EvernightCastabilityTest {

    private static final int OWNER = 1413;
    private static final String MEMORY = "亿质";

    /**
     * 15 points: refused - and refused BEFORE the spell settles, so nothing is consumed.
     */
    @Test
    public void fifteenPointsCannotCast() {
        Scene scene = sceneWith(15);

        Assertions.assertFalse(scene.cast(),
                "\"【忆质】>= 16 to cast\" must refuse a cast at 15 points");
        Assertions.assertEquals(15, scene.owner.getResources().get(MEMORY).value(),
                "a refused cast spends nothing");
    }

    /**
     * 16 points: allowed, and the price is paid (the spend is the rule's own half, so this asserts the cast happened).
     */
    @Test
    public void sixteenPointsCanCast() {
        Scene scene = sceneWith(16);

        Assertions.assertTrue(scene.cast(), "16 points meets the row's #3");
    }

    private record Scene(Character owner, Summon servant, Battle battle) {
        private boolean cast() {
            return battle.castImmediate(servant.skillAt(7), servant, List.of(battle.enemies().getFirst()));
        }
    }

    private static Scene sceneWith(int points) {
        Character owner = CharacterFactory.create(OWNER, 80);
        Battle battle = new Battle(List.of(owner), List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        battle.summonMemosprite(owner);
        Summon servant = battle.memospriteOf(owner);
        Assertions.assertNotNull(servant, "长夜 must be on the field for its slot 7 to exist");
        Assertions.assertNotNull(servant.skillAt(7), "data slot 7 is 「迷梦，流失，如露」");
        owner.getResources().get(MEMORY).setValue(points);
        return new Scene(owner, servant, battle);
    }
}
