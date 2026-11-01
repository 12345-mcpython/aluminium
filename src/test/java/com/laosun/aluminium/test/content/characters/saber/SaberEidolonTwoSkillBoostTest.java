package com.laosun.aluminium.test.content.characters.saber;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.buff.StackBuff;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 1014 Saber's Eidolon 2, second half:「触发【炉心共鸣】提高战技倍率的效果时，每点【炉心共鸣】额外使本次战技的伤害倍率提高 7%」.
 *
 * <p>It rides the SAME branch as the 14%-per-point boost, so the check opens that branch (enough points to fill the bar) at
 * rank 1 and rank 2 and compares the settled damage.
 */
public class SaberEidolonTwoSkillBoostTest {

    private static final int SABER = 1014;
    private static final int LEVEL = 80;
    private static final String RESONANCE = "炉心共鸣";

    @Test
    public void theExtraSevenPercentPerPointRaisesTheSkill() {
        double atOne = skillDamage(1);
        double atTwo = skillDamage(2);
        System.out.println("[1014] skill through the resonance branch: E1 " + atOne + " ; E2 " + atTwo);
        Assertions.assertTrue(atTwo > atOne, "「每点额外使本次战技的伤害倍率提高 7%」");
    }

    /** The Skill's damage on a fresh target, with the resonance branch open at the given Eidolon rank. */
    private static double skillDamage(int eidolon) {
        Character saber = CharacterFactory.create(SABER, LEVEL, true, null, null, eidolon);
        Battle battle = new Battle(new ArrayList<>(List.of(saber)),
                new ArrayList<>(List.of(BattleFixture.dummy())), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        saber.setCurrentEnergy(0);
        for (int i = 0; i < 45; i++) {
            saber.getBuffManager().addBuff(new StackBuff(RESONANCE, 1, true, 60));
        }
        var target = battle.enemyUnits().getFirst();
        double before = target.getCurrentHp();
        battle.castImmediate(saber.getSkills().get(SkillType.SKILL), saber, List.of(target));
        battle.processRequests();
        return before - target.getCurrentHp();
    }
}