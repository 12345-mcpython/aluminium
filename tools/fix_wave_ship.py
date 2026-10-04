"""Repair the ship script: the real way to set a limit, and two bits of junk removed (round 19)."""
import io
import sys

P = "tools/ship_wave_limit.py"
t = io.open(P, encoding="utf-8").read()

junk1 = 'io.open(CANHIT + ".tmp", "w", encoding="utf-8").close()\n\n\n'
junk2 = '''insert_after(
    CANHIT,
    "    public boolean isAttackLimitReady(String key, int attackSequence, int cap) {",
    "",
    "marker",
)

'''
for junk, label in ((junk1, "the stray tmp open"), (junk2, "the pointless marker insert")):
    if t.count(junk) != 1:
        sys.exit("REFUSING: %s appears %d times" % (label, t.count(junk)))
    t = t.replace(junk, "", 1)

old_rule = """                TriggerSpecs.rule("SKILL_CAST", List.of("actor == self"), mark)
                        .withLimits(null, null, null, null, Boolean.TRUE)),"""
new_rule = """                withOncePerWave(TriggerSpecs.rule("SKILL_CAST", List.of("actor == self"), mark))),"""
if t.count(old_rule) != 1:
    sys.exit("REFUSING: the invented API appears %d times" % t.count(old_rule))
t = t.replace(old_rule, new_rule, 1)

# and give the judge the helper it now calls
helper = """
    /** The rule, with the new per-wave limit stated the way a data file states it. */
    private static com.laosun.aluminium.beans.TriggerSpec withOncePerWave(
            com.laosun.aluminium.beans.TriggerSpec spec) {
        TriggerSpecs.set(spec, "oncePerWave", Boolean.TRUE);
        return spec;
    }
}
"""
tail = "\n}\n"
if not t.rstrip().endswith("''')"):  # the judge text is inside the ship script, so patch the judge body itself
    sys.exit("REFUSING: unexpected ship-script shape")
t = t.replace('''        System.out.println("[wave-limit] two casts in wave 1 -> " + firstWave + " ; one more in wave 2 -> " + secondWave);

        Assertions.assertEquals(1, firstWave,''',
              '''        System.out.println("[wave-limit] two casts in wave 1 -> " + firstWave + " ; one more in wave 2 -> " + secondWave);

        Assertions.assertEquals(1, firstWave,''', 1)
# insert the helper before the judge's closing brace inside the embedded judge text
marker = '''                "and the NEXT wave fires again, which is what tells a wave cap apart from a battle-long one");
    }
}
'''
if t.count(marker) != 1:
    sys.exit("REFUSING: the judge tail appears %d times" % t.count(marker))
t = t.replace(marker, '''                "and the NEXT wave fires again, which is what tells a wave cap apart from a battle-long one");
    }

    /** The rule, with the new per-wave limit stated the way a data file states it. */
    private static com.laosun.aluminium.beans.TriggerSpec withOncePerWave(
            com.laosun.aluminium.beans.TriggerSpec spec) {
        TriggerSpecs.set(spec, "oncePerWave", Boolean.TRUE);
        return spec;
    }
}
''', 1)
io.open(P, "w", encoding="utf-8", newline="\n").write(t)
print("ok   the ship script is repaired")
