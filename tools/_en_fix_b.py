# -*- coding: utf-8 -*-
"""English-pass fixes, batch 2: character tests M-Z, enemies, light cones."""

FIXES = []


def add(path, old, new):
    FIXES.append((path, old, new))


C = "src/test/java/com/laosun/aluminium/test/content/characters/"
L = "src/test/java/com/laosun/aluminium/test/content/lightcones/"

add(C + "March7thKitTest.java", r'''"「解除指定我方单体的1个负面效果」"''',
    r'''"「解除指定我方单体的1个负面效果」 (dispels 1 debuff from a designated single ally)"''')
add(C + "March7thKitTest.java", r'''"「冻结状态下，敌方目标不能行动」"''',
    r'''"「冻结状态下，敌方目标不能行动」 (while Frozen, the enemy target cannot act)"''')
add(C + "March7thKitTest.java", r'''"no amount of 效果命中 beats a specific immunity -- which is why the case above needs a "''',
    r'''"no amount of Effect Hit Rate (效果命中) beats a specific immunity -- which is why the case above needs a "''')
add(C + "March7thKitTest.java", r'''"3 turns from the Skill + 1 from the 加护 trace -- 「战技提供的护盾持续时间增加1回合」"''',
    r'''"3 turns from the Skill + 1 from the Reinforce (加护) trace -- \"the shield the Skill provides lasts 1 turn longer\" (「战技提供的护盾持续时间增加1回合」)"''')
add(C + "March7thKitTest.java", r'''"Eidolon 2's battle-start shield; the two amendments (Eidolon 4 raising the talent's per-turn cap, the 行迹「冰咒」 "''',
    r'''"Eidolon 2's battle-start shield; the two amendments (Eidolon 4 raising the talent's per-turn cap, the Traces (行迹) \"Ice Spell\" (「冰咒」) "''')
add(C + "March7thLevelTest.java", r'''"星魂 2 is not 星魂 3"''',
    r'''"Eidolon (星魂) 2 is not Eidolon 3"''')
add(C + "March7thLevelTest.java", r'''"「终结技等级+2」"''',
    r'''"\"Ultimate Lv. +2\" (「终结技等级+2」)"''')
add(C + "March7thLevelTest.java", r'''"「普攻等级+1」"''',
    r'''"\"Basic ATK Lv. +1\" (「普攻等级+1」)"''')
add(C + "March7thLevelTest.java", r'''"…and 星魂 5 has not landed yet"''',
    r'''"…and Eidolon (星魂) 5 has not landed yet"''')
add(C + "March7thLevelTest.java", r'''"「战技等级+2」"''',
    r'''"\"Skill Lv. +2\" (「战技等级+2」)"''')
add(C + "March7thLevelTest.java", r'''"+9 base and +2 from this 星魂"''',
    r'''"+9 base and +2 from this Eidolon (星魂)"''')
add(C + "March7thLevelTest.java", r'''"…and row 12, which is what 星魂 5's 「天赋等级+2」 buys"''',
    r'''"…and row 12, which is what Eidolon (星魂) 5's \"Talent Lv. +2\" (「天赋等级+2」) buys"''')
add(C + "MishaTest.java", r'''"「同时米沙恢复2.00点能量」"''',
    r'''"「同时米沙恢复2.00点能量」 (and Misha restores 2.00 Energy)"''')
add(C + "MydeiBloodfeudSkillsTest.java", r'''"换入的是**槽 9** 的行（【弑王成王】破韧 60/30 ✓），而原来是槽 2"''',
    r'''"the row swapped in is **slot 9** (Kingslayer Be King (【弑王成王】) toughness 60/30 ✓), where it used to be slot 2"''')
add(C + "MydeiBloodfeudSkillsTest.java", r'''"「消耗 150 点充能」"''',
    r'''"\"consumes 150 points of Charge\" (「消耗 150 点充能」)"''')
add(C + "MydeiBloodfeudSkillsTest.java", r'''"the cast ran 槽 11, which is the row the sentence names"''',
    r'''"the cast ran slot 11 (槽 11), which is the row the sentence names"''')
add(C + "MydeiTest.java", r'''"「使用秘技后…对敌方全体造成等同于万敌80%生命上限的虚数属性伤害」: "''',
    r'''"\"after using the Technique ... deals Imaginary damage to all enemies equal to 80% of Mydei (万敌)'s Max HP\" (「使用秘技后…对敌方全体造成等同于万敌80%生命上限的虚数属性伤害」): "''')
