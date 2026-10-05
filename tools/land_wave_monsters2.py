"""Land the wave-monster record and the `wave_monsters` selector (2026-10-02).

The anchor, measured in tbgd rather than invented: `MServant_CyreneServant_00_AmazingBuff_Mydeimos_OnWaveMonster` listens for
`"Event": "OnWaveMonster"` and answers with a `TurnInsertAction` -- the game's own word for 「新入场」 is an ENEMY THAT ENTERED WITH A WAVE.
Our engine has waves (`WaveManager.spawnWave` -> `Battle.beginWave()`, the counter every per-wave limit already compares against), so the
record is hung on that boundary, exactly as `lethallyHitThisAction` is hung on `TURN_START`.
"""
import io
import sys


def patch(path, anchor, addition, label, before=True):
    txt = io.open(path, encoding="utf-8").read()
    if txt.count(anchor) != 1:
        sys.exit("REFUSING %s : %d" % (label, txt.count(anchor)))
    i = txt.index(anchor)
    ls = txt.rfind("\n", 0, i) + 1
    indent = txt[ls:i] if before else ""
    add = "".join((indent + l).rstrip() + "\n" if l.strip() else "\n" for l in addition.split("\n"))
    out = txt[:ls] + add + txt[ls:] if before else txt[:i] + add + txt[i:]
    io.open(path, "w", encoding="utf-8", newline="\n").write(out)
    print("ok   %s" % label)


BATTLE = "src/main/java/com/laosun/aluminium/Battle.java"
patch(BATTLE, "private final List<CanHit> lethallyHitThisAction = new ArrayList<>();", """/**
 * The enemies that ENTERED with the current wave (2026-10-02).
 *
 * <p>\\u2b50 The word is the game's own, and it is an EVENT rather than a target type:
 * `MServant_CyreneServant_00_AmazingBuff_Mydeimos_OnWaveMonster` listens for `"Event": "OnWaveMonster"` and answers with a
 * `TurnInsertAction` -- a wave monster has entered. Reader: 1415's memosprite skill 8,
 * \\u300c\\u82e5\\u65bd\\u653e\\u524d\\u76ee\\u6807\\u88ab\\u6d88\\u706d\\u5219\\u5bf9**\\u65b0\\u5165\\u573a**\\u7684\\u654c\\u65b9\\u76ee\\u6807\\u65bd\\u653e\\u300d.
 *
 * <p>\\u26a0 Cleared in `beginWave()`, the counter every per-wave limit already compares against -- so "this wave" is the boundary the
 * engine had, not a new one.
 */
private final List<CanHit> waveMonsters = new ArrayList<>();

/** Records an enemy that has just entered with a wave ({@code WaveManager.spawnWave}). */
public void noteWaveMonster(CanHit enemy) {
    if (enemy != null && !waveMonsters.contains(enemy)) {
        waveMonsters.add(enemy);
    }
}

/** The enemies that entered with the current wave. */
public List<CanHit> waveMonsters() {
    return List.copyOf(waveMonsters);
}""", "the wave-monster record")
patch(BATTLE, "public void beginWave() {", "waveMonsters.clear();", "clearing it at the wave boundary")

patch("src/main/java/com/laosun/aluminium/models/WaveManager.java",
      "battle.enemies.add(enemy);", "battle.noteWaveMonster(enemy);", "recording each spawned enemy")

T = "src/main/java/com/laosun/aluminium/models/TriggerInterpreter.java"
patch(T, '"all_allies_lethally_hit_this_action",',
      '// \\u2b50 \\u300c\\u65b0\\u5165\\u573a\\u7684\\u654c\\u65b9\\u76ee\\u6807\\u300d (1415 memosprite skill 8): named after the event the game itself uses\n'
      '// for it, `OnWaveMonster` -- an enemy that entered with the current wave.\n'
      '"wave_monsters",', "the selector name")
patch(T, 'if ("all_allies_lethally_hit_this_action".equals(selector)) {',
      'if ("wave_monsters".equals(selector)) {\n'
      '    if (battle == null) {\n'
      '        throw new IllegalStateException(\n'
      '                "Effect targets \\"wave_monsters\\" but no battle was supplied to read the current wave");\n'
      '    }\n'
      '    return battle.waveMonsters();\n'
      '}\n', "the resolver branch")
