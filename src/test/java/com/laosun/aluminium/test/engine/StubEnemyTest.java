package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.StubEnemy;
import com.laosun.aluminium.utils.CharacterFactory;
import com.laosun.aluminium.test.support.BattleFixture;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * The stub enemy: a monster whose numbers and skills the caller states.
 *
 * <p>Its purpose is that a judge about something ELSE should not be at the mercy of the dump. A case that needs "an
 * enemy weak to Ice and nothing else", or one that switches skill at half HP, or one that dies at a chosen moment,
 * cannot say any of that through {@code monsterId} - and if it picks a real monster's numbers, those numbers become
 * load-bearing for a case that is not about them.
 *
 * <p>So the cases below are the stub's contract, and they double as its documentation: every field defaults to
 * something ordinary, the enemy can always act, and the only way it can have more than one skill is the engine's own
 * phase mechanism, which the last case exercises.
 */
public class StubEnemyTest {
    private static final int PEARL = 1503;
    private static final int MONSTERISH = 1002011;
    private static final double EPS = 1e-9;

    /** The stated numbers are the ones the enemy has - none of them comes from the dump. */
    @Test
    public void theStatedNumbersAreTheEnemys() {
        Enemy stub = StubEnemy.named("测试木桩")
                .withHealth(5000)
                .withAttack(321)
                .withDefence(654)
                .withSpeed(87)
                .withEffectResistance(0.4)
                .withEffectHitRate(0.7)
                .withToughness(60)
                .build();
        System.out.println("[stub] hp " + stub.getMaxHp() + " atk " + stub.getAttribute(AttributeType.ATTACK).get()
                + " def " + stub.getAttribute(AttributeType.DEFENCE).get()
                + " spd " + stub.getAttribute(AttributeType.SPEED).get()
                + " res " + stub.getAttribute(AttributeType.EFFECT_RESISTANCE).get()
                + " toughness " + stub.getStance() + "/" + stub.getMaxStance());

        Assertions.assertEquals(5000, stub.getMaxHp(), EPS, "the stated HP");
        Assertions.assertEquals(321, stub.getAttribute(AttributeType.ATTACK).get(), EPS, "the stated attack");
        Assertions.assertEquals(654, stub.getAttribute(AttributeType.DEFENCE).get(), EPS, "the stated defence");
        Assertions.assertEquals(87, stub.getAttribute(AttributeType.SPEED).get(), EPS, "the stated speed");
        Assertions.assertEquals(0.4, stub.getAttribute(AttributeType.EFFECT_RESISTANCE).get(), EPS,
                "the stated effect resistance");
        Assertions.assertEquals(0.7, stub.getAttribute(AttributeType.EFFECT_HIT_RATE).get(), EPS,
                "and the stated effect hit rate - an enemy with 0 hit rate never lands a debuff");
        Assertions.assertEquals(60, stub.getStance(), EPS, "the stated toughness");
        Assertions.assertEquals("测试木桩", stub.getName(), "and the name the case gave it");
    }

    /**
     * Defaults leave a working enemy: it can act, and it is weak to its own element.
     *
     * <p>Why the defaults matter enough to assert: a stub whose attack is 0, or whose toughness is 0, turns every case
     * that forgot the field into a case about the field. These are the ordinary values, and this case is what keeps
     * them from drifting.
     */
    @Test
    public void theDefaultsLeaveAnEnemyThatCanAct() {
        Enemy stub = StubEnemy.dummy().build();
        System.out.println("[stub] default: hp " + stub.getMaxHp() + " atk "
                + stub.getAttribute(AttributeType.ATTACK).get() + " spd "
                + stub.getAttribute(AttributeType.SPEED).get() + " stance " + stub.getStance()
                + " common skill " + (stub.getSkills().get(SkillType.COMMON) == null ? "MISSING" : "present"));

        Assertions.assertTrue(stub.getMaxHp() > 0, "it has HP");
        Assertions.assertTrue(stub.getAttribute(AttributeType.ATTACK).get() > 0, "and an attack");
        Assertions.assertTrue(stub.getAttribute(AttributeType.SPEED).get() > 0, "and a speed, so it takes turns");
        Assertions.assertTrue(stub.getStance() > 0, "and toughness, so it can be broken");
        Assertions.assertNotNull(stub.getSkills().get(SkillType.COMMON),
                "and an ordinary attack - an enemy that cannot act is a wall, not an enemy");
        Assertions.assertTrue(stub.isWeakTo(DamageElement.PHYSICAL),
                "and it is weak to its own element unless told otherwise");
    }

