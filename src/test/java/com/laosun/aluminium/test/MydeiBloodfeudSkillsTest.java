package com.laosun.aluminium.test;

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
 * 1404 万敌：【弑王成王】的自动施放，以及它的代价 (2026-10-02).
 *
 * <p>Two readings, kept apart because the earlier attempt failed with two independent causes. One reads the COST, the other reads
 * the SWAP by the slot the skill reports. ⚠ And the cost reading wounds him first: at full HP "35% of CURRENT" and "35% of
 * MAXIMUM" are the same number, so a reading taken there would pass for either share -- the first version did exactly that.
 */
public class MydeiBloodfeudSkillsTest {
    private static final int MYDEI = 1404;
    private static final int MONSTER = 1002011;
    private static final String BLOODFEUD = "\u8840\u4ec7";

    /** 「消耗等同于万敌当前生命值 35% 的生命值」-- the cost is paid, on the CURRENT value, at the start of his turn. */
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
                "「消耗等同于万敌当前生命值 35% 的生命值」-- the CURRENT value");
        Assertions.assertTrue(hpAfter > hpBefore - 0.35 * maxHp,
                "and NOT a share of the maximum: that would leave " + (hpBefore - 0.35 * maxHp) + ", and he has " + hpAfter);
        Assertions.assertTrue(enemyAfter < enemyBefore, "and the attack lands");

        // ⚠ This reading covers the COST half. The other half -- swapping slot 9 in so the cast runs 【弑王成王】 -- does NOT
        // work from inside a rule yet, measured: the swap lands in the map while a `CAST_SKILL` later in the same rule still runs the
        // old row (767.585 against 383.793 for a direct cast of slot 9). Registered in EXPRESSION §3, so the note does not claim it.
    }

    /** ⚠ The other half on its own: `REPLACE_SKILL` really installs the row the cast then uses. */
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
        Assertions.assertEquals(9, slot, "换入的是**槽 9** 的行（【弑王成王】破韧 60/30 ✓），而原来是槽 2");
    }

    /** ⚠ Half a turn is `beforeMove()` alone; a full one is both halves (see `ArlanEidolonFourTest`). */
    private static void spendTurnOf(Battle battle, Character unit) {
        Signal signal = battle.queue.snapshot().stream()
                .filter(candidate -> candidate.getCanHit() == unit).findFirst()
                .orElseThrow(() -> new AssertionError("precondition: the unit is in the queue"));
        battle.currentMove = signal;
        battle.beforeMove();
        battle.afterMove();
        battle.processRequests();
    }
}
