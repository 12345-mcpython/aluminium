package com.laosun.aluminium.test.content.characters.clara;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.buff.StackBuff;
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
 * 1107 克拉拉's Skill: 「对所有被史瓦罗标上【反击标记】的敌方目标额外造成 120% 攻击力物理伤害」.
 */
public class ClaraMarkedSkillTest {

    private static final int CLARA = 1107;
    private static final int LEVEL = 80;
    private static final String MARK = "反击标记";

    @Test
    public void onlyTheMarkedEnemiesTakeTheExtraHit() {
        Character clara = CharacterFactory.create(CLARA, LEVEL);
        Enemy marked = BattleFixture.monster();
        Enemy plain = BattleFixture.monster();
        Battle battle = new Battle(new ArrayList<>(List.of(clara, CharacterFactory.create(1001, LEVEL))),
                new ArrayList<>(List.of(marked, plain)), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        marked.getBuffManager().addBuff(new StackBuff(MARK, 1, true, 5));

        double markedBefore = marked.getCurrentHp();
        double plainBefore = plain.getCurrentHp();
        battle.castImmediate(clara.getSkills().get(SkillType.SKILL), clara, List.of(marked, plain));
        double markedLoss = markedBefore - marked.getCurrentHp();
        double plainLoss = plainBefore - plain.getCurrentHp();
        System.out.println("[1107] marked loss=" + markedLoss + " plain loss=" + plainLoss);
        Assertions.assertTrue(markedLoss > plainLoss, "「对标记目标**额外**造成一次伤害」");
    }
}