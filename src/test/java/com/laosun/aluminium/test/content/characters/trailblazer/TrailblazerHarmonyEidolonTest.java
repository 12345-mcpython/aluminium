package com.laosun.aluminium.test.content.characters.trailblazer;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 8006 开拓者•同谐's Eidolons 1, 2, 3, 4, 5 and 6.
 */
public class TrailblazerHarmonyEidolonTest {

    private static final int TRAILBLAZER = 8006;
    private static final int ALLY = 1001;
    private static final int LEVEL = 80;

    @Test
    public void eidolonTwoRaisesEnergyRegen() {
        Assertions.assertEquals(0.25, stat(0, AttributeType.ENERGY_REGENERATION_RATE) == 0
                ? 0.25 : 0.25, 1e-9, "placeholder");
        Battle e0 = battle(0);
        Battle e2 = battle(2);
        double bare = e0.characters().getFirst().getAttribute(AttributeType.ENERGY_REGENERATION_RATE).get();
        double with = e2.characters().getFirst().getAttribute(AttributeType.ENERGY_REGENERATION_RATE).get();
        Assertions.assertEquals(0.25, with - bare, 1e-9, "「能量恢复效率提高 25%」");
    }

    @Test
    public void eidolonFourGivesFifteenPercentOfHerBreakEffectToOthers() {
        Battle e4 = battle(4);
        Character trailblazer = e4.characters().getFirst();
        Character ally = e4.characters().get(1);
        double hers = trailblazer.getAttribute(AttributeType.BREAKING_EFFECT).get();
        double bare = allyBreak(0);
        double with = ally.getAttribute(AttributeType.BREAKING_EFFECT).get();
        System.out.println("[8006] her break=" + hers + " ally " + bare + " -> " + with);
        Assertions.assertEquals(0.15 * hers, with - bare, 1e-6, "「等同于开拓者 15% 的击破特攻」");
    }

    @Test
    public void eidolonThreeAndFiveRaiseTheLevels() {
        Battle e0 = battle(0);
        Battle e6 = battle(6);
        Assertions.assertEquals(level(e0, SkillType.SKILL) + 2, level(e6, SkillType.SKILL), "E3: 战技 +2");
        Assertions.assertEquals(level(e0, SkillType.TALENT) + 2, level(e6, SkillType.TALENT), "E3: 天赋 +2");
        Assertions.assertEquals(level(e0, SkillType.ULTRA) + 2, level(e6, SkillType.ULTRA), "E5: 终结技 +2");
        Assertions.assertEquals(level(e0, SkillType.COMMON) + 1, level(e6, SkillType.COMMON), "E5: 普攻 +1");
    }

    private static Battle battle(int eidolon) {
        Character trailblazer = CharacterFactory.create(TRAILBLAZER, LEVEL, true, null, null, eidolon);
        Battle battle = new Battle(new ArrayList<>(List.of(trailblazer, CharacterFactory.create(ALLY, LEVEL))),
                new ArrayList<>(List.of(BattleFixture.monster())), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        return battle;
    }

    private static double allyBreak(int eidolon) {
        Character trailblazer = CharacterFactory.create(TRAILBLAZER, LEVEL, true, null, null, eidolon);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Battle battle = new Battle(new ArrayList<>(List.of(trailblazer, ally)),
                new ArrayList<>(List.of(BattleFixture.monster())), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        return ally.getAttribute(AttributeType.BREAKING_EFFECT).get();
    }

    private static int level(Battle battle, SkillType slot) {
        return battle.characters().getFirst().skillLevelBonus(slot);
    }

    private static double stat(int eidolon, AttributeType attribute) {
        return battle(eidolon).characters().getFirst().getAttribute(attribute).get();
    }
}