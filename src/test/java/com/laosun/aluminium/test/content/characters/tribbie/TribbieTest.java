package com.laosun.aluminium.test.content.characters.tribbie;


import com.laosun.aluminium.Battle;
import com.laosun.aluminium.data.TriggerTables;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.SkillCategory;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.trigger.TriggerPayload;
import com.laosun.aluminium.trigger.TriggerTable;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 1403 Tribbie, from her own file: an 18%-Max-HP follow-up on a TEAMMATE's ultimate, and the Numinosity state.
 *
 * <p>The follow-up needs two things that did not exist together before: `actor is_other_ally` (round 14) so her own ultimate does not trigger it, and the
 * Max HP scale (round 189) so 18% of HER Max HP can be stated at all.
 */
public class TribbieTest {
    private static final int TRIBBIE = 1403;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final String ZONE = "char:1403/barrier";

    /**
     * Note: A teammate's ultimate triggers it; her own does not.
     */
    @Test
    public void onlyATeammatesUltimateTriggersTheFollowUp() {
        double fromAlly = lossWhenTheEventComesFrom(true);
        double fromSelf = lossWhenTheEventComesFrom(false);

        Assertions.assertTrue(fromAlly > 0, "「我方其他角色施放终结技后」 (after another one of our characters casts an Ultimate) -- a teammate's ultimate must trigger it: " + fromAlly);
        Assertions.assertEquals(0.0, fromSelf, 1e-9,
                "「我方**其他**角色」 (**another** one of our characters) -- her own ultimate is excluded, and firing the event by hand means no ultimate damage is mixed in: " + fromSelf);
    }

    /**
     * Note: "进入战斗时获得[神启]，持续3回合", and the trace's own battle-start energy.
     */
    @Test
    public void theTechniqueGrantsNuminosity() {
        Character tribbie = CharacterFactory.create(TRIBBIE, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(tribbie), List.of(enemy), fixed());
        battle.markTechniqueUsed(tribbie);

        battle.startBattle();

        Assertions.assertTrue(tribbie.getBuffManager().hasState("char:1403/revelation"),
                "「使用秘技后，进入战斗时获得【char:1403/revelation】」 (after using the Technique, gains Numinosity (【char:1403/revelation】) on entering battle)");
    }


