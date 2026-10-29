package com.laosun.aluminium.test.content.characters.hyacine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.data.TriggerTables;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1409 Hyacine, from her own file.
 *
 * <p>The rules cover only what a skill's data does NOT do. Measured this round: the HEAL op is exact (`owner_max_hp` 10% + 100 healed
 * 219.52864 against a declared 219.52864000000002 when isolated on TURN_START), while a skill also heals from its own data - so a rule that
 * repeats the document's heal double-counts it. Her state and the party Max HP raise are therefore the shipped clauses.
 */
public class HyacineTest {
    private static final int HYACINE = 1409;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;

    /**
     * Note: "风堇进入[雨过天晴]状态" and "我方全体目标生命上限提高30.00%".
     */
    @Test
    public void herUltimateMarksHerAndRaisesThePartysMaxHp() {
        Character hyacine = CharacterFactory.create(HYACINE, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(hyacine, ally), List.of(enemy), fixed());
        battle.startBattle();
        double allyMaxBefore = ally.getMaxHp();
        double herMaxBefore = hyacine.getMaxHp();

        battle.castImmediate(hyacine.getSkills().get(SkillType.ULTRA), hyacine, List.of(ally));

        Assertions.assertTrue(hyacine.getBuffManager().hasState("雨过天晴"),
                "「风堇进入【雨过天晴】状态」 (Hyacine enters the After Rain (【雨过天晴】) state)");
        Assertions.assertTrue(ally.getMaxHp() > allyMaxBefore, "「我方全体目标生命上限提高30.00%」 (all allies' Max HP is raised by 30.00%) -- ally");
        Assertions.assertTrue(hyacine.getMaxHp() > herMaxBefore, "「我方全体」 (all allies) includes Hyacine herself");
    }

    /**
     * Census: the ultimate's rule and the level convention; the heals belong to the skill data, not to rules.
     */
    @Test
    public void herFileCarriesTheClauses() {
        var table = TriggerTables.of(HYACINE);
        // Two now: her own ultimate, and the sky ode spending a layer on a ULT_CAST.
        // CORRECTED (2026-10-18): the ultimate also SUMMONS and HEALS as rules now - its data carries neither
        // (the skill is `Support`, so nothing heals, and no rule summoned 小伊卡 on a ULT_CAST). Four ULT_CAST rules:
        // the summon, the heals, the state/Max HP, and the sky ode spending a layer.
        Assertions.assertEquals(5, table.ruleCount(TriggerEvent.ULT_CAST), "summon, heals, state and Max HP, Rank 2's +50% Max HP, ode");
        // CORRECTED (2026-10-18): the level convention, Rank 3's +2, Rank 5's Skill/Talent +2, the technique, and now
        // the three clauses that only exist above 200 Speed - the trace's Max HP, the trace's healing share and Rank 4's
        // CRIT DMG. All three read `self_attr_above:SPEED:200`, so they belong to BATTLE_START: they are loadout facts.
        Assertions.assertEquals(7, table.ruleCount(TriggerEvent.BATTLE_START),
                "the level convention, Rank 3's +2, Rank 5's Skill/Talent +2, the technique, and the three Speed-200 clauses");
        // Note: Narrowed: the reason is about the HEAL, and the same skill also says "召唤忆灵 小伊卡".
        // Exactly one SKILL_CAST rule exists and it only summons -- the heal is still absent from the file, so nothing is
        // double-counted. What the pin protects is unchanged; what it counts is now what the document states.
        // Two now as well: the summon, and the sky ode spending a layer on a SKILL_CAST.
        Assertions.assertEquals(5, table.ruleCount(TriggerEvent.SKILL_CAST),
                "the summon rule the document states -- the fifth is the whole-party heal, which the data path could not state (a Restore heals only the caller's targets)");
        // Rank 6's aura, the trace's healing share on 小伊卡, Rank 4's CRIT DMG on 小伊卡, and 忆灵技3's two energy
        // clauses (15 on every arrival, 30 more on the first) -- all of them wait for the memosprite to enter the field.
        // Seven since 2026-10-18: Rank 6s aura also has to reach the memsprites that arrive AFTER 小伊卡
        // (eidolon_six_aura_covers_later_arrivals), which is a SUMMONED rule of its own.
        Assertions.assertEquals(7, table.ruleCount(TriggerEvent.SUMMONED),
                "Rank 6's RES PEN, the two memosprite halves that cannot be inherited, and 忆灵技3's 15 + first-time 30");
        // The talent's +80%/layer on 小伊卡, the two halves of 「风堇与小伊卡累计治疗」(忆灵技1's multiplicand), and
        // 1415 昔涟's extra share written into the same counter.
        Assertions.assertEquals(4, table.ruleCount(TriggerEvent.HEALED),
                "the talent's damage buff, 风堇's tally and 小伊卡's tally, plus 昔涟's extra share");
    }

    private static Random fixed() {
        return BattleFixture.alwaysRoll(1.0);
    }
}
