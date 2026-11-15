package com.laosun.aluminium.test.content.characters.gepard;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.buff.StateBuff;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.trigger.TriggerPayload;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 1104 杰帕德 (Gepard): his kit, his freeze, his flat traces, and Eidolons 1, 2, 4 and 6.
 *
 * <p>Consolidated (P2) from seven sibling classes; each source's class doc is kept, as a plain comment, at the head of the
 * group it supplied, and every case and assertion is unchanged. The two nested fixtures keep separate names
 * ({@code KitFixture}, {@code FreezeFixture}) because they build different scenes, and Eidolon 4's 1001 ally is named
 * {@code EIDOLON_FOUR_ALLY} so it cannot be confused with the kit's 1002 ally.
 */
public class GepardTest {

    private static final int GEPARD = 1104;
    private static final int LEVEL = 80;
    private static final String FREEZE = "ctrl:frozen";
    /** The party ally the kit scene summons (1002). */
    private static final int ALLY = 1002;
    /** Eidolon 4 is measured on a plain 1001 ally, which keeps that case's original probe. */
    private static final int EIDOLON_FOUR_ALLY = 1001;

    // ==================================================================
    // Former class: GepardKitTest
    // ==================================================================
    /*
     * Gepard (杰帕德) (1104): his kit verified through the engine's own readings.
     *
     * <p><b>Why the shield is read as a NUMBER.</b> The source settles it - a shield is
     * {@code CanHit.getShield()}, and the {@code has_shield} condition is literally {@code who.getShield() <= 0}, so buff counting
     * is blind to it by construction.
     *
     * <p><b>Why one case needs E1.</b> The control roll is {@code Battle.rng.nextDouble() < hitChance(...)}, i.e. the 65% base
     * chance runs through effect hit rate / effect RES (效果命中 / 效果抵抗) and can genuinely miss - so a fixed roll cannot make the plain skill deterministic.
     * At E1 his own rule says "the base chance to freeze is increased by 35%" (a {@code MODIFY_RULE}), which brings the base chance to 100%; the roll then
     * cannot miss whatever {@code rng} hands back. The case therefore verifies the freeze AND eidolon 1 in one go.
     *
     * <p>Note: <b>The E1 freeze is unverified.</b> The control roll is
     * Battle.rng.nextDouble() {@code <} hitChance(...), so a fixed roll cannot make the plain skill deterministic, and even at E1 - where
     * his own rule raises the base chance by 35% - the target does not end up frozen under this fixture. Either that MODIFY_RULE
     * does not reach the control roll, or the monster carries a specific resistance to this control. Answering it needs one source
     * read (where MODIFY_RULE files its modifier and what hitChance's specificResistKey resolves to); until then the freeze stays unverified rather than flaky.
     *
     * <p><b>Registered</b> in his file: the talent (needs a lethal-blow observable), the technique's shield (needs a "the technique
     * was used" gate), eidolons 2 and 6, and the trace "Integrity (刚正)" (a soft taunt whose number is in no document).
     */

    /**
     * Note: "为我方全体提供…护盾": read as the engine reads it - a shield VALUE on each unit.
     */
    @Test
    public void hisUltimateShieldsTheCasterAndTheParty() {
        KitFixture f = new KitFixture(0);
        Assertions.assertEquals(0.0, f.gepard.getShield(), 1e-9, "precondition: no shield before the cast");

        f.battle.castImmediate(f.gepard.getSkills().get(SkillType.ULTRA), f.gepard, List.of(f.ally));

        Assertions.assertTrue(f.gepard.getShield() > 0,
                "「为我方全体提供能够抵消…伤害的护盾」 (provides all allies with a shield that absorbs ... damage) — the caster is shielded too");
        Assertions.assertTrue(f.ally.getShield() > 0,
                "…and the aimed ally, so the rule is not written for `target` alone");
    }

