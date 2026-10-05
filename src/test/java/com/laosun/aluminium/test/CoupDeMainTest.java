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
 * 1412："奇袭：复制一次即将施放的技能并提前施放，随后施放原技能。奇袭不会再次触发奇袭"＋
 * "奇袭结束后，消耗 6 点充能使[爵位]变回[军功]" (2026-10-02).
 *
 * <p>SAME SCENE, ONE VARIABLE: six casts promote the ally either way; the control merely removes [爵位] before the
 * peer's own skill, so the only difference is whether the copy happens.
 *
 * <p>Note: The upper bound is the anti-recursion proof: two casts, not an unbounded chain.
 */
public class CoupDeMainTest {
    private static final int OWNER = 1412;
    private static final int ALLY = 1002;
    private static final int MONSTER = 1002011;
    private static final String PEERAGE = "爵位";
    private static final String MERIT = "军功";
    private static final String CHARGE = "充能";

    /** The peer's skill is cast twice -- the copy first, then the original -- and not forever. */
    @Test
    public void thePeerCastsItsSkillTwice() {
        double withCoup = damageDealt(true);
        double without = damageDealt(false);
        Assertions.assertTrue(without > 0, "precondition: the control deals damage (" + without + ")");
        Assertions.assertTrue(withCoup > without * 1.5,
                "「复制一次…提前施放，随后施放原技能」 (with=" + withCoup + ", without=" + without + ")");
        Assertions.assertTrue(withCoup < without * 2.5,
                "「奇袭不会再次触发奇袭」 -- two casts, not a chain (with=" + withCoup + ", without=" + without + ")");
    }

    /** After the coup, she pays six Charge and the peerage reverts to the merit. */
    @Test
    public void theCoupEndsBySpendingSixCharge() {
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
        Assertions.assertTrue(ally.getBuffManager().hasState(PEERAGE), "precondition: the holder is a peer");
        double charge = owner.getResources().value(CHARGE);
        Assertions.assertTrue(charge >= 6, "precondition: at least six Charge (" + charge + ")");

        Skill his = ally.getSkills().get(SkillType.SKILL);
        SkillExecutor.execute(battle, his, ally, List.of(battle.enemies.get(0)));
        battle.processRequests();

        Assertions.assertEquals(charge - 6 + 2, owner.getResources().value(CHARGE), 1e-9,
                "「消耗 6 点充能」—— ❗ 而两次施放（复制 + 原技能）"
                        + "各给她 +1 点（【军功】那条：「施放普攻或战技时使刻律德菈获得 1 点充能」）"
                        + "，所以净变化是 -4 ✓ (before=" + charge + ")");
        Assertions.assertFalse(ally.getBuffManager().hasState(PEERAGE),
                "「使【爵位】变回【军功】」");
        Assertions.assertTrue(ally.getBuffManager().hasState(MERIT),
                "“变回【军功】” -- the merit is still there");
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
