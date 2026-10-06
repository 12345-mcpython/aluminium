package com.laosun.aluminium.test.engine;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Random;

/** "展开战技的结界并使其没有持续时间". */
public class RippleEndlessWardTest {
    private static final String WARD = "结界";

    @Test
    public void theUltimateMakesTheWardEndless() {
        int viaSkill = lengthenable(false);
        int viaUltimate = lengthenable(true);
        System.out.println("[ward] buffs a lengthening can touch: from the skill alone = " + viaSkill
                + " ; through her ultimate = " + viaUltimate);
        Assertions.assertTrue(viaSkill >= 1, "the skill's ward is a timed buff, so it can be lengthened");
        Assertions.assertEquals(0, viaUltimate, "her ultimate makes it permanent, and permanents are skipped");
    }

    /** How many of her buffs a one-turn lengthening finds -- the ward counts only while it is timed. */
    private static int lengthenable(boolean thenTheUltimate) {
        Character cyrene = CharacterFactory.create(1415, 80);
        Battle battle = new Battle(List.of(cyrene), List.of(EnemyFactory.create(1002011, 100, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        cyrene = battle.characters.getFirst();
        com.laosun.aluminium.models.skill.SkillExecutor.execute(battle, cyrene.getSkills().get(SkillType.SKILL),
                cyrene, List.of(battle.enemies.getFirst()));
        battle.processRequests();
        cyrene = battle.characters.getFirst();
        Assertions.assertTrue(cyrene.getBuffManager().hasState(WARD), "precondition: the ward is up");
        if (thenTheUltimate) {
            com.laosun.aluminium.models.skill.SkillExecutor.execute(battle, cyrene.getSkills().get(SkillType.ULTRA),
                    cyrene, List.of(battle.enemies.getFirst()));
            battle.processRequests();
            cyrene = battle.characters.getFirst();
            Assertions.assertTrue(cyrene.getBuffManager().hasState(WARD), "and her ultimate keeps it up");
        }
        return cyrene.getBuffManager().extendAllBuffs(1);
    }
}
