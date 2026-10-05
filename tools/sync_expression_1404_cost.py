"""Checklist sync for the cost half and the swap blocker (round 5 of the goal).

Shipped this round: 1404's turn-start rule gates on 【血仇】 and pays the skill's cost, through a new CURRENT-HP share. That is a
§2 row.
Still blocked, now precisely: `REPLACE_SKILL` installs the row into the map, but a `CAST_SKILL` later in the SAME rule runs the
OLD row -- measured in one scene: the content's cast deals 767.585, a direct cast of slot 9 deals 383.793. That is what keeps
「自动施放【弑王成王】/【弑神登神】」 from being expressible, and it has three readers.
"""
import io
import sys

PATH = "EXPRESSION.md"
lines = io.open(PATH, encoding="utf-8").read().split("\n")

# ---- 1. §3: the row records the real blocker
PREFIX = "| **「充能达到 150 点时"
hits = [index for index, line in enumerate(lines) if line.startswith(PREFIX)]
if len(hits) != 1:
    sys.exit("REFUSING: %d §3 rows start with that prefix" % len(hits))
lines[hits[0]] = (
    "| **「自动施放【强化战技】」**（万敌的【弑王成王】槽 9 ✓ 与【弑神登神】槽 11 ✓；"
    "以及充能 150 那句） "
    "| ⭐ 现在只剩**一件** ✓：**让 `REPLACE_SKILL` 在同一条规则里对随后的 `CAST_SKILL` 生效** ✗ "
    "—— 实测：换装**确实装进了 map** ✓（`getSkills().get(SKILL).getSkillSlot()` 读到 **9** ✓、category `BPSKILL` ✓），"
    "但同一场景里内容那条造成 **767.585** ✓ 而**直接**放槽 9 只有 **383.793** ✓"
    "（他攻击力 426.888 ✓）⇒ 同规则内的放仍用**旧行** ✗；"
    "⚠ 另一件也已实测：「充能 ≥ 100」那条**没有【血仇】门** ✗ ⇒ 它会**反复**触发并把充能抽干 ✗，充能攒不到 150 ✗ "
    "| `1404`（两句）、**`1415` 的忆灵技能 8** ✓ **共 3 位** ✓ "
    "| `CAST_SKILL` 在解析技能时**重新读槽**，**或** 让换装在规则开始前完成 ✓ "
    "|")
print("ok   §3 names the one real blocker")

# ---- 2. §2: the half that ships
ROW = ("| **「消耗等同于…**当前**生命值 X% 的生命值」** "
       "| `CONSUME_HP` ＋ **`scale: \"target_current_hp\"`**（新增：**当前**生命值的份额 ✓；"
       "旧词汇只有 `owner_max_hp`／`target_max_hp`／`target_lost_hp` ✗） "
       "| `src/main/java/com/laosun/aluminium/models/TriggerInterpreter.java`、`src/main/resources/characters/1404.json` "
       "| `MydeiBloodfeudSkillsTest` |")
ANCHOR = "| **「某个状态的持续回合数在**"
target = [index for index, line in enumerate(lines) if line.startswith(ANCHOR)]
if len(target) != 1:
    sys.exit("REFUSING: %d §2 anchors" % len(target))
lines.insert(target[0] + 1, ROW)
io.open(PATH, "w", encoding="utf-8", newline="\n").write("\n".join(lines))
print("ok   §2 gains the current-HP share")
