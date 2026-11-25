package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.data.TriggerTables;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1414 Dan Heng - Permansor Terrae, from his own file: ATK-scaled shields and the Bondmate designation.
 *
 * <p>The shield case asserts the DOCUMENT'S arithmetic - 20.00% of his ATK plus 400 - which needs the new `owner_attack` scale; the mutation that drops the
 * flat addend makes it red.
 */
public class PermansorTerraeTest {
    private static final int DHPT = 1414;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;

    /**
     * Note: "抵消等同于丹恒-腾荒20.00%攻击力+400伤害的护盾，持续3回合" (a shield absorbing damage equal to 20.00% of Dan Heng - Permansor Terrae's ATK + 400, for 3 turns).
     */
    @Test
    public void hisSkillShieldsThePartyForAShareOfHisAttackPlusAFlatNumber() {
        Character dhpt = CharacterFactory.create(DHPT, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(dhpt, ally), List.of(enemy), fixed());
        battle.startBattle();
        double expected = dhpt.getAttribute(AttributeType.ATTACK).get() * 0.2 + 400;

        battle.castImmediate(dhpt.getSkills().get(SkillType.SKILL), dhpt, List.of(ally));

        Assertions.assertTrue(ally.getShield() > 0, "「为我方全体提供…char:1001/shield」 (a shield for all of our side) -- the designated ally");
        Assertions.assertEquals(expected, ally.getShield(), expected * 0.02,
                "「抵消等同于丹恒•腾荒20.00%攻击力+400伤害的护盾」 (a shield that absorbs damage equal to 20.00% of Dan Heng - Permansor Terrae's ATK + 400): expected " + expected + ", shield " + ally.getShield());
        Assertions.assertTrue(ally.getBuffManager().hasState("char:1414/comrade"), "\"makes a designated single ally the Bondmate (【char:1414/comrade】)\" (「使指定我方单体角色成为【char:1414/comrade】」)");
    }

    /**
     * Note: The ultimate states the same shield, so the party is covered even without the Skill.
     */
    @Test
    public void hisUltimateAlsoShieldsTheParty() {
        Character dhpt = CharacterFactory.create(DHPT, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(dhpt, ally), List.of(enemy), fixed());
        battle.startBattle();

        battle.castImmediate(dhpt.getSkills().get(SkillType.ULTRA), dhpt, List.of(enemy));

        Assertions.assertTrue(ally.getShield() > 0, "「为我方全体提供…char:1001/shield」 (a shield for all of our side)");
    }


    private static Random fixed() {
        return BattleFixture.alwaysRoll(1.0);
    }
}
