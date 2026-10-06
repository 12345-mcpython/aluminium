package com.laosun.aluminium.test.content.characters;


import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.enums.TriggerEvent;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Signal;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.buff.StateBuff;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1404 Mydei (万敌): the automatic cast of [弑王成王] (Kingslayer Made King), and its cost.
 *
 * <p>Two readings, kept apart because the earlier attempt failed with two independent causes. One reads the COST, the other reads
 * the SWAP by the slot the skill reports. Note: And the cost reading wounds him first: at full HP "35% of CURRENT" and "35% of
 * MAXIMUM" are the same number, so a reading taken there would pass for either share -- the first version did exactly that.
 */
public class MydeiBloodfeudSkillsTest {
    private static final int MYDEI = 1404;
    private static final int MONSTER = 1002011;
    private static final String BLOODFEUD = "血仇";
    private static final String CHARGE = "天赋充能";

    /** "消耗等同于万敌当前生命值 35% 的生命值"-- the cost is paid, on the CURRENT value, at the start of his turn. */
    @Test
    public void theTurnStartPaysTheSkillsCost() {
        Character him = CharacterFactory.create(MYDEI, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(him),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        him.getBuffManager().addBuff(new StateBuff(BLOODFEUD, 9, true));
        battle.processRequests();

        battle.applyTrueDamage(battle.enemies.getFirst(), him, DamageElement.ICE, him.getCurrentHp() * 0.5);
        battle.processRequests();
        Assertions.assertTrue(him.getCurrentHp() < him.getMaxHp(), "precondition: he is wounded");

        double hpBefore = him.getCurrentHp();
        double maxHp = him.getMaxHp();
        double enemyBefore = battle.enemies.getFirst().getCurrentHp();
        spendTurnOf(battle, him);
        double hpAfter = him.getCurrentHp();
        double enemyAfter = battle.enemies.getFirst().getCurrentHp();
        System.out.println("[mydei-skills] cost: own hp " + hpBefore + " -> " + hpAfter + " (max " + maxHp
                + ") ; enemy " + enemyBefore + " -> " + enemyAfter);

        Assertions.assertEquals(hpBefore * 0.65, hpAfter, hpBefore * 1e-6,
                "「消耗等同于万敌当前生命值 35% 的生命值」 (consumes HP equal to 35% of Mydei's current HP)-- the CURRENT value");
        Assertions.assertTrue(hpAfter > hpBefore - 0.35 * maxHp,
                "and NOT a share of the maximum: that would leave " + (hpBefore - 0.35 * maxHp) + ", and he has " + hpAfter);
        Assertions.assertTrue(enemyAfter < enemyBefore, "and the attack lands");

        // Note: This reading covers the COST half. The other half -- swapping slot 9 in so the cast runs [弑王成王] -- does NOT
        // work from inside a rule yet, measured: the swap lands in the map while a `CAST_SKILL` later in the same rule still runs the
        // old row (6.585 against 383.93 for a direct cast of slot 9). Registered in EXPRESSION §3, so the note does not claim it.
    }

    /** Note: The other half on its own: `REPLACE_SKILL` really installs the row the cast then uses. */
    @Test
    public void theSwapInstallsTheEnhancedRow() {
        Character him = CharacterFactory.create(MYDEI, 80, false, null, null, 0);
        EffectSpec swap = new EffectSpec();
        TriggerSpecs.set(swap, "op", "REPLACE_SKILL");
        TriggerSpecs.set(swap, "skill", "SKILL");
        TriggerSpecs.set(swap, "skillId", 9);
        TriggerSpecs.set(swap, "permanent", Boolean.TRUE);
        TriggerSpecs.set(swap, "target", "self");
        him.setTriggerTable(new TriggerTable(MYDEI,
                List.of(TriggerSpecs.rule("BATTLE_START", List.of(), swap)), List.of()));
        Battle battle = new Battle(List.of(him),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();

        int slot = him.getSkills().get(SkillType.SKILL).getSkillSlot();
        System.out.println("[mydei-skills] SKILL slot after the swap = " + slot
                + " ; category = " + him.getSkills().get(SkillType.SKILL).getData().getCategory());
        Assertions.assertEquals(9, slot, "the row swapped in is **slot 9** (Kingslayer Be King (【弑王成王】) toughness 60/30 ✓), where it used to be slot 2");
    }

    /** Note: Half a turn is `beforeMove()` alone; a full one is both halves (see `ArlanEidolonFourTest`). */
    private static void spendTurnOf(Battle battle, Character unit) {
        Signal signal = battle.queue.snapshot().stream()
                .filter(candidate -> candidate.getCanHit() == unit).findFirst()
                .orElseThrow(() -> new AssertionError("precondition: the unit is in the queue"));
        battle.currentMove = signal;
        battle.beforeMove();
        battle.afterMove();
        battle.processRequests();
    }

    /**
     * And the swap must REACH the cast -- read as a SAME-LEVEL comparison, which is what the first attempt got wrong.
     *
     * <p>The earlier "proof" compared against a scene whose trigger table had been REPLACED, so `level_convention` never ran there
     * and the two numbers came from different levels. This one installs the same row by hand AFTER the battle has started (so the
     * levels convention has already run), casts it directly, and requires the content's commanded cast to deal the same.
     */
    @Test
    public void theSwapReachesTheCast() {
        Character him = CharacterFactory.create(MYDEI, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(him),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        him.getBuffManager().addBuff(new StateBuff(BLOODFEUD, 9, true));
        battle.processRequests();
        double before = battle.enemies.getFirst().getCurrentHp();
        spendTurnOf(battle, him);
        double commanded = before - battle.enemies.getFirst().getCurrentHp();

        Character byHand = CharacterFactory.create(MYDEI, 80, false, null, null, 0);
        Battle other = new Battle(List.of(byHand),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        other.startBattle();
        other.processRequests();
        // Note: AFTER startBattle, so the slot carries the level the convention gives it -- the trap the earlier attempt fell into.
        byHand.getSkills().put(SkillType.SKILL, new com.laosun.aluminium.models.skill.DefaultSkill(
                MYDEI, 9, byHand.getSkills().get(SkillType.SKILL).getLevel()));
        double otherBefore = other.enemies.getFirst().getCurrentHp();
        com.laosun.aluminium.models.skill.SkillExecutor.execute(other,
                byHand.getSkills().get(SkillType.SKILL), byHand, List.of(other.enemies.getFirst()));
        other.processRequests();
        double manual = otherBefore - other.enemies.getFirst().getCurrentHp();
        System.out.println("[mydei-skills] commanded=" + commanded + " ; slot-9 by hand=" + manual);

        Assertions.assertEquals(manual, commanded, manual * 1e-9,
                "「自动施放【弑王成王】」 (automatically casts Kingslayer Be King)-- the commanded cast runs the row the swap installed, not the slot's original one");
    }

    /** The gate: a charge that arrives WHILE [血仇] is already on must not be drained again. */
    @Test
    public void aChargeArrivingInBloodfeudIsNotDrainedAgain() {
        Character him = CharacterFactory.create(MYDEI, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(him),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        him.getBuffManager().addBuff(new StateBuff(BLOODFEUD, 9, true));
        battle.processRequests();

        him.getResources().gain(CHARGE, 100);
        battle.noteChangedResource(CHARGE);
        battle.fireTriggers(TriggerEvent.RESOURCE_CHANGED, him, battle.enemies.getFirst(), 0, 100);
        battle.processRequests();
        int charge = him.getResources().value(CHARGE);
        System.out.println("[mydei-skills] charge after entering bloodfeud with 100 = " + charge);

        Assertions.assertEquals(100, charge,
                "「充能达到 100 时消耗 100 点充能进入【血仇】状态」 (when Charge reaches 100, consumes 100 points of Charge to enter the Vendetta state)-- already in it, so nothing is drained again; "
                        + "without this the charge can never reach 150 and the next sentence is unreachable");
    }

    /** "充能达到 150 点时，立即获得 1 个额外回合并自动施放[弑神登神]". */
    @Test
    public void theHundredAndFiftyChargeCastsGodslayer() {
        Character him = CharacterFactory.create(MYDEI, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(him),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        him.getBuffManager().addBuff(new StateBuff(BLOODFEUD, 9, true));
        battle.processRequests();

        him.getResources().gain(CHARGE, 150);
        battle.noteChangedResource(CHARGE);
        double enemyBefore = battle.enemies.getFirst().getCurrentHp();
        battle.fireTriggers(TriggerEvent.RESOURCE_CHANGED, him, battle.enemies.getFirst(), 0, 150);
        battle.processRequests();
        int charge = him.getResources().value(CHARGE);
        double enemyAfter = battle.enemies.getFirst().getCurrentHp();
        System.out.println("[mydei-skills] 150: charge " + charge + " ; enemy " + enemyBefore + " -> " + enemyAfter
                + " ; SKILL slot now=" + him.getSkills().get(SkillType.SKILL).getSkillSlot());

        Assertions.assertEquals(0, charge, "\"consumes 150 points of Charge\" (「消耗 150 点充能」)");
        Assertions.assertTrue(enemyAfter < enemyBefore, "【弑神登神】 (Godslayer Be God) was cast");
        Assertions.assertEquals(11, him.getSkills().get(SkillType.SKILL).getSkillSlot(),
                "the cast ran slot 11 (槽 11), which is the row the sentence names");
    }
}
