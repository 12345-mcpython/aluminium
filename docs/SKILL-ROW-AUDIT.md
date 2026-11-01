# 技能行冻结值：剩下的都是有理由的（35 条）

> 扫描口径见 `HardcodedSkillRowRatchetTest`。这张表把**每一条没改的原因**写下来，
> 这样「35」这个数字就不是待办量，而是**已判定的清单**。

## 布洛妮娅：CastTargetTest 不启动战斗（第 28 条）（4 条）

| 角色 | 规则 | 字段 | 值 | 槽 | 列 |
|---|---|---|---|---|---|
| 1101 | `skill_effect_2` | MODIFY_ATTR.percent | 0.66 | 战技 | #1 |
| 1101 | `ult_party_attack` | MODIFY_ATTR.percent | 0.55 | 终结技 | #1 |
| 1101 | `ult_party_crit_damage` | MODIFY_ATTR.percent | 0.16 | 终结技 | #2 |
| 1101 | `eidolon2_speed_on_skill_target` | MODIFY_ATTR.percent | 0.3 | 天赋 | #1 |

## 被 MODIFY_RULE 修正（第 27 条：乘 vs 加）（4 条）

| 角色 | 规则 | 字段 | 值 | 槽 | 列 |
|---|---|---|---|---|---|
| 1108 | `talent_windtorn` | APPLY_DOT.percent | 0.52 | 天赋 | #2 |
| 1215 | `ult_blessing` | MODIFY_ATTR.percent | 0.2 | 终结技 | #3 |
| 1215 | `ult_blessing` | MODIFY_ATTR.percent | 0.6 | 终结技 | #1 |
| 1218 | `talent_ashen_roast_and_burn` | APPLY_DOT.percent | 1.8 | 天赋 | #6 |

## 保留字面值：星魂 6 的修正语义（第 27 条，1 条）

| 角色 | 规则 | 字段 | 值 | 槽 | 列 |
|---|---|---|---|---|---|
| 1109 | `talent_followup_against_burning` | DAMAGE.percent | 1 | 天赋 | #1 |

> 这一条不是「未分类」：它的星魂 6 「对灼烧目标伤害提高 20%」是 `MODIFY_RULE effect_percent_scale`，对字面 `percent` 是**乘**、对行份额是**加** ⇒ 改读行会让星魂 6 从 ×1.2 变成 +0.2（实测 1.25 vs 1.2）。同类的 1108 `talent_windtorn` 已经改回去了。

## 巧合同值：source 没提该槽位（第 26 条 B 段）（16 条）

| 角色 | 规则 | 字段 | 值 | 槽 | 列 |
|---|---|---|---|---|---|
| 1202 | `ult_energy_and_boost` | MODIFY_ATTR.percent | 0.5 | 战技 | #2 |
| 1205 | `technique_opening_aoe` | DAMAGE.percent | 0.4 | 战技 | #4 |
| 1215 | `eidolon2_skill` | MODIFY_ATTR.percent | 0.2 | 终结技 | #3 |
| 1221 | `talent_counter_on_being_hit` | DAMAGE.percent | 1.2 | 战技 | #1 |
| 1301 | `ult_tipsy` | MODIFY_DAMAGE_TAKEN.percent | 0.12 | 天赋 | #1 |
| 1301 | `battle_start` | MODIFY_DAMAGE_TAKEN.percent | 0.12 | 天赋 | #1 |
| 1309 | `ult_zone_defence_ignore` | MODIFY_ATTR.percent | 0.2 | 天赋 | #1 |
| 1317 | `talent_break_damage_taken` | MODIFY_DAMAGE_TAKEN.percent | 0.5 | 天赋 | #5 |
| 1404 | `talent_enters_bloodfeud_at_a_hundred` | MODIFY_ATTR.percent | 0.5 | 战技 | #2 |
| 1404 | `bloodfeud_survives_a_lethal_blow_and_ends` | HEAL.percent | 0.5 | 战技 | #2 |
| 1405 | `eidolon4_attack` | MODIFY_ATTR.percent | 0.3 | 天赋 | #1 |
| 1409 | `technique_heals_and_raises_max_hp` | HEAL.percent | 0.3 | 终结技 | #3 |
| 1409 | `technique_heals_and_raises_max_hp` | HEAL.amount | 600 | 终结技 | #4 |
| 1413 | `talent_memosprite_damage_enemy_count_2` | BOOST_DAMAGE.percent | 0.3 | 终结技 | #4 |
| 1501 | `technique_opening_aoe_and_skill_points` | DAMAGE.percent | 0.5 | 终结技 | #2 |
| 1503 | `eidolon1_raises_elation_at_4_elation_allies` | MODIFY_ATTR.percent | 0.6 | 终结技 | #2 |

## 夹具不启动战斗（第 28 条）（10 条）

| 角色 | 规则 | 字段 | 值 | 槽 | 列 |
|---|---|---|---|---|---|
| 1303 | `ult_zone_all_type_res_pen` | MODIFY_ATTR.percent | 0.25 | 终结技 | #1 |
| 1314 | `trace_reverse_repo_one_goods` | MODIFY_ATTR.percent | 0.024 | 天赋 | #1 |
| 1314 | `technique_fifteen_goods` | MODIFY_ATTR.percent | 0.024 | 天赋 | #1 |
| 1314 | `collector_attack_deals_extra` | DAMAGE.percent | 0.25 | 战技 | #3 |
| 1415 | `zone_true_damage_rider` | DAMAGE.percent | 0.24 | 战技 | #1 |
| 1506 | `p1506_talent_hidden_becomes_crit` | MODIFY_ATTR.percent | 0.004 | 天赋 | #4 |
| 8001 | `talent_atk_on_break` | MODIFY_ATTR.percent | 0.2 | 天赋 | #1 |
| 8001 | `eidolon6_talent_also_fires_on_kill` | MODIFY_ATTR.percent | 0.2 | 天赋 | #1 |
| 8002 | `talent_atk_on_break` | MODIFY_ATTR.percent | 0.2 | 天赋 | #1 |
| 8002 | `eidolon6_talent_also_fires_on_kill` | MODIFY_ATTR.percent | 0.2 | 天赋 | #1 |