add(C + "MydeiTest.java", r'''"「最多积攒200点」"''',
    r'''"\"accumulates at most 200 points\" (「最多积攒200点」)"''')
add(C + "NatashaHealTest.java", r'''"「立即为指定我方单体回复等同于娜塔莎10.50%生命上限+280的生命值」"''',
    r'''"「立即为指定我方单体回复等同于娜塔莎10.50%生命上限+280的生命值」 (immediately restores HP to a designated single ally equal to 10.50% of Natasha (娜塔莎)'s Max HP + 280)"''')
add(C + "NatashaHealTest.java", r'''"…and a third time, because the 行迹 调理 trace lengthens it by one turn"''',
    r'''"…and a third time, because the Traces (行迹) Recuperation (调理) trace lengthens it by one turn"''')
add(C + "NatashaHealingBoostTest.java", r'''"「娜塔莎提供的治疗量提高10%」 -- "''',
    r'''"\"the healing Natasha (娜塔莎) provides is raised by 10%\" (「娜塔莎提供的治疗量提高10%」) -- "''')
add(C + "PelaDebuffTest.java", r'''"trace 痛击"''',
    r'''"trace Bash (痛击)"''')
add(C + "PelaDebuffTest.java", r'''"with a debuff on the target, 痛击 matches"''',
    r'''"with a debuff on the target, Bash (痛击) matches"''')
add(C + "PelaDebuffTest.java", r'''"census: the 秘策 rule, the level-convention rule, and (2026-09-29) the technique's "''',
    r'''"census: the Secret Strategy (秘策) rule, the level-convention rule, and (2026-09-29) the technique's "''')
add(C + "PhainonTest.java", r'''"「战斗开始时，获得 1 点【火种】」"''',
    r'''"「战斗开始时，获得 1 点【火种】」 (at the start of battle, gains 1 point of Kindling (【火种】))"''')
add(C + "PhainonTest.java", r'''"「为我方队友恢复25点能量」"''',
    r'''"「为我方队友恢复25点能量」 (restores 25 Energy to allied teammates)"''')
add(C + "PreservationTrailblazerTest.java", r'''"「每受到1次攻击，叠加1层【灼热意志】，最多可叠加8层」"''',
    r'''"「每受到1次攻击，叠加1层【灼热意志】，最多可叠加8层」 (every time it is attacked once, gains 1 stack of Magma Will (【灼热意志】), up to 8 stacks)"''')
add(C + "PreservationTrailblazerTest.java", r'''": 「施放战技后，为我方全体提供…6.00%防御力+80的护盾」"''',
    r'''": \"after casting the Skill, provides all allies with a shield of ... 6.00% DEF + 80\" (「施放战技后，为我方全体提供…6.00%防御力+80的护盾」)"''')
add(C + "PreservationTrailblazerTest.java", r'''"「给自身提供…等同于30%防御力+384的护盾，持续1回合」"''',
    r'''"「给自身提供…等同于30%防御力+384的护盾，持续1回合」 (provides itself with a shield equal to 30% DEF + 384 for 1 turn)"''')
add(C + "QingqueTest.java", r'''"「我方目标回合开始时…随机抽取1张」"''',
    r'''"「我方目标回合开始时…随机抽取1张」 (at the start of one of our targets' turns ... draws 1 tile at random)"''')
add(C + "QingqueTest.java", r'''"「进入战斗时青雀会抽取2张琼玉牌」"''',
    r'''"「进入战斗时青雀会抽取2张琼玉牌」 (when entering battle, Qingque draws 2 jade tiles)"''')
add(C + "QingqueTest.java", r'''"「增伤 28%」叠到「最多 4 层」 -- five casts must still read four"''',
    r'''"\"DMG +28%\" (「增伤 28%」) stacking to \"at most 4 stacks\" (「最多 4 层」) -- five casts must still read four"''')
add(C + "RappaTest.java", r'''"「每当敌方目标的弱点被击破时，乱破获得1点充能」"''',
    r'''"「每当敌方目标的弱点被击破时，乱破获得1点充能」 (every time an enemy target's Weakness is Broken, Rappa gains 1 point of Charge)"''')
