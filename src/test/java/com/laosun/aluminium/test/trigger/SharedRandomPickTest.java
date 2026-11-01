package com.laosun.aluminium.test.trigger;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.trigger.TriggerTable;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * The rule-level {@code pick}: one selector resolved <b>once per firing</b>, so several effects can name the same
 * randomly chosen unit.
 *
 * <p>Reader: 1111 Luka's technique (「对敌方**随机单体**造成伤害，并有…使**该目标**陷入与战技相同的裂伤」 - damage and bleed must
 * land on one enemy). Without it, {@code random_enemy} is resolved per effect and the two halves would roll separately.
 */
public class SharedRandomPickTest {

    private static final int OWNER = 1002;
    private static final int LEVEL = 80;

    /**
     * Two effects that both say {@code target: "pick"} always hit the SAME enemy, and the pick is one enemy (not both).
     */
    @Test
    public void onePickIsSharedByBothEffects() {
        for (int seed = 0; seed < 12; seed++) {
            Character owner = CharacterFactory.create(OWNER, LEVEL);
            TriggerSpec rule = TriggerSpecs.rule("BATTLE_START", List.of(), damage(), damage());
            TriggerSpecs.set(rule, "id", "witness_shared_pick");
            TriggerSpecs.set(rule, "pick", "random_enemy");
            owner.setTriggerTable(new TriggerTable(OWNER, List.of(rule)));
            Enemy a = BattleFixture.monster();
            Enemy b = BattleFixture.monster();
            Battle battle = new Battle(new ArrayList<>(List.of(owner)),
                    new ArrayList<>(List.of(a, b)), new java.util.Random(seed));
            battle.startBattle();

            boolean aHit = a.getCurrentHp() < a.getMaxHp();
            boolean bHit = b.getCurrentHp() < b.getMaxHp();
            Assertions.assertNotEquals(aHit, bHit,
                    "seed " + seed + ": exactly one enemy takes both hits - a shared pick, not two rolls");
            Assertions.assertTrue(aHit || bHit, "seed " + seed + ": the pick resolved to a living enemy");
        }
    }

    /**
     * A {@code target: "pick"} without a rule-level {@code pick} is refused, and a {@code pick} no effect reads is refused.
     */
    @Test
    public void bothHalvesOfTheSpellingAreRequired() {
        EffectSpec effect = damage();
        TriggerSpec withoutPick = TriggerSpecs.rule("BATTLE_START", List.of(), effect);
        TriggerSpecs.set(withoutPick, "id", "witness_no_pick");
        IllegalArgumentException noPick = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(OWNER, List.of(withoutPick)),
                "a target of \"pick\" with no pick says nothing about which unit it is");
        Assertions.assertTrue(noPick.getMessage().contains("pick"), noPick.getMessage());

        EffectSpec ordinary = damage();
        TriggerSpecs.set(ordinary, "target", "self");
        TriggerSpec unusedPick = TriggerSpecs.rule("BATTLE_START", List.of(), ordinary);
        TriggerSpecs.set(unusedPick, "id", "witness_unused_pick");
        TriggerSpecs.set(unusedPick, "pick", "random_enemy");
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(OWNER, List.of(unusedPick)),
                "a pick no effect reads would be rolled and thrown away");
    }

    private static EffectSpec damage() {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "DAMAGE");
        TriggerSpecs.set(effect, "scale", "self_attr:ATTACK");
        TriggerSpecs.set(effect, "percent", 0.5);
        TriggerSpecs.set(effect, "element", "Physical");
        TriggerSpecs.set(effect, "target", "pick");
        return effect;
    }
}
