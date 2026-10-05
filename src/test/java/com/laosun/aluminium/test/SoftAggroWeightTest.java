package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.buff.ShieldBuff;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 「受到攻击的概率大幅提高」 — the SOFT aggro weight, and why it is an attribute rather than {@code TAUNT}.
 *
 * <p>三月七's Skill states no magnitude, which is why this clause sat registered for a long time. The magnitude is her
 * Skill's fifth parameter (the one her prose never references) and the property the game writes is
 * {@code AggroAddedRatio}; {@code characters/1001.json}'s {@code skill_soft_aggro} note carries the evidence chain.
 * This class pins the consequences: the arithmetic, the guard, the HP% gate, and whose shield carries it.
 */
public class SoftAggroWeightTest {
    private static final double EPS = 1e-9;
    private static final int MARCH = 1001;
    private static final int ALLY = 1210;
    private static final int BYSTANDER = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /** ⚠ The ratio multiplies the unit's OWN weight: 100 x (1 + 5) = 600, so two equal allies become 6:1. */
    @Test
    public void theRatioMultipliesTheUnitsOwnAggroWeight() {
        Character marked = withAggro(100);
        Character plain = withAggro(100);
        marked.setTriggerTable(new TriggerTable(MARCH, List.of(TriggerSpecs.rule(
                TriggerEvent.BATTLE_START.name(), List.of(), aggroRatio(5.0)))));
        Battle battle = new Battle(List.of(marked, plain), List.of(dummy()), new Random(0));

        battle.startBattle();

        Assertions.assertEquals(600, battle.aggroOf(marked), EPS, "100 x (1 + 5): AggroAddedRatio is a RATIO");
        Assertions.assertEquals(100, battle.aggroOf(plain), EPS, "the unit without it keeps its stated weight");

        Map<CanHit, Double> table = battle.getAggroTable(List.of(marked, plain));
        Assertions.assertEquals(600.0 / 700, table.get(marked), EPS, "6/7 instead of 1/2 -- a soft weight, not a lock");
        Assertions.assertEquals(100.0 / 700, table.get(plain), EPS, "and the other ally is still a candidate");
    }

    /** ⚠ The guard: a stated change that would zero the weight keeps the weight instead of deleting the unit. */
    @Test
    public void aStatedChangeThatWouldZeroTheWeightDoesNotDeleteTheUnit() {
        Character doomed = withAggro(100);
        doomed.setTriggerTable(new TriggerTable(MARCH, List.of(TriggerSpecs.rule(
                TriggerEvent.BATTLE_START.name(), List.of(), aggroRatio(-2.0)))));
        Battle battle = new Battle(List.of(doomed), List.of(dummy()), new Random(0));

        battle.startBattle();

        Assertions.assertEquals(100, battle.aggroOf(doomed), EPS,
                "1 + (-2) <= 0 would take a LIVING unit out of the aggro table -- the stated weight is kept instead");
        Assertions.assertEquals(1.0, battle.getAggroTable(List.of(doomed)).get(doomed), EPS, "still a legal candidate");
    }

    /** ⚠ Her Skill's gate, both branches: at or above 30% HP it lands, below it her Skill raises nothing. */
    @Test
    public void herSkillRaisesAggroOnlyWhileTheAimedAllyIsAtThirtyPercentOrMore() {
        Assertions.assertEquals(6.0, aimedAllyWeightRatio(false), 1e-6,
                "「若该目标当前生命值百分比大于等于30%」 -- x6 above the gate");
        Assertions.assertEquals(1.0, aimedAllyWeightRatio(true), 1e-6,
                "below 30% the game pins MDF_AggroUp to 0, so the weight must be untouched");
    }

