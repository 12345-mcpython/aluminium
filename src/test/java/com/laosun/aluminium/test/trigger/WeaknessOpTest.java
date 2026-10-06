package com.laosun.aluminium.test.trigger;


import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerInterpreter;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * ADD_ELEMENTAL_WEAKNESS:
 * "为指定敌方单体添加物理弱点".
 *
 * <p>isWeakTo is both the engine judgement point and a ready-made observable, so no two-enemy or hand-read
 * trickery is needed: false before, true after. The rule is built by hand because the reader has not shipped.
 */
public class WeaknessOpTest {
    private static final int CASTER = 1315;
    private static final int LEVEL = 80;

    private static boolean weakAfter(DamageElement element) {
        Character caster = CharacterFactory.create(CASTER, LEVEL);
        Enemy enemy = EnemyFactory.create(1002011, 90, 1);
        Battle battle = new Battle(List.of(caster), List.of(enemy), new Random(0));
        battle.startBattle();
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "ADD_ELEMENTAL_WEAKNESS");
        TriggerSpecs.set(effect, "element", element.name());
        TriggerSpecs.set(effect, "target", "target");
        caster.setTriggerTable(new TriggerTable(CASTER, List.of(TriggerSpecs.rule(
                TriggerEvent.BATTLE_START.name(), List.of(), effect))));
        TriggerTable.TriggerContext ctx = new TriggerTable.TriggerContext(caster, caster, enemy, 1, 0, null,
                battle, com.laosun.aluminium.enums.SkillCategory.UNSPECIFIED);
        TriggerInterpreter.apply(battle, caster.getTriggerTable().rulesFor(TriggerEvent.BATTLE_START).getFirst(), ctx);
        return enemy.isWeakTo(element);
    }

    @Test
    public void theOpAddsTheNamedElement() {
        Assertions.assertFalse(WeaknessOpTest.probeIsWeakToBefore(DamageElement.PHYSICAL),
                "the fixture enemy must not already be weak to it");
        Assertions.assertTrue(weakAfter(DamageElement.PHYSICAL),
                "the op must add the weakness, and isWeakTo must then see it");
        System.out.println("[weakness] ok: PHYSICAL before=false after=" + weakAfter(DamageElement.PHYSICAL));
    }

    private static boolean probeIsWeakToBefore(DamageElement element) {
        Enemy enemy = EnemyFactory.create(1002011, 90, 1);
        return enemy.isWeakTo(element);
    }
}
