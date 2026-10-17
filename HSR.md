# 崩坏：星穹铁道 战斗机制总结（全版本 · AI 实现版）

> 整合基础战斗机制、3.0 忆灵系统、4.0 欢愉体系与超击破体系四大模块，供游戏实现与数值参考。
> 文本取自游戏机制文档整理，版本以 4.4.0 数据为准。

---

## 1. 核心属性系统

### 1.1 角色属性
最终面板属性（攻击、生命、防御、速度、仇恨）由：角色自身（基础+行迹+星魂）、光锥（基础+被动）、遗器（套装+词条）、战斗增益（队友/场景/祝福）构成。

**最终属性**：
```
最终属性 = (角色基础 + 光锥基础) × (1 + 加成%) + 固定加成
```
- 速度与仇恨的光锥基础恒为 0。
- 游戏内显示向下取整，实际计算使用完整精度。

**受增益/负面影响的属性**：
```
最终属性 = (角色基础 + 光锥基础) × (1 + 基础提高% - 基础降低%) × (1 + 加成% - 降低%) + 固定加成
```

### 1.2 敌人属性
```
敌人属性 = 基础值 × 等级组系数 × 自身调整系数 × Π精英组别系数 + 自身调整值
```
**完整链路（已逐项验证，详见 §10.7）**：
```
敌人属性 = 模板基础值 (tbgd MonsterTemplateConfig)
         × 等级组系数 (来自「关卡」StageConfig.HardLevelGroup + Level)
         × 自身调整系数 (MonsterConfig；HP 用 health_modify_ratio)
         × 精英组系数 (来自「波次」StageInfiniteMonsterGroup.EliteGroup
                       → EliteGroup / InfiniteEliteGroup 的 HPRatio)
```
- **等级组 / 精英组都不来自怪物自身**：怪物自身的 `hard_level_group` / `elite_group` 通常恒为 1，真正的组号与等级由**关卡**给，精英组由**波次**给。
- HP 的自身调整字段是 **`health_modify_ratio`**；同文件里的 `hp_modify_ratio` 是解析残留的**幽灵字段**（恒为 1），误用会导致 HP 差几倍。
- 普通关卡用 `EliteGroup.json`；无限波次关卡（`_StageInfiniteGroup`）用 **`InfiniteEliteGroup.json`** —— **用错表 HP 会差几倍**。
- 通用等级组：90 级敌人防御 ≈ `200 + 10 × 等级`（扑满 `300 + 15 × 等级`）。
- 部分精英 90 级速度可达 190.08。
- 双爆：基础暴击率 0%、暴伤 20%，不随等级成长。
- 效果命中：50 级后每级 +0.8%，90 级 32%。
- 效果抵抗：精英/Boss 20%–30%，50 级后每级 +0.4%，90 级 30%–40%。**该字段是加法**：`模板值 + 等级组值`（如冰锋 0.2 + 组1-Lv90 的 0.1 = 0.3），不是相乘。

---

## 2. 伤害计算乘区总览

```
最终伤害 = 基础伤害区 × 伤害修饰区 × 暴击区 × 防御区 × 抗性区 × 特殊乘区
```

### 2.1 基础伤害区
```
角色技能/持续伤害：基础伤害 = 倍率% × 相应属性 + 固定值
击破/击破持续伤害：基础倍率由等级与击破特攻决定（查数值表）
敌人技能伤害：倍率查阅专门数据汇总
```

### 2.2 伤害修饰区
```
伤害修饰区 = 增伤区 × 易伤区 × 减伤区 × 虚弱区
```
- **增伤区** `1 + Σ(伤害类型增伤 + 攻击类型增伤)`：来源攻击方增益
- **易伤区** `1 + Σ(伤害类型易伤 + 攻击类型易伤)`：来源受击方负面，叠加上限 **3.5**
- **减伤区** `Π(1 - 减伤)`：来源受击方增益，乘算，下限 **0.01**（减伤上限 0.99）
- **虚弱区** `1 - Σ虚弱系数`：来源攻击方负面，下限 **0.2**

**逆向确认的各区精确构成**（字段名见 §10.2）：
- **增伤区** = `(1 + Σ元素增伤 + 全属性增伤 + 持续伤害增伤) × (1 + Level_FinalDamageAddedRatio) × (1 + 连携攻击增伤 ComboDamageAddedRatio) × BaseDamageMultiRatio × 攻击类型附加增伤`
  - 注意 `Level_FinalDamageAddedRatio`（内部名）与 `AllDamageTypeAddedRatio` 是**两个不同的加项**
  - ⚠ 「连携攻击增伤」的属性是 `Combo`(165)，**不是追加攻击**；`AbilityProperty` 里**根本没有「追加攻击」**这一项（§10.8）
