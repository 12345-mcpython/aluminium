"""`INSERTED_CAST_END` carries the unit the commanded cast was AIMED at (2026-10-02, item 66).

Reader: 1415's memosprite skill 「献予「律法」之诗」 -- 「整场生效，**对刻律德菈施放后**…**奇袭结束后**，使**刻律德菈**获得 1 点充能。」
The moment already existed and 1412 already subscribes to it, but the event was fired as
`fireTriggers(INSERTED_CAST_END, actor, actor, 0, 0)`: the target slot carried the CASTER, so a rule could never reach the
unit the commanded skill was aimed at -- and that is exactly who the sentence talks about (刻律德菈, who need not be the one
holding 【爵位】).

Measured before patching: `victims` is in scope one line above the firing, and 1412's own subscriber states no `target` at all
(its effects are `SPEND_RESOURCE` / `REMOVE_STATE`, both on the actor), so nothing shipped changes meaning.
"""
import io
import sys

PATH = "src/main/java/com/laosun/aluminium/models/TriggerInterpreter.java"
text = io.open(PATH, encoding="utf-8").read()

OLD = """        battle.fireTriggers(TriggerEvent.INSERTED_CAST_END, actor, actor, 0, 0);"""
NEW = """        // ⭐ The event carries the unit the commanded cast was AIMED at, not the caster (2026-10-02; reader: 1415's
        // 「奇袭结束后，使刻律德菈获得 1 点充能」). The actor is still the actor; the target slot now answers "who was it cast on",
        // which is what that sentence needs -- and 1412's own subscriber states no target, so nothing shipped moves.
        CanHit aimed = victims == null || victims.isEmpty() ? actor : victims.getFirst();
        battle.fireTriggers(TriggerEvent.INSERTED_CAST_END, actor, aimed, 0, 0);"""

if text.count(OLD) != 1:
    sys.stderr.write("REFUSING: the anchor appears %d times\n" % text.count(OLD))
    raise SystemExit(1)
io.open(PATH, "w", encoding="utf-8", newline="\n").write(text.replace(OLD, NEW, 1))
print("ok   the event carries the aimed unit")
