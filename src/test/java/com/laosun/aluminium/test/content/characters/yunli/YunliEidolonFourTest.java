package com.laosun.aluminium.test.content.characters.yunli;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 1221 云璃's Eidolon 4: 「发动【勘破•斩】或【勘破•灭】后使自身效果抵抗提高 50%，持续 1 回合」.
 */
public class YunliEidolonFourTest {

    private static final int YUNLI = 1221;
    private static final int LEVEL = 80;

    @Test
    public void herCounterRaisesHerEffectResistance() {
        Assertions.assertEquals(0.5, gain(4), 1e-9, "「效果抵抗提高 50%」");
        Assertions.assertEquals(0.0, gain(0), 1e-9, "control: nothing without the Eidolon");
    }

    private static double gain(int eidolon) {
        Character yunli = CharacterFactory.create(YUNLI, LEVEL, true, null, null, eidolon);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(new ArrayList<>(List.of(yunli, CharacterFactory.create(1001, LEVEL))),
                new ArrayList<>(List.of(enemy)), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        double before = yunli.getAttribute(AttributeType.EFFECT_RESISTANCE).get();
        battle.applyDamage(yunli, new Damage(enemy, yunli, DamageElement.PHYSICAL, DamageType.NORMAL, 100));
        battle.processRequests();
        double after = yunli.getAttribute(AttributeType.EFFECT_RESISTANCE).get();
        System.out.println("[1221] effect res " + before + " -> " + after);
        return after - before;
    }
}