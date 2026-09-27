package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.data.TriggerTables;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.DoubleValue;
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
 * 三月七 (1001) — a kit whose clauses each needed a different engine capability, and what that looks like in the data.
 *
 * <p><b>Why her.</b> Her document is a wall of shields: a DEF-scaled shield, a taunt, a freeze, and a counter that
 * needs a shielded ally. Three clauses are complete and pinned here — the shield (a DEF share plus a constant, and
 * a <b>duration</b>), the cleanse (行迹「纯洁」) and, since 2026-09-27, the 天赋 counter, which is what the
 * {@code has_shield} condition and the {@code per_turn} limit were added for. The rest is <b>registered rather than
 * approximated</b>: every one of those clauses would be a wrong number or a wrong trigger if it were guessed at, and
 * a census of what is missing is worth more than a file that looks finished.
 *
 * <p>⚠ The blocked clauses and the capability each one needs are listed in {@code characters/1001.json}'s notes.
 */
public class March7thKitTest {
    private static final double EPS = 1e-6;

    /** 三月七 herself. */
    private static final int MARCH = 1001;
    /** 桂乃芬 — a plain ally to be shielded and cleansed; her own rules never touch the numbers below. */
    private static final int ALLY = 1210;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    /** An ordinary monster with no specific resistances (1002011 is immune to 冻结 — see the freeze cases). */
    private static final int ORDINARY_MONSTER = 1003010;
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
     * 天赋「少女的特权」: 「当持有护盾的我方目标受到敌方目标攻击后，三月七立即向攻击者发起反击…每回合可触发2次」.
     *
     * <p>⚠ The hit is deliberately one the <b>shield absorbs entirely</b> (HP never moves). That is the case the
     * event choice exists for: 「受到攻击后」 is about being attacked, not about losing HP, so gating this on
     * {@code HP_LOST} would have silently skipped exactly the situation the talent describes. The damage is applied
     * through the engine's own path ({@code Battle.applyDamage}), which is what emits {@code TAKING_HIT}.
     */
    @Test
    public void herTalentCountersTheEnemyThatAttacksAShieldedAlly() {
        Fixture f = new Fixture();
        f.shieldTheAlly();
        double enemyBefore = f.enemy.getCurrentHp();

        f.hitTheAlly(100);

        Assertions.assertEquals(f.ally.getMaxHp(), f.ally.getCurrentHp(), EPS,
                "precondition: the shield ate the whole hit");
        Assertions.assertTrue(f.enemy.getCurrentHp() < enemyBefore,
                "「立即向攻击者发起反击」 -- the ATTACKER takes it, and the trigger is being attacked, not losing HP");
    }

    /** 「该效果每回合可触发2次」: the third hit of the same turn is not answered, and her own turn refreshes it. */
    @Test
    public void theCounterStopsAtTwoPerTurnAndComesBackOnHerOwnTurn() {
        Fixture f = new Fixture();
        f.shieldTheAlly();

        f.hitTheAlly(100);
        double afterFirst = f.enemy.getCurrentHp();
        f.hitTheAlly(100);
        Assertions.assertTrue(f.enemy.getCurrentHp() < afterFirst, "the second counter of the turn is allowed");

        double afterSecond = f.enemy.getCurrentHp();
        f.hitTheAlly(100);
        Assertions.assertEquals(afterSecond, f.enemy.getCurrentHp(), EPS,
                "the third is refused: without `per_turn` the counter would answer every hit of a three-hit attack");

        TestTurns.take(f.battle, f.march);
        f.hitTheAlly(100);
        Assertions.assertTrue(f.enemy.getCurrentHp() < afterSecond, "her own turn hands the two uses back");
    }

    /** 「持有护盾的」 is a real gate: an ally with no shield is not counter-protected. */
    @Test
    public void anAllyWithNoShieldIsNotCounteredFor() {
        Fixture f = new Fixture();
        double enemyBefore = f.enemy.getCurrentHp();

        f.hitTheAlly(100);

        Assertions.assertEquals(enemyBefore, f.enemy.getCurrentHp(), EPS, "no shield, no counter");
    }