- **易伤区** = `min(上限, 1 + Σ元素易伤 + 全属性易伤) × (1 + 怪物易伤)` —— `Monster_DamageTakenRatio` 在**上限之外**单独相乘
- **抗性区** = `1 − 净抗性`（净抗性 = 元素抗性 + 全属性抗性 − 元素穿透 − 全属性穿透，已 clamp）
- **减伤区** 与防御、抗性用 `1 − Π(1−x)` 合并（见 §10.6），不是三者各自独立相乘
- **吸收（Absorb）** 是**减法项**，不属于乘区

### 2.3 暴击区
可暴击伤害才生效（击破、持续伤害、超击破不可暴击）：
```
暴击时：暴击区 = 1 + 暴击伤害；未暴击：暴击区 = 1
期望：1 + 暴击率 × 暴击伤害
```
- 多段伤害：每段独立判定暴击。
- 敌人基础暴伤 20%、暴击率 0%。

### 2.4 防御区
```
防御区 = (200 + 10 × 攻击方等级) / (受击方防御 + 200 + 10 × 攻击方等级)
受击方防御 = 原始防御 × (1 - 减防% - 防御穿透%)
```
- 防御不为负，防御区上限 1。
- **逆向确认**：游戏内部先算「防御减伤」`D / (D + 等级项)`，再与抗性、减伤一起用 `1 − Π(1−x)` 合成，最后取 `1 −` 得到三者乘积（见 §10.6）。

### 2.5 抗性区
```
抗性区 = 1 - 对应属性抗性
对应属性抗性 = 原始抗性 + 抗性提高 - 抗性降低 - 抗性穿透
```
- 取值范围：抗性 `-100% ~ 90%`（抗性区 `0.1 ~ 2.0`）。
- 弱点击破状态不改变抗性；是否降低抗性看技能描述。
- **逆向确认**：`净抗性 = 元素抗性 + 全属性抗性 − 元素穿透 − 全属性穿透`（先 clamp），`抗性区 = 1 − 净抗性`；该净抗性会被单独存进伤害对象，便于逐项校验。

### 2.6 特殊乘区
独立于上述乘区的增伤/易伤，仅特定机制出现（活动、Boss 技能），常规计算忽略。
- **真实伤害**（`TrueDamage`）：独立分类，通常绕过防御/抗性等常规乘区（具体看技能）。
- **独立乘区**（如忆灵/欢愉/超击破的专属增伤）：与常规增伤区**相乘**，不并入 `Σ增伤`。
- 实现建议：把「独立乘区」显式建模为单独的乘数，不要塞进 §2.2 的求和里。

---

## 3. 辅助机制

### 3.1 行动与速度
```
行动值 = 10000 / 速度
首轮总行动值 150，后续轮次每轮 100
```
- **额外回合**：不消耗回合数，期间不可插入终结技。
- 行动提前/延后：缩短/延长下次行动等待时间。

### 3.2 弱点击破
- 基准破韧：普攻对单 1；战技（对单）2；终结技 3；扩散 2 / 1（中心/周围）；详见技能数据表。

```
破韧 = 基础破韧 × (1 + 弱点击破效率)
```
- 不同属性击破附带不同状态效果（灼烧/裂伤/触电/冻结…），数值查数据表。

### 3.3 能量恢复
```
最终获得能量 = 基础获得能量 × (1 + 能量恢复效率%)
```
- 基础回能来源于攻击、受击、技能；追加攻击的基础回能因角色而异（如景元 0、黑塔 5）。

### 3.4 仇恨系统
**角色基础仇恨值**：存护 150 / 毁灭 125 / 其他命途 100。

```
受击概率 = 角色当前仇恨值 / 全队总仇恨值
```
- 嘲讽：状态，按百分比提高角色仇恨值。

### 3.5 效果命中与抵抗
```
生效概率 = 基础概率 × (1 + 施加方效果命中%) × (1 - 受击方效果抵抗%)
特定免疫：生效概率 = 基础 × (1+命中%) × (1-抵抗%) × (1-特定负面效果抵抗%)
```

---

## 4. 治疗与护盾

```
基础治疗量 = 倍率% × 相应属性 + 固定值
基础护盾量 = 倍率% × 相应属性 + 固定值

治疗量 = 基础 × (1 + Σ治疗量加成) × (1 - Σ治疗量降低)
护盾量 = 基础 × (1 + Σ护盾量提高 + Σ获得护盾量提高)
```
- 护盾通常不可叠加；受到伤害时所有护盾值同时减少。

---

## 5. 忆灵系统（3.0 记忆命途）

### 5.1 忆灵 vs 传统召唤物

| 特性 | 忆灵（记忆命途） | 传统召唤物 |
|---|---|---|
| 本质 | 独立战斗单位（第 5 个角色） | 技能召唤的辅助单位 |
| 面板继承 | 继承召唤者**战斗外**面板（攻/防/双爆/抗性穿透/击破特攻），生命速度由天赋定 | 随本体或技能固定 |
| 战斗参与度 | 可多次行动、技能可手动选目标 | 纯自动 |
| 装备特效 | 属「召唤物」范畴，可触发相关效果但本身不吃召唤物加成（除非写"忆灵"） | 本体增强=召唤物增强 |
| 伤害类型 | **忆灵伤害**（独立分类） | **追加攻击伤害** |
| 本体被控/死亡 | 不受影响，可继续行动 | 取决于设定 |
| 作为技能目标 | 可被我方单体技能选中 | 不可选 |
| 可被敌人攻击 | 有独立血条可受击 | 无实体 |
| 能量条 | 各自独立、可互帮充能 | 无 |

