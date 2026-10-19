# AGENT.md — 这个仓库的唯一台账

> **一份文件，三个读者。** 这个仓库**只有这一份**台账 ✓。
> 找某条规矩时**只在这里找** ✓；找不到就是没有，**不要去别的文件里猜** ✗。
>
> | 你是 | 读哪节 | 你要回答的问题 |
> |---|---|---|
> | **改引擎的** | §三 | 这台引擎的线在哪、改它必须过什么闸门 |
> | **做项目 / 写内容的** | §四 | 现在是什么阶段、下一步做什么、为什么这样排 |
> | **收尾 / 交接的** | §五 | 怎么报、报什么、没做什么 |
> | **谁都该读** | §一 · §二 | 行为准则、这份台账自己的规矩 |
>
> 仓库外的两份资料：`TRIGGER.md`（触发器 JSON 格式——是**规范**不是台账）、
> `HSR.md`（游戏侧公式，正典副本在仓库外）。**"现在是什么"的权威是代码** ✓，
> 以及 `src/main/resources/game/characters/<cid>.json` 里每条规则自带的 `source` / `note` ✓。

---

## 一、行为准则（**所有角色都适用**）

> 这一节是**硬规矩**，不限于本项目 ✓（原 `CONDUCT.md` 已并入本文件 ✓）。
> **先读 §1.0 的清单**，照着做；正文的每条都来自一次**真实的失败** ✓，
> 所以写成「**现象 → 我错在哪 → 以后怎么做**」，而不是「要仔细」这种没人会反对也没人会执行的话 ✗。
> ⚠ 本节只写**不变量**：需要数字的事一律现量（§1.11）；引用代码只写**符号名**，不写行号（§2.4）。

### 1.0 行为准则清单（照着做，不用记）

| # | 准则 | 为什么 / 详见 |
|---|---|---|
| 1 | **先量现象**，再解释为什么；一次只改一个变量 | §1.1 |
| 2 | 每个结论都要说清**证据来自哪一层**；量不到就说「这一层我没量」 | §1.2 |
| 3 | **错了就撤回**，并**留在原地**写 `CORRECTED` | §1.3 |
| 4 | **对照实验本身也要被验证**：变红 ≠ 注入成功 | §1.4 |
| 5 | **断言落在功能上**，不落在仪器的代理上 | §1.5 |
| 6 | **一处通过 ≠ 处处通过** | §1.6 |
| 7 | **诊断器默认做正事**，结论必须能被数据推翻 | §1.7 |
| 8 | **读官方资料**，不反编译字节码 | §1.8 |
| 9 | 先问清「你具体看到什么」，再动手 | §1.9 |
| 10 | **尊重用户环境**：不杀进程、不关窗口、不提权 | §1.10 |
| 11 | 文档里**每个数字都要量过**；「写不了」必须附出处 | §1.11、§6.2 |
| 12 | 交付时**说清没做什么** | §1.12 |
| 13 | 跑闸门先读 `UP-TO-DATE`；要真跑就 `--rerun-tasks` | §3.3、§6.5 |
| 14 | **变异绿有三种可能**：判据弱 / 变量没动 / 变异写坏 | §6.4 |
| 15 | 穿过乘区就断言**比值**；探针打**前提**，打在构建真正读的那份文件上 | §6.3、§6.5 |
| 16 | **同形只准有一条**（`×N` 计数器）；有守卫的压成一行 | §2.2、§2.3 |
| 17 | **不近似、不静默降级、不越权、不把绿当结论** | §3.5、§5.2 |
| 18 | **一轮**：读者计数 → 实现 → 判据 → 变异 → 文档 → 提交 → **四道闸门** | §3.3 |
| 19 | `{@code X.md}` **不是引用**；文档指针靠**自己的巡检** | §6.7 |

### 1.1 先量现象，再回答「为什么」

⚠ **真实案例**：前端报告「鼠标没反应」✗。我先后判断为「框架默认关掉了鼠标」→「终端不支持」→
「解析器太旧」→「配置没生效」✗ —— **四次推断，四次都错** ✓。真正的原因由**一次分模式的字节计数**给出：

```
?1006 单独       bytes=0     ← 它把上报关掉了
?1000 单独       bytes=360   ← 有效，但格式是旧的 X10
?1000 ?1006      bytes=0     ← 加上 ?1006，能用的也归零
模块五条一起发    bytes=0     ← 模块正是这么发的
```

- **多层问题时，逐层量** ✓，不要从中间开始猜 ✓。先确认「有没有到这一层」，再讨论「这一层处理得对不对」✓。
- 一次实验**只改变一个变量** ✓（上表每一行就是一次）✓。
- **量出来的数字要能推翻我的假设** ✓ —— 否则那不是实验，是确认偏误 ✗。

### 1.2 不许用推断代替实测

⚠ **真实案例**：我宣称「整条链都验过了，全通」✗ ——
**我验到「真实后端能解析 SGR 序列」，却没验「JLine 愿不愿意读鼠标」** ✓，而断点**恰好在那一层** ✓。

⇒ **每个结论都要能说清它的证据来自哪一层** ✓；做不到的，就说「**这一层我没量**」✓。

| 结论 | 证据 |
|---|---|
| 配置是开的 | `TuiConfig.defaults()` 的返回值 |
| 事件到了处理器 | 真实运行的日志 |
| **标记画出来了** | **渲染后的转义序列**（不是状态）|

### 1.3 错了就撤回，而且留在原地

- ⚠ **真实案例**：「框架默认关鼠标」这个错误判断**被我当成前提用了好几轮** ✓ ——
  它写进了代码注释、写进了测试断言 ✗。发现后**注释和断言都改了** ✓，并**在原地**写明原文是错的、为什么错 ✓。
- 新证据推翻旧判断时，**第一步是明确撤回** ✓，不是悄悄改掉 ✗。
- ⚠ **代码注释里的错误结论比没有注释更坏** ✗ —— 没有注释会让人去查，错的注释让人去**照做** ✓。
- 撤回写成「**CORRECTED：原文说 X，实测是 Y**」✓，**因为下一个人会踩同一个坑** ✓。

### 1.4 对照实验本身也要被验证

⚠ **真实案例（两个）**：
1. 我的对照脚本**打印「cases added」却什么都没插入** ✗ —— 锚点少了一截，而 `str.replace` 找不到时**静默不做事** ✓。
2. 我的对照**该红却绿着通过** ✗ —— **锚点对着旧代码** ✓，脚本只打印一行「anchor not found」✓，**看起来像通过** ✓。

- **「变红」和「注入成功」必须一起看** ✓。只看变红 ⇒ 无法区分「改对了」和「没改到」✗。
- **锚点从文件里读出来，不要手打** ✓；**注入后立即校验目标字符串真的变了** ✓。

### 1.5 断言要断言在功能上，而不是它的代理

⚠ **真实案例**：**整批用例全绿，而悬停功能是坏的** ✗ —— 它们**全都在查状态**（`hover == 1`）✓，
**没有一条查「看得见」** ✓，而我把标记画成了 `dim`（SGR 2）✓，**在深色终端上几乎不可见** ✗。
⇒ 正确的断言是 **「转义序列里必须有 SGR 7」** ✓，不是「状态是 1」✗。

⇒ 问自己 **「这个断言在功能坏掉时会红吗？」** ✓ —— 只验证中间变量，验证的是**实现**不是**功能** ✗。

### 1.6 一处通过不等于处处通过

⚠ **真实案例**：两个设置页把 `hover` **写死为 `-1`** ✗ ⇒ **标记永远不可能出现** ✓，
而**所有悬停用例都只测主菜单** ✗ ⇒ **完全测不出来** ✓。

- **每个分支 / 屏幕 / 入口都要有至少一条用例** ✓。
- 新增一个「同类的地方」时，**回头检查老的用例覆盖了哪些、漏了哪些** ✓。

### 1.7 诊断工具本身会骗人

⚠ **真实案例（三个）**：
1. 诊断器**没等够就自己退出** ✗（我写的是「没加 `--live` 就只报告」✓）⇒ **看起来像崩溃** ✓。
2. 我放在 `build/` 里的手写启动器**被下一次构建删掉** ✗（`installDist` 会重建整个目录）✓。
3. 字节级诊断器的**判据写错** ✗ —— 它把「收到过 ESC」当成「终端在上报鼠标」✓，而当时**只回来 1 个字节** ✓。

- **诊断器要默认做那件正事** ✓，不要额外参数 ✗。
- **生成物放进构建**（Gradle task）✓，**不要手写在 `build/` 里** ✗。
- **诊断器的结论行必须能被数据推翻** ✓ —— 写结论前先问「这个判据在最坏情况下会说谎吗？」✓

### 1.8 读官方资料，不要反编译字节码

⚠ **真实案例**：我为了搞清鼠标路径**反编译了大量字节码** ✗，
而**官方源码 jar 就躺在本地 Gradle 缓存里** ✓（`*-sources.jar`）。改读源码后，**几个关键语义几分钟就清楚了** ✓。

```java
// MouseSupport：hasMouseSupport 就是查 key_mouse 能力
return terminal.getStringCapability(InfoCmp.Capability.key_mouse) != null;

// NativeWinSysTerminal：移动事件由 tracking 字段过滤，而不是由转义序列
if (tracking == Off
 || tracking == Normal && moved
 || tracking == Button && moved && noButtonDown) return;   // 丢弃

// ListWidget：高亮是【在 item 之后】整行覆盖
if (isSelected) buffer.setStyle(rowArea, highlightStyle);
```

⇒ **先找源码 / 文档，再考虑反编译** ✓。反编译适合确认**「生成的产物里到底是什么」** ✓，
不适合理解**语义** ✗。

### 1.9 先问清「你具体看到什么」

⚠ **真实案例**：报告「滚轮不好使」✗，我当成「事件没到」✓，去查路由 ✗。
实际情况是 **`hover` 和 `selection` 的视觉重叠** ✓ 加上**命中区只有面板那么宽** ✓ —— 两件完全不同的事 ✓。

- 追问 **「你具体做了什么 → 看到什么」** ✓，不要接受「不好使」「没反应」这种描述 ✗。
- 当**我的实测和报告相反**时，**先假设是我量错了** ✓，把**坐标、尺寸、状态**打进日志 ✓ ——
  **不要先怀疑用户的观察** ✗。⚠ 一条**带坐标的日志**胜过三轮推断 ✓。

### 1.10 尊重用户的环境

⚠ **真实案例**：构建目录被占 ✓（游戏开着，一批 jar 被锁）✓。**我没有去杀那个进程** ✓ ——
强杀会**留下没关闭的鼠标上报状态** ✓，那个终端会一直往程序发鼠标序列 ✓。

- **不替用户杀进程、不关他的窗口** ✓；**不擅自提权** ✓。
- 构建被占用时，**说清楚是谁占着、请他关掉** ✓，不要绕过去 ✗。

### 1.11 文档里的数字必须量过

⚠ **真实案例**：我在 `TRIGGER.md` 里写了一个「听起来对」的角色文件计数 ✗ —— **实测不是那个数** ✓。
⇒ 病不是那个数，而是**它没有从数据里数出来** ✗ —— 而那份文档存在的意义正是防止这类错误 ✓。

- **能写的只有三类** ✓：**日期**（带出处 ✓）、**公式与数据常量**（它们本身就是被验证的对象 ✓）、
  以及某个**具体实验**的读数（它描述"那一次"，不描述"现在" ✓）。
- 其余一律**现跑现量**，并把**一条能重算它的命令**写在旁边 ✓；引用代码只写**符号名**（`Constant.RESIST_MIN` ✓），不写行号 ✗。
- ⛔ **"现在是什么"的计数**（套件 / 用例 / 文件 / 规则 / 提交数、行号、大小）由 §2.4 管 ✓ —— 那条有守卫，这条不重复说 ✗。
- ⚠ 尤其小心**自己在文档里违反自己立的规矩** ✓。

### 1.12 交付时说清「没做什么」

⚠ **真实案例**：我做过**静默降级** ✗（鼠标起不来就退回普通后端）✓ —— **这是错的** ✓：
**它把失败变成一个看不出来的降级** ✗。

- **没做的事、做不到的事、故意不做的事，都要说出来** ✓。
- 缺能力时**登记** ✓，**不要近似** ✗；**降级要出声** ✓（写到 stderr）✓，**不要静默** ✗。

---

## 二、台账自己的规矩（**反增长条款**）

> ⚠⚠ **这一节是 2026-10-16 加的，因为它自己长歪过** ✓：本仓库曾同时存在
> `GAPS.md` / `HANDOFF.md` / `REFACTOR.md` / `EXPRESSION.md` 四份"什么都记"的台账 ✓，
> 它们**膨胀成了第二份真相**，于是在 2026-10-08 被**整个删掉** ✓；
> 随后 `ENGINE.md` / `CONDUCT.md` / `AGENT.md` **又长回了三份** ✓，
> 并且 `AGENT.md` 里出现了"**2026-10-02 那一轮**"/"**2026-10-16 那一轮**"这种**按轮次追加**的节 ✓
> —— 那是**同一个病的第二种形态** ✗。2026-10-16 合并成本文件 ✓。

### 2.1 只有一份

- ⛔ **不要再新建台账** ✗（`GAPS.md` / `HANDOFF.md` / `REFACTOR.md` / `EXPRESSION.md` / `ENGINE.md` /
  `CONDUCT.md` / `AGENT.md` 这一族**都已经并入本文件** ✓）。发现自己在写新的一份 ⇒ **停下** ✗。
- ⛔ **不要新开"第 N 轮"那一栏** ✗。教训**并进 §六 已有的主题小节** ✓；
  确实装不下时**新建"主题"小节** ✓（"效力 / 判据 / 测量 / 文档"这类名字 ✓，不是"某年某月某日那一轮" ✗）。
  **发现自己在写 `### ⭐ 某轮一句话版本` ⇒ 停下** ✗。

### 2.2 同形只准有一条（**规则垃圾回收**）

- ⭐ **一个形状只有一条规矩** ✓。第十次踩同一个形状时，
  **把计数器 +1** ✓（`（×N）`），**不要再写一段叙述** ✗。
- ⭐ **复现次数是信息** ✓ —— 它说明这条规矩**拦不住**，该想的是**加机器守卫**，
  而不是把这段话写得更长 ✗。
- ⚠ 合并时**逐字重复**是要真删的 ✓（2026-10-16 实测：`AGENT.md` 的 bullet 里确有与 §三 逐字相同的 ✓
  —— 用**逐行分组比对**数出来的 ✓，不是"看着像"✗）。

### 2.3 按「是否已被把守」决定篇幅

- ⭐ **已有机器守卫的规矩，压成一行 + 守卫名** ✓ —— 守卫会拦住你，散文不用再说一遍 ✓。
- ⚠ **只靠散文的规矩，才需要完整叙述** ✓ —— 因为它**没有任何东西替你拦** ✗。
- ⇒ **篇幅是稀缺资源，按这个分配** ✓。守卫清单在 §七。

### 2.4 不写死会腐烂的数字（**只写不变量**）

- ⛔ **不写"现在是什么"的数字**：套件 / 用例 / 文件 / 规则 / 提交数、代码行号、行数、大小 ✗ ——
  权威是**代码**，要数字就**现跑现量**（§1.11 有清单）✓，不要抄本文件 ✗。【已把守：`LedgerNumberGuardTest`】
- ✅ **实验读数可以写，但要标日期** ✓ —— 它描述"那一次"，不描述"现在" ✓。
- ⚠ **本文件如果不再被维护，就删掉或降级** ✓ —— 一份腐烂的"准则"比没有更坏 ✗
  （§1.3：错的注释让人照着做 ✓）。

---

## 三、角色 A：改引擎的

> **谁读**：动 `Battle` / `SkillExecutor` / `Damage` / `CanHit` / `models/**` / `models/trigger/**` 的人 ✓。
> **不读也行**：只写 `src/main/resources/` 下 JSON 的人 ✓（去 §四）。
> ⚠ **改引擎的每一批都必须过 §3.3 的四道闸门** ✓，并**贴出数字** ✓。

### 3.1 三条红线（违反即返工）

| 红线 | 判据（能机械检查） | 把守 |
|---|---|---|
| **引擎不认角色** | `Battle` / `SkillExecutor` / `Damage` / `CanHit` 里**不出现 `cid` 判断** ✓。唯一允许出现 cid 的地方是装配点（`CharacterFactory`）与 provider / 触发器注册表 ✓ | 【只靠散文】 |
| **能数据化就数据化** | 新增机制先问「**引擎有没有这个能力**」✓；**先补引擎能力，再数据化角色** ✓。写成专用 Java 类的，必须在 §4.1 逃生舱表登记 ✓ | 【只靠散文】 |
| **一个任务自带判据** | 没有测试的修复等于没修 ✓。**改完必须能说清「哪条用例会因这次改动变红」** ✓ | 【只靠散文】 |

⭐ **推论（本仓库实测）**：**「有测试」不等于「被把守」** ✓。曾发现角色文件整段规则消失而
**全量测试仍全绿** ✓ —— 因为消失的恰好是「没有用例把守」的那类规则 ✓。
⇒ 规则改动后要**对着原文逐条读一遍** ✓，不能只看测试绿 ✗。

### 3.2 三层结构：判据先问「这属于哪一层」

**层定错了，后面全错** ✗：

| 层 | 是什么 | 位置 | 判据 |
|---|---|---|---|
| **引擎** | 通用能力（伤害 / 能量 / 韧性 / 行动条 / buff / 资源） | `Battle`、`models/*`、`models/trigger/*` | 与具体角色无关，**不得出现 cid** |
| **数据** | 「在某事件后，做一件**引擎已有能力**的事」 | `characters/*.json`、`relic_sets/`、`light_cones/` | 触发器 + 条件 + 效果能写完 ⇒ **不写 Java** |
| **逃生舱** | 需要跨系统状态机，数据表达不出来 | 一个 Java 类 + §4.1 登记 | **登记了**才算合规 |

⭐ **可验收的标准**：**「新增一个角色机制时，引擎目录下应该一行都不用改」** ✓。
改不动就是**层定错了**或**引擎缺能力** ✓ —— 两条路的做法完全不同 ✗。

