# -*- coding: utf-8 -*-
"""English-pass fixes, batch 1: main sources + character tests A-L.

Convention (already in the tree, see HookTest):
  * a game-text quote keeps its Chinese characters and gains an English gloss
    in parens beside it;
  * a bare Chinese term in English prose becomes "English (中文)", using
    tools/content_names.tsv for the official English wording.
"""

FIXES = []


def add(path, old, new):
    FIXES.append((path, old, new))


M = "src/main/java/com/laosun/Main.java"
C = "src/test/java/com/laosun/aluminium/test/content/characters/"

add(M, r'''"[1] Break control states — 冰 锁行动 / 量子·虚数 减速 + 推条"''',
    r'''"[1] Break control states — Ice (冰) locks the action / Quantum (量子)·Imaginary (虚数) slow + push the bar"''')
add(M, r'''"    → only 冰 stopped the victim acting; all three pushed the bar further than the"''',
    r'''"    → only Ice (冰) stopped the victim acting; all three pushed the bar further than the"''')

add(C + "AcheronTest.java", r'''"「获得1点【残梦】」"''',
    r'''"「获得1点【残梦】」 (gains 1 point of [残梦])"''')
add(C + "ArgentiStacksTest.java", r'''"「回合开始时，立即获得1层【升格】」"''',
    r'''"「回合开始时，立即获得1层【升格】」 (at the start of the turn, immediately gains 1 stack of Apotheosis (【升格】))"''')
add(C + "ArlanTest.java", r'''"「对指定敌方单体造成…雷属性伤害」"''',
    r'''"「对指定敌方单体造成…雷属性伤害」 (deals ... Lightning damage to a designated single enemy)"''')
add(C + "ArlanTest.java", r'''"「立即回复等同于自身生命上限20%的生命值」: "''',
    r'''"「立即回复等同于自身生命上限20%的生命值」 (immediately restores HP equal to 20% of his own Max HP): "''')
add(C + "ArlanTest.java", r'''"「抵抗持续伤害类负面状态的概率提高50%」"''',
    r'''"「抵抗持续伤害类负面状态的概率提高50%」 (raises RES to DoT Debuffs by 50%)"''')
add(C + "AstaTest.java", r'''"「对敌方全体目标造成等同于艾丝妲50%攻击力的火属性伤害」"''',
    r'''"「对敌方全体目标造成等同于艾丝妲50%攻击力的火属性伤害」 (Fire damage to all enemies equal to 50% of Asta (艾丝妲)'s ATK)"''')
add(C + "AventurineTest.java", r'''"「使指定敌方单体陷入【惊惶】状态，持续3回合」"''',
    r'''"「使指定敌方单体陷入【惊惶】状态，持续3回合」 (puts a designated single enemy into the Unnerved (【惊惶】) state for 3 turns)"''')
add(C + "AventurineWaveflairLaughterTest.java", r'''"\"gain 4 笑点\""''',
    r'''"\"gain 4 笑点\" (gain 4 Punchline)"''')
add(C + "AventurineWaveflairLaughterTest.java", r'''"\"gain 6 笑点\""''',
    r'''"\"gain 6 笑点\" (gain 6 Punchline)"''')
add(C + "AventurineWaveflairLaughterTest.java", r'''"\"and 1 笑点\""''',
    r'''"\"and 1 笑点\" (and 1 more Punchline)"''')
add(C + "AventurineWaveflairTest.java", r'''"「队友施放攻击后，砂金•戏浪获得1点【热意】」"''',
    r'''"「队友施放攻击后，砂金•戏浪获得1点【热意】」 (after an ally casts an attack, Aventurine Waveflair gains 1 point of [热意])"''')
add(C + "AventurineWaveflairTest.java", r'''"「获得8点【热意】」"''',
    r'''"「获得8点【热意】」 (gains 8 points of [热意])"''')
add(C + "BailuRegenTest.java", r'''"「for our targets that do not have [生息] ... apply [生息]」"''',
    r'''"「for our targets that do not have [生息] (Invigoration) ... apply [生息] (Invigoration)」"''')
add(C + "BlackSwanTest.java", r'''"「使目标…陷入1层【奥迹】」"''',
    r'''"「使目标…陷入1层【奥迹】」 (puts the target ... into 1 stack of [奥迹])"''')
