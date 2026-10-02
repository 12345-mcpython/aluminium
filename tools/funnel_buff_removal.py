"""Refactor (behaviour-preserving): every explicit detach goes through `removeBuff`.

`removeOneBuff(Class)` and `addStackable` each did the two-step dance by hand (`buffs.remove(i)` then
`X.removeBuff(instance)`), while `removeBuff(AbstractBuff)` does exactly those two things by identity. Routing them
through it makes "a named state is no longer on this unit" ONE fact -- which is what `STATE_ENDED` will need, since a
state can leave by expiry (now funnelled in `tickBuff`) or by an explicit removal like this one.

No behaviour change: the full suite is the judge. ASCII only.
"""
import io
import re
import sys

PATH = "src/main/java/com/laosun/aluminium/models/buff/BuffManager.java"
PATTERN = re.compile(r"buffs\.remove\(i\);\s*\n\s*(\w+)\.removeBuff\(instance\);")

text = io.open(PATH, encoding="utf-8").read()
matches = PATTERN.findall(text)
if len(matches) != 2:
    print("FAIL: expected 2 hand-rolled detach sites, found %d (%s)" % (len(matches), matches))
    sys.exit(1)

patched = PATTERN.sub(lambda m: "removeBuff(%s);" % m.group(1), text)
io.open(PATH, "w", encoding="utf-8", newline="").write(patched)
print("ok   rerouted %d sites through removeBuff: %s" % (len(matches), matches))
