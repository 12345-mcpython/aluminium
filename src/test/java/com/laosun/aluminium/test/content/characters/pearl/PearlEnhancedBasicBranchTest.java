package com.laosun.aluminium.test.content.characters.pearl;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.entity.Character;
import com.laosun.aluminium.models.buff.SkillSwapBuff;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Character 1503 (Pearl): her Ultimate's enhanced basic branches on the archetype's Path.
 *
 * <p>`SkillSwapBuff` documents its own accessors as test-readable -- "the slot this swap owns (readable by
 * tests and by future 'which skill is equipped' conditions)" -- and `BuffManager.findBuff(Class)` returns it.
 * So the branch is observable without touching any transient-state guesswork:
 * * the archetype is on the Elation Path -> COMMON is swapped for the skill in slot 8 ([行笔，幻造星月]);
 * * otherwise                            -> slot 9 ([行笔，绘制末浪]).
 *
 * <p>Two-sided by construction: the same call with two different targets must install two different skills.
 */
public class PearlEnhancedBasicBranchTest {
    private static final int PEARL = 1503;
    private static final int ELATION = 1502;
    private static final int HUNTER = 1204;

    @Test
    public void theBranchFollowsTheArchetypesPath() {
        int elationSlot = swappedSlot(ELATION);
        int hunterSlot = swappedSlot(HUNTER);
        System.out.println("[pearl_branch] COMMON is swapped for slot " + elationSlot
                + " when the archetype is Elation, and for slot " + hunterSlot + " otherwise");

        Assertions.assertEquals(8, elationSlot,
                "an Elation archetype upgrades the basic to the slot-8 form (with the extra Elation damage)");
        // ⚠ CORRECTED (2026-10-18, data migration): the non-Elation form is data slot **10** now - the current
        // tbgd has no `150309`, and `150310` is the same 「行笔，绘制末浪」.
        Assertions.assertEquals(10, hunterSlot, "any other Path upgrades it to the slot-10 form");
    }

    /**
     * Fires her Ultimate at the given ally and returns the skill slot it swapped COMMON for.
     */
    private static int swappedSlot(int archetype) {
        Battle battle = new Battle(List.of(CharacterFactory.create(PEARL, 80),
                CharacterFactory.create(ELATION, 80), CharacterFactory.create(HUNTER, 80)),
                List.of(EnemyFactory.create(1002011, 100, 1)), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        Character pearl = battle.characters().get(0);
        Character target = battle.characters().stream().filter(c -> c.getCid() == archetype).findFirst()
                .orElseThrow();

        battle.fire(com.laosun.aluminium.models.trigger.TriggerPayload.Simple.of(TriggerEvent.ULT_CAST, pearl, target, 0, 0));

        SkillSwapBuff swap = pearl.getBuffManager().findBuff(SkillSwapBuff.class);
        Assertions.assertNotNull(swap, "the Ultimate installs a skill swap on Pearl");
        Assertions.assertEquals(SkillType.COMMON, swap.getSlot(), "and it owns the basic-attack slot");
        return swap.getReplacement().getSkillSlot();
    }
}
