"""Item 1 is done the way the goal allows: written where it could be, registered where it could not (2026-10-02).

Five branches, measured one by one:
  1. 血仇 ⇒ 不消耗充能的【弑神登神】        SHIPPED (commit 2651219a; judge OdeToStrifeBloodfeudTest)
  2. 不在血仇 ⇒ 行动提前 100%              SHIPPED (commit 325bbd78; judge OdeToStrifeAdvanceTest)
  3. 本次攻击中暴击伤害 +200%              SHIPPED (commit c2717ec6; judge OdeToStrifeCritTest)
  4. 解除控制类负面状态                    REGISTERED: DISPEL carries only an amount, no class filter
  5. 施放前目标被消灭 ⇒ 对新入场敌方目标    REGISTERED: target_else_random_enemy picks ANY enemy, not a newly-arrived one

So section 3's row for 1415 stops saying the sentence is waiting to be written, and says what is measured: three fifths shipped, two
fifths registered with the exact missing piece.
"""
import io
import sys

PATH = "EXPRESSION.md"
lines = io.open(PATH, encoding="utf-8").read().split("\n")
PREFIX = "| **\u300c\u4f7f\u4e07\u654c\u81ea\u52a8\u65bd\u653e1\u6b21\u4e0d\u6d88\u8017\u5145\u80fd\u7684\u3010\u5f11\u795e\u767b\u795e\u3011\u300d**"
hits = [i for i, l in enumerate(lines) if l.startswith(PREFIX)]
if len(hits) != 1:
    sys.exit("REFUSING: %d rows for 1415" % len(hits))
lines[hits[0]] = (
    "| **`1415` \u5fc6\u7075\u6280\u80fd 8 \u300c\u732e\u4e88\u300c\u7eb7\u4e89\u300d\u4e4b\u8bd7\u300d\u7684\u4e94\u53e5**\uff08`1415` \u2713\uff09 "
    "| \u2705 **\u4e09\u53e5\u5df2\u51fa\u8d27** \u2713\uff1a\u2460 \u8840\u4ec7\u21d2\u4f7f\u5176\u81ea\u52a8\u65bd\u653e\u4e0d\u6d88\u8017\u5145\u80fd\u7684\u3010\u5f11\u795e\u767b\u795e\u3011 \u2713\uff1b"
    "\u2461 **\u4e0d**\u5904\u4e8e\u8840\u4ec7\u21d2\u884c\u52a8\u63d0\u524d 100% \u2713\uff08\u5426\u5b9a\u7528\u73b0\u6210\u7684 `!` \u2713\uff09\uff1b"
    "\u2462 \u672c\u6b21\u653b\u51fb\u4e2d\u66b4\u51fb\u4f24\u5bb3 +200% \u2713\uff08`MODIFY_ATTR{CRIT_ATTACK, percent: 2.0, until: \"cast_end\"}` \u2713\uff09\u3002"
    "\u2757 **\u4e24\u53e5\u5df2\u767b\u8bb0** \u2713\uff1a\u2463 \u89e3\u9664**\u63a7\u5236\u7c7b**\u8d1f\u9762\u72b6\u6001 \u2717\uff08`DISPEL` \u53ea\u6709 `amount` \u2717\uff0c"
    "\u65e0\u7c7b\u522b\u8fc7\u6ee4 \u2717\uff0c\u800c `target_when` \u662f**\u9009\u5355\u4f4d**\u7684 \u2717\u3001\u4e0d\u7b49\u4ef7 \u2717\uff09\uff1b"
    "\u2464 \u82e5\u65bd\u653e\u524d\u76ee\u6807\u88ab\u6d88\u706d\u5219\u5bf9**\u65b0\u5165\u573a**\u654c\u65b9\u76ee\u6807\u65bd\u653e \u2717"
    "\uff08`target_else_random_enemy` \u9009\u7684\u662f**\u4efb\u610f**\u654c\u4eba \u2717\uff0c\u4e0d\u662f\u65b0\u5165\u573a\u7684 \u2717\uff09\u3002"
    "| `1415`\uff081 \u4f4d\uff09 "
    "| \u2463 \u7ed9 `DISPEL` \u4e00\u4e2a**\u7c7b\u522b\u8fc7\u6ee4**\uff1b\u2464 \u4e00\u4e2a\u201c**\u65b0\u5165\u573a\u7684\u654c\u65b9\u76ee\u6807**\u201d\u9009\u62e9\u5668 "
    "|")
io.open(PATH, "w", encoding="utf-8", newline="\n").write("\n".join(lines))
print("ok   section 3 says what is measured for 1415")
