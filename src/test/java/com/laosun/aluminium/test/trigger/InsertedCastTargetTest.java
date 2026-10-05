package com.laosun.aluminium.test.trigger;


import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * The commanded cast's own target is readable when it ends (2026-10-02).
 *
 * <p>1415's memosprite skill says "对刻律德菈施放后…奇袭结束后，使刻律德菈获得 1 点充能" -- so the moment must be able to say WHO the
 * commanded skill was aimed at. It used to pass the caster in the target slot.
 */
public class InsertedCastTargetTest {
    private static final int OWNER = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final String MARK = "probeMark";

    /** The mark lands on the unit the commanded cast was aimed at. */
    @Test
    public void theEndMomentKnowsWhatTheCopyWasAimedAt() {
        Character owner = CharacterFactory.create(OWNER, LEVEL, false, null, null, 0);
        owner.setTriggerTable(scene());
        Enemy victim = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(owner), List.of(victim), new Random(0));
        battle.startBattle();
        battle.processRequests();

        battle.castImmediate(owner.getSkills().get(SkillType.SKILL), owner, List.of(victim));
        battle.processRequests();

        int onVictim = victim.getBuffManager().stacksOf(MARK);
        int onOwner = owner.getBuffManager().stacksOf(MARK);
        System.out.println("[inserted-target] onVictim=" + onVictim + " onOwner=" + onOwner);
        Assertions.assertEquals(1, onVictim, "the event's target is the unit the copy was aimed at");
        Assertions.assertEquals(0, onOwner, "and not the caster");
    }

    // ==================================================================

    private static TriggerTable scene() {
        EffectSpec command = effect("CAST_SKILL", "skill", "SKILL");
        EffectSpec mark = effect("ADD_STACK", "buff", MARK, "amount", 1.0, "maxStacks", 9,
                "permanent", Boolean.TRUE, "target", "target");
        return new TriggerTable(OWNER,
                List.of(TriggerSpecs.rule("CAST_SETUP", List.of("actor == self"), command),
                        TriggerSpecs.rule("INSERTED_CAST_END", List.of(), mark)));
    }

    private static EffectSpec effect(String op, Object... pairs) {
        EffectSpec spec = new EffectSpec();
        TriggerSpecs.set(spec, "op", op);
        for (int i = 0; i < pairs.length; i += 2) {
            TriggerSpecs.set(spec, (String) pairs[i], pairs[i + 1]);
        }
        return spec;
    }
}
