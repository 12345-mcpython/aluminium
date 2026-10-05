package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Signal;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1303 阮•梅's zone: 「我方全体全属性抗性穿透提高25.00%」 for two of HER turns, plus 星魂 1's defence ignore on the same clock.
 *
 * <p>Both are stated on her Ultimate with `ticks_on: self`, because the document gives the zone her own clock (「自身每回合开始时
 * 结界持续回合数减1」) and nothing extra happens when it ends. Two ways: after the cast every ally reads both values, and before
 * it they read nothing -- plus the eidolon's gate.
 */
public class RuanMeiZoneTest {
    private static final int RUAN_MEI = 1303;
    private static final int ALLY = 1210;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final double EPS = 1e-9;

    @Test
    public void theZoneGivesEveryAllyResistancePenetration() {
        Assertions.assertEquals(0.25, afterUlt(0, AttributeType.DAMAGE_PENETRATION), EPS,
                "处于结界中时我方全体全属性抗性穿透提高25.00%");
        Assertions.assertEquals(0.0, beforeUlt(0, AttributeType.DAMAGE_PENETRATION), EPS,
                "without the Ultimate nothing is stated");
    }

    @Test
    public void theFirstEidolonIgnoresDefenceOnTheZonesClock() {
        Assertions.assertEquals(0.2, afterUlt(1, AttributeType.DEFENCE_IGNORE), EPS,
                "结界期间，我方全体造成伤害时无视目标的20%的防御力");
        Assertions.assertEquals(0.0, afterUlt(0, AttributeType.DEFENCE_IGNORE), EPS, "rank 0 states nothing");
    }

    /**
     * The zone's own clock, which the document gives her: 「自身每回合开始时结界持续回合数减1」.
     *
     * <p>⚠ A WHOLE turn is `beforeMove()` AND `afterMove()`: the countdown is split into an early pass (beforeMove) and a
     * late pass (afterMove) by `if (buff.isEarlyBuff != early) continue;`, and a stat modifier is a LATE buff. Driving only
     * beforeMove leaves the zone standing forever.
     */
    @Test
    public void theZoneExpiresOnHerSecondWholeTurn() {
        Assertions.assertEquals(0.25, penetrationAfterHerTurns(1), EPS, "still up after one turn of hers");
        Assertions.assertEquals(0.0, penetrationAfterHerTurns(2), EPS,
                "自身每回合开始时结界持续回合数减1 -- so two turns end it");
    }

    private static double penetrationAfterHerTurns(int turns) {
        Fixture f = fixture(0);
        f.battle.castImmediate(f.ruanMei.getSkills().get(SkillType.ULTRA), f.ruanMei, List.of(f.enemy));
        for (int i = 0; i < turns; i++) {
            f.battle.currentMove = new Signal(f.ruanMei);
            f.battle.beforeMove();
            f.battle.afterMove();
        }
        return read(f.ally, AttributeType.DAMAGE_PENETRATION);
    }

    private static double afterUlt(int rank, AttributeType attribute) {
        Fixture f = fixture(rank);
        f.battle.castImmediate(f.ruanMei.getSkills().get(SkillType.ULTRA), f.ruanMei, List.of(f.enemy));
        return read(f.ally, attribute);
    }

    private static double beforeUlt(int rank, AttributeType attribute) {
        Fixture f = fixture(rank);
        return read(f.ally, attribute);
    }

    private static double read(Character ally, AttributeType attribute) {
        return ally.getAttribute(attribute).get() - ally.getAttribute(attribute).baseValue();
    }

    private static Fixture fixture(int rank) {
        Character ruanMei = CharacterFactory.create(RUAN_MEI, LEVEL, true, null, null, rank);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(ruanMei, ally), List.of(enemy), new Random(0));
        battle.startBattle();
        return new Fixture(ruanMei, ally, enemy, battle);
    }

    private record Fixture(Character ruanMei, Character ally, Enemy enemy, Battle battle) {
    }
}
