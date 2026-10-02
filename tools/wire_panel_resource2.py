"""Wire the battle's resource reader into the 1-arg summon entry points (2026-10-02).

`Battle` calls `SummonFactory.memosprite(master)` / `servant(master)`, which load the spec themselves. The new twins take
the resource reader (the previous attempt appended the reader to a call that had no spec argument -- reverted).
ASCII only.
"""
import io
import sys

FACTORY = "src/main/java/com/laosun/aluminium/models/enemy/SummonFactory.java"
BATTLE = "src/main/java/com/laosun/aluminium/Battle.java"
READER = "java.util.function.ToIntFunction<String> resourceValue"

factory = io.open(FACTORY, encoding="utf-8").read()
for kind in ("memosprite", "servant"):
    if "public static Summon %s(Character master, %s)" % (kind, READER) in factory:
        print("skip %s: twin exists" % kind)
        continue
    TAIL = "        return %s(master, spec);\n    }\n" % kind
    NEW = (TAIL + "\n"
           "    /** The same, for a panel that derives from a battle-level RESOURCE (see {@link #panelOf}). */\n"
           "    public static Summon %s(Character master, %s) {\n"
           "        MemospriteSpec spec = Memosprites.of(master.getCid());\n"
           "        if (spec == null) {\n"
           "            throw new IllegalArgumentException(\n"
           "                    \"Character \" + master.getName() + \" (\" + master.getCid() + \") has no memosprite spec\");\n"
           "        }\n"
           "        return %s(master, spec, resourceValue);\n"
           "    }\n" % (kind, kind, kind))
    if factory.count(TAIL) != 1:
        print("FAIL %s: tail matched %d times" % (kind, factory.count(TAIL)))
        sys.exit(1)
    factory = factory.replace(TAIL, NEW)
io.open(FACTORY, "w", encoding="utf-8", newline="").write(factory)
print("ok   SummonFactory: 1-arg resource-aware twins")

battle = io.open(BATTLE, encoding="utf-8").read()
wired = 0
for kind in ("memosprite", "servant"):
    OLD = "SummonFactory.%s(master);" % kind
    NEW = "SummonFactory.%s(master, this::partyResourceValue);" % kind
    if battle.count(OLD) == 1:
        battle = battle.replace(OLD, NEW)
        wired += 1
    elif battle.count(NEW) == 1:
        print("skip Battle.%s: already wired" % kind)
    else:
        print("FAIL Battle.%s: matched %d times" % (kind, battle.count(OLD)))
        sys.exit(1)
io.open(BATTLE, "w", encoding="utf-8", newline="").write(battle)
print("ok   Battle: %d call sites pass partyResourceValue" % wired)
