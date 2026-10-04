"""A per-wave firing limit, mirroring the per-attack one (round 19 of the goal).

Reader: 1506's warehouse skill 「若敌方目标对我方施加了控制类负面状态，则使我方全体获得【防火墙】，持续 1 回合…该效果**每个波次最多触发 1 次**」.
Measured: `TriggerSpec` has cooldown / per_turn / per_subject / once_per_battle / once_per_attack and NO per-wave field.

Everything is mirrored from the per-attack twin, which exists for exactly this reason (its own comment: "the one cap a per-turn count
cannot express"):
  * `TriggerSpec.oncePerWave`            <- `oncePerAttack`
  * `CanHit.isWaveLimitReady` / `recordWaveUse` <- `isAttackLimitReady` / `recordAttackUse`
  * the interpreter's gate and recording  <- the per_attack ones, three lines apart
  * the SEQUENCE is a wave counter on `Battle`, incremented by `WaveManager` (which already owns `waveIndex`) right before WAVE_START --
    a sequence comparison needs no reset, which is why the twin works that way.
"""
import io
import sys

SPEC = "src/main/java/com/laosun/aluminium/beans/TriggerSpec.java"
CANHIT = "src/main/java/com/laosun/aluminium/models/CanHit.java"
BATTLE = "src/main/java/com/laosun/aluminium/Battle.java"
WAVE = None
INTERP = "src/main/java/com/laosun/aluminium/models/TriggerInterpreter.java"
JUDGE = "src/test/java/com/laosun/aluminium/test/WaveLimitTest.java"

import os
for base, dirs, files in os.walk("src/main/java"):
    if "WaveManager.java" in files:
        WAVE = os.path.join(base, "WaveManager.java").replace("\\", "/")
if WAVE is None:
    sys.exit("REFUSING: WaveManager.java not found")


def patch(path, old, new, label, count=1):
    text = io.open(path, encoding="utf-8").read()
    found = text.count(old)
    if found != count:
        sys.stderr.write("REFUSING %s: the anchor appears %d times in %s\n" % (label, found, path))
        raise SystemExit(1)
    io.open(path, "w", encoding="utf-8", newline="\n").write(text.replace(old, new, count))
    print("ok   %s" % label)


# 1. the field, beside its twin
patch(
    SPEC,
    '    @SerializedName("once_per_attack")',
    '    /**\n'
    '     * \u300c\u8be5\u6548\u679c**\u6bcf\u4e2a\u6ce2\u6b21**\u6700\u591a\u89e6\u53d1 1 \u6b21\u300d (2026-10-02; reader: 1506's warehouse skill). \u26a0 `per_turn` cannot say it and neither can\n'
    '     * `once_per_battle`: a wave is neither, so it gets the same treatment `once_per_attack` got -- a SEQUENCE comparison, which needs no\n'
    '     * reset because a new wave is a new number.\n'
    '     */\n'
    '    @SerializedName("once_per_wave")\n'
    '    private Boolean oncePerWave;\n\n'
    '    @SerializedName("once_per_attack")',
    "TriggerSpec.oncePerWave",
)

# 2. the counter and its predicates, beside their twins
patch(
    CANHIT,
    "    /** Per rule, how many times it has fired inside that attack (the cap is {@code per_attack}). */",
    "    /** Per rule, the wave sequence of its last \"once per wave\" firing (see {@link #isWaveLimitReady}). */\n"
    "    private final Map<String, Integer> ruleWaveUses = new HashMap<>();\n\n"
    "    /** Per rule, how many times it has fired inside that attack (the cap is {@code per_attack}). */",
    "CanHit.ruleWaveUses",
)

io.open(CANHIT + ".tmp", "w", encoding="utf-8").close()


def insert_after(path, anchor, addition, label):
    text = io.open(path, encoding="utf-8").read()
    if text.count(anchor) != 1:
        sys.stderr.write("REFUSING %s: the anchor appears %d times\n" % (label, text.count(anchor)))
        raise SystemExit(1)
    io.open(path, "w", encoding="utf-8", newline="\n").write(text.replace(anchor, anchor + addition, 1))
    print("ok   %s" % label)


insert_after(
    CANHIT,
    "    public boolean isAttackLimitReady(String key, int attackSequence, int cap) {",
    "",
    "marker",
)

text = io.open(CANHIT, encoding="utf-8").read()
start = text.index("    public boolean isAttackLimitReady(String key, int attackSequence, int cap) {")
end = text.index("\n    }", start) + len("\n    }")
block = text[start:end]
insert_after(
    CANHIT,
    block,
    "\n\n    /**\n"
    "     * The same question for a WAVE (see {@link #isAttackLimitReady}): the sequence is `Battle.waveSequence()`, and because a new wave\n"
    "     * is a new number, nothing has to be cleared.\n"
    "     */\n"
    "    public boolean isWaveLimitReady(String key, int waveSequence) {\n"
    "        Integer sequence = ruleWaveUses.get(key);\n"
    "        return sequence == null || sequence.intValue() != waveSequence;\n"
    "    }\n\n"
    "    /** Records that the rule has used its wave allowance in {@code waveSequence}. */\n"
    "    public void recordWaveUse(String key, int waveSequence) {\n"
    "        ruleWaveUses.put(key, waveSequence);\n"
    "    }",
    "CanHit.isWaveLimitReady / recordWaveUse",
)

