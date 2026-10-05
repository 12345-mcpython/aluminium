"""Undo the DUPLICATE half of `event_amount` (2026-10-02).

Measured after shipping: the "magnitude comes from the event" spelling ALREADY EXISTS --
`EffectSpec.amountFromEvent` (+ `amount_percent`), used by four shipped rules (1312 on SKILL_POINT_SPENT, 1505 twice on
ENERGY_GAINED, 1506 on RESOURCE_CHANGED). So `scale: "event_amount"` was a second name for a thing the engine already
had, which is exactly what this project refuses to keep. What is genuinely new is `times_from`: the existing field
carries an AMOUNT, never a REPEAT COUNT.

This drops: the `grantAmount` branch, the `derivedMagnitude` branch + its constant, and the two whitelist entries
(`SCALES`, `ENERGY_SCALES`). It keeps `times_from` and only the judge case that proves it -- plus the two judge cases
that proved the magnitude, which now belong to the existing spelling and are removed rather than left to rot.
ASCII only.
"""
import io
import sys

INTERP = "src/main/java/com/laosun/aluminium/models/TriggerInterpreter.java"
JUDGE = "src/test/java/com/laosun/aluminium/test/EventAmountTest.java"

text = io.open(INTERP, encoding="utf-8").read()
DROPS = [
    ('            case "event_amount" -> Math.abs(ctx.amount()) * share + flat;\n', ""),
    ('        Set.of("target_max_hp", "target_lost_hp", "owner_max_hp", "owner_def", "owner_attack", "event_amount");',
     '        Set.of("target_max_hp", "target_lost_hp", "owner_max_hp", "owner_def", "owner_attack");'),
    ('    private static final Set<String> ENERGY_SCALES = Set.of("target_max_energy", "event_amount");',
     '    private static final Set<String> ENERGY_SCALES = Set.of("target_max_energy");'),
    ('        if (SELF_MAX_ENERGY.equals(raw) || EVENT_AMOUNT.equals(raw)) {',
     '        if (SELF_MAX_ENERGY.equals(raw)) {'),
]
for old, new in DROPS:
    if text.count(old) != 1:
        print("FAIL interp: anchor matched %d times -> %r" % (text.count(old), old[:60]))
        sys.exit(1)
    text = text.replace(old, new)

# the constant and the derivedMagnitude branch, dropped together
start = text.index("    /**\n     * 「每消耗/每损失 1 点…」 (2026-10-02): a magnitude that follows the <b>triggering event</b>.")
end = text.index('    private static final String EVENT_AMOUNT = "event_amount";\n') + len('    private static final String EVENT_AMOUNT = "event_amount";\n')
text = text[:start] + text[end:]

branch_start = text.index("        if (EVENT_AMOUNT.equals(effect.getScale().trim())) {")
branch_end = text.index("        if (SELF_MAX_ENERGY.equals(effect.getScale().trim())) {")
text = text[:branch_start] + text[branch_end:]

if "EVENT_AMOUNT" in text:
    print("FAIL interp: EVENT_AMOUNT still referenced")
    sys.exit(1)
io.open(INTERP, "w", encoding="utf-8", newline="").write(text)
print("ok   interp: the duplicate magnitude spelling is gone, times_from stays")

judge = io.open(JUDGE, encoding="utf-8").read()
for marker_start, marker_end in (
    ("    /** ⭐ A magnitude off the event", "    /** ⭐ The same number as a MODIFIER"),
    ("    /** ⭐ The same number as a MODIFIER", "    /** ⭐ A repeat count off the event"),
):
    if marker_start in judge and marker_end in judge:
        a = judge.index(marker_start)
        b = judge.index(marker_end)
        judge = judge[:a] + judge[b:]
judge = judge.replace(
    " * <p>Readers (all with data files): 1407's talent",
    " * <p>`effect_amount` was WITHDRAWN the same day: the magnitude half duplicated `amount_from_event` (4 shipped\n"
    " * rules already use it). What stays is the repeat count -- the existing field carries an amount, never a count.\n"
    " *\n"
    " * <p>Readers (all with data files): 1407's talent")
io.open(JUDGE, "w", encoding="utf-8", newline="").write(judge)
print("ok   judge: the two magnitude cases removed, the repeat case kept")
