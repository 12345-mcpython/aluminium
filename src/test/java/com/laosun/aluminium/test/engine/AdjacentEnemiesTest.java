package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.trigger.TriggerPayload;
import com.laosun.aluminium.trigger.TriggerTable;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * The {@code adjacent_enemies} selector: <b>both</b> living neighbours of the event's target, in roster order.
 *
 * <p><b>Why it exists.</b> 1109 Hook's Eidolon 4 says 「使指定敌方目标的<b>相邻目标</b>也陷入…灼烧状态」 — every neighbour. The
 * existing `random_adjacent_enemy` answers <b>one</b> of them (its reader, 1103 Serval's Eidolon 1, says 「其中一个随机相邻目标」),
 * and picking one of two would be a wrong number with no symptom.
 *
 * <p>The rule is built in the test ({@code TriggerSpecs}, the factory {@code LethalSetTest} uses) because what is being judged is
 * the selector, not Hook's sentence: the state it applies says exactly who was selected.
 */
public class AdjacentEnemiesTest {
    private static final int OWNER = 1217;
    private static final String MARK = "相邻标记";

    @Test
    public void bothNeighboursOfTheMiddleTargetAreSelected() {
        List<Boolean> marked = fireAt(3, 1);
        Assertions.assertFalse(marked.get(1), "the target itself is not its own neighbour");
        Assertions.assertTrue(marked.get(0), "the left neighbour is");
        Assertions.assertTrue(marked.get(2), "and the right one is too — the sentence says 相邻目标, not 相邻目标之一");
    }

    @Test
    public void anEdgeTargetHasOneNeighbour() {
        List<Boolean> marked = fireAt(3, 0);
        Assertions.assertTrue(marked.get(1), "the only neighbour");
        Assertions.assertFalse(marked.get(2), "the far enemy is not adjacent");
    }

    @Test
    public void aLoneTargetHasNone() {
        List<Boolean> marked = fireAt(1, 0);
        Assertions.assertFalse(marked.getFirst(), "nothing is adjacent to the only enemy");
    }

    // ==================================================================

    /**
     * Fires one {@code BASIC_ATTACK} aimed at {@code centerIndex} and reports, per enemy, whether the rule landed.
     */
    private static List<Boolean> fireAt(int enemyCount, int centerIndex) {
        Character owner = CharacterFactory.create(OWNER, 80);
        List<Enemy> enemies = new ArrayList<>();
        for (int i = 0; i < enemyCount; i++) {
            enemies.add(BattleFixture.enemy(BattleFixture.MONSTER_ID, BattleFixture.MONSTER_LEVEL));
        }

        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "APPLY_BUFF");
        TriggerSpecs.set(effect, "buff", MARK);
        TriggerSpecs.set(effect, "turns", 1);
        TriggerSpecs.set(effect, "target", "adjacent_enemies");
        TriggerSpec rule = TriggerSpecs.rule("BASIC_ATTACK", List.of("actor == self"), effect);
        owner.setTriggerTable(new TriggerTable(OWNER, List.of(rule)));

        Battle battle = new Battle(List.of(owner), List.copyOf(enemies), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        battle.fire(TriggerPayload.Simple.of(TriggerEvent.BASIC_ATTACK, owner, enemies.get(centerIndex), 0, 0));
        battle.processRequests();

        List<Boolean> marked = new ArrayList<>();
        for (Enemy enemy : enemies) {
            marked.add(enemy.getBuffManager().hasState(MARK));
        }
        System.out.println("[adjacent] enemies=" + enemyCount + " centre=" + centerIndex + " marked=" + marked);
        return marked;
    }
}
