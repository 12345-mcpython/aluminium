package com.laosun.aluminium.test;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Random;

/**
 * "持续至下一个那刻夏回合开始时" (2026-10-02).
 *
 * Slot 18's [真知] was written `until: next_attack`, which ends the moment he next lands an attack -- but the cast that GRANTS it IS an attack, so the state died on the very cast
 * that created it. The sentence ends it when his next TURN begins, so this reads the state after that attack.
 */
public class TrueKnowledgeLifetimeTest {
    private static final int LEVEL = 80;
    private static final int CYRENE = 1415;
    private static final int ANAXA = 1405;
    private static final int MONSTER = 1002011;
    private static final int ODE_SLOT = 18;
    private static final String KNOWLEDGE = "真知";

    @Test
    public void theKnowledgeOutlivesTheCastThatGrantedIt() {
        Character cyrene = CharacterFactory.create(CYRENE, LEVEL);
        Character him = CharacterFactory.create(ANAXA, LEVEL);
        Battle battle = new Battle(List.of(cyrene, him),
                List.of(EnemyFactory.create(MONSTER, 100, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        var sprite = battle.summonServant(battle.characters.get(0));
        battle.processRequests();
        var ode = sprite.skillAt(ODE_SLOT);
        Assertions.assertNotNull(ode, "precondition: slot 18");
        Character aimed = battle.characters.get(1);
        com.laosun.aluminium.models.skill.SkillExecutor.execute(battle, ode, sprite, List.of(aimed));
        battle.processRequests();
        aimed = battle.characters.get(1);
        var basic = aimed.getSkills().get(SkillType.COMMON);
        com.laosun.aluminium.models.skill.SkillExecutor.execute(battle, basic, aimed,
                List.of(battle.enemies.getFirst()));
        battle.processRequests();
        boolean still = battle.characters.get(1).getBuffManager().hasState(KNOWLEDGE);
        System.out.println("[knowledge_lifetime] after the cast that grants it, 【真知】 is "
                + (still ? "still on him" : "gone"));
        Assertions.assertTrue(still, "the cast that grants 【真知】 must not also consume it");
    }
}
