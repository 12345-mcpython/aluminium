package com.laosun.aluminium.test.content.lightcones;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.equipment.Weapon;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 血火啊，燃烧前路 (23039): 「施放战技或终结技时消耗等同于自身 <b>6%</b> 生命上限的生命值，并使本次攻击造成的伤害提高 <b>30%</b>；
 * 若该效果消耗的生命值高于 <b>500</b> 点，可使伤害额外提高 <b>30%</b>。若当前生命值不足，该效果最多使当前生命值降低至 1 点」.
 *
 * <p>What landed: the price and the first share, and the 1-HP floor (which {@code HealthState.consume} already enforces).
 * <p>Still open: the 500-point branch - see the status row; the marker it needs never armed in a measured run.
 */
public class ConeTwentyThreeThousandThirtyNineTest {

    private static final int LEVEL = 80;
    private static final int WEARER = 1204;

    private static final class Run {
        private final Character wearer;
        private final Enemy enemy;
        private final Battle battle;

        Run() {
            wearer = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(23039, LEVEL, false, 1));
            enemy = BattleFixture.monster();
            battle = new Battle(new ArrayList<>(List.of(wearer)), new ArrayList<>(List.of(enemy)),
                    BattleFixture.seeded());
            battle.startBattle();
            wearer.heal(wearer.getMaxHp());
        }

        /**
         * The boost the SKILL's own instance carries - the clause is about 「本次攻击」.
         */
        double payAndHit() {
            List<Damage> settled = new ArrayList<>();
            battle.addDamageObserver((Damage damage, double applied, double hpLost, double shield, boolean died) ->
                    settled.add(damage));
            battle.castImmediate(wearer.getSkills().get(SkillType.SKILL), wearer, List.of(enemy));
            battle.processRequests();
            return settled.stream()
                    .filter(d -> d.getType() == DamageType.NORMAL)
                    .filter(d -> d.getCastCategory() == com.laosun.aluminium.enums.SkillCategory.BPSKILL)
                    .mapToDouble(d -> d.boostArea().getRate() - 1.0)
                    .findFirst().orElse(Double.NaN);
        }
    }

    /**
     * The price is paid and the same attack is raised by 30%.
     */
    @Test
    public void theSkillPaysHpAndIsRaisedByThirtyPercent() {
        Run run = new Run();
        double before = run.wearer.getCurrentHp();
        Assertions.assertEquals(0.30, run.payAndHit(), 1e-6, "「本次攻击造成的伤害提高 30%」");
        Assertions.assertEquals(before * 0.94, run.wearer.getCurrentHp(), 1e-6,
                "「消耗等同于自身 6% 生命上限的生命值」");
    }

    /**
     * The price never takes her below 1 HP.
     */
    @Test
    public void thePriceStopsAtOneHp() {
        Run run = new Run();
        run.wearer.takeDamage(run.wearer.getCurrentHp() - 2);
        run.battle.castImmediate(run.wearer.getSkills().get(SkillType.SKILL), run.wearer, List.of(run.enemy));
        run.battle.processRequests();
        Assertions.assertEquals(1.0, run.wearer.getCurrentHp(), 1e-6,
                "「该效果最多使装备者的当前生命值降低至 1 点」");
    }
}