add(C + "RemembranceTrailblazerTest.java", r'''": 「召唤忆灵迷迷」"''',
    r'''": \"summons the memosprite Mem\" (「召唤忆灵迷迷」)"''')
add(C + "RemembranceTrailblazerTest.java", r'''": 「忆灵迷迷初始拥有130点速度」"''',
    r'''": \"the memosprite Mem starts with 130 SPD\" (「忆灵迷迷初始拥有130点速度」)"''')
add(C + "RobinConcertoTest.java", r'''"「处于【协奏】状态时，知更鸟免疫控制类负面状态」"''',
    r'''"「处于【协奏】状态时，知更鸟免疫控制类负面状态」 (while in the Concerto (【协奏】) state, Robin is immune to Crowd Control debuffs)"''')
add(C + "RobinSummerettoTest.java", r'''"「召唤忆灵「晴空乐手」贝茜」"''',
    r'''"「召唤忆灵「晴空乐手」贝茜」 (summons the memosprite 贝茜 of the Summer Songbirds (「晴空乐手」))"''')
add(C + "RobinSummerettoTest.java", r'''"the character file must declare 气氛值"''',
    r'''"the character file must declare the atmosphere resource (气氛值)"''')
add(C + "RobinSummerettoTest.java", r'''"「上限50点」"''',
    r'''"\"the ceiling is 50 points\" (「上限50点」)"''')
add(C + "RuanMeiTest.java", r'''"「施放战技后阮•梅获得【弦外音】」"''',
    r'''"「施放战技后阮•梅获得【弦外音】」 (after casting the Skill, Ruan Mei gains Overtone (【弦外音】))"''')
add(C + "RuanMeiZoneTest.java", r'''"处于结界中时我方全体全属性抗性穿透提高25.00%"''',
    r'''"\"while inside the zone, all allies gain 25.00% All-Type RES PEN\" (处于结界中时我方全体全属性抗性穿透提高25.00%)"''')
add(C + "RuanMeiZoneTest.java", r'''"结界期间，我方全体造成伤害时无视目标的20%的防御力"''',
    r'''"\"while the zone lasts, all allies ignore 20% of the target's DEF when dealing damage\" (结界期间，我方全体造成伤害时无视目标的20%的防御力)"''')
add(C + "RuanMeiZoneTest.java", r'''"自身每回合开始时结界持续回合数减1 -- so two turns end it"''',
    r'''"\"at the start of each of her own turns, the zone's remaining turns are reduced by 1\" (自身每回合开始时结界持续回合数减1) -- so two turns end it"''')
add(C + "ServalShockTest.java", r'''"「触电状态下，敌方目标每回合开始时受到等同于希露瓦104%攻击力的雷属性持续伤害」"''',
    r'''"「触电状态下，敌方目标每回合开始时受到等同于希露瓦104%攻击力的雷属性持续伤害」 (while Shocked, the enemy target takes Lightning DoT equal to 104% of Serval (希露瓦)'s ATK at the start of every turn)"''')
add(C + "SilverWolfLV999Test.java", r'''1e-9, "行动提前 100%");''',
    r'''1e-9, "action advance 100% (行动提前 100%)");''')
add(C + "SparkleTest.java", r'''"「每消耗 1 点战技点…伤害提高 6.00%」"''',
    r'''"「每消耗 1 点战技点…伤害提高 6.00%」 (for every 1 Skill Point consumed ... DMG is raised by 6.00%)"''')
add(C + "SparkleTest.java", r'''"「并使我方全体获得【谜诡】」"''',
    r'''"「并使我方全体获得【谜诡】」 (and grants all allies Cipher (【谜诡】))"''')
add(C + "SparxieTest.java", r'''"「对敌方全体造成等同于火花50%攻击力的火属性伤害」"''',
    r'''"「对敌方全体造成等同于火花50%攻击力的火属性伤害」 (Fire damage to all enemies equal to 50% of Sparxie (火花)'s ATK)"''')
add(C + "SparxieTest.java", r'''"「并为我方恢复2个战技点」: "''',
    r'''"\"and restores 2 Skill Points to our side\" (「并为我方恢复2个战技点」): "''')
add(C + "SundaySkillTest.java", r'''"「指定我方单体角色…立即行动」"''',
    r'''"「指定我方单体角色…立即行动」 (a designated single ally ... acts immediately)"''')
