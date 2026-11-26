# aluminium 重构方案

> 测量基准：`main` @ `cadfc872`，2026-11-25 现量。文中每个数字都是那一次的读数，不是回忆。
> 这份文件是**一次性方案**，不是台账：落地后删掉，或把结论并进 `AGENT.md`。**不提交**（同 `HANDOFF.md` / `HANDOVER.md`）。

---

## 0. 一句话结论

这个项目的复杂度不在 26k 行引擎代码里，而在**三处没有单一主人的事实**：

1. 一个 op（触发器效果词）的裁决散在 **7–9 个注册点**，任何一处漏改都是静默失效；
2. 一场战斗的状态归属不明：`Battle` 是 159 个方法的服务定位器，回合身份同时存在于四处，每场战斗的规则状态却挂在**跨战斗共享**的单位对象上；
3. 同一条"发生了什么"有 **四套通知面**，同一个名字有 **八种前缀壳**，同一份文档有 **两个会漂移的副本**。

重构的正确姿势不是"重写引擎"，而是**给这三处各自指定唯一的主人**，然后把已有的 3520 个判据当作等价性证明用掉。清 `clean gate` 只要 **59 秒**（增量 45 秒），这是这个项目做重构最大的资本：反馈回路比大多数人想的短。

---

## 1. 现量（先看事实，再谈方案）

### 1.1 体量

| 项 | 读数 |
|---|---|
| 引擎 | 201 个文件 / 26,366 行有效代码 / 55,984 行总行数 |
| 判据 | 926 个文件 / 98,998 行有效代码 / 3,520 个用例 / 917 个套件 |
| 工具 + demo | 2,776 + 627 行 |
| 主源注释占比 | 25,563 / 55,984 = **45.7%** |
| 内容 | 96 角色（1,681 规则、2,503 效果）、171 光锥（1,665 规则）、63 遗器（122 规则） |
| 闸门 | `GATE: PASS tests=3520 failures=0 suites=917`；`clean gate` **59 秒** |

### 1.2 改动热区（最近 300 个提交）

| 文件 | 改动次数 |
|---|---|
| `Battle.java` | 67 |
| `trigger/TriggerInterpreter.java` | 66 |
| `beans/EffectSpec.java` | 53 |
| `trigger/TriggerValidator.java` | 35 |
| `trigger/TriggerEffects.java` | 29 |
| `trigger/TriggerConditionParser.java` | 28 |
| `trigger/TriggerTable.java` | 28 |

**54 / 300 个提交同时动了引擎和内容。** 而 `AGENT.md` §三 的验收标准是"新增一个角色机制时，引擎目录下应该一行都不用改"。这条标准现在大约每五次改动失败一次——这就是重构要买的东西。

### 1.3 包依赖（全部成环）

`(root)`、`battle`、`trigger`、`buff`、`entity`、`enums`、`data`、`damage`、`skill`、`enemy`、`equipment`、`state`、`energy`、`skillpoint`、`utils`、`event` 之间**任意两个包几乎都有双向边**。几处最贵的反向依赖：

| 反向边 | 引用数 | 具体 |
|---|---|---|
| `buff → Battle`（根包） | 16 | `AbstractBuff`、`BuffManager`、`CounterMechanic`、`ReductionBuff`、`VulnerabilityBuff` |
| `entity → Battle` | 13 | `CanHit` 的钩子签名 `onKill(Battle, …)` |
| `enums → data` | 2 | `DamageElement`、`SkillEffectType` 引 `data.SkillData` —— 叶子反向依赖 |
| `battle → (root)` ↔ `(root) → battle` | 30 / 35 | 服务拿 `Battle`，`Battle` 拿服务 |
| 正文里手写的全限定名 | **414 处** | `com.laosun.aluminium.xxx.Yyy` 直接写在方法体里 |

414 处内联 FQN 是"包设计已经失效"的体温计：作者宁可写全名，也不愿意加 import，因为加了就更乱。

---

## 2. 根因（按代价排序）

### R1 —— 一个 op 的裁决没有主人，散在 7–9 处

现在"新增一个 op"要同时改：

1. `trigger/TriggerOperator.java`（枚举常量 + `wired` 标志）
2. `trigger/TriggerValidator.validateOne`（1,320 行的方法，51 个 arm，110 个 throw）
3. `trigger/TriggerInterpreter.applyOne`（466 行的方法，63 个 arm）
4. `trigger/TriggerVocabulary` 的 4 个集合（`VALUE_OPS`、`DAMAGE_TYPE_READERS`、`SCOPE_ELEMENT_READERS`、`OPS_WITH_DURATION`…）
5. `beans/EffectSpec` 的字段（139 个）+ `EffectSpec.copy()` 的手抄清单
6. `TriggerInterpreter` 的类注释 op 表（**只写了 19 个 op，实际 63 个**，且 `DAMAGE` 那一行已经被 `TriggerValidator` 推翻）
7. `TRIGGER.md` 的 op 表（已漏 6 个：`MODIFY_DAMAGE_TAKEN`、`MODIFY_RULE`、`IGNORE_WEAKNESS`、`BOOST_DOT`、`SET_STACKS`、`REPLACE_SKILL`）
8. 2–4 个守卫测试
9. `EffectSpec.copy()` 由反射测试兜着，否则漏抄字段是静默的

**已经发生的漂移（不是推测）：**

- `TriggerValidator` 的 switch 有 51 个 arm，`TriggerInterpreter` 有 63 个：**12 个已接线 op 在加载期没有自己的校验 arm**（`SHIELD`、`EXTRA_TURN`、`INSERT_ACTION`、`RETURN`、`SET_STACKS`、`BOOST_DOT`、`SUMMON_SERVANT`、`DISMISS_SUMMON`、`RAISE_ENERGY_BANK`、`SPEND_RESOURCE`、`REMOVE_RESOURCE`、`IGNORE_WEAKNESS`）。
- `EffectSpec.as_attack` **有 3 个内容文件在用**（`1015.json:287`、`1107.json:44`、`23013.json` 共 10 处），但全仓只有 `TriggerValidator` 读它（3 处），**运行期没有任何执行路径**。而 `1015.json`（Archer）的 `_note` 明写着"`EffectSpec.as_attack` 的注释就是「a follow-up attack: a real hit」，会让这一发参与攻击级事件与被击回能 ✓；⚠ 这是该字段的**第一个内容读者** ✓"——**读者不存在**。这是一个已经上线的静默无效字段，被算进"完全实现"的角色里。
- 同一个参数语法（`<名>:<数>:<数>`）被**手工切了 14 处 `lastIndexOf`，散在 6 个文件**（`TriggerInterpreter` 4、`TriggerTargets` 3、`TriggerMagnitudes` 2、`TriggerTable` 2、`TriggerConditionParser` 1、`TriggerValidator` 1）。`per_steps` 在 4 处解析，`until` 在 2 处，`scale` 在 3 处。
- `TriggerValidator` 反向调用 `TriggerInterpreter.parseCounterSteps` 来解析，而校验本身的入口又是 `TriggerTable.compile → TriggerInterpreter.validate → TriggerValidator.validateOne`。依赖是倒的。
- 条件语言 `parseCondition` 是 **642 行的单一方法**，28 个前缀分支靠**书写顺序**消歧（`has_added_weakness` 必须排在 `has_weakness` 前、`is_other_ally` 必须排在 `is_ally` 前、`has_companion_ally` 必须排在 `is_companion` 前），分支用 `find()` 而非 `matches()`，所以"关键字出现在任何位置"就命中。这个顺序只写在每段的注释里，没有任何守卫。
- 数值变量词汇表登记**两遍**：`TriggerConditionParser.NUMERIC_VARIABLES`（33 个名字）与 `TriggerTable.value` 的 33 个 `case` 标签，今天靠人手对齐。

### R2 —— `Battle` 是服务定位器，回合身份有四份

| 项 | 读数 |
|---|---|
| 实例字段 | 45（其中 28 个是「服务」对象，10 个声明直接写了内联全限定名） |
| public 方法 | 159 |
| 纯转发（`return service.x(...)`） | 90 |
| 外部对 `battle.*` 的调用点 | 367 处，来自 21 个类 |

`battle/` 包里 40 个"服务"已经把**存储**搬走了，但**顺序**没搬：回合边界仍然散在 7 处（`Battle.beforeMove`、`Battle.afterMove`、`TurnLoop.step`、`Queue`、`BuffManager` 的 tick、`ExtraTurnState`、`Battle.fire`）。

同一个事实被存了多份：

| 事实 | 副本 |
|---|---|
| 当前行动者 | `Battle.currentMove`、`Queue.currentActor`、`Queue.move()` 的返回值 |
| 额外回合 | `ExtraTurnState.actor`、`Queue.extraTurnActor`、`Queue.extraTurnsOwed` |
| 波次 | `WaveState.sequence`、`WaveManager.waveIndex` |
| 护盾 | `CanHit.ShieldState`（标量）、`ShieldBuff`（`has_state sys:shield`）、`BuffManager` 的 shield 查询 |
| 上一发终结技耗能 | `UltimateService.lastEnergySpent` 被 `BattleDamage` 反向读取 |

已确认的边界顺序隐患（读代码可见，非推测）：

- `stepForward` 在 `move()` **之前**快照 `hasPendingExtraTurn()`；若持有者已死，`Queue.moveExtraTurn` 退回普通 `move()`，但 `Battle` 仍然给新的当前行动者发 `EXTRA_TURN_STARTED` 并当成免费回合。
- `beforeMove` 在"跳过"分支提前 return，**跳过了 `damageMarks.clear()` 与 `TURN_START`**；而 `oncePerTurnUsed` 只在 `TURN_START` 清，于是"每回合一次"的键会漏到下一个行动者。
- `WAVE_START` 从两处发：`startBattle`（序号 0）与 `WaveManager.nextWave`（序号 +1），波次限次却是按这个序号键的。

### R3 —— 每场战斗的状态挂在跨战斗共享的单位对象上

`CanHit` 是 abstract class，1,379 行、108 个 public 方法、22 个字段（其中 **12 个是状态容器**），并且 `implements` **11 个事件接口**（`BattleEvent, MoveEvent, DamageEvent, AttackEvent, SkillCastEvent, EnergyEvent, HpLossEvent, HealEvent, KillEvent, BreakEvent, SkillPointEvent`）。

问题不是它大，而是**它同时是定义和运行时**：

- `TriggerCooldowns`、`RuleLimits`、`RuleAmendments`、`RuleChanceState`、`SkillBonuses` 全是**本场战斗**的事实，却存在单位上；
- 于是需要一个 `onBattleStart` Runnable 去重置它们，注释里自己写着"否则会泄漏到下一场"；
- 于是"同一份 `Character` 进两场战斗"要靠人工纪律，而不是类型；
- 基类还要 `instanceof Character` / `instanceof Summon` 向下转型（自己的子类），说明这层抽象切错了位置；
- 3 个 `public` 可变的 `Runnable` 字段（`beforeMove/afterMove/onBattleStart`）让任何调用者都能替换单位的回合行为。

### R4 —— 四套通知面

