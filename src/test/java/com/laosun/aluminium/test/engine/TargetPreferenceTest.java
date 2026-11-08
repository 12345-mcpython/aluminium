package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.trigger.TriggerInterpreter;
import com.laosun.aluminium.trigger.TriggerPayload;
import com.laosun.aluminium.trigger.TriggerTable;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code prefer}: 「本次伤害<b>优先选择</b>韧性值大于0且有火属性弱点的目标」 (1222 Lingsha (灵砂)'s talent).
 *
 * <p><b>Why this is not {@code target_when}.</b> A filter EXCLUDES: an empty filter means "no target", which is the wrong reading of
 * 「优先」 - whenever no enemy carries the preferred property the clause would do nothing at all, while the document says the attack
 * still lands on somebody. A preference therefore narrows the <b>draw</b> and falls back to the whole side when nobody qualifies.
 *
 * <p>Both halves are pinned here: the draw really comes from the preferred pool, and an empty pool still draws.
 */
public class TargetPreferenceTest {
    private static final int OWNER = 1001;
    private static final int LEVEL = 80;

    @Test
    public void theDrawComesFromThePreferredPool() {
        Battle battle = scene(List.of("target has_weakness Fire"));
        List<Enemy> enemies = battle.enemies().stream().map(e -> (Enemy) e).toList();
        enemies.get(1).addWeakness(DamageElement.FIRE);

        for (int draw = 0; draw < 6; draw++) {
            fire(battle);
        }

        Assertions.assertTrue(enemies.get(1).getCurrentHp() < enemies.get(1).getMaxHp(), "the fire-weak enemy took the hits");
        Assertions.assertEquals(enemies.get(0).getCurrentHp(), enemies.get(0).getMaxHp(), 1e-9,
                "…and nobody else did: every draw came from the preferred pool");
        Assertions.assertEquals(enemies.get(2).getCurrentHp(), enemies.get(2).getMaxHp(), 1e-9, "nor the third");
    }

    /**
     * 「优先」 is not 「只打」: with nobody satisfying the preference the damage still lands somewhere.
     */
    @Test
    public void anEmptyPreferredPoolStillDrawsFromEveryone() {
        Battle battle = scene(List.of("target has_weakness Fire"));
        fire(battle);

        double lost = 0;
        for (var unit : battle.enemies()) {
            lost += unit.getMaxHp() - unit.getCurrentHp();
        }
        Assertions.assertTrue(lost > 0,
                "no enemy is fire-weak, and the strike still lands (a filter would have settled nothing at all)");
    }

    /**
     * The field is refused where nothing would consult it: on any selector but {@code random_enemy} there is no draw to narrow, so
     * the effect would carry a condition that reads as one and behaves like a comment.
     */
    @Test
    public void aPreferenceOnAnotherSelectorIsRefusedAtLoadTime() {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "DAMAGE");
        TriggerSpecs.set(effect, "scale", "self_attr:ATTACK");
        TriggerSpecs.set(effect, "percent", 0.1);
        TriggerSpecs.set(effect, "element", "Fire");
        TriggerSpecs.set(effect, "target", "all_enemies");
        TriggerSpecs.set(effect, "prefer", List.of("target has_weakness Fire"));

        IllegalArgumentException refused = Assertions.assertThrows(IllegalArgumentException.class,
                () -> TriggerInterpreter.validate(effect, TriggerSpecs.rule("ALLY_ATTACK", List.of(), effect)));
        Assertions.assertTrue(refused.getMessage().contains("random_enemy"), refused.getMessage());
    }

    private static void fire(Battle battle) {
        battle.fire(TriggerPayload.Simple.of(TriggerEvent.ALLY_ATTACK, battle.characters().getFirst(),
                battle.enemies().getFirst(), 1, 0));
        battle.processRequests();
    }

    private static Battle scene(List<String> prefer) {
        Character owner = CharacterFactory.create(OWNER, LEVEL);
        owner.setTriggerTable(new TriggerTable(OWNER, List.of(rule(prefer))));
        List<Enemy> enemies = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            enemies.add(BattleFixture.dummy());
        }
        Battle battle = new Battle(List.of(owner), enemies, BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        battle.processRequests();
        return battle;
    }

    /** 10% of ATK at a random enemy, preferring one that satisfies the given conditions. */
    private static TriggerSpec rule(List<String> prefer) {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "DAMAGE");
        TriggerSpecs.set(effect, "scale", "self_attr:ATTACK");
        TriggerSpecs.set(effect, "percent", 0.1);
        TriggerSpecs.set(effect, "element", "Fire");
        TriggerSpecs.set(effect, "target", "random_enemy");
        TriggerSpecs.set(effect, "prefer", prefer);
        return TriggerSpecs.rule("ALLY_ATTACK", List.of(), effect);
    }
}
