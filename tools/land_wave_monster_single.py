"""A singe-target `wave_monster`, so a commanded cast can be aimed at a newly arrived enemy (2026-10-02).

Measured before writing:
  * `CAST_SKILL`'s `target` is the CASTER (`CanHit actor = require(resolveTarget(effect, ctx), "target", ctx)`), and the victims come from
    the skill's own business (`damaging ? battle.getOpponents(actor) : battle.getSideOf(actor)`).
  * `cast_target` is the field for "who a COMMANDED cast is aimed at" (EffectSpec: `@SerializedName("cast_target")`, reader 1414's
    `"holder_of:同袍"`); it reads through `resolveSelector`, the SINGLE-target switch, and reorders the victims so the aim goes first --
    which is the unit `SkillExecutor` reads as `targets.getFirst()`.
So the spelling the ode's fifth clause needs is a single-target one: the enemy that entered with the current wave.
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


T = "src/main/java/com/laosun/aluminium/models/TriggerInterpreter.java"
patch(T, '            "wave_monsters",',
      '            // The SINGLE-target sibling of `wave_monsters`, for `cast_target` -- a commanded cast names ONE aim, and the\n'
      '            // game spells the source of that aim as the `OnWaveMonster` event.\n'
      '            "wave_monster",', "the selector name")

patch(T, '            case TARGET_RANDOM_HIT_ENEMY -> require(randomHitEnemy(ctx), TARGET_RANDOM_HIT_ENEMY, ctx);',
      '            // The enemy that entered with the current wave: a commanded cast needs ONE aim, and `wave_monsters` is the set.\n'
      '            // Fails loudly on an empty wave -- an aim that resolves to nobody is the silence this engine refuses.\n'
      '            case "wave_monster" -> require(\n'
      '                    ctx.battle() == null || ctx.battle().waveMonsters().isEmpty()\n'
      '                            ? null\n'
      '                            : ctx.battle().waveMonsters().getFirst(),\n'
      '                    "wave_monster", ctx);', "the resolver case")
