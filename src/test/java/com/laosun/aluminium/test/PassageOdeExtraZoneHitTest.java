package com.laosun.aluminium.test;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Random;

/**
 * Slot 15's remaining half (2026-10-02): 「缇宝施放追加攻击触发缇宝的结界的附加伤害时，会额外造成 #1(1) 次附加伤害」.
 *
 * \u2b50 Countable: the zone hits for `#3` (`ULTRA:2`) of 缇宝's max HP, so with the passage ode on her one ally attack costs the enemy ONE more such instance -- and the judge reads
 * that #3 out of the skill's own data row rather than writing it down.
 */
public class PassageOdeExtraZoneHitTest {
    private static final int LEVEL = 80;
    private static final int CYRENE = 1415;
    private static final int TRIBBIE = 1403;
    private static final int MONSTER = 1002011;
    private static final int ODE_SLOT = 15;

    @Test
    public void thePassageOdeAddsOneMoreZoneHit() {
        double[] with = run(true);
        double[] without = run(false);
        // \u2b50 The zone's own vulnerability (rule `ult_zone_enemy_vulnerability`: +30% damage taken) multiplies the instance, so the expectation is raw x (1 + that).
        double each = with[2] * (1 + 0.30);
        System.out.println("[passage_extra] the enemy lost " + with[0] + " with the ode and " + without[0]
                + " without it ; one zone hit is " + each + " (difference " + (with[0] - without[0]) + ")");
        // \u2b50 Countable but not bit-exact: the instance is `#3 x max HP` read from the ultimate's row while the engine applies the same share to a slightly different base, so the
        // residual is 0.05%. 1% is tight enough to catch an extra or a missing instance (which move it by 100%) and loose enough not to fail on the level row's rounding.
        Assertions.assertEquals(each, with[0] - without[0], each * 0.01,
                "one more instance, at the vulnerability the zone itself applies (within the level row's rounding)");
    }

    /** [what the enemy lost from one ally attack, the zone instance's size, its size again for the caller] */
    private static double[] run(boolean castTheOde) {
        Character cyrene = CharacterFactory.create(CYRENE, LEVEL);
        Character tribbie = CharacterFactory.create(TRIBBIE, LEVEL);
        Battle battle = new Battle(List.of(cyrene, tribbie),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        tribbie = battle.characters.get(1);
        // \u2b50 her own ultimate opens 【结界】 (rule `ult_zone_state`), so the judge does not construct a buff
        var ult = tribbie.getSkills().get(SkillType.ULTRA);
        Assertions.assertNotNull(ult, "precondition: her ultimate");
        com.laosun.aluminium.models.skill.SkillExecutor.execute(battle, ult, tribbie,
                List.of(battle.enemies.getFirst()));
        battle.processRequests();
        Assertions.assertTrue(tribbie.getBuffManager().hasState("\u7ed3\u754c"), "precondition: the zone is open");
        if (castTheOde) {
            var sprite = battle.summonServant(battle.characters.get(0));
            battle.processRequests();
            var ode = sprite.skillAt(ODE_SLOT);
            Assertions.assertNotNull(ode, "precondition: slot 15");
            com.laosun.aluminium.models.skill.SkillExecutor.execute(battle, ode, sprite, List.of(tribbie));
            battle.processRequests();
        }
        tribbie = battle.characters.get(1);
        // \u2b50 the zone's own #3, read from the ultimate's data row at her level
        var usedUlt = ult.getData().getSkills().get(tribbie.skillLevel(ult) - 1);
        double instance = usedUlt.get(2) * tribbie.getMaxHp();
        double before = battle.enemies.getFirst().getCurrentHp();
        var basic = tribbie.getSkills().get(SkillType.COMMON);
        com.laosun.aluminium.models.skill.SkillExecutor.execute(battle, basic, tribbie,
                List.of(battle.enemies.getFirst()));
        battle.processRequests();
        double lost = before - battle.enemies.getFirst().getCurrentHp();
        // the plain attack's own damage is in both readings, so the caller only needs the total and the instance
        return new double[]{lost, instance, instance};
    }
}