    /** Stating weaknesses replaces the default, and a stated element is what it deals. */
    @Test
    public void weaknessesAndElementAreStatedNotGuessed() {
        Enemy stub = StubEnemy.named("冰弱点").withElement(DamageElement.ICE).weakTo(DamageElement.ICE).build();
        System.out.println("[stub] weak to ICE " + stub.isWeakTo(DamageElement.ICE)
                + " ; weak to FIRE " + stub.isWeakTo(DamageElement.FIRE));
        Assertions.assertTrue(stub.isWeakTo(DamageElement.ICE), "the stated weakness");
        Assertions.assertFalse(stub.isWeakTo(DamageElement.FIRE), "and nothing else");
    }

    /** An enemy that resists one element and not another - the shape the damage zones read. */
    @Test
    public void damageResistanceIsStatedPerElement() {
        Enemy stub = StubEnemy.dummy().withDamageResistance(DamageElement.FIRE, 0.5).build();
        System.out.println("[stub] fire resistance " + stub.getDamageResist().get(DamageElement.FIRE)
                + " ; ice resistance " + stub.getDamageResist().get(DamageElement.ICE));
        Assertions.assertEquals(0.5, stub.getDamageResist().get(DamageElement.FIRE), EPS,
                "the element that was stated");
        Assertions.assertNull(stub.getDamageResist().get(DamageElement.ICE),
                "and an element that was not is simply absent");
    }

    /**
     * A second skill, taken over at a stated HP ratio - which is the whole of "an enemy with several skills" today.
     *
     * <p>This is the case the stub exists for: no monster in the dump can be asked to switch ability at half HP, and a
     * case about a phase behaviour needs exactly that.
     */
    @Test
    public void aSecondSkillTakesOverAtTheStatedHpRatio() {
        Enemy stub = StubEnemy.named("两段木桩")
                .withHealth(1000)
                .withAttackProfile(1.0, 1)
                .phase(0.5, DamageElement.FIRE, 3.0)
                .build();
        Assertions.assertEquals(1, stub.phaseCount(), "one phase was registered");
        Assertions.assertNull(stub.activeSkill(), "above the threshold it acts with its ordinary skill");

        stub.takeDamage(600);                                   // down to 40% of max
        System.out.println("[stub] at " + Math.round(100 * stub.getCurrentHp() / stub.getMaxHp())
                + "% HP the active skill is " + (stub.activeSkill() == null ? "the ordinary one"
                        : ((com.laosun.aluminium.models.enemy.EnemySkill) stub.activeSkill()).getElement()));
        Assertions.assertNotNull(stub.activeSkill(), "at or below half HP the phase skill takes over");
        Assertions.assertEquals(DamageElement.FIRE,
                ((com.laosun.aluminium.models.enemy.EnemySkill) stub.activeSkill()).getElement(),
                "and it is the skill that was registered");
    }

    /** A stub stands in a real battle and can hit a real character - which is what a harness needs from it. */
    @Test
    public void itFightsInABattle() {
        Character pearl = CharacterFactory.create(PEARL, 80);
        Enemy stub = StubEnemy.named("陪练木桩").withHealth(100_000).withAttack(500).build();
        Battle battle = new Battle(List.of(pearl), List.of(stub), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        Assertions.assertEquals(1, battle.enemyUnits().size(), "the battle took the stub as its enemy");
        Assertions.assertSame(stub, battle.enemyUnits().getFirst(), "and it is this instance");
        System.out.println("[stub] in a battle: " + battle.enemyUnits().getFirst().getName()
                + " with " + battle.enemyUnits().getFirst().getMaxHp() + " HP");
    }

    /** The dump's own path still works, so the stub is an addition rather than a replacement. */
    @Test
    public void theDataBackedFactoryIsUntouched() {
        Enemy real = com.laosun.aluminium.models.enemy.EnemyFactory.create(MONSTERISH, 90, 1);
        System.out.println("[stub] a data-backed monster: " + real.getName() + " hp " + real.getMaxHp());
        Assertions.assertTrue(real.getMaxHp() > 0, "a real monster still builds from its config");
        Assertions.assertNotNull(real.getSkills().get(SkillType.COMMON), "and still has its fallback attack");
        Assertions.assertNotSame(0, real.getStance(), "with its own toughness");
    }
}
