"""Record round 68: the three unread skills, clause by clause, and the one clause that is closest (2026-10-02)."""
import io
import sys

GAPS = "GAPS.md"
text = io.open(GAPS, encoding="utf-8").read()

ANCHOR = "### `11415` 其余未动"
NEW = """### `11415` 其余三首的**逐句地图**（2026-10-02 全部读过原话）

- **`1141517` 献予「生死」之诗**（参数 `[0, 0.0012, 2, 0, 0.0024, 2]`，随级变）：
  「整场生效，对**遐蝶**施放时，【新蕊】可以溢出至 `#3%`。召唤死龙时会消耗所有溢出【新蕊】，每消耗 1% 溢出值，使本次召唤的死龙触发天赋【灼掠幽墟的晦翼】的技能效果时，造成的伤害倍率提高 `#2%`，召唤时若场上敌方目标小于等于 `#6` 名，伤害倍率额外提高 `#5%`。」
  ⇒ ⛔ **四句全受阻**：【新蕊】与「死龙」**都没建模**，「溢出」这个概念也没有载体。
- **`1141521` 献予「负世」之诗**（参数 `[0.08, 0.15, 5, 0.05, 12, 0.06, 0.36, 6, 4]`）：
  「整场生效，对**白厄**施放后，使白厄获得 `#8` 点【火种】，且在变身时获得【永续的燃烧】…持有【永续的燃烧】时，卡厄斯兰那的暴击率提高 `#1%`…施放攻击后，造成 `#3` 次**附加伤害**，每次伤害对敌方随机单体造成等同于卡厄斯兰那 `#4%` 攻击力的火属性附加伤害。」
  ⇒ ⛔ **全受阻**：【火种】／【永续的燃烧】／【毁伤】都没建模，「变身」与「额外回合」也没有载体。
- **`1141524` 献予「岁月」之诗**（参数 `[0.09, 1, 0.06]`，⭐ 其中 `#2 = 1` **恒定**）：
  「整场生效，对**长夜月**（cid **1413**）施放后，**「长夜」施放忆灵技【迷梦，流失，如露】时造成的伤害提高 `#1%`**，长夜月施放战技/终结技后，额外获得 `#2` 点【忆质】。长夜月战技的暴击伤害提高效果额外提高，提高数值等同于长夜月暴击伤害的 `#3%`。」
  - ⛔ 第二句：「额外获得 1 点【忆质】」 —— ⭐⭐ **【忆质】完全没有建模** ✓，⭐ 这不是我猜的：**我们自己的 `characters/1413.json` 的注释就写着**
    *"…everything else her talent says (the CRIT DMG on HP loss, 【忆质】) is **not authored yet**"* ✓（⭐ 数据侧 ⭐ `skill_traces.json` 与 ⭐ `eidolons.json` 都引用它 ✓）。
    ⭐ 要写它得先**造一个资源**（上限／初值／她天赋里「施放技能时获得 1 点」的规则），⭐ 那是**跨角色的作者工程**，不是一个从句 ✓。
  - ⛔ 第三句：「长夜月**战技的**暴击伤害提高效果额外提高」 —— 是**修饰之修饰**（⭐ 改的是她战技给的那个增益 ✗），⭐ 而那个增益不是命名状态 ✓。
  - ⭐⭐ **第一句是三首里最近的一句** ✓：「『长夜』施放忆灵技【迷梦，流失，如露】时造成的伤害提高 `#1%`」——
    ⭐ 形状现成：`MODIFY_ATTR{ALL_DAMAGE_TYPE_BOOST, percent_from_cast_param: 0}` 挂在 `1413.json` 上、`target: "summon"` 就是**她的**忆灵 ✓；
    ⛔ **但必须只对那一个忆灵技生效**，而规则若在 `CAST_SETUP` 时上增益就会**过宽**（⭐ 会加成它所有伤害 ✗）⇒
    ⭐ **下一问**：⭐ 那个忆灵技的**槽位号**是多少（⭐ 一次即可 ✓）＋ ⭐ 该增益该挂在伤害事件上（`DEALING_DAMAGE` ＋ `actor is_summon` ＋ `from_skill_id == <槽位>`，⭐ 形状照 `1403.json` 的 `zone_true_damage_rider` ✓）。
"""

if text.count(ANCHOR) != 1:
    sys.exit("REFUSING: the section heading occurs %d times" % text.count(ANCHOR))
io.open(GAPS, "w", encoding="utf-8", newline="\n").write(text.replace(ANCHOR, NEW))
print("GAPS.md updated with the clause-by-clause map")

BLOCK = """
> **2026-10-02 更新（第 68 轮：⚠ **本轮没有出货** ✗ —— ⭐ 但把**三个尚未读过的技能**逐句读完并画成了地图 ✓，⭐ 其中一句找到了确切落法 ✓）**：
>
> * ⭐ **读全了三首原话** ✓（⭐ slot 17「生死」／⭐ 21「负世」／⭐ 24「岁月」✓）＋ ⭐ 各自参数表 ✓。
> * ⭐⭐ **结论：三首的每一句都撞在"未建模的资源/状态"上** ✗：
>   ⭐ 17 ⭐ 要【新蕊】＋⭐ 「死龙」＋⭐ 「溢出」；⭐ 21 ⭐ 要【火种】／⭐【永续的燃烧】／⭐【毁伤】／⭐ 「变身」／⭐ 「额外回合」；⭐ 24 ⭐ 要【忆质】／⭐ 「她战技的暴击伤害增益」 ✓。
> * ⭐⭐⭐ **而【忆质】没建模这件事，我们自己的文件就写着** ✓：⭐ `characters/1413.json` 的注释
>   *"…everything else her talent says (the CRIT DMG on HP loss, 【忆质】) is not authored yet"* ✓（⭐ 数据侧 `skill_traces.json`／`eidolons.json` 都引用它 ✓）
>   ⇒ ⭐ 写那句话要先**造一个资源**（⭐ 上限／⭐ 初值／⭐ 她天赋的获取规则 ✓）⇒ ⭐ **跨角色的作者工程**，⭐ 不是一个从句 ✓。
> * ⭐⭐ **最近的一句（⭐ 已具名 ✓）**：⭐ 24 的第一句 ⭐ 「『长夜』施放忆灵技【迷梦，流失，如露】时造成的伤害提高 `#1%`」✗ ——
>   ⭐ 形状现成（`percent_from_cast_param: 0` ＋ ⭐ `target: "summon"` ✗），⛔ **但要只对那一个忆灵技生效** ✗ ⇒
>   ⭐ **下一问**：⭐ 那个忆灵技的**槽位号** ＋ ⭐ 把增益改挂在 `DEALING_DAMAGE` ✗（⭐ 形状照 `1403.json` 的 `zone_true_damage_rider` ✓）。
> * ⭐ **本轮量到的结构事实**：⭐ `skills.json` 的一行有 `skill_introduction` ✗（⭐ 一个 `{chinese, english}` 字典 ✓）＋ ⭐ `skill_effect` ✗；
>   ⭐ 长夜月 ＝ **cid 1413** ✓ 而 ⭐ `characters/1413.json` 是**列表** ✗（⭐ 装不下 `resources` ✓）。
> * **实测（本轮）**：⭐ 全量 **0**（--rerun-tasks ✓）、⭐ `mechanics` **rc 0** ✓、树干净 ✓ 已推送 ✓。
"""
with io.open("GAPS_LOG.md", "a", encoding="utf-8", newline="\n") as handle:
    handle.write(BLOCK)
print("GAPS_LOG.md appended")
