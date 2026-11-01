# 修饰冲突筛查表（常驻 × 临时，同属性同目标）

> 引擎按「**属性 + 改法 + 方向**」认修饰的身份（`StatModifierBuff.isSameKind`），所以**同一属性、同一目标**上的两条规则只会剩下**一条**。
> 这张表把「一个文件里同时有**常驻**修饰与**临时**修饰、且属性与目标都相同」的地方**扫出来**，作为**候选清单**，不是判决书。

## 怎么用

1. 行内已经按 `属性 -> 目标` 分组，所以同一行的两条确实打在同一批单位上。
2. 再问一句：**这两条会同时成立吗**（星魂门槛、事件、状态前提）？同时成立才是真的互相替换。
3. 确认是冲突后：把其中一条换成别的属性/槽位，或把两条合成一条（像 1014 的星魂 2 把 +7% 折进 0.21）。

## 已处理

- **1203 罗刹（2026-11-01）**：天赋规则 `talent_zone_opens_at_two_stacks` 里带着一条 `ATTACK +20%（2 回合、min_eidolon 1）`，与 `eidolon1_attack_while_the_zone_is_up`（常驻、结界期间）**是同一条星魂 1 条款写了两遍** —— 天赋正文只说「结界展开 + 治疗」，没有攻击力加成。已删掉天赋里的那一条，保留常驻版本（更贴合「结界生效时」）。
- **1203 罗刹（第二条，2026-11-01）**：`ALL_DAMAGE_TYPE_BOOST -12%（2 回合、min_eidolon 4）` 同样写在**天赋**里，而它其实是**星魂 4**「结界生效时使敌方陷入虚弱，造成的伤害降低 12%」—— 已删（星魂 4 另有常驻规则与 `APPLY_BUFF 虚弱`）。
- **1408（待查，2026-11-01）**：`CRIT_ATTACK -> self` 上有 **6 条常驻**规则 `world_ode_fire_seed_crit_step_1..6`（各 +6%，门槛 `self_resource:火种 >= 13..18`），它们**同属性同目标同方向** ⇒ 引擎只会留下一条，理论上「火种 18 时应为 6 × 6% = 36%」会变成 **6%**。⚠ 两个探针的结果：① 手动发 `ULT_CAST` 后 `CRIT_ATTACK` **没有任何变化**（连 `ult_transformation` 的 +50% 也没出现）；② 同一时刻**身上多出 4 个 `变身` buff** ⇒ 规则**确实触发了**，但**暴击伤害没有动**。⇒ 这不是「没触发」，而是「触发了但数值没落到 `CRIT_ATTACK` 上」—— 下一步要查的是这几条 `MODIFY_ATTR CRIT_ATTACK` 的**落点**（是落在别的属性、还是被 `变身` 状态覆盖）。**不下结论**，只登记现象。
- **1415 昔涟（2026-11-01，**条件性**，暂不可达）**：天赋「昔涟在场时，我方全体目标造成的伤害提高 **20%**」（常驻）与行迹「三相的因果：速度 ≥ **180** 时…提高 **20%**」（1 回合）是**两条独立条款**，理论上应叠加到 40%；但引擎的修饰身份让第二条**替换**第一条 ⇒ 速度到 180 之后的一回合只有 20%，而那条 1 回合的到期后**连天赋的 20% 也没了**。实测：她的基础速度 **110**，探针里 boost 一直是 **0.2**（行迹从未触发）⇒ 目前**不可达**，但要靠装备/队友把速度堆到 180 就会暴露。修法与 1014 不同（这里是**应该叠加**），需要「同一属性上多条修饰并存」的能力（`AGENT.md` 第 43 条）。
- **1014 Saber 星之冠冕**：`CRIT_ATTACK` 上「战技 +50%（2 回合）」与「每点【炉心共鸣】+4%（实时）」实测总增量 0.46 而非 0.54；最终保留 +50%（`AGENT.md` 第 43 条）。

## 候选清单

| 文件 | 属性 -> 目标 | 常驻修饰 | 临时修饰 |
| --- | --- | --- | --- |
| 1014.json | `RESISTANCE_REDUCTION -> self` | `eidolon4_wind_resistance_penetration` | `eidolon6_ult_resistance_penetration`（1 回合） |
| 1203.json | `ALL_DAMAGE_TYPE_BOOST -> all_enemies` | `eidolon4_weaken_while_the_zone_is_up` | `talent_zone_opens_at_two_stacks`（2 回合） |
| 1203.json | `ATTACK -> all_allies` | `eidolon1_attack_while_the_zone_is_up` | `technique_triggers_talent`（2 回合） |
| 1408.json | `CRIT_ATTACK -> self` | `world_ode_fire_seed_crit_step_1`<br>`world_ode_fire_seed_crit_step_2`<br>`world_ode_fire_seed_crit_step_3`<br>`world_ode_fire_seed_crit_step_4`<br>`world_ode_fire_seed_crit_step_5`<br>`world_ode_fire_seed_crit_step_6` | `ult_transformation`（3 回合）<br>`talent_crit_damage_when_ally_targets_her`（3 回合） |
| 1415.json | `ALL_DAMAGE_TYPE_BOOST -> all_allies` | `talent_party_damage` | `trace_party_damage_at_speed_180`（1 回合） |
