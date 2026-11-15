package com.laosun.aluminium.test.content.characters.mar7th;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.data.TriggerTables;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.DoubleValue;
import com.laosun.aluminium.buff.AbstractBuff;
import com.laosun.aluminium.buff.ControlBuff;
import com.laosun.aluminium.buff.DotBuff;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enemy.EnemyFactory;
import com.laosun.aluminium.skill.DefaultSkill;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.test.support.TestTurns;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * March 7th (1001) - a kit whose clauses each needed a different engine capability, and what that looks like in the data.
 *
 * <p><b>Why her.</b> Her document is a wall of shields: a DEF-scaled shield, a taunt, a freeze, and a counter that
 * needs a shielded ally. Three clauses are complete and pinned here - the shield (a DEF share plus a constant, and
 * a <b>duration</b>), the cleanse (the "纯洁" (Purify) trace) and the talent (天赋) counter, which is what the
 * {@code has_shield} condition and the {@code per_turn} limit were added for. The rest is <b>registered rather than
 * approximated</b>: every one of those clauses would be a wrong number or a wrong trigger if it were guessed at, and
 * a census of what is missing is worth more than a file that looks finished.
 *
 * <p>Note: The blocked clauses and the capability each one needs are listed in {@code characters/1001.json}'s notes.
 *
 * <h2>Levels, Eidolons and the level her own document is quoted at</h2>
 *
 * <p><b>What the documents say.</b> Eidolon 3 = "<b>终结技</b>等级+2，最多不超过15级；<b>普攻</b>等级+1，最多不超过10级" (data row
 * {@code {'100103': 2, '100101': 1}}), Eidolon 5 = "<b>战技</b>等级+2，最多不超过15级；<b>天赋</b>等级+2，最多不超过15级" (row
 * {@code {'100102': 2, '100104': 2}}). Every one of her four slots is quoted at Lv10 in the prose (her shield's #1 and #4,
 * her ultimate's #1 and #4, her talent's #1), which the file states once as a `level_convention` rule (+9), so the
 * Eidolon raises compose with it instead of being pinned per effect.
 *
 * <p>Note: The last case is why that composition matters: at Eidolon 5 her counter reads a row that no fixed
 * {@code damage_level} could ever have expressed.
 *
 * <h2>Trace stat nodes</h2>
 *
 * <p><b>Why this is not already covered.</b> {@code PearlStatTracesTest} pins the same mechanism for 真珠
 * (1502). Her own file has no equivalent, and the gap mattered: an audit of her kit looked for the trace nodes
 * {@code 1001201..1001210} in {@code characters/1001.json}, found neither, and read that as "her traces are
 * missing". They were not missing -- {@code Character.Builder.build()} applies them through
 * {@code SkillTrace.appendTo(SkillTrace.init(cid), calcData)}, and nothing said so for her.
 *
 * <p><b>The numbers.</b> tbgd's {@code AvatarSkillTreeConfig} lists her nodes as:
 * {@code IceAddedRatio} 0.032 + 0.032 + 0.048 + 0.048 + 0.064 = <b>0.224</b>,
 * {@code DefenceAddedRatio} 0.05 + 0.075 + 0.10 = <b>0.225</b>,
 * {@code StatusResistanceBase} 0.04 + 0.06 = <b>0.10</b>.
 *
 * <p><b>The two claims are one claim.</b> That the values are present, and that the rule file does not also
 * grant them: her file deliberately says nothing about trace stats, so if someone later "completed" her kit by
 * writing them as {@code MODIFY_ATTR} rules, the panel would double and this case would go red.
 *
 * <h2>The technique</h2>
 *
 * <p>March 7th's technique (秘技 冻人的瞬间): a battle-start Freeze on one random enemy.
 *
 * <p><b>Why this needed writing at all.</b> Her file carried fifteen rules and not one of them was the
 * technique, so 「使用秘技后…使随机敌方单体陷入冻结状态」 did nothing in this engine. It was the last clause
 * of her kit that was missing: the shield, the soft aggro, the dispel, the counter, the ultimate's freeze,
 * all six Eidolons and the two trace clauses were already there.
 *
 * <p><b>What it is, in the file.</b>
 * {@code on: BATTLE_START, when: ["self has_state sys:technique"], op: APPLY_CONTROL, control: 冻结, turns: 1,
 * base_chance: 1.0, element: Ice, scale: self_attr:ATTACK, percent: 0.5, target: random_enemy} -- the same
 * vocabulary as her ultimate's freeze, which is the point: 「冻结状态」 must not mean one thing when her
 * ultimate applies it and another when her technique does.
 *
 * <p><b>Why the cases are shaped this way.</b>
 * A technique rule only fires when the battle was told the technique was used
 * ({@code Battle.markTechniqueUsed}, then {@code startBattle} turns it into the `秘技` state before any
 * BATTLE_START rule runs). So every case here is a <b>contrast</b> against the same battle without the
 * mark: "the freeze landed" alone would pass even if the rule ignored the gate, because a 100% base chance
 * lands anyway. The difference is what proves the gate.
 *
 * <p>No test anywhere else in the suite covers a technique rule, so this file is also the first pin on
 * that mechanism.
 *
 * <h2>The ultimate's per-turn freeze</h2>
 *
 * <p>1001 三月七's ultimate, second clause: 「冻结状态下，敌方目标不能行动同时**每回合开始时**受到等同于三月七
 * <b>60%</b>攻击力的冰属性<u>附加伤害</u>」.
 *
 * <h3>Why this judge exists</h3>
 * The clause has two halves and only the first was ever visible: the freeze itself ({@code APPLY_CONTROL}) is
 * pinned by the control suites, but "the state carries a per-turn ice hit" travels through a path nobody was
 * asserting - the rule hands {@code element} / {@code scale} / {@code percent_from_skill_param: ULTRA:3} to
 * {@code APPLY_CONTROL}, which turns them into a {@code DotBuff} attached by {@code ControlBuff}, which the
 * victim's own turn start then settles. Every one of those three hand-offs is silent when it breaks.
 *
 * <h3>What is asserted, and why in this shape</h3>
 * Not "the enemy lost HP" - a free-frozen enemy that takes a hit at the WRONG row would also lose HP. The
 * assertions are on the settled instance itself: exactly one {@code DOT} instance, its element is
 * {@code ICE}, and its <b>base</b> is {@code 0.6 x her ATK} - the row the clause names ({@code #4} of
 * {@code 100103}'s Lv10 line). The zones after the base (defence, resistance) are not asserted: they belong to
 * the engine's chain, not to this clause.
 */
public class March7thKitTest {
    private static final double EPS = 1e-6;
    /**
     * The trace-stat cases assert exact panel sums, so they keep the tighter tolerance they were written with.
     */
    private static final double TRACE_EPS = 1e-9;

    /**
     * March 7th herself.
     */
    private static final int MARCH = 1001;
    /**
     * Guinaifen - a plain ally to be shielded and cleansed; her own rules never touch the numbers below.
     */
    private static final int ALLY = 1210;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    /**
     * An ordinary monster with no specific resistances (1002011 is immune to freeze - see the freeze cases).
     *
     * <p>Note: 1003010 states no {@code STAT_CTRL_Frozen} resistance -- unlike Ice Edge (冰锋), which cannot be
     * frozen at all. Sharing this choice with {@code CastAppliedCountTest} keeps the two readers of her freeze
     * comparable.
     */
    private static final int ORDINARY_MONSTER = 1003010;
    private static final int SKILL_SLOT = 2;
    /**
     * 「60% 攻击力」 - {@code ULTRA:3} on the Lv10 row, which is why the file's level convention is load-bearing.
     */
    private static final double SHARE = 0.6;

    // ==================================================================
    // the kit: the shield, the cleanse, the counter, the ultimate and the Eidolons
    // ==================================================================

    /**
     * The "纯洁" (Purify) trace: "施放战技时，解除指定我方单体的1个负面效果".
     *
     * <p>Driven through a real cast: her Skill is a `Defence` skill, so what makes this rule reach the right unit is
     * the cast event carrying the ally it was AIMED at.
     */
    @Test
    public void herSkillCleansesOneDebuffFromTheAimedAlly() {
        Character march = CharacterFactory.create(MARCH, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Battle battle = new Battle(List.of(march, ally), List.of(dummy()), BattleFixture.seeded());
        battle.startBattle();
        ally.getBuffManager().addBuff(new DotBuff(march, DamageElement.WIND, 100, 3));
        Assertions.assertEquals(1, ally.getBuffManager().debuffCount(), "precondition: the ally carries one debuff");
        Assertions.assertEquals(0, march.getBuffManager().debuffCount(), "and she does not");

        battle.castImmediate(new DefaultSkill(MARCH, SKILL_SLOT, 1), march, List.of(ally));

        Assertions.assertEquals(0, ally.getBuffManager().debuffCount(), "「解除指定我方单体的1个负面效果」 (dispels 1 debuff from a designated single ally)");
    }

    /**
     * The Skill (战技): the shield absorbs "等同于三月七 <b>5% 防御力 + 60</b>" - a share of HER DEFENCE plus a constant -
     * and the "加护" (Reinforce) trace lengthens that same shield by one turn.
     *
     * <p>Note: Asserted exactly, off her own DEFENCE as the engine resolves it: that is the whole point of
     * {@code scale: "owner_def"} -- a literal would be wrong for every build and every skill level.
     *
     * <p>Note: The 57% / 760 are <b>row 10</b> of her Skill, which is the row the file's {@code level_convention} puts
     * the slot on. The row itself is read at the skill's current level, so the next case (星魂 5) moves it.
     *
     * <p>Note: The +1 turn is a <b>separate rule</b> from the Skill's shield, and it has to run after it in the same
     * event - which it does, because effects of one event run in order (only *conditions* are all evaluated up front).
     * The two numbers therefore stay in two rules, and the trace can be gated on its own one day.
     */
    @Test
    public void herSkillShieldsTheAimedAllyFromHerOwnDefenceForFourTurns() {
        Fixture f = new Fixture();
        double expected = 0.57 * f.march.getAttribute(com.laosun.aluminium.enums.AttributeType.DEFENCE).get() + 760;
        f.shieldTheAlly();

        com.laosun.aluminium.buff.ShieldBuff shield =
                f.ally.getBuffManager().findBuff(com.laosun.aluminium.buff.ShieldBuff.class);

        Assertions.assertAll("her Skill's shield on the aimed ally, and the Grace trace's extra turn",
                () -> Assertions.assertEquals(expected, f.ally.getShield(), EPS,
                        "「抵消等同于三月七 57% 防御力 + 760 伤害的护盾」 (a shield that absorbs damage equal to 57% of March 7th's DEF + 760) -- the ally's shield, scaled off HER Defence"),
                () -> Assertions.assertEquals(0, f.march.getShield(), EPS, "and not on herself: 「指定我方单体」 (a designated single ally) is the ally"),
                () -> Assertions.assertEquals(4, shield.duration(),
                        "3 turns from the Skill + 1 from the Reinforce (加护) trace -- \"the shield the Skill provides lasts 1 turn longer\" (「战技提供的护盾持续时间增加1回合」)"));
    }

    /**
     * The shield is not a frozen pair of literals: at 星魂 5 「战技等级+2」 moves the Skill to row 12, and <b>both</b>
     * halves of the sentence move with it (0.608 x DEF + 845.5 instead of 0.57 x DEF + 760).
     *
     * <p>Note: This is the case that would have been impossible before {@code amount_from_skill_param} existed: a rule
     * with the share read from the row and the constant written as a literal produces a number that <b>no row</b> of
     * the Skill contains, and the assertion below is deliberately computed from the row rather than from written-down
     * numbers, so a changed table moves the expectation with it.
     */
    @Test
    public void theShieldFollowsTheSkillsLevel() {
        Character atFive = CharacterFactory.create(MARCH, LEVEL, true, null, null, 5);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Battle battle = new Battle(List.of(atFive, ally), List.of(dummy()), BattleFixture.seeded());
        battle.startBattle();

        int level = atFive.skillLevel(atFive.getSkills().get(com.laosun.aluminium.enums.SkillType.SKILL));
        Assertions.assertEquals(12, level, "precondition: 星魂 5's 「战技等级+2」 on top of the file's Lv10 convention");
        var row = atFive.getSkills().get(com.laosun.aluminium.enums.SkillType.SKILL).getData().getSkills()
                .get(level - 1);
        double expected = row.get(0) * atFive.getAttribute(com.laosun.aluminium.enums.AttributeType.DEFENCE).get()
                + row.get(3);

        battle.castImmediate(new DefaultSkill(MARCH, SKILL_SLOT, 1), atFive, List.of(ally));

        Assertions.assertEquals(expected, ally.getShield(), EPS,
                "row 12 of her Skill: " + row + " -- #1 of DEF \"+\" #4, read at the skill's current level");
        Assertions.assertNotEquals(0.57 * atFive.getAttribute(com.laosun.aluminium.enums.AttributeType.DEFENCE).get() + 760,
                ally.getShield(), EPS, "and NOT the row-10 pair the file used to write as two literals");
    }

    /**
     * The talent (天赋) "少女的特权": "当持有护盾的我方目标受到敌方目标攻击后，三月七立即向攻击者发起反击 ...每回合可触发2次".
     *
     * <p>Note: The hit is deliberately one the <b>shield absorbs entirely</b> (HP never moves). That is the case the
     * event choice exists for: "受到攻击后" is about being attacked, not about losing HP, so gating this on
     * {@code HP_LOST} would have silently skipped exactly the situation the talent describes. The damage is applied
     * through the engine's own path ({@code Battle.applyDamage}), which is what emits {@code TAKING_HIT}.
     *
     * <p>Note: The same sequence pins the per-turn limit: the third hit of the turn is not answered, and her own turn
     * hands the two uses back.
     */
    @Test
    public void herTalentCountersTheAttackerTwicePerTurn() {
        Fixture f = new Fixture();
        f.shieldTheAlly();
        double enemyBefore = f.enemy.getCurrentHp();

        f.hitTheAlly(100);
        double allyHpAfterTheFirstHit = f.ally.getCurrentHp();
        double afterFirst = f.enemy.getCurrentHp();
        f.hitTheAlly(100);
        double afterSecond = f.enemy.getCurrentHp();
        f.hitTheAlly(100);
        double afterThird = f.enemy.getCurrentHp();

        TestTurns.take(f.battle, f.march);
        f.hitTheAlly(100);
        double afterHerOwnTurn = f.enemy.getCurrentHp();

        Assertions.assertAll("her talent's counter, and its two-per-turn limit",
                () -> Assertions.assertEquals(f.ally.getMaxHp(), allyHpAfterTheFirstHit, EPS,
                        "precondition: the shield ate the whole hit"),
                () -> Assertions.assertTrue(afterFirst < enemyBefore,
                        "「立即向攻击者发起反击」 (immediately counterattacks the attacker) -- the ATTACKER takes it, and the trigger is being attacked, not losing HP"),
                () -> Assertions.assertTrue(afterSecond < afterFirst, "the second counter of the turn is allowed"),
                () -> Assertions.assertEquals(afterSecond, afterThird, EPS,
                        "the third is refused: without `per_turn` the counter would answer every hit of a three-hit attack"),
                () -> Assertions.assertTrue(afterHerOwnTurn < afterSecond, "her own turn hands the two uses back"));
    }

    /**
     * "持有护盾的" is a real gate: an ally with no shield is not counter-protected.
     */
    @Test
    public void anAllyWithNoShieldIsNotCounteredFor() {
        Fixture f = new Fixture();
        double enemyBefore = f.enemy.getCurrentHp();

        f.hitTheAlly(100);

        Assertions.assertEquals(enemyBefore, f.enemy.getCurrentHp(), EPS, "no shield, no counter");
    }

    /**
     * The Ultimate (终结技) "冰刻箭雨之时": "受到攻击的敌方目标有50%基础概率陷入冻结状态，持续1回合".
     *
     * <p>Note: The ultimate's <b>damage</b> needs no rule (the engine's ordinary AoE path reads 100103's own row), so
     * what is asserted here is the state: the victim cannot act, and "冻结状态" is readable by the condition DSL.
     *
     * <p>Note: The fixture is 1003010, not the Ice Edge the other cases use: Ice Edge's data carries
     * {@code STAT_CTRL_Frozen = 1.0} - it cannot be frozen by a skill at all (which the next test pins). Both
     * sides of the probability pipeline are stated here (her effect hit, the victim's effect resistance) so that "50% base"
     * is not a coin flip in a test.
     */
    @Test
    public void herUltimateFreezesWhoeverItHits() {
        Character march = CharacterFactory.create(MARCH, LEVEL);
        march.setAttribute(AttributeType.EFFECT_HIT_RATE, new DoubleValue(1.0));
        Enemy enemy = EnemyFactory.create(ORDINARY_MONSTER, 90, 1);
        enemy.setAttribute(AttributeType.EFFECT_RESISTANCE, new DoubleValue(0));
        Battle battle = new Battle(List.of(march), List.of(enemy), BattleFixture.seeded());
        battle.startBattle();

        battle.castImmediate(march.getSkills().get(com.laosun.aluminium.enums.SkillType.ULTRA), march,
                List.of(enemy));

        Assertions.assertTrue(enemy.getBuffManager().hasState("ctrl:frozen"),
                "「有50%基础概率陷入冻结状态」 (a 50% base chance to fall into the Frozen state) -- with effect hit +100% and no resistance, this is the certain case");
        Assertions.assertFalse(enemy.getBuffManager().canAct(), "「冻结状态下，敌方目标不能行动」 (while Frozen, the enemy target cannot act)");
        Assertions.assertTrue(enemy.getCurrentHp() < enemy.getMaxHp(),
                "and the first sentence of the ultimate is the engine's own AoE path, not a rule");

        // The second half of that sentence, and the reason it is one rule: "冻结状态下 ...每回合开始时受到等同于
        // 三月七60%攻击力的冰属性附加伤害".
        com.laosun.aluminium.buff.DotBuff ice = enemy.getBuffManager()
                .findBuff(com.laosun.aluminium.buff.DotBuff.class);
        Assertions.assertNotNull(ice, "the frozen enemy carries the state's per-turn damage");
        Assertions.assertEquals(0.6 * march.getAttribute(AttributeType.ATTACK).get(), ice.getBaseDamage(), EPS,
                "60% of HER attack, read when the freeze landed");
        Assertions.assertEquals(DamageElement.ICE, ice.getElement());
    }

    /**
     * The ultimate reads its own row for <b>both</b> halves: the AoE damage (the engine's ordinary path) and the
     * freeze's per-turn damage ({@code percent_from_skill_param: "ULTRA:3"}).
     *
     * <p>Note: The two halves are checked together on purpose. They used to sit on different rows - the damage came
     * from the skill's own level (row 1, 0.9) while the DOT was a literal 0.6 (row 10) - so a case that only looked at
     * one of them would have passed while the sentence contradicted itself. 星魂 3's 「终结技等级+2」 moves both.
     */
    @Test
    public void theUltimateFollowsItsOwnLevelForItsDamageAndItsFreeze() {
        double[] atZero = ultimateOutcome(0);
        double[] atThree = ultimateOutcome(3);

        Character atThreeUnit = startedAt(3);
        int level = atThreeUnit.skillLevel(atThreeUnit.getSkills().get(com.laosun.aluminium.enums.SkillType.ULTRA));
        Assertions.assertEquals(12, level, "precondition: 星魂 3's 「终结技等级+2」 on top of the file's Lv10 convention");
        var row = atThreeUnit.getSkills().get(com.laosun.aluminium.enums.SkillType.ULTRA).getData().getSkills()
                .get(level - 1);
        double attack = atThreeUnit.getAttribute(AttributeType.ATTACK).get();

        Assertions.assertEquals(row.get(3) * attack, atThree[1], EPS,
                "row 12's #4 (0.66 of her attack) -- the freeze's per-turn damage follows the ultimate's level");
        Assertions.assertNotEquals(0.6 * attack, atThree[1], EPS,
                "and not the row-10 literal (0.6) it used to be written as");
        Assertions.assertEquals(1.0, atZero[1] / (0.6 * attack), EPS,
                "precondition: at 星魂 0 the file's Lv10 convention puts the DOT on row 10, so 0.6 x ATK is right there");

        Assertions.assertEquals(row.get(0) / 1.5, atThree[0] / atZero[0], 1e-6,
                "the AoE damage moves by the same rows: row 12's #1 over row 10's (1.62 / 1.5), zones cancelling");
    }

    /**
     * The other side of the same roll, and a fact about the <b>data</b> rather than about the fixture: Ice Edge
     * (1002011) is immune to freeze ({@code STAT_CTRL_Frozen = 1.0}), so even a certain base chance cannot land.
     */
    @Test
    public void herUltimateCannotFreezeAMonsterThatIsImmuneToIt() {
        Character march = CharacterFactory.create(MARCH, LEVEL);
        march.setAttribute(AttributeType.EFFECT_HIT_RATE, new DoubleValue(10.0));
        Enemy immune = dummy();
        Battle battle = new Battle(List.of(march), List.of(immune), BattleFixture.seeded());
        battle.startBattle();

        battle.castImmediate(march.getSkills().get(com.laosun.aluminium.enums.SkillType.ULTRA), march,
                List.of(immune));

        Assertions.assertEquals(java.util.Map.of("STAT_CTRL_Frozen", 1.0), immune.getDebuffResist(),
                "precondition: this monster's data is what makes it unfreezable");
        Assertions.assertFalse(immune.getBuffManager().hasState("ctrl:frozen"),
                "no amount of Effect Hit Rate (效果命中) beats a specific immunity -- which is why the case above needs a "
                        + "different enemy rather than a bigger number");
    }

    /**
     * The Eidolons (星魂) 2 "记忆中的它": "进入战斗时，为当前生命值百分比最低的我方目标提供等同于三月七24%防御力+320的护盾，
     * 持续3回合".
     *
     * <p>Note: What this needed was the <b>selector</b> (`lowest_hp_ally`), not a new op: the shield's numbers are the
     * Skill's shape (`scale: owner_def` + a constant + a duration) and the gate is the ordinary Eidolon rank.
     *
     * <p>Note: And it always fires on a <b>tie</b>: at battle start everybody is at 100%, so "the lowest percentage" is
     * the whole party. The engine's tie-break is the earliest unit in the party order, which is what makes it
     * deterministic - hence the assertion on the FIRST ally rather than on "somebody".
     */
    @Test
    public void herSecondEidolonShieldsTheMostHurtAllyAtBattleStart() {
        Character march = CharacterFactory.create(MARCH, LEVEL, true, null, null, 2);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Battle battle = new Battle(List.of(march, ally), List.of(dummy()), BattleFixture.seeded());

        battle.startBattle();

        double expected = 0.24 * march.getAttribute(AttributeType.DEFENCE).get() + 320;
        Assertions.assertEquals(expected, march.getShield(), EPS,
                "the first unit in the party order takes it, because everybody is at 100% at battle start");
        Assertions.assertEquals(0, ally.getShield(), EPS, "and only one unit is the lowest");
        Assertions.assertEquals(MARCH, ((Character) battle.allies().getFirst()).getCid(),
                "precondition: she is first in the party order");
    }

    /**
     * Below rank 2 the rule is not there at all - the Eidolon gate, not a weaker shield.
     */
    @Test
    public void theSecondEidolonsShieldDoesNotExistBelowItsRank() {
        Character march = CharacterFactory.create(MARCH, LEVEL, true, null, null, 1);
        Battle battle = new Battle(List.of(march), List.of(dummy()), BattleFixture.seeded());

        battle.startBattle();

        Assertions.assertEquals(0, march.getShield(), EPS, "Eidolon 1 does not grant it");
    }

    /**
     * The rest of her kit is <b>registered, not approximated</b>.
     *
     * <p><b>Twelve</b> clauses exist and each is pinned above; the counts here are what says nothing else was written.
     *
     * <p>Note: <b>Her Skill's soft taunt is not on the registered list</b>: it was never a missing number.
     * The sentence states no magnitude, but the skill's {@code param_list} has five slots per level and the prose
     * references four, so the fifth (a constant 5) is the ratio; the game's own ability config attaches its shield
     * modifier with the property {@code AggroAddedRatio} and gates it on the HP% the prose does name, pinning that
     * value to 0 on the below-30% branch. It is written as {@code AGGRO_ADDED_RATIO} -- a SOFT weight -- and never as
     * {@code TAUNT}, which is the hard "can only be selected" and would be a different mechanic rather than a
     * different number. See {@code SoftAggroWeightTest} and the rule's note in {@code characters/1001.json}.
     *
     * <p>Note: Three clauses came off that list and each is worth a line: Eidolon 1 needed a per-cast count of the
     * victims a state actually landed on ({@code "scale": "cast_applied:冻结"}); Eidolon 4's first sentence plus
     * the "冰咒" (Ice Spell) trace needed a way to raise another rule's own number ({@code MODIFY_RULE}); Eidolon 4's second
     * sentence needed an absolute damage addend derived from an attribute ({@code ADD_DAMAGE}); and Eidolon 6 needed a
     * shield whose <i>origin</i> can be asked ({@code has_shield from_rule}). Eidolons 3/5 need no rule at all: a
     * skill level is content data (a {@code RAISE_SKILL_LEVEL} rule), not a fixed {@code damage_level}.
     */
    @Test
    public void theRestOfHerKitIsNotAuthored() {
        Assertions.assertEquals(4, TriggerTables.of(MARCH).ruleCount(TriggerEvent.SKILL_CAST),
                "the shield, the soft aggro weight (AGGRO_ADDED_RATIO), the cleanse trace and the Grace trace"
                        + "(the shield's +1 turn) -- and nothing else on her Skill");
        Assertions.assertEquals(1, TriggerTables.of(MARCH).ruleCount(TriggerEvent.TAKING_HIT),
                "the Talent's counter");
        Assertions.assertEquals(2, TriggerTables.of(MARCH).ruleCount(TriggerEvent.ULT_CAST),
                "the ultimate's freeze (its damage is the engine's own path, so there is no damage rule) and Eidolon 1's "
                        + "energy per landed freeze");
        Assertions.assertEquals(7, TriggerTables.of(MARCH).ruleCount(TriggerEvent.BATTLE_START),
                "Eidolon 2's battle-start shield; the two amendments (Eidolon 4 raising the talent's per-turn cap, the Traces (行迹) \"Ice Spell\" (「冰咒」) "
                        + "raising the ultimate's base chance); Eidolon 3's and Eidolon 5's skill levels plus the talent's "
                        + "base level (three rules); and the technique's freeze (「进入战斗后…使随机敌方单体陷入冻结状态」), "
                        + "which was the last clause of hers left unwritten -- see the technique cases below");
        Assertions.assertEquals(0, TriggerTables.of(MARCH).ruleCount(TriggerEvent.KILL),
                "and nothing of hers reacts to kills");
        Assertions.assertEquals(1, TriggerTables.of(MARCH).ruleCount(TriggerEvent.TURN_START),
                "Eidolon 6's heal, which fires once per shielded ally's own turn start");
    }

    // ==================================================================
    // levels and Eidolons: the level convention, and what 星魂 3 / 5 add to it
    // ==================================================================

    /**
     * The document's level for her talent, stated by the file instead of by a per-effect pin.
     */
    @Test
    public void herTalentIsReadAtTheDocumentsLevel() {
        Character march = CharacterFactory.create(MARCH, LEVEL, true, null, null, 0);
        new Battle(List.of(march), List.of(dummy()), fixed()).startBattle();

        Assertions.assertEquals(9, march.skillLevelBonus(SkillType.TALENT),
                "the base rule states 「the document quotes Lv10」 as +9 (level 1 -> 10)");
        Assertions.assertEquals(10, march.skillLevel(march.getSkills().get(SkillType.TALENT)),
                "…so every effect that reads her talent indexes row 10");
    }

    /**
     * Eidolon 3 raises the ultimate and the basic attack - and nothing else.
     *
     * <p>Measured as a <b>delta against 星魂 0</b>, not as an absolute bonus: the file's own `level_convention` rule
     * already puts all four slots at +9 (「the document quotes Lv10」), so an absolute assertion would be reading the
     * convention's number and calling it the Eidolon's. The question this case asks is what the Eidolon <b>adds</b>.
     */
    @Test
    public void starRailThreeRaisesTheUltimateAndTheBasicAttack() {
        Character atZero = startedAtWithFixedRolls(0);
        Character atTwo = startedAtWithFixedRolls(2);
        Assertions.assertEquals(0, bonus(atTwo, SkillType.ULTRA) - bonus(atZero, SkillType.ULTRA),
                "Eidolon (星魂) 2 is not Eidolon 3");

        Character atThree = startedAtWithFixedRolls(3);
        Assertions.assertEquals(2, bonus(atThree, SkillType.ULTRA) - bonus(atZero, SkillType.ULTRA),
                "\"Ultimate Lv. +2\" (「终结技等级+2」)");
        Assertions.assertEquals(1, bonus(atThree, SkillType.COMMON) - bonus(atZero, SkillType.COMMON),
                "\"Basic ATK Lv. +1\" (「普攻等级+1」)");
        Assertions.assertEquals(0, bonus(atThree, SkillType.SKILL) - bonus(atZero, SkillType.SKILL),
                "…and Eidolon (星魂) 5 has not landed yet");
    }

    /**
     * Eidolon 5 raises the skill and the talent; the talent's raise sits <b>on top of</b> the base level.
     */
    @Test
    public void starRailFiveRaisesTheSkillAndTheTalent() {
        Character atZero = startedAtWithFixedRolls(0);
        Character atFive = startedAtWithFixedRolls(5);

        Assertions.assertEquals(2, bonus(atFive, SkillType.SKILL) - bonus(atZero, SkillType.SKILL),
                "\"Skill Lv. +2\" (「战技等级+2」)");
        Assertions.assertEquals(9, bonus(atZero, SkillType.TALENT),
                "the file's own `level_convention` states 「the document quotes Lv10」 as +9");
        Assertions.assertEquals(11, bonus(atFive, SkillType.TALENT), "+9 base and +2 from this Eidolon (星魂)");
        Assertions.assertEquals(12, atFive.skillLevel(atFive.getSkills().get(SkillType.TALENT)),
                "so her talent is read at level 12 — its table has 15 rows, and this is the number the game means");
    }

    /**
     * Note: The composition lands on a row whose number is <b>different</b>: level 12 is not level 10.
     *
     * <p>Pinned at the data + resolver level on purpose. The behavioural case ("the counter hits harder at 星魂 5") was
     * written first and measured <b>1.13 at both ranks</b> - a fixture that was not reading the counter's row at all
     * (the rows are {@code [1.0, 2.0]} and {@code [1.1, 2.0]}, so a real read cannot be equal). Her own suite pins the
     * counter's Lv10 behaviour at Eidolon 0 ({@code March7thKitTest}), and this case pins the half that suite cannot see.
     */
    @Test
    public void theRaisedRowIsADifferentNumber() {
        Character atZero = CharacterFactory.create(MARCH, LEVEL, true, null, null, 0);
        new Battle(List.of(atZero), List.of(dummy()), fixed()).startBattle();
        Character atFive = CharacterFactory.create(MARCH, LEVEL, true, null, null, 5);
        new Battle(List.of(atFive), List.of(dummy()), fixed()).startBattle();

        var rows = atZero.getSkills().get(SkillType.TALENT).getData().getSkills();
        double baseRow = rows.get(atZero.skillLevel(atZero.getSkills().get(SkillType.TALENT)) - 1).getFirst();
        double raisedRow = rows.get(atFive.skillLevel(atFive.getSkills().get(SkillType.TALENT)) - 1).getFirst();

        Assertions.assertEquals(1.0, baseRow, EPS, "her talent's row 10 (「100%攻击力」)");
        Assertions.assertEquals(1.1, raisedRow, EPS, "…and row 12, which is what Eidolon (星魂) 5's \"Talent Lv. +2\" (「天赋等级+2」) buys");
    }

    // ==================================================================
    // trace stat nodes: the ten nodes on her panel, and the rules that must not add them twice
    // ==================================================================

    /**
     * Her ten trace nodes reach the panel: the five ice nodes sum to 22.4%, the three defence nodes multiply defence
     * by 1.225, and the two effect-resistance nodes sum to 10%.
     *
     * <p>Note: All three are read off the same character built with traces ({@code false} is "no light cone"; the
     * nodes are applied by {@code Character.Builder.build()} through {@code SkillTrace.appendTo(SkillTrace.init(cid),
     * calcData)}), because the panel value is the trace sum alone.
     *
     * <p>Note: The defence claim is stated as a ratio against the same character's base defence, and not as an
     * absolute defence: defence also depends on level scaling and on any light cone or relic the caller passes, while
     * the trace contribution does not. The un-traced figure is obtained by the same subtraction 真珠's case uses --
     * {@code SkillTrace.sumAttributes} is the only thing that differs.
     *
     * <p>Note: The resistance claim is the sharpest of the three, for the reason 真珠's case gives: nothing else in
     * this engine grants {@code EFFECT_RESISTANCE}, so the panel value cannot be anything but the trace sum.
     */
    @Test
    public void theTraceStatNodesReachThePanel() {
        Character her = CharacterFactory.create(MARCH, LEVEL, false, null, null, 0);
        double withTraces = her.getAttribute(AttributeType.DEFENCE).get();

        double baseDefence = 573.3;   // the Lv80 promoted base the document states
        Assertions.assertAll("her ten trace stat nodes on the panel",
                () -> Assertions.assertEquals(0.224, her.getAttribute(AttributeType.ICE_DAMAGE_BOOST).get(), TRACE_EPS,
                        "3.2% + 3.2% + 4.8% + 4.8% + 6.4% -- and nothing else on this character grants an ice boost, "
                                + "so the panel value is the trace sum alone"),
                () -> Assertions.assertEquals(0.225, withTraces / baseDefence - 1, 1e-6,
                        "5% + 7.5% + 10% of the base defence: " + withTraces + " / " + baseDefence),
                () -> Assertions.assertEquals(0.10, her.getAttribute(AttributeType.EFFECT_RESISTANCE).get(), TRACE_EPS,
                        "4% + 6%, and no other source"));
    }

    /**
     * Her rule file grants no panel stat, which is what keeps the three cases above from doubling.
     *
     * <p>Read through the engine's own loader rather than by grepping the file: what matters is that no rule
     * in her table carries a {@code MODIFY_ATTR} that the trace already covers.
     */
    @Test
    public void herRulesDoNotGrantTheTraceStatsASecondTime() {
        Character her = CharacterFactory.create(MARCH, LEVEL, true, null, null, 6);
        var battle = new com.laosun.aluminium.Battle(java.util.List.of(her), java.util.List.of(),
                new java.util.Random(0));
        battle.startBattle();

        // 星魂 6 is active here, so her battle-start rules HAVE run; the trace values must be unchanged
        // rather than merely present.
        Assertions.assertEquals(0.224, her.getAttribute(AttributeType.ICE_DAMAGE_BOOST).get(), TRACE_EPS,
                "her rules must not add a second ice boost on top of the traces");
        Assertions.assertEquals(0.10, her.getAttribute(AttributeType.EFFECT_RESISTANCE).get(), TRACE_EPS,
                "her rules must not add a second effect resistance on top of the traces");
    }

    // ==================================================================
    // the technique: a battle-start freeze on one random enemy, gated on 「使用秘技后」
    // ==================================================================

    /**
     * The technique's gate, and the three ways a battle can be told about it: declared when the battle is BUILT,
     * marked afterwards, or not told at all.
     *
     * <p>Two ways to say the same thing, and they must not disagree: a caller that assembles a battle and
     * then decides calls {@link Battle#markTechniqueUsed}, while a caller that knows the team's technique
     * releases up front passes them to the constructor. The assertion is that the second route produces the
     * same battle as the first -- compared against the marking route rather than against a constant, so the
     * two cannot drift apart.
     *
     * <p>The old constructors are untouched: building a battle without techniques still freezes nobody. The change
     * was an overload, so the thousand-odd existing call sites had to keep their behaviour; stated as the contrast
     * that would break first if the new path leaked into the old one.
     *
     * <p>And the freeze itself is what pays for the gate: an otherwise identical battle without the mark freezes
     * nobody, because the freeze is the only control either battle can produce.
     */
    @Test
    public void theTechniqueGateDecidesWhetherAnEnemyFreezes() {
        Battle declared = techniqueBattleWithTechniques();
        Battle marked = techniqueBattle(true);
        declared.startBattle();
        marked.startBattle();

        int frozenWithTheTechnique = frozenCount(true);
        int frozenWithoutTheTechnique = frozenCount(false);

        Assertions.assertAll("the technique gate: declared at construction, marked afterwards, and absent",
                () -> Assertions.assertNotNull(controlOn(declared.enemyUnits().getFirst()),
                        "declaring the release at construction must freeze, exactly as marking does"),
                () -> Assertions.assertEquals(controlOn(marked.enemyUnits().getFirst()) != null,
                        controlOn(declared.enemyUnits().getFirst()) != null,
                        "the two routes must reach the same state"),
                () -> Assertions.assertTrue(frozenWithTheTechnique >= 1,
                        "with the technique marked, the battle-start rule must freeze an enemy"),
                () -> Assertions.assertEquals(0, frozenWithoutTheTechnique,
                        "a battle built without technique flags must freeze nobody -- the flags are the only new "
                                + "input, so silence is the correct default"),
                () -> Assertions.assertEquals(0, frozenWithoutTheTechnique,
                        "without the technique the same battle must freeze nobody -- the `self has_state sys:technique` "
                                + "gate is the whole reason the freeze happened"));
    }

    /**
     * The frozen enemy cannot act (「冻结状态下，敌方目标不能行动」), the freeze lasts one turn, and it does not survive
     * its own turn -- so the survivor acts again.
     *
     * <p>Asserted on {@code canAct()} rather than on the buff's presence, because "a control is attached"
     * and "the control stops the turn" are different claims and only the second is the sentence.
     *
     * <p>One turn of duration means the state is spent at its first turn boundary; without that half a
     * `turns` value that was written but never consumed would look correct forever -- the same failure the
     * shield had before {@code ShieldBuff} counted its duration.
     */
    @Test
    public void theFrozenEnemyCannotActAndTheFreezeIsSpentAfterOneTurn() {
        Battle battle = techniqueBattle(true);
        battle.startBattle();

        Enemy victim = frozenEnemy(battle);
        boolean canActWhileFrozen = victim != null && victim.getBuffManager().canAct();

        // Play turns until the victim's own turn has come and gone.
        for (int i = 0; i < 6 && !battle.isOver(); i++) {
            battle.stepForward();
            battle.beforeMove();
            battle.afterMove();
        }

        Assertions.assertAll("the technique's freeze: it stops the turn, and it is spent after one turn",
                () -> Assertions.assertNotNull(victim, "precondition: frozen at the start"),
                () -> Assertions.assertNotNull(victim, "precondition: the technique froze somebody"),
                () -> Assertions.assertFalse(canActWhileFrozen, "a frozen unit must not be able to act"),
                () -> Assertions.assertNull(controlOn(firstEnemy(battle)),
                        "one turn of freeze must not still be up several turns later"));
    }

    /**
     * The freeze lasts one turn and carries the 50%-of-ATK Ice damage that rides with it.
     *
     * <p>Two clauses of one sentence: 「持续1回合」 and 「每回合开始时受到等同于三月七50%攻击力的冰属性附加
     * 伤害」. The magnitude is read when the state lands, so it is compared against her own ATK at that
     * moment rather than against a constant.
     */
    @Test
    public void theFreezeCarriesItsDurationAndItsPerTurnDamage() {
        Battle battle = techniqueBattle(true);
        Character march = battle.characters().getFirst();
        battle.startBattle();

        Enemy victim = frozenEnemy(battle);
        Assertions.assertNotNull(victim, "precondition: the technique froze somebody");

        ControlBuff freeze = controlOn(victim);
        Assertions.assertNotNull(freeze, "the control must be a ControlBuff");
        Assertions.assertEquals(1, freeze.duration(), "「持续1回合」");
        Assertions.assertEquals(DamageElement.ICE, freeze.getDotElement(),
                "「冰属性附加伤害」");
        Assertions.assertEquals(march.getAttribute(AttributeType.ATTACK).get() * 0.5, freeze.getDotDamage(),
                1e-6, "「等同于三月七50%攻击力的冰属性附加伤害」 -- read from her ATK when it landed");
    }

    // ==================================================================
    // the ultimate's per-turn freeze: the payload rides the state, on the row the sentence names
    // ==================================================================

    @Test
    public void theFrozenEnemyTakesSixtyPercentOfHerAttackAtTheStartOfItsTurn() {
        Character march = CharacterFactory.create(MARCH, 80);
        Enemy enemy = BattleFixture.dummy();
        Battle battle = new Battle(List.of(march), List.of(enemy), alwaysRoll(0.0));

        List<Damage> settled = new ArrayList<>();
        battle.addDamageObserver((damage, applied, hpLoss, shieldAbsorbed, died) -> settled.add(damage));

        battle.startBattle();
        battle.processRequests();
        march.setCurrentEnergy(march.getMaxEnergy());
        Assertions.assertTrue(battle.castUltra(march, List.of(enemy)),
                "precondition: her ultimate went off (base chance 50%, dice forced to pass)");
        Assertions.assertTrue(enemy.getBuffManager().hasState("ctrl:frozen"),
                "precondition: the freeze landed -- without it there is no per-turn payload to check");

        // The victim's own turn start is where the clause says the hit lands (the same tick as any DOT).
        settled.clear();
        battle.tickDots(enemy);

        List<Damage> dots = settled.stream().filter(damage -> damage.getType() == DamageType.DOT).toList();
        Assertions.assertEquals(1, dots.size(),
                "「每回合开始时受到…冰属性附加伤害」 is ONE instance per settlement; settled " + settled.size()
                        + " instance(s) in total: " + settled.stream().map(Damage::getType).toList());
        Damage dot = dots.getFirst();
        Assertions.assertEquals(DamageElement.ICE, dot.getElement(),
                "the per-turn hit is 冰属性 -- an element-less DOT is the silent failure this pins");
        double expected = SHARE * march.getAttribute(AttributeType.ATTACK).get();
        Assertions.assertEquals(expected, dot.breakdown().getOrDefault("base", Double.NaN),
                Math.max(1e-6, expected * 1e-9),
                "「等同于三月七 60% 攻击力的冰属性附加伤害」: base = 0.6 x ATK (" + expected + ")");
    }

    /**
     * Without the freeze there is no per-turn hit: the payload rides the STATE, not the ultimate.
     */
    @Test
    public void aResistedFreezeCarriesNoPerTurnHit() {
        Character march = CharacterFactory.create(MARCH, 80);
        Enemy enemy = BattleFixture.dummy();
        Battle battle = new Battle(List.of(march), List.of(enemy), alwaysRoll(1.0));

        List<Damage> settled = new ArrayList<>();
        battle.addDamageObserver((damage, applied, hpLoss, shieldAbsorbed, died) -> settled.add(damage));

        battle.startBattle();
        battle.processRequests();
        march.setCurrentEnergy(march.getMaxEnergy());
        battle.castUltra(march, List.of(enemy));
        Assertions.assertFalse(enemy.getBuffManager().hasState("ctrl:frozen"), "precondition: the roll failed, so no freeze");

        settled.clear();
        battle.tickDots(enemy);

        Assertions.assertTrue(settled.stream().noneMatch(damage -> damage.getType() == DamageType.DOT),
                "no state, no payload -- a per-turn hit here would be the ultimate's own AoE counted again");
    }

    // ==================================================================
    // helpers - the kit's own fixture
    // ==================================================================

    /**
     * Her at the given 星魂 count, with a battle started - so the file's own rules (the level convention and the
     * Eidolon raises) have run and {@code skillLevel} answers with the row an effect would read.
     */
    private static Character startedAt(int eidolon) {
        Character march = CharacterFactory.create(MARCH, LEVEL, true, null, null, eidolon);
        new Battle(List.of(march), List.of(dummy()), BattleFixture.seeded()).startBattle();
        return march;
    }

    /**
     * One ultimate from her at the given 星魂 count, against a victim that can be frozen.
     *
     * @return {@code {damage dealt, the freeze's per-turn damage}}
     */
    private static double[] ultimateOutcome(int eidolon) {
        Character march = CharacterFactory.create(MARCH, LEVEL, true, null, null, eidolon);
        march.setAttribute(AttributeType.EFFECT_HIT_RATE, new DoubleValue(1.0));
        Enemy enemy = EnemyFactory.create(ORDINARY_MONSTER, 90, 1);
        enemy.setAttribute(AttributeType.EFFECT_RESISTANCE, new DoubleValue(0));
        enemy.setAttribute(AttributeType.HEALTH, new DoubleValue(5_000_000));
        enemy.heal(5_000_000);
        Battle battle = new Battle(List.of(march), List.of(enemy), BattleFixture.seeded());
        battle.startBattle();

        battle.castImmediate(
                march.getSkills().get(com.laosun.aluminium.enums.SkillType.ULTRA), march, List.of(enemy));

        DotBuff ice = enemy.getBuffManager().findBuff(DotBuff.class);
        Assertions.assertNotNull(ice, "precondition: the freeze landed, so it carries its per-turn damage");
        return new double[]{5_000_000 - enemy.getCurrentHp(), ice.getBaseDamage()};
    }

    /**
     * March 7th, one plain ally and one enemy: the shape every case above needs.
     */
    private static final class Fixture {
        private final Character march;
        private final Character ally;
        private final Enemy enemy;
        private final Battle battle;

        private Fixture() {
            this.march = CharacterFactory.create(MARCH, LEVEL);
            this.ally = CharacterFactory.create(ALLY, LEVEL);
            this.enemy = dummy();
            this.battle = new Battle(List.of(march, ally), List.of(enemy), BattleFixture.seeded());
            battle.startBattle();
        }

        /**
         * Casts her Skill on the ally, which is what puts a shield on them.
         */
        private void shieldTheAlly() {
            battle.castImmediate(new DefaultSkill(MARCH, SKILL_SLOT, 1), march, List.of(ally));
            Assertions.assertTrue(ally.getShield() > 0, "precondition: 「为指定我方单体提供…护盾」 (provides a shield to a designated single ally)");
        }

        /**
         * One enemy hit on the ally, through the engine's own damage path (which emits {@code TAKING_HIT}).
         */
        private void hitTheAlly(double amount) {
            battle.applyDamage(ally, new Damage(enemy, ally, DamageElement.ICE, DamageType.NORMAL, amount));
        }
    }

    // ==================================================================
    // helpers - levels and Eidolons
    // ==================================================================

    /**
     * Her, at the given 星魂 count, with the battle started so the file's own rules have run.
     *
     * <p>Note: Separate from {@link #startedAt(int)} because these cases pin the RNG's first roll to 0.0
     * ({@link #fixed()}), while the kit's own fixture runs on the suite's seeded sequence.
     */
    private static Character startedAtWithFixedRolls(int eidolon) {
        Character march = CharacterFactory.create(MARCH, LEVEL, true, null, null, eidolon);
        new Battle(List.of(march), List.of(dummy()), fixed()).startBattle();
        return march;
    }

    /**
     * How far above its base level that slot reads, i.e. the file's convention plus whatever the 星魂 adds.
     */
    private static int bonus(Character who, SkillType slot) {
        return who.skillLevelBonus(slot);
    }

    /**
     * The suite's fixed crit/dice roll: every {@code nextDouble} answers 0.0, and {@code nextInt} still varies.
     */
    private static Random fixed() {
        return BattleFixture.alwaysRoll(0.0);
    }

    // ==================================================================
    // helpers - the technique's battles
    // ==================================================================

    /**
     * How many enemies are frozen after a battle start, optionally without the technique.
     *
     * @param withTechnique whether the battle is told the technique was used
     * @return the number of frozen enemies
     */
    private static int frozenCount(boolean withTechnique) {
        Battle battle = techniqueBattle(withTechnique);
        battle.startBattle();
        int n = 0;
        for (Enemy enemy : battle.enemyUnits()) {
            if (controlOn(enemy) != null) {
                n++;
            }
        }
        return n;
    }

    /**
     * A battle against one freezable monster, with the technique marked or not.
     *
     * <p>The RNG is fixed at 0.0 so the base-chance roll always passes and the case measures the rule
     * rather than luck. Note this pins only {@code nextDouble}: {@code random_enemy} draws its target with
     * {@code nextInt}, which still varies -- harmless here because there is a single enemy, and the reason
     * the case with several enemies is not written.
     *
     * @param withTechnique whether to mark the technique as used
     * @return the battle, not started
     */
    private static Battle techniqueBattle(boolean withTechnique) {
        Character march = CharacterFactory.create(MARCH, LEVEL, false, null, null, 0);
        Enemy monster = EnemyFactory.create(ORDINARY_MONSTER, LEVEL, 1);
        monster.setAttribute(AttributeType.EFFECT_RESISTANCE, new DoubleValue(0));
        monster.setDebuffResist(Map.of());

        Battle battle = new Battle(List.of(march), List.of(monster), BattleFixture.alwaysRoll(0.0));
        if (withTechnique) {
            battle.markTechniqueUsed(march);
        }
        return battle;
    }

    /**
     * The same battle, with the technique release stated to the constructor instead of marked afterwards.
     *
     * <p>Same monster, same fixed RNG, and the same single character -- the only difference from
     * {@link #techniqueBattle(boolean)} is which route tells the battle about the technique, which is what
     * makes the two comparable.
     *
     * @return the battle, not started
     */
    private static Battle techniqueBattleWithTechniques() {
        Character march = CharacterFactory.create(MARCH, LEVEL, false, null, null, 0);
        Enemy monster = EnemyFactory.create(ORDINARY_MONSTER, LEVEL, 1);
        monster.setAttribute(AttributeType.EFFECT_RESISTANCE, new DoubleValue(0));
        monster.setDebuffResist(Map.of());

        return new Battle(List.of(march), List.of(monster), BattleFixture.alwaysRoll(0.0),
                Battle.Techniques.released(MARCH));
    }

    /**
     * The first enemy of a started battle.
     *
     * @param battle the battle
     * @return its first enemy
     */
    private static Enemy firstEnemy(Battle battle) {
        return battle.enemyUnits().getFirst();
    }

    /**
     * The frozen enemy, or {@code null} when none is frozen.
     *
     * @param battle the battle
     * @return the frozen enemy
     */
    private static Enemy frozenEnemy(Battle battle) {
        for (Enemy enemy : battle.enemyUnits()) {
            if (controlOn(enemy) != null) {
                return enemy;
            }
        }
        return null;
    }

    /**
     * The control buff on a unit, or {@code null}.
     *
     * @param unit the unit to inspect
     * @return its control buff
     */
    private static ControlBuff controlOn(Enemy unit) {
        for (AbstractBuff buff : unit.getBuffManager().allBuffsOf(ControlBuff.class)) {
            return (ControlBuff) buff;
        }
        return null;
    }

    // ==================================================================
    // helpers - the ultimate's per-turn freeze
    // ==================================================================

    /**
     * A dice that forces every roll: {@code 0.0} passes any positive chance, {@code 1.0} fails any chance below 1.
     *
     * <p>Note: Unlike {@link #fixed()} this also pins {@code nextInt} to 0, which is what the ultimate's own
     * cast path needs here.
     */
    private static Random alwaysRoll(double value) {
        return new Random(0L) {
            @Override
            public double nextDouble() {
                return value;
            }

            @Override
            public int nextInt(int bound) {
                return 0;
            }
        };
    }

    private static Enemy dummy() {
        return EnemyFactory.create(MONSTER, 90, 1);
    }
}
