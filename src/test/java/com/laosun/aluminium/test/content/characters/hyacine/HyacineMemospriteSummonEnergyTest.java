package com.laosun.aluminium.test.content.characters.hyacine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * 1409 风堇's memosprite skill 3, 【展翼，奔向日辉】 (data slot 5, ServantID 11409):
 *
 * <pre>
 * 「被召唤时，为风堇恢复 15 点能量，首次被召唤时，额外恢复 30 点能量。」 param = [15, 30]
 * </pre>
 *
 * <p>Two different facts, so two rules: the 15 is paid on <b>every</b> arrival (a memosprite that perished can be
 * summoned again), the 30 only on the <b>first</b> one -- which is what {@code once_per_battle} means in this
 * repository (8006's Rank 1 is the shipped precedent for exactly that word).
 *
 * <p>为什么值得钉：这两句话共用同一个事件（{@code SUMMONED}），合成一条规则就得给 {@code amount} 一个「第一次
 * 才加」的开关，而那种开关不存在；两条规则反而各自对应原文的一小句。少了 {@code once_per_battle} 时第二次召唤
 * 会变成 45，多了则第一次只有 15 —— 两种错都不会报错，只会给出一个不对的能量数。
 */
public class HyacineMemospriteSummonEnergyTest {

    private static final int HYACINE = 1409;
    private static final int ALLY = 1001;
    private static final double EPS = 1e-9;

    @Test
    public void theFirstArrivalPaysFifteenPlusThirty() {
        Character hyacine = CharacterFactory.create(HYACINE, 80);
        Battle battle = battle(hyacine);
        double before = hyacine.getCurrentEnergy();

        battle.summonMemosprite(hyacine);
        battle.processRequests();

        Assertions.assertEquals(15.0 + 30.0, hyacine.getCurrentEnergy() - before, EPS,
                "「被召唤时…恢复 15 点能量，首次被召唤时，额外恢复 30 点」 (before=" + before + ", after="
                        + hyacine.getCurrentEnergy() + ")");
    }

    @Test
    public void aLaterArrivalPaysOnlyFifteen() {
        Character hyacine = CharacterFactory.create(HYACINE, 80);
        Battle battle = battle(hyacine);
        battle.summonMemosprite(hyacine);
        battle.processRequests();
        CanHit ika = battle.memospriteOf(hyacine);
        Assertions.assertNotNull(ika, "precondition: 小伊卡 is out");

        // 「首次」 is once per BATTLE, so the second arrival must be told apart from the first. `perish()` is the
        // engine's "it left, nobody killed it" (no DEATH payout), which is exactly what a memosprite that simply goes
        // away does.
        ika.perish();
        battle.processRequests();
        double before = hyacine.getCurrentEnergy();
        battle.summonMemosprite(hyacine);
        battle.processRequests();

        Assertions.assertEquals(15.0, hyacine.getCurrentEnergy() - before, EPS,
                "the +30 is 「首次被召唤时」 only -- a later arrival pays the flat 15");
    }

    private static Battle battle(Character hyacine) {
        Battle battle = new Battle(List.of(hyacine, CharacterFactory.create(ALLY, 80)),
                List.of(BattleFixture.dummy()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        return battle;
    }
}
