package com.laosun.aluminium.test;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.buff.ControlBuff;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Random;

/** "[追忆]达到 24 点时可激活终结技并解除自身所有负面效果"与"最多溢出至 2点" (2026-10-02). */
public class CyreneRecollectionCapTest {
    private static final int LEVEL = 80;
    private static final int CYRENE = 1415;
    private static final int MONSTER = 1002011;
    private static final String MEMORY = "追忆";

    @Test
    public void thecapCanOverflowToTwentySeven() {
        Character her = CharacterFactory.create(CYRENE, LEVEL);
        Battle battle = new Battle(List.of(her), List.of(EnemyFactory.create(MONSTER, 100, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        her = battle.characters.getFirst();
        var res = her.getResources().get(MEMORY);
        System.out.println("[recollection] max=" + res.getMax() + " overflow=" + res.getMaxOverflow());
        Assertions.assertEquals(24, res.getMax(), "the stated cap");
        Assertions.assertEquals(3, res.getMaxOverflow(), "and it may overflow to 27");
    }

    @Test
    public void aFullRecollectionCleansesHer() {
        Character her = CharacterFactory.create(CYRENE, LEVEL);
        Battle battle = new Battle(List.of(her), List.of(EnemyFactory.create(MONSTER, 100, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        her = battle.characters.getFirst();
        var control = com.laosun.aluminium.Constant.CONTROL_EFFECTS.get("IMPRISONED");
        her.getBuffManager().addBuff(new ControlBuff(control, 3));
        Assertions.assertTrue(her.getBuffManager().debuffCount() > 0, "precondition: she carries a debuff");
        her.getResources().gain(MEMORY, 23);
        var basic = her.getSkills().get(SkillType.COMMON);
        com.laosun.aluminium.models.skill.SkillExecutor.execute(battle, basic, her,
                List.of(battle.enemies.getFirst()));
        battle.processRequests();
        int left = battle.characters.getFirst().getBuffManager().debuffCount();
        System.out.println("[recollection] after reaching 24, her debuffs read " + left);
        Assertions.assertEquals(0, left, "a full 【追忆】 cleanses her");
    }
}
