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
 * 1412：「若已升为【爵位】…该角色造成战技伤害时**额外无视 20%** 防御」 (2026-10-02).
 *
 * <p>⭐ SAME SCENE, ONE VARIABLE: both runs reach six Charge (so the merit holder is a peer), cast the same skill at the same
 * enemy; the control then removes 【爵位】. What is compared is the DAMAGE -- because `self_attr:` reads the sheet, and a
 * sentence about "when dealing Skill DMG" lives in one settled instance.
 */
public class PeeragePierceDamageTest {
    private static final int OWNER = 1412;
    private static final int ALLY = 1002;
    private static final int MONSTER = 1002011;
    private static final String PEERAGE = "爵位";

    /** ⭐ A peer's skill hits harder than the same skill without the peerage. */
    @Test
    public void thePeerPiercesMoreOnSkillDamage() {
        double withPeer = damageDealt(true);
        double without = damageDealt(false);
        Assertions.assertTrue(without > 0, "precondition: the control deals damage (" + without + ")");
        Assertions.assertTrue(withPeer > without,
                "「额外无视 20% 防御」 (with=" + withPeer + ", without=" + without + ")");
    }

    private static double damageDealt(boolean keepPeerage) {
        Character owner = CharacterFactory.create(OWNER, 80);
        Character ally = CharacterFactory.create(ALLY, 80);
        Battle battle = new Battle(List.of(owner, ally),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();

        Skill hers = owner.getSkills().get(SkillType.SKILL);
        for (int i = 0; i < 6; i++) {
            SkillExecutor.execute(battle, hers, owner, List.of(ally));
            battle.processRequests();
        }
        Assertions.assertTrue(ally.getBuffManager().hasState("军功"),
                "precondition: the ally holds the merit");
        Assertions.assertTrue(ally.getBuffManager().hasState(PEERAGE),
                "precondition: six casts promote the holder");
        if (!keepPeerage) {
            ally.getBuffManager().removeState(PEERAGE);
            battle.processRequests();
        }

        double before = battle.enemies.get(0).getCurrentHp();
        Skill his = ally.getSkills().get(SkillType.SKILL);
        Assertions.assertNotNull(his, "precondition: the ally has a skill");
        SkillExecutor.execute(battle, his, ally, List.of(battle.enemies.get(0)));
        battle.processRequests();
        return before - battle.enemies.get(0).getCurrentHp();
    }
}
