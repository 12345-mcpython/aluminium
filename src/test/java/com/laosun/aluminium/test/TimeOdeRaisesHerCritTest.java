package com.laosun.aluminium.test;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.models.skill.SkillExecutor;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Random;

/**
 * 1415's memosprite skill 24, third sentence (2026-10-02): 「长夜月战技的暴击伤害提高效果额外提高，提高数值等同于长夜月暴击伤害的 #3%」.
 *
 * ⭐ Her skill states its share as `percent_from_skill_param` (it varies with level: 0.12 -> 0.264), so the amendment must ADD to whatever that resolves to -- not write a `percent`,
 * which `shareOf` reads first and which would therefore erase her skill's own number. Both readings come from the engine: the gain a memosprite actually received, and her own CRIT DMG.
 */
public class TimeOdeRaisesHerCritTest {
    private static final int LEVEL = 80;
    private static final int LONGNIGHT = 1413;
    private static final int CYRENE = 1415;
    private static final int MONSTER = 1002011;
    private static final int ODE_SLOT = 24;

    @Test
    public void theOdeRaisesHerSkillCritEffect() {
        double[] with = run(true);
        double[] without = run(false);
        System.out.println("[time_crit] the memosprite's CRIT DMG gained " + with[0] + " with the ode ; " + without[0]
                + " without it (her CRIT DMG " + with[1] + ", her skill's #1 " + with[2] + ")");
        Assertions.assertEquals(with[1] * (with[2] + 0.06), with[0], 1e-6,
                "with the ode the gain is her skill's #1 PLUS the ode's #3, times her CRIT DMG");
        Assertions.assertEquals(without[1] * without[2], without[0], 1e-6,
                "and without the ode it is only her skill's #1 times her CRIT DMG");
    }

    /** [the memosprite's CRIT DMG gain, her CRIT DMG, her skill's #1] */
    private static double[] run(boolean castTheOde) {
        Character cyrene = CharacterFactory.create(CYRENE, LEVEL);
        Character longnight = CharacterFactory.create(LONGNIGHT, LEVEL);
        Battle battle = new Battle(List.of(cyrene, longnight),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        cyrene = battle.characters.get(0);
        longnight = battle.characters.get(1);

        if (castTheOde) {
            var sprite = battle.summonServant(cyrene);
            battle.processRequests();
            var ode = sprite.skillAt(ODE_SLOT);
            Assertions.assertNotNull(ode, "precondition: slot 24");
            SkillExecutor.execute(battle, ode, sprite, List.of(longnight));
            battle.processRequests();
        }

        var ourSprite = battle.summonServant(battle.characters.get(0));
        battle.processRequests();
        double before = ourSprite.getAttribute(AttributeType.CRIT_ATTACK).get();
        double herCrit = battle.characters.get(1).getAttribute(AttributeType.CRIT_ATTACK).get();
        var skill = battle.characters.get(1).getSkills().get(com.laosun.aluminium.enums.SkillType.SKILL);
        Assertions.assertNotNull(skill, "precondition: she has a skill");
        double param = skill.getData().getSkills().get(battle.characters.get(1).skillLevel(skill) - 1).get(0);
        SkillExecutor.execute(battle, skill, battle.characters.get(1), List.of(battle.enemies.getFirst()));
        battle.processRequests();
        return new double[]{ourSprite.getAttribute(AttributeType.CRIT_ATTACK).get() - before, herCrit, param};
    }
}
