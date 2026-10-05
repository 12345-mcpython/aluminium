package com.laosun.aluminium.test;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Signal;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Random;

/**
 * "使其战技的伤害次数增加 3 次" (2026-10-02).
 *
 * One variable: the same battle, the same seed, the same ode; the control takes the three segments back with the op's own negative amount. Nothing else differs, so what moves is the segments.
 */
public class ReasonOdeExtraHitsTest {
    private static final int LEVEL = 80;
    private static final int CYRENE = 1415;
    private static final int ANAXA = 1405;
    private static final int MONSTER = 1002011;
    private static final int ODE_SLOT = 18;

    @Test
    public void theOdeAddsThreeDamageSegments() {
        double with = hisSkillDamage(true);
        double without = hisSkillDamage(false);
        System.out.println("[extra_hits] his skill deals " + with + " with the three extra segments, " + without
                + " without them");
        Assertions.assertTrue(with > without, "three more segments must deal more damage");
    }

    private static double hisSkillDamage(boolean keepTheSegments) {
        Character cyrene = CharacterFactory.create(CYRENE, LEVEL);
        Character him = CharacterFactory.create(ANAXA, LEVEL);
        Battle battle = new Battle(List.of(cyrene, him),
                List.of(EnemyFactory.create(MONSTER, 100, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        var sprite = battle.summonServant(battle.characters.get(0));
        battle.processRequests();
        var ode = sprite.skillAt(ODE_SLOT);
        Assertions.assertNotNull(ode, "precondition: slot 18");
        Character aimed = battle.characters.get(1);
        com.laosun.aluminium.models.skill.SkillExecutor.execute(battle, ode, sprite, List.of(aimed));
        battle.processRequests();
        aimed = battle.characters.get(1);
        var skill = aimed.getSkills().get(SkillType.SKILL);
        Assertions.assertNotNull(skill, "precondition: his skill");
        int slot = skill.getSkillSlot();
        if (!keepTheSegments) {
            // Take back EXACTLY what is there, not a hard-coded three: a control that assumes the bonus sits on this slot cannot see a misdirected one.
            aimed.raiseSkillHits(slot, -aimed.skillHitBonus(slot));
        }
        double before = battle.enemies.getFirst().getCurrentHp();
        com.laosun.aluminium.models.skill.SkillExecutor.execute(battle, skill, aimed,
                List.of(battle.enemies.getFirst()));
        battle.processRequests();
        return before - battle.enemies.getFirst().getCurrentHp();
    }
}