add(C + "BladeTest.java", r'''"「进入【地狱变】状态」"''',
    r'''"「进入【地狱变】状态」 (enters the Hellscape (【地狱变】) state)"''')
add(C + "BoothillEidolonTest.java", r'''"when 波提欧 deals damage, ignore the enemy target's "''',
    r'''"when Boothill (波提欧) deals damage, ignore the enemy target's "''')
add(C + "BoothillTest.java", r'''"「弱点被击破后…获得1层【优势口袋】」"''',
    r'''"「弱点被击破后…获得1层【优势口袋】」 (after the Weakness is Broken ... gains 1 stack of Pocket Trickshot (【优势口袋】))"''')
add(C + "CastoriceTest.java", r'''"「造成的伤害提高20%」"''',
    r'''"「造成的伤害提高20%」 (DMG dealt is raised by 20%)"''')
add(C + "CerydraTest.java", r'''"「使指定我方单体角色获得【军功】」"''',
    r'''"「使指定我方单体角色获得【军功】」 (grants a designated single ally Military Merit (【军功】))"''')
add(C + "CerydraTest.java", r'''"「并使刻律德菈获得1点充能」"''',
    r'''"「并使刻律德菈获得1点充能」 (and Cerydra (刻律德菈) gains 1 point of Charge)"''')
add(C + "ClaraTraceTest.java", r'''"the burn is a negative effect, so 解除 (dispelling) takes it"''',
    r'''"the burn is a negative effect, so dispelling (解除) takes it"''')
add(C + "ClaraTraceTest.java", r'''"「抵抗控制类负面状态的概率提高35%」"''',
    r'''"「抵抗控制类负面状态的概率提高35%」 (raises RES to Crowd Control debuffs by 35%)"''')
add(C + "CyreneFutureSpendTest.java", r'''" ; 昔涟's 【追忆】 "''',
    r'''" ; Cyrene (昔涟)'s Recollection (【追忆】) "''')
add(C + "CyreneFutureTest.java", r'''" ; 昔涟 herself = "''',
    r'''" ; Cyrene (昔涟) herself = "''')
add(C + "CyreneFutureTest.java", r'''"and 昔涟 does not, because the text says 其他"''',
    r'''"and Cyrene (昔涟) does not, because the text says other allies (其他)"''')
add(C + "CyreneTest.java", r'''"「获得1点【追忆】」"''',
    r'''"「获得1点【追忆】」 (gains 1 point of Recollection (【追忆】))"''')
add(C + "CyreneTest.java", r'''"「昔涟在场时，我方全体目标造成的伤害提高20.00%」"''',
    r'''"「昔涟在场时，我方全体目标造成的伤害提高20.00%」 (while Cyrene is on the field, all allies deal 20.00% more DMG)"''')
add(C + "DanHengTest.java", r'''"「当丹恒成为我方技能的施放目标时，下一次攻击的风属性抗性穿透提高36%」: "''',
    r'''"「当丹恒成为我方技能的施放目标时，下一次攻击的风属性抗性穿透提高36%」 (when Dan Heng becomes the target of one of our Skills, his next attack has 36% more Wind RES PEN): "''')
add(C + "DrRatioTest.java", r'''"「理真医生对该目标发动1次天赋的追加攻击」: "''',
    r'''"「理真医生对该目标发动1次天赋的追加攻击」 (Dr. Ratio launches 1 follow-up attack from his Talent at that target): "''')
add(C + "EarthOdeMarksDanHengTest.java", r'''"cast at him, he gains 献予「大地」之诗"''',
    r'''"cast at him, he gains the poem offered to Earth (献予「大地」之诗)"''')
add(C + "EvanesciaTest.java", r'''" -> 欢愉度 (Elation)="''',
    r'''" -> Elation (欢愉度)="''')
add(C + "EvanesciaTest.java", r'''") ; 好活当赏 (gifts)="''',
    r'''") ; Certified Banger gifts (好活当赏)="''')
add(C + "EvernightTalentTest.java", r'''"precondition: the 忆灵 (memosprite) landed both hits"''',
    r'''"precondition: the memosprite (忆灵) landed both hits"''')
add(C + "EvernightTalentTest.java", r'''"precondition: the 忆灵 (memosprite) is out"''',
    r'''"precondition: the memosprite (忆灵) is out"''')