### 3.3 四道闸门（**每一批都必须过**）

```powershell
# 1) 全量测试 —— 必须报出 套件数 / 用例数 / 失败数
#    ⚠⚠ 必须带 --rerun-tasks：不带它时 Gradle 会报 `Task :test UP-TO-DATE` 并把**上一次的
#    XML 原样写回**（几秒就结束）⇒ 你读到的是一份**看起来全新**的旧结果。
#    判据是 Gradle 那一行写着 executed（真跑要十几秒），不是 XML 的文件时间。
.\gradlew.bat test --rerun-tasks --console=plain

# 2) 全部 demo 输出，与基线**内容级**对比（不是哈希，见 §6.3）
.\gradlew.bat run --console=plain -q                     # battle   —— 三对三整场
.\gradlew.bat run --console=plain -q --args=mechanics    # 四幕机制演示
.\gradlew.bat run --console=plain -q --args=technique    # 带秘技开局
.\gradlew.bat run --console=plain -q --args=driven       # 无头驱动（BattleDriver）
.\gradlew.bat run --console=plain -q --args=all          # 全部
# 基线：改动前把同样几条各存一份，⚠ 每批做完还要「跑两次互比」自检闸门本身。

# 3) 内容零丢失（搬迁 / 重构才需要）：原文件每个非空行仍在新文件之一里
# 4) javadoc 引用审计：拆分类会让同文件 {@link #x} 静默失效
```

- ⚠ **闸门 2 必须自检**：先用**同一份代码**跑两次互比，必须 MATCH ✓ ——
  否则你不知道这个闸门会不会**对任何输入都报警** ✗。
- ⚠ **不要写"看起来对比了"的脚本**（§1.4）：对比脚本要**打印出判定行** ✓，
  且**元素缺失要报出来**（归并成集合后比较，**不能只比行数**）✗。
- ⚠ **闸门 4 现在是"硬"的**（**2026-10-18 起**）：原先 `failOnError = false` ✓，所以 `BUILD SUCCESSFUL` **不是**判据 ✗，判据是"输出里有没有那一行" ✓ —— 那靠的是**人去看** ✓。现在改成：
  ```groovy
  options.addStringOption('Xdoclint:html,reference,accessibility,syntax', '-quiet')
  options.addStringOption('Xwerror', '-quiet')
  failOnError = true
  ```
  ⇒ **javadoc 报错就构建失败** ✓ 且 `-Xwerror` 把**警告也当错** ✓（`Task :javadoc` 真跑过：`BUILD SUCCESSFUL` ✓）；⚠ 输出里的 `WARNING: ... lombok.permit.Permit` 是 **JVM/lombok 的**，不是 javadoc 的 ✓，别把它当成闸门报警 ✓。
  ⚠ **仍开着的口子（成本已量，2026-10-18）**：doclint 的类别列表**不含 `missing`** ✓ ⇒ "public 成员少了 `@param` / `@return`" **不算错** ✗。临时把它加进去跑了一次：**全部落在 `beans/` 下那些 Gson DTO record 的规范构造器上** ✓（`CharacterData` / `MonsterConfig` / `RelicSubAttribute` / `WeaponData` 等，Gson 映射的 DTO ✓），每条是"某字段没有 `@param`" ✓，**没有一处**出现在引擎/触发器代码里 ✓ ⇒ 这不是"注释烂"，而是**DTO 的 `@param` 要不要写**这个取舍 ✓。⇒ 想开就把这些文件的构造器各补一行 `@param <字段>` ✓（机械、量大、价值低 ✓）；**在那之前**保持现状，并记下这个口子是**已知且量化过的** ✓（不是"没发现" ✗）。
  判据是**输出里有没有那一行** ✓；且它**对 `{@link}` 有效、对 `{@code X.md}` 完全瞎** ✓（§6.5）。
- ⚠ **闸门跑一半要说清楚** ✓：只跑了闸门 1 就报「**绿只覆盖测试，不覆盖 demo 形状**」✓，
  **不许含糊成"全绿"** ✗。**没有对象**时（例如本批没搬迁）明写「**不适用**」✓，不要空转凑数 ✗。

⚠ **无头驱动（`BattleDriver`）是独立的一条路径** ✓，有自己的入口与独立用例
（`BattleDriverTest` / `BattleDriverRosterTest` / `BattleDriverTechniqueTest`）✓。
它曾**绕过收费点 / 技能点收两次 / 破韧照常行动**三个形状的偏差 ✓（§6.8(b) 有记录 ✓）
⇒ **改到 `Battle` 的公共口子时，这条路径要单独想一遍** ✓，别假定「demo 绿了就等于它也对」✗。

⚠ **「跑绿了」不是结论** ✗。要能说清**这次改动为什么不会改变行为** ✓：

- **纯搬迁** ⇒ 用「**行多重集不变**」证明（0 行丢失、0 行新增）✓
- **语义改动** ⇒ 必须指名**哪条用例**覆盖它，并说明**变异后它会不会红** ✓

### 3.4 三层结构的现有范式（新东西照这个来）

| 轴 | 抽象 | 注入点 | 为什么这样分 |
|---|---|---|---|
| **个人**资源（能量条） | `EnergyProvider`（5 个钩子） | `CanHit.energyProvider` | 能量条是**个人**的 |
| **队伍**资源（战技点） | `SkillPointPolicy` | `Battle.skillPointPolicy` | 战技点是**全队共享**的 |
| **通用队伍级数值** | `Resource`（有界 + 显式溢出 + 原子消耗） | 由上面两者持有 | 战技点与层数资源共用 |
| **技能类型判定** | `SkillCategory` 枚举 | `SkillData.getCategory()` | 消灭裸字符串 `switch` 的**静默失配** |

**验收标准**：新增一个角色机制时，**引擎目录下应该一行都不用改** ✓。
`SkillPointPolicyExtensibilityTest` 就是这条标准的样板 ✓【已把守：`SkillPointPolicyExtensibilityTest`】。

### 3.5 落地纪律

- **机制先行、角色当验收** ✓：每补一个引擎能力，顺手数据化 1–2 个代表角色 ✓，**不做全量** ✗。
- **写完必须变异验证** ✓：把源码改回旧行为，确认新测试**真的会红** ✓。
- **文档承诺必须有测试兜着** ✓ —— 只写在 javadoc 里的承诺会腐烂 ✓。
- ⚠ **警惕"假前置"** ✓：追加攻击曾被认为「数据里没有 `is_follow_up` 字段所以判断不了」✗ ——
  真相是**不需要那个字段** ✓（payload 本来就在天赋槽自己的技能数据里 ✓，
  唯一缺的是"**什么时候**放"，而那正是触发器表提供的 ✓）。
  ⇒ **动手前先确认"缺的那个东西"真的缺** ✓，否则会为不存在的前置造能力 ✗。

### 3.6 核心类与口子速查

> ⚠ **完整的机制说明没有第二份文档** ✓ —— 老 `engine.md` 已删 ✓。
> 分工是：**本文件说"还要做什么"与"以前踩过什么"，代码说"现在是什么"** ✓。

| 类 | 关键口子 |
|---|---|
| `Battle` | `applyDamage`（**唯一结算入口**）/ `performAction` / `useSkill` / `castUltra` / `startBattle` / `stepForward` / `beforeMove` / `afterMove` / `processRequests` / `grantExtraTurn` / `heal` / `grantShield` / `skillPointPolicy` |
| `models.Damage` | `toValue()` / `breakdown()` / 7 个乘区 accessor / `addBoost` 等装配口 / `crit` / `trueDamage` / `notCountsAsAttack` |
| `models.CanHit` | `getAttribute` / `takeDamage` / `heal` / `getBuffManager` / `getLevel` / `getCamp` / `getEnergyProvider` / 事件转发 |
| `models.Resource` | `gainClamped` / `gain`（显式溢出）/ `spend` / `spendExactly` / `setMaxOverflow` / `isFull` / `isCapped` |
| `models.skillpoint.*` | `SkillPointPolicy`（接口）/ `StandardSkillPointPolicy`（基础规则 + `gainForCast` 覆盖点） |
| `SkillData` | `getElement()` / `getEffect()` / `getStanceList()` / `getSkills()`（倍率表）/ **`getCategory()`** / `getSpNeed()` |
| `Queue` | `move` / `setTopZero` / `delayAction` / `advanceActionByPercent` / `refreshSpeed` / `grantExtraTurn` |
| `utils.CharacterFactory` | `create(cid, level)` / `usesSpecialResource(cid)` / `exists` |
| `models.EnemyFactory` | `create(monsterId, level, hardLevelGroup)` |
| `utils.StageFactory` | `load(stageId)` |
| `Constant` | `CHARACTERS` / `WEAPONS` / `SKILLS` / `SKILL_TRACES` / `MONSTERS` / `SKILL_SLOT` / `stages()`（懒加载） |

⚠ **拆分的刀口被不变量钉住**：`DEALING_DAMAGE` 公告**在装配中途**发射 ✓；
`fireTriggers(…, Damage, …)` 与 `assemble` **必须保持 private** ✓
【已把守：`DamagePipelineTest`（数"公共方法里收 `Damage` 的恰好 1 个"）】。
解释器已拆出五个 —— `TriggerVocabulary`（合法拼写的封闭集合）/ `TriggerValidator`（装载期校验）/
`TriggerTargets`（选择器 → 打到谁）/ `TriggerMagnitudes`（scale → 多少）/ `TriggerEffects`（op → 做什么）✓；
`Battle` 拆出 `BattleDamage`（伤害六乘区装配）✓；`TriggerTable` 拆出 `TriggerConditionParser` ✓。

### 3.7 全局约定（写代码时永远遵守）

| 约定 | 内容 | 把守 |
|---|---|---|
| **常量** | 所有数值（回能、击破基数、clamp 上限、比率）一律进 `Constant` ✓，代码里不写魔法数字 ✗ | 【已把守：`ConstantImmutabilityTest`】 |
| **随机数** | 暴击 / 目标选择一律走注入的 `java.util.Random` ✓，**禁止 `Math.random()`** ✗ | 【只靠散文】 |
| **测试** | JUnit 5；断言 `assertEquals(expected, actual, 1e-6)`；放 `src/test/java/com/laosun/aluminium/test/`，命名 `XxxTest` | 【只靠散文】 |
| **数据** | 真实数据只从 `data/*.json` 经 Gson 读 ✓，不硬编码 ✗；测试/演示里允许手写常量并标 `// TODO data` | 【只靠散文】 |
| **单段单目标** | `Damage` 永远是「单段·单目标」✓；一个技能 = N 个 `Damage`（AOE→每敌 1 个；BLAST→主目标+相邻；BOUNCE→循环 N 次） | 【只靠散文】 |
| **判别口诀** | **有伤害要结算 ⇒ 构造 `Damage`；不造 `Damage` ⇒ 无伤害** ✓（治疗/护盾/回能/上 Buff 不构造 `Damage`） | 【只靠散文】 |
| **每段独立** | 每段伤害独立判定暴击、独立结算 | 【只靠散文】 |
| **判断失败** | **数据错误要 fail fast**（如伤害技能缺元素）✓；**数据不认识要降级**（未知 `attack_type` → `UNKNOWN` 而不是抛异常）✓ | 【只靠散文】 |
| **单位** | 内容里的数字必须用**引擎自己的单位** ✓ | 【已把守：`UnitDisciplineTest`】 |
| **内容不重复** | 同一件事不许在两处登记 | 【已把守：`ContentDuplicateGuardTest`】 |
| **效果键** | 触发器的 effect key 有封闭词表，拼错即失败 | 【已把守：`EffectKeyGuardTest` / `EffectKeyDisciplineTest`】 |
| **幅度：份额还是绝对值** | 判据是**有没有 `scale`** ✓ —— 有 scale（「等于 X% 的防御力」）= 已经是该属性的**绝对值** ✓；没有 scale 的**份额**（字面 `percent` 或其四种拼法）= **目标基础值的分数** ✓；纯 `amount`（「速度提高 50 点」）= 绝对值 ✓。⚠ 曾经的错法：**无 scale 的份额拼法**被当成绝对值 ✗（1002 秘技成了 +0.4 **点**攻击 ✗） | 【已把守：`DanHengLevelSourceTest`、`ReasonOdeEruditionAttackTest`、`TrueKnowledgeSkillDamageTest`】 |
| **字段名大小写** | `EffectSpec` 的字段**多数没有** `@SerializedName` ⇒ JSON 里必须写**驼峰**（`amountFromEvent` ✓）；写蛇形会被 Gson **静默丢掉** ✗（2026-10-19 又踩一次：写了 `amount_from_event` ✓ —— 这次被装载期喊住 ✓，见 §3.7zd） | 【已把守：`TriggerTables.load` 的封闭键表（报 `unknown key`，不是静默 ✓）】 |
| **伤害类型作用域** | 只在声明消费它的 op 上生效 | 【已把守：`DamageTypeScopeValidationTest`】 |
| **集合顺序** | 打印 / 遍历顺序要确定 | 【已把守：`DeterministicCollectionOrderTest`】 |
| **技能等级表边界** | 等级越界要有定义 | 【已把守：`SkillLevelTableBoundTest`】 |
| **读参数行只准走一个解析器** | 任何读参数行的地方都用 `CanHit.skillLevel` ✓（角色按 `SkillType`、忆灵按 data slot ✓）；写死行号 / `getFirst()` ✗ —— ⚠ 由**描述**驱动的那几个读法（弹射的份额与次数、Debuff 基础概率）曾是例外 ✓，在数值恰好平坦时**完全隐形** ✗（2026-10-17 实测：1004 瓦尔特 的弹射每段读 0.36 而第 10 行是 0.72 ✓） | 【已把守：`BounceSkillLevelTest`、`DebuffChanceDataTest.theLevelSelectsTheRow`】 |
| **份额的分数** | `percent` 与**一个**行／计数份额**同时写** = 「**那个份额的** X%」✓（读者：1206「原伤害的 **50%**」✓ —— 那一份是 `SKILL:1`，E5 抬级后仍对 ✓）。⚠ **两条行份额**仍然互斥 ✓（一行与一个计数器不能都当份额 ✓）；⚠ 这个口子只在**伤害路径**开 ✓（`requireDerivedScale(…, percentMayScaleRow)` ✓） | 【已把守：`PercentFromSkillParamTest.aPercentBesideARowShareIsAFractionOfIt`、`SushangExtraRollFollowsTheRowTest`（E0／E5 两端）】 |
| **能量：固定还是可被效率放大** | `GAIN_ENERGY` 的 `amount` 走**能量恢复效率** ✓；写 `"fixed": true` 就是**「固定恢复」** ✓（`EnergyGain.fixed` ✓，速率**不参与** ✓）。读者：光锥 23055「为装备者**固定**恢复 #2 点」✓、1014 天赋「每消耗1点**固定**恢复」✓ | 【已把守：`Cone23055Test.theEnergyIsFixedNotScaledByTheRate`（把效率设成 50%：固定读 10，按效率会读 15）】 |

### 3.7b–3.7zd 已落能力索引（**原来逐条一节的记录，2026-10-19 压成索引**）

> ⚠ 这些是**已经修好/已经落地**的东西 ✓ —— 每条只留**不变量 ＋ 读者 ＋ 把守** ✓。
> 为什么不再逐条叙述：§4.5 说"现在是什么"的权威是**代码**，而每条规则自带的 `note` 与判据已经把细节写在最近处 ✓；
> 本文件只该留下**找不回来**的那一半（形状、教训、还开着的口子）✓。**仍开**的缺口汇总在 §4.3 ✓（本节只留「这条从句还差什么」的那半句 ✓）。

#### 3.7b 数据从 tbgd 生成（生成器的四处缺口已补）
`hp_modify_ratio` 读 `HPModifyRatio`（曾读 `AttackModifyRatio` ⇒ 1381 只怪的血量倍率错 ✓）· `skill_effects.json` 改成**合并**（保住人工行 ✓）· 写 `bp_add`（强化普攻回战技点 ✓）· 并入忆灵技能（`AvatarServantSkillConfig` ✓）。
新增表 `skill_max_hits.json` = 官方最大命中次数 ⇒ **段数的长度是数据** ✓【已把守：`SegmentCountAgreementTest`】；**分裂比**仍只能从文档的分段表来 ✓。生成器在仓库外（`E:\code\python\generate_data.py`）✓。

#### 3.7c 实例级 `MODIFY_ATTR` 只读 `percent`
已修：份额 / 派生 / 扁平 `amount` 收进 `TriggerMagnitudes.magnitudeOf`，`cap_amount` 生效 ✓。读者：1512【Fever】的无视防御 ✓【已把守：`FeverDefenceIgnoreTest`（观测口是结算后的 `DefenceArea` ✓）】。
⚠ 两次**错误方向**（`amount 15 + percent 0.5`、`amount 0.15 + percent 0.005`）都不是真的，病在实例级分支 ✗。

#### 3.7d `SUMMON_ATTACK` 只在 `EnemySkill` 上触发
已修：`SkillExecutor` 收尾时若执行者是 `Summon` 且命中，也 fire ✓（与 `EnemySkill` 两条路径不重叠 ✓）。读者：光锥 23042 ✓【已把守：`Cone23042TriggerTest`】。

#### 3.7e 加性易伤（原「忆灵光环为什么没落地」）
**已落** ✓：`MODIFY_DAMAGE_TAKEN` 收 `coexist: true`（`BuffManager` 的 `keepsSiblings` 两个方向都护住 ✓，见 §3.7l 的同一条注释）✓。读者：1512 忆灵光环（逐成员 0.112/0.168/0.224 ✓）＋ 光锥 23042（0.18…0.36 ✓），与捕获的 `all_damage_type_taken_ratio = 0.64 = 0.36 + 0.28` 逐位吻合 ✓。
⚠ 当时那两个数（1.2230 / 1.112）**不是**"光环算了两遍" ✓：`1.112 × 1.1 = 1.2232` 里的 1.1 是**韧性减伤从 0.9 变 1.0**（忆灵那一发把目标打进了击破 ✓）⇒ 夹具两侧的韧性状态不同 ✓（§6.3·3「前提不许被抽走」的又一例 ✓）。
**仍开**：光环**不随忆灵离场撤下**（与 3.7l 同一缺口，`SUMMON_LEFT` 已能补 ✓ 只是没写 ✓）。

