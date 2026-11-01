package com.laosun.aluminium.test.content.characters.sampo;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.buff.DotBuff;
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
 * 1108 桑博's three 行迹: 圈套 (longer windshear), 后手 (+10 energy on the Ultimate) and 加料 (windsheared attackers hit him
 * for 15% less).
 */
public class SampoTraceTest {

    private static final int LEVEL = 80;
    private static final int SAMPO = 1108;

    /**
     * 圈套: the talent's windshear lasts one turn longer (3 -> 4).
     */
    @Test
    public void theTalentWindshearLastsOneTurnLonger() {
        Assertions.assertEquals(3 + 1, windshearTurns(), "「天赋使敌方陷入风化状态的持续时间延长 1 回合」");
    }

    /**
     * 后手: casting the Ultimate pays 10 energy on top of whatever the cast itself restores.
     */
    @Test
    public void theUltimatePaysTenEnergy() {
        Fixture f = new Fixture();
        double before = f.sampo.getCurrentEnergy();
        f.battle.castImmediate(f.sampo.getSkills().get(SkillType.ULTRA), f.sampo, List.of(f.enemy));
        double gained = f.sampo.getCurrentEnergy() - before;
        System.out.println("[1108] ultimate energy delta=" + gained);
        Assertions.assertEquals(10.0, gained, 1e-9, "「施放终结技时，额外恢复 10 点能量」");
    }

    /**
     * 加料: a windsheared enemy hits him for 15% less.
     */
    @Test
    public void windshearedAttackersHitHimForLess() {
        double fromBurning = lossFromAttacker(true);
        double fromClean = lossFromAttacker(false);
        System.out.println("[1108] windsheared=" + fromBurning + " clean=" + fromClean);
        Assertions.assertEquals(0.85, fromBurning / fromClean, 1e-6, "「造成的伤害降低 15%」");
    }

    // ==================================================================

    private static int windshearTurns() {
        Fixture f = new Fixture();
        f.battle.castImmediate(f.sampo.getSkills().get(SkillType.SKILL), f.sampo, List.of(f.enemy));
        return f.enemy.getBuffManager().activeBuffs().stream()
                .filter(DotBuff.class::isInstance)
                .map(DotBuff.class::cast)
                .mapToInt(DotBuff::remainingTurns)
                .max().orElseThrow();
    }

    /**
     * The trace's energy is measured against a fixture whose trace rule is not firing otherwise: same character without
     * the Ultimate's own energy is impossible to separate, so the delta is taken between two battles that differ ONLY in
     * the Ultimate being cast.
     */
    private static double traceDelta() {
        Fixture cast = new Fixture();
        Fixture idle = new Fixture();
        double beforeCast = cast.sampo.getCurrentEnergy();
        cast.battle.castImmediate(cast.sampo.getSkills().get(SkillType.ULTRA), cast.sampo, List.of(cast.enemy));
        return cast.sampo.getCurrentEnergy() - beforeCast + idle.sampo.getCurrentEnergy();
    }

    private static double lossFromAttacker(boolean windsheared) {
        Fixture f = new Fixture();
        if (windsheared) {
            f.enemy.getBuffManager().addBuff(new DotBuff(f.enemy, DamageElement.WIND, 100, 3, 0, "风化"));
        }
        double before = f.sampo.getCurrentHp();
        f.battle.applyDamage(f.sampo, new Damage(f.enemy, f.sampo, DamageElement.PHYSICAL, DamageType.NORMAL, 1000));
        return before - f.sampo.getCurrentHp();
    }

    private static final class Fixture {
        private final Character sampo;
        private final Enemy enemy = BattleFixture.monster();
        private final Battle battle;

        private Fixture() {
            sampo = CharacterFactory.create(SAMPO, LEVEL);
            battle = new Battle(new ArrayList<>(List.of(sampo, CharacterFactory.create(1001, LEVEL))),
                    new ArrayList<>(List.of(enemy)), BattleFixture.alwaysRoll(0.0));
            battle.startBattle();
        }
    }
}



