package com.laosun.aluminium.test.content.characters.luocha;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.buff.StateBuff;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 1203 罗刹's Eidolon 1: 「结界生效时我方全体攻击力提高 20%」 - the aura lives exactly while the zone does.
 */
public class LuochaEidolonOneTest {

    private static final int LUOCHA = 1203;
    private static final int ALLY = 1001;
    private static final int LEVEL = 80;
    private static final String ZONE = "结界";

    @Test
    public void theAuraFollowsTheZone() {
        Character luocha = CharacterFactory.create(LUOCHA, LEVEL, true, null, null, 1);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(new ArrayList<>(List.of(luocha, ally)),
                new ArrayList<>(List.of(enemy)), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();

        // The zone is opened by his Talent; the judge puts it up directly so the clause under test is the only variable.
        luocha.getBuffManager().addBuff(new StateBuff(ZONE, 2, false));
        battle.castImmediate(luocha.getSkills().get(SkillType.SKILL), luocha, List.of(ally));
        double whileUp = ally.getAttribute(AttributeType.ATTACK).get() / ally.getAttribute(AttributeType.ATTACK).baseValue();
        System.out.println("[1203] ally atk/base while the zone is up = " + whileUp);
        Assertions.assertTrue(whileUp > 1.19, "「结界生效时我方全体攻击力提高 20%」");

        // Close the zone and take a turn boundary: the gate drops the aura.
        luocha.getBuffManager().removeState(ZONE);
        luocha.getBuffManager().beforeMove();
        ally.getBuffManager().beforeMove();
        Assertions.assertEquals(1.0, ally.getAttribute(AttributeType.ATTACK).get()
                / ally.getAttribute(AttributeType.ATTACK).baseValue(), 1e-9, "结界结束，加成随之消失");
    }
}