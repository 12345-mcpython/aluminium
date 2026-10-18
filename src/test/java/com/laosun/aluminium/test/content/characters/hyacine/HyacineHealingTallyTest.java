package com.laosun.aluminium.test.content.characters.hyacine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.skill.SkillExecutor;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * 1409 风堇's healing tally -- the multiplicand of 忆灵技1 【乌云乌云快走开！】:
 * 「对敌方全体造成等同于<b>本场战斗风堇与小伊卡累计治疗数值</b> #1% 的风属性伤害并清空累计治疗数值的 50%」.
 *
 * <p>This is only the <b>counting</b> half of that sentence, and it is worth its own test because the resource
 * existed before it was ever the total: 昔涟 (1415)'s 【献予「天空」之诗】 writes the <em>extra</em> share into the
 * same counter, so a reader that mistook that for the tally would read a bonus, not a sum.
 *
 * <p>为什么量的是「实际回复量」: the engine does not fire {@code HEALED} for a heal that heals nothing (already at
 * full HP), so the tally follows what was really restored -- which is the game's own wording (「累计治疗数值」), not
 * the nominal amount the skill would have healed.
 */
public class HyacineHealingTallyTest {

    private static final int HYACINE = 1409;
    private static final int ALLY = 1001;
    private static final String TALLY = "小伊卡累计治疗";

    @Test
    public void herHealingIsTallied() {
        Character hyacine = CharacterFactory.create(HYACINE, 80);
        Character ally = woundedAlly(80);
        Battle battle = battle(hyacine, ally);
        double wounded = ally.getCurrentHp();

        SkillExecutor.execute(battle, hyacine.getSkills().get(SkillType.SKILL), hyacine, List.of(ally));
        battle.processRequests();

        double healed = ally.getCurrentHp() - wounded;
        Assertions.assertTrue(healed > 0, "precondition: the skill healed something (否则 HEALED 不发)");
        // A Resource is an INTEGER counter, so the tally keeps whole HP: the error is below one point of healing,
        // i.e. below 0.28 of the damage the tally will be multiplied by (Lv10's #1).
        Assertions.assertEquals(Math.round(healed), hyacine.getResources().value(TALLY), 0.0,
                "「本场战斗风堇…累计治疗数值」 is the amount actually restored, kept as whole HP (healed=" + healed
                        + ", tally=" + hyacine.getResources().value(TALLY) + ")");
    }

    @Test
    public void aSecondHealAddsToTheSameTally() {
        Character hyacine = CharacterFactory.create(HYACINE, 80);
        Character ally = woundedAlly(80);
        Battle battle = battle(hyacine, ally);

        SkillExecutor.execute(battle, hyacine.getSkills().get(SkillType.SKILL), hyacine, List.of(ally));
        battle.processRequests();
        double afterFirst = hyacine.getResources().value(TALLY);

        ally.consumeHp(ally.getAttribute(AttributeType.HEALTH).get() * 0.5);
        SkillExecutor.execute(battle, hyacine.getSkills().get(SkillType.SKILL), hyacine, List.of(ally));
        battle.processRequests();
        double afterSecond = hyacine.getResources().value(TALLY);

        Assertions.assertTrue(afterFirst > 0, "precondition: the first heal was tallied");
        Assertions.assertTrue(afterSecond > afterFirst, "「累计」: the counter accumulates across heals ("
                + afterFirst + " -> " + afterSecond + ")");
    }

    @Test
    public void healingBySomeoneElseDoesNotEnterHerTally() {
        Character hyacine = CharacterFactory.create(HYACINE, 80);
        Character ally = CharacterFactory.create(ALLY, 80);
        Battle battle = new Battle(List.of(hyacine, ally), List.of(BattleFixture.dummy()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        ally.consumeHp(ally.getMaxHp() * 0.5);
        double wounded = ally.getCurrentHp();

        // 治疗者是**队友自己**，不是风堇、也不是她的忆灵 ⇒ 进的是别人的账。
        battle.heal(ally, ally, ally.getMaxHp() * 0.5);
        battle.processRequests();

        Assertions.assertTrue(ally.getCurrentHp() > wounded, "precondition: the heal really landed");
        Assertions.assertEquals(0.0, hyacine.getResources().value(TALLY), 1e-9,
                "「风堇与小伊卡累计治疗」 -- nobody else's healing counts");
    }

    private static Character woundedAlly(int level) {
        return CharacterFactory.create(ALLY, level);
    }

    private static Battle battle(Character hyacine, Character ally) {
        ally.consumeHp(ally.getAttribute(AttributeType.HEALTH).get() * 0.5);
        Battle battle = new Battle(List.of(hyacine, ally), List.of(BattleFixture.dummy()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        return battle;
    }
}