    /**
     * 终结技「冰刻箭雨之时」: 「受到攻击的敌方目标有50%基础概率陷入冻结状态，持续1回合」.
     *
     * <p>⚠ The ultimate's <b>damage</b> needs no rule (the engine's ordinary AoE path reads 100103's own row), so
     * what is asserted here is the state: the victim cannot act, and 「冻结状态」 is readable by the condition DSL.
     *
     * <p>⚠ The fixture is 1003010, not the 冰锋 the other cases use: 冰锋's data carries
     * {@code STAT_CTRL_Frozen = 1.0} — it cannot be frozen by a skill at all (which the next test pins). Both
     * sides of the probability pipeline are stated here (her 效果命中, the victim's 效果抵抗) so that "50% base"
     * is not a coin flip in a test.
     */
    @Test
    public void herUltimateFreezesWhoeverItHits() {
        Character march = CharacterFactory.create(MARCH, LEVEL);
        march.setAttribute(AttributeType.EFFECT_HIT_RATE, new DoubleValue(1.0));
        Enemy enemy = EnemyFactory.create(ORDINARY_MONSTER, 90, 1);
        enemy.setAttribute(AttributeType.EFFECT_RESISTANCE, new DoubleValue(0));
        Battle battle = new Battle(List.of(march), List.of(enemy), new Random(0));
        battle.startBattle();

        battle.castImmediate(march.getSkills().get(com.laosun.aluminium.enums.SkillType.ULTRA), march,
                List.of(enemy));

        Assertions.assertTrue(enemy.getBuffManager().hasState("冻结"),
                "「有50%基础概率陷入冻结状态」 -- with 效果命中 +100% and no resistance, this is the certain case");
        Assertions.assertFalse(enemy.getBuffManager().canAct(), "「冻结状态下，敌方目标不能行动」");
        Assertions.assertTrue(enemy.getCurrentHp() < enemy.getMaxHp(),
                "and the first sentence of the ultimate is the engine's own AoE path, not a rule");

        // The second half of that sentence, and the reason it is one rule: 「冻结状态下…每回合开始时受到等同于
        // 三月七60%攻击力的冰属性附加伤害」.
        com.laosun.aluminium.models.buff.DotBuff ice = enemy.getBuffManager()
                .findBuff(com.laosun.aluminium.models.buff.DotBuff.class);
        Assertions.assertNotNull(ice, "the frozen enemy carries the state's per-turn damage");
        Assertions.assertEquals(0.6 * march.getAttribute(AttributeType.ATTACK).get(), ice.getBaseDamage(), EPS,
                "60% of HER attack, read when the freeze landed");
        Assertions.assertEquals(DamageElement.ICE, ice.getElement());
    }

    /**
     * The other side of the same roll, and a fact about the <b>data</b> rather than about the fixture: 冰锋
     * (1002011) is immune to freeze ({@code STAT_CTRL_Frozen = 1.0}), so even a certain base chance cannot land.
     */
    @Test
    public void herUltimateCannotFreezeAMonsterThatIsImmuneToIt() {
        Character march = CharacterFactory.create(MARCH, LEVEL);
        march.setAttribute(AttributeType.EFFECT_HIT_RATE, new DoubleValue(10.0));
        Enemy immune = dummy();
        Battle battle = new Battle(List.of(march), List.of(immune), new Random(0));
        battle.startBattle();

        battle.castImmediate(march.getSkills().get(com.laosun.aluminium.enums.SkillType.ULTRA), march,
                List.of(immune));

        Assertions.assertEquals(java.util.Map.of("STAT_CTRL_Frozen", 1.0), immune.getDebuffResist(),
                "precondition: this monster's data is what makes it unfreezable");
        Assertions.assertFalse(immune.getBuffManager().hasState("冻结"),
                "no amount of 效果命中 beats a specific immunity -- which is why the case above needs a "
                        + "different enemy rather than a bigger number");
    }

    /**
     * 星魂 2「记忆中的它」: 「进入战斗时，为当前生命值百分比最低的我方目标提供等同于三月七24%防御力+320的护盾，
     * 持续3回合」.
     *
     * <p>⚠ What this needed was the <b>selector</b> (`lowest_hp_ally`), not a new op: the shield's numbers are the
     * Skill's shape (`scale: owner_def` + a constant + a duration) and the gate is the ordinary Eidolon rank.
     *
     * <p>⚠ And it always fires on a <b>tie</b>: at battle start everybody is at 100%, so "the lowest percentage" is
     * the whole party. The engine's tie-break is the earliest unit in the party order, which is what makes it
     * deterministic — hence the assertion on the FIRST ally rather than on "somebody".
     */
    @Test
    public void herSecondEidolonShieldsTheMostHurtAllyAtBattleStart() {
        Character march = CharacterFactory.create(MARCH, LEVEL, true, null, null, 2);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Battle battle = new Battle(List.of(march, ally), List.of(dummy()), new Random(0));

        battle.startBattle();

        double expected = 0.24 * march.getAttribute(AttributeType.DEFENCE).get() + 320;
        Assertions.assertEquals(expected, march.getShield(), EPS,
                "the first unit in the party order takes it, because everybody is at 100% at battle start");
        Assertions.assertEquals(0, ally.getShield(), EPS, "and only one unit is 「最低的」");
        Assertions.assertEquals(MARCH, ((Character) battle.allies.getFirst()).getCid(),
                "precondition: she is first in the party order");
    }

