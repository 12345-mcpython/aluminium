package com.laosun.aluminium.test.content.characters.saber;

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
 * 1014 Saber's enhanced basic:「若施放时敌方目标数量为 2/1，额外对敌方全体造成 150%/220% 攻击力的风属性伤害」.
 *
 * <p>The two tiers are row reads off the ENHANCED skill's own row (Lv6 = [1.5, 2, 1.5, 2.2]), which is only possible while
 * the COMMON slot still points at it - the restore rule runs later in the file on purpose. The judge compares what ONE
 * enemy takes: 150% base + 150% extra beside a neighbour, 150% + 220% when alone.
 */
public class SaberEnhancedBasicTiersTest {

    private static final int SABER = 1014;
    private static final int LEVEL = 80;

    @Test
    public void theExtraHitDependsOnHowManyEnemiesThereAre() {
        double alone = enhancedDamage(1);
        double amongTwo = enhancedDamage(2);
        System.out.println("[1014] enhanced basic: alone " + alone + " ; one of two " + amongTwo);
        Assertions.assertTrue(alone > 0 && amongTwo > 0, "「额外对敌方全体造成…风属性伤害」 must land in both cases");
        // 370% alone against 300% beside a neighbour: the ratio is the tier difference, 2.2 vs 1.5 over the same base.
        Assertions.assertTrue(alone > amongTwo, "220% (alone) must beat 150% (with a neighbour): " + alone + " vs " + amongTwo);
        Assertions.assertEquals((1.5 + 2.2) / (1.5 + 1.5), alone / amongTwo, 0.02,
                "the tier is the only difference between the two situations");
    }

    /** The damage the FIRST enemy takes from one enhanced basic, with {@code enemies} enemies on the field. */
    private static double enhancedDamage(int enemies) {
        Character saber = CharacterFactory.create(SABER, LEVEL);
        List<Enemy> foes = new ArrayList<>();
        for (int i = 0; i < enemies; i++) {
            foes.add(BattleFixture.dummy());
        }
        Battle battle = new Battle(new ArrayList<>(List.of(saber)), foes, BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        battle.castImmediate(saber.getSkills().get(SkillType.ULTRA), saber, List.of(battle.enemyUnits().getFirst()));
        battle.processRequests();
        Enemy target = battle.enemyUnits().getFirst();
        double before = target.getCurrentHp();
        battle.castImmediate(saber.getSkills().get(SkillType.COMMON), saber, List.of(target));
        battle.processRequests();
        Assertions.assertFalse(target.isDeath(), "the target must survive, or a second hit settles nothing");
        return before - target.getCurrentHp();
    }
}