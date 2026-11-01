package com.laosun.aluminium.test.content.characters.clara;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 1107 克拉拉's Ultimate, first clause: 「施放终结技后克拉拉受到的伤害额外降低 25%…持续 2 回合」.
 */
public class ClaraUltReductionTest {

    private static final int CLARA = 1107;
    private static final int LEVEL = 80;

    @Test
    public void theUltimateCutsTheDamageSheTakes() {
        double before = taken(false);
        double after = taken(true);
        System.out.println("[1107] damage taken " + before + " -> " + after);
        Assertions.assertEquals(0.75, after / before, 1e-6, "「受到的伤害额外降低 25%」");
    }

    private static double taken(boolean afterUlt) {
        Character clara = CharacterFactory.create(CLARA, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(new ArrayList<>(List.of(clara, CharacterFactory.create(1001, LEVEL))),
                new ArrayList<>(List.of(enemy)), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        if (afterUlt) {
            battle.castImmediate(clara.getSkills().get(SkillType.ULTRA), clara, List.of(enemy));
        }
        return battle.applyDamage(clara, new Damage(enemy, clara, DamageElement.PHYSICAL, DamageType.NORMAL, 1000));
    }
}