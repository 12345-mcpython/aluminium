package com.laosun.aluminium.test.trigger;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.trigger.TriggerTable;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Gap witness: one random pick cannot yet be <b>shared</b> by several effects of one firing.
 *
 * <p>Readers: 1111 卢卡's technique (「对敌方**随机单体**造成伤害，并有…使**目标**陷入与战技相同的裂伤」 - the damage and the
 * bleed must land on the SAME randomly chosen enemy) and 1308 黄泉's 【四相断我】. Today {@code random_enemy} is resolved per
 * effect, so two effects roll separately; the only spelling that would bind them is a rule-level pick, and {@code "pick"}
 * is not a selector the loader accepts.
 *
 * <p>The test pins today's reading in both directions: two {@code random_enemy} effects CAN disagree, and {@code "pick"}
 * is refused. The day a rule-level pick exists, the second assertion turns red.
 */
public class RandomPickBindingGapTest {

    private static final int OWNER = 1002;
    private static final int LEVEL = 80;

    /**
     * Two {@code random_enemy} effects of one rule draw independently - the reason a binding spelling is needed.
     */
    @Test
    public void twoRandomEffectsRollSeparately() {
        // Over several seeds some firing must hit two DIFFERENT enemies; with one enemy the counts could never differ.
        boolean disagreed = false;
        for (int seed = 0; seed < 12 && !disagreed; seed++) {
            Character owner = CharacterFactory.create(OWNER, LEVEL);
            EffectSpec first = damage();
            EffectSpec second = damage();
            TriggerSpec rule = TriggerSpecs.rule("BATTLE_START", List.of(), first, second);
            TriggerSpecs.set(rule, "id", "witness_two_random_effects");
            owner.setTriggerTable(new TriggerTable(OWNER, List.of(rule)));
            Enemy a = BattleFixture.monster();
            Enemy b = BattleFixture.monster();
            List<Enemy> enemies = new ArrayList<>(List.of(a, b));
            Battle battle = new Battle(new ArrayList<>(List.of(owner)), enemies, new java.util.Random(seed));
            battle.startBattle();
            boolean aHit = a.getCurrentHp() < a.getMaxHp();
            boolean bHit = b.getCurrentHp() < b.getMaxHp();
            disagreed = aHit && bHit;   // both took one hit each -> the two effects chose different enemies
        }
        Assertions.assertTrue(disagreed,
                "with independent rolls the two effects can hit DIFFERENT enemies - that is what a shared pick would fix");
    }

    /**
     * {@code "pick"} is not a selector: a rule that names it is refused at load time.
     */
    @Test
    public void theBindingSpellingDoesNotExist() {
        EffectSpec effect = damage();
        TriggerSpecs.set(effect, "target", "pick");
        TriggerSpec rule = TriggerSpecs.rule("BATTLE_START", List.of(), effect);
        TriggerSpecs.set(rule, "id", "witness_pick");
        IllegalArgumentException refused = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(OWNER, List.of(rule)),
                "if this stops throwing, a rule-level pick exists and 1111's technique can be written");
        System.out.println("[pick] refused as expected: " + refused.getMessage());
    }

    private static EffectSpec damage() {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "DAMAGE");
        TriggerSpecs.set(effect, "scale", "self_attr:ATTACK");
        TriggerSpecs.set(effect, "percent", 0.5);
        TriggerSpecs.set(effect, "element", "Physical");
        TriggerSpecs.set(effect, "target", "random_enemy");
        return effect;
    }
}