    /**
     * Note: The trace "每回合开始时刷新": his own turn start puts the defence-derived attack bonus on him.
     */
    @Test
    public void hisTraceRefreshesOnHisTurn() {
        KitFixture f = new KitFixture(0);
        double before = f.gepard.getAttribute(AttributeType.ATTACK).get();

        f.battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.TURN_START, f.gepard, f.gepard, 0, 0));

        Assertions.assertTrue(f.gepard.getAttribute(AttributeType.ATTACK).get() > before,
                "「提高等同于自身当前防御力35%的攻击力，每回合开始时刷新」 (raises ATK by an amount equal to 35% of his current DEF, refreshed at the start of every turn)");
    }

    /**
     * Traces (行迹) "Integrity (刚正)": "raises the chance that Gepard (杰帕德) is attacked by enemies" - the number is upstream, not in the prose.
     *
     * <p>{@code AvatarSkillTreeConfig}'s row for point 1104101 carries {@code ParamList = [3]} and the ability it names
     * attaches {@code M_SkillTree_AggroUp}, which writes {@code AggroAddedRatio} as <b>+parameter</b>; so his weight
     * becomes  x (1 + 3) = <b> x 4</b>. The sibling trace "Grit (战意)" is the method's own check: its row says 0.35 and the
     * document renders it as "DEFENCE increased by 35%".
     */
    @Test
    public void hisTraceRaisesHisOwnAggroWeight() {
        Character gepard = CharacterFactory.create(GEPARD, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Battle battle = new Battle(List.of(gepard, ally), List.of(BattleFixture.monster()),
                new Random(0));
        double before = battle.aggroOf(gepard);

        battle.startBattle();

        Assertions.assertEquals(4.0, battle.aggroOf(gepard) / before, 1e-9,
                "\"Gepard is more likely to be attacked by enemies\" (「傑帕德被敌方攻击的概率提高」) "
                        + "-- ParamList [3] reads as weight x (1 + 3)");
        Assertions.assertTrue(battle.aggroOf(gepard) > battle.aggroOf(ally),
                "and he now outweighs a plain ally, which is the whole point of the trace");
    }

    // ==================================================================
    // Former class: GepardFlatTracesTest
    // ==================================================================
    /*
     * 1104 杰帕德's FLAT trace bonuses (冰伤 3.2/3.2/4.8/4.8/6.4%、效果抵抗 4/6/8%、防御 5/7.5%) need no rule: the character
     * factory applies the data's attribute rows. This judge pins that so the old "未写" registration cannot come back.
     */

    @Test
    public void theFlatTraceBonusesAreAlreadyOnThePanel() {
        Character gepard = CharacterFactory.create(GEPARD, LEVEL);
        Assertions.assertEquals(0.224, gepard.getAttribute(AttributeType.ICE_DAMAGE_BOOST).get(), 1e-9,
                "冰伤行迹 3.2+3.2+4.8+4.8+6.4 = 22.4%");
        Assertions.assertEquals(0.18, gepard.getAttribute(AttributeType.EFFECT_RESISTANCE).get(), 1e-9,
                "效果抵抗行迹 4+6+8 = 18%");
        Assertions.assertEquals(1.125, gepard.getAttribute(AttributeType.DEFENCE).get()
                / gepard.getAttribute(AttributeType.DEFENCE).baseValue(), 1e-9, "防御行迹 5+7.5 = 12.5%");
    }

    // ==================================================================
    // Former class: GepardFreezeTest
    // ==================================================================
    /*
     * Gepard (杰帕德)'s freeze, made deterministic, and the reason it reads as broken on the default fixture.
     *
     * <p><b>What the source says.</b> `Battle.hitChance` computes
     * <pre>baseChance * (1 + EFFECT_HIT_RATE) * (1 - EFFECT_RESISTANCE) * (1 - specific)</pre>
     * where `specific` is, for an enemy target, `enemy.getDebuffResist().getOrDefault(specificResistKey, 0.0)` - and the control's
     * key for frozen (冻结) is its own resist key. The monster this project's fixtures use by default carries that key at <b>1.0</b>, so the
     * chance is clamped to <b>0</b> and the roll can never pass: the content is right, the enemy is immune.
     *
     * <p><b>What makes it deterministic.</b> A hand-made target with no resistances at all, plus eidolon 1's +35% base chance on
     * top of the 65% in the document - i.e. exactly 100%, against 0 hit-rate and 0 resistance. The freeze then lands whatever
     * `rng` returns, so this single case verifies the control, its per-turn payload, the base-chance amendment, and the choice of
     * target all at once.
     */

    /**
     * Note: E1 (65% + 35% = 100%) against a target with no resistances: deterministic, and it proves the whole chain.
     */
    @Test
    public void hisFreezeLandsOnAnUnresistingTarget() {
        FreezeFixture f = new FreezeFixture(1);
        Assertions.assertFalse(f.enemy.getBuffManager().hasState("ctrl:frozen"), "precondition: not frozen yet");

        f.battle.castImmediate(f.gepard.getSkills().get(SkillType.SKILL), f.gepard, List.of(f.enemy));

        Assertions.assertTrue(f.enemy.getBuffManager().hasState("ctrl:frozen"),
                "\"a 65% base chance to put the attacked enemy target into the Frozen state\" (「有65%的基础概率使受到攻击的敌方目标陷入冻结状态」) + the +35% from Eidolon (星魂) 1 ⇒ 100% base chance");
    }

    /**
     * Note: The control carries its own per-turn payload: "冻结状态下...每回合开始时受到...冰属性附加伤害".
     */
    @Test
    public void theFreezeCarriesItsPerTurnDamage() {
        FreezeFixture f = new FreezeFixture(1);
        f.battle.castImmediate(f.gepard.getSkills().get(SkillType.SKILL), f.gepard, List.of(f.enemy));
        Assertions.assertTrue(f.enemy.getBuffManager().hasState("ctrl:frozen"), "precondition: the freeze landed");

        double before = f.enemy.getCurrentHp();
        f.battle.tickDots(f.enemy);

        Assertions.assertTrue(f.enemy.getCurrentHp() < before,
                "「冻结状态下，敌方目标不能行动同时每回合开始时受到等同于杰帕德60%攻击力的冰属性附加伤害」 (while Frozen, the enemy target cannot act and takes additional Ice DMG equal to 60% of Gepard (杰帕德)'s ATK at the start of every turn)");
    }

    /**
     * At E0 the chance is the document's 65%, so a roll of 0.0 still lands - the amendment is not what makes it pass.
     */
    @Test
    public void atEidolonZeroTheDocumentChanceApplies() {
        FreezeFixture f = new FreezeFixture(0);
        f.battle.castImmediate(f.gepard.getSkills().get(SkillType.SKILL), f.gepard, List.of(f.enemy));

        Assertions.assertTrue(f.enemy.getBuffManager().hasState("ctrl:frozen"),
                "65% base chance with a roll of 0.0 and no resistance: the plain clause works without any eidolon");
    }

    // ==================================================================
    // Former class: GepardEidolonTest
    // ==================================================================
    /*
     * Gepard (杰帕德)'s eidolon 1, verified through the engine's own reading.
     *
     * <p><b>How the source settled it.</b> `TriggerInterpreter.modifyRule` dispatches on which field the effect carries: `amount` is a
     * per-turn count, `effect_turns` is a duration, `effect_max_stacks` is a stack cap, `effect_percent` is the rule's effect
     * percentage, and - the branch that matters here - <b>a plain `percent` is the base-chance amendment</b>
     * (`owner.addRuleBaseChanceBonus(target, percent)`). The control's roll reads it back as
     * `baseChance += ctx.owner().ruleBaseChanceBonus(ctx.ruleId())`, so the amendment is keyed by the id of the rule that applies
     * the control.
     *
     * <p>That gives a deterministic assertion for "冻结的基础概率提高35%" that does not touch the dice at all: ask the combatant what
     * it has filed. The freeze itself stays unverified - even at E1 it did not land under the fixture, which is now narrowed to
     * either the roll or the state's name/resistance rather than to this op.
     */

    /**
     * Note: E1 files a +0.35 base-chance amendment under the id of the rule that applies the freeze.
     */
    @Test
    public void eidolonOneFilesTheBaseChanceAmendment() {
        Character atE0 = gepardAt(0);
        Character atE1 = gepardAt(1);

        double none = atE0.ruleBaseChanceBonus("skill_daunting_smite");
        double raised = atE1.ruleBaseChanceBonus("skill_daunting_smite");

        Assertions.assertEquals(0.0, none, 1e-9,
                "at E0 nothing is filed for that rule");
        Assertions.assertEquals(0.35, raised, 1e-9,
                "「施放战技时，使受到攻击的敌方目标陷入冻结状态的基础概率提高35%」 (when casting the Skill, raises the base chance of putting the attacked enemy target into the Frozen state by 35%) — filed as a BASE CHANCE amendment, "
                        + "which is what a plain `percent` on MODIFY_RULE means");
    }

    // ==================================================================
    // Former class: GepardEidolonTwoTest
    // ==================================================================
    /*
     * 1104 杰帕德's Eidolon 2: 「冻结状态解除后，目标的速度降低 20%，持续 1 回合」.
     *
     * <p>Note: a state added by a rule is a POST-move buff, so its expiry is announced on {@code afterMove} -- the judge ticks
     * both halves and asserts on the result.
     */

    @Test
    public void theEnemyIsSlowerOnceTheFreezeEnds() {
        Character gepard = CharacterFactory.create(GEPARD, LEVEL, true, null, null, 2);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(new ArrayList<>(List.of(gepard, CharacterFactory.create(1001, LEVEL))),
                new ArrayList<>(List.of(enemy)), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        double before = enemy.getAttribute(AttributeType.SPEED).get();

        enemy.getBuffManager().addBuff(new StateBuff(FREEZE, 1, false));
        enemy.getBuffManager().beforeMove();
        enemy.getBuffManager().afterMove();
        double after = enemy.getAttribute(AttributeType.SPEED).get();
        System.out.println("[1104] speed " + before + " -> " + after);
        Assertions.assertEquals(0.8, after / before, 1e-6, "「速度降低 20%」");
    }

    // ==================================================================
    // Former class: GepardEidolonFourTest
    // ==================================================================
    /*
     * 1104 杰帕德's Eidolon 4: 「杰帕德在场时，我方全体的效果抵抗提高 20%」 - the bonus is anchored to HIM, so it leaves with him.
     *
     * <p>Measured as a DELTA against the same ally without the Eidolon: the ally's own panel already carries some resistance.
     */

    @Test
    public void theResistanceLeavesWhenHeFalls() {
        double bare = allyResistance(0);
        double withEidolon = allyResistance(4);
        Assertions.assertEquals(0.2, withEidolon - bare, 1e-9, "「效果抵抗提高 20%」");

        Character gepard = CharacterFactory.create(GEPARD, LEVEL, true, null, null, 4);
        Character ally = CharacterFactory.create(EIDOLON_FOUR_ALLY, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(new ArrayList<>(List.of(gepard, ally)),
                new ArrayList<>(List.of(enemy)), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        // His Talent survives the FIRST lethal blow once per battle, so the judge spends it and then lands the fatal one.
        battle.applyTrueDamage(enemy, gepard, DamageElement.PHYSICAL, gepard.getCurrentHp() * 10);
        battle.processRequests();
        battle.applyTrueDamage(enemy, gepard, DamageElement.PHYSICAL, gepard.getCurrentHp() * 10);
        battle.processRequests();
        Assertions.assertTrue(gepard.isDeath(), "precondition: he is down");
        double after = ally.getAttribute(AttributeType.EFFECT_RESISTANCE).get();
        System.out.println("[1104] bare=" + bare + " with=" + withEidolon + " after his death=" + after);
        Assertions.assertEquals(bare, after, 1e-9, "「在场时」 - the bonus left with him");
    }

    // ==================================================================
    // Former class: GepardEidolonSixTest
    // ==================================================================
    /*
     * 1104 杰帕德's Eidolon 6: 「触发天赋时杰帕德立即行动，且当前生命值的回复量额外增加自身生命上限的 50%」.
     *
     * <p>With the Talent (a 50% heal on the same moment) the two together put him back at full HP.
     */

    @Test
    public void theTalentTriggerHealsAnotherHalf() {
        Character gepard = CharacterFactory.create(GEPARD, LEVEL, true, null, null, 6);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(new ArrayList<>(List.of(gepard, CharacterFactory.create(1001, LEVEL))),
                new ArrayList<>(List.of(enemy)), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        double max = gepard.getMaxHp();
        gepard.consumeHp(max * 0.9);
        double before = gepard.getCurrentHp();

        battle.fire(TriggerPayload.Simple.of(TriggerEvent.LETHAL_DAMAGE, enemy, gepard, 0, 0));
        double healed = gepard.getCurrentHp() - before;
        System.out.println("[1104] max=" + max + " healed=" + healed + " -> hp=" + gepard.getCurrentHp());
        Assertions.assertTrue(healed >= 0.5 * max, "「额外增加自身生命上限的 50%」");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private static Character gepardAt(int eidolon) {
        Character gepard = CharacterFactory.create(GEPARD, LEVEL, true, null, null, eidolon);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(gepard), List.of(enemy), new Random() {
            @Override
            public double nextDouble() {
                return 0.0;
            }
        });
        battle.startBattle();
        return gepard;
    }

    private static double allyResistance(int eidolon) {
        Character gepard = CharacterFactory.create(GEPARD, LEVEL, true, null, null, eidolon);
        Character ally = CharacterFactory.create(EIDOLON_FOUR_ALLY, LEVEL);
        Battle battle = new Battle(new ArrayList<>(List.of(gepard, ally)),
                new ArrayList<>(List.of(BattleFixture.monster())), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        return ally.getAttribute(AttributeType.EFFECT_RESISTANCE).get();
    }

    /**
     * The kit scene: Gepard at the requested Eidolon beside a 1002 ally on the default monster fixture.
     */
    private static final class KitFixture {
        private final Character gepard;
        private final Character ally = CharacterFactory.create(ALLY, LEVEL);
        private final Enemy enemy = BattleFixture.monster();
        private final Battle battle;

        private KitFixture(int eidolon) {
            gepard = CharacterFactory.create(GEPARD, LEVEL, true, null, null, eidolon);
            battle = new Battle(List.of(gepard, ally), List.of(enemy), new Random() {
                @Override
                public double nextDouble() {
                    return 0.0;
                }
            });
            battle.startBattle();
        }
    }

    /**
     * The freeze scene: Gepard alone against a hand-made target with NO resistances, so the control can actually land.
     */
    private static final class FreezeFixture {
        private final Character gepard;
        private final Enemy enemy = Enemy.fromAttributes("Test Dummy", 20000, 100, 100, 90);
        private final Battle battle;

        private FreezeFixture(int eidolon) {
            gepard = CharacterFactory.create(GEPARD, LEVEL, true, null, null, eidolon);
            battle = new Battle(List.of(gepard), List.of(enemy), new Random() {
                @Override
                public double nextDouble() {
                    return 0.0;
                }
            });
            battle.startBattle();
        }
    }
}
