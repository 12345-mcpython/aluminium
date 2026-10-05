package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.models.skill.Skill;
import com.laosun.aluminium.models.skill.SkillExecutor;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1403 Tribbie's (缇宝) zone deals its own additional damage (2026-10-02).
 *
 * <p>"受到我方目标攻击后，每有1名目标受到攻击，会对被攻击目标中当前生命值最高的目标造成 1 次等同于缇宝 #3% 生命上限的量子属性附加伤害。"
 *
 * <p>The two scenes differ by EXACTLY one rule, and the zone is open in both. Note: An earlier version replaced his table with an EMPTY one to remove that rule, which also
 * dropped `level_convention` -- the trap `literalBase`'s own comment records being sprung by a judge three times. This one keeps every loaded rule and filters out
 * exactly the rider.
 */
public class ZoneAdditionalDamageTest {
    private static final int LEVEL = 80;
    private static final int TRIBBIE = 1403;
    private static final int ATTACKER = 1402;
    private static final int MONSTER = 1002011;
    private static final String ZONE = "结界";

    @Test
    public void theZoneAddsExactlyItsOwnInstance() {
        double withRider = damageFromAllyAttack(true);
        double withoutRider = damageFromAllyAttack(false);
        double delta = withRider - withoutRider;
        double rawAt80 = rawShare(LEVEL);
        double rawAtLow = rawShare(1);

        System.out.println("[zone_rider] zone open, rider on = " + withRider + " ; rider off = " + withoutRider
                + " ; the rider's own instance = " + delta + " (raw #3 x Max HP = " + rawAt80 + ")");

        Assertions.assertTrue(withoutRider > 0, "precondition: the ally attack lands");
        Assertions.assertTrue(delta > 0, "「造成 1 次…附加伤害」-- the zone's own instance");
        Assertions.assertTrue(delta < rawAt80,
                "it is a real damage instance, so the target's zones scale it: " + delta + " < " + rawAt80);
        Assertions.assertTrue(delta > rawAt80 / 4,
                "and of the same order as the share -- not the 0.0 a share spelling the op ignores would settle to");
        Assertions.assertTrue(rawAt80 > rawAtLow, "#3 x Max HP runs with level, which is what percent_from_skill_param reads");
    }

    /** `#3  x  Max HP` at a level, read the way the engine reads it -- at the skill level that character actually has. */
    private static double rawShare(int level) {
        Character tribbie = CharacterFactory.create(TRIBBIE, level);
        Skill ultra = tribbie.getSkills().get(SkillType.ULTRA);
        var row = ultra.getData().getSkills().get(tribbie.skillLevel(ultra) - 1);
        return row.get(2) * tribbie.getAttribute(AttributeType.HEALTH).get();
    }

    private static double damageFromAllyAttack(boolean withRider) {
        Character tribbie = CharacterFactory.create(TRIBBIE, LEVEL);
        Character attacker = CharacterFactory.create(ATTACKER, LEVEL);
        Battle battle = new Battle(List.of(tribbie, attacker),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        tribbie = battle.characters.getFirst();
        attacker = battle.characters.get(1);

        tribbie.setCurrentEnergy(tribbie.getMaxEnergy());
        Assertions.assertTrue(battle.castUltra(tribbie, List.of(battle.enemies.getFirst())),
                "precondition: his ultimate opens the zone");
        battle.processRequests();

        if (!withRider) {
            // EVERY rule stays loaded (level_convention included) and the ultimate has already been cast, so the vulnerability is on the enemies in both scenes.
            // The one thing taken away is the STATE the rider's gate reads -- `removeState` takes the state name off him and touches nothing else.
            int taken = tribbie.getBuffManager().removeState(ZONE);
            Assertions.assertTrue(taken > 0, "precondition: the zone state was on him to remove");
        }

        var enemy = battle.enemies.getFirst();
        double before = enemy.getCurrentHp();
        Skill skill = attacker.getSkills().values().stream()
                .filter(s -> s != null && s.getData() != null && s.getData().getEffect().isDamaging())
                .findFirst().orElse(null);
        Assertions.assertNotNull(skill, "precondition: the attacker has a damaging skill");
        SkillExecutor.execute(battle, skill, attacker, List.of(enemy));
        battle.processRequests();
        return before - enemy.getCurrentHp();
    }
}
