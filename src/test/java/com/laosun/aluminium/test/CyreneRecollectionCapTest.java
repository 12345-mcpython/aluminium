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

/** \u300c\u3010\u8ffd\u5fc6\u3011\u8fbe\u5230 24 \u70b9\u65f6\u53ef\u6fc0\u6d3b\u7ec8\u7ed3\u6280\u5e76\u89e3\u9664\u81ea\u8eab\u6240\u6709\u8d1f\u9762\u6548\u679c\u300d\u4e0e\u300c\u6700\u591a\u6ea2\u51fa\u81f3 27 \u70b9\u300d (2026-10-02). */
public class CyreneRecollectionCapTest {
    private static final int LEVEL = 80;
    private static final int CYRENE = 1415;
    private static final int MONSTER = 1002011;
    private static final String MEMORY = "\u8ffd\u5fc6";

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
        Assertions.assertEquals(0, left, "a full 【\u8ffd\u5fc6\u3011 cleanses her");
    }
}