| 面 | 形状 | 谁用 |
|---|---|---|
| `event/**` | 11 个极小的接口（`onKill(Battle,…)`） | `CanHit`、`BuffManager` 等 5 个类 |
| `BattleEvents` | 8 个具名方法（`onKill`、`onHeal`…）+ "涉及方 + 我方全体"路由 | 引擎内部 |
| `Battle.*Observer` | 7 个嵌套接口 + `BattleObservers` + 14 个 add/remove 方法 | 测试、demo、tools |
| `TriggerPayload`/`TriggerBus` | 8 个 record + 45 个事件枚举 | 规则表 |

这四套描述的是同一件事："刚刚发生了什么，谁该知道"。结果是每加一个事件要决定"从哪一套发"，而且同一个语义（例如"护盾获得"）在两套里各发一次。

### R5 —— 包依赖全环，编译器帮不上忙

见 §1.3。后果不是"不优雅"，而是：改 `buff` 里一个签名可能要求改 `Battle`，而 `Battle` 又在 `buff` 的调用链上——**没有单向依赖，就没有局部推理**。

### R6 —— 文档是第二份代码库，而且已经分叉

| 文档 | 体量 | 状态 |
|---|---|---|
| `HANDOVER.md` | 545 KB / 5,637 行 | 未跟踪，逐棒日志 |
| `TRIGGER.md` | 50 KB | op 表漏 6 个 |
| `HSR.md` | 48 KB | 机制参考 |
| `CONTENT-STATUS.md` | 37 KB | 台账 |
| `HANDOVER-NAMING.md` | 41 KB | 未跟踪 |
| `HANDOFF.md` | 30 KB | 未跟踪 |
| `AGENT.md` | 17 KB | 行为总纲 |

已经证实的自相矛盾：

- `AGENT.md` §七 说"四道闸门没有 Gradle 任务强制，全是手跑命令"，§九 说"唯一入口 `gradlew gate`"，而 `build.gradle` 里确实有 `gate`（并强制 test/compile 重跑）。同一份纲领先自相矛盾。
- `HANDOFF.md` §1 记 `tests=3999 suites=1258`，现量是 **3520 / 917**。
- `TriggerInterpreter` 的类注释 op 表写 19 个，实际 63 个，其中 `DAMAGE` 那一行已经与 `TriggerValidator` 冲突。

主源 45.7% 是注释，且 `javadoc` 用 `-Werror` 跑——这意味着**过期的散文会变成编译门**。注释在替 AGENT.md 干活，两边都在腐烂。

### R7 —— 测试重复的是前提，不是行为

| 项 | 读数 |
|---|---|
| `@ParameterizedTest` / `@Nested` / 生命周期钩子 | **0 / 0 / 1**（全仓只有 1 个 `@BeforeAll` 之类） |
| 重复"建战斗 + startBattle"这个 6 行惯用式的文件 | 355 |
| 自己手写一个 `Fixture` 类/record 的文件 | 119 |
| 做"打之前/打之后血量差"探针的文件 | 209 |
| 写 `System.out.println` 做叙述的文件 | 478 |
| 5 行窗口的重复片段（跨文件） | 658 |
| 测试总耗时 | 9.64 秒（3,520 个用例） |

`BattleFixture` 已被 739 个文件使用——说明**常量**已经收敛了，没收敛的是**形状**。而且有两个守卫在惩罚正确改动：

- `ContentProvenanceTest`：`assertEquals(RECORDED.size(), found.size())` + `fixed.isEmpty()` ⇒ **修好一个缺口会让测试变红**。
- `build.gradle` 只声明了 `AGENT.md` 与 `TRIGGER.md` 为 test 输入；读 `CONTENT-STATUS.md` 的 `LedgerCountsMatchJsonTest`、`LedgerListsEveryShippedCharacterTest` **没有声明** ⇒ 只改台账时 `:test` 会 UP-TO-DATE，守卫根本不跑。这正是 `build.gradle` 自己注释里写的那个坑，只是没修完。
- `TriggerDispatchCoverageTest` 用**源码正则**数 `case ([A-Z_]+) ->` 的个数，等于**禁止合并 arm**（`case HEAL, SHIELD ->`）——正好是这次重构最想做的编辑。

---

## 3. 目标架构

### 3.1 分层与依赖方向

只允许向下依赖，用一个**守卫测试**钉住（约 80 行扫 import，不加新依赖）：

```
L3 app      demo, tools, test
L2 engine   battle, Battle(门面), event          ← 唯一允许持有运行时全图
L1 dsl      trigger(parse/compile/execute), data, beans
L0 model    entity, state, buff, skill, damage, energy, skillpoint, equipment, enemy
L-1 core    enums, DoubleValue, exceptions        ← 不允许依赖任何东西
port        engine.port：只放接口，被 L0/L1 依赖，由 L2 实现
```

三个必须先拆的反向边：

| 反向边 | 替换成 |
|---|---|
| `BuffManager` 只用到 `Battle.fireStateEnded` | 一个方法的 `StateEndNotifier` 端口 |
| `CanHit` 的 `onKill(Battle,…)` 等 11 个钩子 | `TriggerSink`（`fire`）+ `TurnOrderPort`（`refreshSpeed`） |
| `enums.DamageElement` / `SkillEffectType` 引 `data.SkillData` | 把那个辅助方法移进 `SkillData` |

这一层做完，`buff`、`entity`、`damage`、`skill`、`enemy`、`equipment`、`event`、`utils` 对根包 `Battle` 的 **78 处引用**可以清零（其中 `buff` 16、`entity` 13、`event` 11、`utils` 10）。

### 3.2 op 的唯一主人：handler 注册表

原则：**一个 op = 一个类 = 读一次字符串**。磁盘上的 JSON 词汇**完全不变**（96 个角色文件、1,681 条规则、2,503 条效果一行都不用改），变的只是引擎怎么组织这些知识。

```java
package com.laosun.aluminium.trigger.op;

/** Everything the engine knows about one op: how to read it, how to refuse it, how to perform it. */
public interface OpHandler<A extends EffectArgs> {

    TriggerOperator op();

    /** The only reader of the spelling. Runs once, at load time. */
    A parse(EffectSpec raw, TriggerSpec rule);

    /** Runs once, at load time, on the parsed form. */
    void validate(A args, EffectSpec raw, TriggerSpec rule);

    /** Runs every firing, on the parsed form. */
    void apply(OpContext ctx, A args);
}
```

```java
record GainEnergyArgs(double amount, TargetSpec target, boolean toMax) implements EffectArgs {}

final class GainEnergy implements OpHandler<GainEnergyArgs> {
    public TriggerOperator op() { return GAIN_ENERGY; }

    public GainEnergyArgs parse(EffectSpec raw, TriggerSpec rule) {
        // the four spellings of "an amount" collapse here, once
        return new GainEnergyArgs(TriggerMagnitudes.amountOf(raw, rule),
                                  TargetSpec.of(raw.getTarget(), rule),
                                  Boolean.TRUE.equals(raw.getToMax()));
    }

    public void validate(GainEnergyArgs a, EffectSpec raw, TriggerSpec rule) {
        require(a.amount() > 0, "GAIN_ENERGY needs a positive amount", rule);
        require(!a.toMax() || a.amount() == 0, "...", rule);
    }

    public void apply(OpContext ctx, GainEnergyArgs a) {
        for (CanHit target : ctx.targets(a.target())) {
            ctx.port().gainEnergy(target, a.toMax() ? target.getMaxEnergy() : a.amount());
        }
    }
}
```

注册表就是词汇表本身：

```java
final class OpRegistry {
    private static final Map<TriggerOperator, OpHandler<?>> ALL = of(
            new AbsorbDamage(), new AddDamage(), new Advance(), new ApplyBuff(), /* ... 63 个 */);

    static {
        Set<String> registered = ALL.keySet().stream().map(Enum::name).collect(toSet());
        if (!registered.equals(TriggerOperator.wiredNames())) {
            throw new IllegalStateException("every wired op needs exactly one handler: missing "
                    + minus(TriggerOperator.wiredNames(), registered) + " / stale " + minus(registered, TriggerOperator.wiredNames()));
        }
    }
}
```

（用 `throw` 而不是 `assert`：Gradle 默认不开 `-ea`，断言会静默失效——这与本项目"守卫不跑比没有守卫更糟"的教训同形。）

`TriggerTable.compile` 把每条效果编译成 `CompiledEffect(EffectSpec raw, EffectArgs args)`，运行期的 `applyOne` 退化成一次查表：

```java
for (CompiledEffect effect : rule.effects()) {
    OpRegistry.handler(effect.raw().op()).apply(ctx, effect.args());
}
```

**必须提前设计的三件事**（否则会返工）：

1. **`BattlePort`**：handler 从一开始就依赖端口而不是 `Battle`。端口可以先胖（40 个方法），它的价值不是好看，而是**让一个 op 可以脱离一场战斗被单测**——63 个 op 各自可测，这是 R1 的附带收益。
2. **`MODIFY_RULE` 会改效果本身**：`TriggerInterpreter.amendedEffect` 每次触发都会把修正并进 `EffectSpec`。`CompiledEffect` 只能当**模板**，修正要产出**新的 args record**（`args.amendedWith(patch)`），不能改写缓存。
3. **`TriggerValidator` 的 110 条 throw 全部带 `spec.getSource()`**：迁移时把错误消息一起搬进 handler 的 `validate`，不要重写文案——判据是断言在消息上的。

### 3.3 状态归属：定义 / 运行时 / 顺序

```
Unit（定义，可跨战斗共享）        Combatant（每场战斗一份）
  cid, name, camp, level            hp, energy, shield, buffs, resources
  attributes base                   triggerCooldowns, ruleLimits, ruleAmendments, ruleChances
  skills, triggerTable              skillBonuses
```

- `CanHit` 拆成 `Unit`（不可变）与 `Combatant`（可变）。`onBattleStart` 那个重置 Runnable **可以删掉**，因为新对象天然是干净的。
- 回合身份收敛成一个值：`Queue.move()` 返回 `TurnToken(actor, kind, round)`，删掉 `Battle.currentMove` 与 `ExtraTurnState` 的重复副本。
- 回合边界收敛成一个类 `TurnBoundary`，它持有唯一一份顺序（`stepForward → TURN_START → beforeMove → act → afterMove → 结算`），`Battle` 只调用它。**顺序就是语义**，所以这个类的注释就是规格，§2 里那三个已确认的顺序隐患在搬迁时一并修掉（并各配一条判据）。

### 3.4 事件：一套，带类型

`event/**` 的形状是对的（小接口、payload 明确），把它扩成唯一的通知面，用 sealed 层级 + 一个 dispatch（现有的 6 行标记接口 `event.BattleEvent` 被这个 sealed 根取代）：

```java
sealed interface BattleEvent permits DamageDealt, Healed, ShieldGranted, Broken, StateEnded,
        StackGained, ResourceChanged, Killed, TurnSkipped, RuleFired { CanHit subject(); }
```

- `Battle.*Observer` 与 `BattleEvents` 都退化成订阅者；
- `TriggerPayload` 保留（它有 45 个事件枚举、是规则表的投递格式），但**由同一个 dispatch 派生**，而不是第二套分发。

---

## 4. 路线图

