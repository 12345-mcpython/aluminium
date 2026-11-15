package com.laosun.aluminium.test.content.characters.qingque;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.SkillCategory;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.trigger.TriggerPayload;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 1201 青雀's Eidolon 1 (Ultimate damage +10%) and Eidolon 2 (a point of Energy per draw).
 */
public class QingqueEidolonTest {

    private static final int QINGQUE = 1201;
    private static final int LEVEL = 80;

    /**
     * Both Eidolons run on the same fixture — 青雀 at an Eidolon level beside ally 1001 against one monster with a fixed
     * roll — so one combat casts her Ultimate to read the boost ZONE and another lets her own draw pay the Energy.
     */
    @Test
    public void eidolonsLiftTheUltimateZoneAndPayEnergyOnHerDraw() {
        double without = ultBoostZone(0);
        double with = ultBoostZone(1);
        System.out.println("[1201] boost zone " + without + " -> " + with);
        double energy = energyAfterHerOwnDraw();
        System.out.println("[1201] energy after her draw = " + energy);

        // The clause is a share of the boost ZONE, so the ratio of settled damage is smaller whenever another boost is
        // already there; the zone itself moves by exactly 0.1.
        Assertions.assertAll("星魂 1 / 星魂 2: the Ultimate's boost ZONE and the Energy her draw pays",
                () -> Assertions.assertEquals(0.1, with - without, 1e-6,
                        "Eidolon 1: 「施放终结技造成的伤害提高 10%」"),
                () -> Assertions.assertEquals(1, energy, 1e-9,
                        "Eidolon 2: 「每次触发抽牌时，立即恢复 1 点能量」"));
    }

    /**
     * Eidolon 2's fixture: her own turn start is the draw that has to pay one point of Energy.
     */
    private static double energyAfterHerOwnDraw() {
        Character qingque = CharacterFactory.create(QINGQUE, LEVEL, true, null, null, 2);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(new ArrayList<>(List.of(qingque, CharacterFactory.create(1001, LEVEL))),
                new ArrayList<>(List.of(enemy)), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        qingque.setCurrentEnergy(0);
        battle.fire(TriggerPayload.Simple.of(TriggerEvent.TURN_START, qingque, qingque, 0, 0));
        return qingque.getCurrentEnergy();
    }

    /**
     * Eidolon 1's fixture: the same combat, read at Eidolon 0 and Eidolon 1 to isolate the Ultimate's boost ZONE.
     */
    private static double ultBoostZone(int eidolon) {
        Character qingque = CharacterFactory.create(QINGQUE, LEVEL, true, null, null, eidolon);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(new ArrayList<>(List.of(qingque, CharacterFactory.create(1001, LEVEL))),
                new ArrayList<>(List.of(enemy)), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        double[] zone = new double[]{-1};
        battle.addDamageObserver((damage, applied, hp, shield, died) ->
                zone[0] = damage.breakdown().getOrDefault("BoostArea", 0.0));
        battle.castImmediate(qingque.getSkills().get(com.laosun.aluminium.enums.SkillType.ULTRA), qingque, List.of(enemy));
        return zone[0];
    }
}
