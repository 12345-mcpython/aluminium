package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.buff.ReductionBuff;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 「结界持续期间…」 — the zone's clock is a COUNTDOWN, so its effects last exactly as long as that unit is alive.
 *
 * <p>The sentence (行迹 千锻魂) has three effects and the upstream modifier writes exactly three properties
 * (AggroAddedRatio + AllDamageReduce + HealTakenRatio, from the same row whose ParamList is [10, 0.5, 0.5]). All three are
 * applied named at the Ultimate and taken off on the countdown's turn, which is what 「倒计时回合开始时结界解除」 says.
 */
public class MortenaxZoneTest {
    private static final int MORTENAX = 1507;
    private static final int ALLY = 1210;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final double EPS = 1e-6;

    @Test
    public void theZoneGrantsThreeEffectsAndTheCountdownTakesThemAllOff() {
        Character mortenax = CharacterFactory.create(MORTENAX, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(mortenax, ally), List.of(enemy), new Random(0));
        battle.startBattle();
        double baseWeight = battle.aggroOf(mortenax);
        double baseHeal = battle.calculateHeal(ally, mortenax, 1000);

        battle.castImmediate(mortenax.getSkills().get(SkillType.ULTRA), mortenax, List.of(enemy));

        Assertions.assertEquals(baseWeight * 11.0, battle.aggroOf(mortenax), EPS,
                "\u300c\u88ab\u654c\u65b9\u653b\u51fb\u7684\u6982\u7387\u63d0\u9ad8\u300d -- the unclaimed 10 reads as x (1 + 10)");
        Assertions.assertEquals(1, mortenax.getBuffManager().countBuffs(ReductionBuff.class),
                "\u300c\u81ea\u8eab\u53d7\u5230\u7684\u4f24\u5bb3\u964d\u4f4e50%\u300d");
        Assertions.assertEquals(baseHeal * 1.5, battle.calculateHeal(ally, mortenax, 1000), EPS,
                "\u300c\u53d7\u5230\u7684\u6cbb\u7597\u91cf\u63d0\u9ad850%\u300d");

        battle.fireTriggers(TriggerEvent.COUNTDOWN_TURN, battle.countdownsOf(mortenax).getFirst(), mortenax, 0, 0);

        Assertions.assertEquals(baseWeight, battle.aggroOf(mortenax), EPS,
                "\u300c\u5012\u8ba1\u65f6\u56de\u5408\u5f00\u59cb\u65f6\u7ed3\u754c\u89e3\u9664\u300d -- the weight goes back");
        Assertions.assertEquals(0, mortenax.getBuffManager().countBuffs(ReductionBuff.class),
                "and the reduction comes off with it");
        Assertions.assertEquals(baseHeal, battle.calculateHeal(ally, mortenax, 1000), EPS,
                "and so does the healing taken bonus");
    }
}
