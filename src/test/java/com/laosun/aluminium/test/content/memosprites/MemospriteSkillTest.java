package com.laosun.aluminium.test.content.memosprites;

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
 * A memosprite's own skills are real skills.
 *
 * <p>The engine addresses a skill by {@code (cid, slot)} -- {@code SkillEffects.forSkill} keys on {@code skill.getCid()} and
 * {@code skill.getSkillSlot()}, and {@code DefaultSkill(cid, slot, level)} is the one implementation. A memosprite's cid is its
 * {@code ServantID}, so its memosprite skills (忆灵技能) are ordinary skills with no special case.
 *
 * <p>THE SLOT IS THE DATA'S, NOT THE CHECKLIST'S: the item calls "Ode to Strife" (献予'纷争'之诗) memosprite skill <b>8</b>, but the table keys it
 * {@code SkillID 1141516} / {@code SkillTriggerKey SkillCY04} -- data slot <b>16</b> -- which is the row carrying
 * {@code ExtraEffectIDList [10000001, 10000011]}, exactly the two effect ids the item named. An earlier version used 8 on both sides,
 * so it passed while pointing at a slot that does not exist.
 */
public class MemospriteSkillTest {
    private static final int CYRENE = 1415;
    private static final int SERVANT_ID = 11415;
    private static final int ODE_TO_STRIFE = 16;
    private static final int MONSTER = 1002011;

    /** Demiurge (德谬歌) carries "Ode to Strife" (献予'纷争'之诗) as a Skill with the SERVANT's cid, and the engine can deliver it. */
    @Test
    public void theMemospriteCarriesItsOwnSkill() {
        Summon demiurge = servantOf(CYRENE);
        Skill skill = demiurge.skillAt(ODE_TO_STRIFE);
        Assertions.assertNotNull(skill, "the memosprite carries the skill at its DATA slot");
        boolean deliverable = SkillExecutor.canDeliver(skill);
        System.out.println("[memosprite] skill cid = " + skill.getCid() + " slot = " + skill.getSkillSlot()
                + " ; canDeliver = " + deliverable);

        Assertions.assertEquals(SERVANT_ID, skill.getCid(),
                "「ServantID 11415」-- a memosprite's skill is keyed by the SERVANT's id, which is what SkillEffects looks up");
        Assertions.assertEquals(ODE_TO_STRIFE, skill.getSkillSlot(), "and by the DATA slot the table states (16, not the checklist's 8)");
        Assertions.assertTrue(deliverable,
                "「献予「纷争」之诗」is a support skill whose work is on the rule side, so the Rules entry is what makes it deliverable");
    }

    /** Every memosprite that the servant table knows carries all of its skills, keyed by the servant's id. */
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
            // Note: `Battle.summonServant` is the seam that hands the panel its resource reader (140's derives from New Bud (新蕊))
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

    /**
     * Every memosprite skill whose imported row says it DEALS DAMAGE is deliverable with no delivery entry at all.
     *
     * <p>That is what the row import bought: before it `SkillData` handed back a placeholder for a ServantID, so `isDamaging()` was false
     * for all 48 skills and each needed an entry. Now the damaging ones are named by their own `skill_effect`.
     */
    @Test
    public void damagingMemospriteSkillsNeedNoDeliveryEntry() {
        StringBuilder report = new StringBuilder("[damaging] ");
        for (int cid : new int[]{1402, 1407, 1409, 1413, 1415, 1512, 8007}) {
            MemospriteSpec spec = Memosprites.of(cid);
            Character master = CharacterFactory.create(cid, 80, false, null, null, 0);
            Battle battle = new Battle(List.of(master),
                    List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
            battle.startBattle();
            battle.processRequests();
            Summon servant = battle.summonServant(master);
            int damaging = 0;
            for (Map.Entry<Integer, Skill> e : servant.skillsByDataSlot().entrySet()) {
                if (e.getValue().getData() != null && e.getValue().getData().getEffect().isDamaging()) {
                    damaging++;
                    Assertions.assertTrue(SkillExecutor.canDeliver(e.getValue()),
                            cid + " slot " + e.getKey() + ": a damaging skill is delivered as a swing, with no entry");
                }
            }
            report.append(cid).append("=").append(damaging).append(" ");
        }
        System.out.println(report.toString().trim());
    }
    /**
     * The imported elements are the ones the SERVANT'S ABILITY states -- not a default.
     *
     * <p>Where they come from, measured: not from `AvatarServantSkillConfig` (which carries the delivery shape and a toughness type but
     * no damage element), but from `Config/ConfigAbility/Servant/Servant_*_Ability.json`, inside the ability the skill's own
     * `SkillTriggerKey` names. Four of the thirteen damaging skills resolved unambiguously; the rest are recorded, not guessed.
     */
    @Test
    public void theImportedElementsAreTheOnesTheAbilitiesState() {
        String[][] want = {
                {"1402", "1", "THUNDER"},
                {"1407", "1", "QUANTUM"},
                {"1512", "1", "WIND"},
                {"8007", "1", "ICE"},
                // the second pass: their ability files are named after the SUMMONER, so the pairing runs through the master's English name
                {"1413", "1", "ICE"},
                {"1413", "7", "ICE"},
                {"1415", "1", "ICE"},
                // the second pass, done again from each skill OWN ability subtree instead of a byte window
                {"1407", "10", "QUANTUM"},
                {"1407", "11", "QUANTUM"},
                {"1407", "12", "QUANTUM"},
                {"1409", "1", "WIND"},
        };
        StringBuilder report = new StringBuilder("[elements] ");
        for (String[] w : want) {
            int cid = Integer.parseInt(w[0]);
            int slot = Integer.parseInt(w[1]);
            Character master = CharacterFactory.create(cid, 80, false, null, null, 0);
            Battle battle = new Battle(List.of(master),
                    List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
            battle.startBattle();
            Summon servant = battle.summonServant(master);
            Skill skill = servant.skillAt(slot);
            Assertions.assertNotNull(skill, cid + " carries skill " + slot);
            Assertions.assertNotNull(skill.getData().getElement(), cid + " slot " + slot + " has an element at all");
            String got = skill.getData().getElement().name();
            report.append(cid).append("/").append(slot).append("=").append(got).append(" ");
            Assertions.assertEquals(w[2], got, cid + " slot " + slot + ": the element the servant's ability states");
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
