# AGENT.md

## 零、五件事的动手顺序

### 0.1 写 trigger
1. 先抄数据文件里的原文整句，不凭记忆。
2. 把句子切成从句：触发事件、条件门、数值来源。
3. 数值能按等级读就不写死：`percent_from_skill_param`、`skill`、`damage_param`、`value` 等。
4. 先查引擎词表；没有就登记缺口，不近似、不发明词。
5. `properties` 那一半不要重写，避免读成两倍。
6. 同属性同身份的修饰会互相替换；“最多叠 N 层”用层缓冲。
7. 每条规则写 `source` 与 `_note`，说明每个从句对上哪个词。
8. 事件没有队友门：`KILL`、`ULT_CAST`、`SKILL_CAST` 等对每个队友各发一次；“使装备者/本人”要加 `actor == self` 或 `target == self`。

### 0.2 写 test
1. 一次只钉一个性质，断言落在后果上。
2. 先跑一次看读数，再把实测量写进断言；不要先编期望值。
3. 判据要有反例半边：“只在 X 时生效”要同时断言“不是 X 时不生效”。
4. 两条读数不一致时，先怀疑自己的读法：技能等级、实例修饰、分母、夹具前提。
5. 对照要在独立战场里跑；A 与 B 各建一份 setup，或用同一档位前后差分。
6. 判据变红先查三件事：目标还活着吗、目标有该属性/抗性吗、战斗启动了吗。
7. 改引擎先问“哪条用例会因此变红”；一条都不红说明没把守。
8. 登记会过时，引用前先量。
9. 测试要对齐实际游戏。

### 0.3 改引擎
1. 先判断是“通用能力”还是“某个角色的句子”；后者回内容侧。
2. 新增词要同时做四件事：解析、执行、校验、引擎侧判据。
3. 改完过四道闸门：全量测试、demo 与基线内容级对比、捕获 `compare`、`replay --strict`、`javadoc`。
4. 引擎与内容分开提交。
5. 报告写清“跑了什么 / 没跑什么”。
6. 引擎要以实际游戏为主。

### 0.4 写 commit
1. 一行英文，动词开头。
2. 引擎 / 内容 / 判据 / 文档各一个提交。
3. 提交前跑全量测试，红的绝不提交。
4. 不提交构建产物、一次性探针、分析脚本。
5. 提交信息不写会腐烂的数字；提交历史已统一截成一句话。

### 0.5 写 doc
1. 一条规矩只写一个地方；能查到的别抄，能链接的别复述。
2. 状态表每行写依据：读数或处置。
3. 每个数字都要量过；量不到就写“这一层我没量”。
4. 缺口登记必须回答：读者是谁、缺的到底是什么词、缺口见证是哪条判据。
5. 账本里不留行号、套件数、用例数。
6. 代码里不要有非 ASCII 符号 (字符串除外)。

### 0.6 量状态与项目地图
- 状态现量脚本见 `HANDOVER.md` §3；三个不变量收尾时必须为 0：
  ① `implemented ⇔ 无 ⛔`；② `CONTENT-STATUS.md` 与 JSON 双向一致；③ 每条规则都有 `id`。
- 权威语料在仓库外：`E:\turnbasedgamedata\aluminium_texts\<cid>_<角色名>.md`，读 `.md`。
- 角色内容：`src/main/resources/game/characters/<cid>.json`。
- 派生数据：`src/main/resources/data`；`skill_effects.json` 是生成物，部分被跟踪，重生成时要保留手改项。
- 引擎：`src/main/java/com/laosun/aluminium/`；判据：`src/test/java/.../content/characters/<角色>/`。
- 沙箱：Gradle 要写 `C:\Users\laosu\.gradle`；workspace-write 会因 `.lck` 失败，需要 full access。

## 一、行为准则
1. 先量现象，再解释为什么；一次只改一个变量。
2. 每个结论说清证据来自哪一层；量不到就说没量。
3. 错了就撤回，并在原地写 `CORRECTED`。
4. 对照实验本身也要被验证：变红 ≠ 注入成功。
5. 断言落在功能上，不落在仪器代理上。
6. 一处通过 ≠ 处处通过。
7. 诊断器默认做正事，结论必须能被数据推翻。
8. 读官方资料，不反编译字节码。
9. 先问清“你具体看到什么”，再动手。
10. 尊重用户环境：不杀进程、不关窗口、不提权。
11. 文档里的数字必须量过；“写不了”必须附出处。
12. 交付时说清没做什么。
13. 跑闸门先读 `UP-TO-DATE`；要真跑就 `--rerun-tasks`。
14. 变异绿有三种可能：判据弱 / 变量没动 / 变异写坏。
15. 穿过乘区就断言比值；探针打前提，打在构建真正读的文件上。
16. 同形只准有一条；有守卫的压成一行。
17. 不近似、不静默降级、不越权、不把绿当结论。
18. 一轮：读者计数 → 实现 → 判据 → 变异 → 文档 → 提交 → 四道闸门。
19. `{@code X.md}` 不是引用；文档指针靠自己的巡检。