add(C + "SundaySkillTest.java", r'''"「当星期日对「同谐」命途的角色施放该技能时，无法触发立即行动效果」"''',
    r'''"「当星期日对「同谐」命途的角色施放该技能时，无法触发立即行动效果」 (when Sunday casts this Skill on a character of the Harmony (「同谐」) Path, the immediate-action effect cannot be triggered)"''')
add(C + "SundaySkillTest.java", r'''"only 立即行动 is blocked by the sentence, not 「使其造成的伤害提高」"''',
    r'''"only the immediate action (立即行动) is blocked by the sentence, not the DMG boost (「使其造成的伤害提高」)"''')
add(C + "SundaySkillTest.java", r'''"「持续#3[i]回合」 -- #3 = 2"''',
    r'''"\"lasts #3[i] turns\" (「持续#3[i]回合」) -- #3 = 2"''')
add(C + "SundaySkillTest.java", r'''"「使目标及其召唤物成为【蒙福者】」"''',
    r'''"「使目标及其召唤物成为【蒙福者】」 (makes the target and its summons The Beatified (【蒙福者】))"''')
add(C + "SushangTest.java", r'''"「当场上有敌方目标的弱点被击破，素裳的速度提高20%，持续2回合」: "''',
    r'''"\"when an enemy target's Weakness is Broken on the field, Sushang's SPD is raised by 20% for 2 turns\" (「当场上有敌方目标的弱点被击破，素裳的速度提高20%，持续2回合」): "''')
add(C + "TingyunTest.java", r'''"「为指定我方单体提供【赐福】」"''',
    r'''"「为指定我方单体提供【赐福】」 (provides a designated single ally with [赐福])"''')
add(C + "TingyunTest.java", r'''"「使其攻击力提高50%」: "''',
    r'''"\"raises its ATK by 50%\" (「使其攻击力提高50%」): "''')
add(C + "TingyunTest.java", r'''"「为指定我方单体恢复50点能量」: "''',
    r'''"\"restores 50 Energy to a designated single ally\" (「为指定我方单体恢复50点能量」): "''')
add(C + "TingyunTest.java", r'''"「同时使目标造成的伤害提高50%，持续2回合」"''',
    r'''"「同时使目标造成的伤害提高50%，持续2回合」 (and raises the target's DMG dealt by 50% for 2 turns)"''')
add(C + "TrailblazerDestructionTest.java", r'''"「每次击破敌方目标的弱点后，攻击力提高20%」: "''',
    r'''"\"after each time an enemy target's Weakness is Broken, ATK is raised by 20%\" (「每次击破敌方目标的弱点后，攻击力提高20%」): "''')
add(C + "TrailblazerDestructionTest.java", r'''"the talent's ATK stack and (2026-09-29) the 坚韧 trace's per-layer DEFENCE, both on BREAK"''',
    r'''"the talent's ATK stack and (2026-09-29) the Tenacity (坚韧) trace's per-layer DEFENCE, both on BREAK"''')
add(C + "TrailblazerEidolonTest.java", r'''"precondition: emptying the bar of a weak enemy puts it in 弱点击破"''',
    r'''"precondition: emptying the bar of a weak enemy puts it in Weakness Break (弱点击破)"''')
add(C + "TrailblazerHarmonyTest.java", r'''"「为我方全体附上【伴舞】效果」"''',
    r'''"「为我方全体附上【伴舞】效果」 (applies the Backup Dancer (【伴舞】) effect to all allies)"''')
add(C + "TrailblazerHarmonyTest.java", r'''"「持有【伴舞】的我方目标击破特攻提高30%」"''',
    r'''"「持有【伴舞】的我方目标击破特攻提高30%」 (an ally holding Backup Dancer (【伴舞】) gets 30% more Break Effect)"''')
add(C + "TrailblazerHarmonyTest.java", r'''"「当有敌方目标的弱点被击破时，开拓者立即恢复10点能量」"''',
    r'''"「当有敌方目标的弱点被击破时，开拓者立即恢复10点能量」 (when an enemy target's Weakness is Broken, the Trailblazer immediately restores 10 Energy)"''')
add(C + "TrailblazerSiblingTest.java", r'''"「每次击破敌方目标的弱点后，攻击力提高20%」: "''',
    r'''"\"after each time an enemy target's Weakness is Broken, ATK is raised by 20%\" (「每次击破敌方目标的弱点后，攻击力提高20%」): "''')