原则：**每一阶段独立可交付、闸门绿、可回滚**。阶段之间不允许"半完成态"过夜——一个 op 要么走旧路径，要么走新路径，绝不能两条都活着。

| # | 阶段 | 估 | 风险 | 收益 |
|---|---|---|---|---|
| 0 | 安全网 | 1 棒 | 无 | 让后面每一步都有等价性证明 |
| 1 | op 注册表 + BattlePort | 12–18 棒 | 中 | 新增 op 从 7–9 处降到 2 处；引擎/内容解耦；op 级单测 |
| 2 | `EffectSpec` → args record | 3–5 棒 | 中 | 139 个可空字段 → 每 op 一个 record |
| 3 | 测试架构（与 1/2 并行） | 8–12 棒 | 低 | 消 355 个文件的样板；守卫不再惩罚正确改动 |
| 4 | 数据 / 身份层 | 3–4 棒 | 低 | 名字拼错从静默变红；去重复解析 |
| 5 | 状态归属 + Battle 拆分 | 10–15 棒 | **高** | 每场战斗泄漏消失；回合顺序单点 |
| 6 | 事件统一 | 3–5 棒 | 中 | 一个事件一个发点 |
| 7 | 文档与守卫收敛 | 3–5 棒 | 低 | 注释不再替配置干活 |

合计 **43–65 棒**（估计，不是读数）。若只做 0–2（**16–24 棒**），R1 基本解决、R2 的入口打开，这是推荐的最小可行方案。

---

### 阶段 0 —— 安全网（1 棒，必须先做）

**为什么**：`build.gradle` 自己写着 `gate` **不做** demo 与基线的对比（"那需要人说这次变更是想要的"）。重构期间每一次变更都是"想要的"，所以现在必须补上机器判据，否则"绿"只证明没崩，不证明没变。

**动作**

1. `gateBaseline`：把 5 个 demo 的输出快照到 `scenarios/baseline/*.txt`，`gate` 里加一步 diff。**这是重构期唯一的"行为不变"证据**，跑完即可删。
2. `DependencyDirectionTest`（新文件，扫 import）：把 §3.1 的允许边写成表，越界即红。**先让它对现状红一次**，再登记当前违规为基线白名单，之后只准减不准增。
3. 修两个反向守卫：
   - `ContentProvenanceTest`：`assertEquals(RECORDED.size(), found.size())` 改成子集断言（`found ⊆ RECORDED ∪ FIXED`），修好的缺口不再让测试红；
   - `build.gradle`：补 `inputs.file('CONTENT-STATUS.md')`——否则台账守卫根本不跑。
4. `BattleFixture.started(party, enemies)`（纯新增，不动任何调用点）。

**证明**：gate 绿；三个反向验证——① 故意改一行伤害系数，`gateBaseline` 必须红；② 故意加一条越界 import，`DependencyDirectionTest` 必须红；③ 修掉一个已登记的缺口，`ContentProvenanceTest` 必须**绿**（现在会红）。

**停止条件**：上述三个反向验证都做过再进阶段 1。

---

### 阶段 1 —— op 注册表 + BattlePort（12–18 棒）

**这是主菜**，但它是可以切香肠的：一次迁 4–6 个 op，一提交一闸门。

**里程碑**

| 里程碑 | 内容 | 出口判据 |
|---|---|---|
| M1 | `engine.port` 包 + `BattlePort`（先胖） | 能写一个 op 的假端口单测 |
| M2 | `OpHandler`/`EffectArgs`/`CompiledEffect`/`OpRegistry` 骨架 + 迁 **5 个简单 op**（`GAIN_ENERGY`、`GAIN_SKILL_POINT`、`HEAL`、`DISPEL`、`REMOVE_STACK`） | 旧 arm 删除；63 个 op 的注册表断言等于 `wiredNames()` |
| M3 | 按**频率**迁：`RAISE_SKILL_LEVEL`(625)、`MODIFY_ATTR`(431)、`DAMAGE`(143)、`GAIN_RESOURCE`(136)、`APPLY_BUFF`(136)、`ADD_STACK`(130)、`REMOVE_STATE`(97)、`GAIN_ENERGY`(89) = **约占全部效果的 71%** | 前 8 个 op 走新路径 |
| M4 | 剩下的 55 个，按家族分批：资源族 / 修正族 / 召唤族 / 伤害族 / 技能行族 | 63 个 arm 全迁完，`applyOne` 与 `validateOne` 的 switch 删除 |
| M5 | 删掉 `TriggerInterpreter` 的 19 行 javadoc op 表，改为一句指针；`TRIGGER.md` 的 op 表改为从 `OpRegistry` 生成 | 只剩一个 op 登记处 |

**每次迁移的固定动作**（一条 op 一个提交）

1. 写 `<Op>Args` record + `<Op>Handler`（`parse`/`validate`/`apply`）——**错误文案原样搬**。
2. `OpRegistry` 加一行。
3. 删 `TriggerValidator.validateOne` 对应 arm、删 `TriggerInterpreter.applyOne` 对应 arm。
4. `gate` 绿，**且用例数不变**（少一个就是漏了覆盖）。
5. 一条规则：**不准两条路径同时活**。

**风险与对策**

| 风险 | 对策 |
|---|---|
| `MODIFY_RULE` 的运行时修正会改效果本身 | `CompiledEffect` 当模板，修正产出新 args（见 §3.2） |
| 共享 arm（`GAIN/SPEND/REMOVE_RESOURCE` 共用一个校验）拆开后语义漂移 | 先补一条"三者的差异"判据，再拆 |
| `TriggerDispatchCoverageTest` 禁止合并 arm | M2 里就把它换成注册表断言 |
| 63 个 handler 的类数量吓人 | 目录 `trigger/op/` 下按家族分子包；每个 handler 约 30–80 行，比现在 1,320 行的 `validateOne` 好读 |

**收益（可量）**：新增 op 的注册点从 7–9 → 2（枚举 + 一个 handler 文件）；`TriggerValidator` 从 3,060 行降到约 800（只剩跨字段的全局约束）；引擎/内容同提交率（现 18%）应下降。

---

### 阶段 2 —— `EffectSpec` → 每 op 的 args（3–5 棒）

阶段 1 完成后，`EffectSpec` 只剩"所有 op 共用"的字段（`op`、`target`、寿命、权重…），其余 130 个字段被 args record 取代。

同时清掉已经量出来的死重：

- 删 `as_attack`（或真的接线），删 `stacks`（只有 `TriggerValidator` 读）；
- `percent` / `percent_from` / `percent_from_skill_param` / `value` **四种拼法收成一个 `Magnitude`**；
- 27 个"只有一个读者"的字段随各自 op 进 record。

**守卫**：`EffectSpec` 字段数 ratchet（只准减），与项目现有的 `HardcodedSkillRowRatchetTest` 同一风格。

---

### 阶段 3 —— 测试架构（8–12 棒，可与 1/2 并行）

**动作（按性价比排序）**

1. `BattleFixture` 只加四个东西（全部是"只组合调用者给的事实"）：
   ```java
   static Battle started(List<Character> party, List<? extends CanHit> enemies);
   record ConeScene(Character wearer, Character ally, Enemy enemy, Battle battle, Integer coneId) {}
   static double dealt(Battle b, CanHit from, CanHit to, DamageElement e, DamageType t, double base);
   static void ratioIs(double expected, double plain, double boosted, double tol, String premise);
   ```
   迁 35 个 `battle(boolean withCone)` 文件 + 209 个血量差探针文件。**前提仍留在调用点**——这条不能破。
2. 把 29 个 `LightConeAuditBatch*`（152 个用例）转成 `@ParameterizedTest`；这是零风险的样板消除。
3. `@Tag("guard")` 给 56 个"扫源码/读文档"的守卫，加第二个 `Test` 任务 `includeTags`，让行为套件可以脱离语料扫描跑。
4. 478 个 `System.out.println` 收进一个 `Narrator`（`--info` 才输出）。

**明确的"不要做"**：不要重写用例、不要为了参数化把"一次只钉一个性质"拆掉、不要动那 394 个内容行为文件的断言（它们是行为基线）。

---

### 阶段 4 —— 数据 / 身份层（3–4 棒）

1. **`state_names.json` 现在没有任何生产读者**（377 个身份，只有 `StateNameRegistryTest` 读它）。把它接进 `TriggerTable` 的编译期校验：`has_state` / `buff` / `resource` 的名字不在声明表里就**加载期报错**。这是"拼错名字静默失效"这一类 bug 的根除。
2. `TriggerTables.warehouse` 每场战斗重读一次（`Battle.startBattle` 里注册 warehouse 监听时）→ 加缓存；`TriggerTable.plus` 每次 `CharacterFactory.create` 重新合并+重校验 → 按 `(cid, cone, relic)` 缓存。
3. 8 个 `*_PREFIX` 家族的 30 个前缀常量分散在 6 个文件 → 收进一个 `SelectorPrefix` 枚举表，解析/执行/校验共读一份。
4. 表单校验（`TriggerTables` 的反射 key 白名单）与语义校验（`TriggerValidator`）合并成**一份声明**，避免两边各记一套。

---

### 阶段 5 —— 状态归属 + Battle 拆分（10–15 棒，风险最高）

**必须放在最后**，因为它依赖前四个阶段把"知识"从 `Battle` 里搬出来。

三个子阶段，每个都可独立收尾：

| 子阶段 | 动作 | 证明 |
|---|---|---|
| 5a | `CanHit` → `Unit`（定义）+ `Combatant`（运行时）；删 `onBattleStart` 重置 | 新建一场用同一个 `Character` 对象的判据：第二场读数为 0 |
| 5b | 回合身份收敛为 `TurnToken`；删 `Battle.currentMove`、`ExtraTurnState` 的重复副本 | §2 R2 的三个顺序隐患各配一条判据，修前红、修后绿 |
| 5c | `TurnBoundary` 收走 `beforeMove`/`afterMove`/`stepForward`；`Battle` 只剩门面 | 行多重集不变 + 全量集成用例绿 |

**风险**：这是唯一可能让内容行为变化的阶段。对策：5a/5b/5c 各只做一件事；每步先加判据再搬代码；`gateBaseline` 必须绿。

---

### 阶段 6 —— 事件统一（3–5 棒，可跳过）

把四套通知面收成一套（§3.4）。收益是真收益但不是瓶颈；**如果时间紧，这一阶段可以永远不做**。

---

### 阶段 7 —— 文档与守卫收敛（3–5 棒）

1. **javadoc 只写契约**：方法做什么、抛什么、有什么前置。现在的 45.7% 注释率里，大量是"为什么这个类存在"的议论文——那是文档的活，不是编译门的活。
2. 生成而非手写：op 参考表从 `OpRegistry` 生成，事件表从 `TriggerEvent` 生成（项目里已有 `TriggerEventTableDriftTest` 在做双向核对，说明手抄表已经被证明会漂）。
3. `AGENT.md` 修两处自相矛盾（§七 vs §九 关于 `gate`）；把"能查到的别抄"落成一条守卫：**主源注释里出现 op 表/参数表即红**。
4. `HANDOVER.md`（545 KB）按"一棒一节"切分或直接归档——它是未跟踪的快照，不是台账，但已经长成了台账。

