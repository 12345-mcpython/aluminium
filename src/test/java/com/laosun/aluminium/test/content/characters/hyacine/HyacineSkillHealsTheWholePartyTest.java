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
 * 1409 风堇's skill heals 「我方<b>全体</b>目标」 - the scope, not just the amount.
 *
 * <p>The heal used to come from the skill data (`skill_effects.json` 1409/2 = `Restore`), and a `Restore` heals the
 * targets the CALLER passed in - so in practice one ally was healed no matter what the sentence said. The scope is now
 * stated by a rule (`target: all_allies`), while the numbers still come from the same row.
 */
public class HyacineSkillHealsTheWholePartyTest {

    private static final int HYACINE = 1409;
    private static final double SHARE_AT_LV10 = 0.08;
    private static final double FLAT_AT_LV10 = 160;

    @Test
    public void everyWoundedAllyIsHealedNotJustTheChosenOne() {
        Character hyacine = CharacterFactory.create(HYACINE, 80);
        Character first = CharacterFactory.create(1001, 80);
        Character second = CharacterFactory.create(1003, 80);
        Battle battle = new Battle(List.of(hyacine, first, second), List.of(BattleFixture.dummy()),
                BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        battle.summonMemosprite(hyacine);
        battle.processRequests();
        Summon ika = (Summon) battle.memospriteOf(hyacine);
        Assertions.assertNotNull(ika, "precondition: 小伊卡 is out");

        // Every ally wounded, so a heal has room to show on each of them.
        for (Character ally : List.of(first, second)) {
            ally.consumeHp(ally.getMaxHp() * 0.5);
        }
        ika.consumeHp(ika.getMaxHp() * 0.9);
        double herMaxHp = hyacine.getMaxHp();
        double firstBefore = first.getCurrentHp();
        double secondBefore = second.getCurrentHp();

        // The caller aims at ONE ally; the sentence heals the whole side.
        SkillExecutor.execute(battle, hyacine.getSkills().get(SkillType.SKILL), hyacine, List.of(first));
        battle.processRequests();

        double expected = SHARE_AT_LV10 * herMaxHp + FLAT_AT_LV10;
        Assertions.assertEquals(expected, first.getCurrentHp() - firstBefore, expected * 1e-9,
                "the ally the caller aimed at");
        Assertions.assertEquals(expected, second.getCurrentHp() - secondBefore, expected * 1e-9,
                "「我方全体目标」 means the ones the caller did NOT aim at are healed too");
    }
}
