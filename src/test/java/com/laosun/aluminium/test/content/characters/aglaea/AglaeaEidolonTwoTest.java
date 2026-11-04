package com.laosun.aluminium.test.content.characters.aglaea;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.entity.Summon;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillCategory;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * 1402 阿格莱雅's Eidolon 2:「当阿格莱雅或衣匠行动时，使阿格莱雅与衣匠造成的伤害无视目标 14% 的防御力，最多叠加 3 层，持续至
 * 除自身与衣匠以外的任意单位主动施放技能时」.
 *
 * <p>The layer count is measured as the DEFENCE_IGNORE attribute, which is what the damage formula reads: 1/2/3 actions
 * give 0.14/0.28/0.42 and a fourth action adds nothing (the cap). The clearing half is measured on the teammate's cast.
 */
public class AglaeaEidolonTwoTest {
    private static final int AGLAEA = 1402;
    private static final int LEVEL = 80;
    private static final String BUFF = "星魂二穿防";

    @Test
    public void everyActionAddsALayerUpToThree() {
        Assertions.assertEquals(0.14, defenceIgnoreAfter(2, 1), 1e-9, "one action ⇒ 14%");
        Assertions.assertEquals(0.28, defenceIgnoreAfter(2, 2), 1e-9, "two actions ⇒ 28%");
        Assertions.assertEquals(0.42, defenceIgnoreAfter(2, 3), 1e-9, "three actions ⇒ 42%");
        Assertions.assertEquals(0.42, defenceIgnoreAfter(2, 4), 1e-9, "「最多叠加 3 层」 — a fourth action adds nothing");
        Assertions.assertEquals(0.0, defenceIgnoreAfter(1, 3), 1e-9, "and nothing at all below E2");
    }

    /** 「衣匠行动时」 half: the memosprite's own turn start stacks HER copy, not Aglaea's. */
    @Test
    public void theMemospriteStacksItsOwnCopy() {
        Character aglaea = CharacterFactory.create(AGLAEA, LEVEL, false, null, null, 2);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(aglaea), List.of(enemy), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        Summon garmentmaker = battle.summonMemosprite(aglaea);
        battle.processRequests();
        double hersBefore = aglaea.getAttribute(AttributeType.DEFENCE_IGNORE).get();

        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(
                TriggerEvent.TURN_START, garmentmaker, garmentmaker, 0, 0));
        battle.processRequests();

        Assertions.assertEquals(0.14, garmentmaker.getAttribute(AttributeType.DEFENCE_IGNORE).get(), 1e-9,
                "「衣匠…行动时」 — the memosprite gets its own layer");
        Assertions.assertEquals(hersBefore, aglaea.getAttribute(AttributeType.DEFENCE_IGNORE).get(), 1e-9,
                "and her copy is untouched by the memosprite's action");
    }

    /** 「持续至除自身与衣匠以外的任意单位主动施放技能时」 — a teammate's cast clears BOTH copies. */
    @Test
    public void aTeammatesCastClearsBothCopies() {
        Character aglaea = CharacterFactory.create(AGLAEA, LEVEL, false, null, null, 2);
        Character ally = CharacterFactory.create(1001, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(aglaea, ally), List.of(enemy), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        Summon garmentmaker = battle.summonMemosprite(aglaea);
        battle.processRequests();
        for (int i = 0; i < 2; i++) {
            battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(
                    TriggerEvent.TURN_START, aglaea, aglaea, 0, 0));
            battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(
                    TriggerEvent.TURN_START, garmentmaker, garmentmaker, 0, 0));
        }
        Assertions.assertEquals(0.28, aglaea.getAttribute(AttributeType.DEFENCE_IGNORE).get(), 1e-9,
                "precondition: her copy is stacked");
        Assertions.assertEquals(0.28, garmentmaker.getAttribute(AttributeType.DEFENCE_IGNORE).get(), 1e-9,
                "precondition: so is the memosprite's");

        // A teammate actively casts a Skill. Her own casts must NOT clear it, so the fixture uses the ally's.
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Cast.of(TriggerEvent.SKILL_CAST, ally, enemy, 1, 0,
                SkillCategory.BPSKILL, 100102, 0, List.of(enemy)));
        battle.processRequests();

        Assertions.assertEquals(0.0, aglaea.getAttribute(AttributeType.DEFENCE_IGNORE).get(), 1e-9,
                "「除自身与衣匠以外的任意单位主动施放技能时」 — her copy is gone");
        Assertions.assertEquals(0.0, garmentmaker.getAttribute(AttributeType.DEFENCE_IGNORE).get(), 1e-9,
                "and so is the memosprite's");
    }

    /** Her own DEFENCE_IGNORE after {@code turnStarts} of her own turn starts, at the given Eidolon rank. */
    private static double defenceIgnoreAfter(int eidolon, int turnStarts) {
        Character aglaea = CharacterFactory.create(AGLAEA, LEVEL, false, null, null, eidolon);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(aglaea), List.of(enemy), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        for (int i = 0; i < turnStarts; i++) {
            battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(
                    TriggerEvent.TURN_START, aglaea, aglaea, 0, 0));
            battle.processRequests();
        }
        return aglaea.getAttribute(AttributeType.DEFENCE_IGNORE).get();
    }
}