## 二、台账规矩（反增长）
- 不要再新建台账；不新开“第 N 轮”栏。
- 一个形状只有一条规矩；第十次踩同一形状时加计数器，不写长叙述。
- 已有机器守卫的规矩压成一行 + 守卫名；只靠散文的规矩才需要完整叙述。
- 不写“现在是什么”的数字：套件 / 用例 / 文件 / 规则 / 提交数、行号、大小。
- 实验读数可以写，但要标日期；它描述“那一次”，不描述“现在”。
- 状态快照、逐棒日志、完全实现 ID 清单放 `HANDOVER.md`，它是快照不是台账；引用前先现量。

## 三、改引擎
### 三条红线
1. 引擎不认角色：`Battle`、`SkillExecutor`、`Damage`、`CanHit` 里不出现 `cid` 判断。
2. 能数据化就数据化：先补引擎能力，再数据化角色。
3. 一个任务自带判据：没有测试的修复等于没修。

### 三层结构
- 引擎：通用能力，不得出现 cid。
- 数据：触发器 + 条件 + 效果能写完，就不写 Java。
- 逃生舱：跨系统状态机，一个 Java 类 + 登记。

验收标准：新增一个角色机制时，引擎目录下应该一行都不用改。

### 四道闸门
1. 全量测试，必须带 `--rerun-tasks`，报套件数 / 用例数 / 失败数。
2. 五个 demo 输出与基线内容级对比；对比脚本要打印判定行。
3. 捕获 `compare` 与 `replay --strict`。
4. `javadoc`，现在警告也当错。

跑绿了不是结论：纯搬迁用“行多重集不变”证明；语义改动要指名哪条用例覆盖、变异后会不会红。

### 3.7 能力索引（已落地，新增条目）
以下能力已有判据与首个读者；写内容前先查，不要重复造：

- 条件/选择器：`target_buff_count`、`adjacent_enemies`、`lowest_stacks_enemy:<状态>`、`highest_stacks_enemy:<状态>`、`random_enemy_with_state`、`prefer`、`allies_of_path:`、`allies_with_path:`、`lowest_ally_hp_percent`、`target_toughness_percent`、`is_debuffed`、`any_ally has_state`、`self has_same_path_ally`、`!actor is_summon`（布尔可否定）、`damage_is_main_target`、`damage_type_is`、`target_hp_percent_before`、`self_resource_before:<名>`。
- 数值/缩放：`floor`、`chance_from`、`const:`、`summon_attr:`/`actor_attr:` 作 DAMAGE 基数、`base_terms`（可带 `resource` 项与 `cap`）、`parts`（一发多基数）、`blast`（主目标幅度溅射）、`DAMAGE.addSettledFlat` + `ADD_DAMAGE{after_zones:true}`（已结算实例上的绝对加算）、`RAISE_SKILL_MULTIPLIER`（按行份额的倍率，可带敌人数窗口）、`RAISE_RESOURCE_MAX`（抬声明上限，区别于抬溢出额度的 `RAISE_RESOURCE_CAP`）、`per_stack_cap`、`per_stack_live`、`per_stack: target_debuff_count`（`MODIFY_DAMAGE_TAKEN` 上也生效）、`while_resource` + `while_resource_min`。
- 事件/寿命：`STACK_GAINED`、`OVERHEAL`、`SUMMON_LEFT`、`WEAKNESS_ADDED`、`next_skill:<槽位>`（下一次指定技能）、`until: cast_end` / `next_attack` / `next_skill` / `next_ultimate`、`while_state` / `while_holder_state` / `while_stacks` / `while_hp_percent` / `while_holder_hp_percent` / `while_summon_count` / `while_resource`。
- 动作/目标：`attacker: "summon"` / `"actor"` / `"holder_of:<状态>"`、`target_summon`、`other_hit_enemies`、`panel_less` 召唤（含 `summon_speed`）、`DISMISS_SUMMON`、`SUBLIMATED` 控制状态、`blocks: "skill,ultimate"`、`REPLACE_SKILL { remove: true }`、`DISPEL { kind: control }`、`MODIFY_DAMAGE_TAKEN { damage_type: BREAK }`、`scope_category`、`ADD_STACK amount_from_skill_param`、`SHIELD` 的 `add_to_shield` + `cap_*`、`BOOST_TOUGHNESS` 的 `per_stack`、`VulnerabilityBuff.stackCap` / `setPerDebuffStackCap`。

