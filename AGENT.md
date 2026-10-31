# AGENT.md — 这个仓库的唯一台账

> **一份文件，三个读者。** 这个仓库**只有这一份**台账 ✓。
> 找某条规矩时**只在这里找** ✓；找不到就是没有，**不要去别的文件里猜** ✗。
>
> | 你是 | 读哪节 | 你要回答的问题 |
> |---|---|---|
> | **改引擎的** | §三 | 这台引擎的线在哪、改它必须过什么闸门 |
> | **做项目 / 写内容的** | §四 | 现在是什么阶段、下一步做什么、为什么这样排 |
> | **刚接手 / 要动手的** | **§零** | 五件事（trigger / test / engine / commit / doc）各自的**动手顺序** |
> | **收尾 / 交接的** | §五 | 怎么报、报什么、没做什么 |
> | **谁都该读** | §一 · §二 | 行为准则、这份台账自己的规矩 |
>
> 仓库外的两份资料：`TRIGGER.md`（触发器 JSON 格式——是**规范**不是台账）、
> `HSR.md`（游戏侧公式，正典副本在仓库外）。**"现在是什么"的权威是代码** ✓，
> 以及 `src/main/resources/game/characters/<cid>.json` 里每条规则自带的 `source` / `note` ✓。

---

## 零、怎么动手（**先看这一节，五件事各一段**）

> 每一段只写「**动手时的顺序**」和「**写错的典型形状**」✓；细节都在后面各节，括号里给了 §号 ✓。
> 这一节是**入口**，不是替代品 ✓：它不重复规矩，只负责让你在动手前问到对的问题 ✓。

### 0.1 写 trigger（内容侧：把一句话变成规则）

1. **先读原文那一句** ✓：从数据文件（`game/data/skills.json` 的 `skill_introduction`、`skill_traces.json` 的 `desc`、
   `eidolons.json` 的 `desc`、`game/data/weapons.json` 的 `skill_description`、`game/data/relic_sets.json` 的 `effects[].desc`）
   抄下**整句**，不要凭记忆 ✓。
2. **把句子切成从句** ✓，每个从句问三件事：**触发是什么事件**（§3.6 的口子速查）、**条件是什么门**（`actor == self`？`target == …`？）、
   **数值从哪来**（第 3 条）。
3. ⭐ **数值只要能读出就不用抄成字面值** ✓ —— 这是本节最容易走偏的一步：
   - **技能自己的倍率 / 概率 / 回合数** ⇒ 用 `percent_from_skill_param: "SKILL:0"` 这类**按等级读**的写法 ✓；
     `DAMAGE` 用 `skill:` / `damage_param:` ✓；`scale:` 指定它读的是哪个属性 ✓。
   - **判据「能不能读到」的标准是：数据里这一列**在每一档等级上都存在 ✓。存在就必须读 ✗ 不要硬编码 ——
     硬编码会把「升到高等级」和「星魂 +2 级」全都定死在作者当时看的那个数上 ✓，而且**账本里看不出来** ✗。
   - **只有整列都是同一个常数时**，字面值才可接受 ✓；而且要在 `note` 里写明「这一列每一档都是这个数，所以字面值等价」✓
     （仓库里有先例：`base_chance` 写成字面值的判据就是这个理由 ✓）。
   - **没有 `*_from_skill_param` 这类写法的字段**（例如某些概率）不要临时发明一个 ✗；要么用现成的读法 ✓，
     要么**登记**（§4.3）✓ —— 「发明词汇」和「硬编码」是同一个错的两种形状 ✓。
4. **先查引擎有没有这个词** ✓（§3.7 / §3.7b–3.7zd 索引）；**没有就登记**（§4.3）并写缺口见证，**不要近似** ✗。
5. **`properties` 那一半不要重写** ✓：光锥/遗器的 `ability_property` 由装配点直接施加 ✓，再写一条规则就是**读成两倍** ✗
   —— 这是本仓库最常犯的一类错 ✓。
6. **同一属性、同一身份的修饰会互相替换** ✓ ⇒ 「最多叠 N 层」必须用层缓冲（`ADD_STACK` ＋ `per_stack`）✓，
   把 `max_stacks` 写在修饰上**不叠层** ✗（光锥如此；遗器的该形状**会**累加 ✓，先量再写 ✓）。
7. **每句都要标出来源** ✓：规则的 `source` 抄原文、`note` 写「每个从句对上哪个词、为什么别的词不行」✓；
   若某个数**是按等级读的**，`note` 里点名它读的是哪一列 ✓（否则下一个人无法判断它会不会随等级动 ✓）。
8. **事件没有队友门** ✓：`KILL` / `ULT_CAST` / `SKILL_CAST` / `BASIC_ATTACK` / `TAKING_HIT` / `BREAK` 都是**对每个队友各发一次** ✓
   ⇒ 「使装备者/本人…」的句子必须加 `actor == self`（或 `target == self`）✓ —— 漏门是本仓库第一大族 ✓。

### 0.2 写 test（判据：证明那条规矩真的在起作用）

1. **一次只钉一个性质** ✓，断言落在**后果**上（谁的血少了、哪个属性变了、状态在不在）✓，不落在仪器上 ✗（§1.5、§6.3）。
2. **先跑一次看读数** ✓，再把**实测量**写进断言 ✓；永远不要先编一个"应该是"的数 ✗（§1.11）。
3. **判据要有"反例半边"** ✓：句子说「只在 X 时生效」，就同时断言「不是 X 时不生效」✓（只看正例的判据挡不住漏门 ✓）。
4. **两条真实读数不一致时，先怀疑自己的读法** ✓（§6.3·8 的分母、§6.3·9 的技能等级与实例修饰）：
   - 技能等级读 `skillLevelBonus(slot)` / `skillLevel(skill)` ✓，`skill.getLevel()` 是**基础等级、永不变化** ✗；
   - 实例修饰（`instance: true`）在**面板上永远看不到** ✓ ⇒ 判据落在「这一发是否暴击 / 这一发的区值」✓。
5. **夹具要造出句子点名的那样东西** ✓：要「带盾的队友」就真的给盾 ✓、要「灼烧目标」就真的先上灼烧 ✓；
   但如果**夹具自己造出**了句子条件，判据只证明了它自己 ✗（§6.3 的「制造前提」条）。
6. **一条判据写死旧 bug 的签名时** ✓，改内容后它会「有理有据地红」✓ ⇒ **改断言时写清旧断言钉的是哪条 bug** ✓，
   并把断言换成**性质**而不是换一个数字 ✓（§6.3·6）。
7. **改了引擎，先问「哪条用例会因此变红」** ✓；一条都不红说明这条规矩**没有把守** ✓（§3.1 推论）。

### 0.3 写 engine（改引擎：能力先于数据）

1. **红线三条**（§3.1）：引擎**不认角色** ✓、**能数据化就数据化** ✓、**一个任务自带判据** ✓。
2. **改引擎前先问：这是"能力"还是"某个角色的句子"** ✓ —— 是后者就回内容侧 ✓（§4.1 逃生舱表）。
3. **新增一个词（事件 / 条件 / op）要同时做四件事** ✓：解析（`TriggerConditionParser` / `TriggerOperator`）、
   执行（`TriggerInterpreter`）、**校验**（`TriggerValidator`，未知词必须**报错**而不是静默 ✗）、以及一条**引擎侧判据** ✓（§3.4 的范式）。
4. **改完必须过四道闸门** ✓（§3.3）：**一条命令 `gradlew gate`** ✓（全量测试 + 五种 demo + 每份捕获 `compare` + `replay --strict` + `javadoc` ✓）、demo 还要与**基线**逐行比 ✓、外加**变异验证**
   （把改动反向注入一次，确认**目标用例变红**、再按字节还原 ✓，§6.4）。
5. **引擎与内容分开提交** ✓：回退粒度不同 ✓（§5.1）。
6. **报告里写清「跑了什么 / 没跑什么」** ✓（§5.2）：`MATCH` 是跑出来的，不是推断出来的 ✓。

### 0.4 写 commit（提交：一个提交只做一件事）

1. **一行英文** ✓，动词开头、说清动的是什么 ✓；中文只出现在**引号里的原文** ✓（§5.1）。
2. **分开提交** ✓：引擎 / 内容 / 判据 / 文档**各一个** ✓ —— 回退粒度不同 ✓。
3. **提交前跑全量测试** ✓，并报「套件数 / 用例数 / 失败数」✓；**红的绝不提交** ✓（§3.3）。
4. **提交里不放**：构建产物 ✗、一次性探针 ✗、分析脚本 ✗（脚本放**仓库外** ✓，§5.1）。
5. **提交信息里不要写会腐烂的数字** ✓（「N 处」「N 个文件」）—— 账本守卫会拦 ✓（§2.4）；
   要写数量就**现量并说明是量到的** ✓。
6. **用户工作区里的改动不要替他提交/回退** ✗（除非他明确要求 ✓）。

### 0.5 写 doc（文档：账本是唯一台账）

1. **一条规矩只写在一个地方** ✓（§二 反增长条款）：**能查到的别抄**、**能链接的别复述** ✓。
2. **状态表（`CONTENT-STATUS.md`）** 每行写**依据**（读数 / 处置）✓，不要写「已核对」这种不可验证的话 ✗。
3. **每个数字都要量过** ✓；量不到就写「这一层我没量」✓（§1.11）。
4. **缺口登记必须能回答三件事** ✓（§4.3）：读者是谁、缺的到底是什么词、缺口见证是哪条判据 ✓。
5. **改了内容就同步状态行** ✓；**改了状态行就单独一个 docs 提交** ✓（§5.1、§4.5）。
6. **账本里不要留行号 / 套件数 / 用例数** ✗ —— 它们会腐烂 ✓；写**符号名**和**§号** ✓（§2.4）。

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

> **谁读**：动 `Battle` / `SkillExecutor` / `Damage` / `CanHit` / 领域包（`entity/` `state/` `damage/` `buff/` `trigger/` …） 的人 ✓。
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
| **引擎** | 通用能力（伤害 / 能量 / 韧性 / 行动条 / buff / 资源） | `Battle`、领域包（`entity/` `state/` `damage/` `buff/` `trigger/` …） | 与具体角色无关，**不得出现 cid** |
| **数据** | 「在某事件后，做一件**引擎已有能力**的事」 | `characters/*.json`、`relic_sets/`、`light_cones/` | 触发器 + 条件 + 效果能写完 ⇒ **不写 Java** |
| **逃生舱** | 需要跨系统状态机，数据表达不出来 | 一个 Java 类 + §4.1 登记 | **登记了**才算合规 |

⭐ **可验收的标准**：**「新增一个角色机制时，引擎目录下应该一行都不用改」** ✓。
改不动就是**层定错了**或**引擎缺能力** ✓ —— 两条路的做法完全不同 ✗。

### 3.3 四道闸门（**每一批都必须过**）