add(C + "FeixiaoTest.java", r'''"「发动此攻击时使自身造成的伤害提高60%」"''',
    r'''"「发动此攻击时使自身造成的伤害提高60%」 (this attack raises her own DMG dealt by 60%)"''')
add(C + "FireflyTest.java", r'''"「使自身下一次行动提前25%」: "''',
    r'''"「使自身下一次行动提前25%」 (advances her own next action by 25%): "''')
add(C + "FireflyTest.java", r'''"「自身行动提前100%」: "''',
    r'''"「自身行动提前100%」 (advances her own action by 100%): "''')
add(C + "FuXuanTest.java", r'''"「处于【穷观阵】的我方全体获得【鉴知】」"''',
    r'''"「处于【穷观阵】的我方全体获得【鉴知】」 (all allies inside the Matrix of Prescience (【穷观阵】) gain Knowledge (【鉴知】))"''')
add(C + "FuXuanTest.java", r'''"「暴击率提高12.00%」"''',
    r'''"「暴击率提高12.00%」 (CRIT Rate is raised by 12.00%)"''')
add(C + "FugueTest.java", r'''"「使指定我方单体获得【狐祈】」"''',
    r'''"「使指定我方单体获得【狐祈】」 (grants a designated single ally [狐祈])"''')
add(C + "FugueTest.java", r'''"「持有【狐祈】的我方目标，击破特攻提高30%」"''',
    r'''"「持有【狐祈】的我方目标，击破特攻提高30%」 (an ally holding [狐祈] gets 30% more Break Effect)"''')
add(C + "FugueTest.java", r'''"「进入战斗后忘归人行动提前40%」: "''',
    r'''"「进入战斗后忘归人行动提前40%」 (after entering battle, Fugue (忘归人) advances her action by 40%): "''')
add(C + "GallagherTest.java", r'''"「进入战斗后使敌方全体陷入【酩酊】状态，持续2回合」"''',
    r'''"「进入战斗后使敌方全体陷入【酩酊】状态，持续2回合」 (after entering battle, puts all enemies into the Besotted (【酩酊】) state for 2 turns)"''')
add(C + "GallagherTest.java", r'''"「使敌方全体陷入【酩酊】状态」 and the 行迹 天然酵母 (Natural Yeast) trace's 「行动提前100%」"''',
    r'''"\"puts all enemies into the Besotted state\" (「使敌方全体陷入【酩酊】状态」) and the Traces (行迹) Natural Yeast (天然酵母) trace's \"advances the action by 100%\" (「行动提前100%」)"''')
add(C + "GepardFreezeTest.java", r'''"「有65%的基础概率使受到攻击的敌方目标陷入冻结状态」 + 星魂 1 的 +35% ⇒ 100% base chance"''',
    r'''"\"a 65% base chance to put the attacked enemy target into the Frozen state\" (「有65%的基础概率使受到攻击的敌方目标陷入冻结状态」) + the +35% from Eidolon (星魂) 1 ⇒ 100% base chance"''')
add(C + "GepardFreezeTest.java", r'''"「冻结状态下，敌方目标不能行动同时每回合开始时受到等同于杰帕德60%攻击力的冰属性附加伤害」"''',
    r'''"「冻结状态下，敌方目标不能行动同时每回合开始时受到等同于杰帕德60%攻击力的冰属性附加伤害」 (while Frozen, the enemy target cannot act and takes additional Ice DMG equal to 60% of Gepard (杰帕德)'s ATK at the start of every turn)"''')
add(C + "GepardKitTest.java", r'''"「提高等同于自身当前防御力35%的攻击力，每回合开始时刷新」"''',
    r'''"「提高等同于自身当前防御力35%的攻击力，每回合开始时刷新」 (raises ATK by an amount equal to 35% of his current DEF, refreshed at the start of every turn)"''')
add(C + "GepardKitTest.java", r'''"the 刚正 trace's aggro ratio (2026-09-29)"''',
    r'''"the Integrity (刚正) trace's aggro ratio (2026-09-29)"''')
add(C + "GepardKitTest.java", r'''"「傑帕德被敌方攻击的概率提高」 "''',
    r'''"\"Gepard is more likely to be attacked by enemies\" (「傑帕德被敌方攻击的概率提高」) "''')
add(C + "HanyaBurdenTest.java", r'''"Eidolon 1 (the carrier\'s kill advances her) and the 行迹 幽府 (Netherworld) trace (a kill on a 【承负】 target refunds one more)"''',
    r'''"Eidolon 1 (the carrier\'s kill advances her) and the Traces (行迹) Netherworld (幽府) trace (a kill on a Burden (【承负】) target refunds one more)"''')
