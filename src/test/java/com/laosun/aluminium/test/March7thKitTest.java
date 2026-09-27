package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.data.TriggerTables;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.buff.DotBuff;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.models.skill.DefaultSkill;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 三月七 (1001) — the first character whose kit is mostly <b>blocked</b>, and what that looks like in the data.
 *
 * <p><b>Why her.</b> Her document is a wall of shields: a DEF-scaled shield, a taunt, a freeze, and a counter that
 * needs a shielded ally. This suite pins the one clause that is complete (行迹「纯洁」) and, just as deliberately, that
 * the rest is <b>not written</b> -- because every one of those clauses would be a wrong number or a wrong trigger if
 * it were approximated, and a census of what is missing is worth more than a file that looks finished.
 *
 * <p>⚠ The blocked clauses and the capability each one needs are listed in {@code characters/1001.json}'s note.
 */
public class March7thKitTest {
    private static final double EPS = 1e-6;

    /** 三月七 herself. */
    private static final int MARCH = 1001;
    /** 桂乃芬 — a plain ally to be cleansed; her own file is a relic-free character with no rules in play here. */
    private static final int ALLY = 1210;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final int SKILL_SLOT = 2;

    /**
     * 行迹「纯洁」: 「施放战技时，解除指定我方单体的1个负面效果」.
     *
     * <p>Driven through a real cast: her Skill is a `Defence` skill, so what makes this rule reach the right unit is
     * the cast event carrying the ally it was AIMED at (M-35).
     */
    @Test
    public void herSkillCleansesOneDebuffFromTheAimedAlly() {
        Character march = CharacterFactory.create(MARCH, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Battle battle = new Battle(List.of(march, ally), List.of(dummy()), new Random(0));
        battle.startBattle();
        ally.getBuffManager().addBuff(new DotBuff(march, DamageElement.WIND, 100, 3));
        Assertions.assertEquals(1, ally.getBuffManager().debuffCount(), "precondition: the ally carries one debuff");
        Assertions.assertEquals(0, march.getBuffManager().debuffCount(), "and she does not");

        battle.castImmediate(new DefaultSkill(MARCH, SKILL_SLOT, 1), march, List.of(ally));

        Assertions.assertEquals(0, ally.getBuffManager().debuffCount(), "「解除指定我方单体的1个负面效果」");
    }

    /**
     * 战技: the shield absorbs 「等同于三月七 <b>57% 防御力 + 760</b>」 — a share of HER DEFENCE plus a constant.
     *
     * <p>⚠ Asserted exactly, off her own DEFENCE as the engine resolves it: that is the whole point of
     * {@code scale: "owner_def"} -- a literal would be wrong for every build and every skill level.
     */
    @Test
    public void herSkillShieldsTheAimedAllyFromHerOwnDefence() {
        Character march = CharacterFactory.create(MARCH, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Battle battle = new Battle(List.of(march, ally), List.of(dummy()), new Random(0));
        battle.startBattle();
        double expected = 0.57 * march.getAttribute(com.laosun.aluminium.enums.AttributeType.DEFENCE).get() + 760;

        battle.castImmediate(new DefaultSkill(MARCH, SKILL_SLOT, 1), march, List.of(ally));

        Assertions.assertEquals(expected, ally.getShield(), EPS,
                "「抵消等同于三月七 57% 防御力 + 760 伤害的护盾」 -- the ally's shield, scaled off HER Defence");
        Assertions.assertEquals(0, march.getShield(), EPS, "and not on herself: 「指定我方单体」 is the ally");
    }

    /**
     * The rest of her kit is <b>registered, not approximated</b>.
     *
     * <p>A file that shipped the shield as a literal, or the counter without its 「每回合可触发2次」, would look
     * finished and be wrong; the counts here are the promise that nothing of the sort is in the data.
     */
    @Test
    public void theRestOfHerKitIsNotAuthored() {
        Assertions.assertEquals(2, TriggerTables.of(MARCH).ruleCount(TriggerEvent.SKILL_CAST),
                "the shield and the cleanse trace -- and nothing else on her Skill");
        Assertions.assertEquals(0, TriggerTables.of(MARCH).ruleCount(TriggerEvent.ULT_CAST),
                "the freeze needs a control state the engine does not model yet");
        Assertions.assertEquals(0, TriggerTables.of(MARCH).ruleCount(TriggerEvent.TAKING_HIT),
                "the counter needs \"the ally holds a shield\" and a per-turn trigger limit");
        Assertions.assertEquals(0, TriggerTables.of(MARCH).ruleCount(TriggerEvent.BATTLE_START),
                "the 星魂 2 shield rides on the DEF-scaled shield that has no spelling yet");
    }

    private static Enemy dummy() {
        return EnemyFactory.create(MONSTER, 90, 1);
    }
}
