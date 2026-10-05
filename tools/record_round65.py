"""Record round 65: no shipment, three named gaps, four lessons, and a 58-test regression I caused and repaired (2026-10-02)."""
import io

GAPS = "GAPS.md"
text = io.open(GAPS, encoding="utf-8").read()

OLD = "- ⛔「计入小伊卡忆灵技的治疗数值额外提高…等同本次治疗数值的 `#1%`」：需要「本次治疗量」这个载体。"
NEW = ("- ⛔「计入小伊卡忆灵技的治疗数值额外提高…等同本次治疗数值的 `#1%`」：**载体已经找到了，卡在别处**（2026-10-02 实测）：\n"
       "  - ✅ `HEALED` **带量**（`fireTriggersForAlly(TriggerEvent.HEALED, healer, target, healed)`），且该事件的 **actor 是治疗者** ⇒「风堇…提供治疗」可用 `actor has_state`；\n"
       "  - ✅ `GAIN_RESOURCE` 已支持 `amount_from_event` × `amount_percent`（字面量）；\n"
       "  - ⛔ **缺口一**：「累计治疗数值」属于**风堇的小伊卡**，而选择器只能点名**角色**（`ally_cid:`），点不了**别人的忆灵** ⇒ 需要「某角色的忆灵」这样的选择器；\n"
       "  - ⛔ **缺口二**：`characters/1409.json` 是**列表**，装不下 `resources` ⇒ 需要资源声明有别的落点；\n"
       "  - ⛔ **缺口三**：`amount_percent` 只能是字面量，而 `#1` 随等级变（0.36 → 1.008）⇒ 需要 `amount_percent_from_skill_param`（本轮做了又回滚：判据在"
       "`BATTLE_START` 期间由 `HEAL` 触发的 `HEALED` 上读数仍为 0，⭐ **下一问**：那次 `GAIN_RESOURCE` 到底有没有被走到）。")
if text.count(OLD) != 1:
    raise SystemExit("REFUSING: the healing bullet occurs %d times" % text.count(OLD))
io.open(GAPS, "w", encoding="utf-8", newline="\n").write(text.replace(OLD, NEW))
print("GAPS.md updated with the three named gaps")

BLOCK = """
> **2026-10-02 更新（第 65 轮：⚠ **本轮没有出货** ✗ —— ⭐ 而且我中途**弄红了 58 例**，已恢复 ✓；⭐ 三条缺口已具名 ✓）**：
>
> * ⭐ **量到的（⭐ 有价值，⭐ 已写进 GAPS ✓）**：⭐ `HEALED` ✗ **带量** ✓ 且 ⭐ actor 是**治疗者** ✓；
>   ⭐ `GAIN_RESOURCE` ✗ 已支持 ⭐ `amount_from_event` ✗ × ⭐ `amount_percent` ✗（⭐ 字面量 ✓）；⭐ `amount_percent` ✗ **没有**派生拼法 ✗。
> * ⭐⭐ **三条缺口（⭐ 本轮把一句诗拆成了三条 ✓）**：① ⭐ 选择器点不了**别人的忆灵** ✗；② ⭐ `1409.json` 是**列表** ✗ ⇒ ⭐ 装不下 `resources` ✗；
>   ③ ⭐ `amount_percent` ✗ ⭐ 需要**派生份额** ✗（⭐ 我做了引擎改动 ✗ ⭐ 但判据没过 ⇒ ⭐ **按纪律回滚** ✓）。
> * ⚠⚠ **我弄红的 58 例（⭐ 诚实记录 ✓）**：⭐ 回滚脚本里写了 ⭐ `doc.pop("resources", None)` ✗ ⇒ ⭐ **删掉了 `1415.json` 既有的资源声明** ✗（⭐ 追忆 ＋ 充能 ✓）
>   ⇒ ⭐ 加载器报 ⭐ `Character 1415 has a rule that uses the resource "充能", which the character does not declare` ✗ ⇒ **58 例红** ✗ ⇒ ⭐ 用 ⭐ `git checkout HEAD --` ✗ ⭐ 恢复三个内容文件 ✓ ⇒ ⭐ 回绿 ✓。
>   ⭐⭐ **教训：⭐ 改内容文件时不要 `pop` 掉自己不认识的结构 ✗ —— ⭐ 先看它有什么 ✓。**
> * ⚠ **另外三条教训（⭐ 都是本轮真金白银换的 ✓）**：
>   ① ⭐ `TriggerSpecs.set` ✗ ⭐ 要 **Java 字段名** ✗（⭐ 本轮第三次：⭐ `maxStacks` ✗ → ⭐ `amountFromEvent` ✗ → ⭐ `amountPercentFromSkillParam` ✗ ✓）；
>   ② ⭐ 判据的**文件名必须等于公开类名** ✗（⭐ 否则编译失败而 Gradle 一声不吭 ✓）；
>   ③ ⭐ 一次 shell 里既**写**一个文件又**删**同名文件 ✗ ⇒ ⭐ 会把自己刚写的删掉 ✓（⭐ 本轮真发生了 ✓）。
> * ⭐ **测试与变异**：⭐ 本轮**没有留下**测试（⭐ 引擎改动已回滚 ✓）。⭐ 这是**有意的**：⭐ 一条判据读不出预期的量时，⭐ 先弄清机制，⭐ 而不是把断言改到能过 ✓。
> * **实测（本轮）**：⭐ 回滚后 ⭐ 全量 **0**（--rerun-tasks ✓）、⭐ `mechanics` **rc 0** ✓、树干净 ✓ 已推送 ✓。
"""

with io.open("GAPS_LOG.md", "a", encoding="utf-8", newline="\n") as handle:
    handle.write(BLOCK)
print("GAPS_LOG.md appended")
