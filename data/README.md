# 技能表达法

## 属性

ice 冰

fire 火

wind 风

physics 物理

thunder 雷

quantum 量子

imaginary 虚数

## 基础数据

name: str / translatable - 敌对物种/角色的名称/翻译条例

attack: float - 敌对物种/角色的基础攻击力 (白值)

defensive: float - 敌对物种/角色的基础防御力 (白值)

health: float - 敌对物种/角色的基础生命值 (白值)

speed: int - 敌对物种/角色的基础速度

rd: int - 敌对物种的韧度条长度

crit_attack: float - 敌对物种/角色的基础暴击伤害 (敌对物种基础暴击率为 0, 目前游戏中 (1.6版本) 尚未出现敌对物种暴击率提高的增益效果,
但不排除后续版本出现于游戏中)

skill: dict - 敌对物种/角色的技能

- ultra: skill - 敌对物种/角色的终结技
- talent: skill - 敌对物种/角色的战技
- common: skill - 敌对物种/角色的普攻

## 技能

aluminium:attack - 对我方单体进行攻击

- attribute: str - 指定攻击属性
- value: value provider - 指定攻击伤害值
- energy: int - 指定被攻击的角色回复的能量值

## 数值提供器

aluminium:level_times_related - 根据行迹等级进行对有关值的乘区计算

- values: list[float] - 行迹增长表
- related: str / argument - 进行乘区计算的有关值

aluminium:exact_times_related - 根据确定值进行对有关值的乘区计算

- value: float - 乘区的确定值
- related: str / argument - 进行乘区计算的有关值


