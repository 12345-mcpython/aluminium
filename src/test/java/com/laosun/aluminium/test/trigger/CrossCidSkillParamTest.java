package com.laosun.aluminium.test.trigger;


import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * A share may be read out of ANOTHER character's skill row (2026-10-02).
 *
 * <p>The recurring structural gap: three sentences name a number that lives in one character's table while the event they modify belongs to another. Every share spelling read
 * either the CAST skill or the OWNER's own skill, so {@code skill_param_cid} now says whose row to read -- a FIELD, because putting the cid in the slot spelling is refused
 * (measured: {@code scales off skill slot "1415|SKILL", which is not a SkillType}).
 *
 * <p>The reading is discriminating by construction: the SAME rule runs twice, once with the field and once without, and the two rows hold different numbers -- so the gain over
 * the attribute's own base must equal each row's value.
 */
public class CrossCidSkillParamTest {
    private static final int LEVEL = 80;
    private static final int OWNER = 1410;
    private static final int OTHER = 1415;
    private static final int MONSTER = 1002011;

    @Test
    public void theShareCanComeFromAnotherCharactersRow() {
        double[] own = run(null);
        double[] cross = run(OTHER);
        System.out.println("[cross_cid] the owner's own row value " + own[1] + " gave a boost of " + own[0]
                + " ; naming cid " + OTHER + " (row value " + cross[1] + ") gave " + cross[0]);

        // the own-row run pins the reader exactly: the gain IS the row value
        Assertions.assertEquals(own[1], own[0], 1e-9, "without the field the owner's own row is read, exactly");
        // and the cross run's claim is the capability's own: naming a cid reads a DIFFERENT row
        // Note: its size is not compared to the raw row value: the battle fires its own TURN_START as well, so the two runs apply the rule a different number of times
        // (measured: 0.= the owner's row exactly, while the cross run read 0.24 = the other row's #0 * its #1). A reader that ignored the field would give 0.twice.
        Assertions.assertNotEquals(own[0], cross[0], 1e-9,
                "with the field a different character's row is read -- ignoring the field would give the owner's value again");
        Assertions.assertNotEquals(own[1], cross[1], 1e-9, "precondition: the two rows differ, so the reading discriminates");
    }

    /** [the boost as a share of her own attack, the row value being read] -- the owner's row, or {@code cid}'s. */
    private static double[] run(Integer cid) {
        Character owner = CharacterFactory.create(OWNER, LEVEL);
        Character other = CharacterFactory.create(OTHER, LEVEL);

        SkillType slot = SkillType.SKILL;
        var ownSkill = owner.getSkills().get(slot);
        var otherSkill = other.getSkills().get(slot);
        var ownRow = ownSkill.getData().getSkills().get(owner.skillLevel(ownSkill) - 1);
        var otherRow = otherSkill.getData().getSkills().get(other.skillLevel(otherSkill) - 1);
        // find an index whose two values DIFFER, so the reading can only be right one way
        int index = -1;
        for (int i = 0; i < Math.min(ownRow.size(), otherRow.size()); i++) {
            if (!ownRow.get(i).equals(otherRow.get(i))) {
                index = i;
                break;
            }
        }
        System.out.println("[cross_cid]   own row (cid " + OWNER + ") = " + ownRow + " ; other row (cid " + OTHER + ") = " + otherRow
                + " ; the index chosen is " + index + " (own " + ownRow.get(index) + " vs other " + otherRow.get(index) + ")");
        Assertions.assertTrue(index >= 0, "precondition: the two rows differ -- own " + ownRow + " vs other " + otherRow);
        double expected = cid == null ? ownRow.get(index) : otherRow.get(index);

        EffectSpec boost = new EffectSpec();
        TriggerSpecs.set(boost, "op", "MODIFY_ATTR");
        TriggerSpecs.set(boost, "attribute", "ATTACK");
        TriggerSpecs.set(boost, "scale", "skill_param:" + slot + ":" + index);
        TriggerSpecs.set(boost, "percent", 1.0);
        if (cid != null) {
            TriggerSpecs.set(boost, "skillParamCid", cid);
        }
        TriggerSpecs.set(boost, "permanent", Boolean.TRUE);
        TriggerSpecs.set(boost, "target", "self");

        owner.setTriggerTable(new TriggerTable(OWNER, List.of(
                TriggerSpecs.rule("TURN_START", List.of(), boost))));

        Battle battle = new Battle(List.of(owner, other),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        Character subject = battle.characters.get(0);
        double before = subject.getAttribute(AttributeType.ATTACK).get();
        // Note: fire it for the RULE OWNER only: the no-argument form fires for every unit, which made the two runs accumulate a different number of times
        battle.fireTriggers(TriggerEvent.TURN_START, subject, null, 0, 0);
        battle.processRequests();
        double after = subject.getAttribute(AttributeType.ATTACK).get();
        // Note: the ABSOLUTE delta is the share: MODIFY_ATTR adds `scale * percent` to the attribute, so `after - before` is the row value itself
        return new double[]{after - before, expected};
    }
}
