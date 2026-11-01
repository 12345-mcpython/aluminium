package com.laosun.aluminium.test.content.characters.silverwolf;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 1006 银狼's Eidolon 2 (强化后), second half: 「当我方目标攻击敌方目标时，银狼有 100% 的基础概率给受到攻击的敌方目标植入 1 个
 * 随机【缺陷】」.
 *
 * <p>What is measured: after an ALLY attacks, the enemy holds exactly ONE of the three defect names - one, not all three,
 * which is what {@code random_of} means. ⚠ The first version of this test looked for the name on
 * {@code AbstractBuff.getBuffName()} and read zero: a state applied by {@code APPLY_BUFF} is not NAMED there, it is
 * looked up by {@code hasState} - the state DID land, the probe was looking in the wrong place.
 */
public class SilverWolfEidolonTwoDefectTest {

    private static final int WOLF = 1006;
    private static final int ALLY = 1001;
    private static final int LEVEL = 80;
    private static final List<String> DEFECTS = List.of("攻击力降低", "防御力降低", "速度降低");

    @Test
    public void anAllysAttackImplantsOneRandomDefect() {
        Assertions.assertEquals(1, implanted(2), "「植入 1 个随机【缺陷】」: exactly one of " + DEFECTS);
        Assertions.assertEquals(0, implanted(1), "control: nothing before Eidolon 2");
    }

    /** How many of the three defect names the enemy holds after an ally's attack, at the given Eidolon rank. */
    private static int implanted(int eidolon) {
        Character wolf = CharacterFactory.create(WOLF, LEVEL, true, null, null, eidolon);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(new ArrayList<>(List.of(wolf, ally)),
                new ArrayList<>(List.of(enemy)), BattleFixture.seeded());
        battle.startBattle();
        com.laosun.aluminium.skill.SkillExecutor.execute(battle, ally.getSkills().get(SkillType.COMMON), ally,
                List.of(enemy));
        battle.processRequests();
        int count = 0;
        for (String defect : DEFECTS) {
            if (enemy.getBuffManager().hasState(defect)) {
                count++;
            }
        }
        System.out.println("[1006] e" + eidolon + " defects held: " + count);
        return count;
    }
}