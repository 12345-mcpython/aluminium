"""Record round 64: the sky ode's spend sentence, and the sixth table-replacement trap (2026-10-02)."""
import io
import sys

GAPS = "GAPS.md"
text = io.open(GAPS, encoding="utf-8").read()

OLD = "- ⛔「风堇施放战技/终结技后，消耗 1 层」：依赖上面那个状态。"
NEW = ("- ✅ 已出货（2026-10-02）：「风堇施放**战技/终结技**后，消耗 1 层」 —— ⭐ **两条规则**（`from_skill_id` 是**单值**比较，实测读者 `1111.json`：`\"from_skill_id == 8\"`）："
       "战技挂 `on: SKILL_CAST` ＋ `from_skill_id == 2`、终结技挂 **`on: ULT_CAST`** ＋ `from_skill_id == 3`（⭐ 实测：`SkillExecutor` 里 `case ULTRA` 发的是 **`ULT_CAST`**，"
       "只有 `case BPSKILL` 才发 `SKILL_CAST`）⇒ `REMOVE_STACK{buff, amount: 1, target: \"self\"}`。"
       "⭐ 判据 `SkyOdeSpendTest` 三档：普攻 **3** 层不消耗、战技 **2**、终结技 **1**（引擎报的槽位号实测为 `common=1 skill=2 ultra=3`）。")
if text.count(OLD) != 1:
    sys.exit("REFUSING: the spend bullet occurs %d times" % text.count(OLD))
text = text.replace(OLD, NEW)

# and record the trap where the discipline list lives
TRAP = "1. **判据里的「换表」会静默删掉被测的东西**"
if text.count(TRAP) != 1:
    sys.exit("REFUSING: the first discipline occurs %d times" % text.count(TRAP))
text = text.replace(TRAP, "1. **判据里的「换表」会静默删掉被测的东西**（⚠ **第 63/64 轮又犯了一次**：给风堇上层的规则写成了 `hyacine.setTriggerTable(...)`，"
                          "把**被测的两条规则**一起删了，读数全是 3 ⇒ 改用**另一个队友**的表 ＋ `ally_cid:1409` 才读对 ✓）")

io.open(GAPS, "w", encoding="utf-8", newline="\n").write(text)
print("GAPS.md updated")

BLOCK = """
> **2026-10-02 更新（第 64 轮：🎉 **第 126 件出货 ＝ `1141519` 第三句「施放战技/终结技后消耗 1 层」整句成句** ✓ —— ⚠ 而同一轮我**第六次**踩了换表陷阱）**：
>
> * ✅ **原话**：⭐ `风堇施放战技/终结技后，消耗1层【献予「天空」之诗】` ✗（⭐ 该句在 TextMap 里**独立成条** ✓：⭐ hash `5943790019529126162` ✓）。
> * ✅ **两条规则** ✓（⭐ `from_skill_id` 是**单值** ✓，**实测**读者 `1111.json` 写 `"from_skill_id == 8"` ✓）：
>   ⭐ 战技 ⭐ `on: SKILL_CAST` ✗ ＋ ⭐ `from_skill_id == 2` ✓；⭐ 终结技 ⭐ **`on: ULT_CAST`** ✗ ＋ ⭐ `from_skill_id == 3` ✓。
> * ⭐⭐ **量到的事件事实（⭐ 值得记 ✓）**：⭐ `SkillExecutor` 里 ⭐ `case ULTRA` ✗ ⭐ 发的是 **`ULT_CAST`** ✗，⭐ `case BPSKILL` ✗ 才发 `SKILL_CAST` ✗，⭐ `case NORMAL` ✗ 发 `BASIC_ATTACK` ✗ ✓
>   ⇒ ⭐ 而我第一版把终结技也挂在 `SKILL_CAST` ✗ 上 ⇒ ⭐ **永远不会触发** ✗ ✓。⭐ 引擎报的槽位号实测为 ⭐ `common=1 skill=2 ultra=3` ✗ ✓。
> * ⚠⚠ **我第六次踩了换表陷阱** ✗✗：⭐ 判据里给风堇上 3 层的规则写成了 ⭐ `hyacine.setTriggerTable(...)` ✗ ⇒ ⭐ **把她那张表整个换掉** ✗ ⇒
>   ⭐ **被测的两条规则一起被删** ✗ ⇒ ⭐ 读数全是 3 ✗ ✓。⭐ 改法：⭐ 让**另一个队友**（⭐ `1002` ✓）的表来上 ✗ ⭐ —— ⭐ 而且正好用**新出货的** ⭐ `ally_cid:1409` ✗ ✓ ✓。
>   ⭐⭐ **教训（⭐ 已写进 GAPS 纪律第 1 条 ✓）**：⭐ 这个陷阱我已经**记录过**却**又犯** ✗ ⇒ ⭐ **判据里要给自己上状态时，⭐ 永远改别的单位的表 ✓，⭐ 绝不 `setTriggerTable` 被测角色 ✓。**
> * ✅ **判据 ＋ 实测变异**：⭐ `SkyOdeSpendTest` ⇒ ⭐ `layers after basic = 3 ; after skill = 2 ; after ultimate = 1` ✓✓（⭐ 三档 ✓）；
>   ⭐ **变异**（⭐ 终结技改回 `SKILL_CAST` ✗）⇒ 第三档红 ✓。
> * **实测（本轮）**：⭐ 全量 **0**（--rerun-tasks ✓）、⭐ `mechanics` **rc 0** ✓、树干净 ✓ 已推送 ✓。
"""

with io.open("GAPS_LOG.md", "a", encoding="utf-8", newline="\n") as handle:
    handle.write(BLOCK)
print("GAPS_LOG.md appended")
