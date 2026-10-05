"""Make the `event_amount` comment accurate (2026-10-02).

Measured after the fact: `scale: "event_amount"` is NOT new -- 1306's `talent_phantasm_stack` already used it on
`ADD_STACK` (「每消耗1点战技点，获得1层【幻相】」). What was missing is the ROUTE: modifiers read their magnitude in
`derivedMagnitude` and had no way to ask the event. The old comment claimed the spelling did not exist, which is wrong.

ASCII-only matching, so the patch cannot be mangled by the shell.
"""
import io
import sys

PATH = "src/main/java/com/laosun/aluminium/models/TriggerInterpreter.java"
OLD = ("            // modifier. ⚠ Not a duplicate of `amount_from_event`: that spelling is read in `gainResource` ONLY\n"
       "            // (it was withdrawn from `grantAmount` for exactly that reason), while these readers are all modifiers.\n")
NEW = ("            // modifier. ⚠ `event_amount` is NOT a new spelling: `ADD_STACK` already read it (1306's\n"
       "            // `talent_phantasm_stack`, one 【幻相】 layer per point spent). What was missing is this ROUTE --\n"
       "            // modifiers read their magnitude here and had no way to ask the event. (It was ALSO withdrawn once from\n"
       "            // `grantAmount`, where `amount_from_event` already served GAIN_RESOURCE; that note lives there.)\n")

text = io.open(PATH, encoding="utf-8").read()
if OLD not in text:
    print("FAIL: the old comment is not there")
    sys.exit(1)
io.open(PATH, "w", encoding="utf-8", newline="").write(text.replace(OLD, NEW))
print("ok   the event_amount comment now states what was measured")