### 3.8 最新缺口六族（按机制排，不按角色）
1. 召唤物/伙伴的“行动”：`panel_less` 已跑通，仍缺“攻击行”与“行动值独立”的建模。
2. 条件语言可表达性：布尔可否定、全队量词已有；仍缺“当前回合属于谁”。
3. 资源的上限/持续/计数：`RAISE_RESOURCE_MAX` 已落；仍缺“按资源值自动失效”的完整形态与 `CAST_COUNT` 一族。
4. 伤害时序：先削韧再结算、致死转伤链、叠层顺序；引擎按实例结算，缺“一次行动的主目标”。
5. 形态/技能替换：`REPLACE_SKILL` 换行，换不了从句。
6. 阈值跨越：血量有 `target_hp_percent_before`，资源有 `self_resource_before:`；仍缺“刚刚跨过”的通用事件形态。

### 3.9 下一步按机制排
优先做“差一条小改动解锁 2–4 个登记”的能力，而不是再实现一个角色：
1. 阈值跨越（1513 星魂 1/2、1224 类）。
2. 按资源值自动失效（1224 充能 <7、1407 上限）。
3. “下一次”的修饰寿命（1224 星魂 6、1205 清空后重算）。
4. 把修饰复制到另一个单位（1407 的“扩散至死龙”）。
5. 按命途/元素分支（1224 星魂 2、1308 星魂 2 前半）。
6. 已结算实例上的绝对加算（1205 星魂 1/6 已落地，可作范式）。
7. “下一次某个技能”的寿命（`next_skill:<槽位>` 已落地，可接 1213 星魂 6）。

## 四、做内容
1. 实现一个角色 = 先摘原文每条 → 逐条落地为规则或 `_gaps` → 写逐节判据。
2. “没有规则”有三种可能，先量再判：数据行驱动、引擎内置、真的漏了。
3. 逐节判据要写“小节 → 规则 id”的映射；一句话拆成多条规则必须逐条列出。
4. 机器关键词初筛假通过率高，真正确认靠手写映射。
5. 缺能力时登记进规则 `_gaps` 或 §4.3；每条登记写清读者、缺的词、缺口见证。
6. 已落地能力不留在缺口区，进能力索引。
7. 不要用脚本批量改写叙述性字段；结构化数字可以脚本改，人类可读说明一条条改。
8. 写 `_gaps` 只许三个开头：`✅` 落地、`⭐` 落地但带取舍/不建模、`⛔` 未解；`implemented` 文件不得含 `⛔`。
9. 读旧 `⛔` 时先做一件事：去代码里找那个词，不要相信登记。已出现多次“引擎早就有”的过期登记。
10. 看见数字先问它是哪一类：参数序号 / 显示序数 / 秒数 / 段数 / 真正的层数。

## 五、收尾 / 交接
- 提交：一行英文；中文文档；两空格缩进；构建产物不入库；分析脚本放仓库外。
- 交付说清：跑了什么、没跑什么、哪些闸门不适用。
- 交接列：HEAD、树状态、闸门数字、脚本在哪、下一批守卫。
- 没量的事写成待核，不许写成事实。
- 交接前 `--rerun-tasks` 真跑全量，报真实基线。
- `HANDOVER.md` 保持未跟踪，`git add` 用具体路径，不要 `-A`。

