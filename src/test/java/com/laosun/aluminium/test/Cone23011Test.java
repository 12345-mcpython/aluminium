package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * Light cone 23011: at the start of EVERY WAVE, each ally heals a share of its OWN lost HP.
 *
 * <p>\u2b50 The event is new this round (`WAVE_START`, fired by {@code WaveManager.nextWave} right after the wave's monsters are
 * spawned, with neither actor nor subject like BATTLE_START); the heal itself reuses the `target_lost_hp` scale built in
 * round 5, which is exactly what \u300c\u5404\u81ea\u5df2\u635f\u5931\u751f\u547d\u503c\u300d needs.
 */
public class Cone23011Test {
    private static final int CONE = 23011;
    private static final int WEARER = 1205;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final double SHARE = 0.8;

    private Character wearer;
    private Character ally;
    private Enemy enemy;

    private Battle battle(boolean withCone) {
        wearer = withCone
                ? CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, 1))
                : CharacterFactory.create(WEARER, LEVEL);
        ally = CharacterFactory.create(ALLY, LEVEL);
        enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(wearer, ally), List.of(enemy), new Random(0));
        battle.startBattle();
        return battle;
    }

    @Test
    public void aWaveStartHealsEachAllysOwnLostHp() {
        Battle battle = battle(true);
        battle.applyDamage(ally, new Damage(enemy, ally, DamageElement.FIRE, DamageType.NORMAL, 400));
        double lost = ally.getMaxHp() - ally.getCurrentHp();
        double before = ally.getCurrentHp();
        double wearerBefore = wearer.getCurrentHp();
        battle.fireTriggers(TriggerEvent.WAVE_START);
        double healed = ally.getCurrentHp() - before;
        double wearerHealed = wearer.getCurrentHp() - wearerBefore;
        System.out.println("[23011] lost=" + lost + " alliedHealed=" + healed + " expected=" + (lost * SHARE)
                + " wearerHealed=" + wearerHealed);
        Assertions.assertEquals(lost * SHARE, healed, 1e-6, "the share is of the ALLY's own gap (rank 1 param says 0.8)");
        Assertions.assertEquals(0.0, wearerHealed, 1e-9, "and a full-HP ally heals nothing -- \u300c\u5404\u81ea\u300d");
    }

    @Test
    public void theSpecPinsTheScaleTheShareAndTheTargets() {
        Battle battle = battle(true);
        int pinned = 0;
        for (var rule : wearer.getTriggerTable().matching(TriggerEvent.WAVE_START,
                new TriggerTable.TriggerContext(wearer, wearer, enemy, 0, 0, null, battle, null))) {
            for (var effect : rule.effects()) {
                if (!"HEAL".equals(effect.getOp())) {
                    continue;
                }
                pinned++;
                System.out.println("[23011] spec scale=" + effect.getScale() + " percent=" + effect.getPercent()
                        + " target=" + effect.getTarget());
                Assertions.assertEquals("target_lost_hp", effect.getScale(), "each recipient's own gap");
                Assertions.assertEquals(SHARE, effect.getPercent(), 1e-9, "rank 1 states 0.8");
                Assertions.assertEquals("all_allies", effect.getTarget(), "the whole party");
            }
        }
        Assertions.assertEquals(1, pinned, "exactly one wave-start heal from this cone");
    }

    @Test
    public void withoutTheConeNothingIsHealed() {
        Battle battle = battle(false);
        battle.applyDamage(ally, new Damage(enemy, ally, DamageElement.FIRE, DamageType.NORMAL, 400));
        double before = ally.getCurrentHp();
        battle.fireTriggers(TriggerEvent.WAVE_START);
        System.out.println("[23011] without the cone: " + before + " -> " + ally.getCurrentHp());
        Assertions.assertEquals(before, ally.getCurrentHp(), 1e-9, "no cone, no heal (false case)");
    }
}
