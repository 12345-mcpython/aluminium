# 交接文档 · 「用真实捕获复刻铝引擎的战斗流程」

> **⚠ 使用说明（务必先读这一段）**
>
> 这份文件是**一次性**的交接物。接手的人（或 agent）：
>
> 1. 读完之后，把下面「关键口径」与「未完成事项」里**你还不确定**的部分，与 `AGENT.md` 的 §3.7b–§3.7j 对照确认；
> 2. 然后**删除本文件**并提交：`git rm HANDOVER.md && git commit -m "docs: absorb the handover"`；
> 3. 结论已经在 `AGENT.md` 里（§3.7j 是定稿版），所以删掉本文件不会丢信息。**不要**把本文件长期留在仓库里。

---

## 一、这条线要解决什么

把一场真实战斗（捕获 JSON）的**伤害流程**在铝引擎里复刻出来：

- 先做到「整场伤害事件按顺序重放 + 逐条对齐」；
- 再补四位角色（1409 风堇 / 1413 长夜月 / 1415 昔涟 / 1512 知更鸟•晴歌）与忆灵的内容；
- 最终让**减防 / 易伤 / 增伤**这些状态**由规则产生**，而不是由捕获直接喂进算法。

数据来源：`E:\turnbasedgamedata`（`ExcelOutput/` + `TextMap/TextMapCHS.json`）与捕获 JSON。

## 二、当前状态（截至本文件写下时）

| 目标要求 | 状态 |
|---|---|
| 整场伤害事件逐条对齐 | **已完成**：夹具 `src/test/resources/captures/veritas-085418.json`（82 事件）→ `compare-capture` **81/81 逐位复现**（1 条击破按原因跳过） |
| 出手流程还原 | **已完成**：`capture-flow` 把 82 事件归并成 **21 次施放**，覆盖 82/82 |
| 四位角色 + 忆灵内容 | **1512 完整 · 1409 完整 · 1415 主体 · 1413 主体**；忆灵面板与光环都已落地 |
| 减防/易伤/增伤由规则产生 | **已完成**：1512 Fever（减防 + 增伤）、光锥 23042（易伤）、忆灵光环（易伤）、1413【至暗之谜】、1409 天赋、1415 结界真伤，每条都有行为判据 |
| 面板取自遗器（自算，而非读捕获） | **未做**：等用户提供遗器主词条 + 副词条 |

闸门（每次都跑）：`gradlew test --rerun-tasks`（811 套件 / 2782 用例 / 0 失败）、`gradlew javadoc`（0 条 `error`）。

## 三、三条最硬的证据（先看这三条，能快速判断引擎是否还健康）

1. **减防**：Fever = `15% + 0.5% × 气氛值`，cap 40%。判据在 `FeverDefenceIgnoreTest`（气量 0/15/50 ⇒ 0/0.15/0.225/0.40）。
   捕获里那串"越打越痛"的因子（22.5/23/24/27.5/29/29.5/31.5/40%）正是它。
2. **易伤**：光锥 23042 叠影 5 的 `0.36` + 忆灵光环（2 名，`0.112+0.168=0.28`）= **0.64**，与捕获恒定的
   `all_damage_type_taken_ratio` 逐位吻合。判据在 `MemospriteVulnerabilityTest`（1.112 与 1.3237）。
3. **真伤**：1415 结界的 `0.24` ⇒ 捕获里每段配对真伤与主伤害的比值 `371.472019 / 1547.800079 = 24.0000%`。
   判据在 `CyreneZoneRiderTest`。

## 四、未完成事项与下一步（按优先级）

1. **两段式技能**（`AGENT.md §3.7i`）：1415【向着爱与明天♪】一行里两段伤害，
   参数顺序是 **#1 = 全体那一段、#3 = 单体那一段**（与文档叙述顺序**相反**，容易读反）；引擎目前只落一段。
   两条可选修法：技能数据支持"一行多段（各段自己的目标形状）"，或用内容写两条 `DAMAGE` 规则分别取
   `skill_param:1` / `skill_param:3`。验证材料是捕获里她那几次施放的 `hits/targets`。
2. **忆灵多段**（`§3.7h`）：1413「长夜」的技能 1，表里 **6 段**、捕获 **5 段**，`skill_effects.json` 里没有 servant 分段条目 ⇒
   引擎按单段结算。**总伤害不受影响**，但事件条数对不上、且"每段触发一次"的从句会少算。
   缺两项数据：**分裂比**与**实际段数** —— 不要编。
3. **面板自算**：等用户的遗器数据（主词条 + 副词条）。现在面板取自捕获。
4. **`Battle` 驱动的整场重放**：目前是"逐条复算（`compare-capture`）+ 流程还原（`capture-flow`）"，
   还没有"把 21 次施放喂给 `Battle` 自动打一遍、比总伤害"。
5. **1413 天赋的忆灵那一半**：给 `Summon` 的属性修饰走**份额**路径
   （`amount` 与 `percent` 同效，实测都是 `0.6 × 0.12 = 0.072`，而不是 +0.6 点），需要先定口径再写。

## 五、关键口径（这些都是踩过的坑，别再踩第二次）

### 5.1 `percent` vs `amount`

