# 修饰冲突筛查表（常驻 × 临时，同属性同目标）

> 引擎按「**属性 + 改法 + 方向**」认修饰的身份（`StatModifierBuff.isSameKind`），所以**同一属性、同一目标**上的两条规则只会剩下**一条**。
> 这张表是把「一个文件里同时有**常驻**修饰与**临时**修饰、且属性与目标都相同」的地方**扫出来**，作为**候选清单**，不是判决书。

## 怎么用

1. 逐行看 `target`：表里已经按 `属性 -> 目标` 分组，所以同一行的两条确实打在同一批单位上。
2. 再问一句：**这两条会同时成立吗**（星魂门槛、事件、状态前提）？同时成立才是真的互相替换。
3. 确认是冲突后，二选一：把其中一条换成**别的属性/槽位**，或者把两条合成**一条**（像 1014 的星魂 2 把 +7% 折进 0.21 那样）。

## 已知的真实案例

- **1014 Saber 星之冠冕**：`CRIT_ATTACK` 上「施放战技 +50%（2 回合）」与「每点【炉心共鸣】+4%（实时）」—— 实测总增量 **0.46** 而不是 0.54，最终保留 +50%（见 `AGENT.md` 第 43 条）。

## 候选清单

| 文件 | 属性 -> 目标 | 常驻修饰 | 临时修饰 |
| --- | --- | --- | --- |
| 1014.json | `RESISTANCE_REDUCTION -> self` | `eidolon4_wind_resistance_penetration` | `eidolon6_ult_resistance_penetration`（1 回合） |
| 1203.json | `ATTACK -> all_allies` | `eidolon1_attack_while_the_zone_is_up` | `talent_zone_opens_at_two_stacks`（2 回合）<br>`technique_triggers_talent`（2 回合） |
| 1203.json | `ALL_DAMAGE_TYPE_BOOST -> all_enemies` | `eidolon4_weaken_while_the_zone_is_up` | `talent_zone_opens_at_two_stacks`（2 回合） |
| 1408.json | `CRIT_ATTACK -> self` | `world_ode_fire_seed_crit_step_1`<br>`world_ode_fire_seed_crit_step_2`<br>`world_ode_fire_seed_crit_step_3`<br>`world_ode_fire_seed_crit_step_4`<br>`world_ode_fire_seed_crit_step_5`<br>`world_ode_fire_seed_crit_step_6` | `ult_transformation`（3 回合）<br>`talent_crit_damage_when_ally_targets_her`（3 回合） |
| 1415.json | `ALL_DAMAGE_TYPE_BOOST -> all_allies` | `talent_party_damage` | `trace_party_damage_at_speed_180`（1 回合） |
