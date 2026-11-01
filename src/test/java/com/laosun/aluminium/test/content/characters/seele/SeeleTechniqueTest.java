package com.laosun.aluminium.test.content.characters.seele;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 1102 希儿's technique: 「主动攻击敌人进入战斗时，希儿立即进入增幅状态」, whose numbers are the Talent's
 * (「造成的伤害提高 80%，持续 1 回合」).
 */
public class SeeleTechniqueTest {

    private static final int SEELE = 1102;
    private static final int LEVEL = 80;

    @Test
    public void enteringWithTheTechniqueAmplifiesHerFirstHit() {
        double bare = dealt(false);
        double amplified = dealt(true);
        System.out.println("[1102] " + bare + " -> " + amplified + " ratio=" + (amplified / bare));
        Assertions.assertEquals(1.8, amplified / bare, 1e-6, "「增幅状态下…伤害提高 80%」");
    }

    private static double dealt(boolean technique) {
        Character seele = CharacterFactory.create(SEELE, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(new ArrayList<>(List.of(seele, CharacterFactory.create(1001, LEVEL))),
                new ArrayList<>(List.of(enemy)), BattleFixture.alwaysRoll(0.0));
        if (technique) {
            battle.markTechniqueUsed(seele);
        }
        battle.startBattle();
        return battle.applyDamage(enemy, new Damage(seele, enemy, DamageElement.QUANTUM, DamageType.NORMAL, 100));
    }
}