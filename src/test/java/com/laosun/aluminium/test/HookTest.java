package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.data.TriggerTables;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.buff.DotBuff;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 虎克 (1109): his burn and the traces/eidolons that shipped with it (2026-09-28, round 134).
 *
 * <p><b>Why a hand-made target.</b> The project's fixture monster resists controls outright (round 131), and the burn is rolled
 * too — `APPLY_DOT` runs `tryApplyDebuff` with the document's base chance — so an unresisting target is what makes the test
 * deterministic rather than lucky.
 */
public class HookTest {
    private static final int HOOK = 1109;
    private static final int LEVEL = 80;

    /** \u26a0 His skill's burn lands as a DOT with the document's magnitude, and the trace pays energy on the ultimate. */
    @Test
    public void hisBurnLandsAndHisUltimateTracePaysEnergy() {
        Fixture f = new Fixture(0);
        Assertions.assertFalse(f.enemy.getBuffManager().hasState("灼烧"), "precondition: not burning yet");

        f.battle.castImmediate(f.hook.getSkills().get(SkillType.SKILL), f.hook, List.of(f.enemy));

        Assertions.assertTrue(f.enemy.getBuffManager().hasState("灼烧"),
                "\u300c\u6709100%\u7684\u57fa\u7840\u6982\u7387\u4f7f\u5176\u9677\u5165\u707c\u70e7\u72b6\u6001\u300d");
        Assertions.assertFalse(f.enemy.getBuffManager().allBuffsOf(DotBuff.class).isEmpty(), "\u2026as a damage-over-time state");

        double energyBefore = f.hook.getCurrentEnergy();
        f.battle.castImmediate(f.hook.getSkills().get(SkillType.ULTRA), f.hook, List.of(f.enemy));

        Assertions.assertTrue(f.hook.getCurrentEnergy() > energyBefore,
                "\u300c\u65bd\u653e\u7ec8\u7ed3\u6280\u540e\uff0c\u864e\u514b\u7684\u884c\u52a8\u63d0\u524d20%\u5e76\u989d\u5916\u6062\u590d5\u70b9\u80fd\u91cf\u300d \u2014 the trace pays on the cast");
    }

    /** Census: the burn, the level convention, and the five traces/eidolons are all there. */
    @Test
    public void hisFileCarriesTheClauses() {
        var table = TriggerTables.of(HOOK);
        Assertions.assertEquals(1, table.ruleCount(TriggerEvent.SKILL_CAST), "the burn");
        Assertions.assertEquals(1, table.ruleCount(TriggerEvent.ULT_CAST), "the trace that follows the ultimate");
        Assertions.assertEquals(1, table.ruleCount(TriggerEvent.DEALING_DAMAGE), "E6's bonus against a burning target");
        Assertions.assertEquals(4, table.ruleCount(TriggerEvent.BATTLE_START),
                "the level convention plus E2/E3/E5");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private static final class Fixture {
        private final Character hook;
        private final Enemy enemy = Enemy.fromAttributes("Test Dummy", 20000, 100, 100, 90);
        private final Battle battle;

        private Fixture(int eidolon) {
            hook = CharacterFactory.create(HOOK, LEVEL, true, null, null, eidolon);
            battle = new Battle(List.of(hook), List.of(enemy), new Random() {
                @Override
                public double nextDouble() {
                    return 0.0;
                }
            });
            battle.startBattle();
        }
    }
}
