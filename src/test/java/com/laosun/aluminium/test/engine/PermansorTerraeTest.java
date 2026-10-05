package com.laosun.aluminium.test.engine;

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
 * 1414 Dan Heng - Permansor Terrae, from his own file: ATK-scaled shields and the Bondmate designation.
 *
 * <p>The shield case asserts the DOCUMENT'S arithmetic - 20.00% of his ATK plus 400 - which needs the new `owner_attack` scale; the mutation that drops the
 * flat addend makes it red.
 */
public class PermansorTerraeTest {
    private static final int DHPT = 1414;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /** Note: "抵消等同于丹恒-腾荒20.00%攻击力+400伤害的护盾，持续3回合" (a shield absorbing damage equal to 20.00% of Dan Heng - Permansor Terrae's ATK + 400, for 3 turns). */
    @Test
    public void hisSkillShieldsThePartyForAShareOfHisAttackPlusAFlatNumber() {
        Character dhpt = CharacterFactory.create(DHPT, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(dhpt, ally), List.of(enemy), fixed());
        battle.startBattle();
        double expected = dhpt.getAttribute(AttributeType.ATTACK).get() * 0.2 + 400;

        battle.castImmediate(dhpt.getSkills().get(SkillType.SKILL), dhpt, List.of(ally));

        Assertions.assertTrue(ally.getShield() > 0, "「为我方全体提供…护盾」 (a shield for all of our side) -- the designated ally");
        Assertions.assertEquals(expected, ally.getShield(), expected * 0.02,
                "「抵消等同于丹恒•腾荒20.00%攻击力+400伤害的护盾」: expected " + expected + ", shield " + ally.getShield());
        Assertions.assertTrue(ally.getBuffManager().hasState("同袍"), "「使指定我方单体角色成为【同袍】」");
    }

    /** Note: The ultimate states the same shield, so the party is covered even without the Skill. */
    @Test
    public void hisUltimateAlsoShieldsTheParty() {
        Character dhpt = CharacterFactory.create(DHPT, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(dhpt, ally), List.of(enemy), fixed());
        battle.startBattle();

        battle.castImmediate(dhpt.getSkills().get(SkillType.ULTRA), dhpt, List.of(enemy));

        Assertions.assertTrue(ally.getShield() > 0, "「为我方全体提供…护盾」 (a shield for all of our side)");
    }

    /** Census: his skill and its trace, the ultimate, the technique, and the two halves of the Sylvanity (葳蕤) trace. */
    @Test
    public void hisFileCarriesTheClauses() {
        var table = TriggerTables.of(DHPT);
        Assertions.assertEquals(2, table.ruleCount(TriggerEvent.SKILL_CAST),
                "the Bondmate/shield rule AND (since 2026-10-02) the 神秀 (Divine Excellence) trace that buffs whoever holds 【同袍】 (the Bondmate) -- "
                        + "which is why the two are in this order in the file: the first designates, the second reads "
                        + "`holder_of:同袍`");
        Assertions.assertEquals(1, table.ruleCount(TriggerEvent.ULT_CAST), "the party shield");
        Assertions.assertEquals(4, table.ruleCount(TriggerEvent.BATTLE_START),
                "the level convention, the technique's 【同袍】 (「使用秘技后获得【同袍】」, gaining the Bondmate after using the technique), 葳蕤's 「行动提前40%」 and "
                        + "-- since 2026-10-02 -- the technique's auto-cast (「下一次战斗开始时自动对持有【同袍】的"
                        + "角色施放1次战技」), which CAST_SKILL can now deliver because it casts the skill through the "
                        + "engine's own path: his skill is a DEFENCE shield, not a swing");
        Assertions.assertEquals(1, table.ruleCount(TriggerEvent.ALLY_ATTACK),
                "葳蕤's second half: 「【同袍】施放攻击时，丹恒•腾荒恢复6点能量」 (when the Bondmate attacks, Dan Heng - Permansor Terrae restores 6 energy)");
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