---

## 5. 怎么证明"这只是搬家"

项目自己的规矩（`AGENT.md` §三）："跑绿了不是结论"。所以每个阶段都要给出**层级证明**：

| 层 | 手段 | 适用 |
|---|---|---|
| L1 结构等价 | 行多重集不变（纯搬迁）；git diff 的增删行多重集相等 | 阶段 5c、阶段 2 |
| L2 行为等价 | `gate`（3520 用例）+ `gateBaseline` demo 快照 + 5 个 capture `compare` + `replay --strict` | 每个提交 |
| L3 结构守卫 | 依赖方向、op 注册表完整性、`EffectSpec` 字段 ratchet、禁内联 FQN ratchet | 阶段 0/1/2 |
| L4 变异抽检 | 每阶段一次：故意改回旧行为，**指名**哪条用例必须红 | 每阶段收尾 |

**用例数只准增不准减**：阶段 1 每迁一个 op，用例数不变才算没漏覆盖（这是 `build.gradle` 注释里那个"移动类导致少跑四个套件却 PASS"的教训）。

---

## 6. 不做什么（同样重要）

| 不做 | 为什么 |
|---|---|
| **不改内容 JSON 的词汇** | 96 角色 / 1,681 规则 / 1,665 光锥规则是这个项目的资产，语料还在仓库外。阶段 1 的全部设计都围绕"磁盘格式不变" |
| **不先拆 Gradle 模块** | 现在的包依赖全环，拆模块会把环变成编译错误，直接把项目卡死。先做端口（阶段 1/5），再拆 |
| **不重写判据** | 98,998 行判据是**唯一的行为规格**。只做"加夹具、参数化、打标签" |
| **不引入 DI 容器 / 新框架** | 现在只有 8 个依赖，全部必要。`BattlePort` 是手写接口，不需要 Spring |
| **不删守卫** | 那些守卫编码着踩过的坑（"登记会过期"、"已实现的文件不得含 `⛔`"）。只修**惩罚正确改动**的那两个 |
| **不在引擎提交里动内容** | 违反 `AGENT.md` §0.3-4，而且会让"行为变化"无法归因 |
| **不追求 100% 迁移** | 若只做阶段 0–2，R1 解决、R2 的门打开——这就是推荐的最小可行方案 |

---

## 7. 需要你拍板的事

1. **优先级**：先做 op 注册表（我建议，收益最集中且不碰内容），还是先做状态归属（更痛，但风险高）？
2. **预算**：只做 0–2（16–24 棒，R1 解决 + 测试架构），还是走到 5（43–65 棒，R2/R3 一起解决）？
3. **端口粒度**：`BattlePort` 先做胖接口（快，40 个方法）还是直接切 6 个小端口（慢，但阶段 5 不用返工）？我建议**先胖后切**，因为 op 迁移的收益不依赖端口的细度。
4. **文档**：`HANDOVER.md`（545 KB）是归档、切分，还是继续追加？它已经违反 `AGENT.md` §二"不要堆砌文档"。

---

## 8. 第一棒（可以直接开工的具体清单）

```
1. build.gradle        + gateBaseline（5 个 demo 输出快照 → scenarios/baseline/*.txt，gate 里 diff）
2. build.gradle        + inputs.file('CONTENT-STATUS.md')（补上漏声明的守卫输入）
3. test/arch/DependencyDirectionTest.java   新守卫；先对现状红一次，再登记白名单
4. test/content/ContentProvenanceTest.java  给 fixed 开一条合法路径（子集断言）
5. test/support/BattleFixture.java          + started(...)（纯新增）
6. TriggerInterpreter.java                  删掉那张已过期的 javadoc op 表（19 个 op / 实际 63 个），留一句指针
```

前 5 条是安全网，第 6 条是第一次"减少一个注册点"。跑完 `clean gate`，报用例数、失败数、`gateBaseline` 的新增判定行。

**验收**：`GATE: PASS tests=…… failures=0`，且三个"故意红"验证通过。

---

## 9. 进度（每个提交一行，做完就更新）

| 提交 | 内容 | 证明 |
|---|---|---|
| `ab0dd450` | demo 基线对比（`scenarios/baseline/*.txt` + `captureDemoBaseline`）；`CONTENT-STATUS.md` 补进 test 输入 | 改 `DEFENCE_CONST 200→201`，battle demo 第 32 行从 15244 变 15252 ⇒ 红 |
| `9ea3f9cc` | `EngineBoundaryTest`（禁止新的 `Battle` 持有者）；`ContentProvenanceTest` 不再惩罚已修好的缺口；`BattleFixture.started(...)` | 往 `RelicException` 加一行 `import Battle` ⇒ 红；放一个无 `source` 的规则文件 ⇒ 红 |
| `0ba10cb8` | `TriggerInterpreter` 的 op 表降级为"墓碑"，不再是契约 | javadoc 过 |
| `8f0a15a1` | `OpHandler` / `OpArgs` / `OpContext` / `OpRegistry` + 6 个 op（`GAIN_SKILL_POINT`、`RAISE_SKILL_POINT_CAP`、`ADVANCE`、`DELAY`、`DEPART`、`RETURN`） | `GainSkillPointOp` 的 +1 变异被 6 个套件抓住 |
| `6bc00375` | 已迁移的 op 只走 handler（`CompiledRule.compiledArgs` 装下 load 期解析的形状）；`TriggerDispatchCoverageTest` 改成"恰好一次" | 5 个 demo 与基线逐字节相同；`GATE: PASS tests=3523` |

**下一步（同一棒可以继续）**：按同样形状迁下一批 op，优先挑 validator 里 fall-through 到 `default` 的那几个（`EXTRA_TURN`、`INSERT_ACTION`、`IGNORE_WEAKNESS`、`DISMISS_SUMMON`、`SET_STACKS`、`BOOST_DOT`、`SUMMON_SERVANT`、`RAISE_ENERGY_BANK`、`SPEND_RESOURCE`、`REMOVE_RESOURCE`）——它们今天没有自己的校验，迁移时补上，正好还 R1 的债。

⚠ 迁移期间的纪律：一个 op 不允许同时存在于 switch 和 registry；`TriggerDispatchCoverageTest` 会红。

### 9.1 续：63 个 op 全部有了 handler（同一轮）

| 提交 | 内容 | 证明 |
|---|---|---|
| `55d625d8` | 22 个委托型 op（`MODIFY_ATTR`、`APPLY_BUFF`、`APPLY_DOT`、`DISPL`…）落到 handler | gate 绿；demo 与基线一致 |
| `2d845c33` | 回合序 / 重置族（`EXTRA_TURN`、`INSERT_ACTION`、`IGNORE_WEAKNESS`、`RESET_*`、`APPLY_REGEN`、`SUPER_BREAK`、`BOOST_TOUGHNESS`） | 同上 |
| `02c6a4de` | 资源 / 伤害 / 召唤的一行 op（`RAISE_RESOURCE_*`、`RAISE_ENERGY_BANK`、`DRAW_ENERGY_BANK`、`MODIFY_DAMAGE_TAKEN`、`BOOST_DAMAGE`、`SHARE_DAMAGE`、`DELEGATE_DAMAGE`、`REGISTER_HEAL_BONUS`、`ADD_DAMAGE`、`COMMAND_SUMMON`、`CAST_SKILL`、`SUMMON_SERVANT`） | 同上 |
| `8b5a7c1a` | 能量 / 治疗 / 资源三态 / 倍率（`GAIN_ENERGY`、`RAISE_ENERGY_TO`、`HEAL`、`GAIN/SPEND/REMOVE_RESOURCE`、`RAISE_SKILL_MULTIPLIER`、`RESET_TRIGGER_LIMIT`） | 同上 |
| `01a1c25d` | 最后六个大块（`DAMAGE`、`SHIELD`、`ABSORB_DAMAGE`、`CONSUME_HP`、`SUMMON`、`ADD_ELEMENTAL_WEAKNESS`），**`applyOne` 的 switch 整段删除** | `TriggerDispatchCoverageTest` 的 `arms()` 现在返回空集 |

**现状**：63 / 63 个已接线 op 有 handler；`OpRegistry.HANDLERS` 63 行；`TriggerInterpreter` 少了约 490 行；`applyOne` 只剩"播报 → 解析 op → 查表 → 执行"。`TriggerDispatchCoverageTest` 的语义已经变成"registry 覆盖 `wiredNames()`"。

**阶段 1 剩余**：把 `TriggerValidator.validateOne` 的约 50 个 arm 搬进各自的 `handler.validate`（`OpHandler.validate` 的 default 就是为这段过渡存在的），搬完 `validateOne` 只剩那 169 行公共卫兵。之后进阶段 2（`EffectSpec` → 每 op 的 args record，先引入 `Magnitude` 与 `TargetSpec`）。

### 9.2 验证半区：46 → 18 个 arm

| 提交 | 搬走的 op |
|---|---|
| `d8d74ac8` | `ADD_ELEMENTAL_WEAKNESS`、`RESET_TRIGGER_LIMIT`、`GAIN_ENERGY`、`RAISE_ENERGY_TO`、`HEAL`、`SHIELD`、`RAISE_RESOURCE_CAP`、`RAISE_RESOURCE_MAX`、`RAISE_ENERGY_BANK`、`DRAW_ENERGY_BANK`、资源三态、`RESET_RESOURCE_CAP`、`RESET_RULE_CHANCE` |
| `50f1d635` | `APPLY_BUFF`、`REMOVE_STACK`、`MODIFY_DAMAGE_TAKEN`、`ABSORB_DAMAGE`、`SUPER_BREAK`、`CONSUME_HP`、`BOOST_TOUGHNESS`、`BOOST_DAMAGE`、`TICK_DOT`、`REPLACE_SKILL`、`REMOVE_BUFF` |
| `5ee89e15` | `DISPEL`、`TAUNT`、`REMOVE_STATE`、`APPLY_CONTROL`、`APPLY_DOT`、`EXTEND_BUFF` |

**⚠ 这一轮踩到的坑（值得写进 AGENT.md）**：删掉一个 arm 之后，那个 op 会**掉进 `default`**，而 `default` 不是空的——它是 `requireNoStackArguments`。于是 `APPLY_DOT` 的 `max_stacks`（「最多叠加 5 层」）被通用的叠层拒绝挡下，1108 桑波直接装载失败，19 个用例变红。修法是把 `default` 改成"只对**没有 handler** 的 op 生效"，并把原来靠 `default` 拿到这条检查的 7 个 op（`EXTRA_TURN`、`INSERT_ACTION`、`IGNORE_WEAKNESS`、`DISMISS_SUMMON`、`SET_STACKS`、`BOOST_DOT`、`SUMMON_SERVANT`）在自己的 `validate` 里显式写出来。

> 教训：**删 arm 不是删检查**。`default` 是一条真实规则，只是它没有名字。变红的 19 个用例恰好证明了判据是有效的——它们抓到了一个"看起来纯搬家"的行为改动。

