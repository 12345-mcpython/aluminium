"""STATE_ENDED, step 2b: the state-name channel on TriggerContext (2026-10-02).

A record, so the name is a 14th component. Three kinds of site are touched, by STRUCTURE rather than by guessing:
  * the canonical header gains `String stateName`;
  * the two `this(..., "", List.of(), 0, 0, List.of())` / `this(..., null, "", List.of(), 0, 0, List.of())` calls
    (the canonical-shaped ones) pass `null`;
  * every `new TriggerContext(...)` -- the `with*` copy helpers -- passes the CURRENT `stateName` along, which is what
    keeps a chained build from dropping it (the trap the file's own comment records for `weakHitCount`).
Plus a `withStateName(...)` helper for the tick path to use.
ASCII only.
"""
import io
import re
import sys

PATH = "src/main/java/com/laosun/aluminium/models/TriggerTable.java"
text = io.open(PATH, encoding="utf-8").read()

if "String stateName" in text:
    print("skip: already carries the name")
    raise SystemExit(0)

HEADER_OLD = ("    public record TriggerContext(CanHit owner, CanHit actor, CanHit target, int hitCount, double amount,\n"
              "                                 Damage damage, Battle battle, SkillCategory fromCast, String ruleId,\n"
              "                                 List<Condition> targetFilter, int skillId, int weakHitCount,\n"
              "                                 List<CanHit> attackHitTargets) {\n")
HEADER_NEW = ("    public record TriggerContext(CanHit owner, CanHit actor, CanHit target, int hitCount, double amount,\n"
              "                                 Damage damage, Battle battle, SkillCategory fromCast, String ruleId,\n"
              "                                 List<Condition> targetFilter, int skillId, int weakHitCount,\n"
              "                                 List<CanHit> attackHitTargets, String stateName) {\n")
if text.count(HEADER_OLD) != 1:
    print("FAIL: record header anchor matched %d times" % text.count(HEADER_OLD))
    sys.exit(1)
text = text.replace(HEADER_OLD, HEADER_NEW)

CTOR_A = 'fromCast, "", List.of(), 0, 0, List.of());'
CTOR_B = 'battle, null, "", List.of(), 0, 0, List.of());'
if text.count(CTOR_A) != 1 or text.count(CTOR_B) != 1:
    print("FAIL: convenience anchors %d / %d" % (text.count(CTOR_A), text.count(CTOR_B)))
    sys.exit(1)
text = text.replace(CTOR_A, 'fromCast, "", List.of(), 0, 0, List.of(), null);')
text = text.replace(CTOR_B, 'battle, null, "", List.of(), 0, 0, List.of(), null);')

# every copy helper rebuilds the whole record; carry the name along.
def carry(match):
    return match.group(0)[:-1] + ", stateName)"

text, copies = re.subn(r"new TriggerContext\((?:[^()]|\([^()]*\))*\)", carry, text)
if copies < 5:
    print("FAIL: only %d copy helpers found" % copies)
    sys.exit(1)

ANCHOR = ("        public TriggerContext withRule(String id) {\n"
          "            return new TriggerContext(owner, actor, target, hitCount, amount, damage, battle, fromCast,\n"
          "                    id == null ? \"\" : id, targetFilter, skillId, weakHitCount, attackHitTargets, stateName);\n"
          "        }\n")
NEW_METHOD = ANCHOR + ("\n"
    "        /**\n"
    "         * The same context, saying WHICH named state just left its carrier (2026-10-02; the\n"
    "         * {@code STATE_ENDED} event). ⚠ The name has to ride here because the state is already gone by the time\n"
    "         * the event fires, so no condition can read it off the carrier.\n"
    "         */\n"
    "        public TriggerContext withStateName(String name) {\n"
    "            return new TriggerContext(owner, actor, target, hitCount, amount, damage, battle, fromCast, ruleId,\n"
    "                    targetFilter, skillId, weakHitCount, attackHitTargets, name);\n"
    "        }\n")
if text.count(ANCHOR) != 1:
    print("FAIL: withRule anchor matched %d times" % text.count(ANCHOR))
    sys.exit(1)
text = text.replace(ANCHOR, NEW_METHOD)

io.open(PATH, "w", encoding="utf-8", newline="").write(text)
print("ok   stateName component + %d copy helpers carry it + withStateName helper" % copies)
