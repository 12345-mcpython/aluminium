package com.laosun.aluminium.test.content.characters.luocha;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * 罗刹's 秘技 「愚者的悲悯」: "the next battle opens with the talent already triggered".
 *
 * <p>「使用秘技后，下一次战斗开始时立即触发天赋。」
 *
 * <p>⚠ <b>What the technique must produce.</b> The talent's own effect is that 【白花之刻】 reaching 2 stacks is spent to EXPAND
 * THE ZONE. So "trigger the talent" means the zone is already up when the battle opens -- and the zone has to be where the talent
 * puts it, on the ENEMIES, because both rules that read it ask `target has_state 结界` on the unit our attack was aimed at:
 *
 * <ul>
 *   <li>「处于结界中的任意<b>敌方目标</b>受到我方攻击后…」 ({@code talent_zone_heals_the_attacker})</li>
 *   <li>「处于结界中的<b>任意敌方目标</b>受到我方攻击后，除攻击者外的我方目标也会回复…」 ({@code trace_zone_heals_the_others})</li>
 * </ul>
 *
 * <p>⚠ A zone on 罗刹 himself satisfies neither, which is the defect this test exists for: it fired, and produced a 结界, and
 * healed nothing.
 */
public class LuochaTechniqueTest {

    private static final int LUOCHA = 1203;
    private static final int LEVEL = 80;

    /**
     * ⭐ The declared technique opens the battle with the zone already on the enemies.
     */
    @Test
    public void theDeclaredTechniqueExpandsTheZoneOnTheEnemies() {
        Battle battle = battle(true);
        for (Enemy enemy : battle.enemyUnits()) {
            Assertions.assertTrue(enemy.getBuffManager().hasState("结界"),
                    "「立即触发天赋」 -- the talent expands the zone, and it lands on the ENEMIES, which is where both "
                            + "«has_state 结界» rules read it; missing on " + enemy.getName());
        }
    }

    /**
     * ⭐ And the control: without the declaration the zone is not there.
     *
     * <p>Told apart from "the rule works", because a zone that appeared for some other reason would satisfy the case above.
     */
    @Test
    public void withoutTheTechniqueTheZoneIsNotThere() {
        Battle battle = battle(false);
        for (Enemy enemy : battle.enemyUnits()) {
            Assertions.assertFalse(enemy.getBuffManager().hasState("结界"),
                    "「使用秘技后」 -- with no technique declared nothing must open the zone");
        }
    }

    /**
     * ⭐ The zone is on the enemies, NOT on him.
     *
     * <p>⚠ Asserted negatively as well, because the defect was exactly a zone that existed in the wrong place: a case that only
     * asked "is there a 结界 anywhere" would have passed against the broken version.
     */
    @Test
    public void theZoneIsNotOnHim() {
        Battle battle = battle(true);
        Character luocha = battle.characters().get(0);
        Assertions.assertFalse(luocha.getBuffManager().hasState("结界"),
                "moving the zone onto Luocha is what made the technique heal nothing -- an ally never satisfies "
                        + "«target has_state 结界», because the target is the enemy our attack was aimed at");
    }

    /**
     * One battle with Luocha alone, optionally with his technique declared.
     *
     * @param technique whether he used it
     * @return the started battle
     */
    private static Battle battle(boolean technique) {
        Character luocha = CharacterFactory.create(LUOCHA, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(luocha), List.of(enemy), BattleFixture.alwaysRoll(1.0));
        if (technique) {
            battle.markTechniqueUsed(luocha);
        }
        battle.startBattle();
        return battle;
    }
}