**还剩 18 个 arm**（都是大块或依赖多字段的）：`DAMAGE`、`MODIFY_ATTR`、`MODIFY_RULE`、`RAISE_SKILL_LEVEL`、`RESIST_DEBUFF`、`START_COUNTDOWN`、`APPLY_REGEN`、`REGISTER_HEAL_BONUS`、`ADD_STACK`、`SPEND_STEPS`、`RAISE_SKILL_MULTIPLIER`、`RAISE_SKILL_HITS`、`SHARE_DAMAGE`、`ADD_DAMAGE`、`SUMMON`、`CAST_SKILL`、`COMMAND_SUMMON`、`DELEGATE_DAMAGE`。

### 9.3 验证半区清零：两个 switch 都没了

| 提交 | 内容 |
|---|---|
| `c0c67c8f` | 9 个（`RESIST_DEBUFF`、`START_COUNTDOWN`、`APPLY_REGEN`、`REGISTER_HEAL_BONUS`、`ADD_STACK`、`SPEND_STEPS`、`RAISE_SKILL_*`） |
| `9eaf8085` | 7 个（`MODIFY_RULE`、`SHARE_DAMAGE`、`ADD_DAMAGE`、`SUMMON`、`CAST_SKILL`、`COMMAND_SUMMON`、`DELEGATE_DAMAGE`） |
| `9a5b8925` | 最后两个大块（`DAMAGE` 134 行、`MODIFY_ATTR` 113 行），**`validateOne` 的 switch 整段删除** |

**现状**

| 项 | 重构前 | 现在 |
|---|---|---|
| `applyOne` 的 switch | 63 arm / 466 行 | **已删除** |
| `validateOne` 的 switch | 51 arm / ~1,150 行 | **已删除** |
| `TriggerInterpreter` | 3,130 行 | 2,639 |
| `TriggerValidator` | 3,060 行 | 2,001 |
| op 的注册点 | 7–9 处 | 枚举 1 行 + handler 1 个文件 |

`TriggerValidator` 现在只剩：169 行公共卫兵 + 60 多个 `require*` 共享机器 + 表结构检查。`validateOne` 的结尾是这段：

```java
// No per-op switch. Every wired op validates itself now (its handler's `validate`) ...
if (OpRegistry.find(operator) == null) {
    requireNoStackArguments(effect, op, spec);
}
```

这段是**故意留的保险**：它只在"枚举有、registry 没有"时生效，也就是 `TriggerDispatchCoverageTest` 会先抓到的那种不一致。

**阶段 1 剩余只有 `BattlePort`**（用户已定"先胖后切"）。它不改行为、纯粹是给 `OpContext.battle()` 换类型：一个由 `Battle` 实现的胖接口，把 handler 与 `Battle` 的依赖变成 handler 与接口的依赖，从而 63 个 op 可以脱离一场真实战斗被单测。随后进阶段 2。

### 9.4 阶段 1 收口：`BattlePort`（提交 `acf2d291`）

`engine/port/BattlePort.java`：87 个声明 / 142 行，**不是设计的，是从 `Battle` 里抽出来的**——扫一遍 `trigger/` 对 `battle.*` 的全部调用（74 个名字），把对应签名原样搬进接口。`Battle implements BattlePort`，一个方法体都没改。

于是 `trigger/` 里现在 **0 个文件**再写 `com.laosun.aluminium.Battle`。`EngineBoundaryTest` 的 `KNOWN` 里删掉了 `trigger`（原来是最大的持有者，8 个文件）。

**唯一的口子：`BattlePort.engine()`**。两个调用点要把整场战斗交给 `Skill.execute` / `AttackSequence.execute`，而它们的参数类型是具体的 `Battle`（阶段 5 才轮到它们）。口子放在接口上、`Battle` 实现为 `return this;`，注释写明"这是唯一要删的铸造点，grep `engine()` 能找到所有调用者"。

**⚠ 搬运时踩到的三件事**

1. `TriggerContext` 的 `battle` 字段必须一起换成 `BattlePort`，否则构造器不收 port——但换了之后 `TriggerContext.battle()` 返回 port，所有 `BattlePort battle = ctx.battle();` 的局部变量声明要跟着改（7 处）。
2. `Battle` 的 import 删掉后，javadoc 里的 `{@link Battle}` 会解析失败，而 `-Xwerror` 让它变成编译错误。全部改成 `{@code Battle}`（10 处）——顺手让"javadoc 里不再出现这个类型名"这件事成立。
3. `Battle.PendingCast` 这种嵌套类型一旦不再 import `Battle` 就必须写 `var` 推断。

**阶段 1 完成判据（全部满足）**：63 个 op 各有 handler；两个 switch 已删；`TriggerDispatchCoverageTest` 断言的"恰好一次"成立；`trigger/` 不再命名 `Battle`；`gate` 绿、demo 与基线逐字节相同。

### 9.5 阶段 2 起点：`Magnitude` 与 `TargetSpec`

`EffectSpec` 的 139 个字段里最大的一簇是**数值**（31–44 个：`amount` / `percent` / `percent_from` / `percent_from_skill_param` / `scale` / `value` / `cap_*` …），第二簇是**选择器**（`target` 及其 30 个前缀）。阶段 2 就是把这两簇从 bean 里搬到两个值对象上：

- `Magnitude`：一次解析、运行期求值；**不复制** `TriggerMagnitudes` 的判定树——它是那棵树的新输入。
- `TargetSpec`：一次解析选择器，运行期不再切字符串（现在 `TriggerTargets` 有 3 处 `lastIndexOf`、`TriggerInterpreter` 4 处）。

判据建议：加一条 ratchet——**`parse`/`validate` 之外的文件不得调用 `EffectSpec.getX()`**，按 op 逐个下降，像 `HardcodedSkillRowRatchetTest` 那样只准减。

> 先做 `Magnitude`：44 个字段、63 个 op 里至少 40 个要它；`TargetSpec` 依赖选择器语法，可以晚一步。

### 9.6 阶段 2 第一步：`Magnitude` 接缝（`e9ccbd65`、`629a77fa`）

**做法：接口 + 适配器，不是重写。** `beans/EffectSpec` 实现 `trigger/Magnitude`（31 个 getter，全部由 Lombok 提供，**零行代码改动**），然后 `TriggerMagnitudes` / `NumberProviders` 的参数类型从 `EffectSpec` 换成 `Magnitude`。行为零变化，但"数值层认识哪些字段"第一次有了答案，而且答案是一份可缩短的清单。

**为什么这样切。** 直接重写 `TriggerMagnitudes`（1,300 行、读 31 个字段）会同时动"怎么读"和"拿什么读"，判据分不清谁坏了。适配器让这两件事分开：先换类型，再换实现。

**⚠ 一个真实的泄漏。** `target_max_hp` / `actor_max_hp` 这族 scale 要解析选择器才知道"谁的血上限"，而选择器词汇在 `EffectSpec` 上。所以 `TriggerMagnitudes` 里有**一处**显式 `(EffectSpec) effect` 铸造，注释写明它是等"parsed Magnitude 携带已解析的单位"那天删掉的。这不是"层认识 bean"，是"一处有日期的欠账"。

**守卫 `MagnitudeSeamTest`（3 个用例）**：
- 反射断言 `TriggerMagnitudes` / `NumberProviders` 的**任何非私有方法都不以 `EffectSpec` 为参数**（源码正则会被注释干扰，反射说的正是这个主张）；
- `EffectSpec implements Magnitude`；
- 一个行为锚：通过接口读 `percent` 与读 bean 同值，防止这个套件退化成同义反复。

**第一个真正的 args record**：`IgnoreWeaknessArgs(int casts)`。`IGNORE_WEAKNESS` 的 `amount` 是字面量，现在在 load 期读一次，"未写 = 1 次"的决定也只做一次；`apply` 不再碰 bean。这是后续 62 个 op 的模板。

⚠ **Java 的坑**：`implements OpHandler<XArgs>` 里不能引用本类体内声明的嵌套 record——成员类型的作用域是类**体**，不含头部。所以 args record 必须是独立文件（这正好符合"一个 op 一个 args"）。

### 9.7 阶段 2 推进：5 / 63 个 op 的参数只解析一次（`634e2856`、`6bb22a00`、`45e5c38c`）

| op | args record | 搬走了什么 |
|---|---|---|
| `IGNORE_WEAKNESS` | `(int casts)` | `amount` + "未写 = 1 次"的决定 |
| `RESET_RULE_CHANCE` | `(String rule)` | 被重置的规则 id |
| `START_COUNTDOWN` | `(String statedName, Double speed)` | 名字（没写就按召唤者命名）与速度 |
| `SUMMON` | `(boolean anotherInstance, boolean panelLess, String name, Double speed)` | 三种形态的判定 |
| `RAISE_SKILL_LEVEL` | `(double delta, boolean memosprite, Integer skillId, List<String> skills, String skill)` | 三种槽位写法 + 等级 |

顺手删掉了 `TriggerInterpreter.startCountdown`（转完之后没有调用者），以及它的 javadoc——**`-Werror` 的 `dangling-doc-comments` 会立刻抓到没删干净的那一半**，这个项目为什么要开这条 lint 一目了然。

**`SUMMON` 的一处行为改进**：`panel_less` 却没写名字，原来要到**开火时**才抛 `IllegalStateException`，现在在 `validate` 里就是 `IllegalArgumentException`——错在装载期而不是战斗中途。gate 绿说明 96 个角色文件里没有这种写法。

**守卫 `ArgsMigrationRatchetTest`（2 个用例）**：扫 `*Op.java` 的 `implements OpHandler<...>`，把 `OpArgs`（未迁移）与 `XxxArgs`（已迁移）分开，断言"已迁移的集合只增不减"。它明说不检查 parse 是否**完整**——只记录谁搬了家，完整性归编译器（`apply` 只能拿到 record）。

**下一步**：剩下的 op 分两类。①字段是布尔/枚举/整数的（`REPLACE_SKILL`、`MODIFY_RULE`、`ADD_STACK`、`RAISE_SKILL_HITS`、`SPEND_STEPS`、`REGISTER_HEAL_BONUS`、`RESIST_DEBUFF`）——同 `SUMMON` 的做法，直接搬。②字段是**数值**的（`DAMAGE`、`HEAL`、`SHIELD`、`MODIFY_ATTR`、`GAIN_*`…）——要等 `Magnitude` 从接口变成"编译期解析出来的 record"，否则 args 里只能放回 bean。

### 9.8 阶段 2 推进：11 / 63（`edb9914b`、`ae7bee29`、`eab43f85`）

**关键解锁：args 里放 `Magnitude`，而不是 `double`。** 数值在开火时才确定（谁的面板、几层、事件给了多少），所以 load 期解析不出值；但**解析得出"这是哪种写法"**。`MagnitudeArgs(Magnitude magnitude)` 一个 record 服务了三个 op——因为它们说的都是"一个数"，只是各自问法不同（`ADVANCE`/`DELAY` 要份额，`GAIN_SKILL_POINT` 要整数）。等 `ParsedMagnitude` 落地，这些 record **不用再改**。