#### 3.7f `HEALED` 只在**真的回了血**时发（早先的「缺口」是满血探针假象）
不变量：满血治疗**不发** `HEALED` ✓【已把守：`HyacineTalentTest`（先扣血再治 ✓）】。教训并进 §1.4/§1.7：**探针的前提本身就是变量** ✓。

#### 3.7g 仍开：技能数据里的「消耗生命值」没有被执行
`141302` 写明「消耗长夜月当前 #6(10)% 的生命值召唤忆灵」，实测施放前后 `currentHp` **一模一样**、`HP_LOST` / `HP_CONSUMED` 计数都是 0 ✗ ⇒ 引擎没读这一项代价 ✓。要的能力：**执行技能数据里的生命消耗**，按 `consumeHp` 的语义发 `HP_CONSUMED`（不发 `HP_LOST` ✓ 引擎的区分是对的）。

#### 3.7h 忆灵的多段（分裂比 ＋ 段数都从上游数据来）
落地：`skill_effects.json` 的 `segments` ✓（11413 = `[0.1×5, 0.5]` ✓，总份额不变、变的是**六次独立结算** ✓）【已把守：`EvernightServantSegmentsTest`、`SegmentCountAgreementTest`】。
⚠ 「捕获 5 段」**撤回** ✓：那是 `capture-flow` 按 `raw_damage_before_modifiers` 分组的伪影 ✓（已改成按 `attack_type` 分组 ✓）。
**仍开**：① 「每有 #3 点【忆质】额外造成 #2%」游戏是**并进那六发**（总份额 0.6→0.9 ✓），引擎没有这条口径 ✗；② 演示/回合路径仍走忆灵规格里的单段攻击（`COMMON` ✓），换成数据技能会改 demo 输出 ✓ 属另一件事 ✗。

#### 3.7i 两段式强化普攻（一行多段、各段自己的目标形状）
落地：`skill_effects.json` 的 **`damage_parts`**（每段 `shape` ＋ `index` ✓，按表顺序结算 ✓，削韧各取自己的列 ✓）【已把守：`CyreneEnhancedBasicTest`、`CyreneEnhancedBasicPartsTest`】。
**仍开**：① 各段**削韧**未核（`stance_list` 给 single 30 / all 15，游戏 `Phase02` 写死 15 ✓ 两种读法都无更强证据 ✗ 等含强化普攻的捕获 ✓）；② 强化普攻「获得 **3** 点【追忆】」现在由 `basic_recollection` 无条件给 **1** 点 ✓ ⇒ 需要按 `from_skill_id`（1 vs 8）分两条 ✗（没做 ✓）。

#### 3.7j 复刻流程这条线
| 面 | 证据 |
|---|---|
| 单次命中全链算术 | `veritas-085418.json`（82 事件）→ **81/81 逐位复现**（1 条击破按原因跳过 ✓） |
| 出手流程 | `capture-flow`：13 次施放 ＋ 4 次派生 ＋ 1 次击破，覆盖 82/82 ✓【已把守：`CaptureFlowTest`】 |
| 减防 / 易伤 / 增伤 | 1512【Fever】、光锥 23042、忆灵光环、1413【至暗之谜】、1409 天赋、1415 结界 24% 真伤 ✓ 全部由**规则**产生 ✓ |
| 面板自算 | ✗ 等用户给遗器（主词条 + 副词条）；现在面板取自捕获 ✓ |

#### 3.7k 1409【雨过天晴】按行读 ＋ 星魂 2 的 +50% ＋ 星魂 3 的终结技 +2
`percent_from_skill_param: ULTRA:2` ＋ `max_stacks: 2`；E2 的 `HEALTH 0.5` 与它**相加**（0.33 ＋ 0.50 = 0.83 ✓，固定项只加一次 ✓）；E3 的 `RAISE_SKILL_LEVEL ULTRA +2` ⇒ 终结技 Lv12 ✓【已把守：`HyacineRainClearMaxHpTest`（钉 E0 与 E6 两端 ＋ `skillLevel(ULTRA)` ✓）】。

#### 3.7l 1409 星魂 6 的「小伊卡在场时，我方全体抗性穿透 +20%」
三条口径（都量过 ✓）：**门是事件不是读盘**（`SUMMONED` ✓，第一句断言就是"召唤前读到 0" ✓）· **`actor == summon` 不能省**（`SUMMONED` 对**两方**都发 ✓）· **`all_allies` 含忆灵** ✓（判据同时读主角/队友/忆灵 ✓）。`DAMAGE_PENETRATION` 是**我方**属性 ✓（不是 `RESISTANCE_REDUCTION` ✗）。【已把守：`HyacineEidolonSixTest`、`HyacineEidolonSixLateSummonTest`】
⚠ **仍开**：忆灵**离场后**光环要撤下（`SUMMON_LEFT` 已落地 ✓，见 3.7w，规则已写 ✓）；星魂 6 前半的 **12% 阈值**改的是忆灵技 1 的 `#2` ✓（那条忆灵技现在有规则了 ✓ 见 3.7q/3.7r）。

#### 3.7m 1409 行迹【暴风停歇】＋ 星魂 4（速度 200 的三条从句）
五条规则（生命 +20% ／ 每点超速治疗 +1% · `max_points: 200` ／ 忆灵同享 ／ 星魂 4 的暴伤每点 +2% ／ 忆灵同享 ✓）。三处判定：**严格大于 200** ✓（200 整点给 0 ✓）· **小伊卡的面板不另写**（它 = 她的面板 × 50%，再写就是双算 ✗）· **`coexist` 是量出来的** ✓（去掉它秘技那条 +20% 被顶掉 ⇒ 该用例红 ✓）。
【已把守：`HyacineWindstormTraceTest`（6 例：0 / 0.5 / 2.0 / 2.0 封顶点在**点数**上 ✓）】
⚠ 仍开：这 5 条都在**读盘那一刻**求值一次 ⇒ **战斗中途**越过 200 速度不会打开 ✓（没有"速度变了"事件）；忆灵的**治疗**那一半仍不可观测 ✓。

#### 3.7n 1409 忆灵技 3【展翼，奔向日辉】（召唤回能）
`GAIN_ENERGY 15`（每次 ✓）＋ `GAIN_ENERGY 30` ＋ `once_per_battle`（首次 ✓）**两条规则**：给 `amount` 加"第一次才加"的开关不存在 ✗。「首次」= `once_per_battle` ✓ 不是 `cooldown` ✗。【已把守：`HyacineMemospriteSummonEnergyTest`（首次 +45 / 再次 +15 ✓）】

#### 3.7o 1409「本场战斗累计治疗」的计量
两条 `HEALED` 规则（`actor == self` / `actor == summon` ✓ 各管一方 ✓，因为条件只能 AND ✗）、`GAIN_RESOURCE` ＋ `amountFromEvent` ✓。与 1415 那条「额外提高」**相加**才是计入忆灵技 1 的数值 ✓。【已把守：`HyacineHealingTallyTest`（他人治疗不进账 ✓）】
⚠ `Resource` 是**整数** ⇒ 计量只留整点（实测 255.62 → 256 ✓），落到忆灵技 1 的伤害上 < 0.28 ✓。

#### 3.7p（引擎）`scale: "self_resource:<id>"`
以**单位自己的计数器**为倍数（`party_resource:` 是队伍的 ✓ 两个不同问题 ✓；计数器不存在时**大声报错** ✓ 不是 0 ✗）。判据：`SelfResourceMagnitudeTest`（0.5 与 0.25 差两倍 ✓、计数器不存在时消息带名字 ✓）。

#### 3.7q ／ 3.7r 1409 忆灵技 1 整句闭合
- 伤害那一半：`DAMAGE` ＋ `scale: self_resource:小伊卡累计治疗` ＋ `percent_from_cast_param: 0` ✓。
- 「清空 **50%**（星魂 6 → **12%**）」：`REMOVE_RESOURCE` 收 `scale: self_resource:X` ＋ `percent` ✓（两条孪生规则用 `max_eidolon`/`min_eidolon` 分档 —— 见内容文件 ✓）。
【已把守：`HyacineRaincloudsTest`（3 例：伤害 = 计量 × #1 × 天赋层数 ✓、扣完剩 50% ✓、星魂 6 扣完剩 **88%** 而不是 44% ✓）】
⚠ 仍开：规则那一发的攻击者是**风堇**（`attacker: "summon"` 已能改 ✓ 见 3.7s）、数据侧仍残留 **1 点**地板（`skill_effects` 已声明 `Rules` ✓ 见 3.7v ③）。
⚠ 3.7r 末尾的教训（**按行号手术 ⇒ 留下重复的 `on` 键 ⇒ Gson 取后者，行为对而文件错** ✓）并进 §6.6 ✓。

#### 3.7s（引擎）`"attacker": "summon"`
规则那笔伤害可以**算忆灵的** ✓（没有忆灵时**大声报错**并给 `self_summon_count >= 1` 的建议 ✓）。`EffectSpec.copy()` 必须同步带该字段 ✓（`RuleEffectAmendmentTest.theCopyCarriesEveryField` 是它的把守 ✓）。

#### 3.7t 撤回：星魂 2 的速度**是可观测的**
错的是那条判据（读错了单位 ＋ 绕过了事件，`CanHit.takeDamage` **不发** `HP_LOST` ✓）⇒ 规则**一个字没改** ✓。全库现在 **0 个 `@Disabled`** ✓。教训并进 §6.3·1（「观测不到」先怀疑观察方法 ✓）。

#### 3.7u 仍开：`skill_segments.json`（全角色/忆灵的分段表，已勘未接入）
仓库外 `E:\code\python\data\skill_segments.json` ✓（98 角色 ＋ 7 忆灵、311 phase、668 hit ✓）。可信度锚点已量：它把 1002 的两条手写值**逐位复现** ✓。接入规则：按 `slot_guess` 归位 ✓、只对 `hit_count > 1` 出行 ✓、**先验 `split_ratio_sum == 1.0`** ✓（和不为 1 要**拒绝**，不是归一化 ✗）。⚠ 一次失败的尝试（PowerShell 摊平数组 ⇒ 数字不可信 ✓）并进 §6.6。

#### 3.7v 1409 其余三块的落点（已全部收口）
① 忆灵技 2 的「生命值降低的那个我方目标」⇒ 选择器 `all_allies_lost_hp_this_action` ✓（照抄 `all_allies_lethally_hit_this_action` ✓）见 3.7z；② 忆灵技 4 的「消失时提前 30%」⇒ `SUMMON_LEFT` ✓ 见 3.7w/3.7y；③ 忆灵技 1 的 1 点地板 ⇒ `skill_effects` 声明 `Rules` ✓ 见 3.7q。

#### 3.7w（引擎）`SUMMON_LEFT`
「忆灵离场」这个事件 ✓（`CanHit.perish()` **不发** `DEATH` ✓ 那是被击杀 ✓）。在**清扫点**发、用**身份**去重（一次离场只发一次 ✓）。⚠ 门的写法是 **`actor is_summon`**，**不能** `target == summon` ✗（后者按 `isDeath()` 过滤，而这条事件的单位**正是刚死的那个** ⇒ 当场抛错 ✓）。【已把守：`HyacineMemospriteLeavesTest`】

#### 3.7x 订正：`advanceRequests` 是没人填的死通道（已删）
「入口已经存在」这种判断**必须跟到调用点** ✓。同一形状出现两次 ⇒ 并进 §2.2（×2 → 后并入 6.2 的"半对结论"一条 ✓）。

#### 3.7y 忆灵技 4 的「行动提前 30%」有了真判据
读数早就有：`Queue.snapshot()` ＋ `Queue.getTimeRemaining(Signal)` ✓（"顺序变了"是弱断言 ✗）。判据比的是**剩余等待 × 0.7** ✓（精确等式，因为战斗时钟不动 ✓）。【已把守：`HyacineMemospriteLeavesTest` 第 2 例】

#### 3.7z 忆灵技 2 第一从句（掉血治疗）
选择器 `all_allies_lost_hp_this_action` ✓ 落在 `HP_LOST` **之前**填 ✓（规则要**当场**读到它 ✓）。⚠ 当场撤回一次"付两次"的结论 ✓：那是**我的算术**错了（`Damage` 会过防御与抗性，20% 生命上限 ≠ 实际掉血量 ✓）⇒ 正确量法是**两场只差一个变量**、用差值读 ✓。【已把守：`HyacineIkachisCharityTest`】

#### 3.7za 忆灵技 2 其余两个从句 —— 剩**一个词**
②「额外回合 ＋ 自动施放忆灵技 1」**已落** ✓：`INSERT_ACTION` ＋ `CAST_SKILL skill_id: 1`（`skill_id` 是数据槽 ✓ 校验器允许「`skill` 或 `skill_id` 二选一」✓）；⚠ 不能拆成两半（没有自动施放，额外回合就是个空转的回合 ✗）。
③「施放后使自身所有持续效果 −1」**仍开** ✗：`EXTEND_BUFF` 收负数与 `kind: all` 都支持 ✓、规则装载得过 ✓，但**判据立不起来**（给忆灵挂 3 回合状态、放一次技能后就找不到了 ✓）⇒ 一次行动里扣了 **2**，来源不在一处（不是 `tickBuff` 双扣 ✗）⇒ **下一步是一枚探针**：数一次忆灵行动里 `beforeMove` / `afterMove` 各跑几次 ✓。⇒ 规则与判据**一起撤下** ✓。

#### 3.7zb 整场重放（把捕获的每次施放喂给 `Battle`）
`gradlew run --args="replay scenarios/veritas-085418.replay.json"`（`--detail` / `--strict` ✓），代码 `compare/BattleReplay` ✓。三件事原本就够：**施放→槽位**只能人工表（捕获只给 `attack_type` ✓，且表不许自由漂 ✓）、**敌人属性**用捕获自己的数字 ✓、**脚本驱动**早就有（`Battle.castImmediate` ✓ —— 缺的从来不是"能施放" ✗ 是"这是哪一招" ✓）。
⭐ 它开箱抓到一个真 bug：`actor is_summon` 会把**别人的**忆灵也算进来 ⇒ 六条门改成 `actor == summon` ✓【已把守：`HyacineOwnMemospriteOnlyTest`（4 例）】
**仍开（对账差异，逐条登记）**：窗口前缀（捕获是一段已经在打的战斗 ✓）· 忆灵的「%生命上限」读主人还是自己（这份捕获**分不开** ✓）· `11512` 槽 1 的倍率在参数 **#2**（引擎读 #1 = 0 ✓）· 忆灵技的行号（捕获像 Lv8/Lv6，规格写 10 ✓）· `skills.json` 缺「长夜」的强化普攻（槽 8/11 ✓）· 敌人没有韧性条（不加那 10% 减伤 ✓）。

#### 3.7zc 1413 忆灵技 1 的「施放后获得 #4 点【亿质】」
`CAST_SETUP` ＋ `actor == summon` ＋ `from_skill_id == 1` → `GAIN_RESOURCE 亿质 amount_from_cast_param: 3` ✓（点数**读这一发自己的行** ✓）。⚠ 资源名是数据里的「**亿质**」不是「忆质」✗（写错只会得到一个恒 0 的计数器 ✓）。【已把守：`EvernightMemoryGainTest`（他人的忆灵施放一点不给 ✓）】
⚠ 仍开：「每有 #3 点【亿质】额外造成 #2%」游戏是**并进那六发**的 `DamagePercentage` ✓（同 3.7h）。

#### 3.7zd（引擎）三个「句子缺一个读数」的补口
| 能力 | 之前为什么写不出来 | 现在是什么 | 判据 |
|---|---|---|---|
| `ATTACK_FINISHED` 带 `from_skill_id` | 它传的 `skillId` 是 **0** ⇒ 无法说「施放**那一招**的攻击后」 | `fireAfterAttack` 新增带槽位的重载 ✓；四参数旧口子 = **未知**（`EnemySkill` 与既有判据仍在用 ✓） | `PhainonEidolonSixTest` |
| `amountFromEvent` 作 `DAMAGE` 基数 | 字段早就有，**DAMAGE 路径没读者** ✗；`settled_damage_this_event` 在最外层 firing 清零 ⇒ 读它恒 **0** ✗ | `literalBase` 读 `amountFromEvent × percent` ✓，装载期**只准配真伤害** ✓。⚠ **不是**新造 `scale: "event_amount"` ✗（它被**撤回**过 ✓） | `EventAmountTest.theAttackTotalCanBeTheBaseOfATrueRider` |
| `RAISE_RESOURCE_CAP{unbounded: true}` | 溢出额度是**一个数**，而「不再拥有上限」不是一个数 ✗（`MAX_VALUE - 3` 会在 `+raise` 里溢出成负数 ⇒ 夹回 0 ✗） | 额度置 `Integer.MAX_VALUE` ✓（与【毁伤】的 `max: 2147483647` 同一约定 ✓）；两个拼法互斥 ✓ | `ResourceOverflowUnboundedTest`（两端 ✓） |

⚠ 同族仍开：**单位计数器的「这次花掉了多少」没有事件** ✗（唯一的「花掉」是 `SKILL_POINT_SPENT` ✓ 战技点专属 ✓）⇒ 1408 星魂 2 的后半写不了 ✓（不拿 `self_resource:毁伤 >= 4` 冒充 ✗：那是「持有 4 点」✓）。

### 3.8 数据文件速查

