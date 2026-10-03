package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Summon;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1512 知更鸟•晴歌's memosprite panel (2026-10-02).
 *
 * <p>Document, verbatim (1512_知更鸟•晴歌.html:81): 「忆灵「晴空乐手」<b>初始拥有等同于知更鸟•晴歌 70% 生命上限的生命上限</b>和
 * <b>等同于知更鸟•晴歌 180% 速度的速度</b>。」
 *
 * <p>⭐ The panel itself has been in the tree for a while ({@code memosprites/1512.json} states EXACTLY
 * {@code HEALTH 0.7} and {@code SPEED 1.8}, which two independent readings agree on), but no judge asserted it -- this is that
 * judge, modelled line for line on {@link AglaeaMemospriteTest}: {@code battle.summonMemosprite(...)} returns the {@link Summon},
 * and the ratios are read off its inherited sheet. The mutation is the panel itself (0.7 -> 0.5), not the content.
 */
public class SummerSongbirdPanelTest {
    private static final double EPS = 1e-6;
    private static final int ROBIN = 1512;
    private static final int MONSTER = 1002011;

    /** \u2b50 The memosprite's sheet is 70% of her Max HP and 180% of her speed. */
    @Test
    public void theMemospriteInheritsHerPanelAsRatios() {
        Character robin = CharacterFactory.create(ROBIN, 80);
        Battle battle = new Battle(List.of(robin),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();

        Summon bird = battle.summonMemosprite(robin);
        Assertions.assertNotNull(bird, "her memosprite must be summonable");

        Assertions.assertEquals(robin.getMaxHp() * 0.7, bird.getMaxHp(), EPS,
                "\u300c\u521d\u59cb\u62e5\u6709\u7b49\u540c\u4e8e\u77e5\u66f4\u9e1f\u2022\u6674\u6b4c 70% \u751f\u547d\u4e0a\u9650\u7684\u751f\u547d\u4e0a\u9650\u300d");
        Assertions.assertEquals(robin.getAttribute(AttributeType.SPEED).get() * 1.8,
                bird.getAttribute(AttributeType.SPEED).get(), EPS,
                "\u300c\u7b49\u540c\u4e8e\u77e5\u66f4\u9e1f\u2022\u6674\u6b4c 180% \u901f\u5ea6\u7684\u901f\u5ea6\u300d");
    }
}
