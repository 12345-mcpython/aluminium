package com.laosun.aluminium.test.content.characters.cyrene;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.DoubleValue;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.entity.Summon;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.skill.SkillExecutor;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 德谬歌's 忆灵技 marks <b>the allies that have a variant of its buff</b> - which is how her Eidolon 2 counts them.
 *
 * <p><b>Why the mark exists and who gets it.</b> The Eidolon says 「每有 1 名不同的我方角色获得德谬歌忆灵技的增益效果…」, and the
 * game hands that buff out <b>per character</b>: {@code MServant_CyreneServant_00_AmazingBuff_Hyacine} to 风堇, {@code …_Evernight}
 * to 长夜月, the generic {@code …_AmazingBuff} to 昔涟 herself - and nothing to a character with no variant. The recorded fight
 * shows exactly three carriers (1409 / 1413 / 1415) with 1512 absent, which is that rule at work.
 *
 * <p>What this pins: the mark lands on those three and <b>not</b> on a fourth ally, so the count her Eidolon reads is three.
 */
public class CyreneOdeMarksTheAlliesTest {

    private static Enemy dummy() {
        Enemy enemy = Enemy.fromAttributes("target", 900000, 200, 100, 100);
        enemy.setAttribute(AttributeType.HEALTH, new DoubleValue(900000));
        enemy.heal(900000);
        return enemy;
    }

    private static Random noCrit() {
        return new Random(0) {
            @Override
            public double nextDouble() {
                return 0.99;
            }
        };
    }

    /**
     * Casting the memosprite's skill 1 marks the three allies that have a variant, and leaves the fourth alone.
     */
    @Test
    public void theMemospriteMarksOnlyTheAlliesThatHaveAVariant() {
        Character cyrene = CharacterFactory.create(1415, 80);
        Character hyacine = CharacterFactory.create(1409, 80);
        Character evernight = CharacterFactory.create(1413, 80);
        Character robin = CharacterFactory.create(1512, 80);
        Battle battle = new Battle(new ArrayList<>(List.of(cyrene, hyacine, evernight, robin)),
                new ArrayList<>(List.of(dummy())), noCrit());
        battle.startBattle();
        battle.processRequests();

        Summon demiurge = battle.summonMemosprite(cyrene);
        Assertions.assertNotNull(demiurge, "precondition: 德谬歌 is on the field");
        SkillExecutor.execute(battle, demiurge.skillAt(1), demiurge, List.of(battle.enemyUnits().get(0)));
        battle.processRequests();

        Assertions.assertTrue(hyacine.getBuffManager().hasState("德谬歌忆灵技增益"), "风堇 has a variant");
        Assertions.assertTrue(evernight.getBuffManager().hasState("德谬歌忆灵技增益"), "长夜月 has a variant");
        Assertions.assertTrue(cyrene.getBuffManager().hasState("德谬歌忆灵技增益"), "昔涟 carries the generic one");
        Assertions.assertFalse(robin.getBuffManager().hasState("德谬歌忆灵技增益"),
                "知更鸟•晴歌 has NO variant, so the count her Eidolon reads is three, not four");
    }
}
