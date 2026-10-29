package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.SkillCategory;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.entity.Character;
import com.laosun.aluminium.models.damage.Damage;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.buff.DotBuff;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.models.trigger.TriggerPayload;
import com.laosun.aluminium.models.trigger.TriggerTable;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * The typed cast payload must carry the three facts the old positional overloads carried: the cast
 * category, the hit count, and the skill slot.
 *
 * <p>Each fact is observed through a different event so one dropped field cannot hide behind another:
 * {@code CAST_SETUP} sees the slot before settlement, {@code SKILL_CAST} sees the category and the
 * hit count after it, and {@code ALLY_ATTACK} sees the hit count on the same firing.
 */
public class TriggerPayloadTest {

    /**
     * A cast of 姬子's Skill reaches all three gates with the values they need.
     */
    @Test
    public void aCastPayloadCarriesCategoryHitCountAndSlot() {
        Character owner = CharacterFactory.create(1003, 80);
        owner.getResources().register("cast", 9, 0);
        owner.getResources().register("setup", 9, 0);
        owner.getResources().register("ally", 9, 0);

        owner.setTriggerTable(new TriggerTable(1003, List.of(
                TriggerSpecs.rule("CAST_SETUP", List.of("from_skill_id == 2"), gain("setup")),
                TriggerSpecs.rule("SKILL_CAST", List.of("hit_count >= 1", "from_skill_id == 2"), gain("cast")),
                TriggerSpecs.rule("ALLY_ATTACK", List.of("from_skill SKILL", "hit_count >= 1"), gain("ally"))),
                List.of()));

        Battle battle = new Battle(List.of(owner), List.of(BattleFixture.monster()), new Random(0));
        battle.startBattle();
        Enemy foe = battle.enemyUnits().getFirst();

        battle.castImmediate(owner.getSkills().get(SkillType.SKILL), owner, List.of(foe));

        Assertions.assertEquals(1, owner.getResources().value("setup"),
                "CAST_SETUP must carry the skill slot");
        Assertions.assertEquals(1, owner.getResources().value("cast"),
                "SKILL_CAST must carry the category, the hit count and the slot");
        Assertions.assertEquals(1, owner.getResources().value("ally"),
                "ALLY_ATTACK must carry the hit count");
    }

    /**
     * Damage, break and kill payloads keep the cast that produced them.
     *
     * <p>Each gate uses {@code from_skill}; a payload that dropped the category would leave the
     * counter at zero even though the event itself still fired.
     */
    @Test
    public void damageBreakAndKillPayloadsKeepTheirCast() {
        Character owner = CharacterFactory.create(1003, 80);
        for (String counter : List.of("dealing", "settled", "break", "kill")) {
            owner.getResources().register(counter, 9, 0);
        }
        owner.setTriggerTable(new TriggerTable(1003, List.of(
                TriggerSpecs.rule("DEALING_DAMAGE", List.of("actor == self", "from_skill COMMON"),
                        gain("dealing")),
                TriggerSpecs.rule("DAMAGE_SETTLED", List.of("actor == self", "from_skill COMMON"),
                        gain("settled")),
                TriggerSpecs.rule("BREAK", List.of("actor == self", "from_skill SKILL"), gain("break")),
                TriggerSpecs.rule("KILL", List.of("actor == self", "from_skill COMMON"), gain("kill"))),
                List.of()));

        Enemy first = EnemyFactory.create(1002011, 90, 1);
        Enemy broken = EnemyFactory.create(1002011, 90, 1);
        broken.setStance(1);
        broken.setMaxStance(1);
        Enemy doomed = EnemyFactory.create(8002040, 90, 1);
        doomed.setAttribute(AttributeType.HEALTH, new DoubleValue(1));
        doomed.heal(1);

        Battle battle = new Battle(List.of(owner), List.of(first, broken, doomed), new Random(0));
        battle.startBattle();

        battle.castImmediate(owner.getSkills().get(SkillType.COMMON), owner, List.of(first));
        Assertions.assertEquals(1, owner.getResources().value("dealing"),
                "DEALING_DAMAGE must carry the cast category");
        Assertions.assertEquals(1, owner.getResources().value("settled"),
                "DAMAGE_SETTLED must carry the cast category");

        battle.reduceToughness(owner, broken, DamageElement.FIRE, 9_999, SkillCategory.BPSKILL);
        Assertions.assertEquals(1, owner.getResources().value("break"),
                "BREAK must carry the cast category");

        battle.castImmediate(owner.getSkills().get(SkillType.COMMON), owner, List.of(doomed));
        Assertions.assertEquals(1, owner.getResources().value("kill"),
                "KILL must carry the cast category");
    }