add(C + "TrailblazerSiblingTest.java", r'''"最多叠加2层"''',
    r'''"stacks at most 2 times (最多叠加2层)"''')
add(C + "TrailblazerSiblingTest.java", r'''"「回复等同于各自生命上限15%的生命值」"''',
    r'''"「回复等同于各自生命上限15%的生命值」 (restores HP equal to 15% of each one's Max HP)"''')
add(C + "TrailblazerSiblingTest.java", r'''"the talent's ATK stack and (2026-09-29) the 坚韧 trace's per-layer DEFENCE, both on BREAK"''',
    r'''"the talent's ATK stack and (2026-09-29) the Tenacity (坚韧) trace's per-layer DEFENCE, both on BREAK"''')
add(C + "TribbieTest.java", r'''"「使用秘技后，进入战斗时获得【神启】」"''',
    r'''"「使用秘技后，进入战斗时获得【神启】」 (after using the Technique, gains Numinosity (【神启】) on entering battle)"''')
add(C + "WeltTest.java", r'''"「有100%的基础概率使受到攻击的敌方目标陷入禁锢状态，持续1回合」"''',
    r'''"「有100%的基础概率使受到攻击的敌方目标陷入禁锢状态，持续1回合」 (a 100% base chance to put the attacked enemy target into the Imprisonment (禁锢) state for 1 turn)"''')
add(C + "YanqingTest.java", r'''"「并为彦卿附加【智剑连心】」"''',
    r'''"「并为彦卿附加【智剑连心】」 (and applies Soulsteel Sync (【智剑连心】) to Yanqing)"''')
add(C + "YanqingTest.java", r'''"「为自身提高20.00%暴击率」"''',
    r'''"「为自身提高20.00%暴击率」 (raises his own CRIT Rate by 20.00%)"''')
add(C + "YanqingTest.java", r'''"「和30%暴击伤害」"''',
    r'''"「和30%暴击伤害」 (and 30% CRIT DMG)"''')
add(C + "YanqingTest.java", r'''"「若彦卿处于【智剑连心】效果，则使其暴击伤害额外提高50%」"''',
    r'''"「若彦卿处于【智剑连心】效果，则使其暴击伤害额外提高50%」 (if Yanqing has the Soulsteel Sync (【智剑连心】) effect, his CRIT DMG is raised by an extra 50%)"''')
add(C + "YaoGuangTest.java", r'''"[1502] 笑点 on the battle: "''',
    r'''"[1502] Punchline (笑点) on the battle: "''')
add(C + "YukongCommandTest.java", r'''"「gains 2 layers of [鸣弦号令]」"''',
    r'''"「gains 2 layers of [鸣弦号令] (Roaring Bowstrings)」"''')
add(C + "YukongCommandTest.java", r'''"「on the turn Yukong casts the Skill and gains [鸣弦号令], it is not removed」"''',
    r'''"「on the turn Yukong casts the Skill and gains [鸣弦号令] (Roaring Bowstrings), it is not removed」"''')
add(C + "YunliTest.java", r'''"「额外恢复15点能量」"''',
    r'''"「额外恢复15点能量」 (restores 15 extra Energy)"''')
add("src/test/java/com/laosun/aluminium/test/content/enemies/RandomEnemySelectorTest.java",
    r'''"as 欢愉伤容"''', r'''"as Elation DMG (欢愉伤容)"''')
add(L + "AlongThePassingShoreTest.java", r'''"击中敌方目标时使敌方陷入【泡影】"''',
    r'''"on hitting an enemy target, puts the enemy into Mirage Fizzle (【泡影】)"''')
add(L + "BoundlessChoreoTest.java", r'''"防御降低 is one of them"''',
    r'''"DEF Reduction (防御降低) is one of them"''')
add(L + "BoundlessChoreoTest.java", r'''"或 减速 is the other"''',
    r'''"or Slow (减速) is the other"''')
add(L + "ButTheBattleIsnTOverTest.java", r'''"对我方目标 (false case)"''',
    r'''"our target (对我方目标), the false case"''')
add(L + "ChorusTest.java", r'''"我方全体 includes the wearer"''',
    r'''"all allies (我方全体) includes the wearer"''')
add(L + "DazzledByAFloweryWorldPushTest.java", r'''"the turn boundary clears the counter -- 同一回合内"''',
    r'''"the turn boundary clears the counter -- within the same turn (同一回合内)"''')
