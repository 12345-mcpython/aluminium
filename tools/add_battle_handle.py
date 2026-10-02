"""STATE_ENDED, step 1: give BuffManager a Battle handle (2026-10-02).

Why a field instead of threading five signatures: `CanHit` has no `getBattle()` (measured: zero hits repo-wide) and the
turn hooks are `Runnable` FIELDS on `CanHit`, so passing a Battle through them would touch 5-8 sites. `Battle` owns the
roster, so it can hand the handle to each unit's manager once.

This step is purely additive: the field is written and read by nothing yet (the event comes next), so the full suite is
the judge. If the constructor's roster assignment cannot be found, the script prints the body and changes nothing.
"""
import io
import re
import sys

MANAGER = "src/main/java/com/laosun/aluminium/models/buff/BuffManager.java"
BATTLE = "src/main/java/com/laosun/aluminium/Battle.java"

# 1) the field + setter ----------------------------------------------------------------------------------
manager = io.open(MANAGER, encoding="utf-8").read()
OLD_CTOR = ("    public BuffManager(CanHit instance) {\n"
            "        this.instance = instance;\n"
            "    }\n")
NEW_CTOR = ("    public BuffManager(CanHit instance) {\n"
            "        this.instance = instance;\n"
            "    }\n"
            "\n"
            "    /**\n"
            "     * The battle this unit is in, so a state that leaves the unit can be announced (2026-10-02; the\n"
            "     * {@code STATE_ENDED} event's reader: 1211's 「【生息】结束时…」).\n"
            "     *\n"
            "     * <p>Set by {@link com.laosun.aluminium.Battle} when it takes the roster. It may stay {@code null} in a\n"
            "     * unit built outside a battle, which is why every use of it guards for that: a state still leaves the\n"
            "     * unit, there is simply nobody to tell.\n"
            "     */\n"
            "    private com.laosun.aluminium.Battle battle;\n"
            "\n"
            "    public void setBattle(com.laosun.aluminium.Battle battle) {\n"
            "        this.battle = battle;\n"
            "    }\n")
if "private com.laosun.aluminium.Battle battle;" in manager:
    print("skip BuffManager: already has the battle handle")
else:
    if manager.count(OLD_CTOR) != 1:
        print("FAIL BuffManager: constructor anchor matched %d times" % manager.count(OLD_CTOR))
        sys.exit(1)
    io.open(MANAGER, "w", encoding="utf-8", newline="").write(manager.replace(OLD_CTOR, NEW_CTOR))
    print("ok   BuffManager.battle field + setBattle")

# 2) the assignment in Battle's constructor ---------------------------------------------------------------
text = io.open(BATTLE, encoding="utf-8").read()
if "setBattle(this)" in text:
    print("skip Battle: already assigns the handle")
    raise SystemExit(0)

ctor = re.search(r"public Battle\([^)]*Random[^)]*\)\s*\{", text)
if not ctor:
    print("FAIL Battle: no canonical constructor found")
    sys.exit(1)
body_end = text.find("\n    }", ctor.end())
body = text[ctor.end():body_end]
roster = re.search(r"^(\s*)(characters|allies)\s*=", body, re.M)
if not roster:
    print("FAIL Battle: no roster assignment in the canonical constructor; body is:")
    print(body[:1200])
    sys.exit(1)

insertion = ("\n%s// The units' buff managers need the battle to announce a state that leaves them (STATE_ENDED).\n"
             "%sfor (CanHit unit : allies) {\n"
             "%s    unit.getBuffManager().setBattle(this);\n"
             "%s}\n" % (roster.group(1), roster.group(1), roster.group(1), roster.group(1)))
pos = ctor.end() + roster.start()
patched = text[:pos] + insertion + text[pos:]
io.open(BATTLE, "w", encoding="utf-8", newline="").write(patched)
print("ok   Battle constructor hands the handle to every ally's manager")
