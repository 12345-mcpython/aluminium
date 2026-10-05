"""Record round 63: `ally_cid:` no longer throws when the named character is absent (2026-10-02)."""
import io
import sys

GAPS = "GAPS.md"
text = io.open(GAPS, encoding="utf-8").read()

OLD = ("- ⭐ **引擎侧仍有一处锋利的边（已登记）**：`ally_cid:<cid>` 在**点名的人不在场**时走的是 `require(...)` ⇒ **抛异常**，"
       "而引擎自己的惯例是**空列表、不是错误**（`resolveTargets` 里 `lowest_hp_ally` 旁边那句注释：「clause does nothing」）。"
       "⭐ **下一问**：把 `ally_cid:` 移进**复数**解析路径，让它在人不在场时静默不生效。")
NEW = ("- ✅ **已修（2026-10-02，第 63 轮）**：`ally_cid:<cid>` 现在在**复数**解析路径里解析 —— 点名的人不在场时返回**空列表**"
       "（与 `lowest_hp_ally` 同一形状：`return lowest == null ? List.of() : List.of(lowest);`），**不再抛异常**。"
       "⭐ 判据 `SkyOdeStackTest` 现在读**两侧**：她在场 ⇒ 2 层、旁人 0 层；她**不在场** ⇒ **不抛异常**、且不落到旁人身上。"
       "⭐ 变异（改回 `require(...)`）⇒ 第二条断言报 `Unexpected exception thrown: IllegalStateException` ⇒ 红。")
if text.count(OLD) != 1:
    sys.exit("REFUSING: the sharp-edge bullet occurs %d times" % text.count(OLD))
io.open(GAPS, "w", encoding="utf-8", newline="\n").write(text.replace(OLD, NEW))
print("GAPS.md updated")

BLOCK = """
> **2026-10-02 更新（第 63 轮：🎉 **第 125 件出货 ＝ `ally_cid:` 在点名者不在场时静默不生效** ✓ —— ⭐ 上一轮那处锋利的边磨平了）**：
>
> * ✅ **改法**：⭐ 把 `ally_cid:<cid>` ✗ ⭐ 从**单数**路径移到**复数**路径 ✓ —— ⭐ 因为两条路径的惯例**正好相反** ✓（**实测**）：
>   ⭐ 单数路径用 ⭐ `require(...)` ✗ ⭐ **抛异常** ✓（`random_ally_below_half_energy` 也是 ✓）；⭐ 而复数路径的惯例**明文写在** `lowest_hp_ally` 旁边 ✓：
>   ⭐ `return lowest == null ? List.of() : List.of(lowest);` ✗ ＋ ⭐ 注释 ⭐ 「clause does nothing」 ✓ ⇒ ⭐⭐ **"空列表，不是错误"** ✓。
> * ✅ **判据现在读两侧** ✓（⭐ `SkyOdeStackTest` 两条 ✓）：
>   ⭐ `[sky_stacks] the named character has 2 ; the other ally has 0` ✓
>   ⭐ `[sky_stacks] with the named character absent: no throw, and the bystander has 0` ✓✓
>   ⇒ ⭐ 第二条**正是**上一轮让 22 例红的那条路 ✓（⭐ 现在它是一条**断言** ✓，⭐ 不再只是"⭐ 碰巧没炸 ✗" ✓）。
> * ✅ **实测变异**：⭐ 把 ⭐ `List.of()` ✗ 改回 ⭐ `List.of(require(...))` ✗ ⇒ ⭐ 第二条报 ⭐ `Unexpected exception thrown: IllegalStateException` ⇒ 红 ✓。
> * **实测（本轮）**：⭐ 全量 **0**（--rerun-tasks，**2304** 例 ✓）、⭐ `mechanics` **rc 0** ✓、树干净 ✓ 已推送 ✓。
"""

with io.open("GAPS_LOG.md", "a", encoding="utf-8", newline="\n") as handle:
    handle.write(BLOCK)
print("GAPS_LOG.md appended")
