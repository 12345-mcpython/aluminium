package com.laosun.aluminium.test.content.characters.blackswan;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 130Black Swan, from her own file: the Arcana stack with its 50 cap, and the Skill's defence shred.
 */
public class BlackSwanTest {
    private static final int BLACK_SWAN = 1307;
    private static final int LEVEL = 80;

    /**
     * Note: One Arcana per Skill, stopped at the document's fifty, and exactly 20.80% of the enemy's own defence.
     */
    @Test
    public void theSkillStacksArcanaAndShredsDefence() {
        Character blackSwan = CharacterFactory.create(BLACK_SWAN, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(blackSwan), List.of(enemy), fixed());
        battle.startBattle();
        double defenceBefore = enemy.getAttribute(AttributeType.DEFENCE).get();
        Assertions.assertTrue(defenceBefore > 0, "the fixture must have defence to shred: " + defenceBefore);
        Assertions.assertEquals(0, enemy.getBuffManager().stacksOf("奥迹"), "nothing before the Skill");

        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.SKILL_CAST, blackSwan, enemy, 0, 0));
        Assertions.assertEquals(1, enemy.getBuffManager().stacksOf("奥迹"),
                "「使目标…陷入1层【奥迹】」 (puts the target ... into 1 stack of [奥迹])");
        Assertions.assertEquals(0.208, (defenceBefore - enemy.getAttribute(AttributeType.DEFENCE).get()) / defenceBefore, 1e-6,
                "「防御力降低20.80%」 (lowers DEF by 20.80%) of its own defence");

        for (int i = 0; i < 60; i++) {
            battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.SKILL_CAST, blackSwan, enemy, 0, 0));
        }
        Assertions.assertEquals(50, enemy.getBuffManager().stacksOf("奥迹"),
                "「【奥迹】最多叠加50层」 ([奥迹] stacks at most 50 times) -- sixty-one casts must still read fifty");
    }

    /**
     * 「使目标**与相邻目标**陷入 1 层【奥迹】…防御力降低 20.80%」 — the adjacent halves, which the file used to register as
     * unwritable because it thought adjacency was only a damage shape. It is a SELECTOR ({@code adjacent_enemies}), so the 1 layer
     * and the shred reach the neighbours of the aimed enemy, and nobody else.
     */
    @Test
    public void theSkillReachesTheNeighboursOfTheAimedEnemy() {
        Character blackSwan = CharacterFactory.create(BLACK_SWAN, LEVEL);
        Enemy left = BattleFixture.monster();
        Enemy aimed = BattleFixture.monster();
        Enemy right = BattleFixture.monster();
        Battle battle = new Battle(List.of(blackSwan), List.of(left, aimed, right), fixed());
        battle.startBattle();
        double aimedDefence = aimed.getAttribute(AttributeType.DEFENCE).get();
        double leftDefence = left.getAttribute(AttributeType.DEFENCE).get();

        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.SKILL_CAST, blackSwan, aimed, 0, 0));

        Assertions.assertEquals(1, aimed.getBuffManager().stacksOf("奥迹"), "the aimed enemy");
        Assertions.assertEquals(1, left.getBuffManager().stacksOf("奥迹"), "「与相邻目标」 — the neighbour too");
        Assertions.assertEquals(1, right.getBuffManager().stacksOf("奥迹"), "both neighbours");
        Assertions.assertEquals(0.208, (leftDefence - left.getAttribute(AttributeType.DEFENCE).get()) / leftDefence, 1e-6,
                "the neighbour's defence is lowered by the same 20.80%");
        Assertions.assertTrue(aimedDefence > 0, "the fixture must have defence to shred");
    }

    private static Random fixed() {
        return BattleFixture.alwaysRoll(0.0);
    }

    /**
     * 天赋: 「【奥迹】状态下，敌方目标每回合开始时受到等同于黑天鹅 240% 攻击力的风属性持续伤害，每层【奥迹】使此伤害倍率提高 12%」.
     *
     * <p>The per-layer part is the sentence's whole point, so the test compares the settled damage at two layer counts against the
     * SAME enemy: five layers must be 240% × (1 + 0.6), one layer 240% × (1 + 0.12).
     */
    @Test
    public void theTalentDotScalesWithTheArcanaLayers() {
        double one = dotDamageAt(1);
        double five = dotDamageAt(5);
        Assertions.assertTrue(one > 0, "the dot lands at all");
        Assertions.assertEquals((1 + 0.12 * 5) / (1 + 0.12), five / one, 1e-6,
                "each layer raises the ratio by 12% (" + one + " at 1 layer, " + five + " at 5)");
    }

    private static double dotDamageAt(int layers) {
        Character blackSwan = CharacterFactory.create(BLACK_SWAN, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(blackSwan), List.of(enemy), fixed());
        battle.startBattle();
        for (int i = 0; i < layers; i++) {
            enemy.getBuffManager().addBuff(new com.laosun.aluminium.buff.StackBuff("奥迹", 99, true, 50));
        }
        double[] settled = {0};
        battle.addDamageObserver((damage, applied, hpLoss, shieldAbsorbed, died) -> settled[0] += applied);
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(
                TriggerEvent.TURN_START, enemy, enemy, 0, 0));
        battle.processRequests();
        return settled[0];
    }

    /**
     * 天赋: 「大于等于 7 层：使本次造成的持续伤害无视该目标及其相邻目标 20% 的防御力」.
     *
     * <p>What is asserted is the threshold, not the scope: at six layers the attacker has no defence ignore, at seven it does. The
     * 「本次」 scope is an APPROXIMATION the rule records — the modifier lands on her rather than on that one instance.
     */
    @Test
    public void sevenLayersTurnOnTheDefenceIgnore() {
        // Five and eight, not six and seven: the turn start itself can add a layer before the threshold rule is asked, so the two samples are chosen to stay on their own side of seven.
        Assertions.assertEquals(0.0, defenceIgnoreAt(5), 1e-9, "five layers is below the threshold");
        Assertions.assertEquals(0.2, defenceIgnoreAt(8), 1e-9, "「大于等于 7 层…无视 20% 的防御力」");
    }

    private static double defenceIgnoreAt(int layers) {
        Character blackSwan = CharacterFactory.create(BLACK_SWAN, LEVEL);
        // Three enemies and the middle one carries the layers: with a single enemy the 「≥3 层」 branch's adjacent selector
        // resolves back onto it and adds a seventh layer, which quietly moved the threshold this test is about.
        Enemy left = BattleFixture.monster();
        Enemy enemy = BattleFixture.monster();
        Enemy right = BattleFixture.monster();
        // Rolls are made to FAIL the 65% layer chance, so nothing else moves the count either.
        Battle battle = new Battle(List.of(blackSwan), List.of(left, enemy, right), BattleFixture.alwaysRoll(0.99));
        battle.startBattle();
        for (int i = 0; i < layers; i++) {
            enemy.getBuffManager().addBuff(new com.laosun.aluminium.buff.StackBuff("奥迹", 99, true, 50));
        }
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(
                TriggerEvent.TURN_START, enemy, enemy, 0, 0));
        battle.processRequests();
        return blackSwan.getAttribute(AttributeType.DEFENCE_IGNORE).get();
    }

    /**
     * 终结技: 「使敌方全体陷入【揭露】，持续 2 回合」 and the +25% damage taken that rides it.
     *
     * <p>Both approximate the sentence in ways the rule records: the modifier's lifetime is a 2-turn clock beside the state rather
     * than a gate on the holder's state, and 「自身回合内」 is not distinguished from any other moment in the window.
     */
    @Test
    public void theUltimateMarksEveryEnemyAndTheyTakeMore() {
        Character blackSwan = CharacterFactory.create(BLACK_SWAN, LEVEL);
        Enemy left = BattleFixture.monster();
        Enemy right = BattleFixture.monster();
        Battle battle = new Battle(List.of(blackSwan), List.of(left, right), fixed());
        battle.startBattle();
        double before = hitFor(blackSwan, left);

        battle.castImmediate(blackSwan.getSkills().get(com.laosun.aluminium.enums.SkillType.ULTRA),
                blackSwan, List.of(left, right));
        battle.processRequests();

        Assertions.assertTrue(left.getBuffManager().hasState("揭露"), "「使敌方全体陷入【揭露】」");
        Assertions.assertTrue(right.getBuffManager().hasState("揭露"), "both of them");
        Assertions.assertTrue(hitFor(blackSwan, left) > before,
                "「受到的伤害提高 25%」 (" + before + " -> " + hitFor(blackSwan, left) + ")");
    }

    /** One fixed hit, measured as the HP it actually removes. */
    private static double hitFor(Character attacker, Enemy enemy) {
        double before = enemy.getCurrentHp();
        new Battle(List.of(attacker), List.of(enemy), fixed()).applyDamage(enemy,
                new com.laosun.aluminium.damage.Damage(attacker, enemy,
                        com.laosun.aluminium.enums.DamageElement.WIND,
                        com.laosun.aluminium.enums.DamageType.NORMAL, 1000));
        return before - enemy.getCurrentHp();
    }

    /**
     * 星魂 4: 「每回合开始时…使黑天鹅恢复 8 点能量」，with 「在【揭露】持续时间内最多触发 1 次」 as a counted allowance.
     */
    @Test
    public void eidolonFourPaysEightEnergyOncePerEpiphany() {
        Character blackSwan = CharacterFactory.create(BLACK_SWAN, LEVEL, false, null, null, 4);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(blackSwan), List.of(enemy), fixed());
        battle.startBattle();
        double start = blackSwan.getCurrentEnergy();
        battle.castImmediate(blackSwan.getSkills().get(com.laosun.aluminium.enums.SkillType.ULTRA),
                blackSwan, List.of(enemy));
        battle.processRequests();
        double afterUlt = blackSwan.getCurrentEnergy();

        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(
                TriggerEvent.TURN_START, enemy, enemy, 0, 0));
        battle.processRequests();
        double afterFirstTurn = blackSwan.getCurrentEnergy();
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(
                TriggerEvent.TURN_START, enemy, enemy, 0, 0));
        battle.processRequests();

        Assertions.assertTrue(afterFirstTurn > afterUlt, "the first turn start pays (from " + start + ")");
        Assertions.assertEquals(afterFirstTurn, blackSwan.getCurrentEnergy(), 1e-9,
                "「最多触发 1 次」 — the second turn start pays nothing until 揭露 is applied again");
    }

    /**
     * 星魂 4: the other half of the energy clause — 「或被消灭时」 — sharing the same allowance, so a window pays once whichever half
     * comes first.
     */
    @Test
    public void eidolonFourSharesOneAllowanceBetweenTheTwoTriggers() {
        Character blackSwan = CharacterFactory.create(BLACK_SWAN, LEVEL, false, null, null, 4);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(blackSwan), List.of(enemy), fixed());
        battle.startBattle();
        battle.castImmediate(blackSwan.getSkills().get(com.laosun.aluminium.enums.SkillType.ULTRA),
                blackSwan, List.of(enemy));
        battle.processRequests();

        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(
                TriggerEvent.TURN_START, enemy, enemy, 0, 0));
        battle.processRequests();
        double afterTurn = blackSwan.getCurrentEnergy();

        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(
                TriggerEvent.KILL, enemy, enemy, 0, 0));
        battle.processRequests();

        Assertions.assertEquals(afterTurn, blackSwan.getCurrentEnergy(), 1e-9,
                "「在【揭露】持续时间内最多触发 1 次」 — the turn start already spent the window's allowance");
    }
}
