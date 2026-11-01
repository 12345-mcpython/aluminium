package com.laosun.aluminium.test.content.characters.luka;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.buff.StackBuff;
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
 * 1111 卢卡's Eidolons 3, 4 and 5: the two level bumps (data-driven, from {@code skill_add_level}) and 「每获得 1 层【斗志】，
 * 攻击力提高 5%，最多叠加 4 层」.
 *
 * <p>Note on the E4 measurement: he starts with ONE layer from his talent, so the panel already carries 0.05 x base at battle
 * start. The judge therefore measures the step each ADDITION makes, not the distance from the starting panel - comparing the
 * latter against "0.05 x layers" reads one layer short and looks like an engine defect when it is a baseline mistake.
 */
public class LukaEidolonTest {

    private static final int LEVEL = 80;
    private static final int LUKA = 1111;
    private static final String WILL = "斗志";

    @Test
    public void eidolonThreeAndFiveRaiseTheSkillLevels() {
        Battle e0 = battle(0);
        Battle e6 = battle(6);
        Assertions.assertEquals(level(e0, SkillType.SKILL) + 2, level(e6, SkillType.SKILL), "E3: 战技 +2");
        Assertions.assertEquals(level(e0, SkillType.TALENT) + 2, level(e6, SkillType.TALENT), "E3: 天赋 +2");
        Assertions.assertEquals(level(e0, SkillType.ULTRA) + 2, level(e6, SkillType.ULTRA), "E5: 终结技 +2");
        Assertions.assertEquals(level(e0, SkillType.COMMON) + 1, level(e6, SkillType.COMMON), "E5: 普攻 +1");
    }

    @Test
    public void eidolonFourPaysFivePercentPerLayerAndStopsAtFour() {
        Battle battle = battle(4);
        Character luka = battle.characters().getFirst();
        double base = luka.getAttribute(AttributeType.ATTACK).baseValue();
        int startLayers = luka.getBuffManager().stacksOf(WILL);
        Assertions.assertEquals(1, startLayers, "precondition: his talent gives one layer at battle start");

        // Each ADDITION before the cap is worth exactly one layer.
        for (int layer = startLayers + 1; layer <= 4; layer++) {
            double before = luka.getAttribute(AttributeType.ATTACK).get();
            addWill(luka, 1);
            Assertions.assertEquals(0.05 * base, luka.getAttribute(AttributeType.ATTACK).get() - before, 1e-6,
                    "layer " + layer + " of 「每获得 1 层【斗志】，攻击力提高 5%」");
        }

        // Past the cap nothing moves.
        double atCap = luka.getAttribute(AttributeType.ATTACK).get();
        addWill(luka, 3);
        System.out.println("[1111] layers=" + luka.getBuffManager().stacksOf(WILL) + " atk stays " + atCap);
        Assertions.assertEquals(atCap, luka.getAttribute(AttributeType.ATTACK).get(), 1e-9,
                "「最多叠加 4 层」");
    }

    // ==================================================================

    private static Battle battle(int eidolon) {
        Character luka = CharacterFactory.create(LUKA, LEVEL, true, null, null, eidolon);
        Battle battle = new Battle(new ArrayList<>(List.of(luka, CharacterFactory.create(1001, LEVEL))),
                new ArrayList<>(List.of(BattleFixture.monster())), BattleFixture.seeded());
        battle.startBattle();
        return battle;
    }

    private static int level(Battle battle, SkillType slot) {
        return battle.characters().getFirst().skillLevelBonus(slot);
    }

    private static void addWill(Character luka, int layers) {
        for (int i = 0; i < layers; i++) {
            luka.getBuffManager().addBuff(new StackBuff(WILL, 1, true, 99));
        }
    }
}
