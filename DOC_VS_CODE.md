# 文档 ↔ 代码 不一致记录

核对日期：2026-09-19 ｜ 基线：`main` @ `929b460` + 本轮 C-1/H-3/H-4/H-5/H-7 修复（工作区未提交）

**核对范围**：`README.md`、`ROADMAP.md`、`engine.md`（本轮新写）、所有 `src/**/*.java` 的注释与 javadoc、
以及 `src/main/resources/` 下的数据文件与 `code` 的实际行为。

> ⚠ **重要：本文所有"`ROADMAP` 第 N 行"的行号，指的是 v2 版 ROADMAP**
> （`ROADMAP.md` @ 2026-09-23 重写之前），**存档在 `docs/archive/ROADMAP-v2-历史.md`**。
> 2026-09-23 的 v3 重写把 ROADMAP 从 2446 行压到 ~350 行，**行号已全部失效** ——
> 引用行号时请对照存档文件，不要对照当前的 `ROADMAP.md`。
>
> 任务编号（`P3-4` / `P4-6` / `P5-2` 等）**仍然有效**：v3 保留了所有待办任务的编号，
> 已完成任务的完整规格在存档里。

### 对 ROADMAP v3 的核对（2026-09-23，本轮新写）

新写的 `ROADMAP.md` v3 逐条核对过，**未发现与代码/数据不符的断言**。已核实的事实：

| 断言 | 结论 |
|---|---|
| `Main` 的入口是 `public static void main()`（无参） | ✅ 相符 |
| `relic_sets.json` 未装载 | ✅ 相符（`Constant` 里唯一提到它的地方是本轮新加的 javadoc） |
| `elation_basic_level_damage.json` 从未被加载 | ✅ 相符（`Constant` 里 0 处引用） |
| `StageFactory.temporaryTeam()` 仍在（P8-5 未做） | ✅ 相符 |
| `Summon` 全项目没有 `new Summon(...)` | ✅ 相符 |
| `enemy_skills.json` 只覆盖 5 只怪 | ✅ 相符 |
| 93 个角色文档 / `aluminium_texts` 路径 | ✅ 相符 |

**方法**：把文档里所有**可验证的断言**（文件名、类名、方法名、字段名、测试用例名、数值、条数、
行为描述）抽出来逐条对代码/数据核实；再反向扫代码注释里对自身行为的描述是否与实现相符。
本文只记录**已核实**的不一致，每条给出证据。

严重度：**A** = 会直接导致写错代码/编译失败；**B** = 事实性错误（描述的东西不存在或数值不对）；
**C** = 过期/误导（不影响动手但会带偏判断）；**D** = 遗漏（代码里有但文档未记录）。

---

## A. 会让下一个动手的人直接写错

### A-1 `ROADMAP` P5-2 的嘲讽（TauntBuff）设计是错的：嘲讽是**硬指定目标**，不是仇恨加权

**ROADMAP 第 1254-1268 行的设计**（`aggroOf` 里做乘法）：

```java
public double aggroOf(CanHit a) {                       // public：P5-4 的 TargetSelector 要跨包调用
    double aggro = a instanceof Character c ? c.getPath().getAggro() : 100;
    for (AbstractBuff b : a.getBuffManager().getBuffs()) {   // ← 这个方法不存在（见下）
        if (b instanceof TauntBuff t) {
            aggro *= 1 + t.getExtraPercent();           // 嘲讽：仇恨 × (1 + 百分比)
        }
    }
    return aggro;
}
```

**问题一：接口不存在。** `BuffManager` 没有 `getBuffs()`（全仓库 grep 无此方法）。
第 1262 行的注释"`getBuffs`：P1-7 已加"是**错的** —— P1-7 的设计注释（第 451 行）恰恰写了
"**因此不需要 `getBuffs()`**"。另外两处依赖声明（第 1304、1448 行）也把 `getBuffs` 当成已有能力。
`getBuffs()` 在 ROADMAP 里出现 4 次：第 451 行（说不需要）、第 1176 行（不暴露）、
第 1262 行（当它存在）、第 1304/1448 行（当它存在）。

**问题二（更严重）：嘲讽的机制本身写错了。** 按作者的口径：

> **嘲讽 buff 只要被附加，攻击方（角色或怪物）的「单体攻击」与「扩散攻击的中心」，
> 就只能选中被附加嘲讽的那个个体。**
> —— 双向生效：我方单体/扩散打敌方时同理（若敌方身上有嘲讽）。

也就是说嘲讽是一个**目标选择的硬约束**，**不是**"把仇恨值乘一个百分比"。
两者语义差别很大：

- 乘法方案里，被嘲讽目标只是**概率更高**，仍然可能被随机到别人 → 与"只能选中"矛盾；
- 且百分比要多大才能保证 100%？没有这样的数值，所以乘法方案**结构上不可能**实现这条规则。

`HSR.md` §3.4 写的是"嘲讽：状态，**按百分比提高角色仇恨值**"——
**文档与实际规则不一致**，实现时以作者口径为准（已按此记录）。

**正确的落点应该是"目标选择"（P5-4 `TargetSelector`），而不是 `aggroOf`**：

```
选主目标(attacker, candidates, intent):
  如果 intent ∈ {单体, 扩散}：
      若 candidates 里存在"身上挂着 TauntBuff 的存活个体"（嘲讽 buff 挂在被打的那一方身上）
          → 强制返回那一个（若有多个，需再定规则：取第一个 / 按仇恨加权 / 取最近）
  否则：按仇恨加权随机（受击概率 = 仇恨 / 全队总仇恨）
```

**范围边界（据同一口径推导，需要确认）**：

