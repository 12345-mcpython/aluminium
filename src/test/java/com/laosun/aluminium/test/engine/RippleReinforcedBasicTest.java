package com.laosun.aluminium.test.engine;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Signal;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Random;

/** "获得强化普攻". */
public class RippleReinforcedBasicTest {
    @Test
    public void herUltimateReplacesHerBasic() {
        Character cyrene = CharacterFactory.create(1415, 80);
        Battle battle = new Battle(List.of(cyrene), List.of(EnemyFactory.create(1002011, 100, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        cyrene = battle.characters.getFirst();
        int before = cyrene.getSkills().get(SkillType.COMMON).getSkillSlot();
        com.laosun.aluminium.models.skill.SkillExecutor.execute(battle, cyrene.getSkills().get(SkillType.ULTRA),
                cyrene, List.of(battle.enemies.getFirst()));
        battle.processRequests();
        int after = battle.characters.getFirst().getSkills().get(SkillType.COMMON).getSkillSlot();
        System.out.println("[reinforced] her basic slot went " + before + " -> " + after);
        Assertions.assertEquals(1, before, "the ordinary basic is data slot 1");
        Assertions.assertEquals(8, after, "and the ripple replaces it with 141508, data slot 8");
    }

    @Test
    public void theReinforcedBasicRestoresNoSkillPoint() {
        int ordinary = skillPoints(false);
        int reinforced = skillPoints(true);
        System.out.println("[reinforced_sp] after her Skill and then an ordinary basic = " + ordinary
                + " ; after her Skill, her ultimate and the reinforced basic = " + reinforced);
        Assertions.assertEquals(3, ordinary, "her Skill spent one (3 -> 2) and the ordinary basic restored one");
        Assertions.assertEquals(2, reinforced, "the ultimate spends nothing, so the reinforced basic restored none");
    }

    /** Spends a point on her Skill, then either casts her basic or the reinforced one the ripple installs. */
    private static int skillPoints(boolean insideTheRipple) {
        Character her = CharacterFactory.create(1415, 80);
        Battle battle = new Battle(List.of(her), List.of(EnemyFactory.create(1002011, 100, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        her = battle.characters.getFirst();
        battle.currentMove = new Signal(her);
        Assertions.assertTrue(battle.useSkill(her.getSkills().get(SkillType.SKILL), List.of(battle.enemies.getFirst())),
                "precondition: her Skill is affordable");
        battle.processRequests();
        her = battle.characters.getFirst();
        if (insideTheRipple) {
            battle.currentMove = new Signal(her);
            Assertions.assertTrue(battle.useSkill(her.getSkills().get(SkillType.ULTRA), List.of(battle.enemies.getFirst())),
                    "precondition: her ultimate is affordable");
            battle.processRequests();
            her = battle.characters.getFirst();
        }
        battle.currentMove = new Signal(her);
        battle.useSkill(her.getSkills().get(SkillType.COMMON), List.of(battle.enemies.getFirst()));
        battle.processRequests();
        return battle.getSkillPoints();
    }
}