### 5.2 关键规则
- **连携攻击**：角色与忆灵一次行动先后攻击，可穿透"红绿灯"多次受击屏障，触发两次同类附伤效果。
- 星期日战技可同时拉动角色与忆灵。
- 忆灵只继承**进战斗前**的本体面板；战斗中本体的临时增益不同步（除非写明"对忆灵也有效"）。

---

## 6. 欢愉体系（4.0）

### 6.1 核心名词

| 名词 | 解释 |
|---|---|
| 阿哈（行动） | 行动条上的面具单位；欢愉角色越多速度越快，行动时发动「阿哈时刻」 |
| 阿哈时刻 | 解除欢愉命途角色控制，随后按参演编号依次释放欢愉技；结束后消耗全部笑点 |
| 笑点 | 战技点上方计数，笑点越多阿哈伤害越高 |
| 好活当赏 | 战斗开始及阿哈时刻结束时获得，各角色效果不同，时长独立计算 |
| 要有笑声 | 阿哈行动但无可放欢愉技角色时释放；笑点越多效果越强 |

### 6.2 阿哈速度
```
阿哈速度 = 80 + 最快欢愉角色速度/5 + 第二快/10 + 第三快/20 + 最慢/50
```

### 6.3 参演编号
- 决定阿哈时刻中欢愉角色释放顺序；编号越小越先（可在技能面板"欢愉技"查看）。

### 6.4 欢愉伤害公式（完整版）
```
欢愉伤害 = 基础值 × 欢愉倍率 × (1 + 欢愉度) × (1 + 增笑) × 笑点加成 × 抗性穿透区 × 韧性减伤区 × 减防区 × 易伤区 × 双爆区 × 真实伤害
```
- **基础值**：仅与角色等级相关（80 级固定值相同）
- **欢愉倍率**：受技能倍率影响
- **欢愉度**：行迹加点提升的倍率系数
- **增笑**：独立特殊增伤乘区
- **笑点加成**：`1 + 笑点 × 5 / (笑点 + 240)`
- 其余因子属于传统增伤区（抗性穿透、减防、易伤、双爆、真实伤害）

### 6.5 限制与误区
- ⚠️ 欢愉伤害**不受攻击力与属性伤害加成影响**（除非技能特殊说明）。
- 欢愉角色内圈遗器：攻击球与属性伤害球均无效。
- 口诀：**得笑点者得阿哈，得好活者得伤害**。

---

## 7. 超击破（2.2+）

### 7.1 与基础击破的区别

| 项目 | 基础击破 | 超击破 |
|---|---|---|
| 削韧单位 | 常规单攻普攻 = 1 | 常规单攻普攻 = **10 点** |
| 等级基数（80 级） | 3767 | **376.7**（等比缩放） |
| 伤害类型 | 击破伤害 | **超击破伤害**（独立新类型） |

> 单位调整仅便于计算，实际游戏数值未变，只是缩放。

### 7.2 定义与触发条件
- 伤害属性：取决于触发角色属性
- 触发条件：队伍携带**开拓者·同谐** 且 其**终结技生效期间** 我方目标攻击**处于弱点击破状态的敌人**
- **不吃**：攻击力、常规增伤区（属性/攻击类型增伤）、双暴
- **吃**：等级、击破特攻、削韧值、超击破独立增伤、易伤、防御、抗性、减伤

### 7.3 伤害公式
```
超击破伤害 = 击破基数 × (1 + 击破特攻) × 技能最终削韧值 × (1 + 超击破伤害提高)
             × 易伤区 × 防御区 × 抗性区 × 减伤区

技能最终削韧值 = 技能基础削韧值 × (1 + 削韧值提高) × (1 + 弱点击破效率提高)
```
- 击破基数：仅与角色等级相关（80 级 ≈ 376.7）
- 超击破伤害提高：独立增伤乘区（2.2 仅开拓者·同谐提供）

### 7.4 示例（验算）
条件：80 级（基数 376.7）、击破特攻 300%、基础削韧 30、弱点击破效率提高 50%、削韧值提高 150%、超击破提高 40%、敌人 95 级无抗性。

```
技能最终削韧值 = 30 × 2.5 × 1.5 = 112.5
防御区 = 1000 / (1150 + 1000) ≈ 0.4651
超击破伤害 = 376.7 × 4 × 112.5 × 1.4 × 1 × 0.4651 ≈ 110,381 ✓
```

### 7.5 双击破（高阶）
多段伤害中，最后一段之前将韧性削至 0 → 该段触发**击破伤害**；敌人已处于击破状态 → 后续段触发**超击破伤害**。

