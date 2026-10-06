package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.models.skill.SkillExecutor;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1415's memosprite skill 23 "献予'律法'之诗": "持有[军功]的角色暴击伤害提高 #1%".
 *
 * <p>Nothing is replaced here. The mark [军功] is granted by 1412's OWN kit, so the judge casts her skill and lets the game do it -- an earlier version
 * replaced her whole trigger table with a hand-built mark rule, which silently deleted the very rule under test (the table-replacement trap, again).
 *
 * <p>The reading is TWO-SIDED: the ally carrying the mark gains the crit damage, the ally without it gains nothing.
 */
public class LawOdeCritDamageTest {
    private static final int LEVEL = 80;
    private static final int CYRENE = 1415;
    private static final int CERYDRA = 1412;
    private static final int PLAIN = 1002;
    private static final int MONSTER = 1002011;
    private static final String MARK = "军功";

    @Test
    public void onlyTheMarkedAllyGainsTheCritDamage() {
        Character cyrene = CharacterFactory.create(CYRENE, LEVEL);
        Character cerydra = CharacterFactory.create(CERYDRA, LEVEL);
        Character plain = CharacterFactory.create(PLAIN, LEVEL);
        Battle battle = new Battle(List.of(cyrene, cerydra, plain),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        cyrene = battle.characters.get(0);
        cerydra = battle.characters.get(1);
        plain = battle.characters.get(2);

        // HER OWN skill is what grants [军功] -- cast it at herself, and no table is ever replaced
        var herSkill = cerydra.getSkills().get(SkillType.SKILL);
        Assertions.assertNotNull(herSkill, "precondition: 1412 has a SKILL");
        SkillExecutor.execute(battle, herSkill, cerydra, List.of(cerydra));
        battle.processRequests();
        Assertions.assertTrue(cerydra.getBuffManager().hasState(MARK),
                "precondition: her own kit put the mark on her");
        Assertions.assertFalse(plain.getBuffManager().hasState(MARK), "precondition: the other one is unmarked");

        double markedBefore = cerydra.getAttribute(AttributeType.CRIT_ATTACK).get();
        double plainBefore = plain.getAttribute(AttributeType.CRIT_ATTACK).get();

        var demiurge = battle.summonServant(cyrene);
        var ode = demiurge.skillAt(23);
        Assertions.assertNotNull(ode, "precondition: the memosprite carries slot 23");
        var row = ode.getData().getSkills().get(demiurge.skillLevel(ode) - 1);
        SkillExecutor.execute(battle, ode, demiurge, List.of(cerydra));
        battle.processRequests();

        double markedGain = cerydra.getAttribute(AttributeType.CRIT_ATTACK).get() - markedBefore;
        double plainGain = plain.getAttribute(AttributeType.CRIT_ATTACK).get() - plainBefore;
        System.out.println("[law] marked CRIT_ATTACK gain = " + markedGain + " (=" + row.get(0) + ")"
                + " ; unmarked gain = " + plainGain);

        Assertions.assertEquals(row.get(0), markedGain, Math.abs(row.get(0)) * 1e-6,
                "「持有【军功】的角色暴击伤害提高 #1%」 (a character holding Military Merit gets #1% more CRIT DMG)-- and #1 runs with level");
        Assertions.assertEquals(0.0, plainGain, 1e-9,
                "and an ally WITHOUT it gains nothing -- 「持有【军功】的角色」 (a character holding Military Merit) names a subset");
    }
}