| 文件 | 内容 | 已确认锚点 |
|---|---|---|
| `monster_config.json` | 怪物实例：`monster_id → {name, template_id, elite_group, hard_level_group, stance_weak, *modify_ratio, damage_resistance, debuff_resistance, summon_id}` | **1002011 冰锋**：弱 `[Fire, Thunder]`，抗 5 项 0.2，`STAT_CTRL_Frozen=1`；**1003010 银鬃尉官**：`summon_id = [1002040, 1002040]` |
| `monster_template_config.json` | 模板基础属性 | **1002011**：atk 18 / def 210 / hp 69.75 / spd 100 / stance 60 / 效果抵抗 0.2 |
| `hard_level_group.json` | `{组: {等级: {attack, defence, health, speed, stance, effect_hit_rate, effect_resistance}}}` | 组1 Lv90 `{36.821384, 5.238095, 236.53471, 1.32, 1, 0.32, 0.1}` |
| `breaking_rate.json` | 等级 → 击破基数（**原始 10 倍值**） | Lv80 = 3767.5535 → 公式里 `/10` |
| `skills.json` | `cid → {槽位 → {attack_type, max_level, param_list, skill_effect, stance_list, element, sp_need, sp_base}}` | 槽位 1 普攻 / 2 战技 / 3 终结技 / 4 天赋 / 6 地图普攻 / 7 秘技；**敌人技能不在这里** |
| `character_data.json` | `cid → {name, attribute, mt, max_energy, aggro, ..., rarity}` | 景元 1204：thunder / 能量 130 / aggro 75 / hp 158.4 |
| `stage.json` | `stage_id → {type, hard_level_group, level, monster:[...]}`（**懒加载**） | `103201`：level 29 |
| `monster_attack_modify_ratio.json` | **本项目补丁**（相当一部分怪 ≠1，要数就现数） | 见 §6.1(e) 陷阱 2 |
| `enemy_skills.json` | **自建**，只覆盖演示怪，倍率是猜的 | 见 §6.1(e) 陷阱 7 |
| `skill_effects.json` | **自建**：非伤害技能怎么读参数（`effect` / `scale` / `params` / `turns_param`），外加伤害**段数** `segments` | §4.3 的段数条目 |
| `dump_data.json` | `Benchmark` 用的预计算遗器套表（**唯一读者**，不在 `Constant` 的加载链里） | — |

> ⚠ **2026-10-17 清理过一批"生成了但没人读"的表** ✓：`challenge_*` / `enhanced_*` / `growth` / `materials` /
> `recommend` / `property_names` / `global_buffs` / `character_id_mappings` ✓ —— 全库 `src/main` + `src/test`
> 里对它们的引用是 **0** ✓（`enhanced` 的命中全是"强化普攻"这类**正文**，不是文件 ✓），删掉后
> 全量测试与四个 demo **逐字节不变** ✓。
> ⚠ **它们由仓库外的 `generate_data.py` 产出** ✓ ⇒ 再跑一次生成器会**重新出现** ✓（那些文件也不在 git 里 ✓，
> 所以这次清理只影响本机 ✓）。想让清理**长期有效**，得改生成器的输出清单 —— 那是仓库外的文件，本项目没动 ✓。

### 3.9 已核对的公式结论（**别再重新推导**）

- HSR.md §2.2 伤害修饰区 = 增伤 `1+Σ` × 易伤 `1+Σ`（cap **3.5**）× 减伤 `Π(1-r)`（floor **0.01**） × 虚弱 `1-Σ`（floor **0.2**）
- HSR.md §2.2 / §10.3 **增伤的四个因子是相乘的**（2026-10-17 补齐 ✓）：`(1 + Σ元素增伤 + 全属性增伤 + DoT增伤)` × `(1 + Level_FinalDamageAddedRatio)` × `(1 + ComboDamageAddedRatio)` × `BaseDamageMultiRatio`
  ⇒ 引擎属性 `FINAL_DAMAGE_BOOST` / `COMBO_DAMAGE_BOOST` / `BASE_DAMAGE_MULTI_RATIO`（后两个存**相对 1 的增量**）✓。
  ⚠ 判据钉的是**位置**：`1.2 × 1.3` 而不是 `1 + 0.2 + 0.3` ✓【已把守：`DamageChainFactorsTest`】
- HSR.md §2.2 **易伤上限之外**还有一项：`× (1 + Monster_DamageTakenRatio)`（引擎属性 `MONSTER_DAMAGE_TAKEN`）✓
  ⇒ 已经顶到 3.5 的目标**仍然**吃这一项 ✓（折进上限里会把它吞掉）【已把守：`DamageChainFactorsTest`】
- HSR.md §2.2 **元素吸收是减法**，不是乘区：结算后减、floor 0（引擎属性 `<元素>_DAMAGE_ABSORB`）✓【已把守：`DamageChainFactorsTest`】
  ⚠ 这三组能力目前**没有内容读者**（全库扫过 ✓），是用户要求为公式完整性补的 ✓ —— 判据钉的是**乘/减与上限内外**，不是某个角色的数字 ✓。
- ✅ **已核（2026-10-17）：`_GetExtraDamageRatio` 字段表已拿到** —— 它是**链末独立乘区**（在 `− 吸收` **之后**乘），**不是**主增伤桶 ✓
  `x = (前后排基准 GetFrontBackPower/200) × (1 + 幸运触发时的 ExtraLuckDamage) × (1 + ExtraAllDamageTypeAddedRatio1 + Σ对应攻击类型) × (1+ExtraAllDamageTypeAddedRatio2) × …3 × …4 × …5`
  - 攻击类型槽（`AbilityProperty`，`switch(AttackType)` 累加进**同一个**累加器）：`ExtraNormalDamageAddedRatio1`(203 普攻) / `ExtraSkill…1`(202 战技) /
    `ExtraUltra…1`(201 终结技) / `ExtraInsert…1`(204 附加) / `ExtraDOT…1`(205 持续) / `ExtraElement…1`(206 击破) / `ExtraElation…1`(207 欢愉)
  - ⇒ `BASIC_ATTACK_DAMAGE_BOOST` / `SKILL_DAMAGE_BOOST` / `ULTRA_DAMAGE_BOOST`（及附加/持续/击破/欢愉槽位）**应挪到这个独立乘区**：
    它们**彼此加算**、并与 `ExtraAllDamageTypeAddedRatio1` 加算成**一个** `(1+…)` 因子，**不是**留在主加性桶 ✓
  - ⚠ **`FOLLOW_UP_DAMAGE_BOOST` 例外**：游戏**没有「追加攻击」槽**（该表只有 7 项，无 追加/忆灵/秘技/真实）⇒ 它**不**属于这个乘区，
    **保持原状**，别按"看着像"挪进来 ✓（游戏要让追加攻击吃到普攻增伤，走的是 `RegardAsAttackTypeMap`「视为攻击类型」）
  - 依据：`HSR.md §10.8`（含 `ToExtraDamageDataFinal` VA `0x18E5236F0`、跳表 VA `0x1957CD29C`、`PAKIJFOGOJF` VA `0x1957CD2E0`）
- HSR.md §2.5 抗性区 = `1 - 抗性`，范围 `-100% ~ 90%` ⇒ 抗性区 `0.1 ~ 2.0`；**负抗全效**（不是半效）
- HSR.md §2.4 防御区 `(200+10L)/(def+200+10L)`，`def = 原始防御 × (1 - 减防% - 穿透%)`（**加算**），上限 1、不为负
- HSR.md §5.4 欢愉伤害**吃双爆、不吃增伤** ⇒ `DamageType.ELATION(isCrittable=true, isBoostable=false)`
- HSR.md §6.2 超击破不吃攻击力/常规增伤/双暴，吃等级、击破特攻、削韧值、超击破独立增伤、易伤、防御、抗性、减伤
- HSR.md §6.1 **两套刻度不可混用**：80 级基础击破基数 3767（削韧单位 1）vs 超击破 376.7（削韧单位 10）
- HSR.md §2.2 增伤/易伤区各自都是「伤害类型 + 攻击类型」**加算进同一个区** ⇒「战技/终结技增伤」灌 `BoostArea`，**不新开区**
- HSR.md §4 治疗 `= 基础 × (1 + Σ治疗加成) × (1 - Σ治疗降低)`；护盾 `= 基础 × (1 + Σ护盾量提高)`
- **欢愉伤害**（⚠ 2026-10-16 补：旧 `ROADMAP.md` 里那条锚点**早已失效** ⇒ 这里补上**实测过的**公式 ✓）：
  `基础值 × (1 + 欢愉度 ELATION_DAMAGE_BOOST) × (1 + 增笑 ELATION_DAMAGE_AMP) × 笑点倍率` ✓
  ⚠ **两个因子是相乘、不是相加** ✓ —— 各自 +100% 要变成 **4 倍**（相加会读成 3 倍 ✗）。
  代码：`SkillExecutor` 的欢愉结算与 `TriggerInterpreter` 的同式**两处一致** ✓；
  属性定义：`AttributeType.ELATION_DAMAGE_BOOST` / `AttributeType.ELATION_DAMAGE_AMP` ✓；
  判据：【已把守：`ElationAmpTest`】——`assertEquals(ampFactor × boostFactor, both / plain)` ✓
  就是为"想加的实现会读成 3x 而失败"写的 ✓。

> ⚠⚠ **CORRECTED（2026-10-16）**：曾有一句把**两套机制**混成一句 ——
> 「负数抵抗会被当成 0 而不是增伤」✗。拆开如下，两句**各有自己的代码做依据** ✓：
>
> | | 伤害**抗性区** | Debuff **命中率** |
> |---|---|---|
> | 公式 | 本节的抗性区 | `base × (1+命中) × (1-抵抗) × (1-具体抵抗)` |
> | 负抗怎么算 | **全效**，`RES` 下限 `Constant.RESIST_MIN = -1.0` ⇒ 抗性区**最高 2.0** | **不会超过 1** —— 返回值 clamp 到 `[0, 1]` |
> | 依据 | `Constant.RESIST_MIN` ＋ `Damage` 的抗性区 ＋ `Battle` 的伤害侧调用 | `Battle.hitChance` 的 clamp |
>
> ⇒ 「负抗**不**产生额外收益」**只对命中率那一侧成立** ✓（"必然命中"已是上限 ✓），
> 而**伤害侧恰恰相反：负抗是实打实的增伤** ✓。
> ⭐ 命中率侧的 clamp **上界有判据** ✓【已把守：`HitResistTest.enemyEffectHitRateReachesThePanel`
> （命中 0.32 时返回 **1.0** 而不是 1.32）】；
> ⚠ **下限那一半 `[0, …]` 没有判据、而且够不到** ✗，两条路都量过（2026-10-16）✓：
> ① 具体抵抗**不可能超过 1** ✓（装运数据 `STAT_*` 的取值只有 **0.5 / 0.75 / 1** 这一档，没有更大的值 ✓）
> ⇒ `(1 - 具体抵抗) ≥ 0` ✓；② 唯一能把乘积压到 0 以下的是 `EFFECT_HIT_RATE ≤ -100%` ✓，
> 而这要角色面板先到 −100%，本项目没有任何数据这样做 ✓ ⇒ **登记为未把守** ✗。

---

## 四、角色 B：做项目 / 写内容的

> **谁读**：写 `src/main/resources/` 下 JSON、排下一批做什么、判断"现在到哪了"的人 ✓。
> ⚠ **"现在是什么"的权威是代码** ✓，不是本文件 ✗（§2.4：本文件不写死会腐烂的数字）。
> **怎么量现状**：`git rev-list --count HEAD`（提交数）✓、数 `build/test-results/test/*.xml` ✓、
> 比 `character_data.json` 与 `characters/*.json` 的个数 ✓、跑 `gradlew run` / `--args=all` 的行数 ✓。

### 4.1 分层与逃生舱

**问"引擎有没有这个能力"，而不是"这是哪个角色"** ✓：

| 机制长什么样 | 归到哪 | 例子 |
|---|---|---|
| 在某事件后，做一件**引擎已有能力**的事（加成 / 附加伤害 / 真伤 / 回能 / 上 buff / 额外回合 / 削韧 / 治疗 / 护盾 / 资源增减） | **数据**（触发器 + 条件 + 效果） | 知更鸟「队友每次攻击后 +2 能量」、缇宝「我方每命中 1 目标 +1.5」 |
| 需要引擎**还不具备**的能力 | **引擎任务**（先补能力，再做数据） | 「层数替代能量条」（黄泉/飞霄/白厄/昔涟）、「损血/治疗转资源」（遐蝶） |
| 需要跨系统状态机，数据表达不出来 | **逃生舱**：一个 Java 类 + 下表登记 | 刻律德菈【军功】↔【爵位】双轨 + 目标变更重置充能 |

**逃生舱登记表**（每加一个专用类就往这里加一行 ✓）：

| cid | 角色 | 机制 | 登记于 |
|---|---|---|---|
| 1412 | 刻律德菈 | 【军功】↔【爵位】双轨；目标变更充能重置为 0 | v2 P8-0 |

⭐ **验收标准**：新增一个角色机制时，**引擎目录下应该一行都不用改** ✓（§3.2）。

### 4.2 待办

> ⚠ **`验收` 里提到的测试，有的是"已经写好的守卫"、有的是"做了这件事时才该出现的验收条件"** ✓ ——
> 两者别混 ✗。★ 标的是**现在真的存在**的守卫（2026-10-16 逐个 `Get-ChildItem` 核过 ✓）。

#### B 层：敌人（只有少数怪有技能，其余全是平 A）

| 任务 | 现状 | 验收 | 依赖 |
|---|---|---|---|
| **P9-1** `enemy_skills.json` 扩充 | 自建表只覆盖少数演示怪，**倍率是猜的**（每条 `guessed=true`） | ★`EnemySkillTest`：条目数 / 倍率 / 削韧值与表一致 ✓；无条目的怪走兜底普攻 | — |
| **P9-2** `EnemySkill` 全效果 | 🚧 形状分派已落地 | ★`EnemySkillFullTest`：冰锋战技打 3 人 → 每敌 `attack × 倍率 × 防区` ✓；按 bean 削韧 | P9-1、P5-3、P1-8 |
| **P9-3** 敌方 AI 技能选择器 | 未开始 | `SkillSelectorTest`（**待写**）：权重 1.0/1.2 两技能跑 1000 次 → 次数比 ≈ 1:1.2（±3%） | P9-2、P5-5 |
| **P9-5** Boss 机制（phase 换招 / 受击反击 / 控制免疫） | 控制免疫已是 P6-1 的一部分，**不需要新代码** ✓ | ★`BossMechanicTest`：HP 降到 50% 以下换招 ✓；受击反击段 `getCountsAsAttack() == false` ✓ | P9-2、P1-7、P6-1 |

> ⚠ P9-5 那段的原结论：`Battle.hitChance(caster, target, baseChance, specificResistKey)` 里
> `chance = base × (1 + 效果命中) × (1 - 效果抵抗) × (1 - 具体 debuff 抵抗)` ✓，
> 冰锋的 `{"STAT_CTRL_Frozen": 1}` 让 `(1 - 1) = 0` ⇒ **完全免疫** ✓，与原文逐字一致 ✓，
> **并且已被 `HitResistTest` 钉住** ✓【已把守：`HitResistTest`】。
> ⇒ **不需要 `Enemy.isImmuneTo(resistKey)`** ✓ —— 免疫就是"具体抵抗 = 1"这一种情形，
> 走同一条概率公式即可 ✓。**加一个 `isImmuneTo` 只会是第二套判据** ✗。
> ⚠ **负抗那半边的正确拆法见 §3.9** ✓（伤害侧"全效" vs 命中率侧"不超过 1"，是**两套机制**）✓。

#### C 层：机制补完（全是"填口子"，**无新架构**）

| 任务 | 现状 | 验收 | 依赖 |
|---|---|---|---|
| **P10-4** 速度与行动条操纵 | 🚧 **只差"拉条"那一半** ✓。⚠ 计划里那句"公开 API 还没上移"**已过时** ✓ —— `Battle.delayMovePercent` **已经是 public** ✓ | `SpeedBuffTest`（**待写**）：speed 100 减速 30% → 下次行动间隔 ≈ 142.857；拉条 50% → 提前半圈 | P10-3 ✅、P7-1 |
| **P10-5** 终结技插入 | 未开始 | `UltraInsertTest`（**待写**）：满能量任意时点 `castUltra` true；行动条前后一致；能量 = 5 × (1+回能率) | P3-2、P7-2 |
| **P10-6** Debuff 基础概率数据化 | ✅ **前半**（读得出几率 ✓；"**哪个** debuff"**仍缺数据** ✗） | ★`DebuffChanceDataTest`：标定样本全部对上 ✓ / 计划里那个 index 3 的数值**确实不是几率** ✓ | P6-1、P8-2 ✅（P10-2 ✅） |
| **P10-7** 「按被治疗者的状态，加治疗者的治疗量」 | ✅ **已落地**（= 数据） | — | — |
| **P10-8** 罗刹的【结界】死时解除 | ✅ **已落地**（靠**新事件 `DEATH`**） | — | — |

#### D 层：演示与收尾

| 任务 | 验收 |
|---|---|
| **P11-2** 真实内容演示（冰锋战） | 一遍跑通全链路 |
| **P11-3** 测试总盘点 + 基准 + 零警告 | 全绿 + `Benchmark` 输出 |
| **P11-4** `AutoPolicy` 到底强不强 | ⚠ **旧结论已被推翻** ✗，先按 §6.3 的规矩说清"为什么推翻"再重做 |

#### 远期（只记规格，不排实现）

| 任务 | 现状 | 验收 |
|---|---|---|
| **7.1** 战斗拆分（组装 / 主战斗 / 引擎原语） | **量过了**，第一小刀已落地 ✓（`Battle` 拆出 `BattleDamage` ✓、`TriggerTable` 拆出 `TriggerConditionParser` ✓） | ★`BattleEventRoutingTest` 三条 ✓（当事方 + 全队各一次 ✓ / 兼任两角色**只收一次** ✓ / …） |

### 4.3 登记（**缺能力时来这里，不要近似**）

- ⚠ **缺引擎能力 ⇒ 先补能力，再数据化角色** ✓（§3.2 的逃生舱三选一 ✓）。
- **登记写在哪里** ✓：**规则自带的 `note`** ✓（最贴近那条内容 ✓）或**本节** ✓；**已落**的东西**不留在本节** ✗（它们进 §3.7b–3.7zd 的索引 ✓）。
- ⚠ **每条登记必须能回答三件事** ✓：**读者是谁**（哪条内容要它 ✓）、**缺的到底是什么词/事件**（不是"写不了"这个印象 ✓）、**缺口见证**（一条钉住"今天读数"的判据 ✓，能力落地时它红 ✓）。没有见证就把今天的样子钉住 —— 否则下一个人只能重新推导 ✓。

