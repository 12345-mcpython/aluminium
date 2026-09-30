package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/** Cones 23037 (skill + ultimate damage after an ultimate) and 23034 (a stack for the skill's target). */
public class ConeScopedBoostTest {
    private static final int WEARER = 1205;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    private Character wearer(int cone) {
        return cone == 0 ? CharacterFactory.create(WEARER, LEVEL)
                : CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(cone, LEVEL, false, 1));
    }

    private Enemy enemy() {
        return EnemyFactory.create(MONSTER, 90, 1);
    }

    @Test
    public void theUltimateRaisesBothScopedBoosts() {
        Character unit = wearer(23037);
        Enemy enemy = enemy();
        Battle battle = new Battle(List.of(unit), List.of(enemy), new Random(0));
        battle.startBattle();
        double skillBefore = unit.getAttribute(AttributeType.SKILL_DAMAGE_BOOST).get();
        double ultBefore = unit.getAttribute(AttributeType.ULTIMATE_DAMAGE_BOOST).get();
        battle.fireTriggers(TriggerEvent.ULT_CAST, unit, enemy, 0, 0);
        double skillAfter = unit.getAttribute(AttributeType.SKILL_DAMAGE_BOOST).get();
        double ultAfter = unit.getAttribute(AttributeType.ULTIMATE_DAMAGE_BOOST).get();
        System.out.println("[23037] skill " + skillBefore + " -> " + skillAfter
                + " ; ultimate " + ultBefore + " -> " + ultAfter);
        Assertions.assertEquals(0.6, skillAfter - skillBefore, 1e-9, "60 points of skill damage");
        Assertions.assertEquals(0.6, ultAfter - ultBefore, 1e-9, "and 60 points of ultimate damage");
        int pinned = 0;
        for (var rule : unit.getTriggerTable().matching(TriggerEvent.ULT_CAST,
                new TriggerTable.TriggerContext(unit, unit, unit, 0, 0))) {
            for (var effect : rule.effects()) {
                // \u26a0 getAttribute() is the SERIALIZED name (a String), not the enum: compare the document spelling.
                // \u26a0 getAttribute() is the ENUM name (SKILL_DAMAGE_BOOST), not the serialized spelling
                // (skill_damage_boost) -- the same convention 21006's judge relies on.
                if ("SKILL_DAMAGE_BOOST".equals(effect.getAttribute())
                        || "ULTIMATE_DAMAGE_BOOST".equals(effect.getAttribute())) {
                    pinned++;
                    Assertions.assertEquals(3, effect.getTurns(), "for three turns");
                }
            }
        }
        Assertions.assertEquals(2, pinned, "one effect per damage scope");
    }

    @Test
    public void theStackGoesToAnAllyButNotToAnEnemy() {
        Character unit = wearer(23034);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = enemy();
        Battle battle = new Battle(List.of(unit, ally), List.of(enemy), new Random(0));
        battle.startBattle();
        double allyBefore = ally.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();
        double enemyBefore = enemy.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();
        for (int i = 0; i < 5; i++) {
            battle.fireTriggers(TriggerEvent.SKILL_CAST, unit, ally, 0, 0);
        }
        double allyAfter = ally.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();
        System.out.println("[23034] ally boost " + allyBefore + " -> " + allyAfter
                + " (delta=" + (allyAfter - allyBefore) + ")");
        Assertions.assertEquals(0.15 * 3, allyAfter - allyBefore, 1e-9, "five casts cap at three layers of 15 points");
        // The FALSE case: aiming the same cast at an ENEMY must grant nothing.
        battle.fireTriggers(TriggerEvent.SKILL_CAST, unit, enemy, 0, 0);
        System.out.println("[23034] enemy boost " + enemyBefore + " -> "
                + enemy.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get());
        Assertions.assertEquals(enemyBefore, enemy.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get(), 1e-9,
                "the clause is about OUR single targets");
    }
}
