package com.laosun.aluminium.test.content.lightcones;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
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
 * 「我」的诞生 (21006): 「使装备者追加攻击造成的伤害提高 <b>24%</b>；若该敌方目标当前生命值百分比小于等于 <b>50%</b>，
 * 则追加攻击造成的伤害额外提高 <b>24%</b>」.
 *
 * <p><b>The scope question, answered.</b> The old row called this half unproven because the engine's {@code SkillCategory} has
 * no FOLLOW_UP. It does not need one: a follow-up attack IS {@code DamageType.ADDITIONAL} in this engine (the same fact
 * {@code BattleDamage} states when it pays FOLLOW_UP_DAMAGE_BOOST), so {@code damage_type: "ADDITIONAL"} is the precise
 * scope. This test pins both sides of it - an additional instance is raised, an ordinary one is not.
 */
public class ConeTwentyOneThousandSixTest {

    private static final int LEVEL = 80;
    private static final int WEARER = 1204;

    private static final class Run {
        private final Character wearer;
        private final Enemy enemy;
        private final Battle battle;
        private final List<Damage> settled = new ArrayList<>();

        Run() {
            wearer = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(21006, LEVEL, false, 1));
            enemy = BattleFixture.monster();
            battle = new Battle(new ArrayList<>(List.of(wearer)), new ArrayList<>(List.of(enemy)),
                    BattleFixture.seeded());
            battle.startBattle();
            battle.addDamageObserver((Damage damage, double applied, double hpLost, double shield, boolean died) ->
                    settled.add(damage));
        }

        /**
         * The boost the instance carries in each scope, as a fraction of the attack it was built from.
         */
        double boostOf(DamageType type) {
            return settled.stream().filter(d -> d.getType() == type)
                    .mapToDouble(Damage::getSkillBaseValue).findFirst().orElse(Double.NaN);
        }
    }

    @Test
    public void theFlatShareIsOnTheWearer() {
        Run run = new Run();
        Assertions.assertEquals(0.24, run.wearer.getAttribute(AttributeType.FOLLOW_UP_DAMAGE_BOOST).get(), 1e-9,
                "「追加攻击造成的伤害提高 24%」 - a flat share on the wearer, no condition");
    }

    /**
     * The second sentence's +24% lands on an ADDITIONAL instance and only when the target is at or below half HP.
     */
    @Test
    public void theExtraShareIsScopedToFollowUpsAndToALowTarget() {
        Run run = new Run();
        run.enemy.takeDamage(run.enemy.getMaxHp() * 0.6);
        Damage additional = new Damage(run.wearer, run.enemy, DamageElement.PHYSICAL, DamageType.ADDITIONAL, 1000);
        run.battle.applyDamage(run.enemy, additional);
        run.battle.processRequests();
        Assertions.assertTrue(additional.boostArea().getRate() > 1.24,
                "an ADDITIONAL instance against a low target carries the flat 0.24 AND the extra 0.24 (was x"
                        + additional.boostArea().getRate() + ")");

        Run healthy = new Run();
        Damage ordinary = new Damage(healthy.wearer, healthy.enemy, DamageElement.PHYSICAL, DamageType.NORMAL, 1000);
        healthy.battle.applyDamage(healthy.enemy, ordinary);
        healthy.battle.processRequests();
        Assertions.assertEquals(1.0, ordinary.boostArea().getRate(), 1e-9,
                "「追加攻击」 - an ordinary hit carries nothing from this cone");
    }
}
