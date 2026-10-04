"""1407's gap is CONFIRMED, and now has a mechanism (round 28 of the goal).

For once the register was right, and this round supplies the reason -- which is worth more than the verdict:

  * `LETHAL_DAMAGE` is announced PER INSTANCE: `fireTriggersForAlly(TriggerEvent.LETHAL_DAMAGE, target, target, 0)` (Battle:1425), i.e.
    one victim at a time, with actor == target == that victim.
  * the clause is 「该效果**单场战斗中最多触发 1 次**」, so it may fire ONCE for the whole battle.
  * multiplied together: only the FIRST victim of a lethal action would ever get 【月茧】, and its siblings would die. So the needed
    vocabulary is "every ally that received a lethal blow in this ACTION", not a different cap -- which is exactly the selector the row
    already names.

Also measured, and recorded because it bounds what exists: the engine's widest "the units that were hit" carrier is per-ATTACK. The
`random_hit_enemy` selector reads either the ATTACK_FINISHED frozen set or the damage instance's own snapshot (TriggerInterpreter:1507-1512),
and nothing in main ever passes a non-empty `attackHitTargets` -- that carrier's readers are in tests.
"""
import io
import sys

PATH = "EXPRESSION.md"
lines = io.open(PATH, encoding="utf-8").read().split("\n")
PREFIX = "| \u4ed3\u5e93\u6280\u300c**\u83b7\u5f97\u8be5\u89d2\u8272\u5373\u751f\u6548\uff0c\u65e0\u9700\u4e0a\u573a**\u300d"
hits = [index for index, line in enumerate(lines) if line.startswith(PREFIX)]
if len(hits) != 1:
    sys.exit("REFUSING: %d rows" % len(hits))
lines[hits[0]] = (
    "| \u4ed3\u5e93\u6280\u300c**\u83b7\u5f97\u8be5\u89d2\u8272\u5373\u751f\u6548\uff0c\u65e0\u9700\u4e0a\u573a**\u300d "
    "| \u2705 `1506` \u5168\u90e8\u51fa\u8d27 \u2713\u3002\u2757 `1407` \u6708\u8307\u4e4b\u5e87\uff1a\u2b50 **\u7f3a\u53e3\u672c\u8f6e\u88ab\u786e\u8ba4** \u2713\uff08\u800c\u4e14\u673a\u5236\u5df2\u627e\u5230 \u2713\uff09\u2014\u2014 "
    "\u2b50 `LETHAL_DAMAGE` **\u6309\u5b9e\u4f8b**\u516c\u544a \u2713\uff08`fireTriggersForAlly(LETHAL_DAMAGE, target, target, 0)` \u2713\uff0c\u4e00\u6b21\u4e00\u4e2a\u53d7\u5bb3\u8005 \u2713\uff09\uff0c"
    "\u800c\u8be5\u53e5\u662f\u300c\u8be5\u6548\u679c**\u5355\u573a\u6218\u6597\u4e2d\u6700\u591a\u89e6\u53d1 1 \u6b21**\u300d\u2713 \u21d2 \u76f8\u4e58\u4e4b\u540e **\u53ea\u6709\u7b2c\u4e00\u4e2a\u53d7\u5bb3\u8005\u62ff\u5230\u3010\u6708\u8307\u3011** \u2717\uff0c\u540c\u884c\u52a8\u7684\u5176\u4f59\u53d7\u5bb3\u8005\u4f1a\u6b7b \u2717\u3002"
    "\u26a0 \u5df2\u6709\u7684\u201c\u547d\u4e2d\u96c6\u5408\u201d\u6700\u5bbd\u5230**\u4e00\u6b21\u653b\u51fb** \u2713\uff08`random_hit_enemy` \u8bfb `ATTACK_FINISHED` \u7684\u51bb\u7ed3\u96c6 \u2713 \u6216\u4f24\u5bb3\u5b9e\u4f8b\u5feb\u7167 \u2713\uff1b"
    "\u4e14\u4e3b\u5e72\u4ee3\u7801**\u6ca1\u4eba\u4f20**\u975e\u7a7a `attackHitTargets` \u2717\uff09\u3002"
    "\u26a0 \u53e6\u5916\u4e24\u534a\u90fd\u5199\u5f97\u51fa \u2713\uff1a\u300c\u5ef6\u540e\u5012\u4e0b\u300d\u2713\uff08`defers_death` \u2713\uff09\u4e0e\u7ed3\u7b97 \u2713\uff08`HEALED`\uff0f`SHIELD_GRANTED` \u21d2 `REMOVE_STATE` \u2713\uff09 "
    "| `1407`\uff08\u6708\u8307\u4e4b\u5e87 \u2713\uff09 **1 \u4f4d**\uff08`1506` \u5df2\u51fa\u8d27 \u2713\uff09 "
    "| \u4e00\u4e2a\u201c**\u4e00\u6b21\u884c\u52a8**\u4e2d\u53d7\u5230\u81f4\u547d\u653b\u51fb\u7684\u5168\u4f53\u201d\u9009\u62e9\u5668 \u2713\uff08\u2757 \u800c\u975e\u6539\u9650\u989d \u2717\uff09 |")
io.open(PATH, "w", encoding="utf-8", newline="\n").write("\n".join(lines))
print("ok   1407's row now records WHY, not just that")
