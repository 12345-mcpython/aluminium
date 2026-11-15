package com.laosun.aluminium.test.content.lightcones;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.equipment.Weapon;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Light cone 23041, read off the shipped file (discipline 232).
 *
 * <p>只看今朝 (23041): 「若敌方目标拥有<b>装备者添加的</b>弱点，装备者对其造成的伤害提高 <b>60%</b>」.
 *
 * <p><b>Why this was listed as impossible.</b> The old note said the engine can ask 「目标有几个弱点」 but not 「这个弱点是不是她加的」.
 * It never needed the applier: the engine already keeps TWO tables - {@code stanceWeak} is DATA from the monster table, and
 * {@code timedWeak} holds exactly the weaknesses an EFFECT inserted. So 「添加的」 is the second table, and the new condition
 * {@code target has_added_weakness} asks precisely that.
 */
public class LifeShouldBeCastToFlamesTest {
    private static final int WEAPON_ID = 23041;
    private static final int WEARER = 1210;
    private static final int LEVEL = 80;
    // From the merged LifeShouldBeCastToFlamesConeTest: its wearer is a DIFFERENT character id.
    private static final int CONE_WEARER = 1204;

    @Test
    public void theShippedRulesCarryTheNumbers() {
        Character c = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(WEAPON_ID, LEVEL));
        var turns = c.getTriggerTable().rulesFor(TriggerEvent.TURN_START).stream()
                .filter(r -> r.id().startsWith("cone23041_")).toList();
        var hits = c.getTriggerTable().rulesFor(TriggerEvent.DEALING_DAMAGE).stream()
                .filter(r -> r.id().startsWith("cone23041_")).toList();
        System.out.println("[23041] turn rules=" + turns.size() + " hit rules=" + hits.size());
        Assertions.assertEquals(1, turns.size(), "the turn-start energy");
        Assertions.assertEquals(2, hits.size(), "the defence-down clause and the added-weakness boost");
        var energy = turns.getFirst().effects().getFirst();
        var down = hits.stream().filter(r -> r.id().equals("cone23041_defence_down")).findFirst().orElseThrow()
                .effects().getFirst();
        System.out.println("[23041] spec energy op=" + energy.getOp() + " amount=" + energy.getAmount()
                + " target=" + energy.getTarget()
                + " ; def attr=" + down.getAttribute() + " percent=" + down.getPercent()
                + " turns=" + down.getTurns() + " target=" + down.getTarget());
        Assertions.assertEquals(10.0, energy.getAmount(), 1e-9, "ten energy");
        Assertions.assertEquals("self", energy.getTarget(), "for the wearer");
        Assertions.assertEquals("GAIN_ENERGY", energy.getOp(), "energy");
        // 2026-10-31: the data row for rank 1 is 0.12 (12/15/18/21/24%), and the file used to carry the RANK-5 number.
        Assertions.assertEquals(-0.12, down.getPercent(), 1e-9, "12% less defence at rank 1");
        Assertions.assertEquals(2.0, down.getTurns(), 1e-9, "for two turns");
        Assertions.assertEquals("target", down.getTarget(), "on the enemy it hit");
    }

    // ==================================================================
    // Merged from LifeShouldBeCastToFlamesConeTest.

    private static final class Run {
        private final Character wearer;
        private final Enemy enemy;
        private final Battle battle;

        Run() {
            wearer = CharacterFactory.create(CONE_WEARER, LEVEL, true, Weapon.build(23041, LEVEL, false, 1));
            enemy = BattleFixture.monster();
            battle = new Battle(new ArrayList<>(List.of(wearer)), new ArrayList<>(List.of(enemy)),
                    BattleFixture.seeded());
            battle.startBattle();
        }

        /**
         * The boost one settled instance carried.
         */
        double boostOfOneHit() {
            Damage hit = new Damage(wearer, enemy, DamageElement.PHYSICAL, DamageType.NORMAL, 1000);
            battle.applyDamage(enemy, hit);
            battle.processRequests();
            return hit.boostArea().getRate() - 1.0;
        }
    }

    @Test
    public void theBoostOnlyAppliesToAnAddedWeakness() {
        Run plain = new Run();
        Assertions.assertEquals(0.0, plain.boostOfOneHit(), 1e-9,
                "the enemy's OWN weaknesses are not 「装备者添加的」");

        Run added = new Run();
        added.enemy.addWeakness(DamageElement.QUANTUM, 2);
        Assertions.assertEquals(0.6, added.boostOfOneHit(), 1e-6,
                "「拥有装备者添加的弱点 ⇒ 对其造成的伤害提高 60%」");
    }
}