    /** Below rank 2 the rule is not there at all — the Eidolon gate, not a weaker shield. */
    @Test
    public void theSecondEidolonsShieldDoesNotExistBelowItsRank() {
        Character march = CharacterFactory.create(MARCH, LEVEL, true, null, null, 1);
        Battle battle = new Battle(List.of(march), List.of(dummy()), new Random(0));

        battle.startBattle();

        Assertions.assertEquals(0, march.getShield(), EPS, "星魂 1 does not grant it");
    }

    /**
     * 行迹「加护」: 「战技提供的护盾持续时间增加1回合」.
     *
     * <p>⚠ The +1 is a <b>separate rule</b> from the Skill's shield, and it has to run after it in the same event —
     * which it does, because effects of one event run in order (only *conditions* are all evaluated up front). The
     * two numbers therefore stay in two rules, and the trace can be gated on its own one day.
     */
    @Test
    public void theGraceTraceLengthensTheShieldHerSkillJustApplied() {
        Fixture f = new Fixture();
        f.shieldTheAlly();

        com.laosun.aluminium.models.buff.ShieldBuff shield =
                f.ally.getBuffManager().findBuff(com.laosun.aluminium.models.buff.ShieldBuff.class);

        Assertions.assertEquals(4, shield.duration(),
                "3 turns from the Skill + 1 from 加护 -- 「战技提供的护盾持续时间增加1回合」");
    }

    /**
     * The rest of her kit is <b>registered, not approximated</b>.
     *
     * <p><b>Nine</b> clauses exist and each is pinned above; the counts here are what says nothing else was written.
     * Every missing one would be a wrong number or a wrong trigger if it were spelled with the vocabulary that exists
     * today: 星魂 4's <i>second</i> sentence needs a damage addend derived from an attribute (「提高数值等同于三月七防御力
     * 的30%」 — {@code BOOST_DAMAGE} takes a percentage of the instance, not "X = 30% of my DEF"), and 星魂 6 needs the
     * shield's provider to be askable <i>per ally</i>. Her 战技's soft taunt is a data gap (the document states no
     * magnitude), and 星魂 3/5 are skill levels (M-32).
     *
     * <p>⚠ Two clauses came off that list and each is worth a line: 星魂 1 (2026-09-28) needed a per-cast count of the
     * victims a state actually landed on ({@code "scale": "cast_applied:冻结"}), and 星魂 4's first sentence plus
     * 行迹「冰咒」 (same day) needed a way to raise another rule's own number ({@code MODIFY_RULE}, which is what put two
     * more rules on her BATTLE_START).
     */
    @Test
    public void theRestOfHerKitIsNotAuthored() {
        Assertions.assertEquals(3, TriggerTables.of(MARCH).ruleCount(TriggerEvent.SKILL_CAST),
                "the shield, the cleanse trace and 加护 (the shield's +1 turn) -- and nothing else on her Skill");
        Assertions.assertEquals(1, TriggerTables.of(MARCH).ruleCount(TriggerEvent.TAKING_HIT),
                "the Talent's counter");
        Assertions.assertEquals(2, TriggerTables.of(MARCH).ruleCount(TriggerEvent.ULT_CAST),
                "the ultimate's freeze (its damage is the engine's own path, so there is no damage rule) and 星魂 1's "
                        + "energy per landed freeze");
        Assertions.assertEquals(3, TriggerTables.of(MARCH).ruleCount(TriggerEvent.BATTLE_START),
                "星魂 2's battle-start shield for the most hurt ally, plus the two amendments: 星魂 4 raising the "
                        + "talent's per-turn cap and 行迹「冰咒」 raising the ultimate's base chance");
        Assertions.assertEquals(0, TriggerTables.of(MARCH).ruleCount(TriggerEvent.KILL),
                "and nothing of hers reacts to kills");
    }

    // ==================================================================
    // helpers
    // ==================================================================

    /** 三月七, one plain ally and one enemy: the shape every case above needs. */
    private static final class Fixture {
        private final Character march;
        private final Character ally;
        private final Enemy enemy;
        private final Battle battle;

        private Fixture() {
            this.march = CharacterFactory.create(MARCH, LEVEL);
            this.ally = CharacterFactory.create(ALLY, LEVEL);
            this.enemy = dummy();
            this.battle = new Battle(List.of(march, ally), List.of(enemy), new Random(0));
            battle.startBattle();
        }

        /** Casts her Skill on the ally, which is what puts a shield on them. */
        private void shieldTheAlly() {
            battle.castImmediate(new DefaultSkill(MARCH, SKILL_SLOT, 1), march, List.of(ally));
            Assertions.assertTrue(ally.getShield() > 0, "precondition: 「为指定我方单体提供…护盾」");
        }

        /** One enemy hit on the ally, through the engine's own damage path (which emits {@code TAKING_HIT}). */
        private void hitTheAlly(double amount) {
            battle.applyDamage(ally, new Damage(enemy, ally, DamageElement.ICE, DamageType.NORMAL, amount));
        }
    }

    private static Enemy dummy() {
        return EnemyFactory.create(MONSTER, 90, 1);
    }
}
