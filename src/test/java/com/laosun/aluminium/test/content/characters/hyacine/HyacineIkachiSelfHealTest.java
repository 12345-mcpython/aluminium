package com.laosun.aluminium.test.content.characters.hyacine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.entity.Summon;
import com.laosun.aluminium.skill.SkillExecutor;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * 1409 风堇's skill, the half that had no reader: 「**为小伊卡回复**等同于风堇 <b>#3%</b> 生命上限 + <b>#4</b> 的生命值」.
 *
 * <p>The row is {@code 1409/2} = {@code [0.08, 160, 0.10, 200]} at Lv10: {@code #1/#2} are the party heal (delivered
 * by the skill data) and {@code #3/#4} belong to 小伊卡 alone. A judge that only measures the party heal cannot see
 * this half at all - 小伊卡's own HP is what moves here.
 */
public class HyacineIkachiSelfHealTest {

    private static final int HYACINE = 1409;
    private static final int ALLY = 1001;
    private static final double SHARE_AT_LV10 = 0.10;
    private static final double FLAT_AT_LV10 = 200;

    @Test
    public void theSkillHealsIkachiByItsOwnShareOfHerMaxHpPlusFlat() {
        Character hyacine = CharacterFactory.create(HYACINE, 80);
        Character ally = CharacterFactory.create(ALLY, 80);
        Battle battle = new Battle(List.of(hyacine, ally), List.of(BattleFixture.dummy()),
                BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        battle.summonMemosprite(hyacine);
        battle.processRequests();
        Summon ika = (Summon) battle.memospriteOf(hyacine);
        Assertions.assertNotNull(ika, "precondition: 小伊卡 is out");

        // Leave room for the heal on BOTH sides, so the amount is what the rule delivers and not the cap.
        ally.consumeHp(ally.getMaxHp() * 0.5);
        // It receives BOTH halves now: 「我方全体」 (0.08/160) and 「为小伊卡」 (0.10/200). Leave nearly all of its
        // bar empty so two heals fit under its own cap.
        ika.consumeHp(ika.getMaxHp() * 0.98);
        double herMaxHpBefore = hyacine.getMaxHp();
        double ikaBefore = ika.getCurrentHp();

        SkillExecutor.execute(battle, hyacine.getSkills().get(SkillType.SKILL), hyacine, List.of(ally));
        battle.processRequests();

        // Both clauses land on it: it is 我方一员 (0.08/160) AND the one 「为小伊卡」 names (0.10/200).
        double expected = (0.08 + SHARE_AT_LV10) * herMaxHpBefore + 160 + FLAT_AT_LV10;
        Assertions.assertEquals(expected, ika.getCurrentHp() - ikaBefore, Math.max(1e-6, expected * 1e-9),
                "「为小伊卡回复 #3% + #4」 on top of 「我方全体 #1% + #2」 at Lv10 = 0.18 x " + herMaxHpBefore + " + 360");
    }

}