### 7.6 配装要点
- 超击破角色只堆击破特攻、速度、削韧效率；攻击/增伤/双暴均为无效词条。

---

## 8. 机制总览与实现建议

### 8.1 伤害类型分类
普攻/战技/终结技伤害、追加攻击伤害、击破伤害、超击破伤害、持续伤害、附加伤害、秘技伤害、忆灵伤害（3.0）、欢愉伤害（4.0）、真实伤害。

游戏内部枚举 `AttackType`（用于伤害分类与增伤判定）：

| 值 | 名称 | 中文 |
|---|---|---|
| 0 | Unknown | — |
| 1 | Normal | 普攻 |
| 2 | BPSkill | 战技 |
| 3 | Ultra | 终结技 |
| 4 | QTE | 终结技连携/秘技 |
| 5 | DOT | 持续伤害 |
| 6 | Pursued | 追加攻击 |
| 7 / 8 | Maze / MazeNormal | 秘技 |
| 9 | Insert | 插入/附加攻击 |
| 10 | ElementDamage | 击破 |
| 11 | Level | 场景/关卡伤害 |
| 12 | Servant | 忆灵 |
| 13 | TrueDamage | 真实伤害 |
| 14 | ElationDamage | 欢愉 |

### 8.2 召唤物体系层级
```
召唤物（广义）
├── 传统召唤物（追加攻击型：神君、账账）——属性跟随本体，无独立受击/能量
├── 忆灵（记忆命途）——独立单位，有血条/能量/手动技能，伤害=忆灵伤害
└── 欢愉体系（阿哈/欢愉技）——基于笑点与欢愉度，独立于攻击/属性增伤
```

### 8.3 AI 实现建议
1. **基础模块**：沿用 §1–§4 全部公式，确保属性、乘区、行动、破韧、命中、治疗/护盾精度。
2. **忆灵模块**：独立单位管理（位置/血条/仇恨/能量/增益）；战斗开始时快照本体面板（不含临时增益）；连携攻击按一次行动中的两次独立攻击判定。
3. **欢愉模块**：阿哈为特殊行动单位；笑点计数器独立累加；欢愉伤害禁用攻击力与属性增伤；释放顺序按参演编号。
4. **超击破模块**：统一削韧为"点"单位（普攻=10）；每次攻击前检查击破状态与同谐终结技光环；独立公式、独立增伤乘区；双击破逐段判定。
5. **贪饕（污染）模块**：见 §9。按 `StageMonsterInvasionParam` 取参数；`DirtyHPRatio` 累积压制进度（普通伤害 ×1、**欢愉伤害 ×3**）；多血条怪上限 ×`MaxHPRatioForMultHP`；压制到阈值前 HP 归零只复活、不死。

---

## 9. 贪饕（污染 / StageInvasion）机制

> 结论来自对当前版本（`GameAssembly.dll` dump + tbgd `ExcelOutput` / `Config`）的逆向与实测校验。

### 9.1 触发与形态

- 关卡里符合条件的敌人有概率被「贪饕」侵蚀（`ExcelOutput/StageInvasionConfig.json` 按 `StageID` 指定 `InvasionID`）。
- 被侵蚀的敌人获得 modifier **`MCommon_Gluttony_LV{1,2,3}`**（污染等级 1/2/3），我方获得 `MCommon_Gluttony_BUFF_LV{1,2,3}`。
- **判定**：实体身上存在名字含 `Gluttony` 的 modifier 即为被侵蚀，等级 = `LV{n}`。
- 敌人血条变为品红色（`GameCoreUISetting.json` → `MonsterHPBarStyleColorConfig.StageInvasion = #ff53a4 → #f92eb9`）。

### 9.2 参数表（`ExcelOutput/StageMonsterInvasionParam.json`）

| 污染等级 InvasionID | KillThresh | MaxHPRatio | NormalDamageConvertRatio | ElationDamageConvertRatio | MaxHPRatioForMultHP |
|---|---|---|---|---|---|
| 1 | 0.4 | 0.2 | 1 | 1 | 0.4 |
| 2 | 0.4 | 0.4 | 1 | 1 | 0.72 |
| 3 | 0.6 | 0.8 | 1 | **3** | **1.44** |

参数语义来自枚举 `StageMonsterInvasionParamType`：

| 序号 | 名称 | 含义 |
|---|---|---|
| 0 | `KillThresh` | 击杀阈值 |
| 1 | `MaxHPRatio` | **单血条**怪的生命上限倍率（压制后） |
| 2 | `NormalDamageConvertRatio` | 普通伤害 → 压制进度 的转化率 |
| 3 | `ElationDamageConvertRatio` | 欢愉伤害 → 压制进度 的转化率 |
| 4 | `MaxHPRatioForMultHP` | **多血条（MultHP）**怪的生命上限倍率 |

### 9.3 敌人生命上限（公式，已精确验证）

```
正常 HP = template.health × hardLevelGroup(组, 等级).health × config.hpRatio × EliteGroup(波次.EliteGroup).HPRatio
```