| op | args | 备注 |
|---|---|---|
| `ADVANCE` / `DELAY` / `GAIN_SKILL_POINT` | `MagnitudeArgs` | 一个 record 三个 op |
| `HEAL` / `CONSUME_HP` | `MagnitudeArgs` | 走 `grantAmount(magnitude, …)` |
| `SHIELD` | `ShieldArgs(magnitude, turns, addToShield, capScale, capPercent, capAmount)` | 上限的三种写法只判定一次 |

同时把 `scaledAmount` / `grantAmount` / `flatAmount` 的参数从 `EffectSpec` 换成 `Magnitude`，并给接口补了 `getPerTarget()`（它乘的是**数值**，不是选择器）。

**⚠ 一个故意没搬的**：`BOOST_DAMAGE` 除了数值还读 `per_stack`，而叠加是**另一簇**（自己的读者、自己的 `per_stack_cap`）。只搬一半等于"换了类型没换知识"，所以它留在 `OpArgs.NONE`，等 `per_stack` 也被解析。这条判断写进了 ratchet 的注释——**没搬也是一种声明**。

**为什么不用一次改 50 个**：`SHIELD` 与 `HEAL` 的区别不是字段多少，而是**上限/持续时间是否也搬**。每搬一个 op 都要回答"它读的每一簇字段都解析了吗"，答案是否就停手——这正是 `as_attack` 那种"校验了、没人读"的字段教给这个项目的。

### 9.9 阶段 2 推进：15 / 63（`33f09966`、`4dffd467`）

| op | args | 顺带删掉的 helper |
|---|---|---|
| `RAISE_ENERGY_TO` | `MagnitudeArgs` | — |
| `RAISE_SKILL_HITS` | `RaiseSkillHitsArgs(int slot, Magnitude)` | `TriggerInterpreter.raiseSkillHits` |
| `SHARE_DAMAGE` | `MagnitudeArgs` | `TriggerInterpreter.shareDamage` |
| `REGISTER_HEAL_BONUS` | `RegisterHealBonusArgs(Magnitude, boolean scaleByMissingHp)` | `TriggerInterpreter.registerHealBonus` |

**一个反复出现的形状**：凡是"`resolveTargets(battle, effect, ctx)` + 读两三个字段"的 helper，搬进 handler 后就变成"`ctx.targets()` + 读 args"，helper 随之消失。`OpContext.targets()` 就是为这个存在的——它是 `resolveTargets` 的唯一调用者，所以搬完不会有第二份目标解析。

**⚠ 删 helper 时踩的坑（值得写进 AGENT.md）**：我用"从声明行开始找第一行只含 `}` 的行"来定方法结尾，结果第一个匹配到的是 `if (owner == null) { … }` 的闭合括号——删掉了方法前半段，留下后半段在文件里。**编译立刻报错**，但正确的做法是数括号深度而不是找第一个 `}`：

```powershell
$depth += ([regex]::Matches($line,'\{')).Count - ([regex]::Matches($line,'\}')).Count
if ($depth -eq 0 -and $i -gt $declIndex) { break }
```

这类手工行手术在本轮发生了 **1 次**（`raiseSkillHits`），修正方式是删掉残留片段再编译确认。结论：**行手术永远配一次编译**，这不是形式主义。

**下一步**：剩下的 48 个里，`MODIFY_ATTR` / `MODIFY_DAMAGE_TAKEN` / `ADD_DAMAGE` / `GAIN_*` 都在等两样东西——①`per_stack` 那一簇被解析（`BOOST_DAMAGE` 卡在这里）；②`ParsedMagnitude`（把"哪种写法"在 load 期定下来）。这两件事做完，剩下的会成片落地。

### 9.10 阶段 2 推进：20 / 63（`12592f60`、`a2a4d7e2`）

| 提交 | op |
|---|---|
| `12592f60` | `ADD_ELEMENTAL_WEAKNESS(element, turns)`、`ABSORB_DAMAGE(resource, unitAmount, magnitude, payer, turns, permanent)` |
| `a2a4d7e2` | `RAISE_RESOURCE_CAP(resource, unbounded, magnitude)`、`RAISE_RESOURCE_MAX(resource, magnitude)`、`RAISE_ENERGY_BANK(magnitude)`；`DRAW_ENERGY_BANK` 内联（它什么也不读） |

**资源族抽出了一个共享的 `TriggerInterpreter.resourceFor(battle, holder, id, op)`**：两个 RAISE_* 都要"先在本单位找这个资源，找不到再问全队"，而这段逻辑原来抄了两份、错误消息里的 op 名各写各的。搬进 handler 时顺手合成一处，错误消息改成把 op 名当参数传进来。

**⚠ 又一次 helper 删除的教训**：这轮删 4 个 helper，我的"往上找最近一个 `/**`"在其中一个方法没有 javadoc 时会把**上一个方法**的 javadoc 一起圈进去。编译和 `javadoc` 都过了（`-Werror` 只抓悬空注释，不抓"这段注释讲的是别的函数"），是靠 `git diff -U0 | Select-String '^-'` 逐条看删除行才确认只删了该删的。**结论：删注释一定要看 diff，编译器不会替你检查注释的归属。**

**进度对比**：阶段 2 开始时 0/63 → 现在 20/63，`TriggerInterpreter` 少了一批"取 target + 读两三个字段"的 helper。剩下的 43 个主要卡在 `per_stack` 与选择器两簇上。

### 9.11 阶段 2 推进：22 / 63（`ecd441e2`、`c6235e40`）

| 提交 | op | 字段数 |
|---|---|---|
| `ecd441e2` | `MODIFY_RULE` | **10 个**，全是字面量（八种修正写法 + 规则 id + 兜底的 base chance） |
| `c6235e40` | `RESIST_DEBUFF` | 7 个（两种形态：充能盾 vs 职业抗性） |

`MODIFY_RULE` 说明了一件事：**"字段多"不是障碍，"字段属于哪一簇"才是**。它的 10 个字段全是当句的字面量，一次解析完全成立；而 `BOOST_DAMAGE` 只有 3 个字段却卡住，因为它读的那一簇（`per_stack`）还没解析。

`RESIST_DEBUFF` 顺带把 `named(buff, EffectSpec)` 改成 `named(buff, String name)` —— 这个 helper 只在两处被调用，且都在这次被内联的 `resistDebuff` 里。

**⚠ 这一轮变红了，原因值得记住**：

```java
// parse 里：PLACEHOLDER 是 int，getTurns() 是 Integer
permanent ? TriggerEffects.UNBOUNDED_DURATION_PLACEHOLDER : effect.getTurns()
```

`?:` 两边一个是 `int`、一个是 `Integer` ⇒ **整个表达式按 `int` 求值 ⇒ 拆箱 ⇒ 为 null 时 NPE**。原代码把它写在"充能盾提前 return"之后，所以充能盾从不到这行；搬进 `parse` 后它对**所有**效果都执行，于是 1207 驭空（充能盾、没有 `turns`）在**装载期**就崩了，12 个用例变红。

修法：`parse` 只存 `effect.getTurns()` 原值，占位符在 `apply` 的职业抗性分支里替换，并注释写明原因。教训：**把"只在某个分支里合法"的表达式搬进无条件执行的 `parse`，等于给所有分支加了前提**。`gate` 绿之后才提交，所以历史里没有红状态。

### 9.12 阶段 2 推进：28 / 63（`e55d5517`、`13ffd009`）

**helper 的签名变了一次，这是本轮真正的结构变化。** 原来：

```java
static void taunt(BattlePort battle, EffectSpec effect, TriggerContext ctx)   // 三个参数，其中一个就是 bean
```

现在：

```java
static void taunt(OpContext ctx, TauntArgs args)                              // 两个参数，bean 不在其中
```

`OpContext` 已经带着 battle + 触发上下文 + 效果（`targets()` 就在它上面），所以 helper **拿不到 bean 就只能读 args**。这比"handler 不读 bean"更强：连 helper 也读不到。搬完的 6 个：

| op | args |
|---|---|
| `TICK_DOT` | `(String element, Magnitude)` |
| `SUPER_BREAK` | `MagnitudeArgs` |
| `REMOVE_BUFF` | `MagnitudeArgs` |
| `TAUNT` | `(Integer turns)` |
| `DISPEL` | `(String kind, Magnitude)` |
| `BOOST_TOUGHNESS` | `(Magnitude, Integer turns, String perStack, Integer perStackCap)` |

**⚠ PowerShell here-string 与 CRLF**：这一轮有三次 `$raw.Replace(@'…'@, …)` 静默没生效——`Get-Content -Raw` 读出来是 `\r\n`，而 here-string 里是 `\n`。`Replace` 找不到就**什么也不做，也不报错**，我当时还打印了 "patched"。改法是改用 `edit` 工具（它会规范化换行）。教训：**批量文本替换必须验证结果，不能只看"命令跑完了"**。

**下一步**：`APPLY_REGEN`(16) `EXTEND_BUFF`(18) `REMOVE_STACK`(14) `SET_STACKS`(20) `BOOST_DOT`(19) `DISMISS_SUMMON`(11) 都是同形状、helper 很短，可以按这一轮的模板继续。再往后就是 `per_stack` 与选择器两簇，以及 `DAMAGE`/`MODIFY_ATTR`/`MODIFY_DAMAGE_TAKEN` 三个大块。

### 9.13 阶段 2：32 / 63，过半（`104dc183`）

`APPLY_REGEN`、`REMOVE_STATE`、`SET_STACKS`、`BOOST_DOT` 四个按上轮模板搬完，`DISMISS_SUMMON` 内联并删掉 helper（它一个字段都不读，没有 args 可给——**"没有 args"也是一种结论**，已记进 ratchet 的注释风格）。

**剩下的 31 个，按"卡在哪一簇"分类**（这份分类比逐个点名有用）：

| 卡在哪 | op | 说明 |
|---|---|---|
| **生命周期簇**（`turns`/`permanent`/`until`/`ticks_on`） | `APPLY_BUFF`、`APPLY_DOT`、`APPLY_CONTROL`、`EXTEND_BUFF`、`REPLACE_SKILL` | 这簇被 `TriggerLifetimes.withLifetime(buff, effect)` 一次性消费，所以搬 args 就得先把 lifetime 解析出来 |
| **叠加簇**（`per_stack`） | `BOOST_DAMAGE`、`ADD_STACK` | 上轮已记 |
| **选择器簇** | `EXTRA_TURN`、`INSERT_ACTION`、`DEPART`、`RETURN`、`RESET_*`、`RAISE_SKILL_POINT_CAP`、`SUMMON_SERVANT`、`RAISE_SKILL_MULTIPLIER` | 它们读**只有 target**，args 是空的；搬了也不减少耦合，**故意留着** |
| **资源搬运簇** | `GAIN_RESOURCE`、`SPEND_RESOURCE`、`REMOVE_RESOURCE` | ~12 字段（含 `amount_from_previous`/`amount_from_event`/`amount_from_attr`/`amount_cap`/`max_per_round`/`to_max`） |
| **大块** | `DAMAGE`、`MODIFY_ATTR`、`MODIFY_DAMAGE_TAKEN`、`GAIN_ENERGY`、`ADD_DAMAGE`、`CAST_SKILL`、`COMMAND_SUMMON`、`DELEGATE_DAMAGE`、`SPEND_STEPS` | 各自 30–266 行的 helper |

