# 内容实现情况（角色 / 光锥 / 遗器）

> **这是一份带日期的快照，不是台账。** `AGENT.md` §2.4 说得很清楚：状态数字会腐烂，**权威是代码**。
> 本文的每个数字都能用下面这条命令重算；改完内容请重跑它，不要手改表。
> 生成日期：2026-10-18（内容一改就重跑，别手改）。

```powershell
python C:\Users\laosu\Downloads\aluminium-baseline\content_status.py
```

## 怎么判定

- **完全**：内容文件里**没有**任何 `未写 / 仍未 / 未核 / 待核 / 未实现 / 登记 / 近似 / 待定` 标记（必要条件，**不等于** kit 写完）。
- **有登记**：文件里有规则，但至少一条规则的 `source` / `note` 还留着上面那些词 —— ⚠ 这是**上界**：注记可能是在**引述**已经修好的缺口。
- **无文件 / 零规则**：连内容都没有；或文件在但一条规则都没有。
- 角色表另外并列 **规则数 / 技能槽数** —— 只留标记会漏掉「静默的半成品」（规则数远少于 kit 从句数、却没写任何登记）。

## 汇总

| 类 | 数据里有的 | 完全 | 有登记 | 零规则 | 无文件 |
|---|---|---|---|---|---|
| 角色 | 98 | **9** | 85 | 0 | 4 |
| 光锥 | 170 | **110** | 57 | 0 | 3 |
| 遗器套装 | 62 | **50** | 11 | 0 | 1 |

## 角色（全部）