- `template.health`：`monster_template_config.json` 的 `health`（tbgd `HPBase`）
- `hardLevelGroup`：来自**关卡**（`StageConfig.HardLevelGroup` + `StageConfig.Level`），不是怪物自身的
- `EliteGroup`：来自**波次**（`StageInfiniteMonsterGroup.EliteGroup` → `InfiniteEliteGroup.json` 的 `HPRatio`）；普通关卡用 `EliteGroup.json`
- `config.hpRatio`：`monster_config.json` 的 `health_modify_ratio`（注意：**不是** `hp_modify_ratio`，那是幽灵字段恒为 1）

**贪饕对上限的作用**：

| 怪形态 | 开战上限 | 每次致命伤后 |
|---|---|---|
| 单血条（Elite 等） | 正常 HP × **1.0** | × `MaxHPRatio`（污染3 → ×0.8） |
| **多血条（MultHP）** | 正常 HP × **`MaxHPRatioForMultHP`**（污染3 → **×1.44**） | 按同一参数 |

> `1.44` 只给**多血条**怪，单血条怪不吃 —— 这就是「同关卡、同污染等级，两只怪上限倍率不同」的原因。

### 9.4 压制进度（DirtyHP）

- 血条上的**灰条** = 属性 **`DirtyHPRatio`**（modifier `MCommon_Gluttony_DirtyHp` 读的 `MDF_AccumDirtyHPRatio`）。
- 累积规则：
  ```
  ΔDirtyHPRatio = 本次伤害 × 转化率 / 参考值
    转化率：普通伤害 = NormalDamageConvertRatio（=1）
            欢愉伤害 = ElationDamageConvertRatio（污染3 = 3）
  ```
  → **欢愉伤害的压制效果是普通伤害的 3 倍**（污染3级），因此「欢愉伤害能让它少复活一遍」。
- **普通伤害压制**与**欢愉伤害压制**共用同一条灰条，仅权重不同。

### 9.5 复活与彻底死亡

- **每次 HP 归零**：敌人不死亡，立即回满到当前上限，并推进压制进度。
- 未达阈值前，无论打空多少次都会复活。
- **压制进度达到阈值后**，下一次致命伤 = 彻底消灭。
- 实测（污染3级）：`DirtyHPRatio` 到 **0.75** 时死亡；`0.75 = KillThresh / MaxHPRatio = 0.6 / 0.8`。

### 9.6 血条布局（与游戏一致）

```
[ 品红 = 当前 HP ][ ◆ 分界线 = 阈值 ][ 灰 = 压制进度 ]
```
- 灰条**从右往左**填充；
- 分界线在**左侧** `1 − 阈值` 处（污染3级 = 25%）；
- 灰条左边缘推到分界线 = 压制完成，可彻底击杀。

### 9.7 相关 modifier 速查

| modifier | 作用 |
|---|---|
| `MCommon_Gluttony_LV{1,2,3}` | 侵蚀标记 + 等级 |
| `MCommon_Gluttony_DirtyHp` | DirtyHP 血层（压制过程中出现） |
| `MCommon_LockHPThresholdReached_Mark` | 达到击杀阈值标记 |
| `MCommon_Gluttony_PendingLockHP_Mark` | 待锁定标记 |
| `MCommon_Gluttony_BUFF_LV{1,2,3}` | 我方对应的加成 |

### 9.8 实测校验样本（污染3级）

| 怪 | 单血条? | 正常 HP | 实测 MaxHP | 倍率 | 精英组 |
|---|---|---|---|---|---|
| 破晓战队·苍翼 (5013040) | 是 | 3,142,420.21 | 3,142,420.21 → 2,513,936.17 | ×1.0 → ×0.8 | EliteGroup 361 (HPRatio 6.0) |
| 合金机铠·帕姆王 (5014020) | **多血条** | 7,332,313.83 | 10,558,531.91 | **×1.44** | EliteGroup 358 (HPRatio 5.6) |

正常 HP 验算（关卡 30510011，`HardLevelGroup = 3`，`Level = 95`，`hard_level_group[3][95].health = 375.4385`）：

```
苍翼 : 1395   × 375.4385 × 1 × 6.0 = 3,142,420.21 ✓
帕姆王: 3487.5 × 375.4385 × 1 × 5.6 = 7,332,313.83 ✓
```

### 9.9 数据来源

- `ExcelOutput/StageMonsterInvasionParam.json` — 污染参数表
- `ExcelOutput/StageInvasionConfig.json` — 关卡 → 污染等级 → 怪
- `ExcelOutput/StageInvasionBuff.json` — 污染等级 → MazeBuffID
- `ExcelOutput/InfiniteEliteGroup.json` / `EliteGroup.json` — 精英组倍率
- `ExcelOutput/StageInfiniteGroup.json` / `StageInfiniteMonsterGroup.json` — 波次 → 精英组
- `Config/ConfigAbility/BattleEventAbility_ChallengePeakBattle.json` — 污染能力
- `Config/GameCoreUISetting.json` — 血条配色

---