# 3. the wave sequence on the battle
patch(
    BATTLE,
    "    private String lastChangedResource;",
    "    private String lastChangedResource;\n\n"
    "    /**\n"
    "     * \u2705 How many waves have begun (2026-10-02). A SEQUENCE, not a flag: \u300c\u6bcf\u4e2a\u6ce2\u6b21\u6700\u591a 1 \u6b21\u300d is then a comparison against the number the\n"
    "     * rule last fired in, so nothing has to be reset -- the same reason the per-attack cap works this way.\n"
    "     */\n"
    "    private int waveSequence;\n\n"
    "    /** Starts a wave: the counter every per-wave limit is compared against. Called before WAVE_START is fired. */\n"
    "    public void beginWave() {\n"
    "        waveSequence++;\n"
    "    }\n\n"
    "    /** The current wave's number. */\n"
    "    public int waveSequence() {\n"
    "        return waveSequence;\n"
    "    }",
    "Battle.waveSequence / beginWave",
)

# 4. the wave manager calls it before the event
patch(
    WAVE,
    "        battle.fireTriggers(TriggerEvent.WAVE_START);",
    "        // \u26a0 Before the event: a per-wave limit is a comparison against this number, so the rule that fires ON the wave's start already\n"
    "        // belongs to the new wave (otherwise the first wave's own rule would be compared against the previous number).\n"
    "        battle.beginWave();\n"
    "        battle.fireTriggers(TriggerEvent.WAVE_START);",
    "WaveManager.beginWave",
)

# 5. the interpreter's gate and recording, beside the per-attack pair
patch(
    INTERP,
    """            if (owner != null && rule.perAttack() > 0
                    && !owner.isAttackLimitReady(limitKey, battle.attackSequence(), rule.perAttack())) {
                continue;
            }""",
    """            if (owner != null && rule.perAttack() > 0
                    && !owner.isAttackLimitReady(limitKey, battle.attackSequence(), rule.perAttack())) {
                continue;
            }
            // \u300c\u6bcf\u4e2a\u6ce2\u6b21\u6700\u591a\u89e6\u53d1 1 \u6b21\u300d: the same shape as the attack cap above, compared against the wave number instead.
            if (owner != null && Boolean.TRUE.equals(rule.oncePerWave())
                    && !owner.isWaveLimitReady(limitKey, battle.waveSequence())) {
                continue;
            }""",
    "the interpreter's wave gate",
)

patch(
    INTERP,
    """                if (rule.perAttack() > 0) {
                    owner.recordAttackUse(limitKey, battle.attackSequence());
                }""",
    """                if (rule.perAttack() > 0) {
                    owner.recordAttackUse(limitKey, battle.attackSequence());
                }
                if (Boolean.TRUE.equals(rule.oncePerWave())) {
                    owner.recordWaveUse(limitKey, battle.waveSequence());
                }""",
    "the interpreter's wave recording",
)

io.open(JUDGE, "w", encoding="utf-8", newline="").write('''package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.ResourceSpec;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * \u300c\u8be5\u6548\u679c**\u6bcf\u4e2a\u6ce2\u6b21**\u6700\u591a\u89e6\u53d1 1 \u6b21\u300d (2026-10-02).
 *
 * <p>Two-way: twice in one wave fires once, and the next wave fires again -- which is the whole difference between a wave cap and a
 * battle-long one, and what a mutant that ignores the cap turns red.
 */
public class WaveLimitTest {
    private static final int OWNER = 1002;
    private static final int MONSTER = 1002011;
    private static final String PROBE = "probeWave";

    /** Twice in one wave = once; a new wave = once more. */
    @Test
    public void theCapIsPerWaveNotPerBattle() {
        Character owner = CharacterFactory.create(OWNER, 80, false, null, null, 0);
        EffectSpec mark = new EffectSpec();
        TriggerSpecs.set(mark, "op", "GAIN_RESOURCE");
        TriggerSpecs.set(mark, "resource", PROBE);
        TriggerSpecs.set(mark, "amount", 1.0);
        TriggerSpecs.set(mark, "target", "self");

        owner.setTriggerTable(new TriggerTable(OWNER, List.of(
                TriggerSpecs.rule("SKILL_CAST", List.of("actor == self"), mark)
                        .withLimits(null, null, null, null, Boolean.TRUE)),
                List.of(new ResourceSpec(PROBE, 99, 0, null, null, "probe", null))));

        Battle battle = new Battle(List.of(owner),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        battle.beginWave();

        battle.fireTriggers(TriggerEvent.SKILL_CAST, owner, null, 0, 0);
        battle.fireTriggers(TriggerEvent.SKILL_CAST, owner, null, 0, 0);
        int firstWave = owner.getResources().has(PROBE) ? owner.getResources().value(PROBE) : 0;

        battle.beginWave();
        battle.fireTriggers(TriggerEvent.SKILL_CAST, owner, null, 0, 0);
        int secondWave = owner.getResources().has(PROBE) ? owner.getResources().value(PROBE) : 0;
        System.out.println("[wave-limit] two casts in wave 1 -> " + firstWave + " ; one more in wave 2 -> " + secondWave);

        Assertions.assertEquals(1, firstWave,
                "\u300c\u6bcf\u4e2a\u6ce2\u6b21\u6700\u591a\u89e6\u53d1 1 \u6b21\u300d-- the second cast in the same wave changes nothing");
        Assertions.assertEquals(2, secondWave,
                "and the NEXT wave fires again, which is what tells a wave cap apart from a battle-long one");
    }
}
''')
print("ok   the judge is written")
