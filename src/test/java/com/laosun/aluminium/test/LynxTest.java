package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.data.TriggerTables;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.buff.DotBuff;
import com.laosun.aluminium.models.buff.RegenBuff;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 玲可 (1110), from her own file (2026-09-28): 【求生反应】, the two regenerations, and the party cleanse.
 *
 * <p><b>What it needed.</b> The \u300cshare + constant\u300d magnitude on `MODIFY_ATTR`, `APPLY_REGEN`, `DISPEL` over a list, and
 * `target_when` for \u300c\u82e5\u8be5\u76ee\u6807\u6301\u6709\u3010\u6c42\u751f\u53cd\u5e94\u3011\u5219**\u989d\u5916**\u56de\u590d\u300d.
 */
public class LynxTest {
    private static final int LYNX = 1110;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /** \u26a0 The state lands on the aimed ally, and it raises THEIR Max HP. */
    @Test
    public void herSkillGrantsTheStateAndRaisesMaxHp() {
        Fixture f = new Fixture();
        double before = f.ally.getMaxHp();

        f.skill();

        Assertions.assertTrue(f.ally.getBuffManager().hasState("\u6c42\u751f\u53cd\u5e94"), "\u300c\u9644\u4e0a\u3010\u6c42\u751f\u53cd\u5e94\u3011\u300d");
        Assertions.assertTrue(f.ally.getMaxHp() > before, "\u300c\u63d0\u9ad8\u7b49\u540c\u4e8e\u73b2\u53ef7.50%\u751f\u547d\u4e0a\u9650+200\u7684\u751f\u547d\u4e0a\u9650\u300d");
        Assertions.assertFalse(f.lynx.getBuffManager().hasState("\u6c42\u751f\u53cd\u5e94"),
                "\u26a0 \u300c\u6307\u5b9a\u6211\u65b9**\u5355\u4f53**\u300d: not party-wide");
    }

    /** Her ultimate cleanses the whole side. */
    @Test
    public void herUltimateCleansesTheParty() {
        Fixture f = new Fixture();
        f.ally.getBuffManager().addBuff(new DotBuff(f.lynx, DamageElement.FIRE, 10, 2));
        Assertions.assertTrue(f.ally.getBuffManager().hasState("\u707c\u70e7"), "precondition: one negative effect");

        f.ultimate();

        Assertions.assertFalse(f.ally.getBuffManager().hasState("\u707c\u70e7"),
                "\u300c\u89e3\u9664\u6211\u65b9\u5168\u4f53\u76841\u4e2a\u8d1f\u9762\u6548\u679c\u300d -- the cleanse reaches every ally, not just the aimed one");
    }

    /** Census: the clauses are where the notes say they are. */
    @Test
    public void herFileCarriesTheClauses() {
        var table = TriggerTables.of(LYNX);
        Assertions.assertEquals(2, table.ruleCount(TriggerEvent.SKILL_CAST), "the state and the base regeneration");
        Assertions.assertEquals(2, table.ruleCount(TriggerEvent.ULT_CAST), "the cleanse and the regeneration half");
        Assertions.assertEquals(1, table.ruleCount(TriggerEvent.BATTLE_START), "the level convention");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private static final class Fixture {
        private final Character lynx = CharacterFactory.create(LYNX, LEVEL);
        private final Character ally = CharacterFactory.create(ALLY, LEVEL);
        private final Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        private final Battle battle = new Battle(List.of(lynx, ally), List.of(enemy), fixed());

        private Fixture() {
            battle.startBattle();
        }

        private void skill() {
            battle.castImmediate(lynx.getSkills().get(SkillType.SKILL), lynx, List.of(ally));
        }

        private void ultimate() {
            battle.castImmediate(lynx.getSkills().get(SkillType.ULTRA), lynx, List.of(ally, lynx));
        }
    }

    private static Random fixed() {
        return new Random() {
            @Override
            public double nextDouble() {
                return 0.0;
            }
        };
    }
}