| 攻击形状 | 是否被嘲讽强制 | 说明 |
|---|---|---|
| 单体（`SINGLE_ATTACK`） | ✅ 是 | 只能选中嘲讽者 |
| 扩散（`BLAST`）的**中心** | ✅ 是 | 中心被强制；**相邻格仍由站位决定**（不是"只能打中心"） |
| 群攻（`AOE_ATTACK`） | ❌ 否 | 本来就是全体，没有中心可选 |
| 弹射（`BOUNCE`） | ❓ 待定 | 规则没提。若每段都被强制，会与"每段重新随机存活目标"冲突 |
| 治疗/增益选我方 | ❌ 否 | 是友方选目标，与嘲讽无关 |

**边界条件**：嘲讽者若已死亡 → 约束失效，退回仇恨加权（不能强制选中尸体）；嘲讽者的阵营必须与
"被打的一方"一致，否则不生效。

**落地需要的东西**（P5-1/P5-2 之前）：

1. `TauntBuff` 本身**不需要带数值** —— 它是纯标记。所以"按百分比提高仇恨"这个概念在这里不存在，
   `getExtraPercent()` 也应该从设计里去掉。
2. 但 `TargetSelector` 需要能**拿到嘲讽者本人**（不是"有没有"）。所以 `BuffManager` 需要的不是
   `hasBuff(...) → boolean`，而是**按类型取实例**的口子。建议：

   ```java
   /** 取身上第一个该类型的 buff，没有则 null */
   public <T extends AbstractBuff> T findBuff(Class<T> kind)
   ```

   它是 `hasBuff` 的严格超集（`findBuff(X) != null` 即"有"），可以**替代** `hasBuff`
   （`hasBuff` 是我上一轮为 P4-6 加的，继续留着也无害）。
   不要把 `getBuffs()` 交出去 —— 遍历留在 manager 内部是 P1-7 的决定，且暴露可变列表会带来 CME 口子。
3. P5-4 的 `TargetSelector` 加一个"意图"参数（单体 / 扩散 / 群攻），由调用方（`EnemySkill` / 玩家指令）给出。

**当前代码状态**：`hasBuff(Class)` 已存在（上一轮加的，给 P4-6 用）；
`TauntBuff`、`Path` 枚举、`TargetSelector` 都**尚未创建**。

### A-2 `README.md` 的生成步骤不完整 —— **数据文件不存在时，报错完全指不出原因**

> ✅ **已修（2026-09-19）**：
> - `JSONReader.fromJSON` 改为显式判断 + `IllegalStateException`，报错带上文件名、目录与生成指引；
> - `README.md` 补了目标目录（`src/main/resources/data/`）、"数据不在仓库里"的说明、
>   以及缺失时的失败现象。
>
> 实测报错信息（临时移走探针验证，验证后已删）：
> ```
> java.lang.IllegalStateException: 缺少数据文件 /data/this_file_does_not_exist.json（应位于 src/main/resources/data/）
> 游戏数据不在仓库里（.gitignore 排除了 src/main/resources/data/），请先按 README 的 generator 一节生成数据，否则所有测试都会失败。
> ```
> 验证方式：`grep JSONReader src` 确认无测试依赖旧行为 → 改 → 167/167 全绿 → 用探针实际触发一次缺失路径。

> **原始判断（保留）**：它不影响数值正确性，但影响每一次从零开始的构建，
> 且失败信息与真实原因无关，排查成本高。

**`README.md` 全文 7 行**，关于数据只有第 7 行：

