"""Regenerate the mooncocoon judge: an unpolluted scene for the deferral, and a real heal for the save.

Measured (previous run): with the healer in the party, the victim came back at 784 HP -- half its Max HP -- so 1211's own
kit had already answered the lethal blow, and my content's `HEALED` rule had then (correctly) removed the trace. That is
the content working; the SCENE was confounded. So:

  * scene A = the passive's owner and the victim only  -> nothing can heal, so 「延后」 and 「否则倒下」 are observable;
  * scene B = owner + healer + victim                  -> the healer's own answer is the heal that ends the trace.
"""
import io

JUDGE = "src/test/java/com/laosun/aluminium/test/MooncocoonTest.java"

io.open(JUDGE, "w", encoding="utf-8", newline="").write('''package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Signal;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1407：「若我方角色受到致命攻击，则…获得【月茧】状态。【月茧】状态下的角色会暂时延后陷入无法战斗状态，
 * 且可以正常行动。若行动后、下一次回合开始前当前生命值提高或获得护盾，则解除【月茧】状态，否则将立即陷入无法战斗状态」 (2026-10-02).
 *
 * <p>⭐ ONE SENTENCE, THREE READINGS: it does not fall; it falls once its own turn is over; a heal before that saves it.
 *
 * <p>⚠ TWO SCENES, because a healing teammate is a confound for the first two readings: measured, 1211's own kit answered
 * the lethal blow and left the victim at half HP, which is a heal, which is exactly what ends the trace. Scene A therefore
 * holds the owner and the victim alone.
 */
public class MooncocoonTest {
    private static final int OWNER = 1407;
    private static final int HEALER = 1211;
    private static final int VICTIM = 1002;
    private static final int MONSTER = 1002011;
    private static final String STATE = "月茧";

    /** ⭐⭐ 「暂时延后陷入无法战斗状态」: the blow is HELD -- the victim is alive, at zero HP, carrying the trace. */
    @Test
    public void theBlowIsHeld() {
        Scene scene = alone();
        strike(scene);
        Assertions.assertFalse(scene.victim.isDeath(), "「不会陷入无法战斗状态」");
        Assertions.assertEquals(0.0, scene.victim.getCurrentHp(), 1e-9, "它真的停在 0 血 —— 那就是「延后」");
        Assertions.assertTrue(scene.victim.getBuffManager().hasState(STATE), "「获得【月茧】状态」");
    }

    /** ⭐⭐ 「行动后…否则将立即陷入无法战斗状态」: nothing saved it, so its own turn's END commits the death. */
    @Test
    public void itsOwnTurnEndsIt() {
        Scene scene = alone();
        strike(scene);
        Assertions.assertFalse(scene.victim.isDeath(), "precondition: the blow was held");
        takeItsTurn(scene);
        Assertions.assertTrue(scene.victim.isDeath(),
                "没有人救它 ⇒ 它自己的回合结束时候倒下（且它确实行动过 ✓）");
    }

    /**
     * ⭐⭐ 「若行动后…当前生命值提高…则解除【月茧】状态」: a real heal ends the trace, and then it does NOT fall.
     *
     * <p>⚠ The heal is 1211's own answer to the same lethal blow -- i.e. a second, shipped reader of `LETHAL_DAMAGE`
     * intervening -- which is what makes this a real end-to-end reading: one character's lethal-damage heal fires
     * `HEALED`, and 1407's trace is removed by it.
     */
    @Test
    public void aHealEndsTheTrace() {
        Scene scene = withHealer();
        strike(scene);
        Assertions.assertTrue(scene.victim.getCurrentHp() > 0, "precondition: the teammate's heal really restored HP");
        Assertions.assertFalse(scene.victim.getBuffManager().hasState(STATE),
                "生命值提高 ⇒ 【月茧】解除");
        takeItsTurn(scene);
        Assertions.assertFalse(scene.victim.isDeath(), "【月茧】已解除 ⇒ 它不再倒下");
    }

    // ==================================================================

    private static final class Scene {
        final Battle battle;
        final Character victim;

        Scene(Battle battle, Character victim) {
            this.battle = battle;
            this.victim = victim;
        }
    }

    /** ⭐ The unpolluted scene: nobody in it can heal, so the deferral is observable on its own. */
    private static Scene alone() {
        Character owner = CharacterFactory.create(OWNER, 80, false, null, null, 0);
        Character victim = CharacterFactory.create(VICTIM, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(owner, victim),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        return new Scene(battle, victim);
    }

    /** The same, plus a teammate whose own kit answers a lethal blow with a heal. */
    private static Scene withHealer() {
        Character owner = CharacterFactory.create(OWNER, 80, false, null, null, 0);
        Character healer = CharacterFactory.create(HEALER, 80, false, null, null, 0);
        Character victim = CharacterFactory.create(VICTIM, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(owner, healer, victim),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        return new Scene(battle, victim);
    }

    private static void strike(Scene scene) {
        scene.battle.applyTrueDamage(scene.battle.enemies.getFirst(), scene.victim,
                DamageElement.ICE, scene.victim.getMaxHp() * 2.0);
        scene.battle.processRequests();
    }

    /** ⚠ A WHOLE turn: the early tick on `beforeMove`, the late tick and TURN_END on `afterMove`. */
    private static void takeItsTurn(Scene scene) {
        Signal signal = scene.battle.queue.snapshot().stream()
                .filter(candidate -> candidate.getCanHit() == scene.victim).findFirst()
                .orElseThrow(() -> new AssertionError("precondition: the unit is in the queue"));
        scene.battle.currentMove = signal;
        scene.battle.beforeMove();
        scene.battle.afterMove();
        scene.battle.processRequests();
    }
}
''')
print("ok   judge regenerated: an unpolluted scene and a real heal")
