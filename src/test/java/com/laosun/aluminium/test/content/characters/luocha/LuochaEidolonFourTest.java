package com.laosun.aluminium.test.content.characters.luocha;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.buff.StateBuff;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.entity.CanHit;
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
 * 1203 罗刹's Eidolon 4: 「结界生效时使敌方目标陷入虚弱状态，造成的伤害降低 12%」 - the damage the ENEMY deals falls.
 */
public class LuochaEidolonFourTest {

    private static final int LUOCHA = 1203;
    private static final int ALLY = 1001;
    private static final int LEVEL = 80;
    private static final String ZONE = "结界";

    @Test
    public void theEnemyDealsLessWhileTheZoneIsUp() {
        double bare = dealtByEnemy(false);
        double weakened = dealtByEnemy(true);
        System.out.println("[1203] enemy damage " + bare + " -> " + weakened + " ratio=" + (weakened / bare));
        Assertions.assertEquals(0.88, weakened / bare, 1e-6, "「造成的伤害降低 12%」");
    }

    private static double dealtByEnemy(boolean withZone) {
        Character luocha = CharacterFactory.create(LUOCHA, LEVEL, true, null, null, 4);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(new ArrayList<>(List.of(luocha, ally)),
                new ArrayList<>(List.of(enemy)), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        if (withZone) {
            luocha.getBuffManager().addBuff(new StateBuff(ZONE, 2, false));
            battle.castImmediate(luocha.getSkills().get(SkillType.SKILL), luocha, List.of(ally));
            Assertions.assertTrue(enemy.getBuffManager().activeBuffs().size() > 0, "precondition: the debuff landed");
        }
        CanHit target = ally;
        return battle.applyDamage(target, new Damage(enemy, target, DamageElement.PHYSICAL, DamageType.NORMAL, 100));
    }
}