## 六、核心教训与操作坑
- 先验证前提：目标活着吗、有该属性/抗性吗、战斗启动了吗。
- 技能等级读 `skillLevelBonus(slot)` / `skillLevel(skill)`，不读 `skill.getLevel()`。
- 实例修饰在面板上看不到，判据落在“这一发是否暴击 / 区值”。
- 分母要选对：武器份额、护盾、开局自带层数都可能让比值失真。
- `per_stack` 按层，`stacksOf` 按实例，两者不等价。
- 两条规则修饰同一属性会互相替换；不要同时写固定修饰与按层修饰。
- 结算顺序即语义：同一事件上的规则按文件顺序执行；先读的状态可能被另一条规则改掉。
- “加 buff 没效果”先问事件时机，再问能力；造成伤害“时”加攻击用 `CAST_SETUP`。
- 规则发放的伤害先证明基础量非零，再证明放大系数生效。
- 说“引擎缺机制”之前，先数它在库里被用了多少次；先找近义词，再登记缺具体字段。
- 写规则前先读文件里已有规则，避免重复规则叠两层。
- 判据变红先怀疑夹具与读法，再怀疑引擎；撤回也要有证据。
- 角色 JSON 一律 LF；CRLF 会让字符串替换静默失配。
- 改 JSON 后用严格解析器验证：`[System.Text.Json.JsonDocument]::Parse(...)`；`ConvertFrom-Json` 太宽松。
- 改 `rules` / `_gaps` 前先探位置；别用 `LastIndexOf("\n  ]")`；结构改动走 `edit` 或 JSON 往返，不要按行号手术。
- `_status` 只改第一处；全局替换会改掉每条规则的 `_status`。
- 标记名别混拉丁字母，会被规范化。
- `alwaysRoll(0.0)` 是每次掷骰都成功，不是“不暴击”；80 级暴击率常 > 1，造不出“不暴击”对照组。
- `applyDamage` 会被各区缩放；压血用小额循环。
- 三个 `gate_*` 字段都在回合边界扫，状态在两次边界间变化会晚一拍。
- `MODIFY_ATTR` 不收 `ticks_on`；`SET_STACKS` 必须写 `scale`；`REMOVE_BUFF` 必须写 `amount`。
- `amountFromEvent` 的 DAMAGE 只准挂 `ATTACK_FINISHED`，且强制 `damage_type: TRUE`。
- `effect_percent_scale` 对字面 `percent` 是乘、对行份额是加；被它修正的规则保留字面值。
- 同一字段在不同 op 的 buff 类上未必都实现；写 `per_stack` 前确认那个 op 的 buff 类有没有读它。
- 改一个角色的 kit 前，grep 谁把它当“中性载体”；光锥/遗器判据常假设某角色面板安静。
- 给已有角色补新行为时，先 grep 那个角色的旧判据；它们可能把“尚未实现”当成了前提。
- 登记也要复核：一条“引擎坏了”的登记如果不带对照量，只能当假设。
- 改文档段落后检查标题是否还在行首。

## 七、守卫清单（摘要）
已有机器守卫包括：
- 内容数字来源、技能行硬编码棘轮、等级口径覆盖、内容状态行一一对应。
- 同文件重复规则、非法效果装载、同一数值效果抄进另一条规则。
- 星魂效应藏在非星魂规则、文件头、格式骨架、注解拼写、运行时注解剥离。
- 参数行对账、`value` 表达式、`times` 重复效果、`chance_from`、`STACK_GAINED`。
- 伤害乘区形状、速度与行动条、终结技插入、无头驱动收费/技能点/破韧。
- 类型化载荷、`TriggerContext` 形状、`CanHit` 组合化、引擎不打印、`GameData` 注入。
- 台账不写“现在是什么”的数字、账本行与 JSON 对账、缺口标记硬规则、已实现文件无 `⛔`。
- `CastDamageDeliveryInventoryTest`（这一发伤害归谁：交接 or 额外一击，两个方向都查）。
- `BlastAdjacentColumnTest`（`adjacent_param` / `main_param` 与文档列对照）。
- `GapMarkerTest`（无标记未解项必须为 0）。

只靠散文、没有守卫的重点：
- 三条引擎红线，尤其“引擎不认角色”。
- 禁止 `Math.random()`。
- 夹具前提不许被抽走。
- 提交纪律。
- “同形只准有一条”。
- 四道闸门没有 Gradle 任务强制，全是手跑命令。

## 八、缺口登记格式
每条登记必须能回答：
1. 读者是谁：哪条内容要它。
2. 缺的到底是什么词 / 事件 / 字段。
3. 缺口见证：一条钉住“今天读数”的判据，能力落地时它红。

没有见证就把今天的样子钉住；否则下一个人只能重新推导。

## 九、构建与环境
- 唯一入口：`.\gradlew.bat gate --console=plain`。
- 必须看到 `GATE: PASS tests=... failures=0 suites=... ; demos=5 ; scenarios=5 ; replay --strict ; javadoc`。
- 已配阿里云 Maven/Gradle 插件镜像；慢主要来自“改主源后全量重编译测试类”，不是启动。
- 改主源后第一次闸门读套件数，别只看 `PASS`；编译通过 ≠ 该类被编译过。
- 提交历史已统一截成一句话；旧历史在标签 `backup-before-msg-rewrite`，确认后可删。