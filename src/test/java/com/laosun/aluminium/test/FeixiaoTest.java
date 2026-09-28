package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1220 Feixiao, from her own file (2026-09-29, round 232): the teammate-triggered follow-up with its per-turn limit, and the boost it carries.
 */
public class FeixiaoTest {
    private static final int FEIXIAO = 1220;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /** \u26a0 The follow-up needs a TEAMMATE, and `per_turn: 1` caps it: the second firing in a turn does nothing. */
    @Test
    public void theFollowUpNeedsATeammateAndFiresOncePerTurn() {
        double first = followUpLoss(true, 1);
        double twice = followUpLoss(true, 2);
        double hers = followUpLoss(false, 1);

        Assertions.assertTrue(first > 0, "a teammate's attack must draw it");
        Assertions.assertEquals(first, twice, 1e-6,
                "\u300c\u8be5\u6548\u679c\u6bcf\u56de\u5408\u6700\u591a\u89e6\u53d11\u6b21\u300d -- two firings in the same turn must deal ONE instance");
        Assertions.assertEquals(0.0, hers, 1e-9,
                "\u300c\u5f53\u98de\u9704\u7684\u961f\u53cb\u5bf9\u654c\u65b9\u76ee\u6807\u65bd\u653e\u653b\u51fb\u540e\u300d -- her OWN attack must not draw it");
    }

    /** \u26a0 The boost the same trigger grants her: 60% for two turns. */
    @Test
    public void theTriggerAlsoBoostsHerself() {
        Character feixiao = CharacterFactory.create(FEIXIAO, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(feixiao, ally), List.of(enemy), fixed());
        battle.startBattle();
        double before = feixiao.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();

        battle.fireTriggers(TriggerEvent.ALLY_ATTACK, ally, enemy, 0, 0);

        Assertions.assertEquals(0.6, feixiao.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get() - before, 1e-9,
                "\u300c\u53d1\u52a8\u6b64\u653b\u51fb\u65f6\u4f7f\u81ea\u8eab\u9020\u6210\u7684\u4f24\u5bb3\u63d0\u9ad860%\u300d");
    }

    /** Fires `times` teammate attacks and returns the total damage they drew; `byTeammate` false means Feixiao herself attacks. */
    private static double followUpLoss(boolean byTeammate, int times) {
        Character feixiao = CharacterFactory.create(FEIXIAO, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(feixiao, ally), List.of(enemy), fixed());
        battle.startBattle();
        double before = enemy.getCurrentHp();
        for (int i = 0; i < times; i++) {
            battle.fireTriggers(TriggerEvent.ALLY_ATTACK, byTeammate ? ally : feixiao, enemy, 0, 0);
        }
        return before - enemy.getCurrentHp();
    }

    private static Random fixed() {
        return new Random() {
            @Override
            public double nextDouble() {
                return 0.0;
            }
        };
    }
}