**结论（写给下一个读者）**：阶段 2 剩下的价值集中在**三个簇**（lifetime / per_stack / selector）和**资源搬运**上。把这三件事解析出来，`DAMAGE`/`MODIFY_ATTR` 之外的就都能成片搬完；而 `EXTRA_TURN` 那类"只有 target"的 op，args 是空的，**搬不搬都不影响耦合**——从 ratchet 里能看出它们是"刻意未搬"而不是"漏了"。

### 9.14 阶段 2：38 / 63（`c5c50c26`、`be9eac4a`）

| 提交 | op |
|---|---|
| `c5c50c26` | `EXTEND_BUFF(turns, kind, buff, attribute)`、`REMOVE_STACK(magnitude, buff, attribute)`、`SPEND_STEPS(steps)` |
| `be9eac4a` | `RAISE_SKILL_MULTIPLIER(skill, magnitude, minEnemies, maxEnemies)`、`RAISE_SKILL_POINT_CAP` → `MagnitudeArgs`、`RESET_TRIGGER_LIMIT(rule)` |

**剩下的 25 个分成两类，比例值得记下来**：

- **19 个是真工作**：`DAMAGE`、`MODIFY_ATTR`、`MODIFY_DAMAGE_TAKEN`、`GAIN_ENERGY`、`ADD_DAMAGE`、`CAST_SKILL`、`COMMAND_SUMMON`、`DELEGATE_DAMAGE`、`REPLACE_SKILL`、`APPLY_BUFF`、`APPLY_DOT`、`APPLY_CONTROL`、`ADD_STACK`、`BOOST_DAMAGE`、`GAIN/SPEND/REMOVE_RESOURCE`、`SUMMON_SERVANT`。
- **6 个是"没有 args 可搬"**：`EXTRA_TURN`、`INSERT_ACTION`、`DEPART`、`RETURN`、`DRAW_ENERGY_BANK`（已内联）、`RESET_RESOURCE_CAP`。它们的效果就是"对目标做一件事"，`OpArgs.NONE` 已经是正确答案——**强迫它们有 args 只会造出死字段**，正是 `as_attack` 那种东西。

**因此 63 个里真正可搬的上限是 57 个左右**，38/57 ≈ 三分之二。剩下的 19 个里，`APPLY_BUFF`(13 字段) / `DAMAGE`(基数的七种写法) / `MODIFY_ATTR`(266 行 helper) 三个占了大头，其余多是"资源搬运簇"同形状。

**下一轮**：`APPLY_DOT` + `APPLY_CONTROL` + `APPLY_REGEN`(已完成) 是同一形状（元素/控制 + 回合 + 元素伤害），`APPLY_DOT` 的 helper 只有 29 行；接着是资源搬运三兄弟（一个 args 记录覆盖三者，因为 `requireResourceMovement` 已经把共享校验收成了一处）。

### 9.15 阶段 2：41 / 63（`433b34f0`、`a0585b77`）

`APPLY_DOT`、`APPLY_CONTROL`、`DELEGATE_DAMAGE` 三个搬完；顺手删掉变成死代码的 `TriggerInterpreter.dotStackCap`（`APPLY_DOT` 改从 args 读 `maxStacks` 之后没有读者了）。

**⚠ 一次批量替换打偏了**：把 `requireCharacterOwner(effect, ctx)` 全局换成 `(args, ctx)`，结果 `gainResource` 里那一行也被改（那里根本没有 `args`），编译器在**同一次编译**里同时报出"DelegateDamageArgs 无法转换为 Magnitude"和"找不到符号"两条——后者正是被误改的那行。教训：**批量替换要带上下文**（`\\n            amount =` 之类），否则它命中的是"这个名字的所有出现"，不是"我想改的那一处"。

**`delegateDamage` 的签名变成了 `(OpContext, DelegateDamageArgs)`**：它要读 `skillParamCid`（在 `Magnitude` 上）才能找到 rule owner 的 `Character`，而 `TriggerContext` 已经不再持有 effect，所以只能把 `OpContext` 整个传进去。这是本轮第二次遇到"helper 需要 effect 才能问出 owner"，`OpContext` 因此成了这类 helper 的标准入参。

### 9.16 阶段 2：44 / 63 —— 资源搬运三点一组（`f51d9b38`）

`GAIN_RESOURCE` / `SPEND_RESOURCE` / `REMOVE_RESOURCE` 共用一个 `ResourceMoveArgs`（13 个字段）。这一簇是全项目最难的一处：**"移动多少"有七种写法**（字面量、属性份额、上一发实际移动量、事件给的数量、另一个计数器的值、`to_max`、`spend_all`/`overflow_only`），所以 args 里放的**不是解析出来的数字，而是"哪一种"的旗标**——数值依赖开火现场，写法的判定不依赖。

顺带删掉三个便利重载（`gainResource(effect, ctx, prev)` 等）和 `percentOfOwnCounter`/`isCastParamScale`/`eventShare` 的 `EffectSpec` 参数；`noteElationGiftIfThisIsOne` 也从"读 effect 的 resource"改成"接收 resource 字符串"。

**⚠ 删重载时留下两段悬空 javadoc**：便利方法被删掉后，它的注释还留在原地，紧跟着下一个真方法的注释——**两个 `/**` 挨在一起**，第二个生效、第一个悬空。`-Werror` 的 `dangling-doc-comments` 抓到了（这正是当初开这条 lint 的理由），删掉那段注释即可。教训：**删方法要连同它的注释一起删**，编译器只检查"注释有没有下家"，不检查"下家是不是它本来描述的那个"。

**进度**：44/63，其中 6 个是"没有 args 可搬"（`EXTRA_TURN`/`INSERT_ACTION`/`DEPART`/`RETURN`/`DRAW_ENERGY_BANK`/`RESET_RESOURCE_CAP`），所以**可搬的 57 个里已完成 44 个 = 77%**。

### 9.17 阶段 2：47 / 63（`11a2b2ee`、`63e4f392`）

| 提交 | op | 顺带 |
|---|---|---|
| `11a2b2ee` | `GAIN_ENERGY`、`SUMMON_SERVANT` → `MagnitudeArgs` | 给 `Magnitude` 补 `getFixed()` / `getAmountPercent()`（两者乘的都是**数值**）；`gainEnergy` 改 `(OpContext, MagnitudeArgs)` |
| `63e4f392` | `ADD_DAMAGE` → `AddDamageArgs(magnitude, afterZones, damageType, countedLaughs)` | `parseDamageType` 从"吃 EffectSpec"改成"吃字符串"，三个调用点各自传 `getDamageType()` |

**⚠ 同一种错这一轮犯了两次**：`String.Replace` 是**全局**的，我把 `resolveTargets(battle, effect, ctx)` 换成 `ctx.targets()` 时，`modifyDamageTaken` 里同样的一行也被改了（那里的 `ctx` 是 `TriggerContext`，没有 `targets()`）。第二次是 `parseDamageType` 的签名改了、三个调用点只改了一个。

两次都是**编译立刻抓到**，但都说明一件事：**这个规模的重命名应当用带上下文的 `edit`，而不是 `Replace`**。`Replace` 命中的是"这个名字的所有出现"，不是"我想改的那一处"——而 PowerShell 不会告诉你它改了几处。

**剩下的 10 个真工作**：`DAMAGE`、`MODIFY_ATTR`、`MODIFY_DAMAGE_TAKEN`、`APPLY_BUFF`、`REPLACE_SKILL`、`CAST_SKILL`、`COMMAND_SUMMON`、`ADD_STACK`、`BOOST_DAMAGE`、`BOOST_DOT`(已完成)。其余全是"大 helper + 多簇"，`DAMAGE`(143 处引用) 与 `MODIFY_ATTR`(431 处) 是最后两块。

### 9.18 阶段 2：49 / 63（`58822d2c`）

`REPLACE_SKILL(skill, remove, skillId)` 与 `COMMAND_SUMMON(skill, attribute, magnitude)` 搬完。两者都是"读 skill 名 + 若干字段 + 调 `TriggerEffects`"的形状，`multiplierOf` 早在第 9 轮就已经是 `Magnitude` 参数，所以 COMMAND_SUMMON 只差一层 args。

**剩余 7 个真工作 + 6 个无 args**，按难度排：

| 难度 | op | 卡点 |
|---|---|---|
| 中 | `BOOST_DAMAGE` | 数值 + `per_stack`（叠加簇） |
| 中 | `ADD_STACK` | 叠加簇 + 溢出桶 + 生命周期 |
| 中 | `MODIFY_DAMAGE_TAKEN` | 数值 + `instance`/`damage_type`/元素范围 + 生命周期 |
| 大 | `APPLY_BUFF` | 13 字段（含 `random_of`、`blocks`、`counts_as`、`defers_death`） |
| 大 | `CAST_SKILL` | 123 行 helper |
| **特大** | `DAMAGE` | 基数的**七种写法组合**（这里已经搬了 `ADD_DAMAGE` 的同款判断） |
| **特大** | `MODIFY_ATTR` | 266 行 helper，含 `ratio`/`per_stack`/`cap_*`/`instance` |

**给后续的估计**：剩下的活是"7 个 op × 每个一次仔细读 + 一次重写"，而不是 49 个那样的机械搬运。按本轮的节奏（一个提交 2 个中等 op），大约 4–6 轮能收口。

### 9.19 阶段 2：50 / 63，并给这类 bug 装了守卫（`ea317bf1`、`70709a1e`）

`APPLY_BUFF` 搬完（13 字段 → `ApplyBuffArgs`），顺带把 `stampCountsAs` / `blocksOf` / `attachRolled` 的参数从 bean 换成"名字/字符串/Magnitude"。

**然后同一个 Java 陷阱第三次出现**：

```java
permanent ? TriggerEffects.UNBOUNDED_DURATION_PLACEHOLDER : effect.getTurns()
//          int 常量                                         Integer 字段
// ⇒ 整体按 int 求值 ⇒ 为 null 时拆箱 NPE
```

第一次 `RESIST_DEBUFF`（1207 驭空），第二次 `APPLY_BUFF`，第三次——**这次是守卫自己找出来的**——`ABSORB_DAMAGE`（第 9 轮写的，一直潜伏着）。

**新增守卫 `OpArgsParseIsTotalTest`（2 个用例）**：反射遍历 `trigger/*Op.java`，对每个 handler 调 `parse(new EffectSpec())`——一个"什么都没写"的效果。`parse` 在 `validate` **之前**运行，所以它必须容忍半成品；如果它在这里抛 NPE，作者看到的是引擎崩溃而不是"你没给持续时间"。

- 第一个用例：每个 `parse` 都必须活下来。
- 第二个用例：扫描必须真的找到了 handler（否则第一个用例会**空洞地变绿**）。

> 这条守卫的价值当场兑现：它一上线就找出第三个潜伏实例。**三次都是同一种形状**，而编译器一次都没报——只有"用一个空效果去喂它"才能暴露。

**测试数**：3,528 → **3,530**（+2），套件 920 → 921。

### 9.20 阶段 2：51 / 63（`04ebb32b`）

