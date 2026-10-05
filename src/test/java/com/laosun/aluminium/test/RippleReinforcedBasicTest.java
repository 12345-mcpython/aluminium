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

/** \u300c\u83b7\u5f97\u5f3a\u5316\u666e\u653b\u300d (2026-10-02). */
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
}
