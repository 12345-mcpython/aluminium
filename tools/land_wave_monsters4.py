"""Finish the wave-monster landing: the two WaveManager edits and the two TriggerInterpreter edits.

The `Battle` half is already in (its patch line printed ok). The first attempt at `WaveManager` was refused because the anchor carried the
wrong whitespace, so these anchors are single unique lines and the insertion reuses the anchor line's own indentation.
"""
import io
import sys


def indent_of(txt, i):
    return txt[txt.rfind("\n", 0, i) + 1:i]


def patch(path, anchor, addition, label, before=True):
    txt = io.open(path, encoding="utf-8").read()
    if txt.count(anchor) != 1:
        sys.exit("REFUSING %s : %d" % (label, txt.count(anchor)))
    i = txt.index(anchor)
    line_start = txt.rfind("\n", 0, i) + 1
    indent = indent_of(txt, i) if before else ""
    block = "".join((indent + l).rstrip() + "\n" if l.strip() else "\n" for l in addition.split("\n"))
    out = txt[:line_start] + block + txt[line_start:] if before else txt[:i] + block + txt[i:]
    io.open(path, "w", encoding="utf-8", newline="\n").write(out)
    print("ok   %s" % label)


WAVE = "src/main/java/com/laosun/aluminium/models/WaveManager.java"
patch(WAVE, "spawnWave(waveIndex);",
      "// Before the spawn, not at `beginWave()`: that runs AFTER it and would erase the wave that just arrived.\n"
      "battle.forgetWaveMonsters();", "clearing before the spawn")
patch(WAVE, "battle.enemies.add(enemy);", "battle.noteWaveMonster(enemy);", "recording each spawned enemy")

T = "src/main/java/com/laosun/aluminium/models/TriggerInterpreter.java"
patch(T, '            "all_allies_lethally_hit_this_action",',
      '            // The game spells this as an EVENT, `OnWaveMonster`, so the selector is named after it -- an enemy\n'
      '            // that entered with the current wave.\n'
      '            "wave_monsters",', "the selector name")
patch(T, '        if ("all_allies_lethally_hit_this_action".equals(selector)) {',
      '        if ("wave_monsters".equals(selector)) {\n'
      '            if (battle == null) {\n'
      '                throw new IllegalStateException(\n'
      '                        "Effect targets \\"wave_monsters\\" but no battle was supplied to read the current wave");\n'
      '            }\n'
      '            return battle.waveMonsters();\n'
      '        }\n', "the resolver branch")
