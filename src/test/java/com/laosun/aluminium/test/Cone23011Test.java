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
 * <p>⭐ The event is new this round (`WAVE_START`, fired by {@code WaveManager.nextWave} right after the wave's monsters are
 * spawned, with neither actor nor subject like BATTLE_START); the heal itself reuses the `target_lost_hp` scale built in
 * round 5, which is exactly what 「各自已损失生命值」 needs.
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
        Assertions.assertEquals(0.0, wearerHealed, 1e-9, "and a full-HP ally heals nothing -- 「各自」");
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

    /**
     * ★ The WIRING test, closing the debt round 21 registered: this one enters a wave for real, through
     * {@code WaveManager.nextWave}, so the line that fires {@code WAVE_START} is covered -- every other case fires the event
     * by hand and therefore judges only the content.
     *
     * <p>⚠ Stage-dependent: {@code stage.json} is generator output, so the test skips when the table is empty, the same
     * convention {@code WaveManagerTest} uses.
     */
    @Test
    public void aRealWaveBoundaryFiresTheHeal() {
        org.junit.jupiter.api.Assumptions.assumeFalse(com.laosun.aluminium.Constant.stages().isEmpty(),
                "stage.json has not been generated");
        var stage = com.laosun.aluminium.Constant.stages().get(310030);
        Assertions.assertNotNull(stage, "310030 is a multi-wave stage");
        // ⚠ Its own party: the fixture fields belong to the other cases, and a unit must not be in two battles.
        Character waveWearer = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, 1));
        Character waveAlly = CharacterFactory.create(ALLY, LEVEL);
        Battle battle = new Battle(List.of(waveWearer, waveAlly), new java.util.ArrayList<>(), new Random(0));
        new com.laosun.aluminium.models.WaveManager(battle, stage);
        Enemy source = EnemyFactory.create(MONSTER, 90, 1);
        battle.applyDamage(waveAlly, new Damage(source, waveAlly, DamageElement.FIRE, DamageType.NORMAL, 400));
        double lost = waveAlly.getMaxHp() - waveAlly.getCurrentHp();
        double before = waveAlly.getCurrentHp();
        boolean entered = battle.getWaveManager().nextWave();
        double healed = waveAlly.getCurrentHp() - before;
        System.out.println("[23011] REAL wave entry=" + entered + " lost=" + lost + " healed=" + healed
                + " enemies=" + battle.enemies.size());
        Assertions.assertTrue(entered, "310030 has a wave to enter");
        Assertions.assertEquals(lost * SHARE, healed, 1e-6,
                "entering a wave for real must fire WAVE_START -- that is the wiring, not the content");
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
