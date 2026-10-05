package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 「立即攻击敌人，进入战斗后克拉拉受到敌方攻击的概率提高，持续 2 回合」 — a technique clause, so the gate is the point.
 *
 * <p>The number is the parameter her prose never names: the technique's table is {@code [2, 5]}, the text quotes only
 * the 2 turns, and {@code MAvatar_Klara_00_MazeSkill_AggroUP} writes {@code AggroAddedRatio} — so 5 is the ratio and the
 * weight becomes x(1 + 5) = <b>x6</b>. The gate is the engine's existing {@code self has_state 秘技}
 * ({@link Battle#markTechniqueUsed}), which is also how 1104's technique shield is spelled.
 *
 * <p>⚠ The 2-turn duration rides in {@code turns: 2} (the number the prose does name). Its countdown belongs to the
 * engine's buff lifetimes, which have their own tests; nothing public exposes "remaining turns" to assert it here, so
 * this case pins the half that is observable: the clause fires with the technique and does nothing without it.
 */
public class KlaraTechniqueAggroTest {
    private static final int KLARA = 1107;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final double EPS = 1e-6;

    @Test
    public void theTechniqueRaisesHerWeightOnlyWhenItWasUsed() {
        Character plain = CharacterFactory.create(KLARA, LEVEL);
        Battle withoutTechnique = new Battle(List.of(plain), List.of(dummy()), new Random(0));
        double plainBefore = withoutTechnique.aggroOf(plain);

        withoutTechnique.startBattle();

        Assertions.assertEquals(plainBefore, withoutTechnique.aggroOf(plain), EPS,
                "no technique was used, so 「进入战斗后…概率提高」 must not fire");

        Character clara = CharacterFactory.create(KLARA, LEVEL);
        Battle withTechnique = new Battle(List.of(clara), List.of(dummy()), new Random(0));
        double base = withTechnique.aggroOf(clara);
        withTechnique.markTechniqueUsed(clara);

        withTechnique.startBattle();

        Assertions.assertEquals(base * 6.0, withTechnique.aggroOf(clara), EPS,
                "「克拉拉受到敌方攻击的概率提高」 "
                        + "-- the unclaimed parameter 5 reads as weight x (1 + 5)");
    }

    private static Enemy dummy() {
        return EnemyFactory.create(MONSTER, 90, 1);
    }
}
