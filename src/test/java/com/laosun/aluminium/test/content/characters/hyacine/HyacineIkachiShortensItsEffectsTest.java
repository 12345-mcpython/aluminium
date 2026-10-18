package com.laosun.aluminium.test.content.characters.hyacine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Summon;
import com.laosun.aluminium.models.buff.StateBuff;
import com.laosun.aluminium.models.skill.SkillExecutor;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/** 1409 忆灵技2: 「小伊卡施放技能后使自身所有持续效果持续回合数减 1」. */
public class HyacineIkachiShortensItsEffectsTest {

    @Test
    public void itsOwnEffectsLoseExactlyOneTurnWhenItCasts() {
        Character hyacine = CharacterFactory.create(1409, 80);
        Battle battle = new Battle(List.of(hyacine, CharacterFactory.create(1001, 80)),
                List.of(BattleFixture.dummy()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        SkillExecutor.execute(battle, hyacine.getSkills().get(SkillType.ULTRA), hyacine,
                List.of(battle.enemies.getFirst()));
        battle.processRequests();
        Summon ika = (Summon) battle.memospriteOf(hyacine);
        Assertions.assertNotNull(ika, "precondition: 小伊卡 is out");
        ika.getBuffManager().addBuff(new StateBuff("测试持续效果", 3));

        SkillExecutor.execute(battle, hyacine.getSkills().get(SkillType.SKILL), hyacine,
                List.of(battle.enemies.getFirst()));
        battle.processRequests();

        // One turn off -- the cast's own -1, and NOTHING from the free turn it acted on (3 would have become 1
        // before the free-turn fix).
        Assertions.assertEquals(2, ika.getBuffManager().activeBuffs().getFirst().remainingTurns(),
                "「施放技能后使自身所有持续效果持续回合数减 1」: 3 -> 2");
    }
}
