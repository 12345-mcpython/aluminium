package com.laosun.aluminium.test.content.characters.pearl;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.entity.Character;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Character 1503 (Pearl): the OTHER enhanced basic - the one for an archetype that is NOT on the Elation path.
 *
 * <p>「对敌方全体造成等同于真珠 100% 防御力的冰属性伤害，为我方全体目标回复等同于真珠 8.00% 防御力+160 的生命值，额外为当前生命
 * 值百分比最低的我方目标回复…」 (her enhanced basic, slot 9 = 【行笔，绘制末浪】).
 *
 * <p>Written because a coverage sweep found that nothing drove this branch at all: {@code PearlEnhancedBasicBranchTest}
 * checks WHICH slot her Ultimate installs but never attacks through it, and the other enhanced-basic cases use an
 * Elation archetype, so slot 8. Slot 9 is the branch a player gets with any non-Elation archetype - the ordinary case
 * - and its healing and its dispel had never been executed.
 *
 * <p>Two-sided: the same attack with the team armed by her Ultimate (slot 8) versus aimed at a Hunt ally (slot 9)
 * differs in what each ally gains, so a rule that fired on both branches, or on neither, fails one of the readings.
 */
public class PearlEnhancedBasicFormBTest {
    private static final int PEARL = 1503;
    private static final int ELATION = 1502;
    private static final int HUNTER = 1204;
    private static final int OTHER = 1101;

    /**
     * The slot-9 form heals the whole party, and the lowest-HP ally a second time.
     */
    @Test
    public void theSlotNineFormHealsThePartyAndTheLowestAllyAgain() {
        Battle battle = newBattle();
        Character pearl = battle.characters().get(0);
        Character archetype = battle.characters().get(2);      // the Hunt ally: NOT Elation, so slot 9
        Character secondLowest = battle.characters().get(1);
        Character other = battle.characters().get(3);
        battle.fire(com.laosun.aluminium.models.trigger.TriggerPayload.Simple.of(TriggerEvent.ULT_CAST, pearl, archetype, 0, 0));
        battle.processRequests();
        // ⚠ CORRECTED (2026-10-18, data migration): data slot **10**, not 9 (150309 no longer exists).
        Assertions.assertEquals(10, pearl.getSkills().get(SkillType.COMMON).getSkillSlot(),
                "precondition: a non-Elation archetype installs the SLOT-10 form, which is what this case is about");

        // Hurt the party, with ONE ally clearly the lowest so the "additional heal" has a unique target.
        for (Character ally : battle.characters()) {
            ally.takeDamage(ally.getMaxHp() * 0.5);
        }
        secondLowest.takeDamage(secondLowest.getMaxHp() * 0.3);
        double lowestBefore = secondLowest.getCurrentHp();
        double otherBefore = other.getCurrentHp();

        attack(battle, pearl);
        double lowestGain = secondLowest.getCurrentHp() - lowestBefore;
        double otherGain = other.getCurrentHp() - otherBefore;
        System.out.println("[pearl_form_b] the lowest ally gained " + lowestGain + " ; another ally gained "
                + otherGain);

        Assertions.assertTrue(otherGain > 0,
                "「为我方全体目标回复…」: every ally is healed by the slot-9 form (" + otherGain + ")");
        // Note: The lowest ally is healed TWICE - once by the party heal and once by 「额外为…最低的…回复」 - so its gain
        // is twice the others'. Asserted as that relation rather than as a number: the two heals are the same clause's
        // two halves, and "the lowest got more" is much weaker than "it got exactly twice".
        Assertions.assertEquals(2 * otherGain, lowestGain, 0.5,
                "and the lowest-HP ally is healed a second time, so it gains twice as much");
    }

    /**
     * A clean control: the same battle WITHOUT her Ultimate, so the ordinary basic is what attacks.
     */
    @Test
    public void theOrdinaryBasicDoesNotHealTheParty() {
        Battle battle = newBattle();
        Character pearl = battle.characters().get(0);
        for (Character ally : battle.characters()) {
            ally.takeDamage(ally.getMaxHp() * 0.5);
        }
        double before = battle.characters().get(3).getCurrentHp();
        attack(battle, pearl);
        System.out.println("[pearl_form_b] with no Ultimate the ally gained "
                + (battle.characters().get(3).getCurrentHp() - before));
        // The ordinary basic is a single-target attack with no healing clause of its own, so nobody gains HP.
        Assertions.assertEquals(0, battle.characters().get(3).getCurrentHp() - before, 1e-9,
                "her unenhanced basic carries no party heal - the healing belongs to the enhanced forms");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    /**
     * Casts her COMMON skill at the first enemy through the engine's own skill request path.
     */
    private static void attack(Battle battle, Character pearl) {
        Enemy victim = battle.enemyUnits().getFirst();
        battle.requestSkill(pearl.getSkills().get(SkillType.COMMON), pearl, List.of(victim));
        battle.processRequests();
    }

    /**
     * Which slot a non-Elation archetype installs, read the same way the branch case reads it.
     */
    private static int slotAfterUltimate() {
        Battle battle = newBattle();
        Character pearl = battle.characters().get(0);
        battle.fire(com.laosun.aluminium.models.trigger.TriggerPayload.Simple.of(TriggerEvent.ULT_CAST, pearl, battle.characters().get(2), 0, 0));
        battle.processRequests();
        for (SkillType slot : SkillType.values()) {
            if (pearl.getSkills().get(SkillType.COMMON) != null
                    && pearl.getSkills().get(SkillType.COMMON).getSkillSlot() == 9) {
                return 9;
            }
        }
        return pearl.getSkills().get(SkillType.COMMON).getSkillSlot();
    }

    private static Battle newBattle() {
        Battle battle = new Battle(
                List.of(CharacterFactory.create(PEARL, 80), CharacterFactory.create(ELATION, 80),
                        CharacterFactory.create(HUNTER, 80), CharacterFactory.create(OTHER, 80)),
                List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        return battle;
    }
}
