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
import java.util.Random;

/**
 * A memosprite's own skills are real skills (2026-10-02).
 *
 * <p>The engine addresses a skill by {@code (cid, slot)} -- {@code SkillEffects.forSkill} keys on {@code skill.getCid()} and
 * {@code skill.getSkillSlot()}, and {@code DefaultSkill(cid, slot, level)} is the one implementation. A memosprite's cid is its
 * {@code ServantID} (「ServantID 11415」 for 德谬歌), so its 忆灵技能 become ordinary skills with no special case: the table that says
 * how to deliver a non-damaging skill, and every op that takes a skill, work on them unchanged.
 *
 * <p>Skill 8 is 「献予「纷争」之诗 / Ode to Strife」: 辅助, and its work is done by the RULE table (dispel, command a cast, advance), so
 * its entry is the {@code Rules} shape -- the third kind, whose reader today is 1412's 奇袭. That is what makes {@code canDeliver}
 * answer yes without any parameter row: a skill whose work is elsewhere needs none.
 */
public class MemospriteSkillTest {
    private static final int CYRENE = 1415;
    private static final int SERVANT_ID = 11415;
    private static final int SKILL_SLOT = 8;
    private static final int MONSTER = 1002011;

    /** 德谬歌 carries skill 8 as a Skill with the SERVANT's cid, and the engine can deliver it. */
    @Test
    public void theMemospriteCarriesItsOwnSkill() {
        Character cyrene = CharacterFactory.create(CYRENE, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(cyrene),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();

        MemospriteSpec spec = Memosprites.of(CYRENE);
        Assertions.assertNotNull(spec, "precondition: her memosprite has a spec file");
        Summon demiurge = SummonFactory.servant(cyrene, spec);

        Skill skill = demiurge.skillAt(SKILL_SLOT);
        Assertions.assertNotNull(skill, "the memosprite carries its own skill at that data slot");
        boolean deliverable = SkillExecutor.canDeliver(skill);
        System.out.println("[memosprite] skill cid = " + skill.getCid() + " slot = " + skill.getSkillSlot()
                + " ; canDeliver = " + deliverable);

        Assertions.assertEquals(SERVANT_ID, skill.getCid(),
                "\u300cServantID 11415\u300d-- a memosprite's skill is keyed by the SERVANT's id, which is what SkillEffects looks up");
        Assertions.assertEquals(SKILL_SLOT, skill.getSkillSlot(), "and by the data slot the row states");
        Assertions.assertTrue(deliverable,
                "\u300c\u732e\u4e88\u300c\u7eb7\u4e89\u300d\u4e4b\u8bd7\u300dis a \u8f85\u52a9 skill whose work is on the rule side, so the Rules entry is what makes it deliverable");
    }
}