    /**
     * Fires ULT_CAST with either the teammate or Tribbie as the actor, and returns what the enemy lost.
     *
     * <p>Hand-firing the event is the point: a real ultimate would deal its own Max-HP damage (measured 131.for 30%) and the reading would be that number
     * instead of the follow-up's. With the event alone, whatever the enemy loses IS the rule's reaction.
     */
    private static double lossWhenTheEventComesFrom(boolean teammate) {
        Character tribbie = CharacterFactory.create(TRIBBIE, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(tribbie, ally), List.of(enemy), fixed());
        battle.startBattle();
        double before = enemy.getCurrentHp();
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ULT_CAST, teammate ? ally : tribbie, tribbie, 0, 0));
        return before - enemy.getCurrentHp();
    }


    /**
     * Note: The number itself: 18% of her Max HP must be 0.36 of a hand-built 50% in the same pipeline.
     */
    @Test
    public void theFollowUpDealsEighteenPercentOfHerMaxHp() {
        double content = followUpLoss(false);
        double reference = followUpLoss(true);

        Assertions.assertTrue(reference > 0, "the reference must deal damage");
        Assertions.assertEquals(0.18 / 0.50, content / reference, 0.05,
                "content " + content + " vs reference " + reference + " (expected " + (0.18 / 0.50) + ")");
    }

    /**
     * 1403 缇宝's talent, last sentence: 「若追加攻击施放前目标被消灭则对<b>新入场</b>的敌方目标发动追加攻击」.
     *
     * <p>The shape is {@code defer_until_wave}: an other-ally Ultimate that finds no living enemy is HELD, and the next wave's
     * {@code WAVE_START} releases it against the enemy that just entered. The yardstick is the SAME follow-up fired directly in
     * a fresh battle - NOT a plain hit measured later in the same battle, because her own follow-up grants a damage boost that
     * makes a later hit look stronger (measured 1.72x, which is what made an earlier reading look like a defect).
     */
    @Test
    public void aFollowUpWithNoTargetWaitsForTheNextWave() {
        Character tribbie = CharacterFactory.create(TRIBBIE, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy first = BattleFixture.monster();
        Battle battle = new Battle(new ArrayList<>(List.of(tribbie, ally)), new ArrayList<>(List.of(first)),
                BattleFixture.alwaysRoll(0.99));
        battle.startBattle();

        // The only enemy dies; now the other ally casts their Ultimate - there is nobody to hit.
        battle.applyTrueDamage(ally, first, DamageElement.PHYSICAL, first.getCurrentHp() * 10);
        battle.processRequests();
        Assertions.assertTrue(first.isDeath());
        battle.fire(TriggerPayload.Cast.of(TriggerEvent.ULT_CAST, ally, first, 0, 0,
                SkillCategory.ULTRA, 3, 0, List.of()));

        Enemy fresh = BattleFixture.monster();
        battle.addEnemy(fresh);
        double beforeWave = fresh.getCurrentHp();
        battle.fire(TriggerPayload.Simple.of(TriggerEvent.WAVE_START, tribbie, tribbie, 0, 0));
        double deferred = beforeWave - fresh.getCurrentHp();

        double direct = directFollowUp();
        System.out.println("[1403] deferred=" + deferred + " direct=" + direct);
        Assertions.assertEquals(direct, deferred, 1e-6,
                "「对新入场的敌方目标发动追加攻击」 - the same follow-up, only later");
    }

    /**
     * Tribbie's zone, as a state on him.
     *
     * <p>His ultimate (140303): "开启<b>结界</b>… <b>结界持续期间</b>，敌方目标受到的伤害提高 #2%… 结界持续 #4 回合，自身每回合开始时结界持续回合数减1。"
     * Two readings: the state is on him right after his ultimate, and it runs out on its own -- a state that never ends would
     * satisfy the first half while being wrong about the zone, which lasts #4 = 2 turns at every level of 140303.
     */
    @Test
    public void theZoneIsAStateThatEndsOnItsOwn() {
        Character tribbie = CharacterFactory.create(TRIBBIE, LEVEL);
        Battle battle = new Battle(List.of(tribbie, CharacterFactory.create(ALLY, LEVEL)),
                List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        Assertions.assertFalse(tribbie.getBuffManager().hasState(ZONE),
                "precondition: no zone before the ultimate");

        tribbie.setCurrentEnergy(tribbie.getMaxEnergy());
        Assertions.assertTrue(battle.castUltra(tribbie, List.of(battle.enemies().getFirst())),
                "precondition: the ultimate can be cast");
        battle.processRequests();

        boolean afterUlt = tribbie.getBuffManager().hasState(ZONE);
        tribbie.afterMove(battle);
        tribbie.getBuffManager().afterMove();
        tribbie.afterMove(battle);
        tribbie.getBuffManager().afterMove();
        boolean afterTwoTurns = tribbie.getBuffManager().hasState(ZONE);
        System.out.println("[zone_state] right after the ultimate = " + afterUlt
                + " ; after two of his turns = " + afterTwoTurns);

        Assertions.assertTrue(afterUlt, "「开启【" + ZONE + "】」 (opens the zone) -- the state says the zone is open");
        Assertions.assertFalse(afterTwoTurns, "「【" + ZONE + "】持续 #4 回合」 (lasts #4 turns) -- #4 is 2 at every level, so it must end");
    }

    /**
     * The same follow-up with a living enemy, in its own battle: same 18% of Max HP, same zones, and no boost from the
     * follow-up itself (it has not landed yet when it is measured).
     */
    private static double directFollowUp() {
        Character tribbie = CharacterFactory.create(TRIBBIE, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(new ArrayList<>(List.of(tribbie, ally)), new ArrayList<>(List.of(enemy)),
                BattleFixture.alwaysRoll(0.99));
        battle.startBattle();
        double before = enemy.getCurrentHp();
        battle.fire(TriggerPayload.Cast.of(TriggerEvent.ULT_CAST, ally, enemy, 0, 0,
                SkillCategory.ULTRA, 3, 0, List.of()));
        return before - enemy.getCurrentHp();
    }

    /**
     * Fires one teammate ULT_CAST, optionally replacing her table with a single 50%-Max-HP reference rule.
     */
    private static double followUpLoss(boolean useReference) {
        Character tribbie = CharacterFactory.create(TRIBBIE, LEVEL);
        if (useReference) {
            com.laosun.aluminium.beans.EffectSpec effect = new com.laosun.aluminium.beans.EffectSpec();
            TriggerSpecs.set(effect, "op", "DAMAGE");
            TriggerSpecs.set(effect, "scale", "owner_max_hp");
            TriggerSpecs.set(effect, "percent", 0.50);
            TriggerSpecs.set(effect, "element", "Quantum");
            TriggerSpecs.set(effect, "target", "all_enemies");
            tribbie.setTriggerTable(new TriggerTable(TRIBBIE,
                    List.of(TriggerSpecs.rule(TriggerEvent.ULT_CAST.name(), List.of("actor is_other_ally"), effect))));
        }
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(tribbie, ally), List.of(enemy), fixed());
        battle.startBattle();
        double before = enemy.getCurrentHp();
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ULT_CAST, ally, tribbie, 0, 0));
        return before - enemy.getCurrentHp();
    }

    private static Random fixed() {
        return BattleFixture.alwaysRoll(1.0);
    }
}