#### 仍开的缺口

- **`DamageType` 给不出"规则身份"** ✗：`Damage` 上没有规则 id、也没有目标选择器 ⇒ "这条天赋伤害是哪个规则发的"在**类型这一层**区分不了 ✓ ⇒ 不猜 ✓（§6.8(d) 的 TALENT 讨论 ✓）。
- **Impair 的 debuff 派发缺的是一列"哪个 debuff"** ✗：`skill_effects.json` 里一条 `Impair` 行都没有 ✓，而正文的 `陷入【吞火】状态` / `风化/裂伤/灼烧/触电其中1种` 只在散文里 ✓（`SkillData.debuffChance` 已有等级感知，**没有调用者** ✓）【缺口见证（§7.4）：`ImpairDebuffGapTest` —— ① 没有一条 `Impair` 带 effect 行 ✓（有一条就红，说明派发有了输入 ✓）；② 正文点名**战斗内**状态的只有极少数几处，其余是**地图层** ✓（本引擎没有那一层 ✓）】。
- **`ENERGY_GAINED` 只说「谁收到了」，不说「谁给的」** ✗（2026-10-19 核对 ✓）：`fireTriggersForAlly(ENERGY_GAINED, target, target, added)` ⇒ `actor` **就是收能量的那位** ✓，`EnergyGain` 只有 `amount` ＋ `affectedByEfficiency` ✓ ⇒「受到**队友提供的**能量恢复…」判不了 ✗（读者：1408 行迹 1408102 的后半 ✓）。⚠ 不许拿「任何人给我回能」冒充 ✗；也不许把 `actor` 改成授予者 ✗ —— **4 条出货规则押在当前语义上** ✓（1207 星魂 2、1310 天赋、1505 两条）。要开这个口子：给事件加「授予者」事实，并复核那 4 条 ✓。【缺口见证：`PhainonTraceBideInFlamesTest.theEnergyHalfIsStillANoOp`】
- **`from_skill` 的闭集里没有欢愉技（槽 20）与强化普攻（槽 8）** ✗（2026-10-19 实测 ✓）：它只认 COMMON／SKILL／ULTRA／TALENT ✓，其余在装载期被拒（原话「不是一场战斗内的施放」✓ —— 那条守卫防的是秘技/地图普攻，而欢愉槽被归进了同一桶 ✓）⇒ 「**施放欢愉技时**」只能用 **`from_skill_id == 20`** ✓（`CAST_SETUP` 带槽号 ✓，`from_skill_id` 是开放整数 ✓）。读者：光锥 23055 ✓。⚠ 别用 `from_skill` 硬套 ✗，也别把 `ELATION_SKILL` 塞进那个闭集 ✗（它会让「秘技也不产生战斗内实例」这条理由失去一致性 ✓）。
- **没有 `SkillType` 的槽是一族缺口（×3）** ✗：抬等级与读行都按 `SkillType` 说话 ✓，而映射是闭集 ✓（`SkillSlotMappingTest` 钉 1／2／3／4／6／7／20／21 ✓）⇒ 其余槽**既抬不了级、也读不了那一行** ✗（1506 的 8／10／12／18、1408 的 5／9／11 与 `140808`、1206/1015 的强化技能 ✓）。症状两种、都**沉默** ✓：星魂的「＋2 级」什么都不做 ✓；份额跟不上等级 ✓（1408 的 0.8／2.7 取自槽 5 ✓）。⚠ **补 `SkillType` 是决定不是补名字** ✓（要先回答「强化形态算不算同一个槽的等级」✓，上游把它们**并列**列 ✓）。【缺口见证：`SilverWolfLV999ElationRowTest`、`PhainonSlotFiveRowDriftTest`】
- **`ElationDamage` 的行有三种布局，而欢愉行分支只认一种** ✗：`[次数, 每次份额, 末段份额]` 是 8009／8010 ✓ 读得对 ✓；1506 槽 21 = `[份额, 次数]`（被读成 **1 次 600%** ✓ 实测 878.17 对 987.94 ✓）、1513 槽 20 = `[份额, 次数, 每次份额]` ✓。⇒ **不许猜「哪个整数是次数」** ✗（三条行整数位置各不相同 ✓，猜错会**同时**改掉已经读对的行 ✗）。缺的能力：把布局写进导出物（`skill_effects.json` 一列 ✓）或按「值域 ＋ 正文」定一条**可判**规则 ✓。【缺口见证：`SilverWolfLV999ElationRowTest`】
- **技能数据里的「消耗生命值」没有被执行** ✗（1408 那一族之外见 §3.7g ✓）：代价在数据里，引擎不读 ⇒ 以它为代价的 kit 缺一半 ✓。
- **`turns: N` 在「战斗开始时」的句子上一向差一回合** ✗（2026-10-19 实测 ✓）：`turns: 3` 的修饰在**第 3 个自己的回合开始时**就被摘掉
  （`BuffManager.tickBuff` 把到 0 的 buff 移除 ✓）⇒ 它只覆盖**两个**回合 ✓，而句子要的是三个 ✓。⚠ 施放类句子上这个差一被「施放的那一回合」吃掉，
  所以**只在 `BATTLE_START` 这类句子露出来** ✓（读者：光锥 20000／20004 ✓）。⇒ 要么给时钟一个「战斗开始时算第一回合」的口径 ✓，
  要么给内容一个「持续到第 N 个回合结束」的拼法 ✓ —— **在那之前不许把 `turns` 改成 N+1 假装对** ✗（那是把缺口埋进内容 ✓）。
  【缺口见证：`ConeAuditBatchOneTest.coneTwoThousandCritChanceForThreeTurns`（它钉的是今天的行为，修好时会红）】
- **`TURN_START` 规则上 `turns: 1` 只在**那一回合之内**活着** ✓（2026-10-19 实测 ✓）：`beforeMove` 之后读到 +16% ✓、`afterMove` 之后读到 0 ✓
  ⇒ 判据若在回合**之后**读数，会误判成「规则没生效」✗（读者：光锥 20003 的阈值那一半 ✓）。**这不是缺口而是夹具陷阱** ✓：
  读数要在回合内取 ✓。
- **单位计数器的「这次花掉了多少」没有事件** ✗（§3.7zd ✓）：唯一的"花掉"事件是 `SKILL_POINT_SPENT`（战技点专属）✓ ⇒ 1408 星魂 2 后半写不了 ✓。
- **`steps:` 形式的 `scale` 对 GAIN 被静默忽略** ✗ —— 用**量化计数器** ＋ 第二个 `amount_from_resource` 即可写出 ✓（§6.2 的撤回 ✓）。
- **`skill_segments.json`（全角色/忆灵分段表）已勘未接入** ✗：见 §3.7u ✓。
- **忆灵的「施放后持续效果 −1」判据立不起来** ✗：见 §3.7za ③ ✓（下一步是一枚探针，不是改 `tickBuff` ✓）。

#### 已核实**不是**缺口（别再登记它们）
- 忆灵技能的**读 / 改 / 点**三样都齐 ✓（`MemospriteSkillLevelTest` ✓）。
- **等级上限**由技能表自己的行数封顶 ✓ ⇒「声明在最后一行 +1 看不出变化」不是缺陷 ✓。
- **回能**读技能自己的 `sp_base` ✓（弹射按段数乘 ✓，`EnergyDataConformanceTest` ✓）。
- **派生伤害的来源施放**：击破/超击破带 `UNSPECIFIED` ＋ `causedByCast` ✓（只有 `KILL` 沿链 ✓，`DAMAGE_SETTLED` 不沿 ✓，`DanHengKitCompletionTest` ✓）。
- **`SkillSwapBuff` 的同类判定**：按槽位 ✓（`SkillSwapTest` ✓）；`StatModifierBuff` 的身份**不含名字** ✓（§3.7l ✓）⇒ 「同类」的身份必须恰好回答「这两条效果会不会互相取代」✓。
- **捕获复算的敌人面板**：观战数值被关卡改过 ✓（HP ×7.55、DEF −34.5% ✓），所以比较场景里 `DEFENCE` 是**输入** ✓ 不是数据 ✓（`scenarios/capture-858483.json` ✓）。
- **逐乘区对数值**：`gradlew run --args="compare <场景.json>"` ✓（拼错 zone 名 = 报错，不是跳过 ✓；留空 `expect` = 只打印引擎自己的数 ✓）✓。

### 4.4 层序与状态（**替代旧的"为什么这样排序"**）

| 层 | 内容 | 状态 | 在哪能查 |
|---|---|---|---|
| **A 层** | P8-6/7/8 + P8-3：角色机制 = 数据的总开关 | ✅ **已完成** | `models/trigger/*` ＋ `characters/*.json` |
| **B 层** | P9：敌人技能与 AI | 🚧 进行中 | §4.2 |
| **C 层** | P10：机制补完 | 🚧 差 P10-4 拉条 / P10-5 终结技插入 | §4.2 |
| **D 层** | P11：演示与收尾 | 未开始 | §4.2 |
| **远期** | 7.1 战斗拆分 | 第一小刀已落地 | §4.2 |

**为什么这个顺序** ✓（一句话版）：A 层是**所有角色机制**的**总开关** ✓ ——
在它之前做任何角色内容都是往 `Battle` 里塞 `switch (cid)`，做完还要返工 ✓；
B 层紧随，因为**演示怪里只有少数有技能、其余全平 A**，没有难度曲线 ✓
（`enemy_skills.json` 的自建表**隔离了数据源缺失这个外部风险** ✓：找到源数据只换加载处 ✓）；
C 层全是**填口子** ✓，依赖已完成的机制，**不该出现新架构** ✗。

### 4.5 文档分工（改文档前先读这一节）

| 文件 | 放什么 | 不放什么 |
|---|---|---|
| **`src/main/java`（唯一权威）** | **引擎当前行为** ✓；"现在是什么"要**读代码** ✓，或读 `characters/<cid>.json` 里每条规则自带的 `source` / `note` ✓ | 设计意图、进度、历史 |
| **`AGENT.md`（本文件）** | 怎么做事、改引擎的线、阶段与待办、以前的坑 | 会腐烂的计数与套件数 |
| `TRIGGER.md` | 触发器 **JSON 格式**（是**规范**，不是台账 ✓） | 本实现的进度 |
| `HSR.md`（正典副本在仓库外） | 游戏机制的**权威公式** | 本实现的差异 |
| `E:\turnbasedgamedata\aluminium_texts\<id>_<名>.md` | **角色反推的原始依据**（天赋/行迹/星魂/秘技原文） | 本实现的差异 |
| `E:\turnbasedgamedata\ExcelOutput\*.json` | **数值的原始依据** | 语义解释 |

> ⚠ **文档曾经删过五个文件** ✓（`engine.md` / `GAPS.md` / `HANDOFF.md` / `REFACTOR.md` / `EXPRESSION.md`
> ＋ `tools/` 整个目录 ✓）；随后 `ENGINE.md` / `CONDUCT.md` / `AGENT.md` **又长回三份** ✓，
> 2026-10-16 **全部并入本文件** ✓（理由见 §2.1 ✓）。
> ⛔ **不要再新建台账** ✗。

---

## 五、角色 C：收尾 / 交接的

> **谁读**：一段工作做完、要交出去的人 ✓（包括我自己每轮结束时 ✓）。

### 5.1 提交

| 项 | 规矩 | 把守 |
|---|---|---|
| **提交信息** | **一行英文** ✓，说清动作 ✓ | 【只靠散文】 |
| **文档语言** | **中文** ✓（注释英文 ✓；中文只出现在引号里 ✓） | 【只靠散文】 |
| **缩进** | **两个空格** ✓ | 【只靠散文】 |
| **构建产物** | **不入库** ✓ —— 探针、临时启动器、`build/` 里的东西都不提交 ✗ | 【只靠散文】 |
| **分析脚本** | **放仓库外** ✓ —— 放 `build/` 里会被构建删掉 ✓；本仓库的 `tools/` **已被整体删除** ✓（曾积攒大量一次性草稿 ✓） | 【只靠散文】 |
| **误提交的探针** | **删掉** ✓ —— 本仓库发生过一次 ✓ | 【只靠散文】 |
| **提交前** | **跑全量测试** ✓ 并报告套件数 / 用例数 / 失败数 ✓ | 【已把守：闸门 1】 |
| **引擎与内容分开提交** ✓ | 两者的回退粒度不同 ✓ | 【只靠散文】 |

### 5.2 交付时说清「没做什么」

- **没做的事、做不到的事、故意不做的事，都要说出来** ✓（§1.12）。
- ⚠ **闸门没跑全**：只跑了闸门 1 就报「**绿只覆盖测试，不覆盖 demo 形状**」✓，**不许含糊成"全绿"** ✗。
- ⚠ **闸门没有对象**（例如本批没搬迁）明写「**不适用**」✓，**不要跑一个空转的检查来凑"四道全绿"** ✗。
- **缺能力时登记** ✓，不要近似 ✗（登记进 §4.3 或规则自带的 `note` ✓）。

### 5.3 交接要列的东西

- **HEAD** ✓、**树状态** ✓、**闸门数字** ✓、手上的**脚本在哪**（⚠ 仓库外 ✓）、**下一批的守卫** ✓。
- ⚠ **"我以为记住了但没量"的事，写成待核** ✓，**不许写成事实** ✗。
- **交接前**：删 `build/test-results` → **`--rerun-tasks` 真跑**全量 → 报**真实**基线 ✓
  （⚠ 只删 XML **不够**：Gradle 会报 `UP-TO-DATE` 并把上一次结果**原样写回** ✓，见 §3.3）。

---

## 六、教训登记（**按主题，不按轮次**）

> ⛔ **不要新开"第 N 轮"栏** ✗ —— 见 §2.1。新教训**并进下面已有的主题小节** ✓。
> **已登记两轮**（内容已并进各主题，✓ 不再单独成节）：
> **2026-10-02**（我开工前**没读**这些纲要 ✗，于是按过时心智模型干了三轮：造了第四份台账、
> 把脚本留在 `tools/`、每轮都报用例数、只跑了一道闸门 ✓）＋
> **2026-10-16**（没改引擎与内容，只跑四道闸门 ✓，结果三道闸门当场教了东西 ✓）。

### 6.1 本仓库的实测陷阱

#### (a) demo 不能按哈希守护（会天天假红）

`Resist {...}` 与 `Weakness [...]` 是 **`Set` / `Map` 打印**的 ⇒ **同一份代码可能给出不同顺序** ✓
⇒ 哈希护栏会对**任何**输入报警，等于没有护栏 ✗。
**正解**：**内容级**对比 —— 把 `{}` / `[]` 内的元素**排序后**再逐行比 ✓
（这样"元素少了一个"仍会报出来 ✓，而"顺序变了"不会假红 ✓）。⚠ 脚本放**仓库外** ✓。

> ⚠⚠ **CORRECTED（2026-10-16 实测）**："同一份代码连跑三次得到三个不同哈希"**我没能复现** ✗。
> 量法：各份 demo 各跑**两轮** ✓，把两个区域的内容**逐份抽取后去重** ✓
> ⇒ 各自只出现稳定的几种，**没有一次是同一集合换了顺序** ✓
> （"3 种"是 **3 只不同的敌人**，不是同一集合的重排 ✓）。
> **旁证**：`Main` 里那套弱点的注释**明说**它刻意用 **insertion-ordered** set，
> 为的是这一行每次读起来一样 ✓ ⇒ 该文件**故意**让它可复现 ✓。
> ⇒ **"必须内容级对比"这条正解仍然成立** ✓（`Resist` 是 `Map` 打印、顺序本就无保证 ✓）；
> 但**"三种三个哈希"这个实测记录在今天的代码上不成立** ✗ ——
> 起点是 `Set.of` 还是 `HashMap` 决定它会不会变 ✓。⚠ **状态：待核** ✗ ——
> 谁要引用这句话，**先自己量一遍** ✓（量法同上 ✓）。
> ⭐ 配套护栏要**双向**：对"**元素少了一个**"报红 ✓，同时对"**顺序变了**"**不**报红 ✓
> —— 只做一个方向，会得到"护栏有效"这个**半对**的结论 ✗
> （2026-10-16 实测：第一版脚本把所有含逗号的方括号区域都排序，
> **误改了 `Main` 里 `toList()` 的有序输出** ✓ ⇒ 改成**白名单制** ✓）。

#### (b) 用脚本量代码时，**解析器少看一行 = 假结论**（×5）

本仓库连续踩到**同一形状**的坑 —— 都是"解析器**静默少看**"，不是报错 ✗：

| 现象 | 后果 |
|---|---|
| 声明**跨行**（`@SafeVarargs`、多参数换行）没被识别 | 该方法**从调用图里消失** ⇒ 读成「没人调用它」⇒ **正好是唯一能毁掉拆分决策的错误答案** |
| `if (...) {` 被当成方法名 `if` | 其括号配平**截断**外层方法的 span ⇒ **吞掉调用点** |
| 4 空格缩进写成 `\s{4}` | `\s` **也匹配换行** ⇒ 缩进捕成 `"\n   "` ⇒ 声明**从图里消失** |
| 声明正则**不要求修饰符** | `    requireSkillSlot(x, y, z);`（第 5 列开始的**调用**）被当成声明 |
| 常量结束位置**不扫到 `;`** | `Pattern.compile(...)` / `Set.of(...)` 跨多行 ⇒ **之后每个块都少切一行** |
| 排除文件**按文件名而非解析路径** | 排除**没生效** ⇒ 夹具把自己重写成**自我调用** ⇒ 整批用例 `StackOverflowError` |
| 批次成员表**从"我记得的那几条"来**（2026-10-16） | 正则 `\w` 边界漏掉 `` `X.md`} §12.5 `` 这种形式 ⇒ **少算了好几处** ✗；**还多出名单里根本没有的文件** ✗ |