add(C + "HanyaBurdenTest.java", r'''"the 承负 (Burden) application and Eidolon 2's speed boost"''',
    r'''"the Burden (承负) application and Eidolon 2's speed boost"''')
add(C + "HanyaKitTest.java", r'''"「持有终结技效果的我方目标消灭敌方目标时，寒鸦行动提前15%」 ("''',
    r'''"\"when an ally holding the Ultimate's effect kills an enemy target, Hanya advances her action by 15%\" (「持有终结技效果的我方目标消灭敌方目标时，寒鸦行动提前15%」) ("''')
add(C + "HanyaKitTest.java", r'''"「施放战技后，速度提高20%，持续1回合」"''',
    r'''"「施放战技后，速度提高20%，持续1回合」 (after casting the Skill, SPD is raised by 20% for 1 turn)"''')
add(C + "HanyaKitTest.java", r'''"…「普攻等级+1」"''',
    r'''"…\"Basic ATK Lv. +1\" (「普攻等级+1」)"''')
add(C + "HertaTest.java", r'''"「抵抗控制类负面状态的概率提高35%」"''',
    r'''"「抵抗控制类负面状态的概率提高35%」 (raises RES to Crowd Control debuffs by 35%)"''')
add(C + "HertaTheTest.java", r'''"「使大黑塔攻击力提高80%，持续3回合」"''',
    r'''"「使大黑塔攻击力提高80%，持续3回合」 (raises The Herta's ATK by 80% for 3 turns)"''')
add(C + "HertaTheTest.java", r'''"「获得1层【灵感】」"''',
    r'''"「获得1层【灵感】」 (gains 1 stack of [灵感])"''')
add(C + "HertaTheTest.java", r'''"「【灵感】最多持有4层」"''',
    r'''"「【灵感】最多持有4层」 ([灵感] can be held up to 4 stacks)"''')
add(C + "HimekoChargeTest.java", r'''"「战斗开始时获得1点充能」"''',
    r'''"「战斗开始时获得1点充能」 (gains 1 point of Charge at the start of battle)"''')
add(C + "HimekoChargeTest.java", r'''"「并消耗全部充能」"''',
    r'''"「并消耗全部充能」 (and consumes all of the Charge)"''')
add(C + "HimekoChargeTest.java", r'''"「当有敌方目标的弱点被击破时」 (anybody's break) and 星魂 4's 「施放战技…造成弱点击破时」"''',
    r'''"\"when an enemy target's Weakness is Broken\" (「当有敌方目标的弱点被击破时」) (anybody's break) and Eidolon (星魂) 4's \"when casting the Skill ... causing a Weakness Break\" (「施放战技…造成弱点击破时」)"''')
add(C + "HimekoChargeTest.java", r'''"「当我方目标施放攻击后」"''',
    r'''"\"after one of our targets casts an attack\" (「当我方目标施放攻击后」)"''')
add(C + "HimekoChargeTest.java", r'''"星魂 4's charge rides on BREAK with `from_skill SKILL`, not on a cast event of its own"''',
    r'''"Eidolon (星魂) 4's charge rides on BREAK with `from_skill SKILL`, not on a cast event of its own"''')
add(C + "HimekoKillEnergyTest.java", r'''"姬子 gains nothing from a kill she did not cause -- the general credit goes to the killer"''',
    r'''"Himeko (姬子) gains nothing from a kill she did not cause -- the general credit goes to the killer"''')
add(C + "HimekoNovaTest.java", r'''"「姬子•启行获得【领航旗语】」"''',
    r'''"「姬子•启行获得【领航旗语】」 (Himeko - Nova gains Navigator's Semaphore (【领航旗语】))"''')
add(C + "HuohuoTest.java", r'''"「施放战技后藿藿获得【禳命】」"''',
    r'''"「施放战技后藿藿获得【禳命】」 (after casting the Skill, Huohuo gains Divine Provision (【禳命】))"''')
add(C + "HyacineTest.java", r'''"「风堇进入【雨过天晴】状态」"''',
    r'''"「风堇进入【雨过天晴】状态」 (Hyacine enters the After Rain (【雨过天晴】) state)"''')
