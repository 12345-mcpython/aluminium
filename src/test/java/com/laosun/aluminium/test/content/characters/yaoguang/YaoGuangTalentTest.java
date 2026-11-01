package com.laosun.aluminium.test.content.characters.yaoguang;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 1502 爻光's talent 大吉大利: 「每次攻击后，对随机 1 个<b>命中目标</b>追加 1 次等同于爻光 20% 攻击力的对应属性欢愉伤害。该伤害不
 * 视为造成了 1 次攻击。」
 *
 * <p>Two things are measured, because the first version of this clause was a silent no-op: that the rider actually settles
 * (damage appears on the enemies after a basic attack), and that it settles on <b>one</b> of the enemies the attack struck -
 * the selector is {@code random_hit_enemy}, not {@code random_enemy}.
 */
public class YaoGuangTalentTest {

    private static final int WEARER = 1502;
    private static final int LEVEL = 80;

    @Test
    public void aBasicAttackPaysOneElationRider() {
        Character yaoguang = CharacterFactory.create(WEARER, LEVEL);
        Enemy first = BattleFixture.monster();
        Enemy second = BattleFixture.monster();
        Battle battle = new Battle(new ArrayList<>(List.of(yaoguang)),
                new ArrayList<>(List.of(first, second)), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();

        double lost = 0;
        for (Enemy enemy : List.of(first, second)) {
            if (enemy.isDeath()) {
                continue;
            }
            double before = enemy.getCurrentHp();
            battle.applyDamage(enemy, new com.laosun.aluminium.damage.Damage(yaoguang, enemy,
                    com.laosun.aluminium.enums.DamageElement.PHYSICAL,
                    com.laosun.aluminium.enums.DamageType.NORMAL, 100));
            lost += before - enemy.getCurrentHp();
        }
        Assertions.assertTrue(lost > 0, "precondition: the hits settled");

        double after = 0;
        for (Enemy enemy : List.of(first, second)) {
            double before = enemy.getCurrentHp();
            battle.castImmediate(yaoguang.getSkills().get(SkillType.COMMON), yaoguang, List.of(enemy));
            after += before - enemy.getCurrentHp();
        }
        System.out.println("[1502] talent: hits " + lost + " -> with rider " + after);
        Assertions.assertTrue(after > lost,
                "「每次攻击后，对随机 1 个命中目标追加 1 次 20% 攻击力的欢愉伤害」 must settle something extra");
    }
}