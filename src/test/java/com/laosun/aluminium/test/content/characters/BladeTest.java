package com.laosun.aluminium.test.content.characters;


import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1205 Blade, from his own file: the [地狱变] state, the technique's Max-HP opening and the charge's cap.
 *
 * <p>The cap is tested by EXCEEDING it (round 192's lesson): six hits must still read 5.
 */
public class BladeTest {
    private static final int BLADE = 1205;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /** Note: The Skill enters the state. */
    @Test
    public void theSkillEntersHellscape() {
        Character blade = CharacterFactory.create(BLADE, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(blade), List.of(enemy), fixed());
        battle.startBattle();

        battle.castImmediate(blade.getSkills().get(com.laosun.aluminium.enums.SkillType.SKILL), blade, List.of(enemy));

        Assertions.assertTrue(blade.getBuffManager().hasState("地狱变"),
                "「进入【地狱变】状态」");
    }

    /** Note: The technique's 40% of his Max HP, measured against a hand-built 50% in the same pipeline, and the control. */
    @Test
    public void theTechniqueDealsFortyPercentOfHisMaxHp() {
        double content = openingLoss(true);
        double reference = referenceLoss();
        double undeclared = openingLoss(false);

        Assertions.assertEquals(0.0, undeclared, 1e-9, "「使用秘技后」 -- undeclared, so nothing");
        Assertions.assertTrue(reference > 0, "the reference must deal damage");
        Assertions.assertEquals(0.4 / 0.5, content / reference, 0.05,
                "content " + content + " vs reference " + reference + " (expected " + (0.4 / 0.5) + ")");
    }

    /** Note: The charge cap, tested by exceeding it. */
    @Test
    public void theChargeStopsAtFive() {
        Character blade = CharacterFactory.create(BLADE, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(blade), List.of(enemy), fixed());
        battle.startBattle();

        for (int i = 0; i < 6; i++) {
            battle.fireTriggers(TriggerEvent.TAKING_HIT, enemy, blade, 0, 0);
        }

        Assertions.assertEquals(5, blade.getBuffManager().stacksOf("充能"),
                "「最多叠加5层」 -- six hits must still read five");
    }

    /** Runs the opening with or without the technique marker. */
    private static double openingLoss(boolean declared) {
        Character blade = CharacterFactory.create(BLADE, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(blade), List.of(enemy), fixed());
        if (declared) {
            battle.markTechniqueUsed(blade);
        }
        double before = enemy.getCurrentHp();
        battle.startBattle();
        return before - enemy.getCurrentHp();
    }

    /** A hand-built 50%-Max-HP instance in the same pipeline, as the ratio's denominator. */
    private static double referenceLoss() {
        Character blade = CharacterFactory.create(BLADE, LEVEL);
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "DAMAGE");
        TriggerSpecs.set(effect, "scale", "owner_max_hp");
        TriggerSpecs.set(effect, "percent", 0.5);
        TriggerSpecs.set(effect, "element", "Wind");
        TriggerSpecs.set(effect, "target", "all_enemies");
        blade.setTriggerTable(new TriggerTable(BLADE, List.of(TriggerSpecs.rule(
                TriggerEvent.BATTLE_START.name(), List.of("self has_state 秘技"), effect))));
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(blade), List.of(enemy), fixed());
        battle.markTechniqueUsed(blade);
        double before = enemy.getCurrentHp();
        battle.startBattle();
        return before - enemy.getCurrentHp();
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
