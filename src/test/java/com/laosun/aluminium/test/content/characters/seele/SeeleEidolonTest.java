package com.laosun.aluminium.test.content.characters.seele;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.trigger.TriggerPayload;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 1102 希儿's Eidolons: 3 and 5 raise skill levels, and 4 restores 15 energy on a kill.
 */
public class SeeleEidolonTest {

    private static final int SEELE = 1102;
    private static final int LEVEL = 80;

    @Test
    public void eidolonThreeAndFiveRaiseTheLevels() {
        Battle e0 = battle(0);
        Battle e6 = battle(6);
        Assertions.assertEquals(level(e0, SkillType.SKILL) + 2, level(e6, SkillType.SKILL), "E3: 战技 +2");
        Assertions.assertEquals(level(e0, SkillType.TALENT) + 2, level(e6, SkillType.TALENT), "E3: 天赋 +2");
        Assertions.assertEquals(level(e0, SkillType.ULTRA) + 2, level(e6, SkillType.ULTRA), "E5: 终结技 +2");
        Assertions.assertEquals(level(e0, SkillType.COMMON) + 1, level(e6, SkillType.COMMON), "E5: 普攻 +1");
    }

    @Test
    public void eidolonFourRestoresEnergyOnAKill() {
        Battle battle = battle(4);
        Character seele = battle.characters().getFirst();
        double before = seele.getCurrentEnergy();
        battle.fire(TriggerPayload.Simple.of(TriggerEvent.KILL, seele,
                battle.enemies().getFirst(), 0, 0));
        System.out.println("[1102] energy " + before + " -> " + seele.getCurrentEnergy());
        Assertions.assertEquals(before + 15, seele.getCurrentEnergy(), 1e-9, "「消灭敌方目标时，自身恢复 15 点能量」");
    }

    private static Battle battle(int eidolon) {
        Character seele = CharacterFactory.create(SEELE, LEVEL, true, null, null, eidolon);
        Battle battle = new Battle(new ArrayList<>(List.of(seele, CharacterFactory.create(1001, LEVEL))),
                new ArrayList<>(List.of(BattleFixture.monster())), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        return battle;
    }

    private static int level(Battle battle, SkillType slot) {
        return battle.characters().getFirst().skillLevelBonus(slot);
    }
}

