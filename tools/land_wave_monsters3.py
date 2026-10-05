"""Land the `wave_monsters` selector (2026-10-02).

The anchor is the game own event, measured last round: `MServant_CyreneServant_00_AmazingBuff_Mydeimos_OnWaveMonster` listens for
`"Event": "OnWaveMonster"` and answers with a `TurnInsertAction` -- an enemy that entered with a wave. Reader: 1415's memosprite skill 8,
「若施放前目标被消灭则对**新入场**的敌方目标施放」.

Ordering, also measured: `WaveManager.nextWave()` is `waveIndex++` -> `spawnWave` -> `battle.beginWave()` -> `WAVE_START`, so the record of
the PREVIOUS wave must be dropped BEFORE the spawn -- hanging the clear on `beginWave()` erases the wave that just arrived.
"""
import io
import sys


def patch(path, anchor, addition, label, before=True):
    txt = io.open(path, encoding="utf-8").read()
    if txt.count(anchor) != 1:
        sys.exit("REFUSING %s : %d" % (label, txt.count(anchor)))
    i = txt.index(anchor)
    line_start = txt.rfind("\n", 0, i) + 1
    indent = txt[line_start:i] if before else ""
    block = "".join((indent + l).rstrip() + "\n" if l.strip() else "\n" for l in addition.split("\n"))
    out = txt[:line_start] + block + txt[line_start:] if before else txt[:i] + block + txt[i:]
    io.open(path, "w", encoding="utf-8", newline="\n").write(out)
    print("ok   %s" % label)


BATTLE = "src/main/java/com/laosun/aluminium/Battle.java"
patch(BATTLE, "    private final List<CanHit> lethallyHitThisAction = new ArrayList<>();", """    /**
     * The enemies that ENTERED with the current wave (2026-10-02).
     *
     * <p>\\u2b50 The word is the game own, and it is an EVENT rather than a target type:
     * `MServant_CyreneServant_00_AmazingBuff_Mydeimos_OnWaveMonster` listens for `"Event": "OnWaveMonster"` and answers with a
     * `TurnInsertAction` -- a wave monster has entered. Reader: 1415's memosprite skill 8,
     * \\u300c\\u82e5\\u65bd\\u653e\\u524d\\u76ee\\u6807\\u88ab\\u6d88\\u706d\\u5219\\u5bf9**\\u65b0\\u5165\\u573a**\\u7684\\u654c\\u65b9\\u76ee\\u6807\\u65bd\\u653e\\u300d.
     */
    private final List<CanHit> waveMonsters = new ArrayList<>();

    /**
     * Forgets the previous wave's monsters. Called by {@code WaveManager.nextWave} BEFORE the new spawn, because the order measured there
     * is `waveIndex++` -> `spawnWave` -> `beginWave` -> `WAVE_START`: clearing any later would erase the wave that just arrived.
     */
    public void forgetWaveMonsters() {
        waveMonsters.clear();
    }

    /** Records an enemy that has just entered with a wave ({@code WaveManager.spawnWave}). */
    public void noteWaveMonster(CanHit enemy) {
        if (enemy != null && !waveMonsters.contains(enemy)) {
            waveMonsters.add(enemy);
        }
    }

    /** The enemies that entered with the current wave. */
    public List<CanHit> waveMonsters() {
        return List.copyOf(waveMonsters);
    }
""", "the wave-monster record")

patch("src/main/java/com/laosun/aluminium/models/WaveManager.java",
      "        waveIndex++\n        spawnWave(waveIndex);",
      "        // \\u2b50 Before the spawn, not at `beginWave()` -- see `forgetWaveMonsters`.\n"
      "        battle.forgetWaveMonsters();",
      "clearing before the spawn")

patch("src/main/java/com/laosun/aluminium/models/WaveManager.java",
      "                battle.enemies.add(enemy);",
      "                battle.noteWaveMonster(enemy);",
      "recording each spawned enemy")

T = "src/main/java/com/laosun/aluminium/models/TriggerInterpreter.java"
patch(T, '            "all_allies_lethally_hit_this_action",',
      '            // \\u2b50 \\u300c\\u65b0\\u5165\\u573a\\u7684\\u654c\\u65b9\\u76ee\\u6807\\u300d (1415 memosprite skill 8): the game spells this as an EVENT,\n'
      '            // `OnWaveMonster`, so the selector is named after it -- an enemy that entered with the current wave.\n'
      '            "wave_monsters",', "the selector name")

patch(T, '        if ("all_allies_lethally_hit_this_action".equals(selector)) {',
      '        if ("wave_monsters".equals(selector)) {\n'
      '            if (battle == null) {\n'
      '                throw new IllegalStateException(\n'
      '                        "Effect targets \\"wave_monsters\\" but no battle was supplied to read the current wave");\n'
      '            }\n'
      '            return battle.waveMonsters();\n'
      '        }\n', "the resolver branch")
