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
 * 1413's skill, the clause that raises every memosprite's CRIT DMG (2026-10-02): 「使我方全体忆灵的暴击伤害提高，提高数值等同于长夜月暴击伤害的 #1%」.
 *
 * \u2b50 It is also the effect that 1141524's third sentence later raises, so it has to exist before that can be written.
 *
 * \u2b50 Two-sided: after her skill, a memosprite's CRIT DMG rises by her own CRIT DMG times her skill's #1 (read from the skill row, never a literal); before it, it does not.
 */
public class EvernightCritForSummonsTest {
    private static final int LEVEL = 80;
    private static final int LONGNIGHT = 1413;
    private static final int CYRENE = 1415;
    private static final int MONSTER = 1002011;

    @Test
    public void herSkillRaisesTheSummonsCritDamage() {
        double[] gained = run(true);
        double[] clean = run(false);
        System.out.println("[evernight_crit] the memosprite's CRIT DMG gained " + gained[0]
                + " (her crit damage " + gained[1] + " x her skill's #1) ; without the skill " + clean[0]);
        Assertions.assertEquals(gained[1] * gained[2], gained[0], 1e-6,
                "the gain is her own CRIT DMG times her skill's #1");
        Assertions.assertEquals(0.0, clean[0], 1e-9, "and before the skill there is no gain");
    }

    /** [what a memosprite's CRIT DMG gained, her CRIT DMG, her skill's #1] */
    private static double[] run(boolean castHerSkill) {
        Character cyrene = CharacterFactory.create(CYRENE, LEVEL);
        Character longnight = CharacterFactory.create(LONGNIGHT, LEVEL);
        Battle battle = new Battle(List.of(cyrene, longnight),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        cyrene = battle.characters.get(0);
        longnight = battle.characters.get(1);

        // \u2b50 cyrene's memosprite stands in for "our memosprites": it is one of ours, which is all the clause asks
        var sprite = battle.summonServant(cyrene);
        battle.processRequests();
        double before = sprite.getAttribute(AttributeType.CRIT_ATTACK).get();

        double herCrit = battle.characters.get(1).getAttribute(AttributeType.CRIT_ATTACK).get();
        var skill = battle.characters.get(1).getSkills().get(com.laosun.aluminium.enums.SkillType.SKILL);
        Assertions.assertNotNull(skill, "precondition: she has a skill");
        double param = skill.getData().getSkills()
                .get(battle.characters.get(1).skillLevel(skill) - 1).get(0);
        if (castHerSkill) {
            SkillExecutor.execute(battle, skill, battle.characters.get(1), List.of(battle.enemies.getFirst()));
            battle.processRequests();
        }
        double after = sprite.getAttribute(AttributeType.CRIT_ATTACK).get();
        return new double[]{after - before, herCrit, param};
    }
}
