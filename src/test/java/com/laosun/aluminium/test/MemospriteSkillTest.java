package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.MemospriteSpec;
import com.laosun.aluminium.data.Memosprites;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Summon;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.models.enemy.SummonFactory;
import com.laosun.aluminium.models.skill.Skill;
import com.laosun.aluminium.models.skill.SkillExecutor;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * A memosprite's own skills are real skills (2026-10-02).
 *
 * <p>The engine addresses a skill by {@code (cid, slot)} -- {@code SkillEffects.forSkill} keys on {@code skill.getCid()} and
 * {@code skill.getSkillSlot()}, and {@code DefaultSkill(cid, slot, level)} is the one implementation. A memosprite's cid is its
 * {@code ServantID}, so its 忆灵技能 are ordinary skills with no special case.
 *
 * <p>⭐ THE SLOT IS THE DATA'S, NOT THE CHECKLIST'S: the item calls 「献予「纷争」之诗」 忆灵技能 <b>8</b>, but the table keys it
 * {@code SkillID 1141516} / {@code SkillTriggerKey SkillCY04} -- data slot <b>16</b> -- which is the row carrying
 * {@code ExtraEffectIDList [10000001, 10000011]}, exactly the two effect ids the item named. An earlier version used 8 on both sides,
 * so it passed while pointing at a slot that does not exist.
 */
public class MemospriteSkillTest {
    private static final int CYRENE = 1415;
    private static final int SERVANT_ID = 11415;
    private static final int ODE_TO_STRIFE = 16;
    private static final int MONSTER = 1002011;

    /** 德谬歌 carries 「献予「纷争」之诗」 as a Skill with the SERVANT's cid, and the engine can deliver it. */
    @Test
    public void theMemospriteCarriesItsOwnSkill() {
        Summon demiurge = servantOf(CYRENE);
        Skill skill = demiurge.skillAt(ODE_TO_STRIFE);
        Assertions.assertNotNull(skill, "the memosprite carries the skill at its DATA slot");
        boolean deliverable = SkillExecutor.canDeliver(skill);
        System.out.println("[memosprite] skill cid = " + skill.getCid() + " slot = " + skill.getSkillSlot()
                + " ; canDeliver = " + deliverable);

        Assertions.assertEquals(SERVANT_ID, skill.getCid(),
                "\u300cServantID 11415\u300d-- a memosprite's skill is keyed by the SERVANT's id, which is what SkillEffects looks up");
        Assertions.assertEquals(ODE_TO_STRIFE, skill.getSkillSlot(), "and by the DATA slot the table states (16, not the checklist's 8)");
        Assertions.assertTrue(deliverable,
                "\u300c\u732e\u4e88\u300c\u7eb7\u4e89\u300d\u4e4b\u8bd7\u300dis a \u8f85\u52a9 skill whose work is on the rule side, so the Rules entry is what makes it deliverable");
    }

    /** ⭐ Every memosprite that the servant table knows carries all of its skills, keyed by the servant's id. */
    @Test
    public void everyMemospriteCarriesItsImportedSkills() {
        StringBuilder report = new StringBuilder("[memosprites] ");
        for (int cid : new int[]{1402, 1407, 1409, 1413, 1415, 1512, 8007}) {
            MemospriteSpec spec = Memosprites.of(cid);
            Assertions.assertNotNull(spec, "precondition: " + cid + " has a memosprite file");
            Character master = CharacterFactory.create(cid, 80, false, null, null, 0);
            Battle battle = new Battle(List.of(master),
                    List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
            battle.startBattle();
            battle.processRequests();
            // \u26a0 `Battle.summonServant` is the seam that hands the panel its resource reader (1407's derives from \u65b0\u854a)
            Summon servant = battle.summonServant(master);
            Map<Integer, Skill> skills = servant.skillsByDataSlot();
            Assertions.assertFalse(skills.isEmpty(), cid + " carries its imported skills");
            Assertions.assertEquals(spec.skills().size(), skills.size(),
                    cid + ": every stated row became a skill");
            for (Skill skill : skills.values()) {
                Assertions.assertEquals(spec.servantId().intValue(), skill.getCid(),
                        cid + ": every skill is keyed by the SERVANT's id");
            }
            report.append(cid).append("=").append(skills.size()).append(" ");
        }
        System.out.println(report.toString().trim());
    }

    /** The memosprite at that cid, built through the seam a test uses. */
    private static Summon servantOf(int cid) {
        Character master = CharacterFactory.create(cid, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(master),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        MemospriteSpec spec = Memosprites.of(cid);
        Assertions.assertNotNull(spec, "precondition: " + cid + " has a memosprite file");
        return SummonFactory.servant(master, spec);
    }
}
