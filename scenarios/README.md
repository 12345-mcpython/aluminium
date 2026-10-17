# scenarios —— 与原游戏逐乘区对数值

一个场景 = **一次施放**：谁、什么技能、几级、打谁、什么面板，以及**原游戏给出的每一个乘区的数**。
比较器把引擎的一次结算按乘区拆开（`Damage.breakdown()`），逐行对，差多少直接打出来。

```
gradlew run --args="compare scenarios/1002-skill.json"
gradlew run --args="compare"                     # 列出 scenarios/ 下的场景
```

退出码：**每一行都对上 = 0**，有差异 = **1**（所以它能当闸门用；`--args=compare` 不会进 `all`）。

---

## 1. 文件格式

```json
{
  "name": "一句话说明这个场景",
  "note": "这些数从哪来（录像 / 表格 / 手算）",

  "attacker": {
    "cid": 1002,
    "level": 80,
    "eidolon": 0,
    "skill": "SKILL",
    "skillLevel": 10,
    "attributes": { "ATTACK": 2000.0, "CRIT_CHANCE": 0.05 }
  },

  "target": {
    "monsterId": 1002011,
    "level": 90,
    "hardLevelGroup": 1,
    "attributes": { "HEALTH": 50000000.0 }
  },

  "crit": false,
  "instance": 0,
  "instances": 4,
  "tolerance": 1e-6,
  "expect": { "base": 629.13942, "final": 264.022858 }
}
```

| 字段 | 必填 | 含义 |
|---|---|---|
| `attacker.cid` / `level` | ✓ | 角色与等级（引擎按真实数据建面板） |
| `attacker.eidolon` | | 星魂 0–6（会真的装上星魂规则） |
| `attacker.skill` | ✓ | `COMMON` / `SKILL` / `ULTRA` / `TALENT` |
| `attacker.skillLevel` | | 施放等级；不写 = 该表的最大行（本库的 Lv10 口径） |
| `attacker.attributes` | | **绝对**属性覆盖，让两边用同一面板（见下） |
| `target.monsterId` / `level` | ✓ | 敌人实例与等级（`monster_config.json` 的键） |
| `target.hardLevelGroup` | | 关卡难度组，默认 1 |
| `target.attributes` | | 同上（常用：`HEALTH` 调大，保证一击打不死） |
| `crit` | | `true` = 这一发必暴击、`false` = 必不暴击、不写 = 交给骰子。**游戏给的数已经含它自己的暴击结论，所以这里必须对齐** |
| `instance` | | 报哪一段（0 基）。1002 的战技分 **4 段** ⇒ 0–3 |
| `instances` | | 期望段数，不符会报一条差异 |
| `tolerance` | | 相对容差，默认 `1e-6` |
| `expect` | | zone 名 → 游戏的值。**留空则只打印引擎自己的数**（就是给你抄格式用的） |

### 为什么需要 `attributes` 覆盖

引擎的面板 = `character_data` + 晋阶 + 行迹，**没有光锥/遗器**。原游戏的数来自某个具体面板，所以比较时必须把那个面板的
`ATTACK` / `CRIT_CHANCE` / `CRIT_ATTACK` / `<元素>_DAMAGE_BOOST` 等**绝对**写进场景，两边才是同一道题。属性名用
`AttributeType` 的枚举名（`ATTACK`、`CRIT_CHANCE`、`WIND_DAMAGE_BOOST`…）。

---

## 2. 乘区对照表（游戏术语 → 引擎 zone）

引擎按这个顺序结算，`breakdown()` 也按这个顺序打印：

| 引擎 zone | 游戏里的那一项 | 说明 |
|---|---|---|
| `base` | 技能倍率 × 攻击力（含欢愉/超击破的折叠） | 进任何乘区之前的值 |
| `BoostArea` | 增伤区 `1 + Σ增伤` | 元素增伤 / 全属性增伤 / 普攻·战技·终结技增伤 / 击破易伤… |
| `FinalBoostArea` | `1 + Level_FinalDamageAddedRatio` | 链末独立因子之一 |
| `ComboArea` | `1 + ComboDamageAddedRatio` | 同上 |
| `BaseMultiArea` | `BaseDamageMultiRatio` | 同上 |
| `MonsterTakenArea` | `1 + Monster_DamageTakenRatio` | 怪物自身承伤 |
| `VulnerableArea` | 易伤区 `1 + Σ易伤`（**上限 3.5**） | |
| `CritArea` | 暴击区 `1 + 暴伤`（暴击时） | 不暴击 = 1.0 |
| `DefenceArea` | 防御区 `攻/(攻+防×(1−穿透))` 一类 | 等级差进这里 |
| `ReductionArea` | 减伤区 `Π(1−r)`（**下限 0.01**） | 韧性减伤 0.9 也在这里 |
| `WeaknessArea` | 虚弱区 `1 − Σ`（**下限 0.2**） | |
| `ResistArea` | 抗性区 `1 − (抗性 − 穿透)` | |
| `absorb` | 元素吸收（**减法**，结算后） | 只有 >0 时出现 |
| `final` | 最终伤害 | |

⚠ `crit` 在引擎里排在 `DefenceArea` **之前**——两者是相乘的，顺序不影响值，但表格的行序就是结算序。

---

## 3. 怎么用

1. 复制一个场景文件，改 `name` / `attacker` / `target`。
2. 先**不写** `expect` 跑一次 ⇒ 打印引擎的全部 zone（格式现成）。
3. 把原游戏的数填进 `expect`（能填几项填几项；只填 `final` 也行）。
4. 再跑一次 ⇒ 逐行 `game / engine / diff / relative`，对不上的行标 `<-- differs`。

⚠ **精度**：`expect` 请填原游戏的**原始精度**。如果游戏只给了 4–6 位小数，`tolerance` 要跟着放到同一量级（默认 `1e-6`，是按“两边都是全精度”设的），否则会看到 `0.0001%` 这种假差异。

**对不上时先看的几件事**：`inputs:` 那行是不是你想要的题（ATK / 倍率 / 敌人 DEF / 暴击结论）；
`settled:` 的段数对不对（多段技能逐段结算，`instance` 选的是其中一段）；
以及 `expect` 的 key 是否拼错（引擎没有的 zone 会直接报 `MISSING` 并列出它所有的 zone 名）。
