package com.laosun.aluminium.test.content.characters.clara;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.SkillCategory;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.trigger.TriggerPayload;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 1107 克拉拉's Eidolon 6: 「终结技额外增加 1 次强化反击」 and 「我方其他目标遭到攻击后，史瓦罗有 50% 的固定概率发动反击」.
 */
public class ClaraEidolonSixTest {

    private static final int CLARA = 1107;
    private static final int LEVEL = 80;
    private static final String CHARGES = "强化反击";

    @Test
    public void theUltimateArmsThreeCountersAtEidolonSix() {
        Assertions.assertEquals(3, charges(6), "「终结技额外增加 1 次强化反击」");
        Assertions.assertEquals(2, charges(0), "control: two without the Eidolon");
    }

    @Test
    public void anotherAllyBeingHitCanTriggerTheCounter() {
        Character clara = CharacterFactory.create(CLARA, LEVEL, true, null, null, 6);
        Character ally = CharacterFactory.create(1001, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(new ArrayList<>(List.of(clara, ally)),
                new ArrayList<>(List.of(enemy)), BattleFixture.alwaysRoll(0.0));   // always roll 0 => the 50% chance lands
        battle.startBattle();
        double hp = enemy.getCurrentHp();
        battle.fire(new TriggerPayload.Cast(TriggerEvent.ATTACK_FINISHED, enemy, ally, 1, 0.0,
                SkillCategory.NORMAL, 0, 0, List.of(ally)));
        System.out.println("[1107] enemy hp " + hp + " -> " + enemy.getCurrentHp());
        Assertions.assertTrue(enemy.getCurrentHp() < hp, "「我方其他目标遭到攻击后…发动反击」");
    }

    @Test
    public void herOwnBeingHitIsTheTalentsJobNotThisOne() {
        Character clara = CharacterFactory.create(CLARA, LEVEL, true, null, null, 6);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(new ArrayList<>(List.of(clara, CharacterFactory.create(1001, LEVEL))),
                new ArrayList<>(List.of(enemy)), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        double hp = enemy.getCurrentHp();
        battle.fire(new TriggerPayload.Cast(TriggerEvent.ATTACK_FINISHED, enemy, clara, 1, 0.0,
                SkillCategory.NORMAL, 0, 0, List.of(clara)));
        Assertions.assertEquals(hp, enemy.getCurrentHp(), 1e-9,
                "「我方**其他**目标」 - her own hit is the Talent's clause, and that one is not unconditional");
    }

    private static int charges(int eidolon) {
        Character clara = CharacterFactory.create(CLARA, LEVEL, true, null, null, eidolon);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(new ArrayList<>(List.of(clara, CharacterFactory.create(1001, LEVEL))),
                new ArrayList<>(List.of(enemy)), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        battle.castImmediate(clara.getSkills().get(SkillType.ULTRA), clara, List.of(enemy));
        return clara.getBuffManager().stacksOf(CHARGES);
    }
}