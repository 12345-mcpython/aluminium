package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.buff.DotBuff;
import com.laosun.aluminium.buff.StateBuff;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
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

import java.util.List;

/**
 * {@code target_buff_count}: how many of the target's buffs a 「解除增益效果」 would <b>really</b> take away.
 *
 * <p><b>Why the variable exists.</b> 1106 Pela's trace 追歼 and her Eidolon 2 both read 「施放战技<b>解除增益效果时</b>…」. Before
 * this, the only related number was {@code target_debuff_count} — the <b>other</b> half — and a {@code has_state} could not
 * stand in, because a skill may strip a modifier that no state name covers and because the state is gone by the time the
 * later rules of the same event run.
 *
 * <p><b>What it must count</b> is exactly what {@code BuffManager.removeBuffs} removes: anything that is neither a negative
 * effect nor a permanent loadout bonus. The four cases below are that predicate, one each — a temporary state counts, a
 * permanent one does not, a DOT does not, and nothing at all does not.
 *
 * <p>The rule is built in the test ({@code TriggerSpecs}, the factory {@code LethalSetTest} uses) because what is being judged
 * is the variable, not Pela's sentence: each firing raises the owner's speed, so the attribute says whether it fired.
 */
public class TargetBuffCountTest {
    private static final int OWNER = 1217;
    private static final int LEVEL = 80;

    @Test
    public void aTemporaryBuffCounts() {
        Assertions.assertTrue(fires(scene -> scene.enemy.getBuffManager().addBuff(new StateBuff("敌方增益", 3))),
                "「解除增益效果」 would remove it, so the count is 1");
    }

    @Test
    public void anEmptyTargetDoesNot() {
        Assertions.assertFalse(fires(scene -> {
        }), "nothing to dispel ⇒ 0");
    }

    @Test
    public void aPermanentBuffDoesNot() {
        Assertions.assertFalse(fires(scene ->
                        scene.enemy.getBuffManager().addBuff(new StateBuff("永久增益", 99, true))),
                "a relic bonus or a trace is not a 「增益效果」 in the sense these sentences use");
    }

    @Test
    public void aNegativeEffectDoesNot() {
        Assertions.assertFalse(fires(scene -> scene.enemy.getBuffManager().addBuff(
                        new DotBuff(scene.owner, DamageElement.FIRE, 1, 3))),
                "a DOT is the other half (`target_debuff_count`), not a 「增益效果」");
    }

    // ==================================================================

    private static boolean fires(java.util.function.Consumer<Scene> setup) {
        Character owner = CharacterFactory.create(OWNER, LEVEL);
        Enemy enemy = BattleFixture.enemy(BattleFixture.MONSTER_ID, BattleFixture.MONSTER_LEVEL);

        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "MODIFY_ATTR");
        TriggerSpecs.set(effect, "attribute", "SPEED");
        TriggerSpecs.set(effect, "amount", 100.0);
        TriggerSpecs.set(effect, "turns", 1);
        TriggerSpecs.set(effect, "target", "self");
        TriggerSpec rule = TriggerSpecs.rule("SKILL_CAST", List.of("target_buff_count >= 1"), effect);
        owner.setTriggerTable(new TriggerTable(OWNER, List.of(rule)));

        Battle battle = new Battle(List.of(owner), List.of(enemy), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        Scene scene = new Scene(battle, owner, enemy);
        setup.accept(scene);

        double before = owner.getAttribute(AttributeType.SPEED).get();
        battle.fire(TriggerPayload.Simple.of(TriggerEvent.SKILL_CAST, owner, enemy, 0, 0));
        battle.processRequests();
        boolean fired = Math.abs(owner.getAttribute(AttributeType.SPEED).get() - before) > 1e-9;
        System.out.println("[target_buff_count] fired=" + fired + " dispellable="
                + enemy.getBuffManager().dispellableBuffCount());
        return fired;
    }

    private record Scene(Battle battle, Character owner, Enemy enemy) {
    }
}