⇒ **纪律**：每个测量脚本都要**先用已知答案自检** ✓；脚本**宁可能拒绝执行，也不要写半成品** ✓
（本仓库多次靠"拒绝"避免了坏改动 ✓）。
⚠ **批次成员表要从"更宽的那条查询"来** ✓，并且**在脚本里 preflight 断言每个锚点恰好出现 1 次** ✓
—— 锚点数 `!= 1` 就**拒绝执行** ✓（2026-10-16 靠它挡住了两个空批次 ✓）。

#### (c) 机械清扫的判据要用**精确字符串**，不许"看起来像"

实测：「`MONSTER` 是某个 `EnemyFactory.create` 的参数」这条判据**没检查 level** ✗ ⇒
各文件的调用点用着不同的 level / group ✓，清扫**删了常量却没改那些调用** ✗ ⇒
**一大批文件编译不过**，只能全量回退 ✓。
⇒ **正解**：判据收紧成**纯改名**（出现次数 = 1 + 精确调用次数）✓，并让 preflight
**把每份文件预期的改写次数写进清单** ✓，清扫**断言自己正好做了那么多次** ✓
—— 正则静默漏掉就变成**硬失败** ✓，而不是"批量扫完才发现编译不过" ✗。

#### (d) `gradlew clean` 会删掉 `build/` 下的基线

基线（demo 输出、分析脚本）放 `build/` 里会被构建清掉 ✓。**重建办法**：
`git worktree add build/_baseline_wt HEAD` ＋ 把 gitignore 的 `src/main/resources/game/data/` 拷进去 ✓，
在那个 worktree 里跑 demo 取基线，取完 `git worktree remove` ✓。
⚠ 长期看**分析脚本应放仓库外** ✓。

#### (e) 怪物数据陷阱

| # | 陷阱 | 事实 | 处理 |
|---|---|---|---|
| 1 | **`hp_modify_ratio` 是幽灵字段** | tbgd 只有 `HPModifyRatio`，**没有** `HealthModifyRatio` ✓；解析器猜错键名后留了个恒为 1 的字段 ✓。真实血量系数在 `health_modify_ratio` | bean 不读幽灵字段 |
| 2 | **`attack_modify_ratio` 未导出** | tbgd 里有相当一部分怪 ≠1 ✓，本数据整体缺这一列 | 补丁 `monster_attack_modify_ratio.json` + `Constant.normalizeMonsterConfigs` 合并 ✓。**正解在导出脚本**：`monster_config.json` 自带该列后删掉补丁与合并代码 ✓ |
| 3 | **效果命中/抵抗是加值不是系数** | 组1·Lv90 给 0.32 / 0.1 ✓，冰锋模板 0.2 → **0.2 + 0.1 = 0.3** ✓；相乘会得 0.02 ✗ | bean 字段名去掉 `Ratio` 后缀 |
| 4 | **等级组的键名 ≠ bean 字段名** | JSON 是 `attack/defence/health/speed/stance` ✓，bean 写成 `attackRatio` 之类 Gson **静默读成 0** ✗ | 一律 `@SerializedName` |
| 5 | **组号/等级来自关卡** | 怪的 `hard_level_group` 通常是 1 ✓；真正决定难度的是 `StageConfig` ✓ | 调用方显式传参 |
| 6 | **精英组有两张表** | 普通关卡 `EliteGroup` ✓；无限波次用 `InfiniteEliteGroup`（绝境王虫 ×6.2）✓。用错血量差几倍 ✗ | 本数据暂无 `elite_group.json`，缺省 1 |

#### (f) Gson 静默失败（本项目历史重灾区）

未知键名 / 类型不匹配时 Gson **不报错，读成 0 或 null** ✗ ⇒ 一律 `@SerializedName` ✓，
并**用一条判据钉住读出来的数字** ✓（见 (e) 陷阱 4 ✓）。

- ⚠ **「减速」不是一个状态** ✓（2026-10-19 实测 ✓）：它由 **SPEED 上的负修饰** 表示 ✓，条件词表的拼法是 **`is_slowed`** ✓
  （读者：1002 ✓，判据 `SlowedConditionTest` ✓）。⚠ 写 `has_state 减速` **装载得过、永不触发** ✗（静默无效 ✓）—— `has_state` 只认**真有那个
  buff 名字**的状态（冻结 ✓、触电 ✓）。⇒ 遇到「XX 状态下」先问：**那是 buff，还是一条属性修饰** ✓。【把守：`ConeConditionLiteralsTest`（字面）＋ `ConeAuditBatchTwoTest`（后果）】
- ⚠ **`KILL` 带着击杀者当 `actor`，并会发给每一张我方表** ✓（2026-10-19 实测 ✓）：`Battle` 的 `fireTriggersWithSubject(KILL, damage.getAttacker(), …)` ✓
  ⇒ 「使**装备者**消灭敌方目标后」这类句子**必须**加 `actor == self` ✗ 否则**队友的击杀**也算 ✓（读者：光锥 20007 ✓，同形：1015 的天赋 ✓）。

#### (g) `instance: true` 只在你**挂在伤害事件上**时才是实例级

⭐ **实测（2026-10-19，1015 Archer）** ✓：`MODIFY_ATTR` 写 `"instance": true` ＋ `"damage_type": "ULTRA"`
却挂在 **`BATTLE_START`** 上 ⇒ 它**不是**实例级 ✓，而是**普通修饰**：E4 的「终结技伤害 +50%」于是落到了
**每一发**伤害上 ✓（实测：队友的追加攻击（`ADDITIONAL`）增伤区从 **1.224 → 1.724** ✗）。
⇒ **实例级修饰必须挂在 `DEALING_DAMAGE`（或同类伤害事件）上** ✓ —— 读者：1220／1309／1406 的追加攻击暴击加成 ✓，
`SkillExecutor` 也只在结算那一刻把实例交出去 ✓。
⚠ **同一批里学到的第二半**：`damage_type: SKILL / ULTRA` **永远命不中技能自己那一发** ✗ ——
引擎把技能自身的实例记成 **`NORMAL`** ✓（`SkillExecutor.damageTypeOf` 只把欢愉改成 `ELATION` ✓）⇒
「战技／终结技造成的伤害」要用**事件条件** `from_skill SKILL` / `from_skill ULTRA` ✓（读者：1215 寒鸦天赋三条 ✓、
1206 剑势三条 ✓），**不是**伤害类型 ✗。

### 6.2 效力与撤回

- ⚠⚠ **"半对"的结论比"没结论"更贵** ✓：我曾把**一个半对结论当事实写进仓库** ✗ ——
  `GAIN_RESOURCE` 的来源是**封闭的几条** ✓，而 `scale` **只认 `cast_param:`** 那一支 ✓
  ⇒ **`steps:` 形式的 `scale` 对 GAIN 被静默忽略** ✗。我据此推出的
  「8009 星魂 1 写不出来」**是错的** ✗ —— 用**量化计数器** ＋ **第二个** `amount_from_resource` 即可写出 ✓。
  ⇒ **凡结论里出现"引擎没有这个能力"** ✗，都必须附**读了哪几行代码 / 跑了哪个探针** ✓；
  **读不全就写"待核"** ✓。
- ⚠ **过期的"注册"也会替你回答** ✓：我两次把"引擎没有这个能力"写进台账 ✓，其中**一次是错的** ✓
  （`element: "attacker"` 我判为"需要新特性" ✓，后来发现**只需把三处内联取值收敛到 `elementOf`** ✓）。
- ⚠⚠ **"写不了"是一个断言，不是一个印象** ✓：同一份角色文件里我一次写错过**好几条** ✓，而错法**完全相同** ——
  **没打开代码** ✗：判"这一发暴击了不可观测"（而**事件一直在** ✓）、判"`MODIFY_ATTR` 没有 `base_chance`"
  （而**字段一直在** ✓，别的角色正在用 ✓）、判"规则够不到暴击判定"（而事件**就在判定之前**发出 ✓，
  源码注释还写明了**为什么**必须在那儿 ✓ ⇒ 那一句本来就是**写给未来读者**的 ✗ 而我跳过了）。
  ⇒ **规矩**：每条"写不了"的登记必须附**一行出处**（`文件:行` / 字段名 / 探针输出 ✓）；
  **没有出处就写"待核"** ✗ 不许写成"引擎没有" ✓（与本节第一条同源 ✓）。
  ⚠ 反向也记一笔：登记**被推翻**时 ✓，把那三句**错理由原文**留在 note 里 ✓ ——
  下一个人会犯同一个错，而"当时我是这么想的"比"已修"更能拦住他 ✓。
  ⚠ **同一形状又一处**（2026-10-17 实测 ✓）：1001 的护盾把 `0.57` / `760` 写成**字面量**、note 里登记成
  「registered as a data gap，因为引擎的角色都是 1 级」✓ —— 前半句（引擎读不出等级份额）是**真的** ✓，
  后半句（所以这句话写不了）**是错的** ✗：缺的是 `HEAL` / `SHIELD` 读**当前等级参数**的拼法 ✓，
  补上之后规则自己就能写 ✓（`percent_from_skill_param` ＋ `amount_from_skill_param` ✓）。
  ⇒ **"引擎没做"与"句子写不了"是两件事** ✓：前者是**待补的能力** ✓，后者才是**真的缺口** ✗。
- **撤回留在原地** ✓（§1.3）—— 而**本仓库那两次的撤回原文已经随旧台账被删** ✓
  ⇒ 所以它现在**只活在本节** ✓，别再去别的文件里找 ✗。
- ⭐ **读法选择也能被数据定下来 —— 去找「那个语义独有的原语」用没用上** ✓（2026-10-19 实证 ✓）：
  1206「并使素裳立即行动」一直是**读法选择** ✓（`EXTRA_TURN` 还是 `ADVANCE 100%` —— `HSR.md` 只定义「额外回合」
  与「行动提前/延后」，而正文用的是**第三个词** ✓），登记了几天没人推翻 ✓。**定下来的是官方 ability 数据** ✓：
  她大招那一 phase 收尾对 **Caster** 发 `RPG.GameCore.SetActionDelay` 且 **`Value = 0`** ✓（等待清零 ✓），
  而全库「插入一个回合」的原语是 **`TurnInsertAbility`**（实测 **1780** 处 ✓）—— 她**没有用** ✓ ⇒ 读法 = **清零** ✓。
  ⇒ **规矩**：一个词没有定义时，**别投票、也别找攻略** ✓ —— 去数据里找**那个语义独有的原语**用没用上 ✓；
  ⚠ 两种读法**可观测地不同** ✓（额外回合**保留**原本那一回合 ✓、清零**消耗**它 ✓）⇒ 判据必须**两侧都钉** ✓
  （`SushangUltimateAndTraceTest.herUltimateMakesHerActImmediately` ✓：剩余等待 → 0 ✓ **且**没有待执行额外回合 ✓）。

### 6.3 判据

1. ⭐ **一层钉不到的，明说，不要顺手钉一个代理量** ✗。
   实例：1513 的「固定计入 **20** 笑点」在本夹具里**不能**用两次施放的伤害比来钉 ✓ ——
   因为**内容自己**会在两次之间挪动笑点快照（实测 **554.77 → 1829.52** ✓）
   ⇒ 内容侧钉**结构** ✓、引擎侧钉**数值** ✓。
2. ⭐ **断言要落在后果上，不是落在仪器上** ✗（§1.5 是同一条的一般形式 ✓）。
   实例：「无法叠加」原本想用 `stacksOf` 数标记 ✓ ——
   实测 **`hasState` 为 true 而 `stacksOf` 读 0** ✗ ⇒ 改用**它的后果**断言：那次终结技付 **90** 而不是 180 ✓。
3b. ⚠⚠ **夹具把「条件点名的那个东西」造出来 ⇒ 它只证明了自己** ✗（2026-10-19 实测 ✓）：光锥 20011 的条件写成 `target has_state 减速` ✗（**没有这个状态** ✓），而 `LightConeSlowedTargetTest` 的夹具**合成了一个叫「减速」的 buff** 并挂上去 ✓ ⇒ 判据与内容**一起错、互相印证** ✓，而真实战斗里那条规则**永不触发** ✗。⇒ **前提要按引擎真正建模它的方式搭** ✓（减速 = SPEED 上的负修饰 ✓，判据读 `is_slowed` ✓）；⚠ 一个「从来没红过」的判据，先问**它为什么不会红** ✓。
3. ⭐ **判据的夹具前提不许被抽走** ✓：抽走的只能是"怎么搭出一个战场" ✓，
   "这个从句的前提为什么成立"必须留在判据里 ✓。
4. ⭐ **被测量要穿过一系乘区时，断言"比值"而不是绝对值** ✓。
   实证：追加伤害的绝对值 12.857 与期望 42.934 差了三倍 ✓ —— 因为它要**穿过防御与抵抗** ✓；
   改成两支队伍（档位和 0.25 与 0.85）的**比值**后，实测 **3.4000000000001416** ✓，区间自动相消 ✓。
5. ⭐ **报告要分"跑了 / 没跑"** ✓（见 §5.2）。
6. ⚠ **一条老判据可能把 bug 钉成期望** ✓（2026-10-17 实证）：`ReasonOdeEruditionAttackTest` 断言"两位智识各涨**同样的点数**"、
   `TrueKnowledgeSkillDamageTest` 断言"普攻几乎不动（+0.111%）" ✓ —— 而这两个数正是"份额被当成绝对值"那个 bug 的**签名** ✓
   （+0.84 点攻击 ≈ 0.111% ✓）。修好引擎后它们**有理有据地红** ✓ ⇒ 改判据时**写清楚旧断言钉的是哪一条 bug** ✓，
   并把断言换成**性质**（"每位按自己的基础涨 84%" ✓、"普攻只按攻击力涨，不额外吃战技增伤" ✓），而不是换一个数字 ✓。
7. ⚠ **「同值刷新」会让一条限流不可观测** ✓（2026-10-19 实证 ✓）：「持续 N 回合、重复施加只是刷新」的效果上，
   `per_turn: 1` **看不出任何差别** ✓ —— 同一回合的第二次施加把 0.45 刷成 0.45 ✓，读数一模一样 ✓
   （读者：1408 的行迹 1408102 ✓）。⇒ 该字段的判据只能在**可计数**的效果上（`TriggerLimitTest` ✓），
   而**内容那条**判据要么钉别的性质 ✓、要么在类注释里**明说它钉不到这一层** ✓（`PhainonTraceBideInFlamesTest` ✓）——
   ⛔ **不要**为了"有判据"而给一个读不出差别的字段编一条假断言 ✗。
8. ⚠⚠ **比值判据的"分母"必须是引擎用的那个和** ✓（2026-10-19 实证 ✓）：1206 之外又踩一次 —— 1408 星魂 6 的
   「本次攻击总伤害值 36%」我写成「真伤实例之和 ÷ **观察者重算**的普伤之和」✗，而引擎拿的是它**自己累加**的那个和 ✓，
   两者在两种情形下**不相等** ✓：
   ① **暴击** —— 引擎累加的是**已含暴击**的结算值，而观察者读的 `breakdown().get("final")` 在**读取时重算** ✓
   （实测：一发暴击 **×1.873** ⇒ 比值读成 **0.308** 或 **0.324** 而不是 0.36 ✓）；
   ② **过量击杀** —— 引擎累加的是"受害者**实际承受**的"（被剩余血量夹住 ✓），而实例的 `toValue()` 是**未夹**的 ✓。
   ⇒ **两条出路，选一条**：把夹具做成**两者必然相等**（**0 防御的 100 万血 dummy** ＋ **不暴击** ＋ **单个敌人** ⇒
   不暴击、不过量、`BOUNCE` 也不再有随机目标 ✓ —— 本批就是这么修的 ✓，连跑三次全绿 ✓）；或者**别用重算的和** ✓。
   ⚠ **一个单独跑 5 次都稳的判据，可能在整包里红** ✓（这次是整包红、单独绿 ✓）⇒ 见到"孤绿群红"先问**这两个数是不是同一个数** ✓。

### 6.4 变异

- **锚点必须唯一** ✓：脚本 `count != 1` 就**拒绝执行** ✓。
- ⚠ **锚点的缩进要量，不能照抄印象** ✗：`target_when` 的锚点少 4 个空格 ⇒ `hit 0` ✓；
  同一条规则里 **6 空格与 10 空格是两件不同的事** ✓。
- **保存 / 回滚按字节** ✓：`git show HEAD:<path>` 取 blob 字节写回 ✓。
  ⚠⚠ **绝不用 `git checkout` 做回滚** ✗（见 §6.8(e) 的破坏性还原 ✓）；
  ⚠⚠ **任何会 `git checkout` 的脚本只能在提交之后跑** ✓（**硬顺序，不是记性** ✓）：`git checkout [<commit>] -- <path>` 恢复的是**索引或旧版本** ✓，会把**未提交的 `src/main` 改动一并丢掉** ✗（本仓库实测丢过 `Battle.grantShield` 与解释器两处改动 ✓）；另两条同族：旧版本**没有**而工作区**有**的文件它**不会删** ✓（跑闸门前要自己删 ✓）；**工作区与 HEAD 相同时跑闸门比的是自己** ✓（看着像通过 ✗）。⇒ 跑闸门之前先 `git status` 确认干净 ✓。
  ⚠⚠ **PowerShell 的 `Set-Content -Encoding utf8` 会写 BOM** ✗ —— 用它回退一个 `.java`，编译**炸一串错** ✓。
  ⚠⚠ **「按字节」必须真的是字节** ✓：Python 用 `encoding="utf-8"` ＋ `newline="\n"` 写回一个 **CRLF** 文件，
  内容一样、**字节不一样** ✓ ⇒ 回滚后 SHA256 对不上 ✓（2026-10-19 实测：11 次变异**全部变红** ✓，
  而 4 个 `.java` 的回滚报 `BYTES DIFFER` ✓ —— CRLF 被无声换成 LF ✓）。
  ⇒ 备份与回滚**用二进制读写** ✓，锚点里的 `\n` 再**映射到文件自己的行尾** ✓（`\r\n` 或 `\n` ✓）；
  改完用 `git ls-files --eol <file>` 复核 `w/` 那一栏 ✓。
