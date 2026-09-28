package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1009 Asta, from her own file (2026-09-29, round 195): the FLAT speed boost, whose base is 17 documents.
 *
 * <p>The assertion is absolute because the document is: speed rises by exactly 50 POINTS, not by a share.
 */
public class AstaTest {
    private static final int ASTA = 1009;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /** \u26a0 「使我方全体速度提高50点，持续2回合」 -- fifty points, for the party. */
    @Test
    public void herUltimateRaisesEveryAllysSpeedByFiftyPoints() {
        Character asta = CharacterFactory.create(ASTA, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(asta, ally), List.of(enemy), fixed());
        battle.startBattle();
        double before = ally.getAttribute(AttributeType.SPEED).get();

        battle.castImmediate(asta.getSkills().get(SkillType.ULTRA), asta, List.of(ally));

        Assertions.assertEquals(50.0, ally.getAttribute(AttributeType.SPEED).get() - before, 1e-6,
                "\u300c\u4f7f\u6211\u65b9\u5168\u4f53\u901f\u5ea6\u63d0\u9ad850\u70b9\u300d: before " + before + ", after " + ally.getAttribute(AttributeType.SPEED).get());
    }

    /** \u26a0 The technique's 50%-ATK opening, and its control. */
    @Test
    public void theTechniqueHitsOnlyWhenDeclared() {
        Character asta = CharacterFactory.create(ASTA, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(asta), List.of(enemy), fixed());
        battle.markTechniqueUsed(asta);
        double before = enemy.getCurrentHp();
        battle.startBattle();
        Assertions.assertTrue(before - enemy.getCurrentHp() > 0,
                "\u300c\u5bf9\u654c\u65b9\u5168\u4f53\u76ee\u6807\u9020\u6210\u7b49\u540c\u4e8e\u827e\u4e1d\u59b250%\u653b\u51fb\u529b\u7684\u706b\u5c5e\u6027\u4f24\u5bb3\u300d");

        Character plain = CharacterFactory.create(ASTA, LEVEL);
        Enemy enemy2 = EnemyFactory.create(MONSTER, 90, 1);
        Battle plainBattle = new Battle(List.of(plain), List.of(enemy2), fixed());
        double untouched = enemy2.getCurrentHp();
        plainBattle.startBattle();
        Assertions.assertEquals(untouched, enemy2.getCurrentHp(), 1e-9, "\u300c\u4f7f\u7528\u79d8\u6280\u540e\u300d -- undeclared, so nothing");
    }

    private static Random fixed() {
        return new Random() {
            @Override
            public double nextDouble() {
                return 1.0;
            }
        };
    }
}