| id | 名称 | 元素 · 命途 | 星级 | 规则数 | 技能槽数 | 状态 | 还挂着的登记 |
|---|---|---|---|---|---|---|---|---|
| 1101 | 布洛妮娅 | 风 · 同谐 | 5 | 7 | 6 | 完全 | — |
| 1103 | 希露瓦 | 雷 · 智识 | 4 | 11 | 6 | 完全 | — |
| 1105 | 娜塔莎 | 物理 · 丰饶 | 4 | 11 | 6 | 完全 | — |
| 1210 | 桂乃芬 | 火 · 虚无 | 4 | 2 | 6 | 完全 | — |
| 1211 | 白露 | 雷 · 丰饶 | 5 | 4 | 6 | 完全 | — |
| 1215 | 寒鸦 | 物理 · 同谐 | 4 | 17 | 6 | 完全 | — |
| 1313 | 星期日 | 虚数 · 同谐 | 5 | 5 | 6 | 完全 | — |
| 1321 | 大丽花 | 火 · 虚无 | 5 | 5 | 6 | 完全 | — |
| 8006 | 开拓者 | 虚数 · 同谐 | 5 | 10 | 6 | 完全 | — |
| 1001 | 三月七 | 冰 · 存护 | 4 | 16 | 6 | 有登记 | (无 id) |
| 1002 | 丹恒 | 风 · 巡猎 | 4 | 15 | 6 | 有登记 | talent_wind_pen、level_convention、technique_opening_attack… |
| 1003 | 姬子 | 火 · 智识 | 5 | 9 | 6 | 有登记 | eidolon6_two_extra_random_hits、(无 id)、technique_incomplete_combustion |
| 1004 | 瓦尔特 | 虚数 · 虚无 | 5 | 3 | 6 | 有登记 | ult_imprison、skill_speed_down |
| 1005 | 卡芙卡 | 雷 · 虚无 | 5 | 5 | 6 | 有登记 | ult_shock_and_tick、level_convention、trace_shock_lasts_one_more_turn |
| 1006 | 银狼 | 量子 · 虚无 | 5 | 4 | 6 | 有登记 | ult_defence_down、level_convention、skill_all_type_res_down… |
| 1008 | 阿兰 | 雷 · 毁灭 | 4 | 5 | 6 | 有登记 | level_convention、e4_survives_a_lethal_blow_at_a_quarter |
| 1009 | 艾丝妲 | 火 · 同谐 | 4 | 4 | 6 | 有登记 | level_convention、skill_extra_hits_random |
| 1013 | 黑塔 | 冰 · 智识 | 4 | 5 | 6 | 有登记 | level_convention |
| 1102 | 希儿 | 量子 · 巡猎 | 5 | 3 | 6 | 有登记 | trace_low_hp_aggro_down |
| 1104 | 杰帕德 | 冰 · 存护 | 5 | 11 | 6 | 有登记 | trace_steadfast_aggro、(无 id)、eidolon4_party_resistance… |
| 1106 | 佩拉 | 冰 · 虚无 | 4 | 7 | 6 | 有登记 | (无 id)、technique_defence_down |
| 1107 | 克拉拉 | 物理 · 毁灭 | 5 | 5 | 6 | 有登记 | technique_taunt_aggro |
| 1108 | 桑博 | 风 · 虚无 | 4 | 3 | 6 | 有登记 | (无 id) |
| 1109 | 虎克 | 火 · 毁灭 | 4 | 10 | 7 | 有登记 | (无 id)、eidolon3_levels |
| 1110 | 玲可 | 量子 · 丰饶 | 4 | 5 | 6 | 有登记 | skill_survival_response、talent_regen_after_skill、talent_regen_after_ult |
| 1111 | 卢卡 | 物理 · 虚无 | 4 | 10 | 7 | 有登记 | ult_coup、(无 id) |
| 1112 | 托帕&账账 | 火 · 巡猎 | 5 | 3 | 6 | 有登记 | skill_proof_of_debt |
| 1201 | 青雀 | 量子 · 智识 | 4 | 5 | 7 | 有登记 | skill_damage_up_stacks、level_convention |
| 1202 | 停云 | 雷 · 同谐 | 4 | 6 | 6 | 有登记 | skill_benediction、level_convention |
| 1203 | 罗刹 | 虚数 · 丰饶 | 5 | 15 | 6 | 有登记 | skill_abyss_flower、level_convention、skill_trigger_on_crossing… |
| 1204 | 景元 | 雷 · 智识 | 5 | 2 | 6 | 有登记 | trace_dispatch_crit_chance |
| 1205 | 刃 | 风 · 毁灭 | 5 | 5 | 7 | 有登记 | skill_hellscape_state、technique_opening_aoe、talent_charge_on_hit… |
| 1206 | 素裳 | 物理 · 巡猎 | 4 | 22 | 6 | 有登记 | talent_speed_on_break、level_convention、trace_low_hp_aggro_down… |
| 1207 | 驭空 | 虚数 · 同谐 | 4 | 15 | 6 | 有登记 | eidolon2_energy_on_ally_full |
| 1208 | 符玄 | 量子 · 存护 | 5 | 5 | 6 | 有登记 | talent_misfortune_avoidance、skill_knowledge_state_and_modifiers、level_convention |
| 1209 | 彦卿 | 冰 · 巡猎 | 5 | 7 | 6 | 有登记 | skill_soulsteel_sync_and_its_modifiers、talent_chance_followup、level_convention… |
| 1212 | 镜流 | 冰 · 毁灭 | 5 | 6 | 7 | 有登记 | skill_syzygy_stack、ult_syzygy_stack、talent_transmigration_at_two_stacks… |
| 1213 | 丹恒•饮月 | 虚数 · 毁灭 | 5 | 2 | 10 | 有登记 | talent_righteous_heart、level_convention |
| 1214 | 雪衣 | 量子 · 毁灭 | 4 | 3 | 6 | 有登记 | level_convention |
| 1217 | 藿藿 | 风 · 丰饶 | 5 | 7 | 6 | 有登记 | ult_tail、e2_saves_an_ally_with_the_talisman |
| 1218 | 椒丘 | 火 · 虚无 | 5 | 7 | 6 | 有登记 | talent_ashen_roast_and_burn、level_convention、talent_res_down_per_ashen_roast… |
| 1220 | 飞霄 | 风 · 巡猎 | 5 | 7 | 9 | 有登记 | talent_followup_on_teammate_attack、skill_repeats_the_talent_followup、level_convention… |
| 1221 | 云璃 | 物理 · 毁灭 | 5 | 3 | 6 | 有登记 | skill_heal、talent_counter_on_being_hit、level_convention |
| 1222 | 灵砂 | 火 · 丰饶 | 5 | 5 | 6 | 有登记 | skill_party_heal、ult_befog_break_damage_taken |
| 1223 | 貊泽 | 雷 · 巡猎 | 4 | 9 | 6 | 有登记 | skill_marks_prey、talent_additional_damage_on_prey、ult_triggers_talent_followup… |
| 1224 | 三月七 | 虚数 · 巡猎 | 4 | 6 | 7 | 有登记 | skill_shifu_and_speed、talent_charge_on_shifu_attack、talent_charge_on_shifu_ult… |
| 1225 | 忘归人 | 火 · 虚无 | 5 | 6 | 7 | 有登记 | skill_marks_foxian_prayer、skill_torrid_scorch_on_self、skill_break_effect_for_marked_ally… |
| 1301 | 加拉赫 | 火 · 丰饶 | 4 | 7 | 7 | 有登记 | ult_tipsy、(无 id) |
| 1302 | 银枝 | 物理 · 智识 | 5 | 11 | 7 | 有登记 | (无 id) |
| 1303 | 阮•梅 | 冰 · 同谐 | 5 | 6 | 6 | 有登记 | talent_speed_for_teammates、skill_overtone_on_self、level_convention… |
| 1304 | 砂金 | 虚数 · 存护 | 5 | 4 | 6 | 有登记 | level_convention、trace_hot_hand_opening_shield、ult_unnerved_state |
| 1305 | 真理医生 | 虚数 · 巡猎 | 5 | 6 | 6 | 有登记 | talent_chance_followup、ult_marks_wisemans_folly、ult_reaction_followup… |
| 1306 | 花火 | 量子 · 同谐 | 5 | 9 | 6 | 有登记 | skill_crit_damage_share_and_advance、ult_skill_points_and_cipher、talent_party_damage_on_spend… |
| 1307 | 黑天鹅 | 风 · 虚无 | 5 | 3 | 6 | 有登记 | skill_arcana_stack、skill_defence_down、level_convention |
| 1308 | 黄泉 | 雷 · 虚无 | 5 | 3 | 10 | 有登记 | level_convention |
| 1309 | 知更鸟 | 物理 · 同谐 | 5 | 11 | 6 | 有登记 | (无 id)、technique_field_recovers_five_at_each_wave |
| 1310 | 流萤 | 火 · 毁灭 | 5 | 6 | 8 | 有登记 | skill_action_advance、ult_action_advance、level_convention… |
| 1312 | 米沙 | 冰 · 毁灭 | 4 | 2 | 6 | 有登记 | talent_energy_on_skill_point_spent、level_convention |
| 1314 | 翡翠 | 量子 · 智识 | 5 | 4 | 6 | 有登记 | trace_reverse_repo_one_goods、technique_fifteen_goods、eidolon2_crit_chance_at_fifteen_goods |
| 1315 | 波提欧 | 物理 · 巡猎 | 5 | 6 | 7 | 有登记 | skill_standoff_on_both、standoff_stack_on_kill、standoff_stack_on_break… |
| 1317 | 乱破 | 虚数 · 智识 | 5 | 12 | 14 | 有登记 | talent_charge_on_break、level_convention |
| 1401 | 大黑塔 | 冰 · 智识 | 5 | 3 | 7 | 有登记 | technique_opening_attack、ult_attack_advance_and_inspiration、level_convention |
| 1402 | 阿格莱雅 | 雷 · 记忆 | 5 | 11 | 8 | 有登记 | (无 id)、talent_fissure_thread_applies_state、memosprite_ode_of_romance_marks_aglaea… |
| 1403 | 缇宝 | 量子 · 同谐 | 5 | 13 | 6 | 有登记 | trace_energy_per_target、talent_followup_on_other_ult、skill_numinosity… |
| 1404 | 万敌 | 虚数 · 毁灭 | 5 | 13 | 8 | 有登记 | ult_heal_charge_and_taunt、level_convention、turn_start_autocasts_skill… |
| 1405 | 那刻夏 | 风 · 智识 | 5 | 8 | 6 | 有登记 | eidolon1_defence_down_on_skill、level_convention、talent_random_weakness_on_hit… |
| 1406 | 赛飞儿 | 量子 · 虚无 | 5 | 12 | 6 | 有登记 | skill_weakness_and_attack、talent_reaction_followup、technique_opening_aoe… |
| 1407 | 遐蝶 | 量子 · 记忆 | 5 | 12 | 7 | 有登记 | talent_damage_up_on_hp_loss、level_convention、mooncocoon_holds_the_blow… |
| 1408 | 白厄 | 物理 · 毁灭 | 5 | 28 | 10 | 有登记 | technique_energy_and_skill_point、level_convention、ult_transformation… |
| 1409 | 风堇 | 风 · 记忆 | 5 | 36 | 6 | 有登记 | eidolon_two_speed_on_hp_loss、eidolon_six_gives_the_party_all_type_resistance_penetration、tally_counts_hyacines_own_healing… |
| 1410 | 海瑟音 | 物理 · 虚无 | 5 | 21 | 6 | 有登记 | skill_damage_taken_up、level_convention、ult_zone_opens_and_lowers… |
| 1412 | 刻律德菈 | 风 · 同谐 | 5 | 15 | 6 | 有登记 | skill_grants_military_merit、talent_attack_share_on_marked_ally、talent_charge_from_marked_ally… |
| 1413 | 长夜月 | 冰 · 记忆 | 5 | 21 | 7 | 有登记 | skill_consumes_hp、talent_hp_cost_raises_her_crit_damage、(无 id)… |
| 1414 | 丹恒•腾荒 | 物理 · 存护 | 5 | 11 | 6 | 有登记 | skill_bondmate_and_shield、ult_party_shield、technique_grants_bondmate… |
| 1415 | 昔涟 | 冰 · 记忆 | 5 | 26 | 9 | 有登记 | true_self_counts_a_new_allies_recollection_first、true_self_marks_the_allies_that_gave_one、skill_recollection… |
| 1501 | 火花 | 火 · 欢愉 | 5 | 3 | 10 | 有登记 | technique_opening_aoe_and_skill_points、level_convention、aha_end_extra_turn |
| 1502 | 爻光 | 物理 · 欢愉 | 5 | 1 | 7 | 有登记 | p1502_ult_penetration |
| 1503 | 真珠 | 冰 · 欢愉 | 5 | 56 | 9 | 有登记 | technique_grants_the_opening_counter、eidolon1_saves_an_ally_twice、trace_gains_on_an_ally_turn_start… |
| 1504 | 不死途 | 雷 · 巡猎 | 5 | 9 | 6 | 有登记 | talent_reaction_to_bait、skill_marks_the_bait、eidolon1_enemies_take_more… |
| 1505 | 绯英 | 物理 · 欢愉 | 5 | 10 | 7 | 有登记 | p1505_elation_value、p1505_technique_gift、p1505_skill_elation_rider… |
| 1506 | 银狼LV.<unbreak>999</unbreak> | 虚数 · 欢愉 | 5 | 27 | 12 | 有登记 | p1506_ult_advance、p1506_ult_enters_godmode、p1506_e3_raises_skill_levels… |
| 1507 | 千冶•刃 | 火 · 虚无 | 5 | 4 | 9 | 有登记 | ult_zone_and_thousand_forgings、zone_ends_takes_thousand_forgings_off、eidolon1_zone_enemy_res_down |
| 1510 | 姬子•启行 | 火 · 智识 | 5 | 2 | 12 | 有登记 | skill_navigators_semaphore、level_convention |
| 1512 | 知更鸟•晴歌 | 风 · 记忆 | 5 | 9 | 6 | 有登记 | skill_summon_and_vibes、fever_aura_ignores_defence_by_vibes、fever_boosts_her_and_her_memosprites_damage… |
| 1513 | 砂金•戏浪 | 量子 · 欢愉 | 5 | 9 | 8 | 有登记 | talent_fervor_on_teammate_attack、skill_fervor、ult_fervor_and_speed… |
| 8001 | 开拓者 | 物理 · 毁灭 | 5 | 5 | 8 | 有登记 | level_convention、technique_party_heal、trace_tough_defence_per_stack |
| 8002 | 开拓者 | 物理 · 毁灭 | 5 | 5 | 8 | 有登记 | level_convention、trace_tough_defence_per_stack |
| 8003 | 开拓者 | 火 · 存护 | 5 | 7 | 7 | 有登记 | talent_magma_will、talent_shield_on_basic、skill_reduction_and_taunt… |
| 8004 | 开拓者 | 火 · 存护 | 5 | 7 | 7 | 有登记 | talent_magma_will、talent_shield_on_basic、skill_reduction_and_taunt… |
| 8005 | 开拓者 | 虚数 · 同谐 | 5 | 6 | 6 | 有登记 | ult_dance、technique_breaking_effect、level_convention… |
| 8007 | 开拓者 | 冰 · 记忆 | 5 | 7 | 8 | 有登记 | skill_summon_mem、ult_summon_mem、level_convention |
| 8008 | 开拓者 | 冰 · 记忆 | 5 | 3 | 8 | 有登记 | skill_summon_mem、ult_summon_mem、level_convention |
| 8009 | 开拓者 | 雷 · 欢愉 | 5 | 6 | 7 | 有登记 | ult_crit_damage_for_the_chosen_ally、talent_energy_after_attack、level_convention… |
| 8010 | 开拓者 | 雷 · 欢愉 | 5 | 6 | 7 | 有登记 | ult_crit_damage_for_the_chosen_ally、talent_energy_after_attack、level_convention… |
| 1014 | Saber | 风 · 毁灭 | 5 | 0 | 7 | 无文件 | — |
| 1015 | Archer | 量子 · 巡猎 | 5 | 0 | 7 | 无文件 | — |
| 1508 | 远坂凛 | 量子 · 智识 | 5 | 0 | 8 | 无文件 | — |
| 1509 | 吉尔伽美什 | 雷 · 毁灭 | 5 | 0 | 8 | 无文件 | — |