- ⭐ **变异仍然绿时，先怀疑"变量没动"，再怀疑"判据太弱"** ✓ ——
  ⇒ **变异绿有三种可能**（判据弱 / 变量没动 / 变异写坏）★ **逐个排除**，别直接改判据 ✗。
  实证：一条"加持 > 不加持"的判据，变异（摘掉条件）后**仍绿** ✓ —— 因为**终结技自己就给了计数** ✓
  ⇒ 两次运行都满足前提 ✓ ⇒ **那个变量是假的** ✓。**规矩：判据一次只改一件事** ✓。
- ⭐ **变异本身必须能失败** ✓ —— 一个**不可能失败**的变异证明不了任何事 ✓，而它披着"已变异"的外衣，
  比没做变异更误导 ✓。两种实测形状：① 变异打在**判据观察不到的地方** ✓（守卫按编号定位文档里那张表，
  而我把标题"编号之后的部分"改名 ⇒ 守卫根本没读那个字段 ⇒ 仍绿 ✓）；② **一次改了两处** ✓
  而两处**互相抵消** ✓（上限与起始层同时下调 ⇒ 两次读数都被对方抹平 ⇒ 仍绿 ✓）。
  ⇒ **规矩**：变异**只改一处** ✓，并且动手前**先说出"哪条用例会因此变红"** ✓；说不出就是变异选错了 ✗。
- ⭐ **变异脚本本身也会写坏** ✓：字串替换破坏括号 ⇒ **编译失败** ⇒ 读数"NO XML" ✓
  （**不是** RED、也**不是** green ✓）⇒ **变异必须语法合法** ✓，且**改内容走 JSON 解析**，
  不做字串替换 ✓（同一形状：`str.replace` 改 note **静默没生效** ✓ ⇒ 改文档 / note 用**赋值或追加** ✓，
  并**回读核对** ✓）。
- **迁移 / 清理脚本必须在参数里写明批内文件** ✓：一次"删无用 import"的清理**越界**改了批外文件 ✗
  ⇒ 批边界**写进脚本** ✓，不能靠"反正无害" ✗。
- **变异后的文件要字节级复核** ✓（每个脚本末尾自查 ✓）。

### 6.5 测量与探针：**先问"谁会在它被读之前重写它"**

- ⚠⚠ **探针会被构建自己冲掉 ⇒ 读出假结论** ✓：把悬空 `{@link}` 注入到 **`build/delombok/`** ⇒ `javadoc` **0 error** ✓ —— 因为 `delombok` **每次构建都从 `src/main/java` 重新生成**那棵树 ✓（注入在执行前就被冲掉了 ✓）。⇒ **探针打在构建真正读取的那份文件上** ✓。
- ⚠⚠ **先确认被测的是哪一份产物**（上一条的反向形状：没有人重写它，是它**压根没被重建** ✓）：实测一处修复"连续三轮没生效" ✓ —— 跑的是**安装目录里的旧 jar** ✓，而 `test`/`assemble` 只刷新 `build/**` ✓。⚠ 同一坑的另一半：`build.gradle` 注释写着"`installDist` 已接进 `build`"，而**根本没有那句接线** ✗ ⇒ **注释里"已经做了"的话要用一次 `--dry-run` 或时间戳核对** ✓。
  ⇒ **规矩**：诊断"改动没生效"之前，先量**被测产物的身份**（时间 / 内容 / 自报来源 ✓）；长期正解是让产物**自报身份**并让唯一支持的构建命令顺带重建它 ✓（两者都**默认安静** ✓）。
- ⭐ **双向灵敏度：只测"会红"不够** ✓：同一道闸门，喂一个它**该抓**的 ✓ ＋ 一个**本项目真实存在**的坏形状 ✓。只做前者会得到"闸门有效"这个**半对**结论 ✗。实证：javadoc 审计对 `{@link}` **有效** ✓，对 `{@code 某个文件.md}` **完全瞎** ✓（注入大量指向已删文档的引用 ⇒ **0 命中** ✓）。
- ⚠⚠ **环境变量会漏进被测进程** ✓：`JAVA_TOOL_OPTIONS=-Duser.language=en` 被 `gradlew run` 的子 JVM 继承 ⇒ 每份 demo **多出两行** `Picked up JAVA_TOOL_OPTIONS: …` ✓ —— 而**多份"一致地"变化看起来极像一次真实的行为改动** ✗。⇒ ① 测量脚本开头清掉 `JAVA_TOOL_OPTIONS` / `JAVA_OPTS` / `GRADLE_OPTS` ✓；② **行数变化先归因**（共同上游：环境变量 / 日志级别 / 一个新警告 ✓）；③ 判据是**原始字节对比** ✓。⭐ 已固化进夹具，并**故意把变量设回去**验证夹具真的挡住了 ✓。
- ⚠⚠ **"当前"是别人写的 ⇒ 顺序即语义** ✓：`level_convention`（`RAISE_SKILL_LEVEL +9`）与**读当前等级**的规则都在 `BATTLE_START` 上，而同一事件的规则**按文件顺序**执行 ⇒ 读行排在约定**后面**就会读到**第 1 行** ✓（实测 1304 砂金的行迹静默给出 `0.16 × DEF + 80` 而文档写 `0.24 × DEF + 320` ✓，**唯一症状是一条精确期望变红** ✓）。⇒ 约定放**文件第一条** ✓；⚠ 只有**早于或等于 `BATTLE_START`** 的事件受影响 ✓（施放类事件永远在开战之后 ✓）。另一半：**测试里「没开战就施放」** ⇒ 约定没跑过、等级是 1 ✓ ⇒ 夹具**必须先把战斗开起来** ✓。【已把守：`LevelConventionOrderTest`】
- ⭐ **探针要打前提，不是打结论** ✓：判据失败后不去猜，而是打一行 `before ult: COMMON slot = 1 ; counter = 0` / `after ult: slot = 8 ; counter = 20` ✓ ⇒ 一次就定位到 §6.4 的假变量 ✓。**探针自回滚**，别提交 ✓。
- ⭐ **词汇是可询问的：让引擎的封闭词表回答** ✓：新 op / 新字段不要猜 ✓ —— 校验器会**指名它接受什么** ✓（`Op ADD_DAMAGE states "damage_type", but only [DAMAGE, MODIFY_ATTR, MODIFY_DAMAGE_TAKEN, BOOST_DAMAGE] read it` ✓；`Op DAMAGE has "scale": "elation_base", which is not a spelling this op knows` ✓）。⚠ **同一个名字在不同事件上可能含义不同** ✓：`from_skill_id` 在 `BASIC_ATTACK` 上是**槽位** ✓，而 `Battle` 同参数的 javadoc 写 "The row, not the slot" ✓ ⇒ **别跨事件搬结论** ✗。
- ⚠ **"闸门跑起来了"不等于"闸门比对了"** ✓：先看两边输出的**大小 / 首行**，确认没有编译错误 ✓。
- ⚠ **提交信息里的数字要"跑完再读"** ✓（实测两次凭记忆写错 ✓）⇒ **先跑、读到那一行、再写信息** ✓。

### 6.6 文本改写与环境

- ⚠⚠ **验证动作本身会悄悄失效**（**×5**）✓ —— 这是本仓库最贵的一条 ✓：
  实测四次不同形态：① 校验通过才提交 ✗（校验明明失败，提交照样发生 ✓）；
  ② 对照脚本"该红却绿" ✓；③ 变异循环用 `git checkout` 冲掉未提交改动 ✓；
  ④ 变异脚本语法写坏 ⇒ 读数是"NO XML"而不是 RED ✓。
  ⇒ **合并成一条可执行的规矩** ✓：**任何改动 → 先在同一个命令里做机器校验
  （`json.load` / 编译 / 跑测试）→ 校验结果要单独取值 → 再提交** ✓，
  ⛔ **绝不让"通过了"和"提交了"分成两步靠记性衔接** ✗。
- ⚠ **文本改写一律用 fs 工具，不用 shell** ✓：
  `Get-Content | Set-Content` 会把 UTF-8 文档按 GBK 往返一次，**整份糊成乱码** ✗；
  PowerShell 的 `>` 重定向写成 **UTF-16**（会把 Java 文件写坏）✗；
  `pwsh` 渲染中文正常 ✓，**Windows PowerShell 5.1 会糊成乱码** ✗ ⇒ **一律用 `pwsh`** ✓。
  shell 只用来**跑命令、读输出、查状态** ✓。
- ⚠ **往里插东西时，"第一个 `{`"不是锚点** ✓：角色文件有两种形状 ✓，
  用"第一个 `{`"定位 ⇒ 插进了 `resources` 而 `rules` 里没有那条规则 ⇒ **一整片测试红** ✓。
  ⇒ 定位"某个数组的第一个元素"时，锚点**必须包含数组名**（`"rules": [`）✓。
- ⚠ **裸引号**（**×4**）✓：脚本里写 `\"…\"` 想得到转义引号，但 Python 会把 `\"` 变成裸 `"` ✓，
  于是插进 JSON 的英文句子**把自己的字符串提前结束** ✗。
  ⇒ **插入的任何文本（中英文都算）里的引号一律用 `「」`** ✓，
  脚本里**不要出现 `\"`** ✓，需要引号就用 `chr(0x300c)/chr(0x300d)` ✓。
  ⚠ 第 4 次之后的规矩：**插入文本里不出现 ASCII 双引号** ✓；真出事了就用**机械化修复**，
  而不是再列一遍短语 ✗。
- ⚠ **比对 demo 要比全文** ✓，不能只 `Select-String "Battle over"` ✗。
- ⚠ **给某个角色写内容文件，会让"没有规则文件的角色"这套测试夹具一起变红** ✓
  （`TriggerTableTest` 一族 ✓）⇒ 写完内容要**把夹具的期望一起更新** ✓。
- ⚠ **行尾**：源码文件在工作树里是 **CRLF** ✓，脚本锚点必须先归一化行尾 ✓
  （第一次重放脚本报"0 匹配"就是这个原因 ✓，**不是内容不对** ✓）。
- ⛔ **源码 / 文档里不写 `\uXXXX`** ✓ —— 一律写**原字符** ✓（`.java` / `.json` / `.md` 都算 ✓；UTF-8 全链路由 `build.gradle` 的 `encoding` 保证 ✓）。
  ⚠ 本节里出现的 `\uXXXX` 都是**例子** ✓，不是可以照抄的用法 ✗。
  两个理由都是实测的 ✓：① `javac` 在**词法分析之前**处理 `\uXXXX` ✓ ⇒ 它**能改语法** ✗
  （把换行写成 `\u000a` 会让字符串跨行未闭合 ✓，而报错指向的地方**看起来完全正常** ✗）；
  ② `\u26a0` 读起来不是 ⚠ ✓。
  ⚠ **唯一例外是控制字符** ✓：`\u0000` 写作 `\0` ✓（原字符不可打印，而且裸 NUL 不是合法源字符 ✓）。
  ⚠⚠ **这条来自一次真实的损坏** ✓：一次脚本改写把字母 `a` 变成了 `\u0007`（BEL）✗ ——
  内容 note 里的 `\u0007ttacker` / `\u0007ctor` 躺到 2026-10-17 才被发现 ✓，全部还原成 `a` ✓；
  同一天把全库的转义一次清完 ✓（脚本分三类处理：可打印字符直写 / NUL 用 `\0` / BEL 还原成 `a` ✓）。
  ⇒ **改写脚本里也不要写 `\u` 转义** ✓：要引号就用 `「」` ✓、要换行就显式 `\n` ✓。

### 6.7 文档与引用

- ⚠⚠ **过时的 note 会在同一轮里复发** ✓：写完实现却没回头改描述 ✓ ——
  同一份角色文件在**连续三批**改动里各留下一处"尚未写" ✓，而它们描述的正是**同一批**刚刚落地的规则 ✓。
  ⇒ **规矩**：改描述**放进实现的同一批** ✓，不是"下一步" ✓；提交前对**改过的每条 note** 搜一次
  `未写` / `仍未` / `登记` ✓ —— **命中即复核** ✓。
  ⚠ 反向陷阱：note 里**引述旧结论**（"当时我以为…"）✓ 必须**明确标成引述** ✓，
  否则下一轮的搜索会把**引述**当**现状** ✓（实测：一次 `未写` 命中的正是对旧文的引述 ✓，
  而真过时的那条**藏在另一个字段里** ✓）。
- ⚠ **文档里的路径与锚点会静默腐烂** ✓：2026-10-16 全库清出的死引用里 ——
  多数指向已改名的 `engine.md` ✓（新文档里**没有**那些 §N ✓）、一部分指向已删的 `GAPS.md` ✓、
  其余指向早已不存在的 `§12.5` / `:1262` 行锚点 ✓。
  ⇒ **改文件名 / 拆文件后要跑一遍"被引用文件是否存在"的巡检** ✓；
  ⚠ **`{@code X.md}` 不是引用** ✓ —— 它是一条**没人守的断言** ✗（§6.5 双向灵敏度 ✓）。
  ⚠ **同名不同大小写要按目标平台判** ✓：Windows 上 `Test-Path engine.md` 会命中 `ENGINE.md` ✓
  ⇒ 本项目的巡检**误报过一次"存在"** ✗。
- ⚠ **机器清扫 / 重放脚本会破坏转义与引号** ✓：切片替换切进转义序列、引号未转义 ✓，
  两次都靠 **`git show` 取字节 ＋ 单独提交**修复回来 ✓。
- ⚠ **本文的两条自身约束**：不复制通用方法两遍 ✓（§一 是唯一家 ✓）；
  **实验读数注明日期；仓库状态的计数与行号不写** ✓（§2.4）。

### 6.8 破韧、天赋伤害、观测点（**旧** ROADMAP §3.9–§3.18 的**规则**部分）

> ⚠ 这一节是从**旧** `ROADMAP.md` 的 §3.9–§3.18 **捞出来的规则** ✓ —— 那 8 节原本被**错插在**（旧编号的）**§8 与 §9 之间**
> （2026-10-16 实测：§8 标题下的真论证只有开头几行 ✓，其后绝大部分其实是踩坑记录 ✓）。
> 原节里的**逐轮叙事、TUI 契约、另一棵工作树里的用例名**没有搬过来 ✓（本 checkout 里
> `src/` 下**没有** tui/ui 包，也搜不到 `ChoiceFlow` / `scrollLog` / `PgUp` ✓）——
> 要读全文去 git 历史 ✓。

#### (a) 破韧的"恢复那一回合"必须**单独断言**

- ⚠ 只测"破韧的敌人不造成伤害"**不够** ✗ —— 一个**永远不递减计数**的实现也会通过 ✓，
  而那意味着敌人**永久无害** ✓，**比原 bug 更糟** ✗。
- 常数与"实际丢掉的回合"**差 1** ✓：`Constant.BROKEN_REMAIN_TURNS = 1` ✓
  ⇒ **一次跳过都不发生** ✓，破韧收益只剩 `BreakDelayRatio` 的行动延迟 + 伤害窗口 ✓。
  **要回"跳过 1 回合"就把常数调回 2** ✓ —— 那条断言就是拿来提醒这件事的 ✓。
- 【已把守：`BreakStateTest.aCounterOfTwoSkipsOneTurnAndACounterOfOneIsTheRecoveryTurn`
  （显式钉 2 与 1 两个边界 ✓）、`DrivenBrokenTurnTest`（显式写 2，因为值 1 根本不会跳过 ✓）】
- ⚠ **返回值语义**：`handleBrokenTurn` 的返回值从"它还是破韧的"改成"**这一回合是否跳过**" ✓
  ⇒ 调用方要跟着改口径 ✓：`BattleDriver` 用 `mayAct = !handleBrokenTurn(...)` ✓，
  而 `Main` 的 `if (handleBrokenTurn(...)) return;` **无需改动** ✓（口径自动跟着变 ✓）。

#### (b) 无头驱动（`BattleDriver`）曾**绕过三个收费 / 计时点**（同一形状 ×3）

| 形状 | 事实 | 守卫 |
|---|---|---|
| 大招**免费** | 无头路径没走收费点 | 【已把守：`DrivenUltimateCostTest`】 |
| 技能点**收两次** | 驱动与 `useSkill` 各收一次 | 【已把守：`DrivenSkillPointCostTest`】 |
| 破韧的敌人**照常行动** | 破韧没有真正跳过回合 —— **最大的一条** | 【已把守：`DrivenBrokenTurnTest` / `DrivenBrokenRecoveryTest`】 |

- ⭐ **教训：把"打不过"读成"策略不行"之前，先确认机制真的生效** ✓ ——
  "策略问题"和"机制没接线"在**结果上长得一样** ✓，但**修法完全不同** ✗。
  实测：曾把"演示打不过 30000"归因于 `AutoPolicy` 的策略 ✓，**那个归因是错的** ✗；
  真因是上面三个形状 ✓。修完后演示改回 **30000**（那个刻度**本来就是数据写的** ✓）。
- ⚠ **"`AutoPolicy` 比手写脚本弱"也是错的** ✗：把手写演示的决策逻辑照抄成 policy 与 `AutoPolicy`
  在同内容同种子上对打 ⇒ **两边都赢，`AutoPolicy` 行动数明显更少** ✓
  （它只在"有人低于阈值**且**没盾"时才上盾 ✓，手写脚本**只要付得起就上盾** ✓）。
  ⇒ **手写脚本不是更强的策略，它只是更慢** ✓；**"更慢"不等于"更强"** ✗。
  【已把守：`DrivenRealBattleTest.theDrivenDemoWinsAtItsOwnScale`】
- ⚠ **恒真的对照**（本项目实测三种）✗：
  ① 实例基数取 100 时 `+X` 与 `+X%` **数学上相同** ✓ ⇒ 那条"抓住错误实现"的用例**永远不可能失败** ✗；
  ② 用"行动前 − 回合结束"量花费 ⇒ 会被回合结束**恢复 1 点**掩盖 ✓（收费 1 次与完全没收费读数都是 2 ✓）
  ⇒ 量法必须**夹住施放本身** ✓；
  ③ 拿驱动去和 `useSkill` 对比**恒真** ✓（两者都走 `useSkill` ✓）⇒ 对照必须指向
  **不可能重复收费**的路径 ✓。