⭐ **一条命令**：`.\gradlew.bat gate --console=plain` ✓ —— 跑「全量测试 ＋ 五个 demo ＋ 每份捕获的 `compare` ＋ `replay --strict` ＋ `javadoc`」✓，任何一步非零就**整条失败** ✓，末尾打印一行 `GATE: PASS tests=… failures=… suites=…` ✓。

- ⚠ 它**强制** `:test` 真跑 ✓，**也强制四个 `compile*` 任务**真跑 ✓（同一个 `outputs.upToDateWhen` 口子）—— 后者是 2026-10-29 量出来的：把类挪到**新包**之后，Gradle 的增量编译既不报错也不编译它，`compileTestJava` 报 UP-TO-DATE 而四个测试类**没有 .class** ⇒ `:test` 只跑了一个更小的套件，闸门照样打印 PASS ✗（见 §6.1(i)）。
- ⚠ 它**不做**两件事，别以为做了 ✗：与**基线**逐行比 demo 输出（那要人判断"这个改动是故意的" ✓，见下）、**变异验证**（§6.4 是按改动做的 ✓）。

下面是它逐步做的事（要单跑某一步或要对比基线时用）✓：

```powershell
# 1) 全量测试 —— 必须报出 套件数 / 用例数 / 失败数
#    ⚠⚠ 必须带 --rerun-tasks：不带它时 Gradle 会报 `Task :test UP-TO-DATE` 并把**上一次的
#    XML 原样写回**（几秒就结束）⇒ 你读到的是一份**看起来全新**的旧结果。
#    判据是 Gradle 那一行写着 executed（真跑要十几秒），不是 XML 的文件时间。
#    ⚠ **读文档的守卫要把文档声明成输入**（`build.gradle`：`inputs.file('AGENT.md')` / `inputs.file('TRIGGER.md')`）✓
#    —— 否则**只改文档**时 `:test` 是 UP-TO-DATE，守卫**根本不跑** ✓。2026-10-29 实测：改坏 `TRIGGER.md` §4 的表，
#    新守卫仍然绿 ✓；补上 `inputs.file` 后同一处改动**三例全红** ✓。⚠ 这也意味着旧的那条 `TriggerVocabularyDriftTest`
#    此前在"只改 TRIGGER.md"的情形下一直是**没跑**的 ✗。
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

# 5) 编译期悬空 Javadoc：build.gradle 已加 -Xlint:dangling-doc-comments -Werror
.\gradlew.bat compileJava compileTestJava --rerun-tasks --console=plain
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
- ⚠ **2026-10-21 起多一道编译期 Javadoc 门** ✓：`build.gradle` 的 `-Xlint:dangling-doc-comments -Werror` 把悬空 `/**` 直接变成编译失败；已用临时探针验证（门真的红）✓。它查的是**注释有没有挂到声明**，不是 `@param` 缺失；`javadoc` 的 `missing` 口子仍按上面的记录保留 ✓。
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
| `damage.Damage` | `toValue()` / `breakdown()` / 7 个乘区 accessor / `addBoost` 等装配口 / `crit` / `trueDamage` / `notCountsAsAttack` |
| `entity.CanHit` | `getAttribute` / `takeDamage` / `heal` / `getBuffManager` / `getLevel` / `getCamp` / `getEnergyProvider` / 事件转发 |
| `state.Resource` | `gainClamped` / `gain`（显式溢出）/ `spend` / `spendExactly` / `setMaxOverflow` / `isFull` / `isCapped` |
| `skillpoint.*` | `SkillPointPolicy`（接口）/ `StandardSkillPointPolicy`（基础规则 + `gainForCast` 覆盖点） |
| `SkillData` | `getElement()` / `getEffect()` / `getStanceList()` / `getSkills()`（倍率表）/ **`getCategory()`** / `getSpNeed()` |
| `Queue` | `move` / `setTopZero` / `delayAction` / `advanceActionByPercent` / `refreshSpeed` / `grantExtraTurn` |
| `utils.CharacterFactory` | `create(cid, level)` / `usesSpecialResource(cid)` / `exists` |
| `enemy.EnemyFactory` | `create(monsterId, level, hardLevelGroup)` |
| `utils.StageFactory` | `load(stageId)` |
| `Constant` | `CHARACTERS` / `WEAPONS` / `SKILLS` / `SKILL_TRACES` / `MONSTERS` / `SKILL_SLOT` / `stages()`（懒加载） |

**包 → 放什么**（2026-10-29 拆过一次：`models/` 那层已经**去掉** ✓，领域包与 `beans/` `data/` 平级 ✓；找类先看这张表 ✓）：

| 包 | 放什么 |
|---|---|
| `com.laosun.aluminium`（根） | `Battle` / `Queue` / `Constant` / `BattleEvents`（战斗与行动条的**门面**） |
| `entity` | **会挨打的东西**：`CanHit`（抽象基类）· `Character` · `Summon` · `Countdown`（`Enemy` 在 `enemy/`） |
| `state` | **挂在单位身上的状态**：`AttributeSheet` · `EnergyState` · `HealthState` · `ShieldState` · `SkillSet` · `SkillBonuses` · `Resource` / `ResourceManager` · `TriggerCooldowns` · `RuleLimits` · `RuleAmendments` · `RuleChanceState` · `ConditionalHealBonuses` |
| `damage` | **一发伤害**：`Damage` · `BattleDamage`（装配）· `BreakDamageCalculator` |
| `equipment` | **装备**：`Relic` · `RelicSuit` · `Weapon` · `ExtraBasicPromote` |
| `battle` | **从 `Battle` 拆出来的协作者**（39 个服务）＋ `WaveManager` |
| `buff` | `AbstractBuff` 家族 ＋ `BuffManager` |
| `trigger` | 触发器的五个协作者（词表 / 校验 / 解释 / 幅度 / 目标）＋ 表与解析器 |
| `skill` / `enemy` / `event` / `energy` / `ai` / `skillpoint` | 按名字读 |
| 主包根 | `Battle` / `Queue` / `Constant` / `BattleEvents` ＋ 两个跨包的值类型 `DoubleValue`（数值＋修饰链）· `Signal`（行动条上的一项） |
| `beans` / `data` / `enums` / `utils` / `exceptions` | JSON DTO / 装载与生成表 / 枚举 / 工厂与工具 / 异常 |

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
⭐ ✅ **「每有 #3 点【忆质】额外造成 #2%」已落地**（2026-10-29）：`skill_effects.json` 的 `resource_share` ⇒ `(资源 / #3) × #2` **并进那六发的份额** ✓（不是额外实例 ✓ —— 捕获同一发 0.06 → 0.09、段数仍是六 ✓）。读到的是**召唤者**的资源 ✓（句子写「长夜月当前每有…」，与 `Summon.damageBase` 同一条"用主人的面板"规则 ✓）；资源未注册时按 0 ✓。⚠ 顺手修了**一半**：原来那条「施放后获得 #4 点」挂在 `CAST_SETUP`（伤害展开**之前**）⇒ 这一发白吃 1 点（0.06 读成 0.063 ✗）；改成 `ATTACK_FINISHED` 后与正文「施放后」一致 ✓，捕获也支持（窗口里第一发的份额正是 0.06 ✓）。【已把守：`EvernightMemoryFoldTest`（0 / 4 / 10 点 ⇒ 0.60 / 0.72 / 0.90，Lv10 另测一次 ✓）】
**仍开**：① 演示/回合路径仍走忆灵规格里的单段攻击（`COMMON` ✓），换成数据技能会改 demo 输出 ✓ 属另一件事 ✗；② replay 的 cast 4 仍是**窗口前缀**（游戏那一发带 10 点，重放从 0 开始、只有第一发给的那 1 点）⇒ 工具报 `DIFFERS-x0.70` ✓，这是**它不该凭空造状态**，不是引擎缺口 ✓。

#### 3.7i 两段式强化普攻（一行多段、各段自己的目标形状）
落地：`skill_effects.json` 的 **`damage_parts`**（每段 `shape` ＋ `index` ✓，按表顺序结算 ✓，削韧各取自己的列 ✓）【已把守：`CyreneEnhancedBasicTest`、`CyreneEnhancedBasicPartsTest`】。
**已收口**（2026-10-29）：① 各段**削韧** —— 游戏自己的 `AvatarSkillConfig.ShowStanceList`（141508，Lv1..Lv10 一致）= **`[30, 15, 0]`**，而生成器把它按 `{single: [0], all: [1], spread: [2]}` 读 ⇒ **单段 30、全体段每敌 15** 正是官方表 ✓（此前那句"`Phase02` 写死 15"是误读，没有 `ShowStanceList` 强 ✗）；② 强化普攻「获得 **3** 点【追忆】」现在由 `basic_recollection` 无条件给 **1** 点 ✓ ⇒ 需要按 `from_skill_id`（1 vs 8）分两条 ✗（没做 ✓）。

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
⭐ ✅ **`11512` 槽 1 的倍率在 `#2`**（`#1` 每一档都是 0 ⇒ 默认读法结算出 0 ✓）：`skill_effects` 用 **`damage_parts[].index`** 声明 ✓，默认读法不动（改默认会把语料其余每一行读错 ✗）【已把守：`SummerSongbirdSkillOneTest`（Lv10 `#2`=2.1 / Lv6 `#2`=1.5 两档 ✓）】
⭐ ✅ **对账口径改为 `base`**（`zones.raw_damage_before_modifiers` ↔ `breakdown().base`，逐实例、按多重集比）✓：settled 总额**只打印、不对账** ✗ —— 计划故意不装暴击/增伤，而捕获是打了一半的窗口 ⇒ 总额**按构造就不可比** ✓。每发三种判词：`base-ok` / `base-ok/count`（值同、实例数不同：捕获里另两只敌人已经倒了，引擎把它们都算活着）/ `base-n/a`（`damage_percentage == 0` ⇒ 这一发的基数是**计数**不是行份额，见 1409）。**每一发按捕获自己那一发的面板驱动** ✓（窗口里 1413 的生命上限 2960 → 4526 → 4309 是三次不同的题 ✓），忆灵的表按其生命上限份额跟着走 ✓。
⭐ **差异的形状先被读出来了** ✓：对不上的忆灵发，倍数**恰好是该忆灵的生命上限份额** —— 长夜 ×0.5、晴空乐手 ×0.7（1413 的终结技也 ×0.5，因为它由「长夜」交付）✓。⚠ 这条当时被写成"读谁的仍分不开" ✗ —— **它是分得开的**，办法不在 payload 里，在游戏数据里（见 3.7ze ✓）。改完之后这些发是 `base-ok` ✓；cast 4 当时还差 ×0.67 = 0.06/0.09，那是「每 3 点【忆质】额外 #2%」被游戏折进同一发（3.7h ✓），折叠落地 + 前缀声明后它也 `base-ok` 了 ✓。
⭐ **折叠组排查（2026-10-30）**：捕获里的 5 个 folded 组仍然 0 匹配，但**排除法**已经走完两条 —— ① 它们**不是**该次施放自己发放的规则伤害（那一条已经作为该行的 `hitBases` 逐位对上了 ✓）；② 它们**也不是**任何一条捕获命中的 24% 真伤骑手 ✗ —— 引擎的真伤骑手只跟"算作攻击"的实例，而**规则发放的伤害不算攻击**（**这是游戏自己的口径** ✓，用户确认 ✓）；且把每个 folded 基值除以捕获列出的**全部**命中基值，都得不到一个"文档会写"的比例 ⇒ 它们的父命中**不在这份 payload 里** ✓。⇒ 剩下的 4 条 Additional + 1 条 Break 是**窗口本身缺数据**，不是引擎缺能力 ✓（**Break 那条已收口**：把捕获自己的三个数代入引擎公式 —— 等级 80 的 `BREAKING_RATE/10` = 376.75535、捕获的 `break_damage_added_ratio` = 0.15552、**实际被吃掉的韧性 25** —— 得 **10883.708573**，捕获是 **10883.708128**（相对 4e-8 ✓）⇒ **公式没有缺口**，缺的是**窗口开始时的韧性条状态**（payload 不带韧性）✓；判据 `BreakBaseAgainstCaptureTest` 钉住，并顺带钉住"按名义 30 结算会高 20%"✓）。
⭐ ✅ **`--strict` 在提交的 fixture 上是绿的** ✓：所有有可比 `base` 的发都对上；`damage_percentage == 0` 的发判 `base-n/a` ✓；**13/13 发都可驱动** ✓（cast 6 的两种新形状已落地，见 3.7zf ✓）—— 判不了的与驱动不了的都**指名**，不混进"对不上" ✗。
**仍开（对账差异，逐条登记）**：忆灵技的行号（捕获像 Lv8/Lv6，规格写 10 ⇒ 计划用 `level` 逐发声明 ✓）· cast 6 ✓ **已收口**（1141307「迷梦，流失，如露」，解码见 3.7zf ✓；计划里它是槽 7、Lv1、起手 50 点【忆质】✓）· 敌人没有韧性条（不加那 10% 减伤 ✓，只影响 settled、不影响 `base` ✓）· 窗口前缀（捕获是一段已经在打的战斗 ⇒ 计划用 `resources` **逐发声明**、工具打印 `seeded:` ✓；声明的是**从捕获算术反解出的数**，不是猜的 ✓）。

#### 3.7zc 1413 忆灵技 1 的「施放后获得 #4 点【亿质】」
`ATTACK_FINISHED` ＋ `actor == summon` ＋ `from_skill_id == 1` → `GAIN_RESOURCE 亿质 amount_from_cast_param: 3` ✓（点数**读这一发自己的行** ✓）。⚠ 资源名是数据里的「**亿质**」不是「忆质」✗（写错只会得到一个恒 0 的计数器 ✓）。⚠ **事件在 2026-10-29 从 `CAST_SETUP` 换成 `ATTACK_FINISHED`** ✓：正文说「施放**后**」，而 `CAST_SETUP` 在伤害展开之前 ⇒ 那一发会白吃 1 点（实测 0.06 → 0.063 ✗）；捕获里第一发的份额正是 0.06 ✓。【已把守：`EvernightMemoryGainTest`（他人的忆灵施放一点不给 ✓）、`EvernightMemoryFoldTest`（0 点 ⇒ 份额 0.60 ✓）】
✅ 另一半「每有 #3 点【亿质】额外造成 #2%」也已落地（`resource_share`，见 3.7h ✓）。

#### 3.7zd（引擎）三个「句子缺一个读数」的补口
| 能力 | 之前为什么写不出来 | 现在是什么 | 判据 |
|---|---|---|---|
| `ATTACK_FINISHED` 带 `from_skill_id` | 它传的 `skillId` 是 **0** ⇒ 无法说「施放**那一招**的攻击后」 | `fireAfterAttack` 新增带槽位的重载 ✓；四参数旧口子 = **未知**（`EnemySkill` 与既有判据仍在用 ✓） | `PhainonEidolonSixTest` |
| `amountFromEvent` 作 `DAMAGE` 基数 | 字段早就有，**DAMAGE 路径没读者** ✗；`settled_damage_this_event` 在最外层 firing 清零 ⇒ 读它恒 **0** ✗ | `literalBase` 读 `amountFromEvent × percent` ✓，装载期**只准配真伤害** ✓。⚠ **不是**新造 `scale: "event_amount"` ✗（它被**撤回**过 ✓） | `EventAmountTest.theAttackTotalCanBeTheBaseOfATrueRider` |
| `RAISE_RESOURCE_CAP{unbounded: true}` | 溢出额度是**一个数**，而「不再拥有上限」不是一个数 ✗（`MAX_VALUE - 3` 会在 `+raise` 里溢出成负数 ⇒ 夹回 0 ✗） | 额度置 `Integer.MAX_VALUE` ✓（与【毁伤】的 `max: 2147483647` 同一约定 ✓）；两个拼法互斥 ✓ | `ResourceOverflowUnboundedTest`（两端 ✓） |
| `BOOST_DAMAGE` 的 `per_stack: target_debuff_count` | `boostDamage` **完全没读 `per_stack`** ✗：1/2 层都只加一次 0.12，只有「达到上限」的分支在涨（光锥 21001 逐句核时量到） | `boostDamage` 与 `MODIFY_ATTR` 的 instance 路径共用 `perStackFactor(effect, ctx.target(), ctx)` ✓ | `Cone21001Test`（`BoostArea` 0.12 / 0.24 / 0.36，4 层仍 0.36） |
| `has_shield from_provider self\|actor\|target` | `has_shield` 只能问「有没有盾」或「哪条规则造的」✗；「装备者提供的护盾」问的是**谁造的** ✗（遗器 128 四件套只能用裸 `has_shield` 近似） | `TriggerConditionParser` 解析 `from_provider`，`TriggerTable.HasShield` 用 `CanHit.getShieldProvider()` 比对 ✓ | `ShieldProviderConditionTest`（装备者盾 ✓ / 队友盾 ✗） |

⚠ 同族仍开：**单位计数器的「这次花掉了多少」没有事件** ✗（唯一的「花掉」是 `SKILL_POINT_SPENT` ✓ 战技点专属 ✓）⇒ 1408 星魂 2 的后半写不了 ✓（不拿 `self_resource:毁伤 >= 4` 冒充 ✗：那是「持有 4 点」✓）。

#### 3.7ze（引擎）忆灵的「%生命上限」读**召唤者**的生命上限

`CanHit.damageBase(attribute)` 是**伤害基数的唯一读口** ✓（`SkillExecutor` 的六处装配与 `EnemySkill.strike` 都走它 ✓）；`Summon` 覆盖它：`HEALTH` ⇒ 读 `master` 的生命上限 ✓，其余属性读自己的 ✓。

⭐ **三份证据，按查的顺序**（payload 一个字段确实分不开，但游戏数据分得开 ✓）：
1. **捕获**：忆灵每一发的 `raw_damage_before_modifiers` 逐位等于 `行份额 × 召唤者的生命上限` ✓（长夜 0.06 × 2960.0287、晴空乐手 1.5 × 5249.7811 ✓，同一段窗口里三个不同面板都对 ✓）。对账口径改成 `base` 后，这些发从 `DIFFERS-x0.5` / `x0.7` 变成 `base-ok` ✓。
2. **`ServantConfig.SyncPropertyExceptList`**（`Config/ConfigCharacter/Servant/*_Config.json`）列的是**不同步**的属性 ✓：长夜 / 晴空乐手 / 衣匠 / 迷迷只列 Speed（晴空乐手还列 `BaseHP`）⇒ **MaxHP 跟着召唤者走** ✓；死龙（Castorice）与德谬歌（Cyrene）列了 `MaxHP`/`CurrentHP` ⇒ 它们有自己的血条 ✓。
3. **ability 的 `FormulaType`**：绝大多数的伤害任务写 `ByMaxHP` ✓，而 `BySummonerMaxHP` **只出现在死龙的配置里** ✓ —— 正好是第 2 条里"HP 不同步"的那一只 ✓。两种拼法互补，说明 `ByMaxHP` 对"HP 同步"的忆灵就等于召唤者的 ✓。

⚠ 这条**不动忆灵自己的面板** ✓：`memosprites/<cid>.json` 的 0.5 / 0.7 仍是它**血条**的起点 ✓（`getMaxHp()` 读到的还是它 ✓），只是伤害基数不再用它 ✓。

【已把守：`EvernightServantSegmentsTest`（六段 × 0.7 × **主人**的上限 ✓）、`SummerSongbirdSkillOneTest`（Lv10/Lv6 两档 ✓）、`SummonCommandTest.theBaseIsTheOwnersMaxHp`（主人翻倍⇒×2 ✓，忆灵翻倍⇒不动 ✓）、`MemospriteAttackTest.theDamageIsProportionalToTheSummonersMaxHp`（同 ✓）、`BattleReplayTest.theReportComparesTheBaseAndNamesWhatIsLeft`（`DIFFERS-x0.5`/`x0.7` 不再出现 ✓）】

#### 3.7zf（引擎）按**点数**结算的份额 ＋ 让忆灵**离场**

两个新形状，读者都是 11413/7「迷梦，流失，如露」（正文 TextMap `7843681538325136114`）✓：

| 形状 | 是什么 | 为什么既有能力都不行 |
|---|---|---|
| `skill_effects` 的 **`per_resource_share`** | `份额 = 行里的 #主 × 当前点数`（主目标）、`#其他 × 当前点数`（**其他全部**敌人） | `resource_share` 是**加**一个台阶份额（3.7h），这里是**乘**；`BLAST` 只分主目标 + 相邻，这里其他目标是**全部**其余敌人 ✗ |
| 新 op **`DISMISS_SUMMON`** | 走 `perish()` 让召唤物**离场**（不是死亡）✓，随后 `SUMMON_LEFT` 照常公告 ✓；目标不是召唤物 ⇒ **报错** ✓ | 引擎原先没有"让忆灵消失"这个动作（`SUMMON_LEFT` 只是**事件** ✓）；`KILL` 会诬告一个击杀 ✗ |

⭐ **捕获就是判据** ✓：cast 6 的主目标 `3.0000000028`、其他 `1.5000000014` = `0.06 × 50` / `0.03 × 50` ⇒ **50 点【亿质】、Lv1 行** ✓（Lv10 行要 17.857 点，不是整数 ⇒ 排除 ✓），2:1 正是 `#1:#2` ✓。⚠ 忆灵的**数据槽位是 `SkillID % 100`**（`generate_data.py`）✓ ⇒ 11413 的槽是 [1,3,5,6,7]，**没有槽 8** ✗（旧注释的 `slot_guess 8` 是猜的）。

「**施放后**，消耗全部【忆质】和生命值，并使「长夜」消失」在**规则侧** ✓（`characters/1413.json`，`ATTACK_FINISHED` + `from_skill_id == 7`）：`SPEND_RESOURCE spendAll` ✓ ＋ `CONSUME_HP percent 1.0`（op 内部**下限 1 点** ✓）＋ `DISMISS_SUMMON` ✓。⚠ 挂 `ATTACK_FINISHED` 而不是 `CAST_SETUP`：伤害要按**这一发开始时**的点数算 ✓，先清零就会打出 0 ✗。

✅ **门已落地**（2026-10-29）：「【忆质】≥ #3（16）才可施放」用 `skill_effects.json` 的新字段 **`castable_when`**（拼法与规则的 `when` 完全一致 ✓），由 `SkillCastability` 在**两个施放入口**（`CastService.useSkill` / `Battle.castImmediate`）把关 ✓ —— 不足时**拒绝施放**（消耗为零 ✓），不再按当时点数结算 ✗。主语是**召唤者的**计数器（忆灵的门读 1413 的【忆质】✓，与 `Summon.damageBase` 同一主体 ✓）。

【已把守：`EvernightDreamDumpTest`（50 点 ⇒ 主/其他 `3.0`/`1.5` ✓；0 点 ⇒ 0 ✓；施放后忆质清空、**又被她自己的天赋退回 2 点**（同一条「消耗生命值」触发 ✓）、她降到 **1 点生命**且**没有倒下** ✓、长夜**离场** ✓）、`BattleReplayTest`（**13/13** 可驱动 ✓）】

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
| `account.json` | **本机生成**（`game/data/` 被 gitignore，与 `dump_data.json` 同）：账号存档 `archive_output-*.json` 的摘要 —— `characters[cid] = {level, ascension, eidolon, skills{basic,skill,ult,talent}, memosprite{skill,talent}, traces}`、`light_cones[cid] = {rank, level}` | 四人 `skills` / `memosprite`：1409 `{6,10,10,10}` / `{6,6}`、1413 `{2,10,10,10}` / `{6,6}`、1415 `{6,10,10,10}` / `{6,6}`、1512 `{1,6,10,10}` / `{6,6}`。**行号从此读它**（`BattleReplay.accountLevel`），不再手写；捕获不携带行号 |
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

#### ⏳ 1415 的强化普攻（槽 8）实例结构：捕获 7 段 / 3 段，引擎 2 段 / 4 段（2026-10-30 实测）

捕获逐段（cast 59，3 个敌人）**实测**：

| 顺序 | 目标 | `base / pct` | 份额 |
|---|---|---|---|
| 1 | 主目标 | 3280.5486 / 0.30 | **0.30**（单体）|
| 2–4 | 三个敌人各一发 | 984.1646 / 0.09 | **0.09**（= 0.30 × 0.3）|
| 5–7 | 三个敌人各一发 | 2296.3840 / 0.21 | **0.21**（= 0.30 × 0.7）|

⇒ 结构是「单体 0.30 + 范围 0.30 **拆成 30%/70% 两段**」：3 敌 → 1+3+3 = **7 段**，1 敌 → 1+1+1 = **3 段** ✓ 与捕获的 `7h/2t`、`3h/1t` 完全一致；`base / pct` 恒等于她的生命面板（10935.16 / 11037.62）⇒ 基数是**召唤者满生命** ✓。
`skills.json` 里该行 Lv6 = `[0.30, 3, 0.30]` ✓（账号 `skills.basic = 6` ✓，与捕获的 0.30 一致）。

⚠ 引擎现在把 `damage_parts`（`[{SingleAttack, #3}, {AoEAttack, #1}]`）读成 **1 + N 段**，所以 59 出 2 段、76 出 4 段 ✗。试过给 `DamagePart` 加 `ratios: [0.3, 0.7]`（引擎侧按比例拆实例）：结果 59 仍 2 段、76 变 6 段 ✗ —— 说明**比例被读到的位置不对**（很可能 `damage_parts` 与 `segments` 两条口径在 1415/8 上互相覆盖）。~~该实验已整体回滚~~（2026-10-30 二次实验：**已落地**，见下）。

**2026-10-30 定论（已落地）**：`DamagePart` 现在可以带 `ratios`（一个 part 的份额按比例拆成多个实例，toughness 均摊），`1415/8` 的范围半边写作 `"ratios": [0.3, 0.7]` ⇒ **3 敌 = 7 段、1 敌 = 3 段**，与捕获的 `7h/2t`、`3h/1t` 逐段一致（`CyreneEnhancedBasicTest` 已按实测改成 2 敌 ⇒ 5 段、基值 `0.42 / 0.126 / 0.294 × HP`、area 削韧 15/2 = 7.5）。

⚠ 第一次实验"失败"的真正原因**不是**比例读错，而是**顺序**：`skillOf(...)` 在 `runPreludes(...)` **之前**执行 ⇒ 前奏放的终结技还没把槽 8 装进 `COMMON`，59 号拿到的是槽 1 的普通普攻。把前奏移到 `skillOf` 之前、并在每个前奏后 `processRequests()`，59 才真正读到槽 8。

⚠ 仍存的偏差（**不影响基值**）：59/76 的**实例数**是 14/6（捕获 7/3）—— 恰好一倍。基值逐段一致，多出来的一份来自**前奏终结技附带的额外一击**在她那一发里就结算了（她的资源里有 `忆灵技的额外一击`）。捕获里那次额外行动**不在这两个 cast_index 内**（若无伤害则不产生事件），所以这是"额外行动该算作独立一发"的语义问题，不是公式问题。下一步：把前奏的结算完全隔离出窗口。

⚠ 2026-10-30 又试了一种："前奏之后把「忆灵技的额外一击」清零" ⇒ **无效**（59/76 仍 14/6）。说明多出来的那一倍**不是那个计数器**造成的，而是一次**被排队的完整普攻**（她的额外行动在引擎里是"再放一次"，而不是"多打一段"）。该实验已回滚（工具未保留 `clearResources`）。⇒ 要精确到 7/3，需要按**施放边界**归因结算，而引擎目前不给结算打施放标签。

#### ⏳ 规则发放的伤害分三类，各有各的算式（2026-10-30 实测，**已定名**）

那 5 行 `base-n/a` 是 1409 的忆灵技施放衍生的 `Additional`，`damage_percentage` 为 0（基数由规则算，不是「面板 × 份额」）。按新 payload 的 `rule` 块与实际数字，全场分成三类：

| 类 | 谁 | 算式 | 实测 |
|---|---|---|---|
| `HEAL_TALLY` | 1409 忆灵本体那一发（`Servant`）| `base = 累计治疗 × params[0]` | cast 9：base 10334.0262、`params[0]` = 0.22 ⇒ **累计治疗 46972.85** |
| `ATTACK` | 那 5 行 `Additional` | `base = attacker_attack × MDF_DamageRatio` | `base / attacker_attack` = **1.068333 / 0.933343 / 2.708076 / 0.847983 / 0.855299** |
| `RULE` | 1415 结界的真实伤害（`True`）| `base = 0.24 × 父命中的结算值` | 相邻父命中可验：`136796.6359 × 0.24 = 32831.1926` ✓；53 条里 **21 条**可按"紧邻上一条"验证，其余需要**父命中归属**（payload 不标父）|

⚠ 我先前把 `Additional` 当成 1409 的累计治疗规则 **是错的**（那 5 行是攻击力乘区）；已按实测更正。⚠ 1409 的 E6 文档（`[0.12, 0.2]`）里 **0.2 的全属性抗性穿透**在内容里已有（`eidolon_six_gives_the_party_all_type_resistance_penetration`）✓，而「清空累计治疗数值改为 12%」这一半还**没有**落地（现有规则的份额读的是行参数 `cast_param 0`）。

**工具已就绪**：`rule-audit <capture.json>` 识别 `HEAL_TALLY / ATTACK / RULE` 三类，并对 `ATTACK` 行做"`base / attacker_attack` 是否等于它自己声明的 `damage_ratio`"这一步自检、对 `HEAL_TALLY` 行反解计数器（`RuleAuditTest` 四例钉住）。当前两份捕获的 `rule` 块都还是 `RULE=64`（旧 DLL）⇒ 5 行要变可比，需要**用修好 chunk 解析的新 DLL 重录一份**。

#### ✅ 0.42 的出处找到了：1415 的**星魂 2**（2026-10-30 结案）

问题：同一份录像里，`True`（结界真伤）对同一个父命中出现两种比率，各 19 条：**0.24** 与 **0.42**；与攻击者、与 `buffs` 都无关（对照组唯一差异是 `MServant_Hyacine_Resist`，那是 1409 的忆灵修正，无关）。

出处（`E:\code\blog\hsr\1415_昔涟.md` §星魂 2）：

> 进入战斗时，额外获得 12 点【追忆】。**每有 1 名不同的我方角色获得德谬歌忆灵技的增益效果，我方目标通过战技的结界造成的真实伤害倍率提高 6%，最多提高 24%**。

参数 `[12, 0, 0.06, 0.24]` ⇒ **加法**，不是乘法：

| n（持有德谬歌增益的我方角色数）| 0 | 1 | 2 | **3** | 4 |
|---|---|---|---|---|---|
| 结界真伤倍率 | 0.24 | 0.30 | 0.36 | **0.42** | 0.48（= 0.24 + 上限 0.24）|

⇒ 捕获里的 **0.42 = 0.24 + 3 × 0.06** ✓ 19 条全部对上；0.24 那 19 条就是 n = 0。乘法会得 24 × 1.18 = 28.3%，对不上 ⇒ 加法成立。

我们的内容是 `zone_true_damage_rider`（`DAMAGE percent 0.24, scale original_damage`），**固定 24%**，没有这个星魂 2 的加算 ⇒ 这是**内容缺口**（不是 payload、也不是驱动器的）。

**2026-10-30（第六份录像 f）实测补强**：`buffs` 里那条"德谬歌忆灵技的增益效果"就是 **`MServant_CyreneServant_00_AmazingBuff`**（同组的 `MServant_Cyrene_00_Rank02_ThanksGift` 也一样），它出现在**恰好 3 个**我方单位上：`[1409, 1413, 1415]` —— **1512 不在其中** ⇒ n = 3 ⇒ 0.24 + 3 × 0.06 = **0.42** ✓ 与 21 条 0.42 rider 一致，也解释了为什么不是 0.24 × 1.24 = 0.2976（乘法对不上）。

**上限**：录制者确认「n 实际封顶 4」（对应 +24%）⇒ `0.24 + 0.06 × min(4, n)`，最大 0.48。

**tbgd 实证（2026-10-30，德谬歌能力文件）**：那个「增益」是**按 cid 分变体**的——`Servant_CyreneServant_00_Skill01_Phase02` 的 `OnStart[12]` 加 `MServant_CyreneServant_00_AmazingBuff_Hyacine`、`OnStart[13]` 加 `…_DanHengPT`；`Skill02_Phase02` 与 `PassiveSkill01` 里同样是"`Predicate` 比较 cid → `TaskList` 加变体"的结构。⇒ **n = 队伍里"有对应变体"的角色数**，这正好解释了捕获里的 **3/4**（1512 那一档没有变体）。我们内容目前只建模了一档（`ally_cid:1409` 的「献予「天空」之诗」）。⚠ 试过把标记规则挂 `from_skill_id: 19` 和 `1`，重放里都**没触发**（同一个字段在两条既有规则里分别是"能力 id"与"数据槽"，语义已分叉）⇒ 这一层要按 cid 变体逐条建模，不能靠一个"全体标记"糊过去（那样任何召唤物的 1 号技能都会误触发）。

**变体清单（tbgd，`Servant_CyreneServant_00_Skill02_Phase02` 的 OnStart[9..18]）**：`_Hyacine` / `_Harscyline(+_Sub)` / `_Evernight` / `_DanHengPT` / `_Castorice` / `_Phainon` / `_Player` / `_Tribbie` / `_Mydeimos` / `_Aglaea(+_Sub)`，各自由 `ByCompareCharacterID` 选中对应 cid 的角色后 `AddModifier`。⇒ 「获得增益效果」是**每人一个专属变体**，4 人队里出现 3 个，正是因为队里只有 3 个 cid 有对应变体。

**2026-10-30 已落地一半（可数标记）**：`memosprite_ode_marks_the_allies_it_buffs` —— 德谬歌忆灵技（数据槽 1）给 **1409 / 1413 / 1415（self）** 施加可数标记 `德谬歌忆灵技增益`，**不给 1512**（他没有变体，这正是捕获里 3/4 的原因）。判据 `CyreneOdeMarksTheAlliesTest`：直接放德谬歌的忆灵技后，三人有标记、第四人没有 ⇒ 星魂 2 数到的 n = 3 ✓。⚠ 标记是"谁拿到过"，**不是**那三个变体各自的效果（各自效果仍缺）。

**变体各自的真实效果（tbgd 读到的，尚未建模）**：
- `_Hyacine`（1409）：`OnCreate` 给持有者加 `ExtraHealRatio`（动态 hash -1945637710），`OnAfterSkillUse` 只在 `Skill`/`Ultra` 后触发进一步结算 ⇒ 是"治疗加成 + 技能后追加"的一整套。
- `_Evernight`（1413）：`OnListenBeforeHitAll` 里用 `ByCurrentSkillName == Skill11` 判断，配合 `S02_ExtraRatio` 动态量 ⇒ 改的是**长夜月 Skill11 的份额**，不是简单属性。
- 通用 `AmazingBuff`：`OnStack` 时若昔涟**点开星魂 2**（`ByRankActivated`，hash 523552506），再给持有者加 `MServant_Cyrene_00_Rank02_ThanksGift` ⇒ 这才是"增益效果"链条的第二段。

⇒ 三档各自是一段独立机制（涉及动态 hash 与技能钩子），不是"加一条 MODIFY_ATTR"能覆盖的；要建模必须先有 1409/1413 各自的文档句子（现有文档只讲了星魂 2 的计数，没讲各变体的效果）。

**收口条件（两步）**：① 引擎要能"数持有某 buff 的我方单位"（现有谓词只有 `debuff_count`，没有队伍侧的 `ally_buff_count`）；② `DAMAGE` 的 `percent` 要能加一项 `per_count × n`（上限 0.24）。有了这两样，`zone_true_damage_rider` 就写成 `0.24 + 0.06 × n`，19~21 条 0.42 rider 一次性进对账。补法：按"持有德谬歌增益的我方角色数"上调该规则的 `percent`，上限 +0.24；1415 在本账号是 **E2** ✓，所以这条在实战里一直生效。

#### ⛔ 23047 的【魂迷】触发会自我递归（2026-10-31 实测，已回退）

写法本身合规（`DEBUFF_APPLIED` + `actor == self` + `!target has_state 魂迷` + `APPLY_BUFF{base_chance: 0.8}`），
但一跑就抛：

```
Trigger recursion exceeded 8 levels while firing DEBUFF_APPLIED; a trigger table is probably reacting to its own effect [rule: cone23047_ensnare]
```

⇒ 施加【魂迷】这一动作**本身**又发了一次 `DEBUFF_APPLIED`，而门 `!target has_state 魂迷` 在嵌套调用里没有变假
（要么状态还没落地就被再触发，要么嵌套上下文的 `target` 不是那一位）。**要收这条必须先弄清**：
`APPLY_BUFF` 在什么条件下会发 `DEBUFF_APPLIED`，以及嵌套时 `ctx.target()` 是谁。

本次改动**已整体回退**（规则与判据都不留），23047 保持"三条已落地、触发与清理仍开"。

#### ⛔ 129 的笑点阶梯：计数器是队级的，但**没有"全局声明"的地方**（2026-10-31 实测）

录制者确认：**笑点是全局的、无上限**。数据侧已经有一半：`1502.json` 里 `{"id": "笑点", "max": 2147483647, "scope": "PARTY"}` ✓
（`max` 取 `Integer.MAX_VALUE` 就是"无上限"的写法）。

但要把 129 的「每累计 5 点笑点 ⇒ 欢愉伤害额外无视 1% 防御，最多 10 层」写成规则，会撞上**装载/运行时的顺序问题**：

- `per_steps: "笑点:5"` 需要一个能读**队伍**计数器的口径（引擎今天只读"属性 / 自己身上的计数器 / 战技点"）——
  已试加 `ctx.battle().partyResource(name) != null` 的回退 ✓，`per_steps_cap: 10` 也已加 ✓，两者都编译通过；
- 但**只要队伍里没有声明笑点的角色**（例如 1002 + 1003 的队伍），规则一开火就会抛
  `a weight step reads "笑点", which is neither an attribute … nor this party's counters` ✗ —— 实测 2 例红。
- 想在 `when` 里加门也做不到：条件语言**没有** `party_resource:<NAME>` 这种拼法（有 `self_resource:`）✗。

⇒ **结论：缺的不是数字，而是"全局计数器在哪里声明"**。三个可选方案：
1. 引擎新增 `game/data/battle_resources.json`（全局计数器一次声明，所有队伍都有，值为 0）——**最干净**，还能顺带解开 20024 / 1501；
2. 条件语言加 `party_resource:<NAME>`（"本队有这个计数器"），规则用它做门；
3. 把 `per_steps` 读不到的计数器当作 **0 层**（不抛），但那就把"拼错名字读 0"的静默错放回来了 ✗ —— 不做。

本次尝试的引擎改动（`per_steps_cap`、party 计数回退）**已撤回**：没有消费者之前不留。

#### ✅ 遗器三件"未完成"里的一件其实是过时记录（2026-10-31）

`CONTENT-STATUS` 把 **312 梦想之地匹诺康尼** 的四件套记为"没有按属性比较两个单位的拼法"，但**条件语言早就有**：

```
target: all_allies  +  target_when: ["is_other_same_element_as_self"]
```

`TriggerConditionParser` 里 `IS_OTHER_SAME_ELEMENT` / `IsSameElementAsSelf` 已在位 ⇒ 只是状态行没更新。补判据
`RelicOpenClausesTest`（姬子 Fire 的同属性队友吃 +10%、三月七 Ice 不吃）后，312 记为**完全实现**。

另外两件仍然**真的**缺内容，不是缺引擎：
- **129** 四件套第二句「每累计 5 点笑点 ⇒ 欢愉伤害额外无视 1% 防御，最多 10 层」：需要一个**累计的笑点计数器**，而文档从未写它的上限 ⇒ 按本项目的规矩**不编数字**（与 1501 火花的「笑点/好活当赏」同一判断）。机制本身可写（`per_steps: "笑点:5"` + 上限），等计数器落地即可；
- **132** 叩问天工的名冶：`_unmodelled.json` 已登记，真正的阻塞是「防御力降低」在游戏数据里**不是具名状态**（只是属性变化），既没有选择器也没有"We inflicted DEF reduction"事件 ⇒ 维持登记。

#### ⏳ 最后 3 行 differ 是「这一发能选中谁」，而 payload 的 `field.targetable` 还不能直接用（2026-10-30 实测）

两份录像（e / f）各剩 1–2 行，现象同一类：**引擎打的敌人比捕获多**。

| 现场 | 捕获 | 引擎 |
|---|---|---|
| e cast 65（长夜槽 7）| `2h/1t`、`14222.6013` × 2 | 再加一发 `28445.2027`（主目标价）|
| e cast 66（1415 强化普攻）| `2h/2t`、`3280.55` + `984.16` | 三个目标 × 两个比例 = 14 段 |
| f cast 84（长夜槽 7）| `23306.09` × 2（两发都是"其他目标"价）| `46612.19` + `23306.09`（一发主目标价）|

捕获给的快照（cast 65）：`alive=[100012,100029,100031,100032,100034,100035]`、**`targetable=[100029]`** ⇒ 只有 1 个敌人**可被选中**，与 `2h/1t` 一致。

⚠ 试过直接用 `field.targetable` 驱动（引擎加"不可选中"标记 + `Battlefield.targetableEnemies` 过滤 + 工具按发设置）：**e 不变、f 反而从 11/1 退到 10/2** ⇒ 说明 `field.targetable` 的语义不是"这一发所有可选中单位"，可能只反映**首条事件那一刻**、或只列"本发参与结算的单位"。该实验**已整体回滚**（引擎与工具都未保留）。

**2026-10-30 定论（实测，不再是猜测）**：逐发比对 `field.targetable` 与"这一发实际打到的 uid 集合"：

| 录像 | 有 field 的发数 | targetable == 打到的集合 | 不等 |
|---|---|---|---|
| e | 17 | 12 | 5 |
| f | 10 | 6 | 4 |
| d | 12 | 10 | 2 |

不等的那些全是 **AoE 发**，例如 cast 16：`targetable=[100015]` 而实际打到 `[100013, 100015]` ⇒ **`field.targetable` 是"这一发瞄准的目标"（AoE 时只有 1 个），不是"能被打到的全部敌人"**。

⚠ 所以它不能用来收窄 AoE 的目标集合——这也解释了上次直接用它时 f 反而变差（把"瞄准"当成了"可达"）。
**真正缺的字段是 `field.on_stage`（或 `reachable`）**：那一刻仍在场、可被 AoE 波及的 uid 列表。`field.alive` 也不行——cast 65 的 `alive` 有 6 个（含已撤离、韧性 0 的），而实打只有 1 个。

#### 🚧 1413 的「忆灵离场后再召唤」路径未走通（2026-10-30 实测，**内容缺口**）

在 2026-10-30 那份捕获里，「长夜」在战斗中**离场后又被重新召唤**（cast 69 之前它不在场，而捕获显示它又开始行动）。工具按捕获把它重新召唤时，引擎报错：

```
Effect targets "summon" but Evernight has no summon on the field;
gate the rule with `self_summon_count >= 1` so it only fires when there is one
```

⇒ **不是驱动器的 bug**：她有一条规则的 `target` 指向 `summon`，而那一刻 `summonOf(1413)` 还是空。**报错现在会点名规则**（`TriggerInterpreter.apply` 包了一层），实测点出来是 **`memosprite_taunt_aggro`**（`on: SUMMONED`、`actor == summon`、`MODIFY_ATTR target: summon`）。⚠ 试过两种修法都被否掉：① 给该规则加 `self_summon_count >= 1` 门 —— 会让「登场的瞬间就把行动提前」**失效**（门读的正是那个为空的查找）✗，而且顶红了 `SummonEntryTest`（它钉着该规则的形状）；② 让「召唤物自己的规则」把 `summon` 解析成自己 —— 无效，因为这条规则的 owner 是 1413 而不是召唤物（改动保留：本身语义正确 ✓）。⇒ 真正待查：**`addCombatant` 之后、`onSummoned` 触发之前，`summonOf(1413)` 为什么仍为空**。该 cast 记 **not drivable**（不猜）。
#### B 层：敌人（只有少数怪有技能，其余全是平 A）

| 任务 | 现状 | 验收 | 依赖 |
|---|---|---|---|
| **P9-1** `enemy_skills.json` 扩充 | 自建表只覆盖少数演示怪，**倍率是猜的**（每条 `guessed=true`） | ★`EnemySkillTest`：条目数 / 倍率 / 削韧值与表一致 ✓；无条目的怪走兜底普攻 | — |
| **P9-2** `EnemySkill` 全效果 | 🚧 形状分派已落地 | ★`EnemySkillFullTest`：冰锋战技打 3 人 → 每敌 `attack × 倍率 × 防区` ✓；按 bean 削韧 | P9-1、P5-3、P1-8 |
| **P9-3** 敌方 AI 技能选择器 | 未开始 | `SkillSelectorTest`（**待写**）：权重 1.0/1.2 两技能跑 1000 次 → 次数比 ≈ 1:1.2（±3%） | P9-2、P5-5 |
| **P9-6** `multi_target_rule`（每目标每段倍率 = 行倍率 ÷ N）| **未实现，且暂时没有消费者**：`skill_segments.json` 全表只有 **8 条**段实例用到它（`1/命中目标数` ×5、`1/敌方存活数` ×3），分布在 **1321 大丽花**（终结技）、**1408 白厄**（强化战技·追加 / 强化终结技）、**1506 银狼LV.999**（强化普攻四段）；**复刻的四人（1409/1413/1415/1512 及四只忆灵）一条都没有** | `MultiTargetRuleTest`（**待写**）：3 敌时每敌 = 行倍率 ÷ 3；单体场景 ≡ ÷ 1 | P5-3、P1-8 |
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
| **P10-4** 速度与行动条操纵 | ✅ **已完成**（2026-10-29 用判据核过）：减速走 `SpeedBoostBuff` → `Queue.refreshSpeed`（按**已走进度**换算 ✓）、拉条走 `ADVANCE` → `Queue.advanceActionByPercent` ✓、延迟走 `Battle.delayMovePercent`（public ✓） | ★`SpeedBuffTest`（3 例 ✓）：减速 30% ⇒ 下一圈 `10000/70 = 142.857` ✓；拉条 50% ⇒ 提前**半圈** ✓；拉条按**剩余**时间、不是整圈 ✓（两次 50% ⇒ 剩 1/4 ✓） | P10-3 ✅、P7-1 |
| **P10-5** 终结技插入 | ✅ **已完成**（2026-10-29 用判据核过；`Battle.castUltra` 早已 public，`../tui` 的前端也一直在用，缺的只是这条验收） | ★`UltraInsertTest`（5 例 ✓）：**没人行动时**也能插入 ✓（"空档"那一刻 —— 上一版只允许我方回合会把它关掉 ✗）、插在别人的行动中间**不夺走**那人的行动 ✓、**行动条前后逐条一致** ✓、放完能量 = **5 × (1+回能率)**（0.4 ⇒ 7 ✓）、空能量放不出 ✓ | P3-2、P7-2 |
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
| **7.1** 战斗拆分（组装 / 主战斗 / 引擎原语） | 第一小刀已落地 ✓（`Battle` 拆出 `BattleDamage` ✓、`TriggerTable` 拆出 `TriggerConditionParser` ✓）；第二小刀已落地 ✓：回合生命周期收进 `TurnLoop`（`stepForward → beforeMove → action → afterMove`），`BattleDriver.step` 与 demo 的 `step` 都走它；第三小刀已落地 ✓：构造器复制 `characters`，`addRequestItems` 私有（入口 `addCombatant` / `pendingCombatants`），`startBattle` 二次调用报错，`skillPointPolicy` 私有 + setter 重接线，各类 observer 可退订，`BattleDriver.run` 只在未开战时启动；类型化载荷入口已收口 ✓：`TriggerPayload` + `Battle.fire(...)`，生产代码里的 `fireTriggers` 调用点已全部改为类型化入口——cast 五事件、damage family（`DEALING_DAMAGE` / `DAMAGE_SETTLED` / `DEATH`）、subject family（`KILL` / `BREAK` / `CRIT_DEALT`）、ally-only subject（`TAKING_HIT` / `HP_LOST` / `LETHAL_DAMAGE` / `HEALED` / `SHIELD_GRANTED` / `ENERGY_GAINED` / `COUNTDOWN_TURN`）、`DEBUFF_APPLIED`、`STATE_ENDED`、`RESOURCE_CHANGED`、`ATTACK_FINISHED` 与 simple family（战斗 / 回合 / 波次 / 召唤 / 技能点）；引擎已不再打印（`print*` 从 `Battle` / `Queue` 删除），`TestBuff*` / `TestSkillGroup1` 已移入 test source，`Main` / `Benchmark` / `compare/*` 已拆到 `src/demo` / `src/tools`（`run` 与 `test` 的 classpath 已接，引擎 jar 不含它们）；`GameData` 已抽出（表格加载/冻结在对象里，`Constant` 只是 `DEFAULT` 的门面，`GameData.load()` 可独立加载），`EnemyFactory` 已有 `create/resolve(GameData, ...)` 重载，`SummonFactory` 也有 `create(GameData, ...)`，`StageFactory` / `WaveManager` 也接受 `GameData`（队伍与波次都读它），`CharacterFactory` 的完整入口也接受 `GameData`（角色基础行、行迹表、技能表、触发表都读它），`Battle` 也接受 `GameData`（技能/召唤读它，默认仍走 `DEFAULT`），`SkillData` / `SkillTrace` 的默认读取也走 `GameData.DEFAULT`，遗物/光锥触发表由 `RelicTriggerTableProvider` / `WeaponTriggerTableProvider` 提供，队伍资源与【好活当赏】快照已拆出 `PartyResources`，召唤创建/查询已拆出 `SummonService`，战前声明已拆出 `PreBattle`，施法结果已拆出 `CastOutcome`，触发侧通道已拆出 `WarehouseListeners` / `DeferredTriggers`，仇恨权重/抽表已拆出 `AggroTable`，观察者注册/发布已拆出 `BattleObservers`，终结技门槛/消耗/结算已拆出 `UltimateService`，待结算技能队列已拆出 `SkillRequests`，待发 `SUMMONED` 的召唤缓冲已拆出 `SummonedBuffer`，能量入账已拆出 `EnergyService`，倒计时单位已拆出 `Countdowns`，触发嵌套深度/本次结算总量已拆出 `TriggerFiring`，技能点池/事件接线已拆出 `SkillPoints`，治疗/护盾落点已拆出 `SustainService`，`useSkill` 门已拆出 `CastService`，触发总线与 payload 入口已拆出 `TriggerBus`，行动内伤害标记已拆出 `DamageMarks`，波次状态已拆出 `WaveState`，伤害装配与结算编排已拆出 `DamageService`，死亡清扫已拆出 `DeathService`，待上行动条缓冲已拆出 `PendingCombatants`，攻击序号/反击嵌套守卫已拆出 `AttackSequence` / `CounterNest`，DOT/HOT 结算已拆出 `TickService`，阵营/目标查询已拆出 `Battlefield`，战斗状态/胜负判定已拆出 `BattleResult`，额外回合状态已拆出 `ExtraTurnState`，韧性/击破链/击破回合已拆出 `BreakService`，命中率/掷骰已拆出 `ChanceService`，debuff 落点已拆出 `DebuffService`，三个名单与 `queue` 已私有化并全量迁移到访问器，`CanHit` 的规则簿记已拆出 `TriggerCooldowns` / `RuleLimits` / `RuleAmendments` / `RuleChanceState` / `SkillBonuses` / `ConditionalHealBonuses`，护盾已拆出 `ShieldState`，生命/死亡/无敌已拆出 `HealthState`，能量条已拆出 `EnergyState`，技能槽位已拆出 `SkillSet`，属性表已拆出 `AttributeSheet`；`TriggerContext` 里无人读取的 `stateName` 字段与 `withStateName` 已删，cast 三项已收进 `CastFacts`，事件侧四项已收进 `EventFacts`，主体三项已收进 `TriggerSubject`，规则作用域两项已收进 `RuleScope`，最后一次触发事实已收进 `TriggerFacts`。⚠ 仍开：`characters` / `allies` / `enemies` 仍是 public 只读视图（写入口只剩 `addCombatant`）；`Battle` 的四个原语仍是 public（TUI 还在直接调，未迁）；旧 `fireTriggers` 重载只剩适配器与测试，`TriggerContext` 已收成 `subject` / `eventFacts` / `battle` / `ruleScope` / `castFacts` 五个组件 | ★`BattleEventRoutingTest` 三条 ✓ / ★`TurnLoopTest`（边界顺序、DOT 击杀后仍收尾、policy 驱动路径）/ ★`BattleLifecycleTest`（二次开场被拒、名单复制、`addCombatant` 入场、policy 重接线、observer 退订）/ ★`TriggerPayloadTest`（cast 的 category / hit count / slot；damage / break / kill 的 cast category；ally 阵营过滤与 debuff 主语；治疗 / 护盾 / 回能；state / resource 名字；simple 事件） |

#### 引擎结构任务（7.1 的后续，按顺序）

1. **`TurnLoop`** ✓ 已落地：四个边界与 broken/control 门只写一次；`BattleDriver` 只留 `run`/终止/监听接线，`Main` 的 demo 只提供 action。
2. **名单/生成/生命周期收口** 🚧 部分：`characters` 构造时复制；`characters`/`allies`/`enemies` 是 public **只读视图**，背后是私有可写名单，唯一写入口是 `addCombatant`；`addRequestItems` 私有，入口 `addCombatant` / 只读 `pendingCombatants`；`startBattle` 二次调用报错；`skillPointPolicy` 私有 + `setSkillPointPolicy` 重接线；各类 observer 都有 `remove*`；`BattleDriver.run` 只在 `NOT_STARTED` 时启动。⚠ 仍开：三个名字仍是 public 字段（读取面太大，尚未收成 getter）。
3. **类型化事件载荷** ✅ 入口：`TriggerPayload` + `Battle.fire(...)` 已覆盖全部生产调用点（cast / damage / subject / ally-only / state / resource / simple；`ATTACK_FINISHED` 用 cast 形状），旧 `fireTriggers` 的 1 / 5 / 6 / 7 参公开重载已全部迁到 `Battle.fire(TriggerPayload...)` 并删除，只剩私有核心与 subject 辅助；`TriggerContext` 的 `stateName` 死字段与 `withStateName` 已删，cast 三项（`skillId` / `weakHitCount` / `attackHitTargets`）已收进 `CastFacts`，事件侧四项（`hitCount` / `amount` / `damage` / `fromCast`）已收进 `EventFacts`，主体三项（`owner` / `actor` / `target`）已收进 `TriggerSubject`，规则作用域两项（`ruleId` / `targetFilter`）已收进 `RuleScope`，触发核心用一次 `withCastFacts` 构造而不是三次复制，最后一次触发的事实（state / resource / debuff）已收进 `TriggerFacts`。`TriggerContext` 已收成 `subject` / `eventFacts` / `battle` / `ruleScope` / `castFacts` 五个组件并由 `TriggerContextShapeTest` 钉住；公开的旧 `fireTriggers` 重载已收口，只剩私有核心。
4. **拆 `Battle`** 🚧 部分：Turn / 回合边界已收进 `TurnLoop`，伤害结算已拆出 `BattleDamage`，队伍资源与【好活当赏】快照已拆出 `PartyResources`，召唤的创建/查询已拆出 `SummonService`，战前声明（秘技 / 地图攻击 / 美学底本）已拆出 `PreBattle`，施法结果（已施加状态 / 本轮笑点计数）已拆出 `CastOutcome`，触发侧通道（仓库监听 / 等波次释放）已拆出 `WarehouseListeners` / `DeferredTriggers`，仇恨权重与抽表已拆出 `AggroTable`，观察者注册与发布已拆出 `BattleObservers`，终结技门槛/消耗/结算已拆出 `UltimateService`，待结算技能队列已拆出 `SkillRequests`，待发 `SUMMONED` 的召唤缓冲已拆出 `SummonedBuffer`，能量入账已拆出 `EnergyService`，倒计时单位的登记与归属查询已拆出 `Countdowns`，触发嵌套深度与本次结算总量已拆出 `TriggerFiring`，技能点池与事件接线已拆出 `SkillPoints`，治疗/护盾的落点与通知已拆出 `SustainService`，`useSkill` 的施法门与技能点/终结技接线、`performAction` 已拆出 `CastService`，`currentMove` 已私有化并全量改走 `currentMove()` / `setCurrentMove(...)`，触发总线（本侧角色 → 仓库监听 → 延迟规则，一次 firing；类型化 payload 入口也在这里）已拆出 `TriggerBus`，每次行动内的“已致命/已掉血”标记已拆出 `DamageMarks`，波次序号与本波敌人名单已拆出 `WaveState`，伤害装配与结算编排（`DEALING_DAMAGE` → 暴击 → 防御 → 韧性减伤 → 抗性 → 落血/致命/死亡 → 能量 → observer）已拆出 `DamageService`，死亡清扫（孤儿召唤 / `SUMMON_LEFT` / 清行动条 / 解锚 buff / 调用结果检查）已拆出 `DeathService`，已入场未上行动条的缓冲已拆出 `PendingCombatants`，攻击序号与 buff 反击嵌套守卫已拆出 `AttackSequence` / `CounterNest`，回合开始的 DOT/HOT 结算已拆出 `TickService`，阵营/可选目标/随机对手查询已拆出 `Battlefield`，战斗状态与胜负判定已拆出 `BattleResult`，额外回合的当前行动者已拆出 `ExtraTurnState`，韧性削减/弱点击破链（含行动延迟与击破回合计数）已拆出 `BreakService`，效果命中率与 seeded 掷骰已拆出 `ChanceService`，debuff 落点（命中/家族抗性/安装/事件）已拆出 `DebuffService`，三个名单（`characters` / `allies` / `enemies`）已私有化，生产 / 测试 / demo 全部改走 `characters()` / `allies()` / `enemies()` 访问器；`queue` 也已私有化并全量改走 `queue()`，`CanHit` 的冷却/每回合次数已拆出 `TriggerCooldowns`、每攻击/每波/每场次数已拆出 `RuleLimits`、规则修正量已拆出 `RuleAmendments`、衰减概率与抽取记忆已拆出 `RuleChanceState`、技能等级/段数加成已拆出 `SkillBonuses`、条件治疗加成已拆出 `ConditionalHealBonuses`（规则簿记组合化完成），护盾值/来源/吸收已拆出 `ShieldState`，生命值/死亡/无敌已拆出 `HealthState`，能量条/上限/provider 已拆出 `EnergyState`，技能槽位已拆出 `SkillSet`，属性表已拆出 `AttributeSheet`；⚠ 仍开：`CanHit` 的资源状态仍在其自身（`ResourceManager` 已是独立对象）。✅ **TUI 的直接访问已改掉**（2026-10-29，`../tui` 的 `BattleSession` 改用 `queue()` / `characters()` ✓）—— 此前那一版在 `queue` / `characters` 私有化之后**编译不过** ✗，是跑到上一层才发现 ✓。
5. **demo/tools 移出 `src/main/java`、`GameData` 注入** 🚧 部分：`Main` / `Benchmark` / `compare/*` 已拆到 `src/demo` / `src/tools`，`run` 与 `test` 的 classpath 已接；`Battle.printHp` / `Battle.printBattle` / `Queue.printActionQueue` 已删（demo 自己打印），`TestBuff*` / `TestSkillGroup1` 已移入 test source；引擎 jar 不再含这些类；`GameData` 已抽出（加载/冻结在对象里，`Constant` 只是 `DEFAULT` 门面，`GameData.load()` 可独立加载）。`EnemyFactory` / `SummonFactory` / `StageFactory` / `WaveManager` / `CharacterFactory` / `Battle` 已接受 `GameData`，行迹表、技能表、触发表可分别由 `GameData.withSkillTraces(...)` / `GameData.withSkills(...)` / `GameData.withTriggerTables(...)` 提供，遗物/光锥触发表也可由 `GameData.withRelicTriggerTables(...)` / `GameData.withWeaponTriggerTables(...)` 提供；`SkillData` / `SkillTrace` 的默认读取也走 `GameData.DEFAULT`；⚠ 仍开：`Battle` 的数值常量仍读 `Constant`。

### 4.3 登记（**缺能力时来这里，不要近似**）

- ⚠ **缺引擎能力 ⇒ 先补能力，再数据化角色** ✓（§3.2 的逃生舱三选一 ✓）。
- **登记写在哪里** ✓：**规则自带的 `note`** ✓（最贴近那条内容 ✓）或**本节** ✓；**已落**的东西**不留在本节** ✗（它们进 §3.7b–3.7zd 的索引 ✓）。
- ⚠ **每条登记必须能回答三件事** ✓：**读者是谁**（哪条内容要它 ✓）、**缺的到底是什么词/事件**（不是"写不了"这个印象 ✓）、**缺口见证**（一条钉住"今天读数"的判据 ✓，能力落地时它红 ✓）。没有见证就把今天的样子钉住 —— 否则下一个人只能重新推导 ✓。

#### 仍开的缺口

- **光锥审计（2026-10-19，168 份逐条核完）新增的缺口族** ✓ —— 每族都写明**读者**（哪条光锥）、**缺的词**、**缺口见证** ✓：
  - **「没有暴击」没有事件** ✗：`CRIT_DEALT` 只报**暴击成功** ✓。读者：**23012**（「普攻或战技伤害**未造成暴击**时暴击率 +36%」）。见证：`ConeAuditBatchNineteenTest` 的「规则数 0」✓。
  - **「记录一个数、以后再读」没有写入端** ✗：读者：**23013**（「记录治疗量 → 按该记录的 36% 造成附加伤害」）。见证：`ConeAuditBatchNineteenTest` ✓。
  - **「上一次施放的是哪一招」记不住** ✗：`damage_skill_key` 只描述当前这一发 ✓。读者：**23064**（「欢愉技与上一次不同 ⇒ 额外获得【潮流】」）。见证：`ConeAuditBatchTwentyEightTest` ✓。
  - **「场上每有 1 名持盾角色」数不出来** ✗：只有 `enemy_state_count` 那一类 ✓。读者：**21043**。见证：`ConeAuditBatchTwelveTest` ✓。
  - **「两名及以上相同命途」配不起来** ✗：`has_path` 只能问**某一个单位** ✓。读者：**21046**。见证：同前 ✓。
  - **「击杀时检查这层 DoT 是不是她上的」没有归属** ✗：`target_dot_count` 数得到 ✓、问不了来源 ✗。读者：**24003**。见证：`ConeAuditBatchTwentyNineTest` ✓。
  - **「受到的**暴击伤害**」不是一个可改的层** ✗：`MODIFY_DAMAGE_TAKEN` 改的是总区 ✓。读者：**23059**（【炼狱】）。见证：同批 ✓。
  - **「助战技」不是技能类** ✗：`from_skill` 闭集只有 COMMON/SKILL/ULTRA/TALENT ✓。读者：**23060**。见证：`ConeAuditBatchTwentySevenTest` ✓。
  - **「就地升级一个状态」没有拼法** ✗：`APPLY_BUFF` 只能新增 ✓。读者：**23029**（【卸甲】→【穷寇】）、**23047**（【魂迷】三条内部效果）。见证：各批 ✓。
  - **「每场 1 次 + 终结技重置」的旗标不可复位** ✗：`once_per_battle` 不能复位 ✓（`RESET_TRIGGER_LIMIT` 只对**规则级**限流有效 ✓）。读者：**23040**（忆灵消失）。见证：`ConeAuditBatchTwentyFourTest` ✓。
  - **「击破伤害」这一层只有在 `damage_type` 上才认** ✓（`BREAK` ✓，23025/23035 用它 ✓）；但**「受到的击破伤害」若要与「来自某人」叠加**仍不行 ✗（读者 **23025** ✓）。
  - **⭐ 已解决的旧登记** ✓：**「这次施放花了多少能量」** —— 变量 **`cast_energy_spent`** 已存在 ✓（读者 **23062** ✓），所以 **23037** 的同类句子可以**下次直接写** ✓（不必再登记 ✓）。

- **`DamageType` 给不出"规则身份"** ✗：`Damage` 上没有规则 id、也没有目标选择器 ⇒ "这条天赋伤害是哪个规则发的"在**类型这一层**区分不了 ✓ ⇒ 不猜 ✓（§6.8(d) 的 TALENT 讨论 ✓）。
- **Impair 的 debuff 派发缺的是一列"哪个 debuff"** ✗：`skill_effects.json` 里一条 `Impair` 行都没有 ✓，而正文的 `陷入【吞火】状态` / `风化/裂伤/灼烧/触电其中1种` 只在散文里 ✓（`SkillData.debuffChance` 已有等级感知，**没有调用者** ✓）【缺口见证（§7.4）：`ImpairDebuffGapTest` —— ① 没有一条 `Impair` 带 effect 行 ✓（有一条就红，说明派发有了输入 ✓）；② 正文点名**战斗内**状态的只有极少数几处，其余是**地图层** ✓（本引擎没有那一层 ✓）】。
- **`ENERGY_GAINED` 只说「谁收到了」，不说「谁给的」** ✗（2026-10-19 核对 ✓）：`fireTriggersForAlly(ENERGY_GAINED, target, target, added)` ⇒ `actor` **就是收能量的那位** ✓，`EnergyGain` 只有 `amount` ＋ `affectedByEfficiency` ✓ ⇒「受到**队友提供的**能量恢复…」判不了 ✗（读者：1408 行迹 1408102 的后半 ✓）。⚠ 不许拿「任何人给我回能」冒充 ✗；也不许把 `actor` 改成授予者 ✗ —— **4 条出货规则押在当前语义上** ✓（1207 星魂 2、1310 天赋、1505 两条）。要开这个口子：给事件加「授予者」事实，并复核那 4 条 ✓。【缺口见证：`PhainonTraceBideInFlamesTest.theEnergyHalfIsStillANoOp`】
- **一个回合上限无法被两条规则共享，条件之间也没有「或」** ✗（2026-10-19 实测 ✓）：光锥 20012 的句子是「施放攻击**或**受到攻击后，
  额外回 4 点，**单个回合内不可重复触发**」✓。两半要的条件不同（`actor == self` vs `target == self` ✓），而条件是 **AND** ✗、
  `per_turn` 是**每条规则各有** ✗ ⇒ 一条规则只能表达一半 ✓；拆成两条则一回合能触发两次 ✗。⇒ 出货保留能精确表达的那一半，
  另一半登记 ✓（缺口见证：`ConeAuditBatchThreeTest` 末尾对 `TAKING_HIT` 的读数 ✓）。要开这个口子：给条件一个「或」的拼法 ✓，
  或者让 `per_turn` 能被几条规则**共享一个计数器** ✓。
- **连续条件（「当生命值百分比小于 X% 时」）只在事件边界采样** ✗（2026-10-19 实测 ✓）：光锥 20016 用 `TURN_START` 每回合重算 ✓，
  ⇒ **掉到 80% 以下的当回合读不到** ✗。⚠ **试过**给同一条规则加 `on_any: [HP_LOST]` ✓：**无效** ✗ —— 真实伤害路径与手动
  `fireTriggers(HP_LOST, …)` 都试过，暴击率一直停在 0.05 ✓（`HP_LOST` 的 `actor` 是**攻击者**、目标才是掉血者 ✓，而 `hp_percent`
  读的是**表的主人** ✓ —— 这条组合为什么不触发，**留给下一位查** ✓，别以为它已经能用了 ✗）。
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
| **A 层** | P8-6/7/8 + P8-3：角色机制 = 数据的总开关 | ✅ **已完成** | `trigger/*` ＋ `characters/*.json` |
| **B 层** | P9：敌人技能与 AI | 🚧 进行中 | §4.2 |
| **C 层** | P10：机制补完 | ✅ **已完成**（P10-4 / P10-5 收口；P10-6 的"**哪个** debuff"仍缺数据 ⇒ 按 §4.3 登记，属数据侧 ✗） | §4.2 |
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

#### (h) 私有化一个字段时，**另一棵树不会跟着红**

⚠ 2026-10-29 实测：`Battle.queue` / `Battle.characters` 收成私有之后，本仓库的 demo、测试、四道闸门**全绿** ✓ ——
而上一层 `../tui`（唯一的第二个消费者，`includeBuild('../aluminium')` 复合构建 ✓）**编译不过** ✗（直接访问了：
`battle.queue.getTimeRemaining` / `battle.characters` / `battle.queue.getCurrentActor` / `battle.queue.peekNext`）。⇒ **动公共面之后，跑一次上一层**
（`cd ../tui && gradlew test` ✓）—— 它不是可选的礼貌，是"公共面到底改了谁"唯一能回答的方式 ✓。
⚠ 这次修复也划出了边界：只把访问方式换成访问器就够 ✓（行为没变、用例全绿 ✓）—— 顺手把上一层"重构一遍"是多余的 ✗。

#### (i) 把一个类挪到**新包**之后，Gradle 可能既不报错也不编译它

⚠ 2026-10-29 实测（`models/` 拆包那一轮）：测试类从 `com.laosun.aluminium.models.trigger` 挪到
`com.laosun.aluminium.trigger` 之后，`compileTestJava` **报 BUILD SUCCESSFUL** ✓，而 §3.3 的闸门**照样打印 `GATE: PASS`** ✓ ——
可 `build/classes/java/test/.../trigger/` 里**一个 .class 都没有** ✗，`:test` 于是跑了一个**少了四个套件**的集合
（`908` 套件 vs 真正的 `912` ✓），而它**仍然是绿的** ✗。
判据：**套件数/用例数与上一批对不上**就是它（不是"测试变少了"）✓；`compileTestJava --rerun-tasks` 之后四个类才出现 ✓。
⇒ 两条结论：① `gate` 现在**强制四个 `compile*` 任务**真跑 ✓（`build.gradle` 的 `outputs.upToDateWhen`，与 `:test` 同一个口子）；
② **动包之后，第一次闸门要读套件数**，别只看 `PASS` ✓ —— 这正是 §1.6「一处通过 ≠ 处处通过」的又一面：
**编译通过 ≠ 该类被编译过** ✓。

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

9. ⚠⚠ **读技能等级要用 `skillLevelBonus(slot)` / `skillLevel(skill)`，不能读 `skill.getLevel()`** ✓（2026-10-19 实证 ✓）：
   基础等级**永不变化** ✓ —— 升级记在 `CanHit.skillLevelBonus` 这个 Map 上 ✓，`skillLevel(skill)` 才是「基础 + 加成」的那个读法 ✓。
   我核 1003 的星魂 3/5 时读了 `skill.getLevel()`，两次都是基础等级 ✓ ⇒ 判据写了跟没写一样（**永远绿、什么都没验证** ✗）。
   改成 `skillLevelBonus(SKILL)` 之后立刻读出「战技 +2、普攻 +1、终结技 +2、天赋 +2」✓。
   ⚠ 引擎里**同形教训已经写过一次** ✓（`CanHit.skillLevel` 的注释：把 int 传进 `Map<SkillType, Integer>`，编译通过、永远取不到 ✓，
   而所有测试仍然绿 ✓）⇒ **凡是「引擎把某个量加在一个 Map 上」的字段，判据必须走那个 Map 的读法** ✓。
   ⚠ 同一趟的另一个同形：**实例修饰（`instance: true`）在面板上永远看不到** ✓ —— 我拿 `CritArea` 在**不暴击**的掷骰下读它，
   两次都是 1.0 ✓，什么也没证明 ✗；正确的读法是**让掷骰落在「只有加了那个暴击率才会暴击」的区间**里 ✓
   （1003 行迹道标：基础 5%、加成后 20%，掷 0.10 ⇒ 满血暴击 `true`、掉血 `false` ✓）。
   ⇒ **凡「实例级」的量，判据只能落在「这一发的后果」上** ✓（是否暴击、这一发的区值 ✓），不能落在面板或未触发的区值上 ✗。

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
| **文档与代码的闭集不许分叉**（`TRIGGER.md` §3.5 变量/前缀 ↔ 解析器；§4 事件 ↔ `TriggerEvent`，**双向 + 标题计数**） | `TriggerVocabularyDriftTest`、`TriggerEventTableDriftTest` |
| **速度变化按已走进度重排；拉条按剩余时间**（P10-4） | `SpeedBuffTest` |
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
| **回合生命周期 / 名单入口 / 生命周期守卫**（四边界顺序、DOT 击杀后仍收尾、二次开场被拒、名单复制、`addCombatant` 入场、policy 重接线、observer 退订） | `TurnLoopTest` / `BattleLifecycleTest` |
| **类型化载荷**（cast 的 category / hit count / slot 各由不同事件观测；damage / break / kill 的 cast category；ally 阵营过滤与 debuff 主语；治疗 / 护盾 / 回能；state / resource 名字；simple 事件） | `TriggerPayloadTest` |
| **`TriggerContext` 形状不回涨**（顶层只许五个分组；`subject` / `eventFacts` / `ruleScope` / `castFacts` 各自保留内部字段） | `TriggerContextShapeTest` |
| **`CanHit` 组合化不回涨**（规则/核心状态必须在组件里，`CanHit` 不许再长出裸 `Map` / `Set` / `List` 字段） | `CanHitCompositionTest` |
| **引擎不打印 / 不含 demo / 生产入口不走旧重载**（`Battle` / `Queue` 不得再暴露 `print*` 方法；生产代码不得调用 `battle.fireTriggers(...)`；验收命令另看 jar 条目） | `EngineApiSurfaceTest` |
| **数据表可由对象独立加载**（第二次 `GameData.load()` 与 `Constant` 同内容、不同容器、冻结） | `GameDataTest` |
| **工厂/战斗读传入的 `GameData`**（空怪物表必须报 Unknown monster，不许回落到 `Constant`；波次路径同；空角色表必须报 CharacterNotFound；空行迹表必须真的少掉行迹加成；空技能表必须留下未加载的占位；空触发表必须真的没有规则；战斗开场附带的技能槽也读传入表；遗物 provider 必须被问到） | `EnemyFactoryDataTest` / `SummonFactoryDataTest` / `StageFactoryDataTest` / `CharacterFactoryDataTest` / `SkillTraceDataTest` / `SkillDataTest` / `TriggerTableDataTest` / `BattleDataTest` / `RelicTriggerTableDataTest` |
| **名单是只读视图**（`characters` / `allies` / `enemies` 不能从外部 clear；写入口只剩 `addCombatant`） | `BattleRosterTest` |
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
| `SpeedBuffTest`（P10-4 的验收） | ✅ **2026-10-29 写了**（3 例，见 §4.2）—— 这一行原先记的是"不存在，将来再说" ✓ |
| `UltraInsertTest`（P10-5 的验收） | ✅ **2026-10-29 写了**（5 例，见 §4.2）—— 这一行原先记的是"不存在" ✓ |
| `SkillSelectorTest`（P9-3 的验收） | ✗ **不存在** ✓ —— 同上 |
| tui 一族用例（`anActionReportsTheDamageItDealt` / `extraHitsAreMarked` / `ChoiceFlow` / `scrollLog` / `PgUp`） | ✗ **本 checkout 里搜不到** ✓ —— `src/` 下**没有 tui/ui 包** ✓（**旧** `ROADMAP.md` 的 §3.11–§3.13 引用的是**另一棵树**的用例 ✓） |
| `TestCharacters.withoutTriggerFile()` 是"取最小 id" | ✗ **已过时** ✓ —— 现在**pin `1506`** ＋ `TriggerTables.exists(cid)` 时**大声抛异常** ✓（`TestCharacters` ✓）；⚠ 但它的 **javadoc 还写着"取最小 id"** ✗ ⇒ **注释与实现不符**，以**实现**为准 ✓（§1.3：错的注释让人照着做 ✗） |

> ⚠ **文档说"没做"、其实已经做了的两处**（2026-10-16 实测 ✓，**照抄会白干** ✗）：
> ① **P9-5 的"受击反击"** —— `buff/CounterMechanic.java` **已存在** ✓、
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