add(C + "JadeEidolonThresholdTest.java", r'''"【当品】叠加至15层时，暴击率提高18%"''',
    r'''"at 15 stacks of Pawned Asset (【当品】), CRIT Rate is raised by 18%"''')
add(C + "JiaoqiuTest.java", r'''"「也会被视为同时陷入了灼烧状态」 -- a Fire DotBuff IS 灼烧 by the engine's own translation"''',
    r'''"\"is also considered to be in the Burn state at the same time\" (「也会被视为同时陷入了灼烧状态」) -- a Fire DotBuff IS Burn (灼烧) by the engine's own translation"''')
add(C + "JiaoqiuTest.java", r'''"「对敌方全体造成等同于椒丘100%攻击力的火属性伤害」"''',
    r'''"「对敌方全体造成等同于椒丘100%攻击力的火属性伤害」 (Fire damage to all enemies equal to 100% of Jiaoqiu (椒丘)'s ATK)"''')
add(C + "JiaoqiuTest.java", r'''"并施加1层【烬煨】"''',
    r'''"and applies 1 stack of Ashen Roast (【烬煨】)"''')
add(C + "JingliuTest.java", r'''"「并获得1层【朔望】」"''',
    r'''"「并获得1层【朔望】」 (and gains 1 stack of [朔望])"''')
add(C + "JingliuTest.java", r'''"「当拥有2层【朔望】时…使行动提前100%」: "''',
    r'''"「当拥有2层【朔望】时…使行动提前100%」 (at 2 stacks of [朔望] ... advances her action by 100%): "''')
add(C + "KafkaShockDurationTest.java", r'''"触电状态的持续时间增加1回合 -- two turns plus the trace's one"''',
    r'''"the Shock (触电) state lasts 1 turn longer -- two turns plus the trace's one"''')
add(C + "KafkaTest.java", r'''"「有100%的基础概率使受到攻击的敌方目标陷入触电状态」"''',
    r'''"「有100%的基础概率使受到攻击的敌方目标陷入触电状态」 (a 100% base chance to put the attacked enemy target into the Shock (触电) state)"''')
add(C + "LukaTest.java", r'''"「战斗开始时，卢卡持有1层【斗志】」"''',
    r'''"「战斗开始时，卢卡持有1层【斗志】」 (at the start of battle, Luka holds 1 stack of Fighting Will (【斗志】))"''')
add(C + "LukaTest.java", r'''"「施放普攻【直冲拳】…后，获得1层【斗志】」"''',
    r'''"「施放普攻【直冲拳】…后，获得1层【斗志】」 (after casting the Basic ATK [直冲拳] ... gains 1 stack of Fighting Will (【斗志】))"''')
add(C + "LukaTest.java", r'''"⚠ exactly one: without `from_skill` the 普攻 and 战技 rules would fire on the ultimate as well, "''',
    r'''"⚠ exactly one: without `from_skill` the Basic ATK (普攻) and Skill (战技) rules would fire on the ultimate as well, "''')
add(C + "LukaTest.java", r'''"「使目标陷入裂伤状态」"''',
    r'''"「使目标陷入裂伤状态」 (puts the target into the Bleed (裂伤) state)"''')
add(C + "LukaTest.java", r'''"⚠ precondition: 槽位 8 的数据必须真的在（第 113 轮的教训）"''',
    r'''"⚠ precondition: slot 8's data (槽位 8) has to really be there (the lesson of round 113)"''')
add(C + "LukaTest.java", r'''"the trace's REMOVE_BUFF and the Skill's 裂伤 DOT"''',
    r'''"the trace's REMOVE_BUFF and the Skill's Bleed (裂伤) DOT"''')
add(C + "LuochaTest.java", r'''"「并使罗刹获得1层【白花之刻】」"''',
    r'''"「并使罗刹获得1层【白花之刻】」 (and Luocha (罗刹) gains 1 stack of Abyss Flower (【白花之刻】))"''')
add(C + "LynxTest.java", r'''"「附上【求生反应】」"''',
    r'''"「附上【求生反应】」 (applies Survival Response (【求生反应】))"''')
add(C + "LynxTest.java", r'''"「提高等同于玲可7.50%生命上限+200的生命上限」"''',
    r'''"「提高等同于玲可7.50%生命上限+200的生命上限」 (raises Max HP by an amount equal to 7.50% of Lynx (玲可)'s Max HP + 200)"''')