## 光锥（全部）

| id | 名称 | 规则数 | 状态 | 还挂着的登记 |
|---|---|---|---|---|
| 20000 | 锋镝 | 5 | 完全 | — |
| 20001 | 物穰 | 10 | 完全 | — |
| 20002 | 天倾 | 10 | 完全 | — |
| 20003 | 琥珀 | 10 | 完全 | — |
| 20004 | 幽邃 | 5 | 完全 | — |
| 20005 | 齐颂 | 5 | 完全 | — |
| 20006 | 智库 | 5 | 完全 | — |
| 20007 | 离弦 | 5 | 完全 | — |
| 20008 | 嘉果 | 5 | 完全 | — |
| 20010 | 戍御 | 5 | 完全 | — |
| 20011 | 渊环 | 5 | 完全 | — |
| 20012 | 轮契 | 5 | 完全 | — |
| 20013 | 灵钥 | 5 | 完全 | — |
| 20014 | 相抗 | 5 | 完全 | — |
| 20016 | 俱殁 | 5 | 完全 | — |
| 20017 | 开疆 | 5 | 完全 | — |
| 20018 | 匿影 | 10 | 完全 | — |
| 20019 | 调和 | 5 | 完全 | — |
| 20020 | 睿见 | 5 | 完全 | — |
| 20021 | 焚影 | 5 | 完全 | — |
| 20022 | 溯忆 | 10 | 完全 | — |
| 21000 | 一场术后对话 | 5 | 完全 | — |
| 21001 | 晚安与睡颜 | 10 | 完全 | — |
| 21002 | 余生的第一天 | 5 | 完全 | — |
| 21003 | 唯有沉默 | 10 | 完全 | — |
| 21004 | 记忆中的模样 | 10 | 完全 | — |
| 21006 | 「我」的诞生 | 10 | 完全 | — |
| 21007 | 同一种心情 | 10 | 完全 | — |
| 21008 | 猎物的视线 | 5 | 完全 | — |
| 21011 | 与行星相会 | 5 | 完全 | — |
| 21012 | 秘密誓心 | 5 | 完全 | — |
| 21013 | 别让世界静下来 | 10 | 完全 | — |
| 21014 | 此时恰好 | 10 | 完全 | — |
| 21015 | 决心如汗珠般闪耀 | 10 | 完全 | — |
| 21016 | 宇宙市场趋势 | 5 | 完全 | — |
| 21018 | 舞！舞！舞！ | 5 | 完全 | — |
| 21019 | 在蓝天下 | 10 | 完全 | — |
| 21020 | 天才们的休憩 | 10 | 完全 | — |
| 21022 | 延长记号 | 15 | 完全 | — |
| 21025 | 过往未来 | 5 | 完全 | — |
| 21026 | 汪！散步时间！ | 15 | 完全 | — |
| 21027 | 早餐的仪式感 | 5 | 完全 | — |
| 21028 | 暖夜不会漫长 | 10 | 完全 | — |
| 21031 | 重返幽冥 | 5 | 完全 | — |
| 21033 | 无处可逃 | 10 | 完全 | — |
| 21034 | 今日亦是和平的一日 | 5 | 完全 | — |
| 21035 | 何物为真 | 10 | 完全 | — |
| 21036 | 美梦小镇大冒险 | 15 | 完全 | — |
| 21037 | 最后的赢家 | 10 | 完全 | — |
| 21039 | 织造命运之线 | 10 | 完全 | — |
| 21040 | 银河沦陷日 | 5 | 完全 | — |
| 21041 | 好戏开演 | 10 | 完全 | — |
| 21043 | 两个人的演唱会 | 5 | 完全 | — |
| 21045 | 谐乐静默之后 | 10 | 完全 | — |
| 21046 | 芳华待灼 | 5 | 完全 | — |
| 21047 | 黑夜如影随行 | 10 | 完全 | — |
| 21048 | 梦的蒙太奇 | 10 | 完全 | — |
| 21050 | 胜利只在朝夕间 | 5 | 完全 | — |
| 21051 | 天才们的问候 | 5 | 完全 | — |
| 21052 | 多流汗，少流泪 | 10 | 完全 | — |
| 21053 | 愿旅途永远坦然 | 10 | 完全 | — |
| 21054 | 故事的下一页 | 5 | 完全 | — |
| 21055 | 直到明天的明天 | 5 | 完全 | — |
| 21056 | 追逐风的时候 | 5 | 完全 | — |
| 21057 | 花儿不会忘记 | 5 | 完全 | — |
| 21058 | 一行往日的血 | 10 | 完全 | — |
| 21060 | 氤氲麦香的梦 | 10 | 完全 | — |
| 21062 | 于那终点再见 | 10 | 完全 | — |
| 21064 | 菇菇嘎嘎历险记 | 5 | 完全 | — |
| 21066 | 放个短假 | 5 | 完全 | — |
| 22002 | 为了明日的旅途 | 10 | 完全 | — |
| 22003 | 忍事录•音律狩猎 | 10 | 完全 | — |
| 22004 | 宇宙大生意 | 5 | 完全 | — |
| 22005 | 永远的迷境饭 | 5 | 完全 | — |
| 22006 | 飞向粉色的明天 | 5 | 完全 | — |
| 22007 | 未来，有我们一起 | 5 | 完全 | — |
| 22008 | 向着地平线的终点 | 5 | 完全 | — |
| 23002 | 无可取代的东西 | 10 | 完全 | — |
| 23003 | 但战斗还未结束 | 10 | 完全 | — |
| 23007 | 雨一直下 | 20 | 完全 | — |
| 23008 | 棺的回响 | 10 | 完全 | — |
| 23009 | 到不了的彼岸 | 10 | 完全 | — |
| 23013 | 时节不居 | 5 | 完全 | — |
| 23014 | 此身为剑 | 25 | 完全 | — |
| 23015 | 比阳光更明亮的 | 5 | 完全 | — |
| 23016 | 烦恼着，幸福着 | 15 | 完全 | — |
| 23020 | 纯粹思维的洗礼 | 15 | 完全 | — |
| 23023 | 命运从未公平 | 15 | 完全 | — |
| 23024 | 行于流逝的岸 | 15 | 完全 | — |
| 23026 | 夜色流光溢彩 | 10 | 完全 | — |
| 23030 | 落日时起舞 | 10 | 完全 | — |
| 23031 | 我将，巡征追猎 | 15 | 完全 | — |
| 23032 | 唯有香如故 | 15 | 完全 | — |
| 23033 | 忍法帖•缭乱破魔 | 20 | 完全 | — |
| 23035 | 长路终有归途 | 5 | 完全 | — |
| 23036 | 将光阴织成黄金 | 20 | 完全 | — |
| 23038 | 如果时间是一朵花 | 10 | 完全 | — |
| 23040 | 让告别，更美一些 | 10 | 完全 | — |
| 23044 | 黎明恰如此燃烧 | 20 | 完全 | — |
| 23046 | 理想燃烧的地狱 | 2 | 完全 | — |
| 23047 | 海洋为何而歌 | 10 | 完全 | — |
| 23054 | 当她决定看见 | 15 | 完全 | — |
| 23056 | 一场谎言的终幕 | 20 | 完全 | — |
| 23057 | 欢迎来到银河城 | 21 | 完全 | — |
| 23062 | 所见即我 | 20 | 完全 | — |
| 24000 | 记一位星神的陨落 | 10 | 完全 | — |
| 24001 | 星海巡航 | 15 | 完全 | — |
| 24004 | 不息的演算 | 10 | 完全 | — |
| 24005 | 记忆永不落幕 | 5 | 完全 | — |
| 24006 | 欢愉满溢祝福 | 10 | 完全 | — |
| 20009 | 乐圮 | 5 | 有登记 | shattered_home_against_healthy、shattered_home_against_healthy、shattered_home_against_healthy… |
| 20015 | 蕃息 | 5 | 有登记 | cone20015_advance_after_basic、cone20015_advance_after_basic、cone20015_advance_after_basic… |
| 20024 | 残泪 | 11 | 有登记 | cone20024_threshold_met、cone20024_threshold_lost、cone20024_threshold_met… |
| 21005 | 鼹鼠党欢迎你 | 15 | 有登记 | moles_stack_on_basic_attack、moles_stack_on_skill_cast、moles_stack_on_ult_cast… |
| 21009 | 朗道的选择 | 10 | 有登记 | landau_damage_taken_down、landau_damage_taken_down、landau_damage_taken_down… |
| 21010 | 论剑 | 10 | 有登记 | cone21010_stack_on_the_target、cone21010_damage_per_stack、cone21010_stack_on_the_target… |
| 21017 | 点个关注吧！ | 10 | 有登记 | cone21017_basic_boost、cone21017_basic_boost、cone21017_basic_boost… |
| 21021 | 等价交换 | 5 | 有登记 | talent_energy_for_low_ally、talent_energy_for_low_ally、talent_energy_for_low_ally… |
| 21023 | 我们是地火 | 10 | 有登记 | cone21023_party_damage_taken_down、cone21023_party_damage_taken_down、cone21023_party_damage_taken_down… |
| 21024 | 春水初生 | 5 | 有登记 | cone21024_battle_start、cone21024_battle_start、cone21024_battle_start… |
| 21029 | 后会有期 | 10 | 有登记 | cone21029_extra_damage_normal、cone21029_extra_damage_bpskill、cone21029_extra_damage_normal… |
| 21030 | 这就是我啦！ | 5 | 有登记 | cone21030_ult_defence_scaled_damage、cone21030_ult_defence_scaled_damage、cone21030_ult_defence_scaled_damage… |
| 21032 | 镂月裁云之意 | 10 | 有登记 | cone21032_battle_start、cone21032_wearer_turn_start、cone21032_battle_start… |
| 21042 | 铭记于心的约定 | 10 | 有登记 | cone21042_turns、cone21042_turns、cone21042_turns… |
| 21044 | 无边曼舞 | 10 | 有登记 | cone21044_crit_vs_defence_down、cone21044_crit_vs_slowed、cone21044_crit_vs_defence_down… |
| 21061 | 假日浴场大冒险 | 2 | 有登记 | cone21061_vulnerability |
| 21065 | 今日好手气 | 5 | 有登记 | cone21065_elation_stack、cone21065_elation_stack、cone21065_elation_stack… |
| 22000 | 新手任务开始前 | 5 | 有登记 | cone22000_energy_after_hitting_a_lowered_target、cone22000_energy_after_hitting_a_lowered_target、cone22000_energy_after_hitting_a_lowered_target… |
| 22001 | 嘿，我在这儿 | 10 | 有登记 | cone22001_turns、cone22001_turns、cone22001_turns… |
| 23000 | 银河铁道之夜 | 5 | 有登记 | cone23000_damage_up_on_break、cone23000_damage_up_on_break、cone23000_damage_up_on_break… |
| 23001 | 于夜色中 | 5 | 有登记 | cone23001_stat、cone23001_stat、cone23001_stat… |
| 23004 | 以世界之名 | 10 | 有登记 | cone23004_boost_vs_debuffed、cone23004_cast_scope_stats、cone23004_boost_vs_debuffed… |
| 23005 | 制胜的瞬间 | 5 | 有登记 | moment_aggro_up、moment_aggro_up、moment_aggro_up… |
| 23006 | 只需等待 | 10 | 有登记 | cone23006_speed_stack_on_attack、cone23006_thread_dot、cone23006_speed_stack_on_attack… |
| 23010 | 拂晓之前 | 15 | 有登记 | cone23010_skill_damage、cone23010_ult_damage、cone23010_skill_damage… |
| 23011 | 她已闭上双眼 | 10 | 有登记 | cone23011_party_damage_up_when_wearer_hurt、cone23011_party_damage_up_when_wearer_hurt、cone23011_party_damage_up_when_wearer_hurt… |
| 23012 | 如泥酣眠 | 5 | 有登记 | cone23012_crit_damage、cone23012_crit_damage、cone23012_crit_damage… |
| 23017 | 惊魂夜 | 5 | 有登记 | cone23017_heal_lowest_ally、cone23017_heal_lowest_ally、cone23017_heal_lowest_ally… |
| 23018 | 片刻，留在眼底 | 10 | 有登记 | cone23018_ult_damage_per_energy、cone23018_ult_damage_per_energy、cone23018_ult_damage_per_energy… |
| 23019 | 镜中故我 | 3 | 有登记 | cone23019_wave_energy |
| 23021 | 游戏尘寰 | 25 | 有登记 | cone23021_mask_at_battle_start、cone23021_flame_per_point、cone23021_flame_pays_out… |
| 23022 | 重塑时光之忆 | 25 | 有登记 | cone23022_seer_from_dot_1、cone23022_seer_from_dot_2、cone23022_seer_from_dot_3… |
| 23025 | 梦应归于何处 | 10 | 有登记 | cone23025_collapse_on_break、cone23025_collapse_on_break、cone23025_collapse_on_break… |
| 23027 | 驶向第二次生命 | 5 | 有登记 | cone23027_break_ignores_defence、cone23027_break_ignores_defence、cone23027_break_ignores_defence… |
| 23028 | 偏偏希望无价 | 5 | 有登记 | cone23028_stat、cone23028_stat、cone23028_stat… |
| 23029 | 那无数个春天 | 1 | 有登记 | cone23029_armor_break |
| 23034 | 回到大地的飞行 | 30 | 有登记 | cone23034_chant_for_the_target_skill、cone23034_chant_for_the_target_ult、cone23034_chant_for_the_target_skill… |
| 23037 | 向着不可追问处 | 5 | 有登记 | cone23037_skill_and_ult_damage_up、cone23037_skill_and_ult_damage_up、cone23037_skill_and_ult_damage_up… |
| 23039 | 血火啊，燃烧前路 | 5 | 有登记 | cone23039_health、cone23039_health、cone23039_health… |
| 23041 | 生命当付之一炬 | 2 | 有登记 | cone23041_defence_down |
| 23042 | 愿虹光永驻天空 | 10 | 有登记 | cone23042_stat、cone23042_stat、cone23042_stat… |
| 23043 | 谎言在风中飘扬 | 5 | 有登记 | cone23043_stat、cone23043_stat、cone23043_stat… |
| 23045 | 没有回报的加冕 | 5 | 有登记 | cone23045_stat、cone23045_stat、cone23045_stat… |
| 23048 | 金血铭刻的时代 | 15 | 有登记 | cone23048_skill_point、cone23048_skill_point、cone23048_skill_point… |
| 23049 | 致长夜的星光 | 5 | 有登记 | cone23049_stat、cone23049_stat、cone23049_stat… |
| 23050 | 勿忘她的火焰 | 15 | 有登记 | cone23050_weakness_grants_skill_point、cone23050_weakness_grants_skill_point、cone23050_weakness_grants_skill_point… |
| 23051 | 纵然山河万程 | 1 | 有登记 | cone23051_ult_heal |
| 23052 | 爱如此刻永恒 | 10 | 有登记 | cone23052_blank_on_ally、cone23052_poem_on_enemy、cone23052_blank_on_ally… |
| 23053 | 花花世界迷人眼 | 20 | 有登记 | cone23053_spent_stack、cone23053_def_ignore_per_stack、cone23053_spent_stack… |
| 23058 | 邂逅于下一个花季 | 5 | 有登记 | cone23058_elation_cast_damage_taken、cone23058_elation_cast_damage_taken、cone23058_elation_cast_damage_taken… |
| 23059 | 灼尽炼狱的新骸 | 20 | 有登记 | cone23059_purgatory、cone23059_purgatory、cone23059_purgatory… |
| 23060 | 当一颗星照亮夜空 | 5 | 有登记 | cone23060_def_ignore、cone23060_def_ignore、cone23060_def_ignore… |
| 23061 | 星火悄然闪耀 | 15 | 有登记 | cone23061_crown_pays_out、cone23061_crown_pays_out、cone23061_crown_pays_out… |
| 23063 | 你将起身歌唱 | 2 | 有登记 | cone23063_battle_advance |
| 23064 | 向浪花掷下盛夏 | 20 | 有登记 | cone23064_wind_gust、cone23064_skill_point_per_wave、cone23064_elation_cast_count… |
| 24002 | 记忆的质料 | 5 | 有登记 | cone24002_shield_when_unshielded、cone24002_shield_when_unshielded、cone24002_shield_when_unshielded… |
| 24003 | 孤独的疗愈 | 1 | 有登记 | cone24003_ult_dot |
| 20023 | 嗤笑 | 0 | 无文件（已登记为不建模） | — |
| 21038 | 在火的远处 | 0 | 无文件（已登记为不建模） | — |
| 23055 | 献给明日的色彩 | 0 | 无文件 | — |

