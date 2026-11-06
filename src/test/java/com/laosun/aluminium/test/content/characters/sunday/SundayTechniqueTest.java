package com.laosun.aluminium.test.content.characters.sunday;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * 1313 星期日's 秘技 荣光之秘:「使用秘技后，下一次战斗星期日首次对我方目标施放技能时，使目标造成的伤害提高 50%，持续 2 回合」.
 *
 * <p>「首次」 has no rule-level switch (a per-turn one exists; a per-battle first does not), so the rule carries a marker
 * state laid at BATTLE_START and spends it with the first cast — and this test is what proves the marker is spent.
 */
public class SundayTechniqueTest {
    private static final int SUNDAY = 1313;
    private static final int ALLY = 1204;
    private static final int LEVEL = 80;

    @Test
    public void theTechniqueArmsOnlyTheFirstAbility() {
        Character sunday = CharacterFactory.create(SUNDAY, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        sunday.getBuffManager().addBuff(new com.laosun.aluminium.buff.StateBuff("秘技", 1));
        Battle battle = new Battle(List.of(sunday, ally), List.of(BattleFixture.dummy()), BattleFixture.seeded());
        battle.startBattle();

        battle.castImmediate(sunday.getSkills().get(SkillType.SKILL), sunday, List.of(ally));
        battle.processRequests();
        long afterFirst = shareModifiers(ally);
        Assertions.assertEquals(2, afterFirst,
                "「首次…使目标造成的伤害提高 50%」 on top of the Skill's own share");

        battle.castImmediate(sunday.getSkills().get(SkillType.SKILL), sunday, List.of(ally));
        battle.processRequests();
        Assertions.assertEquals(afterFirst, shareModifiers(ally),
                "…and the second cast adds nothing: the marker was spent");
    }

    /** How many ALL_DAMAGE_TYPE_BOOST modifiers the ally carries (the Skill's share and the technique's are one each). */
    private static long shareModifiers(Character ally) {
        return ally.getBuffManager().allBuffsOf(com.laosun.aluminium.buff.StatModifierBuff.class).stream()
                .filter(buff -> buff.getAttribute() == AttributeType.ALL_DAMAGE_TYPE_BOOST)
                .count();
    }
}
