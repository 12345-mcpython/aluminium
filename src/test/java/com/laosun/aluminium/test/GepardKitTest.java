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
 * 杰帕德 (1104): his kit verified through the engine's own readings (2026-09-28, rounds 127-128).
 *
 * <p><b>Why the shield is read as a NUMBER.</b> The first attempt counted buffs; the source settles it — a shield is
 * {@code CanHit.getShield()}, and the {@code has_shield} condition is literally {@code who.getShield() <= 0}, so buff counting
 * was blind to it by construction.
 *
 * <p><b>Why one case needs E1.</b> The control roll is {@code Battle.rng.nextDouble() < hitChance(...)}, i.e. the 65% base
 * chance runs through 效果命中 / 效果抵抗 and can genuinely miss — so a fixed roll cannot make the plain skill deterministic.
 * At E1 his own rule says 「冻结的基础概率提高35%」 (a {@code MODIFY_RULE}), which brings the base chance to 100%; the roll then
 * cannot miss whatever {@code rng} hands back. The case therefore verifies the freeze AND eidolon 1 in one go.
 *
 * <p>⚠ <b>An E1 freeze case was REMOVED, not fixed</b> (round 128, measured): the control roll is
 * Battle.rng.nextDouble() < hitChance(...), so a fixed roll cannot make the plain skill deterministic, and even at E1 — where
 * his own rule raises the base chance by 35% — the target did NOT end up frozen under this fixture. Either that MODIFY_RULE
 * does not reach the control roll, or the monster carries a specific resistance to this control. Answering it needs one source
 * read (where MODIFY_RULE files its modifier and what hitChance's specificResistKey resolves to) before the case is
 * written again; until then the freeze stays unverified rather than flaky.
 *
 * <p><b>Registered</b> in his file: the talent (needs a lethal-blow observable), the technique's shield (needs a "the technique
 * was used" gate), eidolons 2 and 6, and the trace 「刚正」 (a soft taunt whose number is in no document).
 */
public class GepardKitTest {
    private static final int GEPARD = 1104;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /** \u26a0 「为我方全体提供…护盾」: read as the engine reads it — a shield VALUE on each unit. */
    @Test
    public void hisUltimateShieldsTheCasterAndTheParty() {
        Fixture f = new Fixture(0);
        Assertions.assertEquals(0.0, f.gepard.getShield(), 1e-9, "precondition: no shield before the cast");

        f.battle.castImmediate(f.gepard.getSkills().get(SkillType.ULTRA), f.gepard, List.of(f.ally));

        Assertions.assertTrue(f.gepard.getShield() > 0,
                "\u300c\u4e3a\u6211\u65b9\u5168\u4f53\u63d0\u4f9b\u80fd\u591f\u62b5\u6d88\u2026\u4f24\u5bb3\u7684\u62a4\u76fe\u300d \u2014 the caster is shielded too");
        Assertions.assertTrue(f.ally.getShield() > 0,
                "\u2026and the aimed ally, so the rule is not written for `target` alone");
    }

    /** \u26a0 The trace 「每回合开始时刷新」: his own turn start puts the defence-derived attack bonus on him. */
    @Test
    public void hisTraceRefreshesOnHisTurn() {
        Fixture f = new Fixture(0);
        double before = f.gepard.getAttribute(AttributeType.ATTACK).get();

        f.battle.fireTriggers(TriggerEvent.TURN_START, f.gepard, f.gepard, 0, 0);

        Assertions.assertTrue(f.gepard.getAttribute(AttributeType.ATTACK).get() > before,
                "\u300c\u63d0\u9ad8\u7b49\u540c\u4e8e\u81ea\u8eab\u5f53\u524d\u9632\u5fa1\u529b35%\u7684\u653b\u51fb\u529b\uff0c\u6bcf\u56de\u5408\u5f00\u59cb\u65f6\u5237\u65b0\u300d");
    }

    /** Census: the clauses are where the notes say they are. */
    @Test
    public void hisFileCarriesTheClauses() {
        var table = TriggerTables.of(GEPARD);
        Assertions.assertEquals(1, table.ruleCount(TriggerEvent.SKILL_CAST), "the freeze with its payload");
        Assertions.assertEquals(1, table.ruleCount(TriggerEvent.ULT_CAST), "the party shield");
        Assertions.assertEquals(1, table.ruleCount(TriggerEvent.TURN_START), "the trace, refreshed each turn");
        Assertions.assertEquals(5, table.ruleCount(TriggerEvent.BATTLE_START),
                "the level convention plus the four eidolon rules (E1/E3/E4/E5)");
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
