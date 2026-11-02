package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.buff.StackBuff;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.AttributeType;
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
 * The two "pick by a COUNTER" spellings 1218 Jiaoqiu's Eidolon 6 and trace 炙香 needed.
 *
 * <ul>
 *   <li>{@code lowest_stacks_enemy:<state>} — a target selector: 「叠加给场上存活的【烬煨】层数最低的敌人」.</li>
 *   <li>{@code {"type": "stacks", "whose": "highest_enemy", "buff": ...}} — a NUMBER: 「层数与…【烬煨】层数最高者相同」.</li>
 * </ul>
 *
 * <p>They are different questions (which unit vs how many) and both are built here with {@code TriggerSpecs} because what is
 * being judged is the vocabulary, not Jiaoqiu's sentence.
 */
public class StacksRankingTest {
    private static final int OWNER = 1218;
    private static final String MARK = "烬煨";

    @Test
    public void theLowestStackedEnemyIsSelected() {
        Scene scene = new Scene(3, 1, 3, 2);
        scene.fireSelectingRule();
        Assertions.assertEquals(100.0, scene.speedGain(scene.enemies.get(1)), 1e-9,
                "the enemy with 1 layer is the lowest");
        Assertions.assertEquals(0.0, scene.speedGain(scene.enemies.get(0)), 1e-9, "...not the one with 3");
        Assertions.assertEquals(0.0, scene.speedGain(scene.enemies.get(2)), 1e-9, "...nor the one with 2");
    }

    @Test
    public void tiesKeepRosterOrder() {
        Scene scene = new Scene(2, 2, 5);
        scene.fireSelectingRule();
        Assertions.assertEquals(100.0, scene.speedGain(scene.enemies.get(0)), 1e-9,
                "two enemies tie at 2 layers ⇒ the earlier one in roster order answers");
        Assertions.assertEquals(0.0, scene.speedGain(scene.enemies.get(1)), 1e-9, "the later one does not");
    }

    @Test
    public void theHighestCountIsAQuantity() {
        Scene scene = new Scene(3, 1, 3, 2);
        Assertions.assertEquals(3.0, scene.fireCountingRule(), 1e-9,
                "「层数最高者」 is 3 — a number, not the unit that holds it");
    }

    // ==================================================================

    private static final class Scene {
        private final Character owner = CharacterFactory.create(OWNER, 80);
        private final List<Enemy> enemies = new ArrayList<>();
        private final Battle battle;
        private final double[] speedBefore;

        private Scene(int... stacks) {
            for (int count : stacks) {
                Enemy enemy = BattleFixture.enemy(BattleFixture.MONSTER_ID, BattleFixture.MONSTER_LEVEL);
                for (int i = 0; i < count; i++) {
                    enemy.getBuffManager().addBuff(new StackBuff(MARK, 2, 99));
                }
                enemies.add(enemy);
            }
            battle = new Battle(List.of(owner), List.copyOf(enemies), BattleFixture.seeded());
            battle.startBattle();
            battle.processRequests();
            speedBefore = new double[enemies.size()];
            for (int i = 0; i < enemies.size(); i++) {
                speedBefore[i] = enemies.get(i).getAttribute(AttributeType.SPEED).get();
            }
        }

        private void fireSelectingRule() {
            EffectSpec effect = new EffectSpec();
            TriggerSpecs.set(effect, "op", "MODIFY_ATTR");
            TriggerSpecs.set(effect, "attribute", "SPEED");
            TriggerSpecs.set(effect, "amount", 100.0);
            TriggerSpecs.set(effect, "turns", 1);
            TriggerSpecs.set(effect, "target", "lowest_stacks_enemy:" + MARK);
            owner.setTriggerTable(new TriggerTable(OWNER, List.of(
                    TriggerSpecs.rule("BASIC_ATTACK", List.of("actor == self"), effect))));
            battle.fire(TriggerPayload.Simple.of(TriggerEvent.BASIC_ATTACK, owner, enemies.getFirst(), 0, 0));
            battle.processRequests();
        }

        private double fireCountingRule() {
            EffectSpec effect = new EffectSpec();
            TriggerSpecs.set(effect, "op", "ADD_STACK");
            TriggerSpecs.set(effect, "buff", "副本");
            com.laosun.aluminium.beans.NumberProvider provider = new com.laosun.aluminium.beans.NumberProvider();
            TriggerSpecs.set(provider, "type", "stacks");
            TriggerSpecs.set(provider, "whose", "highest_enemy");
            TriggerSpecs.set(provider, "buff", MARK);
            TriggerSpecs.set(effect, "value", provider);
            TriggerSpecs.set(effect, "maxStacks", 99);
            TriggerSpecs.set(effect, "turns", 2);
            TriggerSpecs.set(effect, "target", "target");
            owner.setTriggerTable(new TriggerTable(OWNER, List.of(
                    TriggerSpecs.rule("BASIC_ATTACK", List.of("actor == self"), effect))));
            battle.fire(TriggerPayload.Simple.of(TriggerEvent.BASIC_ATTACK, owner, enemies.getFirst(), 0, 0));
            battle.processRequests();
            return enemies.getFirst().getBuffManager().stacksOf("副本");
        }

        private double speedGain(Enemy enemy) {
            int index = enemies.indexOf(enemy);
            return enemy.getAttribute(AttributeType.SPEED).get() - speedBefore[index];
        }
    }
}



