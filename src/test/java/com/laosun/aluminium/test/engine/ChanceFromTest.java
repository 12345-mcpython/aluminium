package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.NumberProvider;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.buff.DotBuff;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.trigger.TriggerPayload;
import com.laosun.aluminium.trigger.TriggerTable;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * {@code chance_from}: a rule whose probability is computed at the moment of the roll.
 *
 * <p>Reader: 1305 Dr. Ratio's talent — 「目标每有 1 个负面效果，追加攻击发动的固定概率提高 20%」. The static {@code chance} field is read once
 * at load time, so a rule like that one had nowhere to live: the engine could say "40%" but not "40% plus 20% per debuff".
 *
 * <p>The rule here is built with {@code TriggerSpecs} so what is judged is the resolution, not Ratio's sentence: a provider that
 * resolves to 0 must never fire, one that resolves to 1 must always fire, and the clamp keeps 1.4 from meaning "more than always".
 */
public class ChanceFromTest {
    private static final int OWNER = 1305;
    private static final int ATTEMPTS = 4;

    @Test
    public void aProviderChanceIsResolvedPerFiring() {
        Assertions.assertEquals(0, firings(0), "0 × 0.2 = 0 ⇒ never fires");
        Assertions.assertEquals(ATTEMPTS, firings(10), "clamped to 1 ⇒ every attempt fires");
        int middle = firings(3);
        Assertions.assertTrue(middle > 0 && middle < ATTEMPTS,
                "3 debuffs at 0.2 each = 0.6 ⇒ some but not all of four attempts fire (got " + middle + ")");
    }

    // ==================================================================

    /** How many times the rule fired over {@link #ATTEMPTS} attempts, with {@code debuffs} negative effects on the enemy. */
    private static int firings(int debuffs) {
        Character owner = CharacterFactory.create(OWNER, 80);
        Enemy enemy = BattleFixture.enemy(BattleFixture.MONSTER_ID, BattleFixture.MONSTER_LEVEL);
        Battle battle = new Battle(List.of(owner), List.of(enemy), BattleFixture.seeded());
        owner.setTriggerTable(new TriggerTable(OWNER, List.of(rule())));
        battle.startBattle();
        battle.processRequests();

        for (int i = 0; i < debuffs; i++) {
            enemy.getBuffManager().addBuff(new DotBuff(owner, DamageElement.QUANTUM, 10, 3, 1, "灼烧" + i));
        }

        int[] fired = {0};
        battle.addDamageObserver((damage, applied, hpLoss, shieldAbsorbed, died) -> fired[0]++);
        for (int i = 0; i < ATTEMPTS; i++) {
            battle.fire(TriggerPayload.Simple.of(TriggerEvent.ALLY_ATTACK, owner, enemy, 0, 0));
            battle.processRequests();
        }
        return fired[0];
    }

    private static TriggerSpec rule() {
        EffectSpec damage = new EffectSpec();
        TriggerSpecs.set(damage, "op", "DAMAGE");
        TriggerSpecs.set(damage, "scale", "self_attr:ATTACK");
        TriggerSpecs.set(damage, "percent", 1.0);
        TriggerSpecs.set(damage, "element", "Imaginary");
        TriggerSpecs.set(damage, "target", "target");
        TriggerSpec spec = TriggerSpecs.rule("ALLY_ATTACK", List.of(), damage);
        TriggerSpecs.set(spec, "chanceFrom", provider());
        return spec;
    }

    /** {@code 0.2 × debuffs(target)} — the arithmetic under judgement is the resolution, not the formula. */
    private static NumberProvider provider() {
        NumberProvider debuffs = new NumberProvider();
        debuffs.setType("debuffs");
        debuffs.setWhose("target");
        NumberProvider scale = new NumberProvider();
        scale.setType("const");
        scale.setValue(0.2);
        NumberProvider product = new NumberProvider();
        product.setType("mul");
        product.setInputs(List.of(debuffs, scale));
        return product;
    }
}
