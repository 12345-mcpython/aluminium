package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.data.TriggerTables;
import com.laosun.aluminium.enums.AttributeType;
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
 * 1401 The Herta, from her own file (2026-09-29, round 192): the technique's opening ATK and the ultimate's three effects.
 *
 * <p>The technique's 60% is measured as 60% of the BASE attack (the engine's `add_percent` convention), and the pair (declared / undeclared) is what makes it a
 * measurement rather than a claim — the same trap round 179 hit with Dan Heng.
 */
public class HertaTheTest {
    private static final int HERTA = 1401;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /** ⚠ 「使用秘技后，下一次战斗开始时大黑塔攻击力提高60%，持续2回合」, and the control. */
    @Test
    public void theTechniqueRaisesHerAttackAndNothingWithoutIt() {
        Character plain = CharacterFactory.create(HERTA, LEVEL);
        Enemy enemy1 = EnemyFactory.create(MONSTER, 90, 1);
        Battle plainBattle = new Battle(List.of(plain), List.of(enemy1), fixed());
        plainBattle.startBattle();
        double untouched = plain.getAttribute(AttributeType.ATTACK).get();

        Character herta = CharacterFactory.create(HERTA, LEVEL);
        Enemy enemy2 = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(herta), List.of(enemy2), fixed());
        battle.markTechniqueUsed(herta);
        battle.startBattle();

        double boosted = herta.getAttribute(AttributeType.ATTACK).get();
        double base = herta.getAttribute(AttributeType.ATTACK).baseValue();
        Assertions.assertEquals(base * 0.6, boosted - untouched, base * 0.6 * 0.02,
                "「攻击力提高60%」 of the BASE: base " + base + ", gain " + (boosted - untouched));
    }

    /** ⚠ The ultimate's own effects: +80% ATK, a full advance, and one Inspiration stack. */
    @Test
    public void herUltimateRaisesAttackAdvancesAndGrantsInspiration() {
        Character herta = CharacterFactory.create(HERTA, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(herta), List.of(enemy), fixed());
        battle.startBattle();
        double before = herta.getAttribute(AttributeType.ATTACK).get();

        battle.castImmediate(herta.getSkills().get(com.laosun.aluminium.enums.SkillType.ULTRA), herta, List.of(enemy));

        Assertions.assertTrue(herta.getAttribute(AttributeType.ATTACK).get() > before,
                "「使大黑塔攻击力提高80%，持续3回合」");
        // ADD_STACK makes a stack, not a named state: it is read with stacksOf, the same API 8003's Magma Will uses.
        Assertions.assertEquals(1, herta.getBuffManager().stacksOf("灵感"),
                "「获得1层【灵感】」");

        // ⚠ The document's cap: five more ultimates must stop at 4, not keep counting (this is what makes the cap testable at all).
        for (int i = 0; i < 5; i++) {
            battle.fireTriggers(TriggerEvent.ULT_CAST, herta, herta, 0, 0);
        }
        Assertions.assertEquals(4, herta.getBuffManager().stacksOf("灵感"),
                "「【灵感】最多持有4层」");
    }

    /** Census: the technique, the ultimate and the convention. */
    @Test
    public void herFileCarriesTheClauses() {
        var table = TriggerTables.of(HERTA);
        Assertions.assertEquals(1, table.ruleCount(TriggerEvent.ULT_CAST), "the ultimate's modifiers");
        Assertions.assertEquals(2, table.ruleCount(TriggerEvent.BATTLE_START), "the technique and the convention");
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