add(L + "DreamvilleAdventureTest.java", r'''"no cone, no 童心 (false case)"''',
    r'''"no cone, no Childlike Heart (童心) -- the false case"''')
add(L + "EarthlyEscapadeTest.java", r'''"队友 crit rate +10%"''',
    r'''"ally (队友) crit rate +10%"''')
add(L + "EarthlyEscapadeTest.java", r'''"队友 crit damage +28%"''',
    r'''"ally (队友) crit damage +28%"''')
add(L + "EarthlyEscapadeTest.java", r'''"the wearer is NOT its own 队友 (false case)"''',
    r'''"the wearer is NOT its own ally (队友) -- the false case"''')
add(L + "EarthlyEscapadeTest.java", r'''"a SINGLE restore of 2 points is TWO more layers -- 每恢复 1 个"''',
    r'''"a SINGLE restore of 2 points is TWO more layers -- one per point restored (每恢复 1 个)"''')
add(L + "ElationBrimmingWithBlessingsTest.java", r'''"an enemy is not 我方单体角色 (false case)"''',
    r'''"an enemy is not a single ally (我方单体角色) -- the false case"''')
add(L + "FlickeringStarsDefenceIgnoreTest.java", r'''"and so do the ally's -- the panel covers 我方全体"''',
    r'''"and so do the ally's -- the panel covers all allies (我方全体)"''')
add(L + "FlickeringStarsSkillDamageTest.java", r'''"the basic attack is NOT a 战技 -- nothing for it"''',
    r'''"the basic attack is NOT a Skill (战技) -- nothing for it"''')
add(L + "FlickeringStarsTest.java", r'''"我方任意角色 includes the wearer"''',
    r'''"any ally (我方任意角色) includes the wearer"''')
add(L + "IShallBeMyOwnSwordTest.java", r'''"最多叠加 3 层"''',
    r'''"at most 3 stacks (最多叠加 3 层)"''')
add(L + "IShallBeMyOwnSwordTest.java", r'''"施放攻击后解除"''',
    r'''"removed after casting an attack (施放攻击后解除)"''')
add(L + "MakeFarewellsMoreBeautifulTest.java", r'''"the MEMOSPRITE’s loss alone grants 冥花 (actor == summon)"''',
    r'''"the MEMOSPRITE’s loss alone grants Death Flower (冥花) (actor == summon)"''')
add(L + "NinjutsuInscriptionDazzlingEvilbreakerTest.java", r'''"普攻 only"''',
    r'''"Basic ATK (普攻) only"''')
add(L + "PatienceIsAllYouNeedTest.java", r'''"[23006] after one hit: has 游丝="''',
    r'''"[23006] after one hit: has Erode (游丝)="''')
add(L + "PatienceIsAllYouNeedTest.java", r'''" has 触电="''',
    r'''" has Shock (触电)="''')
add(L + "PatienceIsAllYouNeedTest.java", r'''"and to 触电 through the element table -- the alias costs nothing"''',
    r'''"and to Shock (触电) through the element table -- the alias costs nothing"''')
add(L + "PatienceIsAllYouNeedTest.java", r'''"[23006] unnamed thunder DOT: has 游丝="''',
    r'''"[23006] unnamed thunder DOT: has Erode (游丝)="''')
add(L + "PatienceIsAllYouNeedTest.java", r'''"an unnamed thunder DOT is still 触电 -- the element path is untouched"''',
    r'''"an unnamed thunder DOT is still Shock (触电) -- the element path is untouched"''')
add(L + "PatienceIsAllYouNeedTest.java", r'''"but it is NOT 游丝: the name is what distinguishes them"''',
    r'''"but it is NOT Erode (游丝): the name is what distinguishes them"''')
add(L + "PatienceIsAllYouNeedTest.java", r'''"[23006] without the cone: has 游丝="''',
    r'''"[23006] without the cone: has Erode (游丝)="''')
add(L + "PatienceIsAllYouNeedTest.java", r'''"thunder is what makes 触电 true"''',
    r'''"thunder is what makes Shock (触电) true"''')
add(L + "PlanetaryRendezvousTest.java", r'''"我方目标 includes the wearer"''',
    r'''"our target (我方目标) includes the wearer"''')
add(L + "ReforgedRemembranceTest.java", r'''"灼烧 gives one layer"''',
    r'''"Burn (灼烧) gives one layer"''')
