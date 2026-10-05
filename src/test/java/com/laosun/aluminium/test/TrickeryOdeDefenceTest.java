package com.laosun.aluminium.test;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Random;

/**
 * Slot 20's last two clauses (2026-10-02): "[老主顾]的防御力降低 #2(10)%，[老主顾]以外的敌方目标的防御力降低 #3(6)%".
 *
 * Two-sided on the SAME enemy: the mark takes 10% and the unmarked one 6%, so a rule that ignored `target_when` would be caught either way.
 */
public class TrickeryOdeDefenceTest {
    private static final int LEVEL = 80;
    private static final int CYRENE = 1415;
    private static final int CIPHER = 1406;
    private static final int MONSTER = 1002011;
    private static final int ODE_SLOT = 20;
    private static final String PATRON = "老主顾";

    @Test
    public void theMarkLosesMoreThanTheRest() {
        double[] r = run();
        System.out.println("[trick_def] the marked one's defence fell " + r[0] + " ; the unmarked one's " + r[1]);
        Assertions.assertEquals(0.10, r[0], 1e-6, "【老主顾】 takes #2");
        Assertions.assertEquals(0.06, r[1], 1e-6, "everyone else takes #3");
    }

    /** [what the marked enemy lost, what the unmarked enemy lost] */
    private static double[] run() {
        Character cyrene = CharacterFactory.create(CYRENE, LEVEL);
        Character cipher = CharacterFactory.create(CIPHER, LEVEL);
        Enemy marked = EnemyFactory.create(MONSTER, 90, 1);
        Enemy plain = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(cyrene, cipher), List.of(marked, plain), new Random(0));
        battle.startBattle();
        battle.processRequests();
        // The mark is applied by Cipher (赛飞儿)'s OWN ultimate rule (`talent_patron_on_ult`, ULT_CAST -> APPLY_BUFF 老主顾), so the judge does not have to construct a buff by hand.
        cipher = battle.characters.get(1);
        com.laosun.aluminium.models.skill.Skill ult =
                cipher.getSkills().get(com.laosun.aluminium.enums.SkillType.ULTRA);
        Assertions.assertNotNull(ult, "precondition: her ultimate");
        com.laosun.aluminium.models.skill.SkillExecutor.execute(battle, ult, cipher, List.of(marked));
        battle.processRequests();
        Assertions.assertTrue(marked.getBuffManager().hasState(PATRON), "precondition: the mark is on");
        double markedBefore = marked.getAttribute(AttributeType.DEFENCE).get();
        double plainBefore = plain.getAttribute(AttributeType.DEFENCE).get();
        var sprite = battle.summonServant(battle.characters.get(0));
        battle.processRequests();
        var ode = sprite.skillAt(ODE_SLOT);
        Assertions.assertNotNull(ode, "precondition: slot 20");
        com.laosun.aluminium.models.skill.SkillExecutor.execute(battle, ode, sprite,
                List.of(battle.characters.get(1)));
        battle.processRequests();
        double markedNow = marked.getAttribute(AttributeType.DEFENCE).get();
        double plainNow = plain.getAttribute(AttributeType.DEFENCE).get();
        return new double[]{(markedBefore - markedNow) / markedBefore, (plainBefore - plainNow) / plainBefore};
    }
}
