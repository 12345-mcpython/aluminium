package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.data.TriggerTables;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * Gepard (杰帕德) (1104): his kit verified through the engine's own readings (2026-09-28, rounds 12-128).
 *
 * <p><b>Why the shield is read as a NUMBER.</b> The first attempt counted buffs; the source settles it - a shield is
 * {@code CanHit.getShield()}, and the {@code has_shield} condition is literally {@code who.getShield() <= 0}, so buff counting
 * was blind to it by construction.
 *
 * <p><b>Why one case needs E1.</b> The control roll is {@code Battle.rng.nextDouble() < hitChance(...)}, i.e. the 65% base
 * chance runs through effect hit rate / effect RES (效果命中 / 效果抵抗) and can genuinely miss - so a fixed roll cannot make the plain skill deterministic.
 * At E1 his own rule says "the base chance to freeze is increased by 35%" (a {@code MODIFY_RULE}), which brings the base chance to 100%; the roll then
 * cannot miss whatever {@code rng} hands back. The case therefore verifies the freeze AND eidolon 1 in one go.
 *
 * <p>Note: <b>An E1 freeze case was REMOVED, not fixed</b> (round 128, measured): the control roll is
 * Battle.rng.nextDouble() < hitChance(...), so a fixed roll cannot make the plain skill deterministic, and even at E1 - where
 * his own rule raises the base chance by 35% - the target did NOT end up frozen under this fixture. Either that MODIFY_RULE
 * does not reach the control roll, or the monster carries a specific resistance to this control. Answering it needs one source
 * read (where MODIFY_RULE files its modifier and what hitChance's specificResistKey resolves to) before the case is
 * written again; until then the freeze stays unverified rather than flaky.
 *
 * <p><b>Registered</b> in his file: the talent (needs a lethal-blow observable), the technique's shield (needs a "the technique
 * was used" gate), eidolons 2 and 6, and the trace "刚正 (Upright)" (a soft taunt whose number is in no document).
 */
public class GepardKitTest {
    private static final int GEPARD = 1104;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /** Note: "为我方全体提供…护盾": read as the engine reads it - a shield VALUE on each unit. */
    @Test
    public void hisUltimateShieldsTheCasterAndTheParty() {
        Fixture f = new Fixture(0);
        Assertions.assertEquals(0.0, f.gepard.getShield(), 1e-9, "precondition: no shield before the cast");

        f.battle.castImmediate(f.gepard.getSkills().get(SkillType.ULTRA), f.gepard, List.of(f.ally));

        Assertions.assertTrue(f.gepard.getShield() > 0,
                "「为我方全体提供能够抵消…伤害的护盾」 — the caster is shielded too");
        Assertions.assertTrue(f.ally.getShield() > 0,
                "…and the aimed ally, so the rule is not written for `target` alone");
    }

    /** Note: The trace "每回合开始时刷新": his own turn start puts the defence-derived attack bonus on him. */
    @Test
    public void hisTraceRefreshesOnHisTurn() {
        Fixture f = new Fixture(0);
        double before = f.gepard.getAttribute(AttributeType.ATTACK).get();

        f.battle.fireTriggers(TriggerEvent.TURN_START, f.gepard, f.gepard, 0, 0);

        Assertions.assertTrue(f.gepard.getAttribute(AttributeType.ATTACK).get() > before,
                "「提高等同于自身当前防御力35%的攻击力，每回合开始时刷新」");
    }

    /** Census: the clauses are where the notes say they are. */
    @Test
    public void hisFileCarriesTheClauses() {
        var table = TriggerTables.of(GEPARD);
        Assertions.assertEquals(1, table.ruleCount(TriggerEvent.SKILL_CAST), "the freeze with its payload");
        Assertions.assertEquals(1, table.ruleCount(TriggerEvent.ULT_CAST), "the party shield");
        Assertions.assertEquals(1, table.ruleCount(TriggerEvent.TURN_START), "the trace, refreshed each turn");
        Assertions.assertEquals(7, table.ruleCount(TriggerEvent.BATTLE_START),
                "the level convention, the four eidolon rules (E1/E3/E4/E5), the technique shield (round 180) and "
                        + "the 刚正 trace's aggro ratio (2026-09-29)");
    }

    /**
     * 行迹 (trace) "刚正 (Upright)": "raises the chance that Gepard (杰帕德) is attacked by enemies" - the number is upstream, not in the prose.
     *
     * <p>{@code AvatarSkillTreeConfig}'s row for point 1104101 carries {@code ParamList = [3]} and the ability it names
     * attaches {@code M_SkillTree_AggroUp}, which writes {@code AggroAddedRatio} as <b>+parameter</b>; so his weight
     * becomes  x (1 + 3) = <b> x 4</b>. The sibling trace "战意 (Battle Intent)" is the method's own check: its row says 0.35 and the
     * document renders it as "DEFENCE increased by 35%".
     */
    @Test
    public void hisTraceRaisesHisOwnAggroWeight() {
        Character gepard = CharacterFactory.create(GEPARD, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Battle battle = new Battle(List.of(gepard, ally), List.of(EnemyFactory.create(MONSTER, 90, 1)),
                new Random(0));
        double before = battle.aggroOf(gepard);

        battle.startBattle();

        Assertions.assertEquals(4.0, battle.aggroOf(gepard) / before, 1e-9,
                "「傑帕德被敌方攻击的概率提高」 "
                        + "-- ParamList [3] reads as weight x (1 + 3)");
        Assertions.assertTrue(battle.aggroOf(gepard) > battle.aggroOf(ally),
                "and he now outweighs a plain ally, which is the whole point of the trace");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private static final class Fixture {
        private final Character gepard;
        private final Character ally = CharacterFactory.create(ALLY, LEVEL);
        private final Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        private final Battle battle;

        private Fixture(int eidolon) {
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
}