add(L + "ReforgedRemembranceTest.java", r'''"触电 adds a second"''',
    r'''"Shock (触电) adds a second"''')
add(L + "ReminiscenceTest.java", r'''"分别获得 1 层: the wearer"''',
    r'''"1 stack each (分别获得 1 层): the wearer"''')
add(L + "ReminiscenceTest.java", r'''"忆灵的回合 (false case)"''',
    r'''"the memosprite's turn (忆灵的回合) -- the false case"''')
add(L + "ReminiscenceTest.java", r'''0, after, "忆灵消失时移除");''',
    r'''0, after, "removed when the memosprite disappears (忆灵消失时移除)");''')
add(L + "ReminiscenceTest.java", r'''"最多叠加 4 层"''',
    r'''"at most 4 stacks (最多叠加 4 层)"''')
add(L + "SummerRidesTheSurfSkillPointTest.java", r'''"[23064] after two non-elation casts: 欢愉技计数="''',
    r'''"[23064] after two non-elation casts: Elation Skill (欢愉技) counter = "''')
add(L + "SummerRidesTheSurfTest.java", r''') has 风口="''',
    r''') has Updraft (风口)="''')
add(L + "SwordplayTest.java", r'''"最多叠加 5 层"''',
    r'''"at most 5 stacks (最多叠加 5 层)"''')
add(L + "TextureOfMemoriesTest.java", r'''"未持有护盾 is the gate (false case)"''',
    r'''"not holding a shield (未持有护盾) is the gate -- the false case"''')
add(L + "TheDayTheCosmosFellTest.java", r'''"one is not 不少于２个"''',
    r'''"one is not \"no fewer than 2\" (不少于２个)"''')
add(L + "ThisIsMeTest.java", r'''"终结技 only (false case)"''',
    r'''"Ultimate (终结技) only -- the false case"''')
add(L + "ThisLoveForeverTest.java", r'''"对我方单体 gives 空白"''',
    r'''"a single ally (对我方单体) gives Blank (空白)"''')
add(L + "ThisLoveForeverTest.java", r'''"对敌方 gives 诗行"''',
    r'''"an enemy (对敌方) gives Verse (诗行)"''')
add(L + "TimeWovenIntoGoldTest.java", r'''"最多叠加 6 层"''',
    r'''"at most 6 stacks (最多叠加 6 层)"''')
add(L + "TomorrowTogetherTest.java", r'''"the wearer is part of 我方全体"''',
    r'''"the wearer is part of all allies (我方全体)"''')
add(L + "UntilTheFlowersBloomAgainTest.java", r'''"the sentence says 受到的伤害 -- 15% on EVERY damage type, not elation only"''',
    r'''"the sentence says DMG taken (受到的伤害) -- 15% on EVERY damage type, not elation only"''')
add(L + "UntilTheFlowersBloomAgainTest.java", r'''"no damage type -- the sentence says 受到的伤害"''',
    r'''"no damage type -- the sentence says DMG taken (受到的伤害)"''')
add(L + "VictoryInABlinkTest.java", r'''"装备者的忆灵, not the wearer (false case)"''',
    r'''"the wearer's memosprite (装备者的忆灵), not the wearer -- the false case"''')
add(L + "VictoryInABlinkTest.java", r'''"对我方目标 (false case)"''',
    r'''"our target (对我方目标), the false case"''')
add(L + "WelcomeToTheCosmicCityLaughterTest.java", r'''" -> 普攻计数="''',
    r'''" -> Basic ATK (普攻) counter = "''')
add(L + "WelcomeToTheCosmicCityLaughterTest.java", r'''"[23057] a Skill used as an attack: 普攻计数="''',
    r'''"[23057] a Skill used as an attack: Basic ATK (普攻) counter = "''')
add(L + "WhereaboutsShouldDreamsRestTest.java", r'''"造成击破伤容时 collapses the target"''',
    r'''"Break DMG (造成击破伤容) collapses the target"''')
add(L + "WhyDoesTheOceanSingTest.java", r''': the 魂迷 rule"''',
    r''': the Enthrallment (魂迷) rule"''')
add(L + "WoofWalkTimeTest.java", r'''"灼烧 (burning)"''',
    r'''"Burn (灼烧)"''')
add(L + "WoofWalkTimeTest.java", r'''"裂伤 (wounded)"''',
    r'''"Bleed (裂伤)"''')
