package com.laosun.aluminium.test.engine;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Random;

/** The extra Ice hits of slot 26's second clause (2026-10-02). */
public class TrueSelfOdeExtraIceTest {
    private static final String COUNTER = "忆灵技的额外一击";

    @Test
    public void eachCounterPointAddsAHit() {
        double none = damageWith(0);
        double many = damageWith(40);
        System.out.println("[extra_ice] the dance costs the enemy " + none + " at zero points and " + many + " at forty");
        Assertions.assertTrue(many > none, "forty extra hits at 0.3% of its Max HP must outweigh the crit-roll shift");
    }

    private static double damageWith(int points) {
        Character cyrene = CharacterFactory.create(1415, 80);
        Battle battle = new Battle(List.of(cyrene), List.of(EnemyFactory.create(1002011, 100, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        // `summonMemosprite` builds through `memospriteWith`, which does NOT install the spec's `skills` (measured: the map is empty);
        // `summonServant` does, and both make the same unit (measured in an earlier round).
        var dragon = battle.summonServant(battle.characters.get(0));
        battle.processRequests();
        if (points > 0) {
            dragon.getResources().gain(COUNTER, points);
        }
        System.out.println("[extra_ice] the memosprite's data slots are " + dragon.skillsByDataSlot().keySet()
                + " ; SkillType.COMMON present = " + dragon.getSkills().containsKey(SkillType.COMMON));
        var dance = dragon.skillAt(1);
        Assertions.assertNotNull(dance, "precondition: the dance is data slot 1");
        double before = battle.enemies.getFirst().getCurrentHp();
        com.laosun.aluminium.models.skill.SkillExecutor.execute(battle, dance, dragon,
                List.of(battle.enemies.getFirst()));
        battle.processRequests();
        return before - battle.enemies.getFirst().getCurrentHp();
    }
}