## 10. 游戏内部实现（逆向补充）

> 由 veritas 插件 + il2cpp 反汇编 + tbgd 数据交叉验证，用于把 §2 的「社区公式」与游戏实际实现对齐。

### 10.1 定点数 FixPoint

游戏所有数值是 `RPG.GameCore.FixPoint`（`i64` 定点），bit0 选小数位宽：

```
value = (raw & ~1) >> 33     (bit0 == 0)
value = (raw & ~1) >> 26     (bit0 == 1)
1.0   = 0x200000000
```

转浮点用 `op_Explicit(double)`。**全程定点**，只在最后转浮点 —— 验算要按定点精度对齐（不要用 double 一路乘）。

### 10.2 一次伤害的乘区对象

游戏为每一击构造一个对象（`NCJILFFNINI`，167 个字段），存放全部乘区输入与中间量。关键字段：

| 含义 | 字段 |
|---|---|
| 基础倍率 | `DamagePercentage`、`ExtraDamagePercentage`、`DamageValue`、`BreakDamagePercentage`、`BaseDamageMultiRatio` |
| 攻击 / 防御 / 生命 | `Attack` / `Defence` / `HP`（`AbilityLinearProperty`，即 `base×(1+ratio)+delta`） |
| 增伤（元素） | 物理 / 火 / 冰 / 雷 / 风 / 量子 / 虚数 `AddedRatio` |
| 增伤（全/其他） | `AllDamageTypeAddedRatio`、`AllDamageAddedRatio`(内部名 `Level_FinalDamageAddedRatio`)、`DotDamageAddedRatio`、`BreakDamageAddedRatio`、`ComboDamageAddedRatio`、`ElationDamageAddedRatio` |
| 穿透 | 元素 `Penetrate` + `AllDamageTypePenetrate` |
| 抗性 | 元素 `Resistance` + `AllDamageTypeResistance`；**净抗性**单独存一个字段 |
| 易伤 | 元素 `TakenRatio` + `AllDamageTypeTakenRatio` + `Monster_DamageTakenRatio` |
| 减伤 | `AllDamageReduce` |
| 暴击 | `CriticalChance`、`CriticalDamage`、`CriticalResistance` |
| 吸收 | 元素 `Absorb`（**减法项**） |
| 破韧易伤 | 元素 `StanceBreakTakenRatio` |

### 10.3 公式链（`AbilityStatic.DamageFormula`）

```
x = 基础(按 AttackFormulaType 选择)
x = x × DamagePercentage + DamageValue
x = x × ExtraDamagePercentage (击破时再 × BreakDamagePercentage)
x = x × (1 + Σ元素增伤 + AllDamageTypeAddedRatio + DotDamageAddedRatio)
x = x × (1 + Level_FinalDamageAddedRatio)
x = x × (防御区 × 抗性区 × 减伤区)          // 用 InverseMultiply 合成，见 10.6
x = x × min(上限, 1 + Σ元素易伤 + AllDamageTypeTakenRatio)
x = x × (1 + Monster_DamageTakenRatio)
x = x × 暴击区
x = x × (1 + ComboDamageAddedRatio)
x = x × BaseDamageMultiRatio
x = x − 元素吸收
x = x × _GetExtraDamageRatio               // 攻击类型附加增伤
```

- `FinalDamageFormulaType` 决定走**全链**还是**精简链**；`ByBaseDamage` 直接返回基础值（且**不暴击**）。
- 暴击由 `DamageCriticalEvaluate` 判定：`暴击率 − 暴击抗性 ≥ rand`；`ByPureDamage`/`ByBaseDamage` 强制不暴击。

### 10.4 基础伤害来源（`AttackFormulaType`）

| 值 | 来源 |
|---|---|
| 1 `ByAttack` | 攻击力 |
| 2 `ByDefence` | 防御力 |
| 3 `ByMaxHP` / 4 `ByCurrentHP` | 生命 |
| 5 `ByDefenderMaxHP` / 6 `ByDefenderCurrentHP` | 受击方生命 |
| 7 `ByBreakDamage` | 击破倍率 |
| 8 `BySummonerMaxHP` | 召唤者生命 |
| 9 `ByElationDamage` | 欢愉 |

### 10.5 击破 / 欢愉 的基数表

- **击破基数**：`AvatarBreakDamageRow.BreakBaseDamage` —— **按「等级」**查表（`ExcelOutput/AvatarBreakDamage.json`，101 行 = Level 1..101）。**80 级 = 3767.55**（社区说的 3767 就是它）、**95 级 = 7494.37**。**与角色无关**（表里没有 AvatarID，查表键是等级）。
- **欢愉基础值**：`ElationBasicLevelDamageRow.ElationBasicLevelDamage`（按等级）。
- **笑点**：实体属性 `ElationEchoPoint`。
- 击破 / 超击破不吃攻击力、增伤、双暴；欢愉伤害不吃攻击力与属性增伤（§6.5）。

### 10.6 与社区公式的差异（验算必看）

