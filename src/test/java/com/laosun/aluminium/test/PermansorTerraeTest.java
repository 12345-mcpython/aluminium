package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.data.TriggerTables;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1414 Dan Heng \u2022 Permansor Terrae, from his own file (2026-09-29, round 176): ATK-scaled shields and the Bondmate designation.
 *
 * <p>The shield case asserts the DOCUMENT'S arithmetic — 20.00% of his ATK plus 400 — which needs the new `owner_attack` scale; the mutation that drops the
 * flat addend makes it red.
 */
public class PermansorTerraeTest {
    private static final int DHPT = 1414;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /** \u26a0 「抵消等同于丹恒\u2022腾荒20.00%攻击力+400伤害的护盾，持续3回合」. */
    @Test
    public void hisSkillShieldsThePartyForAShareOfHisAttackPlusAFlatNumber() {
        Character dhpt = CharacterFactory.create(DHPT, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(dhpt, ally), List.of(enemy), fixed());
        battle.startBattle();
        double expected = dhpt.getAttribute(AttributeType.ATTACK).get() * 0.2 + 400;

        battle.castImmediate(dhpt.getSkills().get(SkillType.SKILL), dhpt, List.of(ally));

        Assertions.assertTrue(ally.getShield() > 0, "\u300c\u4e3a\u6211\u65b9\u5168\u4f53\u63d0\u4f9b\u2026\u62a4\u76fe\u300d -- the designated ally");
        Assertions.assertEquals(expected, ally.getShield(), expected * 0.02,
                "\u300c\u62b5\u6d88\u7b49\u540c\u4e8e\u4e39\u6052\u2022\u817e\u835220.00%\u653b\u51fb\u529b+400\u4f24\u5bb3\u7684\u62a4\u76fe\u300d: expected " + expected + ", shield " + ally.getShield());
        Assertions.assertTrue(ally.getBuffManager().hasState("\u540c\u888d"), "\u300c\u4f7f\u6307\u5b9a\u6211\u65b9\u5355\u4f53\u89d2\u8272\u6210\u4e3a\u3010\u540c\u888d\u3011\u300d");
    }

    /** \u26a0 The ultimate states the same shield, so the party is covered even without the Skill. */
    @Test
    public void hisUltimateAlsoShieldsTheParty() {
        Character dhpt = CharacterFactory.create(DHPT, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(dhpt, ally), List.of(enemy), fixed());
        battle.startBattle();

        battle.castImmediate(dhpt.getSkills().get(SkillType.ULTRA), dhpt, List.of(enemy));

        Assertions.assertTrue(ally.getShield() > 0, "\u300c\u4e3a\u6211\u65b9\u5168\u4f53\u63d0\u4f9b\u2026\u62a4\u76fe\u300d");
    }

    /** Census: the skill, the ultimate and the level convention. */
    @Test
    public void hisFileCarriesTheClauses() {
        var table = TriggerTables.of(DHPT);
        Assertions.assertEquals(1, table.ruleCount(TriggerEvent.SKILL_CAST), "the Bondmate and the shield");
        Assertions.assertEquals(1, table.ruleCount(TriggerEvent.ULT_CAST), "the party shield");
        Assertions.assertEquals(1, table.ruleCount(TriggerEvent.BATTLE_START), "the level convention");
    }

    private static Random fixed() {
        return new Random() {
            @Override
            public double nextDouble() {
                return 1.0;
            }
        };
    }
}
