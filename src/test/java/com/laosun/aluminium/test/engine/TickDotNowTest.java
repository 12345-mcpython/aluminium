package com.laosun.aluminium.test.engine;


import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.buff.DotBuff;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * {@code TICK_DOT}: "使其当前承受的裂伤状态<b>立即产生 1 次</b>相当于原伤害 85% 的伤害" (1111 Luka's talent).
 *
 * <p>It mirrors {@code Battle.tickDots} - same source, element, {@code DamageType.DOT} and {@code EnergyGrant.KILL_ONLY} - so
 * it is "the state ticked once more", not a new kind of damage. Note: The duration is untouched (the sentence asks for an extra
 * instance of <i>damage</i>), and the layer ceiling is honoured while summing, because "当前承受的…伤害" is what the state
 * is dealing <b>now</b>.
 */
public class TickDotNowTest {
    /** Himeko: no shipped rule file, so the table under test is the only one. */
    private static final int OWNER = 1003;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /** Note: The extra instance is exactly {@code percent} of what the state was dealing. */
    @Test
    public void theExtraInstanceIsAShareOfTheState() {
        Fixture f = new Fixture();
        f.applyDot(true);

        double full = f.battle.tickDots(f.enemy);          // what the state deals in one ordinary tick
        double extra = f.battle.tickDotStateNow(f.enemy, DamageElement.THUNDER, 0.85);

        Assertions.assertTrue(full > 0, "precondition: the DOT deals damage");
        Assertions.assertEquals(full * 0.85, extra, 1e-6,
                "「立即产生 1 次相当于原伤害 85% 的伤害」 -- a share of the state's own damage, through the same settlement");
    }

    /** Note: It is damage, not ageing: the state keeps its remaining turns. */
    @Test
    public void theExtraInstanceDoesNotAgeTheState() {
        Fixture f = new Fixture();
        f.applyDot(true);
        double full = f.battle.tickDots(f.enemy);

        f.battle.tickDotStateNow(f.enemy, DamageElement.THUNDER, 0.85);

        // If the extra instance had consumed or aged the state, an ordinary tick afterwards would deal less (or nothing).
        Assertions.assertEquals(full, f.battle.tickDots(f.enemy), 1e-6,
                "the sentence asks for an extra instance of DAMAGE, not for the state to be consumed or aged");
        Assertions.assertFalse(f.enemy.getBuffManager().allBuffsOf(DotBuff.class).isEmpty(), "…and the state is still there");
    }

    /** Note: Only the named state ticks: a different element's DOT is left alone. */
    @Test
    public void onlyTheNamedStateTicks() {
        Fixture f = new Fixture();
        f.applyDot(true);

        double windTick = f.battle.tickDotStateNow(f.enemy, DamageElement.WIND, 0.85);

        Assertions.assertEquals(0.0, windTick, 1e-9, "no Wind DOT is up, so the Wind state has nothing to settle");
    }

    /** Note: A rule that names no share is refused: "立即产生 1 次" always comes with a percentage or an amount. */
    @Test
    public void aMissingShareIsRefused() {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "TICK_DOT");
        TriggerSpecs.set(effect, "element", "Physical");
        TriggerSpecs.set(effect, "target", "target");
        TriggerSpec wrong = TriggerSpecs.rule("KILL", List.of("actor == self"), effect);

        IllegalArgumentException refused = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(OWNER, List.of(wrong)));
        Assertions.assertTrue(refused.getMessage().contains("percent"), refused.getMessage());
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private static final class Fixture {
        private final Character owner = CharacterFactory.create(OWNER, LEVEL);
        private final Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        private final Battle battle = new Battle(List.of(owner), List.of(enemy), new Random() {
            @Override
            public double nextDouble() {
                return 0.0;
            }
        });

        private Fixture() {
            battle.startBattle();
        }

        /** Applies a Thunder DOT through a rule; {@code caps} selects whether the rule states a layer ceiling. */
        private void applyDot(boolean caps) {
            EffectSpec dot = new EffectSpec();
            TriggerSpecs.set(dot, "op", "APPLY_DOT");
            TriggerSpecs.set(dot, "element", "Thunder");
            TriggerSpecs.set(dot, "percent", 0.5);
            TriggerSpecs.set(dot, "scale", "self_attr:ATTACK");
            TriggerSpecs.set(dot, "turns", 3);
            TriggerSpecs.set(dot, "target", "target");
            if (caps) {
                TriggerSpecs.set(dot, "maxStacks", 1);
            }
            owner.setTriggerTable(new TriggerTable(OWNER, List.of(TriggerSpecs.rule("KILL", List.of("actor == self"), dot))));
            battle.fireTriggers(TriggerEvent.KILL, owner, enemy, 0, 0);
            Assertions.assertFalse(enemy.getBuffManager().allBuffsOf(DotBuff.class).isEmpty(), "precondition: the DOT landed");
        }
    }
}
