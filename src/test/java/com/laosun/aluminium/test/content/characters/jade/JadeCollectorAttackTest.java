package com.laosun.aluminium.test.content.characters.jade;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 1314 翡翠's Skill, second clause: 「【收债人】施放攻击后，对每个击中的敌方目标造成 1 次等同于翡翠 25% 攻击力的量子属性附加伤害，
 * 并消耗【收债人】生命上限 2% 的生命值…若翡翠成为【收债人】时…攻击后不消耗生命值」.
 */
public class JadeCollectorAttackTest {

    private static final int LEVEL = 80;
    private static final int JADE = 1314;
    private static final int ALLY = 1001;
    private static final String COLLECTOR = "收债人";

    /**
     * The collector's attack carries an extra 25%-of-Jade hit, and the collector pays 2% of its own Max HP.
     */
    @Test
    public void theCollectorsAttackCarriesAHitAndCostsHp() {
        Fixture f = new Fixture();
        f.makeCollector(f.ally);
        double allyHpBefore = f.ally.getCurrentHp();
        double enemyBefore = f.enemy.getCurrentHp();

        f.attack(f.ally);

        double cost = allyHpBefore - f.ally.getCurrentHp();
        System.out.println("[1314] cost=" + cost + " expected=" + (0.02 * f.ally.getMaxHp()));
        Assertions.assertEquals(0.02 * f.ally.getMaxHp(), cost, 1e-6,
                "「消耗【收债人】生命上限 2% 的生命值」");

        double loss = enemyBefore - f.enemy.getCurrentHp();
        double plainLoss = new Fixture().attackLoss();
        Assertions.assertTrue(loss > plainLoss, "「每个击中的敌方目标…附加伤害」 - the attack is bigger");
    }

    /**
     * When JADE is the collector, she pays no HP.
     */
    @Test
    public void jadeHerselfPaysNothing() {
        Fixture f = new Fixture();
        f.makeCollector(f.jade);
        double before = f.jade.getCurrentHp();
        f.attack(f.jade);
        Assertions.assertEquals(before, f.jade.getCurrentHp(), 1e-9,
                "「若翡翠成为【收债人】时…攻击后不消耗生命值」");
    }

    // ==================================================================

    private static final class Fixture {
        private final Character jade;
        private final Character ally;
        private final Enemy enemy = BattleFixture.monster();
        private final Battle battle;

        private Fixture() {
            jade = CharacterFactory.create(JADE, LEVEL);
            ally = CharacterFactory.create(ALLY, LEVEL);
            battle = new Battle(new ArrayList<>(List.of(jade, ally)), new ArrayList<>(List.of(enemy)),
                    BattleFixture.alwaysRoll(0.99));
            battle.startBattle();
        }

        private void makeCollector(Character who) {
            battle.castImmediate(jade.getSkills().get(SkillType.SKILL), jade, List.of(who));
            Assertions.assertTrue(who.getBuffManager().hasState(COLLECTOR), "precondition: the collector state is up");
        }

        private void attack(Character who) {
            battle.castImmediate(who.getSkills().get(SkillType.COMMON), who, List.of(enemy));
        }

        private double attackLoss() {
            double before = enemy.getCurrentHp();
            attack(ally);
            return before - enemy.getCurrentHp();
        }
    }
}
