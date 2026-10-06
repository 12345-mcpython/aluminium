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

/** "激活全体队友的终结技". */
public class UltimateActivationTest {
    @Test
    public void herUltimateMakesTheOthersReady() {
        Character cyrene = CharacterFactory.create(1415, 80);
        Character ally = CharacterFactory.create(1405, 80);
        Battle battle = new Battle(List.of(cyrene, ally),
                List.of(EnemyFactory.create(1002011, 100, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        boolean beforeAlly = battle.isUltraReady(battle.characters.get(1));
        cyrene = battle.characters.get(0);
        com.laosun.aluminium.models.skill.SkillExecutor.execute(battle, cyrene.getSkills().get(SkillType.ULTRA),
                cyrene, List.of(battle.enemies.getFirst()));
        battle.processRequests();
        boolean afterAlly = battle.isUltraReady(battle.characters.get(1));
        System.out.println("[activate] the ally's ultimate readiness: " + beforeAlly + " -> " + afterAlly);
        Assertions.assertFalse(beforeAlly, "without energy the ally is not ready");
        Assertions.assertTrue(afterAlly, "her ultimate activates it");
    }
}