`ADD_STACK` 搬完（叠加簇：名字、层数、上限、溢出桶、生命周期、别名、roll 概率 → `AddStackArgs` 10 字段）。

**⚠ 这一轮差点把文件改坏，值得写清楚**：替换 `addStack` 时，我的 `old_string` 只写到方法体的**第一段注释**就结束了——于是 `edit` 把新方法插进去、把旧方法的**前半段**换成新的，**后半段（从 `int wanted = ...` 开始）作为"孤儿"留在原地**。文件表面上还在，编译报"语句不应在方法和初始化程序外部"。

修的过程里我又用"数花括号深度"去定位孤儿块的结尾，结果那条语句本身没有花括号，深度一直是 0，立刻 break，删错了范围（829–830），把文件弄得更糟。第二次改成"从 829 行往后找第一行恰好是 `    }`（四个空格缩进的闭合）"才切对。

> 两条教训：①**替换一个方法时，`old_string` 必须覆盖到该方法的最后一个 `}`**，不要停在中间；②**删孤儿代码不要用括号深度**（它起始于语句而不是块），用"下一条方法级闭合括号"或"下一个 javadoc 之前的空行"更可靠。

**剩余 5 个真工作**：`BOOST_DAMAGE`、`MODIFY_DAMAGE_TAKEN`、`CAST_SKILL`、`DAMAGE`、`MODIFY_ATTR`。加上 6 个无 args 的，**51 / 57 = 89%**。

### 9.21 阶段 2：53 / 63（`b7793cd3`、`6a221690`）

| 提交 | op | 顺带 |
|---|---|---|
| `b7793cd3` | `BOOST_DAMAGE` → `MagnitudeArgs` | `Magnitude` 补 `getPerStack()` / `getPerStackCap()`（叠加是**乘数**）；`perStackFactor` / `capPerStack` / `perStackCount` 三个 helper 的入参从 bean 换成 `Magnitude` |
| `6a221690` | `MODIFY_DAMAGE_TAKEN` → `ModifyDamageTakenArgs`（**17 字段**，本项目最大的一条 args） | `parseScopeElement` 改成吃字符串 |

**`MODIFY_DAMAGE_TAKEN` 是本项目最复杂的单个效果**：一个份额、一个生命周期、两个可选范围，外加**五种"什么时候生效"**的写法——`while_state`（看拥有者的状态）、`while_stacks`（看计数器阈值）、`while_holder_state`（看携带者的状态）、`per_stack_live`（每层实时重算）、`coexist`（必须与别的来源并存）。全部是句子的属性，所以全部进 args；只有份额在结算时算。

**⚠ 发现了 `applyOne` 的一条"折叠"快路径**（第 429–442 行）：当 `per_steps` 的阶梯 > 1 且只写了 `percent` 时，它把 N 层折成"一个 N 倍的修正"——因为引擎用**替换**而不是叠加。这条路径直接调 helper，所以本轮得让它改走 handler：

```java
var zone = (ModifyDamageTakenOp) OpRegistry.find(TriggerOperator.MODIFY_DAMAGE_TAKEN);
modifyDamageTaken(new OpContext(battle, effectCtx, folded, 0.0), (ModifyDamageTakenArgs) zone.parse(folded));
```

`applyOne` 里早有一句注释说明了同一条原则："`MODIFY_RULE` 改过数值的 effect 是**另一个** effect，所以 `rule.argsAt()` 不能用于它，要重新 `parse`"——本轮的折叠路径正是同一个道理的第二个例子。

**剩余 3 个**：`CAST_SKILL`(123 行 helper)、`DAMAGE`、`MODIFY_ATTR`(266 行 helper)。**53 / 57 = 93%**。

### 9.22 阶段 2：54 / 63（`0eb0d15a`）

`CAST_SKILL` → `CastSkillArgs(skill, skillId, castTarget, countedLaughs)`。**它只读四个字段，却有 120 行逻辑**——这说明"args 化的价值"和"helper 的长度"没有关系：那一百多行是"找到技能 / 判断能不能交付 / 防自递归 / 交给 `SkillExecutor`"的判断，本来就不该塞进 bean。

签名这一轮第三次变成 `(OpContext, Args)`：`resolveSelector(...)` 需要 effect 才能解析选择器，而 `TriggerContext` 已经不带了。

**⚠ 一次正则替换只改了一半**：我把方法体内的 `ctx.` 批量换成 `opCtx.firing().`，但第 369 行写的是 `resolveTarget(effect, ctx)`——一个**裸 `ctx`**（没有点），正则没命中；第 435 行则被改成了 `opCtx.firing().effect()`（多了个 `.firing()`）。两处都靠编译报"找不到符号"发现。教训：**批量替换只覆盖"带点"的引用，裸变量名要单独查**。

**下一步**（给下一轮）：`DAMAGE` 的核心结算是 `TriggerInterpreter.damage(...)` 的**181 行**，读约 20 个字段（`attacker` / `blast` / `element` / `elation_from` / `damage_type` / `percent_per_ally_state` / `ally_states` / `ordinary` / `stance` / `ignore_weakness` / `crit_rate` / `crit_damage` / `cast_category` / `unboosted` / `parts` / `split` / `per_target` / `times`）。**建议做法**：建 `DamageArgs` 装这 20 个标量与 `parts` 列表，把 `magnitude` 单独作为参数传（因为 `percent`/`scale`/`value` 属于数值层，不属于"这件事怎么结算"），然后用**行范围内的替换表**把 `effect.getX()` 逐条映射成 `args.x()`——不要全局 `Replace`。

### 9.23 阶段 2：55 / 63 —— `DAMAGE` 完成（`247424ca`）

按上一段记下的做法执行：`DamageArgs`（20 字段）+ `magnitude` 旁路，`damage(...)` 的三个重载都改成 `(DamageArgs, Magnitude, BattlePort, TriggerContext, victim, divisor[, blastShare])`，并在 2147–2346 的行范围内用**替换表**（不是全局 `Replace`）把 17 个 `effect.getX()` 映射过去，另外 7 处"把 effect 交给 Magnitude helper"的调用改成传 `magnitude`。`laughFactor` 也顺势从 `(ctx, EffectSpec)` 变成 `(ctx, Double)`。

**一个自我纠正，值得留下**：第一版 `DamageArgs` 我照抄了 `EffectSpec` 的 25 个字段，编译完发现**其中 8 个在结算里一行都没被读**（`damage_param`、`parts`、`amount_from_event`、`true_damage`、`fixed_crit`、`per_target`…）。这正是本项目反复拒绝的 `as_attack` 缺陷——**声明了、校验了、没人读**。于是把记录缩到 20 个"结算真的会用"的字段；`parts` / `amount_from_event` 那些仍然从 `Magnitude` 视图读（基数属于数值层）。

> **判断标准**：一个 record 组件如果没有任何一行代码读它，它就不该在 record 里，哪怕它确实来自那个 JSON。校验器和结算读的东西不一样时，宁可让校验器直接读 bean。

**剩余：只有 `MODIFY_ATTR` 一个**（266 行 helper），加上 7 个 `OpArgs.NONE` 是正确的（`EXTRA_TURN`/`INSERT_ACTION`/`DEPART`/`RETURN`/`DRAW_ENERGY_BANK`/`RESET_RESOURCE_CAP`/`DISMISS_SUMMON`）。**55 / 56 = 98%**。

### 9.24 阶段 2 ③ 完成：56 / 63，其余 7 个"句子里就没有参数"（`291675e53`、`93d99472c`）

`MODIFY_ATTR` 是全项目最大的一个（266 行 helper，26 个字段读取）。做法与 `DAMAGE` 同：`ModifyAttrArgs`（17 字段）+ `Magnitude spec` 旁路。**一处额外处理**：方法体内原本有一个局部变量 `double magnitude`，与参数同名——把**参数**改名成 `spec`（而不是改局部变量），因为映射表里生成的全是 `magnitude.getX()`，批量改成 `spec.getX()` 只动一行正则。

`Magnitude` 这一轮补了三个 getter：`getAmountCap()`、`getPerStackLive()`、`stackCap()`。判断标准始终是"它是不是**数值**的属性"——`per_stack` 是乘数、`amount_cap` 是天花板、`stackCap` 是这个数字能写多高，都在数值层。

**最后一个结构性妥协，写清楚**：`modifyAttr` 的签名是 `(ModifyAttrArgs, Magnitude, BattlePort, TriggerContext, EffectSpec lifetime)`——**第五个参数仍是 bean**，因为 `until` / `ticks_on` 这一簇从来没有被解析（§9.13 记过）。它只被 `withLifetime(...)` 消费一次，是"未完成的簇"，不是"漏掉的字段"。下一个做这块的人应当把 `TriggerLifetimes` 拆成一个真正的 `LifetimeSpec` 值对象。

**ratchet 变成了 closure**（`93d99472c`）：`ArgsMigrationRatchetTest` 不再维护"已迁移清单"（它已经是全部），改成两条断言：

1. 任何 handler 用 `OpHandler<OpArgs>` 都必须出现在 `NO_ARGUMENTS` 里，**每条都写清"这个 op 的句子为什么没有参数"**；
2. `NO_ARGUMENTS` 里的每个名字必须真的还是个 `OpHandler<OpArgs>`——名单不许腐烂。

> 这条从"只增清单"变成"封闭断言"的转变，是这次重构的收尾动作：**它不再记录进度，而是记录例外**。8 个例外，每个都有理由。

### 9.25 阶段 0–2 完成情况

| 目标 | 状态 | 证据 |
|---|---|---|
| ① 安全网：demo 基线对比 | ✅ | `scenarios/baseline/*.txt` + `gate` 里 5 个 demo 逐字节比对 |
| ① 依赖方向守卫 | ✅ | `EngineBoundaryTest`（`ALLOWED = {"", battle, engine, utils}`） |
| ① 修两个反向守卫 | ✅ | `trigger` 从 `KNOWN` 移除；`ContentProvenanceTest` 改为"没有 note 就不许上" |
| ① `BattleFixture.started` | ✅ | 见 `9ea3f9cc` |
| ② 一个 op 一个 handler（63/63） | ✅ | `OpRegistry.HANDLERS` 63 条，静态初始化拒绝重复/未接线 |
| ② 删除 `validateOne`/`applyOne` 的 switch | ✅ | 两个 switch 都没了；`TriggerDispatchCoverageTest` 断言每个 op 恰好被派发一次 |
| ② `BattlePort`（胖接口） | ✅ | 88 个声明，`Battle implements BattlePort` |
| ③ 每 op 一个 args record（56/63） | ✅ | 其余 7 个 `OpArgs.NONE` 由 `NO_ARGUMENTS` 逐条说明 |

**最终数字**：`gate` 绿，3,530 个用例 / 921 个套件（起点 3,520 / 917），5 个 demo 逐字节一致，5 个 scenario，`replay --strict`，javadoc 无警告。`TriggerInterpreter` 3,130 → 2,281 行，`TriggerValidator` 3,060 → 2,000 行。

**没有做的（明确不在阶段 0–2 的范围）**：阶段 3+ 的测试架构重排、状态所有权、事件总线、文档收拢；以及 `LifetimeSpec`（唯一还留在 helper 签名里的 bean 参数，见上）。