| 属性 | 给**角色** | 给**忆灵（Summon）** |
|---|---|---|
| `CRIT_ATTACK`（点数） | `amount: 0.6` ⇒ +0.6 点 ✓；`percent: 0.6` ⇒ 0.6 × base ✗ | 两种写法都一样，得到 `0.6 × 0.12 = 0.072` ✗（**口径待定**） |
| `CRIT_CHANCE`（base = 1.0） | `percent: 0.5` 与 `amount: 0.5` 同值 ✓ | 必须 `amount`（base = 0，`percent` 给 0 ✗） |
| `DEFENCE_IGNORE` / `ALL_DAMAGE_TYPE_BOOST`（份额） | `percent` ✓ | `percent` ✓ |
| `HEALTH` 的固定点数（如 +600） | `amount: 600`（不带 scale）✓ | — |

### 5.2 事件与代价

- 引擎区分「**受伤**」(`HP_LOST`) 与「**消耗生命作为代价**」(`consumeHp` ⇒ 发 `HP_CONSUMED`，**不发** `HP_LOST`)。
- 技能数据里的"消耗 #6% 生命值"**没有被引擎执行**（`§3.7g`）—— 要写就用 `CONSUME_HP` 规则（`scale: owner_max_hp`）。
- `HEALED` 只在**真的回了血**时才发；**满血治疗不发事件**。
- 判据里要给队友「扣血」，用 **`consumeHp(x)`**；`heal(-x)` 会被 `heal()` 的 `amount <= 0` 直接忽略（假扣血）。
- 技能数据自带治疗的条件是 `skill_effect: Restore`；`Support`（如 1409 的终结技）**不带**治疗，得用规则写。

### 5.3 目标与门控

- `target: "summon"` **必须**用 `self_summon_count >= 1` 门控，否则引擎直接报错（它给的是可执行的修法）。
- 同一事件的规则**按文件顺序执行** ⇒ 依赖"先召唤后给忆灵"的场景，把 `SUMMON` 那条排在前面。
- 多来源的 `MODIFY_DAMAGE_TAKEN` 要**加性共存** ⇒ 写 `coexist: true`（引擎此前忽略它，已修）。

### 5.4 资源与数据

- 资源名按文件声明写：1413 文档写【忆质】、本仓库声明的是 **`亿质`**；用错会在**装配期**抛 `CharacterException`。
- 队伍级资源在 `Battle.partyResources`，用 `battle.partyResource(id).gain(n)`；`CanHit.getResources()` 是**自身**池。
- `skill_max_hits.json` 的 `servants` 段给忆灵的段数上限；`skill_effects.json` 目前只按角色 cid 分段。

### 5.5 测量纪律

- **一次只动一个变量**。易伤那几次失败全是"同时动了韧性/光环/光锥"或"目标满血"造成的。
- 比"召唤前后"的伤害时，**两侧先击破**（`((Enemy) …).breakEnemy(DamageElement.ICE)`），把韧性因子固定为 1.0。
- 任何"新规则"，先写判据；判据不过就**回滚**，把量到的事实写进 note，不近似。

## 六、常用命令

```powershell
cd E:\code\java\aluminium
.\gradlew.bat test --rerun-tasks            # 全量（811 套件 / 2782 用例）
.\gradlew.bat javadoc --rerun-tasks         # 必须 0 条 error
.\gradlew.bat run --args="compare-capture src/test/resources/captures/veritas-085418.json"
.\gradlew.bat run --args="capture-flow <capture.json>"
```

新增捕获时：先把事件裁成"只留读取字段"的夹具（参考 `veritas-085418.json`），再进 `CaptureComparatorTest`。

## 七、文件地图（这条线相关）

| 文件 | 作用 |
|---|---|
| `src/main/java/com/laosun/compare/CaptureComparator.java` | `compare-capture`：逐事件复算并打印 `reproduced: n/m` |
| `src/main/java/com/laosun/compare/CaptureFlow.java` | `capture-flow`：事件 → 施放，打印 `covered: n/m` |
| `src/main/java/com/laosun/compare/DamageComparator.java` / `DamageScenario.java` | 单次命中的比较器与场景 |
| `src/main/resources/game/characters/1512.json` | Fever 减防 / 增伤 / 忆灵光环 |
| `src/main/resources/game/characters/1409.json` | 终结技（召唤+治疗+【雨过天晴】）/ 天赋 / 忆灵 |
| `src/main/resources/game/characters/1413.json` | 【至暗之谜】/ 战技消耗 / 天赋 |
| `src/main/resources/game/characters/1415.json` | 全员增伤 / 结界 24% 真伤 / 终结技暴击 / 德谬歌 |
| `src/main/resources/game/light_cones/23042.json` | 忆灵放技能 ⇒ 敌方易伤 0.18…0.36 |
| `AGENT.md` §3.7b–§3.7j | 决策台账：症状 / 根因 / 修法 / 判据，含判断错的纠正过程 |

## 八、不要碰

- `HSR.md`：**不读、不写、不提交**（用户明确要求）。
- 提交时**只 add 明确路径**，不要 `git add -A`（避免把探针、临时文件带进去 —— 这已经发生过一次，教训在 §3.7g 附近）。
- 探针（临时规则 / 临时资源）用完**当轮就删**，不要留在内容文件里。

---

**再次提醒：读完本文后删除它。**

```powershell
git rm HANDOVER.md
git commit -m "docs: absorb the handover"
```
