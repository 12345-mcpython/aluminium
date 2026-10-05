package com.laosun.aluminium.test.content.characters;

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
 * Lynx (玲可) (1110), from her own file (2026-09-28): [求生反应] (Survival Reaction), the two regenerations, and the party cleanse.
 *
 * <p><b>What it needed.</b> The "share + constant" magnitude on `MODIFY_ATTR`, `APPLY_REGEN`, `DISPEL` over a list, and
 * `target_when` for "if that target holds [求生反应], restores extra".
 */
public class LynxTest {
    private static final int LYNX = 1110;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /** Note: The state lands on the aimed ally, and it raises THEIR Max HP. */
    @Test
    public void herSkillGrantsTheStateAndRaisesMaxHp() {
        Fixture f = new Fixture();
        double before = f.ally.getMaxHp();

        f.skill();

        Assertions.assertTrue(f.ally.getBuffManager().hasState("求生反应"), "「附上【求生反应】」");
        Assertions.assertTrue(f.ally.getMaxHp() > before, "「提高等同于玲可7.50%生命上限+200的生命上限」");
        Assertions.assertFalse(f.lynx.getBuffManager().hasState("求生反应"),
                "⚠ 「指定我方**单体**」: not party-wide");
    }

    /** Her ultimate cleanses the whole side. */
    @Test
    public void herUltimateCleansesTheParty() {
        Fixture f = new Fixture();
        f.ally.getBuffManager().addBuff(new DotBuff(f.lynx, DamageElement.FIRE, 10, 2));
        Assertions.assertTrue(f.ally.getBuffManager().hasState("灼烧"), "precondition: one negative effect");

        f.ultimate();

        Assertions.assertFalse(f.ally.getBuffManager().hasState("灼烧"),
                "「解除我方全体的1个负面效果」 -- the cleanse reaches every ally, not just the aimed one");
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
