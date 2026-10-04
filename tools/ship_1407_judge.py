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
 * 1407\uff1a\u300c\u82e5\u6211\u65b9\u89d2\u8272\u53d7\u5230\u81f4\u547d\u653b\u51fb\uff0c\u5219\u2026\u83b7\u5f97\u3010\u6708\u8327\u3011\u72b6\u6001\u3002\u3010\u6708\u8327\u3011\u72b6\u6001\u4e0b\u7684\u89d2\u8272\u4f1a\u6682\u65f6\u5ef6\u540e\u9677\u5165\u65e0\u6cd5\u6218\u6597\u72b6\u6001\uff0c
 * \u4e14\u53ef\u4ee5\u6b63\u5e38\u884c\u52a8\u3002\u82e5\u884c\u52a8\u540e\u3001\u4e0b\u4e00\u6b21\u56de\u5408\u5f00\u59cb\u524d\u5f53\u524d\u751f\u547d\u503c\u63d0\u9ad8\u6216\u83b7\u5f97\u62a4\u76fe\uff0c\u5219\u89e3\u9664\u3010\u6708\u8327\u3011\u72b6\u6001\uff0c\u5426\u5219\u5c06\u7acb\u5373\u9677\u5165\u65e0\u6cd5\u6218\u6597\u72b6\u6001\u300d (2026-10-02).
 *
 * <p>\u2b50 ONE SENTENCE, THREE READINGS: it does not fall; it falls once its own turn is over; a heal before that saves it.
 *
 * <p>\u26a0 TWO SCENES, because a healing teammate is a confound for the first two readings: measured, 1211's own kit answered
 * the lethal blow and left the victim at half HP, which is a heal, which is exactly what ends the trace. Scene A therefore
 * holds the owner and the victim alone.
 */
public class MooncocoonTest {
    private static final int OWNER = 1407;
    private static final int HEALER = 1211;
    private static final int VICTIM = 1002;
    private static final int MONSTER = 1002011;
    private static final String STATE = "\u6708\u8327";

    /** \u2b50\u2b50 \u300c\u6682\u65f6\u5ef6\u540e\u9677\u5165\u65e0\u6cd5\u6218\u6597\u72b6\u6001\u300d: the blow is HELD -- the victim is alive, at zero HP, carrying the trace. */
    @Test
    public void theBlowIsHeld() {
        Scene scene = alone();
        strike(scene);
        Assertions.assertFalse(scene.victim.isDeath(), "\u300c\u4e0d\u4f1a\u9677\u5165\u65e0\u6cd5\u6218\u6597\u72b6\u6001\u300d");
        Assertions.assertEquals(0.0, scene.victim.getCurrentHp(), 1e-9, "\u5b83\u771f\u7684\u505c\u5728 0 \u8840 \u2014\u2014 \u90a3\u5c31\u662f\u300c\u5ef6\u540e\u300d");
        Assertions.assertTrue(scene.victim.getBuffManager().hasState(STATE), "\u300c\u83b7\u5f97\u3010\u6708\u8327\u3011\u72b6\u6001\u300d");
    }

    /** \u2b50\u2b50 \u300c\u884c\u52a8\u540e\u2026\u5426\u5219\u5c06\u7acb\u5373\u9677\u5165\u65e0\u6cd5\u6218\u6597\u72b6\u6001\u300d: nothing saved it, so its own turn's END commits the death. */
    @Test
    public void itsOwnTurnEndsIt() {
        Scene scene = alone();
        strike(scene);
        Assertions.assertFalse(scene.victim.isDeath(), "precondition: the blow was held");
        takeItsTurn(scene);
        Assertions.assertTrue(scene.victim.isDeath(),
                "\u6ca1\u6709\u4eba\u6551\u5b83 \u21d2 \u5b83\u81ea\u5df1\u7684\u56de\u5408\u7ed3\u675f\u65f6\u5019\u5012\u4e0b\uff08\u4e14\u5b83\u786e\u5b9e\u884c\u52a8\u8fc7 \u2713\uff09");
    }

    /**
     * \u2b50\u2b50 \u300c\u82e5\u884c\u52a8\u540e\u2026\u5f53\u524d\u751f\u547d\u503c\u63d0\u9ad8\u2026\u5219\u89e3\u9664\u3010\u6708\u8327\u3011\u72b6\u6001\u300d: a real heal ends the trace, and then it does NOT fall.
     *
     * <p>\u26a0 The heal is 1211's own answer to the same lethal blow -- i.e. a second, shipped reader of `LETHAL_DAMAGE`
     * intervening -- which is what makes this a real end-to-end reading: one character's lethal-damage heal fires
     * `HEALED`, and 1407's trace is removed by it.
     */
    @Test
    public void aHealEndsTheTrace() {
        Scene scene = withHealer();
        strike(scene);
        Assertions.assertTrue(scene.victim.getCurrentHp() > 0, "precondition: the teammate's heal really restored HP");
        Assertions.assertFalse(scene.victim.getBuffManager().hasState(STATE),
                "\u751f\u547d\u503c\u63d0\u9ad8 \u21d2 \u3010\u6708\u8327\u3011\u89e3\u9664");
        takeItsTurn(scene);
        Assertions.assertFalse(scene.victim.isDeath(), "\u3010\u6708\u8327\u3011\u5df2\u89e3\u9664 \u21d2 \u5b83\u4e0d\u518d\u5012\u4e0b");
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

    /** \u2b50 The unpolluted scene: nobody in it can heal, so the deferral is observable on its own. */
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

    /** \u26a0 A WHOLE turn: the early tick on `beforeMove`, the late tick and TURN_END on `afterMove`. */
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
