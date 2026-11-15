package com.laosun.aluminium.test.content.lightcones;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.equipment.Weapon;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.trigger.TriggerTable;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Light cone 21006: a flat follow-up boost (+24%) plus an extra +24% against a target at or below half HP.
 *
 * <p>Rank 1 states 0.24 / 0.5 / 0.24, so a follow-up against a healthy target is 1.24x a cone-less one, against a low
 * target 1.48x. Note: The extra rule MUST be scoped to ADDITIONAL damage: on DEALING_DAMAGE it would otherwise raise the
 * wearer's ORDINARY damage whenever the target is low, which the text never says. That is the third reading below.
 *
 * <p>「我」的诞生 (21006): 「使装备者追加攻击造成的伤害提高 <b>24%</b>；若该敌方目标当前生命值百分比小于等于 <b>50%</b>，
 * 则追加攻击造成的伤害额外提高 <b>24%</b>」.
 *
 * <p><b>The scope question, answered.</b> The old row called this half unproven because the engine's {@code SkillCategory} has
 * no FOLLOW_UP. It does not need one: a follow-up attack IS {@code DamageType.ADDITIONAL} in this engine (the same fact
 * {@code BattleDamage} states when it pays FOLLOW_UP_DAMAGE_BOOST), so {@code damage_type: "ADDITIONAL"} is the precise
 * scope. The instance-reading test below pins both sides of it - an additional instance is raised, an ordinary one is not.
 */
public class TheBirthOfTheSelfTest {
    private static final int CONE = 21006;
    private static final int WEARER = 1205;
    private static final int RUN_WEARER = 1204;
    private static final int LEVEL = 80;

    /**
     * One deterministic additional-damage instance against a FRESH enemy per reading (discipline 149).
     */
    private double followUp(boolean withCone, double enemyHpShare) {
        Character unit = withCone
                ? CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, 1))
                : CharacterFactory.create(WEARER, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(unit), List.of(enemy), BattleFixture.seeded());
        battle.startBattle();
        while (enemy.getCurrentHp() / enemy.getMaxHp() > enemyHpShare) {
            battle.applyDamage(enemy, new Damage(unit, enemy, DamageElement.PHYSICAL, DamageType.NORMAL,
                    enemy.getMaxHp() * 0.1));
        }
        return battle.applyAdditionalDamage(unit, enemy, DamageElement.FIRE, 1000, 0.0, 1.5);
    }

    /**
     * The same low-HP enemy, but hit with an ORDINARY instance: the extra half must not touch it.
     */
    private double ordinaryHit(boolean withCone, double enemyHpShare) {
        Character unit = withCone
                ? CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, 1))
                : CharacterFactory.create(WEARER, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(unit), List.of(enemy), BattleFixture.seeded());
        battle.startBattle();
        while (enemy.getCurrentHp() / enemy.getMaxHp() > enemyHpShare) {
            battle.applyDamage(enemy, new Damage(unit, enemy, DamageElement.PHYSICAL, DamageType.NORMAL,
                    enemy.getMaxHp() * 0.1));
        }
        return battle.applyDamage(enemy, new Damage(unit, enemy, DamageElement.PHYSICAL, DamageType.NORMAL, 1000));
    }

    @Test
    public void theSpecCarriesTheUnconditionalHalf() {
        Character unit = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, 1));
        int constant = 0;
        for (var rule : unit.getTriggerTable().matching(TriggerEvent.BATTLE_START,
                new TriggerTable.TriggerContext(unit, unit, unit, 0, 0))) {
            for (var effect : rule.effects()) {
                if ("FOLLOW_UP_DAMAGE_BOOST".equals(effect.getAttribute())) {
                    constant++;
                    System.out.println("[21006] spec constant percent=" + effect.getPercent());
                    Assertions.assertEquals(0.24, effect.getPercent(), 1e-9, "rank 1 states 24%");
                }
            }
        }
        Assertions.assertEquals(1, constant, "this cone has no ability_property row, so we write the constant");
    }

    @Test
    public void theExtraHalfAppliesOnlyToFollowUpsAgainstALowTarget() {
        double healthy = followUp(true, 0.9);
        double healthyNoCone = followUp(false, 0.9);
        double low = followUp(true, 0.4);
        double lowNoCone = followUp(false, 0.4);
        double lowOrdinary = ordinaryHit(true, 0.4);
        double lowOrdinaryNoCone = ordinaryHit(false, 0.4);
        System.out.println("[21006] follow-up healthy " + healthy + " vs no cone " + healthyNoCone
                + " ratio=" + (healthy / healthyNoCone));
        System.out.println("[21006] follow-up low " + low + " vs no cone " + lowNoCone
                + " ratio=" + (low / lowNoCone));
        System.out.println("[21006] ORDINARY at low hp " + lowOrdinary + " vs no cone " + lowOrdinaryNoCone
                + " ratio=" + (lowOrdinary / lowOrdinaryNoCone));
        Assertions.assertEquals(1.24, healthy / healthyNoCone, 1e-6, "the unconditional 24%");
        Assertions.assertEquals(1.48, low / lowNoCone, 1e-6, "and the extra 24% below half HP");
        Assertions.assertEquals(1.0, lowOrdinary / lowOrdinaryNoCone, 1e-6,
                "the extra half is scoped to ADDITIONAL damage: an ordinary hit is untouched");
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

    private static final class Run {
        private final Character wearer;
        private final Enemy enemy;
        private final Battle battle;
        private final List<Damage> settled = new ArrayList<>();

        Run() {
            wearer = CharacterFactory.create(RUN_WEARER, LEVEL, true, Weapon.build(21006, LEVEL, false, 1));
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
}
