package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.Constant;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.WaveManager;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * `wave_monster`: the SINGLE aim a commanded cast can name (2026-10-02).
 *
 * <p>Why a single-target sibling of `wave_monsters` is needed at all, measured: `CAST_SKILL`'s `target` is the CASTER, and `cast_target` is
 * the field for "who a commanded cast is aimed at" -- it reads through `resolveSelector`, the single-target switch, and reorders the victims
 * so the aim goes first. A set cannot go there.
 *
 * <p>Reader: 1415's memosprite skill 8, 「若施放前目标被消灭则对**新入场**的敌方目标施放」 -- the half of it that says WHERE the commanded
 * strike should go.
 */
public class WaveMonsterAimTest {
    private static final int LEVEL = 80;
    private static final int OWNER = 1217;
    private static final int ALLY = 1002;
    private static final int MONSTER = 1002011;
    private static final int MULTI_WAVE_STAGE = 310030;

    /** Exactly ONE of the wave's enemies is the aim -- the set's first, not the whole set. */
    @Test
    public void exactlyOneEnemyIsTheAim() {
        Assumptions.assumeFalse(Constant.stages().isEmpty(), "stage.json has not been generated");
        var stage = Constant.stages().get(MULTI_WAVE_STAGE);
        Assertions.assertNotNull(stage, "310030 is a multi-wave stage");

        Character owner = CharacterFactory.create(OWNER, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Battle battle = new Battle(List.of(owner, ally), new ArrayList<>(), new Random(0));

        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "MODIFY_ATTR");
        TriggerSpecs.set(effect, "attribute", "SPEED");
        TriggerSpecs.set(effect, "amount", 100.0);
        TriggerSpecs.set(effect, "turns", 1);
        TriggerSpecs.set(effect, "target", "wave_monster");
        owner.setTriggerTable(new TriggerTable(OWNER, List.of(TriggerSpecs.rule("WAVE_START", List.of(), effect))));

        new WaveManager(battle, stage);
        Assertions.assertTrue(battle.getWaveManager().nextWave(), "310030 has a wave to enter");

        // ⚠ The reading is the SPEED DELTA, not "was it touched at all": every enemy has a positive base speed, so the first version of
        // this judge counted all four. Exactly one enemy carries +100, and the other three are equal to each other.
        List<CanHit> boosted = new ArrayList<>();
        List<Double> speeds = new ArrayList<>();
        for (CanHit enemy : battle.enemies) {
            double speed = enemy.getAttribute(AttributeType.SPEED).get();
            speeds.add(speed);
            if (speed > 100.0) {
                boosted.add(enemy);
            }
        }
        System.out.println("[aim] the wave brought " + battle.enemies.size() + " enemies ; the aim boosted " + boosted.size()
                + " of them (speeds " + speeds + ")");

        Assertions.assertEquals(1, boosted.size(),
                "「对新入场的敌方目标施放」-- a commanded cast names ONE aim, so exactly one enemy carries the boost");
        Assertions.assertSame(battle.waveMonsters().getFirst(), boosted.getFirst(),
                "and it is the wave's own first arrival, not some other enemy");
    }

    /** With no wave on the field the aim resolves to nobody, and that must fail loudly rather than silently. */
    @Test
    public void anEmptyWaveFailsLoudly() {
        Character owner = CharacterFactory.create(OWNER, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Battle battle = new Battle(List.of(owner, ally),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();

        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "MODIFY_ATTR");
        TriggerSpecs.set(effect, "attribute", "SPEED");
        TriggerSpecs.set(effect, "amount", 100.0);
        TriggerSpecs.set(effect, "turns", 1);
        TriggerSpecs.set(effect, "target", "wave_monster");
        owner.setTriggerTable(new TriggerTable(OWNER, List.of(TriggerSpecs.rule("WAVE_START", List.of(), effect))));

        boolean threw = false;
        try {
            battle.fireTriggers(TriggerEvent.WAVE_START);
        } catch (IllegalArgumentException | IllegalStateException expected) {
            threw = true;
            System.out.println("[aim] empty wave -> " + expected.getClass().getSimpleName() + ": "
                    + String.valueOf(expected.getMessage()).substring(0, Math.min(90, String.valueOf(expected.getMessage()).length())));
        }
        Assertions.assertTrue(threw, "an aim that resolves to nobody is the silence this engine refuses");
    }
}
