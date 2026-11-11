package com.laosun.aluminium.test.content.characters.danheng;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * The Remembrance Trailblazer pair: the first summon shipped since 1402/1413, because the document states its SPEED.
 *
 * <p>Round 163 measured that a memosprite file MUST state SPEED ("the action bar cannot schedule a unit with 0 speed"), which is why 1409's memosprite is
 * still unwritten. Here both numbers are in the text - 130 SPD and 80% of the Trailblazer's Max HP plus 640 - so the panel can be stated and asserted.
 */
public class RemembranceTrailblazerTest {
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;

    /**
     * Note: The memorisprite arrives with the document's own panel, for both ids.
     */
    @Test
    public void theSummonArrivesWithTheDocumentedPanel() {
        for (int cid : new int[]{8007, 8008}) {
            Character tb = CharacterFactory.create(cid, LEVEL);
            Enemy enemy = BattleFixture.monster();
            Battle battle = new Battle(List.of(tb), List.of(enemy), fixed());
            battle.startBattle();

            battle.castImmediate(tb.getSkills().get(SkillType.SKILL), tb, List.of(enemy));

            var mem = battle.memospriteOf(tb);
            Assertions.assertNotNull(mem, "cid " + cid + ": \"summons the memosprite Mem\" (「召唤忆灵迷迷」)");
            Assertions.assertEquals(130, mem.getAttribute(AttributeType.SPEED).get(), 1e-9,
                    "cid " + cid + ": \"the memosprite Mem starts with 130 SPD\" (「忆灵迷迷初始拥有130点速度」)");
            double expected = tb.getMaxHp() * 0.8 + 640;
            Assertions.assertEquals(expected, mem.getMaxHp(), expected * 0.02,
                    "cid " + cid + ": 「等同于开拓者80%生命上限+640的生命上限」 (Max HP equal to 80% of the Trailblazer's Max HP + 640): expected " + expected + ", got " + mem.getMaxHp());
        }
    }

    /**
     * Note: Casting again does not create a second memosprite - the first is still the one on the field.
     */
    @Test
    public void castingAgainKeepsTheSameMemosprite() {
        Character tb = CharacterFactory.create(8007, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(tb), List.of(enemy), fixed());
        battle.startBattle();

        battle.castImmediate(tb.getSkills().get(SkillType.SKILL), tb, List.of(enemy));
        var first = battle.memospriteOf(tb);
        battle.castImmediate(tb.getSkills().get(SkillType.SKILL), tb, List.of(enemy));
        var second = battle.memospriteOf(tb);

        Assertions.assertSame(first, second, "「若迷迷已在场」 (if Mem is already on the field) -- the same memosprite, not a second one");
    }


    /**
     * 终结技:「召唤忆灵迷迷…随后**使迷迷**对敌方全体造成等同于迷迷 240% 攻击力的冰属性伤害」.
     *
     * <p><b>Two defects lived here until 2026-11-09.</b> The Ultimate's row is {@code Ultra/AoEAttack}, so without a hand-over it
     * swung a second instance on top of the rule's - and scaled off the TRAILBLAZER's attack instead of Mem's (measured:
     * full = 1464.2 against the rule's 509.7). Worse, the rule's {@code scale: "summon_attr:ATTACK"} was <b>refused by DAMAGE</b>,
     * so the Ultimate <b>threw on every cast</b>. Both are fixed together; this criterion pins that the row no longer swings by
     * comparing a real cast with the same event fired by hand (the rule alone) - equality is the whole assertion.
     */
    @Test
    public void theUltimateSettlesOneInstanceOfMemsOwnDamage() {
        for (int cid : new int[]{8007, 8008}) {
            double cast = ultimateDamage(cid, true);
            double ruleOnly = ultimateDamage(cid, false);
            Assertions.assertTrue(ruleOnly > 0, "cid " + cid + ": the rule's instance settled (" + ruleOnly + ")");
            Assertions.assertEquals(ruleOnly, cast, 1e-6,
                    "cid " + cid + ": 「使迷迷对敌方全体造成…」 settles ONE instance - the row's own swing is handed away ("
                            + cast + " vs " + ruleOnly + ")");
        }
    }

    /** 终结技的韧性 60（数据 `stance_list.all`）：被交接的施放不削韧性，所以它必须由规则那一发带着。 */
    @Test
    public void theUltimateStillTakesSixtyToughness() {
        for (int cid : new int[]{8007, 8008}) {
            Scene scene = new Scene(cid);
            // Toughness only comes off an enemy that is WEAK to the element (the gate `reduceToughness` itself keeps) - Mem's is Ice.
            scene.enemy.addWeakness(com.laosun.aluminium.enums.DamageElement.ICE);
            double before = scene.enemy.getStance();
            scene.battle.castImmediate(scene.unit.getSkills().get(SkillType.ULTRA), scene.unit, List.of(scene.enemy));
            scene.battle.processRequests();
            Assertions.assertEquals(before - 60, scene.enemy.getStance(), 1e-6,
                    "cid " + cid + ": 「破韧值 全体 60」 rides on the rule's instance");
        }
    }

    /** Enemy HP lost to one Ultimate, either through a real cast or through the rule's own event. */
    private static double ultimateDamage(int cid, boolean throughTheCast) {
        Scene scene = new Scene(cid);
        double before = scene.enemy.getCurrentHp();
        if (throughTheCast) {
            scene.battle.castImmediate(scene.unit.getSkills().get(SkillType.ULTRA), scene.unit, List.of(scene.enemy));
        } else {
            // The rule's own event: no cast token, so no row can swing - this IS the rule's single instance.
            scene.battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(
                    com.laosun.aluminium.enums.TriggerEvent.ULT_CAST, scene.unit, scene.enemy, 1, 0));
        }
        scene.battle.processRequests();
        return before - scene.enemy.getCurrentHp();
    }

    /** A fight with a toughness bar big enough that a 60-point hit cannot break it (breaking adds a BREAK instance). */
    private static final class Scene {
        private final Character unit;
        private final Enemy enemy;
        private final Battle battle;

        private Scene(int cid) {
            unit = CharacterFactory.create(cid, LEVEL);
            enemy = BattleFixture.monster();
            enemy.setMaxStance(100_000);
            enemy.setStance(100_000);
            battle = new Battle(List.of(unit), List.of(enemy), fixed());
            battle.startBattle();
        }
    }

    private static Random fixed() {
        return BattleFixture.alwaysRoll(1.0);
    }
}
