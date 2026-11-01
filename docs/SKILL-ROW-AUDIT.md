# 技能行冻结值审计（77 条）

> 扫描生成：某字面值恰好等于某技能表的 **Lv10** 行、该列首末行都不是它、且该列随等级变化。
> 已排除：计数器（`GAIN_ENERGY` / `GAIN_RESOURCE` / `ADD_STACK` / `REMOVE_STACK`）与强化槽 8/9/10/12（没有 `SkillType` 能指向）。
> A 段是**待改**清单；B 段是**巧合同值**，不要改（改了会绑到错的行）。

## A. 疑似真来源（60 条）—— 规则的 `source` 里出现了同一槽位的名字

| 角色 | 规则 | 字段 | 值 | 槽 | 列 | 引用的句子（前 70 字）|
|---|---|---|---|---|---|---|
| 1004 | `talent_additional_damage_on_slowed` | DAMAGE.percent | 0.6 | 天赋 | #1 | 1004 瓦尔特 天赋 时空扭曲 (100404, Enhance): 「击中处于减速状态的敌方目标时，额外造成1次等同于瓦尔特60%攻击力 |
| 1101 | `skill_effect_2` | MODIFY_ATTR.percent | 0.66 | 战技 | #1 | 1101 布洛妮娅 战技 作战再部署 (skill 110102, BPSkill/Support): 「解除指定我方单体的1个负面效果，并 |
| 1101 | `ult_party_attack` | MODIFY_ATTR.percent | 0.55 | 终结技 | #1 | 1101 布洛妮娅 终结技（数据出处 skill_id 110102）: 「使我方全体攻击力提高55%…持续2回合」 |
| 1101 | `ult_party_crit_damage` | MODIFY_ATTR.percent | 0.16 | 终结技 | #2 | 1101 布洛妮娅 终结技: 「同时提高等同于布洛妮娅16.00%暴击伤害+20.00%的暴击伤害，持续2回合」 |
| 1102 | `technique_enters_amplified_state` | MODIFY_ATTR.percent | 0.8 | 天赋 | #1 | 1102 希儿 秘技 幻蝶: 「主动攻击敌人进入战斗时，希儿立即进入增幅状态。」+ 天赋 再现 Lv10 = [0.8, 1]（伤害 +80 |
| 1105 | `skill_effect` | APPLY_REGEN.percent | 0.072 | 战技 | #2 | 1105 娜塔莎 战技 爱，救护与抉择 (110502, BPSkill/Restore): 「立即为指定我方单体回复等同于娜塔莎10.50 |
| 1105 | `skill_effect` | APPLY_REGEN.amount | 192 | 战技 | #5 | 1105 娜塔莎 战技 爱，救护与抉择 (110502, BPSkill/Restore): 「立即为指定我方单体回复等同于娜塔莎10.50 |
| 1107 | `skill_extra_damage_on_marked` | DAMAGE.percent | 1.2 | 战技 | #1 | 1107 克拉拉 战技 史瓦罗的大人: 「对所有被史瓦罗标上【反击标记】的敌方目标额外造成等同于克拉拉120%攻击力物理属性伤害。」 par |
| 1107 | `ult_enhanced_counter` | DAMAGE.percent | 1.6 | 终结技 | #2 | 1107 克拉拉 终结技: 「当任意我方目标受到攻击后史瓦罗立即施放反击，对敌方目标造成的伤害倍率提高160%…」 param = [5,  |
| 1108 | `ult_present` | MODIFY_DAMAGE_TAKEN.percent | 0.3 | 终结技 | #2 | 1108 桑博 终结技 惊喜礼盒 (110803, Ultra/AoEAttack): 「对敌方全体造成等同于桑博160%攻击力的风属性伤害 |
| 1108 | `talent_windtorn` | APPLY_DOT.percent | 0.52 | 天赋 | #2 | 1108 桑博 天赋 撕风的匕首 (110804, Enhance): 「桑博击中敌方目标后有65%的基础概率使目标陷入风化状态，持续3回合 |
| 1109 | `skill_burn` | APPLY_DOT.percent | 0.65 | 战技 | #4 | 1109 虎克 战技 嘿！记得虎克吗 (110902, BPSkill/SingleAttack): 「对指定敌方单体造成等同于虎克240% |
| 1109 | `talent_followup_against_burning` | DAMAGE.percent | 1 | 天赋 | #1 | 1109 虎克 天赋 哈！火上浇油 (110904, Enhance): 「攻击处于灼烧状态的敌方目标时，追加1次等同于虎克100%攻击力的 |
| 1111 | `ult_coup` | MODIFY_DAMAGE_TAKEN.percent | 0.2 | 终结技 | #3 | 1111 卢卡 终结技 制胜一击 (111103, Ultra/SingleAttack): 「获得2层【斗志】，并有100%的基础概率使指 |
| 1111 | `talent_enhanced_laceration` | TICK_DOT.percent | 0.85 | 天赋 | #2 | 1111 卢卡 天赋 火花四溅: 「当强化普攻的【碎天拳】击中陷入裂伤状态下的敌方目标后，则使其当前承受的裂伤状态立即产生1次相当于原伤害8 |
| 1112 | `skill_proof_of_debt` | DAMAGE.percent | 1.5 | 战技 | #1 | 1112 托帕&账账 战技「扭亏为盈！」: 「使指定敌方单体陷入【负债证明】状态，使其受到的追加攻击伤害提高 50%…使账账对该目标造成等同 |
| 1112 | `skill_proof_of_debt` | MODIFY_DAMAGE_TAKEN.percent | 0.5 | 战技 | #2 | 1112 托帕&账账 战技「扭亏为盈！」: 「使指定敌方单体陷入【负债证明】状态，使其受到的追加攻击伤害提高 50%…使账账对该目标造成等同 |
| 1202 | `skill_benediction` | MODIFY_ATTR.percent | 0.5 | 战技 | #2 | 1202 停云 战技 祥音和韵 (120202, BPSkill/Support): 「为指定我方单体提供【赐福】，使其攻击力提高50%，最 |
| 1211 | `talent_heals_a_lethal_hit_on_a_teammate` | HEAL.percent | 0.18 | 天赋 | #3 | 1211 白露 天赋：「当**白露的队友**受到致命攻击时，不会陷入**无法战斗状态**，**白露会立即为其提供治疗**，回复等同于**白露 |
| 1211 | `talent_heals_a_lethal_hit_on_a_teammate` | HEAL.amount | 480 | 天赋 | #4 | 1211 白露 天赋：「当**白露的队友**受到致命攻击时，不会陷入**无法战斗状态**，**白露会立即为其提供治疗**，回复等同于**白露 |
| 1213 | `talent_righteous_heart` | MODIFY_ATTR.percent | 0.1 | 天赋 | #1 | 1213 丹恒•饮月 天赋 亢心 (121304, Enhance): 「丹恒•饮月施放每段攻击后获得1层【亢心】，使自身造成的伤害提高10 |
| 1215 | `ult_blessing` | MODIFY_ATTR.percent | 0.2 | 终结技 | #3 | 1215 寒鸦 终结技 十王敕令，遍土遵行 (skill 121503, Ultra/Enhance): 「使指定我方单体速度提高，提高数值 |
| 1215 | `ult_blessing` | MODIFY_ATTR.percent | 0.6 | 终结技 | #1 | 1215 寒鸦 终结技 十王敕令，遍土遵行 (skill 121503, Ultra/Enhance): 「使指定我方单体速度提高，提高数值 |
| 1218 | `talent_ashen_roast_and_burn` | APPLY_DOT.percent | 1.8 | 天赋 | #6 | 1218 椒丘 天赋 四示八权，纤滋精味 (121804, Enhance): 「椒丘使用普攻、战技、终结技击中敌人时，有100%的基础概率 |
| 1218 | `talent_vulnerability_per_ashen_roast` | MODIFY_DAMAGE_TAKEN.percent | 0.05 | 天赋 | #3 | 1218 椒丘 天赋: 「1层时使敌人受到的伤害提高15.00%，此后每叠加1层提高5.00%。【烬煨】最多叠加5层，持续2回合」 |
| 1218 | `talent_ember_tiered_taken` | MODIFY_DAMAGE_TAKEN.percent | 0.05 | 天赋 | #3 | 1218 椒丘 天赋: 「1 层时使敌人受到的伤害提高 15.00%，此后每叠加 1 层提高 5.00%。【烬煨】最多叠加 5 层，持续 2 |
| 1223 | `ult_triggers_talent_followup` | DAMAGE.percent | 1.6 | 天赋 | #3 | 1223 貊泽 终结技 锋入幽渺，影出凌厉 (122303, Ultra/SingleAttack): 「对指定敌方单体造成等同于貊泽270 |
| 1224 | `skill_shifu_and_speed` | MODIFY_ATTR.percent | 0.1 | 战技 | #1 | 1224 三月七 战技 师父，请喝茶！ (122402, BPSkill/Support): 「使除自身以外的我方指定单体成为【师父】，【师 |
| 1301 | `attack_effect` | HEAL.amount | 640 | 天赋 | #2 | 1301 加拉赫 天赋 鏖战正酣 (130104, Enhance/Restore): 「【酩酊】使目标受到的击破伤害提高12.00%。陷入 |
| 1303 | `ult_zone_all_type_res_pen` | MODIFY_ATTR.percent | 0.25 | 终结技 | #1 | 1303 阮•梅 终结技 摇花缎水，沾衣不摘: 「阮•梅展开结界，持续2回合，自身每回合开始时结界持续回合数减1。处于结界中时我方全体全属性 |
| 1305 | `talent_chance_followup` | DAMAGE.percent | 2.7 | 天赋 | #1 | 1305 真理医生 天赋 我思故我在 (130504, Enhance/FollowUp): 「施放战技时，有40%的固定概率对该目标发动1 |
| 1305 | `ult_reaction_followup` | DAMAGE.percent | 2.7 | 天赋 | #1 | 1305 真理医生 终结技 三段悖论 (130503, Ultra/SingleAttack): 「对指定敌方单体造成等同于真理医生240% |
| 1306 | `skill_crit_damage_share_and_advance` | MODIFY_ATTR.percent | 0.24 | 战技 | #1 | 1306 花火 战技 梦游鱼 (130602, BPSkill/Support): 「强化前：使指定我方单体的暴击伤害提高，提高数值等同于花 |
| 1306 | `talent_party_damage_on_spend` | MODIFY_ATTR.percent | 0.06 | 天赋 | #2 | 1306 花火 天赋 叙述性诡计 (130604, Enhance): 「强化前：花火在场时，战技点上限额外增加2点。当我方目标每消耗1点战 |
| 1309 | `ult_effect_3` | MODIFY_ATTR.percent | 0.228 | 终结技 | #1 | 1309 知更鸟 终结技 ③「处于【协奏】状态时，我方全体攻击力提高，提高数值等同于知更鸟攻击力的 22.80%+200 点」+ ⑤「处于【 |
| 1309 | `ult_effect_3` | MODIFY_ATTR.amount | 200 | 终结技 | #3 | 1309 知更鸟 终结技 ③「处于【协奏】状态时，我方全体攻击力提高，提高数值等同于知更鸟攻击力的 22.80%+200 点」+ ⑤「处于【 |
| 1313 | `ult_effect` | MODIFY_ATTR.percent | 0.12 | 终结技 | #4 | 1313 星期日 终结技 轻与伤痕的赞颂 (skill 131303, Ultra/Support): 「…并使目标及其召唤物成为【蒙福者】 |
| 1314 | `trace_reverse_repo_one_goods` | MODIFY_ATTR.percent | 0.024 | 天赋 | #1 | 1314 翡翠 行迹 逆回购: 「在敌方目标进入战斗时，翡翠获得1层【当品】」＋ 天赋「每层【当品】使暴击伤害提高2.40%，最多叠加50层 |
| 1314 | `technique_fifteen_goods` | MODIFY_ATTR.percent | 0.024 | 天赋 | #1 | 1314 翡翠 秘技 猎手的视界: 「进入战斗后…立即获得15层【当品】」（param = [10, 0.5, 15] ⇒ 15 层 ✓）＋ |
| 1314 | `collector_attack_deals_extra` | DAMAGE.percent | 0.25 | 战技 | #3 | 1314 翡翠 战技: 「【收债人】施放攻击后，对每个击中的敌方目标造成1次等同于翡翠25%攻击力的量子属性附加伤害」 param Lv10 |
| 1404 | `ult_heal_charge_and_taunt` | HEAL.percent | 0.2 | 终结技 | #3 | 1404 万敌 终结技 诛天焚骨的王座 (140403, Ultra/Blast): 「回复等同于万敌20.00%生命上限的生命值并积攒20 |
| 1405 | `talent_damage_vs_true_knowledge` | BOOST_DAMAGE.percent | 0.3 | 天赋 | #1 | 1405 那刻夏 天赋: 「那刻夏对处于【质性揭露】状态的目标造成的伤害提高30%…」 param = [0.3] |
| 1406 | `talent_reaction_followup` | DAMAGE.percent | 1.5 | 天赋 | #1 | 1406 赛飞儿 天赋 热情好客的多洛斯人 (140604, Enhance/FollowUp): 「…施放战技和终结技时，使主目标成为【老 |
| 1407 | `talent_damage_up_on_hp_loss` | MODIFY_ATTR.percent | 0.2 | 终结技 | #4 | 1407 遐蝶 天赋 掌心淌过的荒芜 (140704, Enhance): 「【新蕊】上限与场上全体角色等级有关，我方全体每损失1点生命值遐 |
| 1410 | `skill_damage_taken_up` | MODIFY_DAMAGE_TAKEN.percent | 0.2 | 战技 | #3 | 1410 海瑟音 战技 泛音，暗流后齐鸣 (141002, BPSkill/AoEAttack): 「有100%的基础概率使敌方全体受到的伤 |
| 1415 | `zone_true_damage_rider` | DAMAGE.percent | 0.24 | 战技 | #1 | 1415 昔涟 战技 盛放吧，来世的乐土 (141502, BPSkill/Support) 第三句: 「结界持续期间，我方全体目标每造成1 |
| 1503 | `enhanced_basic_form_a_extra_elation_damage` | DAMAGE.percent | 0.6 | 终结技 | #2 | 1503 真珠 终结技 鉴映灵魂的底色 (150303) 的强化普攻端: 「若【美学底本】为「欢愉」命途，则强化为【行笔，幻造星月】，且攻击 |
| 1506 | `p1506_talent_hidden_becomes_crit` | MODIFY_ATTR.percent | 0.004 | 天赋 | #4 | 1506 银狼LV.999 天赋: 「每点【隐藏分】使暴击率提高 0.4%。暴击率达到 100% 后，其余每点【隐藏分】改为使暴击伤害提高  |
| 8001 | `talent_atk_on_break` | MODIFY_ATTR.percent | 0.2 | 天赋 | #1 | 8001 开拓者•毁灭 天赋 牵制盗垒 (800104, Enhance): 「每次击破敌方目标的弱点后，攻击力提高20%，该效果最多叠加2 |
| 8001 | `eidolon6_talent_also_fires_on_kill` | MODIFY_ATTR.percent | 0.2 | 天赋 | #1 | 8001 开拓者•毁灭 星魂 6: 「开拓者消灭敌方目标时，也会触发天赋。」（天赋：击破后攻击力 +20%，最多 2 层） |
| 8002 | `talent_atk_on_break` | MODIFY_ATTR.percent | 0.2 | 天赋 | #1 | 8002 开拓者•毁灭 天赋 牵制盗垒 (800204, Enhance): 「每次击破敌方目标的弱点后，攻击力提高20%，该效果最多叠加2 |
| 8002 | `eidolon6_talent_also_fires_on_kill` | MODIFY_ATTR.percent | 0.2 | 天赋 | #1 | 8002 开拓者•毁灭 星魂 6: 「开拓者消灭敌方目标时，也会触发天赋。」（天赋：击破后攻击力 +20%，最多 2 层） |
| 8007 | `skill_summon_mem` | HEAL.percent | 0.6 | 战技 | #1 | 8007 开拓者•记忆 战技 就决定是你了！ (800702, BPSkill/Summon): 「召唤忆灵迷迷。若迷迷已在场，为迷迷回复等 |
| 8007 | `ult_summon_mem` | DAMAGE.percent | 2.4 | 终结技 | #1 | 8007 开拓者•记忆 终结技 一起上吧，迷迷！ (800703, Ultra/AoEAttack): 「召唤忆灵迷迷。使迷迷获得40%充能 |
| 8008 | `skill_summon_mem` | HEAL.percent | 0.6 | 战技 | #1 | 8008 开拓者•记忆 战技 就决定是你了！ (800702, BPSkill/Summon): 「召唤忆灵迷迷。若迷迷已在场，为迷迷回复等 |
| 8008 | `ult_summon_mem` | DAMAGE.percent | 2.4 | 终结技 | #1 | 8008 开拓者•记忆 终结技 一起上吧，迷迷！ (800703, Ultra/AoEAttack): 「召唤忆灵迷迷。使迷迷获得40%充能 |
| 8009 | `ult_crit_damage_for_the_chosen_ally` | MODIFY_ATTR.percent | 0.5 | 终结技 | #1 | 8009 开拓者•欢愉 终结技 放心飞，开拓永相随！ (800903, Ultra/Support): 「获得5个笑点，使指定我方单体暴击伤 |
| 8009 | `talent_elation_extra_damage` | DAMAGE.percent | 0.3 | 天赋 | #3 | 8009 开拓者•欢愉 天赋: 「开拓者持有【好活当赏】时，战技对敌方全体额外造成30%的雷属性欢愉伤害，此伤害使用我方最高的【好活当赏】数 |
| 8010 | `ult_crit_damage_for_the_chosen_ally` | MODIFY_ATTR.percent | 0.5 | 终结技 | #1 | 8010 开拓者•欢愉 终结技 放心飞，开拓永相随！ (800903, Ultra/Support): 「获得5个笑点，使指定我方单体暴击伤 |
| 8010 | `talent_elation_extra_damage` | DAMAGE.percent | 0.3 | 天赋 | #3 | 8010 开拓者•欢愉 天赋: 「开拓者持有【好活当赏】时，战技对敌方全体额外造成30%的雷属性欢愉伤害，此伤害使用我方最高的【好活当赏】数 |

## B. 疑似巧合同值（17 条）—— `source` 里没有提到该槽位

| 角色 | 规则 | 字段 | 值 | 槽 | 列 |
|---|---|---|---|---|---|
| 1101 | `eidolon2_speed_on_skill_target` | MODIFY_ATTR.percent | 0.3 | 天赋 | #1 |
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
