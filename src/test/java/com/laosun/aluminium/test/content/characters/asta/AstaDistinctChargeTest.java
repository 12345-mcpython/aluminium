package com.laosun.aluminium.test.content.characters.asta;

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
 * 1009 艾丝妲's talent: 「每击中 1 名**不同**的敌方目标获得 1 层蓄能」 - counted per TARGET, not per hit.
 */
public class AstaDistinctChargeTest {

    private static final int ASTA = 1009;
    private static final int LEVEL = 80;
    private static final String CHARGE = "蓄能";

    @Test
    public void threeTargetsPayThreeAndOneTargetPaysOne() {
        Assertions.assertEquals(3, chargeFor(3), "three distinct targets: three layers");
        Assertions.assertEquals(1, chargeFor(1), "one target hit three times is still ONE target");
    }

    /**
     * @param targets how many enemies the attack connected with
     */
    private static int chargeFor(int targets) {
        Character asta = CharacterFactory.create(ASTA, LEVEL);
        List<Enemy> enemies = new ArrayList<>();
        for (int i = 0; i < targets; i++) {
            enemies.add(BattleFixture.enemy(803201006, LEVEL));   // no Fire weakness, so only the base layers count
        }
        Battle battle = new Battle(new ArrayList<>(List.of(asta, CharacterFactory.create(1001, LEVEL))),
                enemies, BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        int before = asta.getBuffManager().stacksOf(CHARGE);
        battle.fire(TriggerPayload.Cast.of(TriggerEvent.ALLY_ATTACK, asta, enemies.getFirst(),
                targets, 0, com.laosun.aluminium.enums.SkillCategory.NORMAL, 1, 0, new ArrayList<com.laosun.aluminium.entity.CanHit>(enemies)));
        return asta.getBuffManager().stacksOf(CHARGE) - before;
    }
}