1. **防御 / 抗性 / 减伤**：游戏用 `InverseMultiply(a,b) = 1 − (1−a)(1−b)` 把「减伤类」因子合成 `1 − Π(1−xᵢ)`，最后再 `1 −` 还原成乘数。**代数等价，但中间取整不同** → 请在**最终因子层**对比，别比中间值。
2. **吸收**是**减法**，不是乘区。
3. **易伤有上限**（常量），`Monster_DamageTakenRatio` 在**上限之外**单独加。
4. **击破基数只与「等级」有关**（`AvatarBreakDamage.json`：80 级 = 3767.55、95 级 = 7494.37），与角色无关。
5. `Level_FinalDamageAddedRatio` 与 `AllDamageTypeAddedRatio` 是**两个不同**的增伤加项。

### 10.7 敌人 HP 完整验算样例（污染 3 级）

关卡 `30510011`：`HardLevelGroup = 3`、`Level = 95`、`_StageInfiniteGroup`（无限波次）。

```
等级组:  hard_level_group[3][95].health = 375.4385
波次 → 精英组: StageInfiniteMonsterGroup.EliteGroup
   wave 305100111 → 361 → InfiniteEliteGroup.HPRatio = 6.0
   wave 305100112 → 358 → InfiniteEliteGroup.HPRatio = 5.6

正常 HP = template.health × 375.4385 × health_modify_ratio(=1) × HPRatio
苍翼   : 1395   × 375.4385 × 6.0 = 3,142,420.21 ✓
帕姆王 : 3487.5 × 375.4385 × 5.6 = 7,332,313.83 ✓
```

### 10.8 `_GetExtraDamageRatio`（攻击类型附加增伤）字段表

> 已完整逆向：`AbilityStatic._GetExtraDamageRatio`（VA `0x18E52C690`）→ 结构 `FJLAKILIMEN` 的
> `LMIEIPAKDGC`（VA `0x1957CD1F0`，跳表 `0x1957CD29C`）与 `PAKIJFOGOJF`（VA `0x1957CD2E0`）；
> 槽位来源 = `ToExtraDamageDataFinal`（VA `0x18E5236F0`）；调用点 = `DamageFormula` VA `0x18E4DA957` / `0x18E4DAA27`。

**位置**：链末**独立乘区**，在「− 吸收」之后：

```
最终伤害 = ( … × BaseDamageMultiRatio − 元素吸收 ) × _GetExtraDamageRatio(…)
```

**载体**：`NCJILFFNINI + 0x380`（`DDMHHFBIJMA`，类型 `FJLAKILIMEN`，0x88 字节）。偏移与 `AbilityProperty` 的对应：

| 偏移 (+0x380) | 字段 | AbilityProperty | 含义 |
|---|---|---|---|
| `0x00` | `MOKNCFJICIC` | — | **有效位**；为 0 时整个乘区恒 `1.0` |
| `0x08` | `HLDABCGMLEN` | `ExtraLuckDamage`(192) + `ExtraLuckDamageConvert`(218) | 幸运伤害 |
| `0x10` | `JFCJNHNBMCB` | `ExtraLuckChance`(191) + `ExtraLuckChanceConvert`(219) | 幸运率 |
| `0x18` | `BICKCCGOKCE` | `GetFrontBackPower(攻击方)/200` | **前后排基准**（不是加项） |
| `0x20` | `GHMBPGFLGKM` | `ExtraAllDamageTypeAddedRatio1`(180) | 通用附加增伤 #1 |
| `0x28` | `AIJFGKGCGDI` | `ExtraAllDamageTypeAddedRatio2`(181) | #2 |
| `0x30` | `PKLIENJDEIH` | `ExtraAllDamageTypeAddedRatio3`(182) | #3 |
| `0x38` | `PBEJAEPALEI` | `ExtraAllDamageTypeAddedRatio4`(183) | #4 |
| `0x40` | `ILBFKHJONOI` | `ExtraAllDamageTypeAddedRatio5`(184) | #5 |
| `0x48` | `MMPHCDCGCKE` | `ExtraSkillDamageAddedRatio1`(202) | **战技** |
| `0x50` | `LKLMCAKKGEP` | `ExtraNormalDamageAddedRatio1`(203) | **普攻** |
| `0x58` | `LKIELCNNLNL` | `ExtraInsertDamageAddedRatio1`(204) | **附加** |
| `0x60` | `DEGHPAJIPFP` | `ExtraUltraDamageAddedRatio1`(201) | **终结技** |
| `0x68` | `CEKOBBDOINO` | `ExtraDOTDamageAddedRatio1`(205) | **持续伤害** |
| `0x70` | `IEEKAECICIF` | `ExtraElementDamageAddedRatio1`(206) | **击破** |
| `0x78` | `ECFJMELFHIO` | — | **累加器**（每次结算前清零） |
| `0x80` | `CILOCPLHJLK` | `ExtraElationDamageAddedRatio1`(207) | **欢愉** |

**按攻击类型累加**（`LMIEIPAKDGC` 的 `switch(AttackType)`）：

