package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
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
 * 1224 Hunt March 7th, from her own file (2026-09-29, round 203): the 师父 marker, the charge it enables, and the speed share.
 *
 * <p>The speed is asserted against a hand-built 20% reference in the SAME pipeline (ratio 0.5), because round 197 showed a share of a zero base compares nothing and a
 * bare "greater than before" lets any percentage pass.
 */
public class March7thHuntTest {
    private static final int MARCH = 1224;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /** \u26a0 The Skill marks the ally AND speeds them by 10%, measured against a hand-built 20% reference. */
    @Test
    public void theSkillMarksTheShifuAndSpeedsThem() {
        double content = skillSpeedGain(0);
        double reference = skillSpeedGain(1);

        Assertions.assertTrue(reference > 0, "the reference must raise speed at all");
        Assertions.assertEquals(0.5, content / reference, 0.05,
                "content " + content + " vs reference " + reference);
    }

    /** \u26a0 The charge is granted by HER basic attack and by the SHIFU's attack, and by nobody else. */
    @Test
    public void theChargeComesFromHerBasicAndFromTheShifu() {
        Character march = CharacterFactory.create(MARCH, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(march, ally), List.of(enemy), fixed());
        battle.startBattle();

        int start = chargeOf(march);
        // 1) an ordinary ally attack: nobody is the Shifu yet, so nothing is granted
        battle.fireTriggers(TriggerEvent.ALLY_ATTACK, ally, enemy, 0, 0);
        Assertions.assertEquals(start, chargeOf(march),
                "\u300c\u3010\u5e08\u7236\u3011\u65bd\u653e\u653b\u51fb\u300d -- before the Skill, no ally is the Shifu");

        // 2) her own basic attack: +1
        battle.fireTriggers(TriggerEvent.BASIC_ATTACK, march, enemy, 0, 0);
        Assertions.assertEquals(start + 1, chargeOf(march),
                "\u300c\u968f\u540e\u83b7\u5f971\u70b9\u5145\u80fd\u300d");

        // 3) mark the ally as the Shifu, then their attack: +1 again
        battle.castImmediate(march.getSkills().get(SkillType.SKILL), march, List.of(ally));
        Assertions.assertTrue(ally.getBuffManager().hasState("师父"),
                "\u300c\u4f7f\u9664\u81ea\u8eab\u4ee5\u5916\u7684\u6211\u65b9\u6307\u5b9a\u5355\u4f53\u6210\u4e3a\u3010\u5e08\u7236\u3011\u300d");
        int beforeShifu = chargeOf(march);
        battle.fireTriggers(TriggerEvent.ALLY_ATTACK, ally, enemy, 0, 0);
        Assertions.assertEquals(beforeShifu + 1, chargeOf(march),
                "\u300c\u3010\u5e08\u7236\u3011\u65bd\u653e\u653b\u51fb\u540e\uff0c\u4e09\u6708\u4e03\u6bcf\u6b21\u83b7\u5f97\u6700\u591a1\u70b9\u5145\u80fd\u300d");
    }

    /** The declared resource's value, read through the combatant's own manager. */
    private static int chargeOf(Character march) {
        return march.getResources().get("充能").getValue();
    }

    /** mode 0 = the shipped file, 1 = a hand-built 20% reference. Returns the ally's speed gain after the Skill. */
    private static double skillSpeedGain(int mode) {
        Character march = CharacterFactory.create(MARCH, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        if (mode == 1) {
            EffectSpec effect = new EffectSpec();
            TriggerSpecs.set(effect, "op", "MODIFY_ATTR");
            TriggerSpecs.set(effect, "attribute", "SPEED");
            TriggerSpecs.set(effect, "percent", 0.2);
            TriggerSpecs.set(effect, "permanent", true);
            TriggerSpecs.set(effect, "target", "target");
            march.setTriggerTable(new TriggerTable(MARCH, List.of(TriggerSpecs.rule(
                    TriggerEvent.SKILL_CAST.name(), List.of("actor == self"), effect))));
        }
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(march, ally), List.of(enemy), fixed());
        battle.startBattle();
        double before = ally.getAttribute(AttributeType.SPEED).get();
        battle.castImmediate(march.getSkills().get(SkillType.SKILL), march, List.of(ally));
        return ally.getAttribute(AttributeType.SPEED).get() - before;
    }

    private static Random fixed() {
        return new Random() {
            @Override
            public double nextDouble() {
                return 0.0;
            }
        };
    }
}