- ⚠ **反面对照必须先 `compileJava`** ✓：变异**编译不过**时 Gradle **一个测试都不跑却报成功** ✗；
  用例数要**从 JUnit XML 读** ✓（全绿时 Gradle 根本不打印 "N tests completed" ✓），
  `tests > 0` 才算通过 ✓。⚠ `--tests *` 会**一个用例都没匹配到** ✓ ⇒ "0 个测试对比 0 个测试"却报告"相同" ✗。

#### (c) 观测点：**只观察、不改变**，数字只在**结算处**取

- ⚠ `DamageEvent.onDamage` **在乘区计算之内**触发 ✓ ⇒ 拿到的是**中间值** ✗；
  结算值**事后也拼不出来** ✓（`addBoost` 是 append ✓，再算一遍会把增伤/易伤/减伤/弱点**算两次** ✗）
  ⇒ 观察者要放在 `applyDamage` 的 `return` 之前 ✓。
- 治疗要说**实际回复量** ✓（`CanHit.heal` 按上限截断 ✓、对死者不生效 ✓）；
  护盾要说**加成之后**的值 ✓ ⇒ **报请求值＝报一个战斗里不存在的数字** ✗。
- ⚠ **日志行数从来没有被测试钉住** ✓：`DemoSmokeTest` **只**断言"演示不抛异常" ✓（全是 `assertDoesNotThrow` ✓）
  ⇒ 行数变化**不是回归** ✓（§6.5 的"行数变化先归因" ✓）。

#### (d) `DamageType.TALENT`：**天赋槽位的伤害**，以及它做不到的那件事

- 引擎保留 `TALENT` ✓ —— 它让消费方能问一个**更真的问题**：「这是天赋槽位的伤害吗」✓，
  而 `ADDITIONAL` 表达不了 ✓（`ADDITIONAL` **同时涵盖天赋与追击** ✓）。
- ⚠ **`TALENT` 与 `ADDITIONAL` 要一起收追击加成** ✓ —— 不加会**静默降低所有天赋伤害** ✗。
- ⚠ **`DamageTypeTest` 断言取值总数是故意的守卫** ✓
  【已把守：`DamageTypeTest`（`assertEquals(…, DamageType.values().length)` ✓）】
  ⇒ **新增类型必须是决定，不能是副作用** ✓。
- ⚠ **日志保持 `[extra]` 一个标记** ✓ —— **把反击叫「天赋」、或把天赋叫「反击」，都是把猜测印成事实** ✗。
- ⚠ **真正缺的是"规则身份"** ✗：`Damage` 上**没有规则 id、也没有目标选择器** ✓
  ⇒ 类型这一层**区分不了反击和追击** ✓ ⇒ **留作已知缺口，不猜** ✓（§4.3）。
- ⚠⭐ **方法论（这条最贵）**：我在一个"锦上添花"的标记上**连试多次** ✓
  ⇒ **第 1 次弄红时就该停下来问"引擎给的信息够不够"** ✓，
  而不是换第 2、3……种推断方式 ✗。
  ⇒ **最终答案不是"更聪明的推断"，而是"让引擎把事实说出来"** ✓ ——
  而且**说出来之后仍然不够** ✓（真正缺的是规则身份 ✓）。
  这些失败形态值得记住（都是"用别的信号去**猜**这一条伤害是谁发的" ✓）：
  用"当前规则不是普通 DAMAGE"推断 ⇒ 把大招的 `[extra]` 弄没了 ✗；
  用 `SPEND_RESOURCE` ⇒ 天赋伤害在扣费**之前**结算 ✗；
  用 `DAMAGE` 规则标记 ⇒ 反击标记坏、原本绿的用例变红 ✗；
  加标记且保留旧路径 ⇒ 标记持续到回合末、敌人普攻被标成天赋 ✗；
  "消费式" ⇒ 只标到少数几条里的一条 ✗；
  "窗口式" ⇒ 脚本锚点没匹配、**改动根本没落地** ✗。

#### (e) 行动轴与开局状态

- ⚠ `turnsTaken = 0` 但 `reset()` **已经推进一步队列** ✓ ⇒ `currentActor` 已指向第一个单位 ✓；
  开局 `nextActionTime` 就是 `10000 / 速度` ✓（实测 `0.00 / 17.33 / 40.61 / 52.21 / 52.21 / 59.95` ✓）。
- ⚠ **"没有"让引擎从 0 开始** ✓ —— 动 `startBattle` / `initialise` 会波及**大量**引擎用例与手写演示 ✓
  ⇒ 已向用户说明并**请其定夺** ✓，不擅自改 ✗。
- ⚠ **布局的高度契约**：营地不能被挤掉单位 ✓；`Constraint.min(...)` / `Constraint.length(...)`
  **两次实测都完全无效** ✓（每个高度结果一致 ✓）⇒ **不要留一个看起来有用的约束** ✗。
- ⚠ **`Battle` 上"由引擎拥有"的方法**：曾逐个判定每个方法 ✓，
  结论是**三个**形状的偏差（本节的 (b) ✓），**没有找到第四个** ✓。
  两条容易踩的：`beginCast` / `endCast` 必须成对（放 `finally` ✓）；
  `markNextCastCountedLaughs` / `castCountedLaughs` 必须成对 ✓。

---

## 七、把守清单（**决定上面每条规矩能写多短**）

> ⚠ **本节是整个压缩方案的地基** ✓：**有守卫的规矩压成一行，没守卫的才需要完整篇幅** ✓（§2.3）。
> 每条守卫都**逐个核过存在性**（2026-10-16 ✓）——**不许推断** ✗（§1.2）；⚠ **不数 `@Test` 条数**（§2.4）。

### 7.1 已有机器守卫

| 规矩 | 守卫 |
|---|---|
| 伤害流水线的公共口子不变（只有 1 个公共方法收 `Damage`） | `DamagePipelineTest` |
| 三层结构的可扩展性标准（不动引擎一行、只换策略） | `SkillPointPolicyExtensibilityTest` |
| 内容里的数字必须用**引擎自己的单位** | `UnitDisciplineTest` |
| 同一件事不许在两处登记 | `ContentDuplicateGuardTest` |
| **伤害类型的作用域** | `DamageTypeScopeValidationTest` |
| **`DamageType` 取值计数**（新增类型必须是**决定**，不是副作用） | `DamageTypeTest` |
| 破韧：计数 2 跳过 1 回合 / 计数 1 是恢复回合（**两个边界都钉**） | `BreakStateTest` |
| 无头驱动：大招**收费** | `DrivenUltimateCostTest` |
| 无头驱动：技能点**只收一次** | `DrivenSkillPointCostTest` |
| 无头驱动：破韧**真的跳过**回合 | `DrivenBrokenTurnTest` |
| 无头驱动：破韧**恢复**那一回合 | `DrivenBrokenRecoveryTest` |
| 无头驱动：在**数据本身的刻度**上能赢 | `DrivenRealBattleTest` |
| 事件公告的路由（当事方 → 我方全体 → 按对象去重） | `BattleEventRoutingTest` |
| 敌人技能：条目 / 倍率 / 削韧与表一致 + 兜底普攻 | `EnemySkillTest` |
| 敌人技能全效果（含"到边界截断、不回绕"） | `EnemySkillFullTest` |
| Boss 阶段换招 | `EnemyPhaseTest` |
| Boss 受击反击（反击者身份 / 不喂养被反击者 / 互反击可解 / 死者不反击 / 递归被拒） | `BossMechanicTest` |
| Debuff 基础概率只认描述里的 `#N`（标定样本） | `DebuffChanceDataTest` |
| 「按被治疗者状态抬高治疗者治疗量」 | `NatashaTalentTest` |
| 罗刹结界：死亡时解除 / 挂对阵营 | `LuochaFieldTest` |
| 「随我死而消失」的集合类 | `LethalSetTest` |
| 遗器能力批量 | `RelicAbilityBatchTest` |
| 效果键的封闭词表 | `EffectKeyGuardTest` / `EffectKeyDisciplineTest` |
| 常量集合不可变 | `ConstantImmutabilityTest` |
| 集合遍历 / 打印顺序确定 | `DeterministicCollectionOrderTest` |
| 技能等级表边界 | `SkillLevelTableBoundTest` |
| 欢愉基础等级伤害表 | `ElationBaseTableTest` |
| 控制状态与减速 | `ControlTest` |
| 命中率 clamp 的**上界**（命中 0.32 时返回 1.0 而不是 1.32） | `HitResistTest` |
| 演示至少不抛异常 | `DemoSmokeTest` |
| 触发规则表装载 | `TriggerTableTest` |
| **`enemy_state_count:` 全场状态计数**（状态过滤 / 只数活人 / 空状态名在加载期被拒 / 未知变量提示它） | `EnemyStateCountTest` / `EnemyStateCountSpellingTest` |
| **`chance_rolls:` 规则级独立掷骰**（两次判定都结算、各 50% 伤害；0 次与"无 chance 却要掷两次"在加载期被拒） | `ChanceRollsTest` / `ChanceRollsValidationTest` |
| **§3.5 变量表与加载器零漂移**（双向：代码有文档无 / 文档有代码无 / 标题消失时大声失败而非空集相等） | `TriggerVocabularyDriftTest` |
| **`CRIT_DEALT` 携带施放槽位**（战技暴击才减速、普攻暴击不减速；星魂 6 的 20% vs 12%；星魂 4 的 0 魂不生效；星魂 1 的 12% 只对 ≥50% 生命的敌人） | `DanHengKitCompletionTest` |
| **`is_slowed`**（`SPEED` 上有负修饰即算减速；+20% 加速不掩盖 −12% 减速） | `SlowedConditionTest` |
| **`MODIFY_RULE{cooldown_delta}`**（缩短等待一个回合；点名无冷却 / 结果转负 / 0 都在加载期拒绝） | `CooldownAmendmentTest` |
| **1002 的三句减速随动**（终结技 +120% 走基数层、罡风 +40% 走增伤区、星魂 2 冷却 −1） | `DanHengKitCompletionTest` |
| **地图攻击的入场削韧**（读 slot 6 自己的元素与韧性；非弱点不削；削空即击破；无地图技能的单位拒绝声明） | `MapAttackTest` |
| **伤害乘区的形状**（增伤四因子相乘 / 易伤上限之外的 `Monster_DamageTakenRatio` / 吸收是减法 / 这四个数从两个单位身上读） | `DamageChainFactorsTest` |
| **`HEAL` / `SHIELD` 的份额与常数项都读「当前等级」的参数**（两个半都动 / 只读字面量时会红 / 拼法写错在加载期被拒 / 两种金额来源互斥） | `SkillParamMagnitudeTest` |
| **读「当前等级」的规则不会早于设置等级的那条跑**（`BATTLE_START` 上与 `level_convention` 同事件时，顺序即语义） | `LevelConventionOrderTest` |
| **忆灵的技能等级**（加成记在召唤者、按数据槽位 / 解析器读到并 clamp 到表行数 / `summon_param` 与 `amount_from_cast_param` 两种读法 / 加载期五种拒绝） | `MemospriteSkillLevelTest` |
| **弹射的份额与次数读的是当前等级那一行**（两行两个数 / 行为比值 = 两行份额之比 / 造表让次数也随行变 / **次数优先读正文**「额外造成N次」） | `BounceSkillLevelTest` |
| **回能读技能自己的 `sp_base`**（弹射按段数乘 / 偏离常量的行正好三行 / 两发弹射的 6×5 与 10×3 都是 30） | `EnergyDataConformanceTest` |
| **1002 潜龙：低血仇恨减半，以及它会被收回去**（AGGRO_ADDED_RATIO = -0.5 / 权重 ×0.5 / 治疗过 50% 后归零并移除状态 / 高于 50% 不触发） | `DanHengAggroDownTest` |
| **伤害段数（分裂比）**（普攻 2 段 / 战技 4 段 / 各段是行倍率的分数且和 = 1 / 每段独立暴击 / 同一减速 Refresh 不成四层） | `DanHengSegmentsTest` |
| **与原游戏逐乘区比较**（出货场景跑通 / 错数必红 / 拼错的 zone 必红 / 空期望只是清单不是通过） | `DamageComparatorTest` |
| **1002 每一处数的来源**（天赋穿透随天赋等级 → 星魂 5 为 row12 / 秘技 40% 是份额不是 0.4 点 / 减速 -12% 是行的量乘正文的符号） | `DanHengLevelSourceTest` |
| **1002 的面板与行值与文档一致**（Lv80 882/546.84/396.9/110、行迹 18%/12.5%/22.4%、破韧 30/60/90、终结技能量 100、四个 Lv10 行） | `DanHengPanelAgreementTest` |
| **规则对象的键必须是 Gson 认得的**（写错的键会让规则以 "Unknown trigger event 'null'" 到场 ✓） | `RuleKeyGuardTest` |
| **`scope_element` 的两处拒绝**（元素是封闭集 ✓ / 只许出现在收它的 op 上 ✓） | `ScopeElementGuardTest` |
| **`DEALING_DAMAGE` 的规则不许够到队友的伤害**（缺 `actor == self` 会把句子悄悄扩到全队 ✓；量法是「队里有他 / 没他」两场的队友伤害必须一样 ✓） | `SelfScopedDamageGuardTest` |
| **台账不写"现在是什么"的数字**（套件 / 用例 / 文件 / 方法 / 类 / 规则数、`file:line`、大小、提交数、「N 处」） | `LedgerNumberGuardTest` |

### 7.2 **只靠散文**（所以上面必须写完整）

- ⚠ **三条红线全部只靠散文** ✗ —— 尤其**「引擎不认角色」没有守卫** ✓：
  `src/test` 里搜 `cid` 命中的都是**角色套件自己在用 cid** ✓，**没有一条规则检查
  `Battle` / `SkillExecutor` / `Damage` / `CanHit` 里不出现 `cid`** ✗。
  ⇒ 谁要加这个守卫，这是一个**明确的缺口** ✓。
- ⚠ **随机数禁止 `Math.random()`** ✗（只靠散文）。
- ⚠ **判据的夹具前提不许被抽走** ✗（只靠散文；§6.3·3）。
- ⚠ **提交纪律**（一行英文 / 中文文档 / 两空格 / 产物不入库 / 脚本放仓库外）✗（只靠散文）。
- ⚠ **"同形只准有一条"**（§2.2）**本身也只靠散文** ✗ —— 它没有脚本检查 ✓。
  ⇒ 这是本文件**最该被守卫**的一条 ✓（否则会再长回去 ✓）。
- ⚠ **`build.gradle` 里没有"闸门"任务** ✓（实测：只有 `test` / `pitest` / `delombok` / `javadoc` ✓）
  ⇒ **四道闸门全是手跑的命令** ✓，**没有任何东西强制它们** ✗。
  ⇒ 「跑闸门」这条**只靠散文** ✓ —— 这是本项目最大的一个"没人守" ✗。

### 7.3 已被推翻的守卫（**别再去引用**）

| 曾被当作守卫 | 实测 |
|---|---|
| `SpeedBuffTest`（P10-4 的验收） | ✗ **不存在** ✓ —— P10-4 的"拉条"还没做，它是**将来**的验收条件 |
| `UltraInsertTest`（P10-5 的验收） | ✗ **不存在** ✓ —— 同上 |
| `SkillSelectorTest`（P9-3 的验收） | ✗ **不存在** ✓ —— 同上 |
| tui 一族用例（`anActionReportsTheDamageItDealt` / `extraHitsAreMarked` / `ChoiceFlow` / `scrollLog` / `PgUp`） | ✗ **本 checkout 里搜不到** ✓ —— `src/` 下**没有 tui/ui 包** ✓（**旧** `ROADMAP.md` 的 §3.11–§3.13 引用的是**另一棵树**的用例 ✓） |
| `TestCharacters.withoutTriggerFile()` 是"取最小 id" | ✗ **已过时** ✓ —— 现在**pin `1506`** ＋ `TriggerTables.exists(cid)` 时**大声抛异常** ✓（`TestCharacters` ✓）；⚠ 但它的 **javadoc 还写着"取最小 id"** ✗ ⇒ **注释与实现不符**，以**实现**为准 ✓（§1.3：错的注释让人照着做 ✗） |

> ⚠ **文档说"没做"、其实已经做了的两处**（2026-10-16 实测 ✓，**照抄会白干** ✗）：
> ① **P9-5 的"受击反击"** —— `models/buff/CounterMechanic.java` **已存在** ✓、
> `Main` 已装配 ✓、`BossMechanicTest` 钉住 ✓ ⇒ 实际**只剩"多血条 / 锁血"** ✓；
> ② **欢愉的 `elation_basic_level_damage.json`"从未被加载"** —— **已过时** ✓，
> 现由 `Constant.ELATION_BASIC_LEVEL_DAMAGE` 加载 ✓，`ElationBaseTableTest` 钉住 ✓。
> ⚠ 反向也有一处：**P10-4 那行的"公开 API 还没上移"已过时** ✓（`Battle.delayMovePercent` **已是 public** ✓）。

### 7.4 缺口见证（**不是守卫，只是"能力落地时它会红"**）

> ⚠ 这些**不拦任何人** ✗ —— 它们钉住的是**今天的样子** ✓：能力落地时变红，并指回登记处 ✓（§4.3 要求每条登记都有这么一条 ✓）。
> ⚠ 所以别把它们当"已经守住" ✗：它们只保证**缺口被悄悄填上或悄悄加深**都看得见 ✓。

| 钉住的缺口 | 见证 |
|---|---|
| 欢愉行的三种布局（1506 槽 21 被读成 **1 次 600%**） | `SilverWolfLV999ElationRowTest` |
| 没有 `SkillType` 的槽读不到那一行（1408 槽 5 的 0.8／2.7 不随 E5 漂） | `PhainonSlotFiveRowDriftTest` |
| `ENERGY_GAINED` 不带授予者（1408 行迹 1408102 的后半什么都不做） | `PhainonTraceBideInFlamesTest.theEnergyHalfIsStillANoOp` |
| `Impair` 的 debuff 派发缺输入（没有一条 `Impair` 带 effect 行） | `ImpairDebuffGapTest` |
