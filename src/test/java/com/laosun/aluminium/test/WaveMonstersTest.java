package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.Constant;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.WaveManager;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 「若施放前目标被消灭则对<b>新入场</b>的敌方目标施放」 -- the selector `wave_monsters` (2026-10-02).
 *
 * <p>⭐ The word came out of the game's own data, not out of my head: `MServant_CyreneServant_00_AmazingBuff_Mydeimos_OnWaveMonster` listens
 * for `"Event": "OnWaveMonster"` and answers with a `TurnInsertAction` -- an enemy that entered WITH A WAVE. The engine already had waves
 * (`WaveManager` -> `Battle.beginWave`, the counter per-wave limits compare against), so the record is hung on that boundary.
 *
 * <p>⚠ The clearing point is load-bearing and was measured: `nextWave()` runs `waveIndex++` -> `spawnWave` -> `beginWave` -> `WAVE_START`,
 * so forgetting the previous wave happens BEFORE the spawn; on `beginWave()` it would erase the wave that just arrived.
 *
 * <p>⭐ The scene is the real one `Cone23011Test` uses -- the generated multi-wave stage 310030 through `WaveManager.nextWave()` -- so the
 * wiring is judged, not a hand-fired event.
 */
public class WaveMonstersTest {
    private static final int LEVEL = 80;
    private static final int OWNER = 1217;
    private static final int ALLY = 1002;
    private static final int MULTI_WAVE_STAGE = 310030;

    /** Entering a wave puts THAT wave's enemies in the set, and a later wave starts the set over. */
    @Test
    public void enteringAWaveIsWhatFillsTheSet() {
        Assumptions.assumeFalse(Constant.stages().isEmpty(), "stage.json has not been generated");
        var stage = Constant.stages().get(MULTI_WAVE_STAGE);
        Assertions.assertNotNull(stage, "310030 is a multi-wave stage");

        Character owner = CharacterFactory.create(OWNER, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Battle battle = new Battle(List.of(owner, ally), new ArrayList<>(), new Random(0));

        // the rule the selector is read through: every enemy that entered with this wave gains 100 speed
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "MODIFY_ATTR");
        TriggerSpecs.set(effect, "attribute", "SPEED");
        TriggerSpecs.set(effect, "amount", 100.0);
        TriggerSpecs.set(effect, "turns", 1);
        TriggerSpecs.set(effect, "target", "wave_monsters");
        TriggerSpec rule = TriggerSpecs.rule("WAVE_START", List.of(), effect);
        owner.setTriggerTable(new TriggerTable(OWNER, List.of(rule)));

        new WaveManager(battle, stage);
        Assertions.assertTrue(battle.waveMonsters().isEmpty(), "precondition: no wave has entered yet");

        boolean entered = battle.getWaveManager().nextWave();
        Assertions.assertTrue(entered, "310030 has a wave to enter");
        List<CanHit> first = battle.waveMonsters();
        System.out.println("[wave] after wave 1: enemies=" + battle.enemies.size() + " in the set=" + first.size()
                + " speeds=" + first.stream().map(e -> e.getAttribute(AttributeType.SPEED).get()).toList());

        Assertions.assertFalse(first.isEmpty(), "「新入场的敌方目标」-- the wave brought enemies");
        for (CanHit enemy : first) {
            Assertions.assertTrue(enemy.getAttribute(AttributeType.SPEED).get() > 0, "the rule reached it");
        }

        // a second wave must start the set over rather than accumulate
        if (battle.getWaveManager().hasNextWave()) {
            battle.getWaveManager().nextWave();
            List<CanHit> second = battle.waveMonsters();
            System.out.println("[wave] after wave 2: in the set=" + second.size()
                    + " ; any of the first wave still in it = " + second.stream().anyMatch(first::contains));
            Assertions.assertFalse(second.stream().anyMatch(first::contains),
                    "「新入场」-- last wave's monsters are not this wave's");
        }
    }
}