    /**
     * Ally-only subject payloads keep the camp filter and their amounts; a landed debuff announces the
     * applier and its subject.
     */
    @Test
    public void allySubjectAndDebuffPayloadsKeepTheirAmounts() {
        Character owner = CharacterFactory.create(1003, 80);
        for (String counter : List.of("taking", "hp", "lethal", "debuff", "anyTaking")) {
            owner.getResources().register(counter, 9, 0);
        }
        owner.setTriggerTable(new TriggerTable(1003, List.of(
                TriggerSpecs.rule("TAKING_HIT", List.of("target == self"), gain("taking")),
                TriggerSpecs.rule("TAKING_HIT", List.of(), gain("anyTaking")),
                TriggerSpecs.rule("HP_LOST", List.of("target == self"), gain("hp")),
                TriggerSpecs.rule("LETHAL_DAMAGE", List.of("target == self"), gain("lethal")),
                TriggerSpecs.rule("DEBUFF_APPLIED", List.of("actor == self"), gain("debuff"))),
                List.of()));

        Enemy foe = EnemyFactory.create(8002040, 90, 1);
        Battle battle = new Battle(List.of(owner), List.of(foe), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();

        battle.applyDamage(foe, new Damage(owner, foe, DamageElement.PHYSICAL, DamageType.NORMAL, 100));
        Assertions.assertEquals(0, owner.getResources().value("anyTaking"),
                "an enemy taking a hit must not be announced to our tables");

        battle.applyDamage(owner, new Damage(foe, owner, DamageElement.PHYSICAL, DamageType.NORMAL, 100));
        Assertions.assertEquals(1, owner.getResources().value("anyTaking"),
                "our own hit is announced once");
        Assertions.assertEquals(1, owner.getResources().value("taking"),
                "TAKING_HIT must reach our side with its subject");
        Assertions.assertEquals(1, owner.getResources().value("hp"),
                "HP_LOST must reach our side with its amount");

        battle.tryApplyDebuff(owner, foe, new DotBuff(owner, DamageElement.FIRE, 100, 2), 1.0, null);
        Assertions.assertEquals(1, owner.getResources().value("debuff"),
                "DEBUFF_APPLIED must reach the applier's table");

        battle.applyDamage(owner,
                new Damage(foe, owner, DamageElement.PHYSICAL, DamageType.NORMAL, 1_000_000));
        Assertions.assertEquals(1, owner.getResources().value("lethal"),
                "LETHAL_DAMAGE must be delivered before the death is committed");
    }

    /**
     * Ally value payloads keep their amounts.
     */
    @Test
    public void allyValuePayloadsKeepTheirAmounts() {
        Character owner = CharacterFactory.create(1003, 80);
        for (String counter : List.of("healed", "shield", "energy")) {
            owner.getResources().register(counter, 9, 0);
        }
        owner.setTriggerTable(new TriggerTable(1003, List.of(
                TriggerSpecs.rule("HEALED", List.of(), gain("healed")),
                TriggerSpecs.rule("SHIELD_GRANTED", List.of(), gain("shield")),
                TriggerSpecs.rule("ENERGY_GAINED", List.of(), gain("energy"))),
                List.of()));

        Battle battle = new Battle(List.of(owner), List.of(BattleFixture.monster()),
                BattleFixture.alwaysRoll(0.0));
        battle.startBattle();

        owner.takeDamage(200);
        battle.heal(owner, owner, 100);
        Assertions.assertEquals(1, owner.getResources().value("healed"),
                "HEALED must be delivered to the healed ally");

        battle.grantShield(owner, owner, 100);
        Assertions.assertEquals(1, owner.getResources().value("shield"),
                "SHIELD_GRANTED must be delivered to the shielded ally");

        battle.grantEnergy(owner, 10);
        Assertions.assertEquals(1, owner.getResources().value("energy"),
                "ENERGY_GAINED must be delivered to the unit that gained");
    }

    /**
     * State-ended and resource-changed payloads scope their name to the firing the conditions read.
     */
    @Test
    public void stateEndedAndResourceChangedPayloadsKeepTheirNames() {
        Character owner = CharacterFactory.create(1003, 80);
        owner.getResources().register("state", 9, 0);
        owner.setTriggerTable(new TriggerTable(1003, List.of(
                TriggerSpecs.rule("STATE_ENDED", List.of("self state_ended test"), gain("state")),
                TriggerSpecs.rule("RESOURCE_CHANGED", List.of("resource_changed:res"), energyGain())),
                List.of()));

        Battle battle = new Battle(List.of(owner), List.of(BattleFixture.monster()),
                BattleFixture.alwaysRoll(0.0));
        battle.startBattle();

        battle.fireStateEnded(owner, "test", 2);
        Assertions.assertEquals(1, owner.getResources().value("state"),
                "STATE_ENDED must expose the state's name to its rules");

        double energyBefore = owner.getCurrentEnergy();
        battle.fire(TriggerPayload.ResourceChanged.of(TriggerEvent.RESOURCE_CHANGED, owner, "res", 1));
        Assertions.assertTrue(owner.getCurrentEnergy() > energyBefore,
                "RESOURCE_CHANGED must expose the resource's name to its rules");
    }

    /**
     * A plain payload still reaches the table of the event it names.
     */
    @Test
    public void aSimplePayloadCarriesItsEvent() {
        Character owner = CharacterFactory.create(1003, 80);
        owner.getResources().register("sp", 9, 0);
        owner.setTriggerTable(new TriggerTable(1003, List.of(
                TriggerSpecs.rule("SKILL_POINT_GAINED", List.of("skill_point >= 4"), gain("sp"))),
                List.of()));

        Battle battle = new Battle(List.of(owner), List.of(BattleFixture.monster()),
                BattleFixture.alwaysRoll(0.0));
        battle.startBattle();

        battle.gainSkillPoint(1);
        Assertions.assertEquals(1, owner.getResources().value("sp"),
                "the plain SKILL_POINT_GAINED payload must reach the rule table");
    }

    /**
     * A one-point resource gain, used as a firing counter.
     *
     * @param resource the counter's name
     * @return the effect
     */
    private static EffectSpec gain(String resource) {
        EffectSpec gain = new EffectSpec();
        TriggerSpecs.set(gain, "op", "GAIN_RESOURCE");
        TriggerSpecs.set(gain, "resource", resource);
        TriggerSpecs.set(gain, "amount", 1.0);
        TriggerSpecs.set(gain, "target", "self");
        return gain;
    }

    /**
     * A one-point energy gain. Used where a resource gain would fire RESOURCE_CHANGED again and recurse.
     *
     * @return the effect
     */
    private static EffectSpec energyGain() {
        EffectSpec energy = new EffectSpec();
        TriggerSpecs.set(energy, "op", "GAIN_ENERGY");
        TriggerSpecs.set(energy, "amount", 1.0);
        return energy;
    }
}
