package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
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
 * 1412："持有[爵位]的角色…造成的战技伤害的暴击伤害提高 2%" (2026-10-02).
 *
 * <p>The assertion is about the CRIT MULTIPLIER, not about raw damage: the peerage also carries +16% DEF ignore, +10%
 * All-Type RES PEN and +20% pierce on skill damage, and all three raise crit and non-crit hits alike -- so dividing them out
 * leaves exactly what a +2% CRIT DMG changes. Both runs make the same six casts; the control removes only [爵位].
 *
 * <p>Note: The crit is forced, not hoped for: the Random returns 0.0 for a crit and 1.0 for "never crits" (the idiom
 * `AnchorDeathTest` and `Cid1220FollowUpCritTest` use).
 */
public class PeerageCritDamageTest {
    private static final int OWNER = 1412;
    private static final int ALLY = 1002;
    private static final int MONSTER = 1002011;
    private static final String PEERAGE = "爵位";

    /** The peer's skill crits harder than the same skill without the peerage. */
    @Test
    public void thePeerCritsHarderOnSkillDamage() {
        double withPeer = critRatio(true);
        double without = critRatio(false);
        Assertions.assertTrue(without > 1.0, "precondition: a crit really is bigger than a non-crit (" + without + ")");
        Assertions.assertTrue(withPeer > without,
                "「战技伤害的暴击伤害提高 72%」 (crit/non-crit: with=" + withPeer + ", without=" + without + ")");
    }

    /** forced-crit damage divided by never-crit damage, same scene, same six casts. */
    private static double critRatio(boolean keepPeerage) {
        double crit = damage(keepPeerage, true);
        double plain = damage(keepPeerage, false);
        Assertions.assertTrue(plain > 0, "precondition: the never-crit run deals damage");
        return crit / plain;
    }

    private static double damage(boolean keepPeerage, boolean forceCrit) {
        Character owner = CharacterFactory.create(OWNER, 80);
        Character ally = CharacterFactory.create(ALLY, 80);
        Battle battle = new Battle(List.of(owner, ally),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random() {
                    @Override
                    public double nextDouble() {
                        return forceCrit ? 0.0 : 1.0;
                    }
                });
        battle.startBattle();
        battle.processRequests();

        Skill hers = owner.getSkills().get(SkillType.SKILL);
        for (int i = 0; i < 6; i++) {
            SkillExecutor.execute(battle, hers, owner, List.of(ally));
            battle.processRequests();
        }
        Assertions.assertTrue(ally.getBuffManager().hasState(PEERAGE), "precondition: six casts promote");
        if (!keepPeerage) {
            ally.getBuffManager().removeState(PEERAGE);
            battle.processRequests();
        }
        double before = battle.enemies.get(0).getCurrentHp();
        Skill his = ally.getSkills().get(SkillType.SKILL);
        SkillExecutor.execute(battle, his, ally, List.of(battle.enemies.get(0)));
        battle.processRequests();
        return before - battle.enemies.get(0).getCurrentHp();
    }
}