## 遗器套装（全部）

| id | 名称 | 规则数 | 状态 | 还挂着的登记 |
|---|---|---|---|---|
| 101 | 云无留迹的过客 | 1 | 完全 | — |
| 102 | 野穗伴行的快枪手 | 1 | 完全 | — |
| 103 | 净庭教宗的圣骑士 | 1 | 完全 | — |
| 104 | 密林卧雪的猎人 | 1 | 完全 | — |
| 105 | 街头出身的拳王 | 2 | 完全 | — |
| 106 | 戍卫风雪的铁卫 | 2 | 完全 | — |
| 107 | 熔岩锻铸的火匠 | 2 | 完全 | — |
| 108 | 繁星璀璨的天才 | 2 | 完全 | — |
| 109 | 激奏雷电的乐队 | 1 | 完全 | — |
| 110 | 晨昏交界的翔鹰 | 1 | 完全 | — |
| 111 | 流星追迹的怪盗 | 1 | 完全 | — |
| 112 | 盗匪荒漠的废土客 | 2 | 完全 | — |
| 114 | 骇域漫游的信使 | 1 | 完全 | — |
| 116 | 幽锁深牢的系囚 | 2 | 完全 | — |
| 117 | 死水深潜的先驱 | 6 | 完全 | — |
| 118 | 机心戏梦的钟表匠 | 1 | 完全 | — |
| 120 | 风举云飞的勇烈 | 1 | 完全 | — |
| 121 | 重循苦旅的司铎 | 2 | 完全 | — |
| 122 | 识海迷坠的学者 | 2 | 完全 | — |
| 123 | 凯歌祝捷的英豪 | 2 | 完全 | — |
| 124 | 哀歌覆国的诗人 | 4 | 完全 | — |
| 127 | 再创天地的救世主 | 2 | 完全 | — |
| 128 | 自匿星芒的隐士 | 2 | 完全 | — |
| 131 | 星如我见的领航员 | 4 | 完全 | — |
| 133 | 戏梦点星的伶人 | 4 | 完全 | — |
| 134 | 贪噬禁果的异端 | 2 | 完全 | — |
| 301 | 太空封印站 | 1 | 完全 | — |
| 302 | 不老者的仙舟 | 1 | 完全 | — |
| 304 | 筑城者的贝洛伯格 | 1 | 完全 | — |
| 305 | 星体差分机 | 1 | 完全 | — |
| 306 | 停转的萨尔索图 | 1 | 完全 | — |
| 307 | 盗贼公国塔利亚 | 1 | 完全 | — |
| 308 | 生命的翁瓦克 | 1 | 完全 | — |
| 309 | 繁星竞技场 | 1 | 完全 | — |
| 310 | 折断的龙骨 | 1 | 完全 | — |
| 311 | 苍穹战线格拉默 | 2 | 完全 | — |
| 312 | 梦想之地匹诺康尼 | 1 | 完全 | — |
| 313 | 无主荒星茨冈尼亚 | 2 | 完全 | — |
| 314 | 出云显世与高天神国 | 1 | 完全 | — |
| 315 | 奔狼的都蓝王朝 | 3 | 完全 | — |
| 316 | 劫火莲灯铸炼宫 | 1 | 完全 | — |
| 317 | 沉陆海域露莎卡 | 1 | 完全 | — |
| 318 | 奇想蕉乐园 | 1 | 完全 | — |
| 319 | 谧宁拾骨地 | 2 | 完全 | — |
| 320 | 渊思寂虑的巨树 | 4 | 完全 | — |
| 322 | 沉欢醉饮的海隅 | 2 | 完全 | — |
| 323 | 永恒之地翁法罗斯 | 1 | 完全 | — |
| 325 | 零号关卡朋克洛德 | 8 | 完全 | — |
| 326 | 千星荟萃之城 | 2 | 完全 | — |
| 328 | 寰宇生研院 | 2 | 完全 | — |
| 113 | 宝命长存的莳者 | 1 | 有登记 | relic113_4_crit_after_hit_or_price |
| 115 | 毁烬焚骨的大公 | 3 | 有登记 | relic115_clear_on_followup、relic115_stack_per_followup_hit |
| 119 | 荡除蠹灾的铁骑 | 2 | 有登记 | relic119_4_break_ignores_defence、relic119_4_super_break_ignores_more_defence |
| 125 | 烈阳惊雷的女武神 | 1 | 有登记 | gentle_rain_on_healing_another |
| 126 | 恶海逐波的船长 | 2 | 有登记 | relic126_help_stack |
| 129 | 闪耀功勋的魔法少女 | 1 | 有登记 | relic129_elation_ignores_defence |
| 130 | 应天涉远的卜者 | 3 | 有登记 | relic130_elation_first |
| 303 | 泛银河商业公司 | 1 | 有登记 | relic303_2_atk_from_effect_hit_rate |
| 321 | 妖精织梦的乐园 | 6 | 有登记 | arcadia_wearer_1_missing、arcadia_memosprite_1_missing、arcadia_wearer_2_missing… |
| 324 | 天国@直播间 | 2 | 有登记 | (无 id) |
| 327 | 坠星启航地 | 1 | 有登记 | relic327_companion_crit_dmg |
| 132 | 叩问天工的名冶 | 0 | 无文件（已登记为不建模） | — |

## 缺口里最该先看的几类

1. **整块空白**：`1014` Saber / `1015` Archer / `1508` 远坂凛 / `1509` 吉尔伽美什 —— 联动四人，原文文档齐全（`E:\turnbasedgamedata\aluminium_texts`），内容文件一行没有。
2. **没人登记的缺口**：光锥 `23055` 献给明日的色彩 —— 既无内容文件，也不在 `_unmodelled.json` 里（**唯一**一条）。⚠ **订正**：遗器 `132` 叩问天工的名冶 **在** `relic_sets/_unmodelled.json` 里，是「登记为不建模」✓ —— 前一次的说法（说它没人登记）是错的，量 `_unmodelled.json` 的键就看得出来。`_unmodelled.json` 是「我们决定**不**建模」的登记处（`20023` / `21038` 在里面，各带理由）。
3. **静默的半成品**：规则数远少于技能槽数的角色（见角色表的「规则数 / 技能槽数」两列）—— 它们**没有**留下登记痕迹，所以「完全」这一列会把它们算进去。这是本表最需要人复核的一列。

