package com.laosun.aluminium.test.content.characters.silverwolflv999;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.entity.Character;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.trigger.TriggerTable;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Character 1506 (Silver Wolf LV.999): the three clauses the audit found without a landing point - the control immunity
 * inside 【无敌玩家】, Eidolon 1's zone vulnerability, and the ENHANCED ELATION SKILL half of 「拥有强化普攻和强化欢愉技」.
 *
 * <p>Each case is written so that the clause it guards can go red: the immunity is asserted BOTH ways (the same control lands
 * after the state ends), the vulnerability as a ratio between two fixtures that differ only in the Eidolon rank, and the
 * replacement by the slot her own skill map hands back after the Ultimate.
 */
public class SilverWolfLV999GodmodeClausesTest {
    private static final int WOLF = 1506;
    private static final String FREEZE = "冻结";
    private static final String GOD = "无敌玩家";
    private static final String ZONE = "结界";

    /**
     * 「【无敌玩家】状态下，「银狼LV.999」免疫控制类负面状态」 - and the immunity leaves with the state.
     *
     * <p>⚠ The three phases are one battle on purpose: a fixture that only showed "the control does not land" would pass just
     * as well if the control could never land at all (the fixture monster is immune to freeze, which is why the injector is a
     * party member with a hand-built table). Phase 1 states the premise - a 100% base chance control lands on her while no
     * state protects her - phase 2 is the clause, and phase 3 is its lifetime.
     */
    @Test
    public void godmodeIsImmuneToControlAndTheImmunityLeavesWithIt() {
        Character wolf = CharacterFactory.create(WOLF, 80);
        Character injector = Character.fromAttributes("injector", 10_000, 100, 100, 100);
        injector.setTriggerTable(new TriggerTable(9999, List.of(TriggerSpecs.rule(
                TriggerEvent.TURN_START.name(), List.of(), control()))));
        Battle battle = new Battle(List.of(wolf, injector), List.of(BattleFixture.monster()),
                BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        battle.processRequests();

        battle.fire(com.laosun.aluminium.models.trigger.TriggerPayload.Simple.of(TriggerEvent.TURN_START, injector, wolf, 0, 0));
        Assertions.assertTrue(wolf.getBuffManager().hasState(FREEZE),
                "premise: a 100% base chance control lands on her when nothing protects her");
        wolf.getBuffManager().removeState(FREEZE);

        castUltimate(battle, wolf);
        Assertions.assertTrue(wolf.getBuffManager().hasState(GOD), "premise: the Ultimate puts her in 【无敌玩家】");
        battle.fire(com.laosun.aluminium.models.trigger.TriggerPayload.Simple.of(TriggerEvent.TURN_START, injector, wolf, 0, 0));
        System.out.println("[1506] in 【无敌玩家】, after the same control: 冻结="
                + wolf.getBuffManager().hasState(FREEZE));
        Assertions.assertFalse(wolf.getBuffManager().hasState(FREEZE),
                "「【无敌玩家】状态下，「银狼LV.999」免疫控制类负面状态」");

        for (int use = 1; use <= 3; use++) {
            cast(battle, wolf, SkillType.COMMON, battle.enemyUnits().getFirst());
        }
        Assertions.assertFalse(wolf.getBuffManager().hasState(GOD), "premise: three enhanced basics end the state");
        battle.fire(com.laosun.aluminium.models.trigger.TriggerPayload.Simple.of(TriggerEvent.TURN_START, injector, wolf, 0, 0));
        System.out.println("[1506] after leaving 【无敌玩家】, the same control: 冻结="
                + wolf.getBuffManager().hasState(FREEZE));
        Assertions.assertTrue(wolf.getBuffManager().hasState(FREEZE),
                "「【无敌玩家】状态下…免疫」 is scoped by the state, so the control lands again once it is gone");
    }

    /**
     * 「结界中的敌方目标受到的伤害提高 20%」 (星魂 1) - read as a RATIO between two fixtures that differ only in the rank.
     *
     * <p>⚠ The ratio and not the absolute: the number has to pass the fixture monster's defence and resistance, so pinning it
     * would pin those. Both readings are the same attack on the same monster, so every other factor cancels - and the ratio
     * still catches the defect this case exists for: without the clause it is 1.0, and a double application would read 1.44.
     */
    @Test
    public void eidolonOneRaisesTheDamageEnemiesTakeInsideTheZone() {
        double atZero = damageInsideTheZone(0);
        double atOne = damageInsideTheZone(1);
        System.out.println("[1506] damage the same attack deals inside the zone: E0 " + atZero + " -> E1 " + atOne
                + " (x" + (atOne / atZero) + ")");
        Assertions.assertTrue(atZero > 0, "premise: the attack really settles damage");
        Assertions.assertEquals(1.2, atOne / atZero, 1e-9,
                "「结界中的敌方目标受到的伤害提高 20%」 - and only once, though two rules apply it");
    }

    /**
     * 「拥有强化普攻和强化欢愉技」 - the second half, which nothing installed until the audit.
     */
    @Test
    public void theUltimateInstallsTheEnhancedElationSkillToo() {
        Battle battle = newBattle();
        Character wolf = battle.characters().getFirst();
        Assertions.assertEquals(20, wolf.getSkills().get(SkillType.ELATION_SKILL).getSkillSlot(),
                "premise: her Elation skill is slot 20 before the Ultimate");
        castUltimate(battle, wolf);
        System.out.println("[1506] her Elation skill's slot after the Ultimate: "
                + wolf.getSkills().get(SkillType.ELATION_SKILL).getSkillSlot());
        Assertions.assertEquals(21, wolf.getSkills().get(SkillType.ELATION_SKILL).getSkillSlot(),
                "【无敌玩家】状态下拥有强化欢愉技 - 崩坏级伤害演示 is slot 21");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    /**
     * One ordinary attack's damage on the enemy, in a battle whose Zone is already up at the given Eidolon rank.
     *
     * <p>⚠ Her SKILL, not her basic: the Ultimate also installs the ENHANCED basic (a 240% row), which kills this fixture's
     * monster outright - and a reading capped by its HP pool reports a ratio of 1.0 whatever the vulnerability is. The premise
     * is asserted rather than assumed, so a future one-shot fails loudly instead of quietly reading 1.0.
     */
    private static double damageInsideTheZone(int eidolonRank) {
        Battle battle = newBattle(eidolonRank);
        Character wolf = battle.characters().getFirst();
        castUltimate(battle, wolf);
        Assertions.assertTrue(wolf.getBuffManager().hasState(ZONE), "premise: the Ultimate deployed the Zone");
        Enemy victim = battle.enemyUnits().getFirst();
        double hp = victim.getCurrentHp();
        double before = hp;
        cast(battle, wolf, SkillType.SKILL, victim);
        double dealt = before - victim.getCurrentHp();
        Assertions.assertTrue(dealt < hp, "premise: the attack must not kill it, or the HP cap hides the ratio (dealt "
                + dealt + " of " + hp + ")");
        return dealt;
    }

    /**
     * A 100% base chance control aimed at the event's target - the same shape RobinConcertoTest uses, because the fixture
     * monster is immune to freeze.
     */
    private static EffectSpec control() {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "APPLY_CONTROL");
        TriggerSpecs.set(effect, "control", FREEZE);
        TriggerSpecs.set(effect, "turns", 1);
        TriggerSpecs.set(effect, "baseChance", 1.0);
        TriggerSpecs.set(effect, "target", "target");
        return effect;
    }

    /**
     * Casts a slot through the engine's own request path, then announces the end of the attack (the event her
     * 「完整施放 3 次强化普攻后退出」 clause hangs on).
     */
    private static void cast(Battle battle, Character unit, SkillType slot, Enemy victim) {
        battle.requestSkill(unit.getSkills().get(slot), unit, List.of(victim));
        battle.processRequests();
        battle.fire(com.laosun.aluminium.models.trigger.TriggerPayload.Simple.of(TriggerEvent.ATTACK_FINISHED, unit, victim, 1, 0));
        battle.processRequests();
    }

    private static void castUltimate(Battle battle, Character wolf) {
        wolf.setCurrentEnergy(wolf.getMaxEnergy());
        battle.castUltra(wolf, List.of(battle.enemyUnits().getFirst()));
        battle.processRequests();
    }

    private static Battle newBattle() {
        return newBattle(0);
    }

    private static Battle newBattle(int eidolonRank) {
        Battle battle = new Battle(
                List.of(CharacterFactory.create(WOLF, 80, false, null, null, eidolonRank), CharacterFactory.create(1204, 80)),
                List.of(BattleFixture.monster()), BattleFixture.alwaysRoll(0.9999));
        battle.startBattle();
        battle.processRequests();
        return battle;
    }
}
