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

    /**
     * 天赋: 「随后重置为 1 层」 — the layers are spent back down to one by the same turn start that deals the dot.
     *
     * <p>Two things had to be true at once for this to be writable: a {@code const:} scale (so {@code SET_STACKS} could say
     * "one"), and the reset sitting LAST on the event (so the dot and the two threshold branches all read the count before it
     * moves).
     */
    @Test
    public void theTalentResetsTheLayersToOne() {
        Character blackSwan = CharacterFactory.create(BLACK_SWAN, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(blackSwan), List.of(enemy), fixed());
        battle.startBattle();
        for (int i = 0; i < 12; i++) {
            enemy.getBuffManager().addBuff(new com.laosun.aluminium.buff.StackBuff("奥迹", 99, true, 50));
        }
        Assertions.assertEquals(12, enemy.getBuffManager().stacksOf("奥迹"), "precondition");

        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(
                TriggerEvent.TURN_START, enemy, enemy, 0, 0));
        battle.processRequests();

        Assertions.assertTrue(enemy.getCurrentHp() < enemy.getMaxHp() || enemy.isDeath(), "the dot still landed");
        Assertions.assertTrue(enemy.getBuffManager().stacksOf("奥迹") <= 1,
                "「随后重置为 1 层」 — twelve layers are spent down to one, got "
                        + enemy.getBuffManager().stacksOf("奥迹"));
    }

    /**
     * Diagnostic for the Eidolon 6 allowance: does the grant rule fire, and does it fire in a SECOND fixture too?
     *
     * <p>The earlier probe printed 1 then 0 for two identically built battles, which is what moved the suspicion away from
     * the comparison operator and onto the rule's reach.
     */
    @Test
    public void eidolonSixGrantFiresInEveryFixture() {
        Assertions.assertEquals(1, allowanceAfterUlt(6), "first fixture");
        Assertions.assertEquals(1, allowanceAfterUlt(6), "second fixture, built exactly the same way");
        Assertions.assertEquals(0, allowanceAfterUlt(5), "below E6 the rule is not in the table at all");
    }

    private static int allowanceAfterUlt(int eidolon) {
        Character blackSwan = CharacterFactory.create(BLACK_SWAN, LEVEL, false, null, null, eidolon);
        // Three enemies, so the 「≥3 层」 branch's adjacent stack lands on the neighbours instead of back on the target (with a
        // single enemy it resolves onto itself and adds a layer, which made the first version of this count read 13).
        Enemy left = BattleFixture.monster();
        Enemy enemy = BattleFixture.monster();
        Enemy right = BattleFixture.monster();
        Battle battle = new Battle(List.of(blackSwan), List.of(left, enemy, right), BattleFixture.alwaysRoll(0.99));
        battle.startBattle();
        battle.castImmediate(blackSwan.getSkills().get(com.laosun.aluminium.enums.SkillType.ULTRA),
                blackSwan, List.of(enemy));
        battle.processRequests();
        return blackSwan.getBuffManager().stacksOf("奥迹不重置额度");
    }

    /**
     * 星魂 6: 「【奥迹】每回合开始造成伤害后不会重置层数」，once per 揭露 window.
     */
    @Test
    public void eidolonSixKeepsTheLayersForOneTurnStart() {
        // > 1 rather than exactly 12: the 65% dot-tick rule can add a layer too, and what this test is about is that the RESET did not happen.
        Assertions.assertTrue(layersAfterTurnStarts(6, 1) > 1, "the window keeps the layers instead of resetting them");
        Assertions.assertTrue(layersAfterTurnStarts(6, 2) <= 1,
                "「最多触发 1 次」 — the next turn start resets it (and the dot may finish the target off entirely)");
        Assertions.assertTrue(layersAfterTurnStarts(5, 1) <= 1, "below E6 there is no skip at all");
    }

    /** Layers left after firing {@code turnStarts} turn starts at the given eidolon rank. */
    private static int layersAfterTurnStarts(int eidolon, int turnStarts) {
        Character blackSwan = CharacterFactory.create(BLACK_SWAN, LEVEL, false, null, null, eidolon);
        // Three enemies, so the 「≥3 层」 branch's adjacent stack lands on the neighbours instead of back on the target (with a
        // single enemy it resolves onto itself and adds a layer, which made the first version of this count read 13).
        Enemy left = BattleFixture.monster();
        Enemy enemy = BattleFixture.monster();
        Enemy right = BattleFixture.monster();
        Battle battle = new Battle(List.of(blackSwan), List.of(left, enemy, right), BattleFixture.alwaysRoll(0.99));
        battle.startBattle();
        battle.castImmediate(blackSwan.getSkills().get(com.laosun.aluminium.enums.SkillType.ULTRA),
                blackSwan, List.of(enemy));
        battle.processRequests();
        for (int i = 0; i < 12; i++) {
            enemy.getBuffManager().addBuff(new com.laosun.aluminium.buff.StackBuff("奥迹", 99, true, 50));
        }
        for (int i = 0; i < turnStarts; i++) {
            battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(
                    TriggerEvent.TURN_START, enemy, enemy, 0, 0));
            battle.processRequests();
        }
        return Math.max(0, enemy.getBuffManager().stacksOf("奥迹"));
    }

    /**
     * 星魂 1: 「处于风化、裂伤、灼烧、触电状态下的敌方目标对应的风、物理、火、雷属性抗性分别降低 25%」.
     *
     * <p>Measured per element: the SAME hit lands harder in its own element and unchanged in the others, which is the difference
     * between a per-element resistance reduction and an all-type one.
     */
    @Test
    public void eidolonOneLowersFourResistancesByElement() {
        Assertions.assertEquals(-0.25, resistanceReduction(1, "WIND"), 1e-9, "风 for 风化 (stored negative: the attribute is a REDUCTION)");
        Assertions.assertEquals(-0.25, resistanceReduction(1, "PHYSICAL"), 1e-9, "物理 for 裂伤");
        Assertions.assertEquals(-0.25, resistanceReduction(1, "FIRE"), 1e-9, "火 for 灼烧");
        Assertions.assertEquals(-0.25, resistanceReduction(1, "THUNDER"), 1e-9, "雷 for 触电");
        Assertions.assertEquals(0.0, resistanceReduction(1, "ICE"), 1e-9, "冰 is not in the sentence");
        Assertions.assertEquals(0.0, resistanceReduction(0, "WIND"), 1e-9, "and nothing at E0");
    }

    private static double resistanceReduction(int eidolon, String element) {
        Character blackSwan = CharacterFactory.create(BLACK_SWAN, LEVEL, false, null, null, eidolon);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(blackSwan), List.of(enemy), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        // The reductions are applied at the victim's turn start while it is in the matching dot state, so the fixture puts all
        // four states on it and then starts its turn.
        for (String state : new String[]{"风化", "裂伤", "灼烧", "触电"}) {
            enemy.getBuffManager().addBuff(new com.laosun.aluminium.buff.StateBuff(state, 2));
        }
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(
                TriggerEvent.TURN_START, enemy, enemy, 0, 0));
        battle.processRequests();
        return enemy.getAttribute(com.laosun.aluminium.enums.AttributeType.resistanceReductionFor(
                com.laosun.aluminium.enums.DamageElement.valueOf(element))).get();
    }

    /**
     * 终结技: the +25% now rides the VICTIM's own state — the modifier is dropped when the state is, instead of running on a
     * parallel two-turn clock that could outlive it or die before it.
     */
    @Test
    public void theEpiphanyBonusEndsWithTheState() {
        Character blackSwan = CharacterFactory.create(BLACK_SWAN, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(blackSwan), List.of(enemy), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        battle.castImmediate(blackSwan.getSkills().get(com.laosun.aluminium.enums.SkillType.ULTRA),
                blackSwan, List.of(enemy));
        battle.processRequests();
        Assertions.assertTrue(enemy.getBuffManager().hasState("揭露"), "precondition: the state is on the victim");
        Assertions.assertEquals(1, enemy.getBuffManager().allBuffsOf(com.laosun.aluminium.buff.VulnerabilityBuff.class).size(),
                "precondition: the +25% sits on the victim");

        enemy.getBuffManager().removeState("揭露");
        enemy.getBuffManager().beforeMove();

        Assertions.assertEquals(0, enemy.getBuffManager().allBuffsOf(com.laosun.aluminium.buff.VulnerabilityBuff.class).size(),
                "the +25% went with the state");
    }

    /**
     * 星魂 1 carries its REAL condition: the reduction is applied at the turn start of a victim that is in the matching dot state,
     * and the holder gate takes it back off when that state leaves.
     */
    @Test
    public void eidolonOneReductionsFollowTheVictimsState() {
        Character blackSwan = CharacterFactory.create(BLACK_SWAN, LEVEL, false, null, null, 1);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(blackSwan), List.of(enemy), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        var wind = com.laosun.aluminium.enums.AttributeType.RESISTANCE_REDUCTION_WIND;

        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(
                TriggerEvent.TURN_START, enemy, enemy, 0, 0));
        battle.processRequests();
        Assertions.assertEquals(0.0, enemy.getAttribute(wind).get(), 1e-9,
                "「处于风化…的敌方目标」 — no state, no reduction");

        enemy.getBuffManager().addBuff(new com.laosun.aluminium.buff.StateBuff("风化", 2));
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(
                TriggerEvent.TURN_START, enemy, enemy, 0, 0));
        battle.processRequests();
        Assertions.assertEquals(-0.25, enemy.getAttribute(wind).get(), 1e-9, "with the state it applies");

        enemy.getBuffManager().removeState("风化");
        enemy.getBuffManager().beforeMove();
        Assertions.assertEquals(0.0, enemy.getAttribute(wind).get(), 1e-9, "and the gate takes it off again");
    }
}