| `AttackType` | 累加进累加器的槽 |
|---|---|
| 1 `Normal` 普攻 | `ExtraNormalDamageAddedRatio1` |
| 2 `BPSkill` 战技 | `ExtraSkillDamageAddedRatio1` |
| 3 `Ultra` 终结技 | `ExtraUltraDamageAddedRatio1` |
| 5 `DOT` 持续伤害 | `ExtraDOTDamageAddedRatio1` |
| 9 `Insert` 附加 | `ExtraInsertDamageAddedRatio1` |
| 10 `ElementDamage` 击破 | `ExtraElementDamageAddedRatio1` |
| 14 `ElationDamage` 欢愉 | `ExtraElationDamageAddedRatio1` |
| 4 / 6 / 7 / 8 / 11 / 12 / 13 | **无槽 → 不计入** |

**最终公式**（`PAKIJFOGOJF`）：

```
x = FrontBackPower / 200
x = x × (1 + 幸运触发时的 ExtraLuckDamage)                 // 幸运未触发则为 ×1
x = x × (1 + ExtraAllDamageTypeAddedRatio1 + Σ对应攻击类型的 Extra*DamageAddedRatio1)
x = x × (1 + ExtraAllDamageTypeAddedRatio2)
x = x × (1 + ExtraAllDamageTypeAddedRatio3)
x = x × (1 + ExtraAllDamageTypeAddedRatio4)
x = x × (1 + ExtraAllDamageTypeAddedRatio5)
```

其中前后排基准：

```
GetFrontBackPower(攻击方, useFront) =
    base × (1 + addedRatio1) × (1 + addedRatio2) + convert
  useFront → ExtraFrontPowerBase(194) / ExtraFrontPowerAddedRatio1(195) / …2(196) / ExtraFrontPowerConvert(217)
  !useFront → ExtraBackPowerBase(198)  / ExtraBackPowerAddedRatio1(199)  / …2(200) / ExtraBackPowerConvert(216)
```

**建模结论（关键 6 条）**：

1. **攻击类型增伤不在主增伤桶里**。它不在 `(1 + Σ元素增伤 + AllDamageTypeAddedRatio + DotDamageAddedRatio)` 中，而是**链末独立乘区**（与吸收同层，在吸收之后乘）。
2. **七种攻击类型之间是加算**，且与 `ExtraAllDamageTypeAddedRatio1` **加算**，共同构成**一个** `(1+…)` 因子。
3. `ExtraAllDamageTypeAddedRatio2..5` 各自是**独立因子**，**不**并入 #1。
4. **追加攻击（`Pursued`）没有专属属性**：整张 `Extra*DamageAddedRatio1` 表只有 7 项（Ultra/Skill/Normal/Insert/DOT/Element/Elation），**没有追加攻击 / 忆灵 / 秘技 / 真实伤害**。若要让追加攻击吃到普攻增伤，游戏用的是 `TurnBasedAbilityComponent.RegardAsAttackTypeMap`（`+0xC8`，`Dictionary<AttackType, List<…>>`「视为攻击类型」）—— `GetTargetAttackTypes` 会把别名一并返回，再逐个累加。
5. **前后排加成**（`GetFrontBackPower/200`）是该乘区的**基准**（乘性），不是加项。
6. 有效位由攻击方组件的能力标志决定（`attacker.PropertyEnumBoundary == AbilityProperty.Count(226)`）；未启用时**整区恒 `1.0`**，即该版本/模式下这些属性全为 0，不影响任何伤害。

**依据（可复现）**：

| 符号 | VA | 作用 |
|---|---|---|
| `AbilityStatic._GetExtraDamageRatio` | `0x18E52C690` | 入口；调 `GetTargetAttackTypes` + 累加 + 求值 |
| `AbilityStatic.GetTargetAttackTypes` | `0x18E52C360` | 攻击类型 + 「视为攻击类型」别名 |
| `AbilityStatic.ToExtraDamageDataFinal` | `0x18E5236F0` | 属性 → 槽位（即上表的来源） |
| `FJLAKILIMEN::LMIEIPAKDGC` | `0x1957CD1F0` | `switch(AttackType)` 累加 |
| `FJLAKILIMEN::PAKIJFOGOJF` | `0x1957CD2E0` | 最终乘积 |
| `AbilityStatic.GetFrontBackPower` | `0x18E523A20` | 前后排基准 |
| `AbilityStatic.DamageFormula` | `0x18E4DA957` / `0x18E4DAA27` | 乘入点 |

---

## 附录：数据与实现备注

1. 数据来源：所有基础属性（角色/光锥/敌人）、技能倍率、行迹加成取自数据表（tbgd `ExcelOutput`）。
2. 精度：游戏内向下取整，中间计算用浮点精度。
3. 分段机制：扩散/弹射有伤害分裂比，单段计算需计入。
4. 状态结算：持续伤害遵循"先上先结算"。
5. 特殊免疫判定：模拟宇宙"记忆"祝福等对冻结判定先检查免疫状态。
