package com.laosun.aluminium.test.engine;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.models.skill.SkillExecutor;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Random;

/**
 * 1415's memosprite skill 21, first sentence: "整场生效，对白厄施放后，使白厄获得 #8 点[火种]" (#8 = 6).
 *
 * Two-sided: with the ode cast at him he gains exactly 6; with no ode he gains nothing. The number is the data row's own `#8`, which is a constant in ParamList.
 */
public class WorldOdeFireSeedTest {
    private static final int LEVEL = 80;
    private static final int CYRENE = 1415;
    private static final int PHAINON = 1408;
    private static final int MONSTER = 1002011;
    private static final int ODE_SLOT = 21;
    private static final String FIRE_SEED = "火种";

    @Test
    public void theOdeGivesHimSixFireSeed() {
        int with = seedAfterOde(true);
        int without = seedAfterOde(false);
        System.out.println("[fire_seed] after the ode 【火种】 (Kindling) is " + with + " with the ode ; " + without + " without it");
        Assertions.assertEquals(6, with, "the data row's #8 is 6, granted when the ode is cast at him");
        Assertions.assertEquals(0, without, "without the ode nothing is granted");
    }

    private static int seedAfterOde(boolean castTheOde) {
        Character cyrene = CharacterFactory.create(CYRENE, LEVEL);
        Character him = CharacterFactory.create(PHAINON, LEVEL);
        Battle battle = new Battle(List.of(cyrene, him),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        cyrene = battle.characters.get(0);
        him = battle.characters.get(1);
        int before = him.getResources().value(FIRE_SEED);
        if (castTheOde) {
            var sprite = battle.summonServant(cyrene);
            battle.processRequests();
            var ode = sprite.skillAt(ODE_SLOT);
            Assertions.assertNotNull(ode, "precondition: slot 21");
            SkillExecutor.execute(battle, ode, sprite, List.of(him));
            battle.processRequests();
        }
        return battle.characters.get(1).getResources().value(FIRE_SEED) - before;
    }
}