> You can use [this](https://gist.github.com/...) Python script and
> [turnbasedgamedata](https://gitlab.com/Dimbreath/turnbasedgamedata) to generate data.
> **You need to copy it into src/main/resources.**

**问题一（主要）：没有说明数据不在仓库里，也没有说明失败时的现象。**

`src/main/resources/data/` 被 `.gitignore` 排除，17 个 JSON 里只有
`monster_attack_modify_ratio.json` 被 `git add -f` 纳入了版本控制 —— 也就是说
**新克隆的仓库里这 16 个数据文件都不存在**（含 `skills.json` 1.7 MB、`monster_config.json` 2 MB、
`stage.json` 8.7 MB 等）。因此 `git clone` 得到的仓库**运行任何测试都会失败**，失败信息为：

```
java.lang.ExceptionInInitializerError
  ... Caused by: java.lang.NullPointerException
```

原因：`Constant` 的静态块在**任何**触碰它的测试里都会执行，而 `JSONReader` 对缺失资源是

```java
Objects.requireNonNull(JSONReader.class.getResourceAsStream(resourcePath))   // JSONReader.java:31
```

`getResourceAsStream` 返回 `null` → `requireNonNull` 抛 NPE → 静态初始化失败 → 测试全红。
此后同一 JVM 内每次访问 `Constant` 还会继续抛 `NoClassDefFoundError`。

该报错**不含**文件名、路径或 README 指引，从信息本身看不出缺失的是数据文件。

**问题二：路径描述不精确。** 第 7 行写"copy it into `src/main/resources`"，
而 `JSONReader` 读的是 classpath 的 `/data/` 前缀，文件需落在
**`src/main/resources/data/`**；按字面放在 `resources/` 根下同样会缺失。

**问题三：没有版本校验手段。** README 与 ROADMAP 都假设"数据与代码同版本"，
但没有可执行的断言，数据换版本后该假设无法被发现。

**建议（按性价比排序）**：

1. README 顶部补一段说明：数据不在仓库里 → 先按 generator 步骤生成 →
   否则测试会以 `ExceptionInInitializerError / NullPointerException` 失败；
   并写明目标目录是 `src/main/resources/data/`。
2. 让 `JSONReader` 在缺文件时给出自解释的报错（改动最小、收益最大，因为在报错现场直接说明原因）：

   ```java
   InputStream in = JSONReader.class.getResourceAsStream(resourcePath);
   if (in == null) {
       throw new IllegalStateException(
           "缺少数据文件 " + resourcePath + "（src/main/resources/data/" + jsonName + "）——"
           + "数据不在仓库里，请先按 README 的 generator 步骤生成，否则所有测试都会失败");
   }
   ```
   顺带修掉它 javadoc 里"资源缺失返回 `null`"的描述（实际从不返回 null，见 D-5）。
3. 加一条数据校验测试（条目数、等级表覆盖等），把"同版本"变成会失败的断言。

**关于 `max_energy` 的 `null`（这条不是问题，是设计）**

早前我把 `character_data.json` 里 1407 遐蝶的 `max_energy: null` 列为"数据陷阱"，那是误判，特此更正：

- 遐蝶**本来就没有常规能量条**，她的资源是【新蕊】，上限 = `5.3125 × 队伍最高等级²`（钳到 2000），
  获取方式是"我方每损失 1 点生命 +1 / 治疗量 100% 转化"等
  （见 `ROADMAP` P3-0 的 A 表，以及 P8-8「层数资源 Resource（替代能量条）」）。
- 所以 `null` 是**数据在如实表达"这一列对该角色不适用"**，不是缺值，也不是导出缺陷。
  引擎侧"`maxEnergy == 0` ⇒ 没有能量条"的语义正好与之衔接。
- bean 用 **`Double`（包装类型）而非 `double`**，正是为了容纳这个 `null`：
  `double` 无法表示 null，用它就只能兜一个数值（比如 100），那才是错的。**这个类型选择是正确的。**
- 需要注意的只是**消费侧不要 auto-unbox**：`ROADMAP` 第 1726 行已给出正确写法
  `c.setMaxEnergy(cd.maxEnergy() != null ? cd.maxEnergy() : 0)`。
  写 `double e = cd.maxEnergy()` 才会 NPE，属调用方问题，与数据无关。

### A-3 `ROADMAP` P4-6 的超击破削韧值口径错了（**已确认正确规则**）

**第 1188 行**原文：

> 超击破公式（参照 P4-3 组织，加在 `SkillExecutor` 里；**`stanceDamage` = P4-2 该段实际削韧值**）

**先厘清两个值**（代码里的实际来源）：

| | 含义 | 来源 |
|---|---|---|
| **标称值** `S` | 技能说明上写的削韧值 | `SkillData.getStanceList()` 的 `single`/`all`/`spread` |
| **实际值** `consumed` | 从韧性条上真扣掉的点数，被"剩余韧性"夹过 | `Enemy.reduceStance(S)` 的**返回值** |

**正确规则（作者口径，2026-09-19 确认）**：设敌人剩余韧性 `T`、技能标称 `S`、攻击方持有超击破 buff：

| 情形 | 击破伤害用 | 超击破伤害用 |
|---|---|---|
| `T > S`（没打空） | 无 | 无 |
| `S ≥ T`（**这一发把韧性打空**） | `min(S, T)` = **实际值** | `max(0, S − T)` = **超出部分** |
| 敌人已 `broken`（`T = 0`） | 无 | `S`（整发都是"超出"） |

要点：

- **不是"标称 vs 实际"二选一，而是把 `S` 拆成两半给两条链** —— 击破拿 `min(S,T)`、
  超击破拿 `max(0, S−T)`，两者相加恒等于 `S`，**不重不漏**。
- 举例（作者给的）：技能标称 **60**、怪物 **30** 韧性、带超击破 buff
  → 伤害构成 = **技能伤害 + 30 点击破伤害 + 30 点超击破伤害**。
- 同一发之内就会同时产生击破与超击破（"双击破"不止发生在多段之间）。
- **没有 buff 时**超出部分才是真的浪费 —— 这也是为什么上面第 2 行与第 3 行要分开写。

**ROADMAP 错在哪**：它写的"`stanceDamage` = 该段**实际**削韧值"。
若照此实现，第 3 行（敌人已 broken）时会拿到实际值 **0**，超击破伤害恒为 0 —— 公式直接失效。
（同一名词 `stanceDamage` 在 `Battle.reduceToughness` 里指的是**标称值**，
而该方法的返回值才是**实际值**，这两个概念在 ROADMAP 里被混用了。）

**实现影响**：

1. `Battle.reduceToughness` 现在只返回 `boolean`（是否触发击破），**超出部分在内部丢掉了**。
   P4-6 需要它把 `consumed` 与"是否破韧"一起带出来（例如返回
   `record StanceResult(double consumed, boolean broke)`），或者由 `SkillExecutor` 自己先取 `T` 再算。
2. `Enemy.reduceStance` 的返回值**够用**（`S − consumed` 就是超出部分），**不必改 `Enemy`**。
3. `Battle.java:363` 那条"H-4"注释已按本规则改准（原来只写"不是标称值"，会让人以为标称值彻底没用）。

### A-4 `ROADMAP` 给了 `Constant.SUPER_BREAK_BOOST` 和 `SUPER_BREAK` 类型，但后者其实已存在

**第 1187 行**：`Constant` 加 `public static final double SUPER_BREAK_BOOST = 0.4;` —— **确实还没有**，这条没错。

**但** P4-6 通篇读起来像"超击破需要新建类型"。**实际** `DamageType.SUPER_BREAK("super_break", false, false)`
**早就存在**（P1-1 就加了），而且 flag 已经正确（不可暴击、不吃增伤）。
文档只在 P1-1 的任务描述里提过它，P4-6 没写"类型已就绪，你只需要产出 base"。容易让人重复造。

---

## B. 事实性错误

### B-1 ⚠️ `hp_modify_ratio` 不是"幽灵字段"，它就是**攻击修正列**

**`ROADMAP` 第 94 行**（§0.6 陷阱 1）：

> **`hp_modify_ratio` 是幽灵字段** — tbgd 只有 `HPModifyRatio`，**没有** `HealthModifyRatio`；
> 解析器猜错字段名后留了个恒为 1 的 `hp_modify_ratio`。

**第 95 行**（陷阱 2）：

> **`attack_modify_ratio` 未导出** — tbgd 的 `AttackModifyRatio` 有 **444/2649** 个怪 ≠ 1，**本数据整体缺这一列**

**实测数据**（对 `monster_config.json` 与补丁文件逐条比对）：

| 字段 | 存在条数 | ≠ 1 的条数 |
|---|---|---|
| `hp_modify_ratio` | 2649 | **444** |
| `health_modify_ratio` | 2649 | 1244 |
| `attack_modify_ratio` | **0** | — |
| 补丁文件 `monster_attack_modify_ratio.json` | 444 | 444 |

**决定性证据**：把 `hp_modify_ratio` 与补丁文件逐条比 ——

```
两者都有且相等 = 444
两者都有但不等 = 0
只有 ghost 且 !=1  = 0
只有 patch 而 ghost 没有 = 0
```

**结论**：`hp_modify_ratio` 这个字段里装的**就是攻击修正值**（444 条与补丁完全一致，一条不差）。
它不是"恒为 1 的幽灵"，而是**被错误命名的攻击修正列**。

**三处连带影响**：

1. `ROADMAP` 第 95 行"本数据整体缺这一列"是**错的** —— 列一直在，只是名字叫 `hp_modify_ratio`。
2. 补丁文件 `monster_attack_modify_ratio.json`（`git add -f` 入库、ROADMAP 花了一整段解释怎么再生成）
   **是冗余的** —— 同样的数据已经在 `monster_config.json` 里。
3. 所以 §0.6 末尾说的"正解在导出脚本"只对了一半：真正该做的是
   **导出侧把 `hp_modify_ratio` 改名为 `attack_modify_ratio`**，然后 bean 直接读它，
   而不是"等数据自己带上 `attack_modify_ratio` 列"（它永远不会自己出现，因为它现在叫别的名字）。

> **注意**：现在不能贸然改代码。`MonsterConfig` 的 `attackRatio` 映射的是 `attack_modify_ratio`
> （JSON 里 0 条 → 恒为 `null` → 由补丁补上）。如果直接让 bean 去读 `hp_modify_ratio`，
> 而 `health_modify_ratio` 的映射保持不变，行为是**等价**的（我验证过 444 条全等），
> 但这是个**数据口径决定**，应该由作者确认后再动，别让审查顺手改掉。

### B-2 `ROADMAP` P1-5 的验收清单：条数对得上，但**读法**容易让人以为那是方法名

**第 386-397 行**写"`DamagePipelineTest`（**9 个用例**）"，然后列了 9 条**行为描述**
（不是方法名），例如"元素增伤：`FIRE_DAMAGE_BOOST=0.3`、受击者 DEFENCE=0 → 1300"。

**实际**：`DamagePipelineTest` 有 **8 个** `@Test`（`grep 'public void' | wc`）。
9 条描述确实都能一一映射到 8 个方法上（描述 1+2 由 `boostZoneWiresElementAndGlobalBoost` 一条覆盖），
所以**内容是对的、条数写成了 9 而实际 8**。

> 本条初稿我写成"3 个方法名不存在"，那是**错的** —— ROADMAP 给的是中文行为描述、
> 不是方法名，不存在"名字不一致"的问题。保留更正后的版本。

### B-3 `ROADMAP` P1-8 的"遗留"提到了不存在的 `aliveEnemies()`

**第 522 行**：

> `aliveEnemies()` 目前只看 `battle.enemies`，敌方召唤物（P9-4）进场后要一起算。

**实际**：代码里**没有** `aliveEnemies()` 这个方法（全仓库 grep 无）。真正的唯一出口是
`Battle.targetableEnemies()`。写 `aliveEnemies()` 会让读者去找一个不存在的符号。

### B-4 ~~`ROADMAP` P7-2 的测试用例名不存在~~ —— **本条已撤回（我核错了）**

撤回说明：我在初稿里声称 ROADMAP 引用了 `DamageZoneTest.java:100 "zonesNeverReroll"`，
但 `grep zonesNeverReroll ROADMAP.md` **零命中** —— 这个引用是我**臆造的**，ROADMAP 从未写过。
`DamageZoneTest` 在 ROADMAP 里只出现在两处"涉及文件"清单里（第 233、250 行），没有任何方法名。
保留这条是为了留痕：下面的 C-6 同样是臆造，已一并撤回。

### B-5 `ROADMAP` P0.3 对 `DefaultSkill` 的描述不准确

**第 47 行**：

> `models.Skill` / `DefaultSkill` | 技能抽象 + 默认实现（打 `target.getFirst()`；**skillId 写死 1**）

**实际**：`DefaultSkill` **没有**写死 skillId —— 它的 `getData()` 是
`DATA_CACHE.computeIfAbsent(cid + "_" + skillId, ...)`，用的是构造器传入的 `cid`/`skillId`。
真正写死 1 的是**调用方** `Character.Builder.build()`（第 243 行）与
`Character.fromAttributes(String,...)`（第 105 行）。

> 这条我上一轮在 `CODE_REVIEW.md` §4 里也误引过一次（说"ROADMAP 说法不准确"），
> 现在确认：ROADMAP 这段话把责任放错了类，`DefaultSkill` 本身是干净的。

---

## C. 过期 / 误导

### C-1 `engine.md`（本轮新写的）没有被任何地方引用

`ROADMAP` 只在第 1177 行提到 `CODE_REVIEW.md` 的 H-7（那是我这轮手动加的引用）。
`engine.md` 与 `CODE_REVIEW.md` 都是**孤立文件**：`README.md` 不提、`ROADMAP.md` 不提。
新来的人只会看到 README 那句"a game like HSR written by java"，找不到引擎说明。

### C-2 `CODE_REVIEW.md` 自身有两处需要修正（我写错的）

**（a）** `CODE_REVIEW.md` §1.5 曾写"`Constant` 的全局状态是不可变的"——**那是错的**，
后来在 H-1 里已改正（八个表七个可变），但 §1"确认没问题的部分"里没有明确撤回那句话。

**（b）本轮新增**：我在 `engine.md` §12.2 写过"`LevelPromotionCalc` 的两个倍率是项目自造的
线性近似、绝对值与游戏不符"，并据此建议"补一张等级成长表查表代替线性插值"。
**这是错的**：该公式**已用游戏数值验证过**（作者确认）。

错因值得留痕：`character_data.json` 里存的是 Lv1 面板、没有 Lv80 参照物，
我把"**找不到参照物**"误判成"**未经校验**"，再进一步推成了"与游戏不符"的结论——
把一个**不确定**的判断写成了断言。同一段里我手算倍率还错了两次（漏看 `level==80` 的额外 `-1`），
最后靠跑一个临时探针打印真值才纠正。

**另外**：`CODE_REVIEW.md` 完全没提 `hp_modify_ratio` 的真实身份（见 B-1）——
因为当时只核对了 bean 的映射，没有把 JSON 里那个字段的**值**与补丁文件逐条比对。
这是那份报告的一个**实质性遗漏**，已由本文 B-1 补上。

### C-3 `ROADMAP` 引用的 5 处外部路径在新克隆里不存在

`E:\code\blog\hsr\HSR.md`、`GLOSSARY.md`、`GLOSSARY_EXTRA.md` 被引用 5 次，并且 §0.5 明确写着
"**公式有疑义时以它为准**"。这些是作者本机路径，不在仓库里。
`data/data_path.txt` 的内容 `E:\turnbasedgamedata` 同样是绝对路径。

新来的人遇到"公式有疑义"时**没有任何依据可查** —— 这是最需要补的一块（要么入库、要么把关键结论内联进 ROADMAP）。

### C-4 `ROADMAP` §0.6 末尾的"版本一致性已核实"无法复现

> 同时验证过：**tbgd 与本仓库数据同版本**（2649 个 id 全部对得上；`HPModifyRatio` 不一致 0 条、
> `SpeedModifyRatio` 0 条、`DefenceModifyRatio` 仅 1 条 = 800205073）

这是一次性的人工核对，**没有可执行的校验**。`src` 里没有任何测试断言 tbgd 侧的条数，
且 tbgd 不在仓库里。数据换版本后这句话会静默失效。

### C-5 `ROADMAP` 没有记录本轮发现的 High 问题

这轮 `CODE_REVIEW.md` 列了 9 条 High，其中 5 条**仍然存在且没有进 ROADMAP**：

| 编号 | 问题 | 应该挂到哪个任务 |
|---|---|---|
| H-1 | `Constant` 八张表七张可变 | 无对应任务 |
| H-2 | `character_data.json` 的 `max_energy` 从未接进角色 | 应挂 P8-1（角色字段补全） |
| H-6 | `CanHit` 拷贝构造器浅拷贝属性数组 | 应挂 P7-4（波次） |
| H-8 | `Queue` 同行动值无裁决 + 显示顺序≠出堆顺序 | 应挂 P10-4（行动条操纵） |
| H-9 | `startBattle()` 从未被任何测试调用 | 无对应任务 |

已修的 4 条（C-1/H-3/H-4/H-5/H-7）也没有在 ROADMAP 的进度表里留痕。

### C-6 ~~`DoubleValueTest.zeroValue` 被 ROADMAP 赋予了它没有的语义~~ —— **本条已撤回（我核错了）**

撤回说明：初稿称 ROADMAP 写了 `DoubleValueTest.zeroValue` 用于"证明永不掷骰"。
`grep DoubleValueTest|掷骰 ROADMAP.md` **零命中** —— 该引用是我**臆造的**。
（`DoubleValueTest` 这个类名在 ROADMAP 里从未出现；"掷骰"一词也没出现。）

> **自我更正说明**：B-4 与 C-6 两条是我在没有回查原文的情况下凭印象写出的，
> 属于**编造证据**，已撤回。其余各条都给出了可复核的行号或数据，并经我实际执行过核对命令。

---

## D. 代码注释与实现不符（反向核对）

### D-1 `TestSkillGroup1` 的注释描述与真实槽位不符，且描述了一个不存在的技能

```java
// TestSkillGroup1.java:12
// 对指定敌方单体造成等同于#1%攻击力的冰属性伤害。
public static class TestSkill1 extends Skill {
    private static final SkillData DATA = SkillData.init(1001, 3);   // ← 槽位 3
```

**实际数据**：`skills.json` 的 `1001/3` 是 **`AoEAttack`**（火？不，Ice，`stance.all = 60`）——
**群体攻击，不是单体**。注释与它自己解析的数据矛盾。`SkillExecutorTest` 里知道这件事
（有用例名带 `aoe`），但注释没改。

另外第 37-38 行留着一段注释：

```java
// 为指定我方单体提供能够抵消等同于三月七 #1 %防御力 + #4 伤害的护盾，
// 持续 #2 回合。若该目标当前生命值百分比大于等于 #3，被敌方攻击的概率提高 #5 %。
```

**这个类里根本没有对应的技能实现**（没有 `TestSkill2`）。这是重构过程的残留注释。

### D-2 `Constant.DOT_RATIO` 的 javadoc 描述的公式与实现不符

```java
// Constant.java:168
 * 击破 DOT 每次结算的基础伤害 = 击破基数 × 本比例（**示例值，TODO data**：
```

这半句是对的。但同一段紧接着写：

> HSR.md §2 只写"**基础倍率由等级与击破特攻决定**（查数值表）"，逐元素倍率还没拿到

**实际**：`Battle.attachBreakDot` 算的是 `breakBaseOf(attacker) * Constant.DOT_RATIO`，
**既没有击破特攻、也没有削韧值**。javadoc 引用"由等级与击破特攻决定"会让人以为击破特攻已经进去了
（其实没有），而它后面又说"逐元素倍率还没拿到"，两句话指向不同的缺口。

### D-3 `Buff.java` 的接口契约实际未实现

```java
// models/Buff.java
public interface Buff {
    CanHit getSource();
    void setSource(CanHit source);
```

`setSource` 全仓库**无人调用**（grep 只命中接口声明与 `AbstractBuff` 的实现体），
所以 `AbstractBuff.source` **恒为 null**，`getSource()` 永远返回 null。
类上还留着裸 `// TODO` 与一句重构便签（"Made BUFF change the ATTRIBUTE not BUFF MANAGER!"）。
这是过程稿，不该留在正式接口上。

### D-4 `SkillData` 的 javadoc 声称 immutable，实际暴露可变集合

```java
// SkillData.java:20
 * <p>... Access is immutable read-only; resolve lazily via {@link #init(int, int)} ...
```

**实际**：`skills`（`List<List<Double>>`）与 `stanceList` 都是 Gson 造的可变对象，
类级 `@Getter` 直接把它们交出去；而 `DefaultSkill.DATA_CACHE` 把**同一个实例**共享给
所有实体/克隆/战斗。`Collections` 只被用于构造 `EMPTY_PARAMS`。

### D-5 `JSONReader` 的 javadoc 与失败方式相反 ✅ 已修

**修改前**：

```java
// JSONReader.java:25
 * @return the deserialized object, or {@code null} if the resource is missing
...
Objects.requireNonNull(JSONReader.class.getResourceAsStream(resourcePath))   // ← 抛 NPE，从不返回 null
```

**实际**：缺失时**抛 NPE**，从不返回 null。`Benchmark.java:40` 的
`if (data == null) System.exit(1)` 因此是**不可达代码**。

**已修（2026-09-19）**：随 A-2 一起改掉了 —— javadoc 改为 `@throws IllegalStateException`，
实现改为显式判断 + 自解释报错（不再用 `requireNonNull`，也不再声称返回 null）。
`Benchmark.java` 里那段不可达的 null 判断**仍在**（属于 N-2 的清理范围，未动）。

### D-6 `BuffManager.processBuffTick` 的语义无文档，且 `blocked` 只对 early buff 生效

代码本身没错，但**没有任何注释**解释 `blocked` 的生命周期：
`beforeMove()` 先 `blocked = false` 再 tick，所以后置控制 buff 在 `afterMove()` 到期时，
它的"最后一回合"挡不住行动。目前只有 `StunBuff`（early）在用，所以看不出来。

---

## E. 附：本文未发现问题的部分（已逐条核实为**一致**）

为免误伤，以下断言我核对过，文档与代码**相符**：

| 断言 | 结论 |
|---|---|
| `Damage` 有 7 个乘区 accessor | ✅ 7 个（boost/vulnerable/reduction/weakness/crit/defence/resist） |
| `monster_config` 2649 条、补丁 444 条 | ✅ 完全一致 |
| `attack_modify_ratio` 在 JSON 里 0 条 | ✅ 属实（但原因与文档说的不同，见 B-1） |
| `hp_modify_ratio` 在 100201101 = 1、`health_modify_ratio` = 0.266667 | ✅ 逐条对上 |
| 802501003 的 `health_modify_ratio` = 1.979167 | ✅ |
| 补丁里 100201506 = 0.33333302 | ✅ |
| `breaking_rate` 与 `hard_level_group` 等级集合一致（1–100 与 120） | ✅ 101 个等级完全相同 |
| `param_list` 长度 == `max_level` | ✅ 702/702 |
| `attack_type` 有 104 个 `null` | ✅ |
| 技能元素分布 Fire 99 / Thunder 64 / … / Unknown 212 | ✅ |
| `DOT_ELEMENTS = {FIRE, THUNDER, PHYSICAL, WIND}` | ✅ |
| `SkillType` 6 值 / `DamageType` 12 值 / `SkillEffectType` 11 值 | ✅ |
| `AttributeType` 值数量 | ✅ **27**（本记录更正：我早前在 `engine.md` 初稿写过 31，已修） |
| `hasToughnessBar` / `isWeakTo` / `reduceToughness` / `handleBrokenTurn` 等方法存在 | ✅ |
| `Battle.assemble` 是第 5 步发双方事件、顺序与注释一致 | ✅ |
| 补丁文件被 `git add -f` 纳入版本控制（`.gitignore` 排除了 `data/`） | ✅ |
| `Main.java` 的 `setSkillLevel(ULTRA,5)` + `setSkillByClass` 用法 | ✅ 方法存在且签名匹配 |
| `Character.fromAttributes(String,double×4)` 参数顺序 (health, defence, attack, speed) | ✅ |
| `Enemy.fromAttributes(name, health, defence, attack, speed)` | ✅ |
| `E:\code\blog\hsr\*`、`E:\turnbasedgamedata` 等外部路径 | ✅ 确实不在仓库里（见 C-3，属遗漏非错误） |

---

## F. 引擎能力缺口登记（2026-09-23，P8-4 战技点复核）

> **本节的性质**：这些**不是**文档与代码不一致，而是"引擎还不具备的能力"。
> 单独登记的目的是让它们**可枚举、可排序、可勾**，而不是散落在
> `engine.md` 的正文里等着被下一个动手的人重新发现一遍。
>
> **处理原则（用户已明确）**：引擎保持**通用、可扩展、稳定**，
> **不替角色机制背锅**。凡是"某个角色要这样"而"引擎没有这个抽象"的，
> 一律记在这里，等对应阶段（P8-7 触发器表 / P10-3 遗器套装 / 数据补全）再动。
>
> **进度**：`F-6`（裸字符串）与 `F-8`（策略挤在 `Battle` 里）已在
> 2026-09-23 的重构中**解决**（各自带护栏测试）。其余 6 条仍是"**有挂靠点、
> 没接线**" —— 重构提供了接口（换策略 / 改上限 / 改开局），但"**谁在什么时候改**"
> 还没定，因为那取决于还没做的系统（触发器表 / 遗器套装 / 数据补全）。

### F-1 战技点上限不是恒定值，且引擎无"改队伍级资源上限"的口子

- **现状**：`Constant.SKILL_POINT_MAX = 5` 是常量，`gainSkillPoint` 直接对它封顶。
- **证据**（都来自本项目自己的数据文档）：
  - `1306_花火.md` 天赋「花火在场时，战技点上限额外增加 **2** 点」（加强后为 +3）；
  - `WEAPONS.md` 欢愉光锥「装备者在场时，队伍中每有 1 名「欢愉」命途的角色，
    战技点上限提高 1 点，最多提高 3 点」；
  - `WEAPONS.md` 另一把光锥的**触发条件**是「进入战斗时，若我方的战技点上限**大于等于 6** 点」
    → 这句话本身就证明上限不恒为 5。
- **归属**：需要一个新的"队伍级资源上限"口子。**不要**在 `Battle` 里判 `cid`。
- **风险**：低（当前只影响那几套配置，基础规则正确）。

### F-2 开局战技点不是恒定值，遗器套装效果整体未接

- **现状**：`Constant.SKILL_POINT_START = 3`，引擎开局恒为 3。
- **证据**：`RELICS.md` 过客 4 件套「战斗开始时，立即为我方恢复 1 个战技点」
  → 穿它的队伍开局 **4** 点；两个角色穿就是 **5** 点。
  （这条也与"常规开局 3"互为印证：正因为基础是 3，+1 才有意义。）
- **现状的根因**：**遗器套装效果整体没有实现** —— `relic_sets.json` 连装载都没装载
  （`Constant` 只装了 `main_attribute.json` / `sub_attribute.json`）。
- **归属**：P10-3（Buff 体系完善）或遗器套装的独立任务，不是 P8-4 的尾巴。
- **风险**：低到中（属于"没实现"，不是"算错"）。

### F-3 ⚠ 「普攻 +1」是一刀切，强化普攻有例外 —— **这条会让引擎算错**

- **现状**：`applySkillPointCost` 按 `attack_type == "Normal"` 给 +1。
- **证据**：
  - `1315_波提欧.md`：强化普攻「**无法恢复战技点**，且仅能以处于【绝命对峙】的敌方目标为目标」
    → 引擎会**错误地 +1**；
  - `1213_丹恒•饮月.md`：强化普攻「施放本技能**不消耗战技点**且不视为使用战技」；
  - **但** `1201_青雀.md`：「施放强化普攻后，**恢复 1 个战技点**」→ 引擎 +1 是**对的**。
- **为什么数据救不了它**：强化普攻在数据里也是 `"Normal"`，**没有单独类型**
  （实测 122 条 `Normal` = 93 角色 × 1 + 饮月/镜流/青雀/波提欧的多档强化普攻）。
- **⚠ 不要这样修**：改成"强化普攻一律 +0"会把**青雀改坏**。
- **正解**：**每个技能自带"战技点增量"字段**（数据补全），引擎只读字段、不做类型推断。
  这也一并解决"某个战技回点/不回点"的所有变体。
- **归属**：数据补全（`generate_data.py` + `skills.json`），不是引擎逻辑。
- **风险**：中（当前只对波提欧一类算错，但错误的性质是"数值偏高"）。

### F-4 角色 / 光锥 / 遗器级的供点机制全部未接

- **现状**：只有全队一份 `gainSkillPoint`，**没有任何"按来源修正增量"的钩子**。
- **证据**（均为角色文档原文）：
  | cid | 角色 | 机制 |
  |---|---|---|
  | 1101 | 布洛妮娅 | 施放战技时，有 50% 固定概率恢复 1 个战技点（1 回合触发冷却） |
  | 1206 | 素裳 | 对陷入击破状态的敌方目标施放战技后，恢复 1 个战技点 |
  | 1201 | 青雀 | 争番：施放战技时恢复 1 个战技点，单场战斗只能触发 1 次 |
  | 1215 | 寒鸦 | 对【承负】目标施放 2 次普攻/战技/终结技后，立即为我方恢复 1 个战技点 |
  | 1223 | 貊泽 | 施放天赋的追加攻击后，恢复 1 个战技点（1 回合后可再触发） |
  | 1321 | 大丽花 | 施放天赋的追加攻击时，为我方恢复 1 个战技点（每 2 次触发 1 次） |
  | 1312 | 米沙 | 我方全体每消耗 1 个战技点 → 下次终结技 +1 段，且米沙回 2 能量 |
  | 1306 | 花火 | 我方消耗战技点时，花火额外恢复 1 点能量；终结技恢复 4/6 点并可**溢出储存**（最多 10） |
  | 1410 | 海瑟音 | 战斗开始时展开结界，恢复 1 个战技点 |
  | 1415 | 昔涟 | 单次生效：为我方恢复 1 个战技点 |
- **注意 1312 / 1306 这一对**：它们要监听的是"**战技点被消耗**"这件**事**，
  而不是某个技能 —— 这正是 `EnergyProvider` 那种"按技能类型查表"的钩子**表达不了**的。
- **归属**：**P8-7 触发器表**（事件 + 条件 + 效果）。需要新事件
  `SkillPointSpentEvent` / `SkillPointGainedEvent` 作为宿主（与 P8-6 一起补）。
- **风险**：低（当前是"没实现"，不产生错误数值）。

### F-5 `EnemySkill.getData()` 恒为 `null`，敌方行动绕过战技点分支

- **现状**：`EnemySkill.getData()` 按设计恒返回 `null`（敌人不走角色倍率表），
  所以 `applySkillPointCost` 在 null 保护处就返回了，**走不到类型分支**。
- **影响**：不是因为阵营判断而"安全"，而是**两重原因同时成立**。
  将来若敌人改用真实技能数据（P9-1/P9-2 接真实技能表），阵营判断就成了**唯一**防线。
- **⚠ 测试陷阱（我踩过）**：拿敌人默认的 `EnemySkill` 测"敌方不影响战技点"是**空转** ——
  去不去掉阵营判断都会通过（变异测试才发现）。已改成
  `SkillPointGameParityTest` 里**手工给敌人装一个真实角色普攻**。
- **归属**：P9-1/P9-2 时注意保留阵营判断。
- **风险**：低（现在有两重保护，去掉一重还有一重）。

### F-6 ~~`applySkillPointCost` 里的 `"Normal"` / `"BPSkill"` 是裸字符串字面量~~ ✅ 已解决（2026-09-23 重构）

- **原状**：判定用字符串 `switch`，与 `StandardEnergyProvider.onSkillCast` 里的写法**重复**
  （两处各写一份 `case "Normal"` / `case "BPSkill"`）。数据侧改拼写或新增类型会**静默失配**。
- **解决**：新增 `enums.SkillCategory`（数据 `attack_type` 的类型化，含
  `UNSPECIFIED` 合法空与 `UNKNOWN` 未知值），`SkillData.getCategory()` 为唯一入口；
  两处 `switch` 都已改用它，**新增数据取值时编译器会逼着每个 `switch` 表态**。
  - 未知取值**不抛异常**（降级成 `UNKNOWN` + `isKnownValue()` 可观测）——
    数据是外部产物，多一个类型就炸引擎是稳定性问题；
  - 大小写不敏感 + 去空白（建表与查表走同一个 `normalize()`）。
    ⚠ 第一版只在 javadoc 写了"大小写不敏感"却没实现，被
    `SkillCategoryAndResourceTest.knownValuesRoundTrip` 抓到。
- **护栏**：`SkillCategoryAndResourceTest`（18 条）。

### F-7 `hasSkillPoint()` 语义过窄

- **现状**：`hasSkillPoint()` = `skillPoints > 0`，即"够不够放一次战技"。
- **问题**：花火的【幻相】与溢出储存让"战技点是否够用"不再是简单比较，
  而且调用方（演示 AI）实际关心的是"允不允许放"这个**策略**问题，不是资源数量。
- **进展**：`Resource` 已提供 `isFull` / `isCapped` / `missingToMax` 等更准的口子，
  `Battle.hasSkillPoint()` 保留为门面（现在是 `skillPointPolicy.canAfford()`）。
  剩下的语义问题要等花火落地（P8-7/P8-8）才能定形。
- **归属**：随 F-1 的口子一起收口。
- **风险**：低（当前无语义错误）。

### F-8 ~~战技点的**策略**与**机制**都挤在 `Battle.useSkill` 里~~ ✅ 已解决（2026-09-23 重构）

- **原状**：`useSkill` 同时负责"阵营判断 + 战技点结算"两个内联块，
  等角色级供点（F-4）落地就会成为机制堆积点 —— 正是 P8-0 三分法要避免的形态。
- **解决**：抽出 `models.skillpoint.SkillPointPolicy`（接口）+
  `StandardSkillPointPolicy`（基础规则）。`Battle` 只持有一个
  `public SkillPointPolicy skillPointPolicy` 并在 `useSkill` 里**问一次**，
  **不认识任何角色**。基础规则 / 阵营判断 / 增量 / 上限 / 开局各有归属：

  | 关注点 | 归属 |
  |---|---|
  | 基础规则（普攻 +1、战技 -1、其余中性） | `StandardSkillPointPolicy` |
  | 阵营判断 | **策略**（不再是 `Battle`）—— 由测试证明 |
  | 上限 / 开局 | 策略构造参数（`new StandardSkillPointPolicy(max, initial)`） |
  | 角色级增量 | 覆盖 `gainForCast`（将来由 P8-7 效果表驱动） |

- **护栏**：`SkillPointPolicyExtensibilityTest`（6 条）—— **不改引擎一行**，只换策略，
  就改变了普攻增量（+2）/ 上限（7）/ 开局（4），并证明"阵营判断在策略里"。
- ⚠ **重构自身的教训**：中途我让 `applySkillPointCost(skill)` 从 `currentMove`
  猜出手者，在"没有行动者"的场景猜出 `null`，而 `null != Camp.PLAYER` 让策略
  **静默变成空操作** —— 一个不报错的错误答案。已改成显式传出手者
  `applySkillPointCost(skill, user)`，让这类误用直接编译失败。

---

## 建议的处理顺序

1. **A-1**（P5-2 的 `getBuffs`）：动手 P5 之前必须决定 API 形态 —— 是按类型取实例，还是把嘲讽下沉进 manager。
2. **A-3**（超击破的削韧值口径）：P4-6 动手前把"标称值 vs 实际值"写清，否则实现出来的公式是反的。
3. **B-1**（`hp_modify_ratio` 的真实身份）：确认后可以删掉补丁文件与 `normalizeMonsterConfigs` 的合并逻辑，
   并修正 §0.6 的两条陷阱描述。**这是本次最有价值的发现，但需要作者拍板。**
4. **A-2**（README）＋ **C-3**（外部文档）：影响下一个克隆仓库的人。
5. 其余 B/C/D 是标注性修正，可以一次性清理。
6. **F-3**（强化普攻的战技点增量）：唯一一条会让引擎**算出错误数值**的引擎能力缺口。
   正解是数据补全（每技能自带增量字段），**不能**靠改引擎的类型判断绕过 ——
   一刀切改成"强化普攻 +0"会把青雀改坏。动手 P8-3（追加攻击）前最好一起定字段。
7. **~~F-6 / F-8~~** ✅ 已在 2026-09-23 重构中解决
   （`SkillCategory` 类型化 + `SkillPointPolicy` 抽出，各带护栏测试）。
8. **F-1 / F-2 / F-7**：接口已经就位（`new StandardSkillPointPolicy(max, initial)`），
   缺的是"**谁在什么时候改**" —— 上限类要等光锥/角色（P8-7），
   开局类要等遗器套装（P10-3），`F-7` 要等花火落地。三条一起做成本最低。
9. **F-4**（角色级供点）/ **F-5**（敌人技能数据）：分别等 P8-7 / P9-1，不需要现在处理。
   做 P8-7 时**直接用**已就位的 `gainForCast` 覆盖点，不要回头改 `Battle`。