    /** ⚠ Only the aimed ally is affected, and 星魂 2's battle-start shield carries no aggro at all. */
    @Test
    public void onlyTheAimedAllyIsAffectedAndTheEidolonShieldIsNot() {
        Character march = CharacterFactory.create(MARCH, LEVEL);
        Character aimed = CharacterFactory.create(ALLY, LEVEL);
        Character bystander = CharacterFactory.create(BYSTANDER, LEVEL);
        Battle battle = new Battle(List.of(march, aimed, bystander), List.of(dummy()), new Random(0));
        battle.startBattle();
        double bystanderBefore = battle.aggroOf(bystander);

        battle.castImmediate(march.getSkills().get(SkillType.SKILL), march, List.of(aimed));

        Assertions.assertEquals(bystanderBefore, battle.aggroOf(bystander), EPS,
                "「指定我方单体」 -- only the aimed ally, never the bystander");

        // 星魂 2 grants a shield at battle start from a DIFFERENT modifier, which has no MDF_AggroUp in the game data.
        Character withEidolon = CharacterFactory.create(MARCH, LEVEL, true, null, null, 2);
        Character shielded = CharacterFactory.create(ALLY, LEVEL);
        Battle second = new Battle(List.of(withEidolon, shielded), List.of(dummy()), new Random(0));
        double marchBefore = second.aggroOf(withEidolon);
        double allyBefore = second.aggroOf(shielded);

        second.startBattle();

        // The shield itself answers `hasBuff(ShieldBuff.class)` -- a shield is not a named state. Which of the two it
        // lands on is the engine's pick (「当前生命值百分比最低的我方目标」 is ambiguous at full HP), so the
        // precondition is "one of them", while the claim below covers BOTH.
        boolean shieldedSomebody = withEidolon.getBuffManager().hasBuff(ShieldBuff.class)
                || shielded.getBuffManager().hasBuff(ShieldBuff.class);
        Assertions.assertTrue(shieldedSomebody, "precondition: 星魂 2's battle-start shield landed");
        Assertions.assertEquals(marchBefore, second.aggroOf(withEidolon), EPS,
                "MAvatar_March7th_00_Rank02_Shield has no MDF_AggroUp, so that shield must not raise aggro");
        Assertions.assertEquals(allyBefore, second.aggroOf(shielded), EPS,
                "and not on the other side of the pick either");
    }

    // ==================================================================

    /** The aimed ally's aggro after her Skill, divided by what it was before; `belowGate` damages them first. */
    private static double aimedAllyWeightRatio(boolean belowGate) {
        Character march = CharacterFactory.create(MARCH, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = dummy();
        Battle battle = new Battle(List.of(march, ally), List.of(enemy), new Random(0));
        battle.startBattle();
        if (belowGate) {
            int guard = 0;
            while (ally.getCurrentHp() / ally.getMaxHp() >= 0.3 && guard++ < 40) {
                battle.applyDamage(ally, new Damage(enemy, ally, DamageElement.PHYSICAL, DamageType.NORMAL,
                        ally.getMaxHp() * 0.2));
            }
            Assertions.assertTrue(ally.getCurrentHp() > 0, "the fixture must leave the ally alive");
            Assertions.assertTrue(ally.getCurrentHp() / ally.getMaxHp() < 0.3, "and below the gate");
        }
        double before = battle.aggroOf(ally);

        battle.castImmediate(march.getSkills().get(SkillType.SKILL), march, List.of(ally));

        return battle.aggroOf(ally) / before;
    }

    private static EffectSpec aggroRatio(double value) {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "MODIFY_ATTR");
        TriggerSpecs.set(effect, "attribute", AttributeType.AGGRO_ADDED_RATIO.attributeString);
        TriggerSpecs.set(effect, "percent", value);
        TriggerSpecs.set(effect, "permanent", true);
        TriggerSpecs.set(effect, "target", "self");
        return effect;
    }

    /** A character with the given stated aggro, bypassing the data (the shape AggroTest uses). */
    private static Character withAggro(int aggro) {
        Character character = Character.fromAttributes("c" + aggro, 10_000, 100, 100, 100);
        character.setAggro(aggro);
        return character;
    }

    private static Enemy dummy() {
        return EnemyFactory.create(MONSTER, 90, 1);
    }
}
