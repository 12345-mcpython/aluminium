# -*- coding: utf-8 -*-
"""English-pass fixes, batch 3: memosprites, relics, data, engine, trigger tests."""

FIXES = []


def add(path, old, new):
    FIXES.append((path, old, new))


C = "src/test/java/com/laosun/aluminium/test/content/characters/"
MS = "src/test/java/com/laosun/aluminium/test/content/memosprites/"
D = "src/test/java/com/laosun/aluminium/test/data/"
E = "src/test/java/com/laosun/aluminium/test/engine/"
T = "src/test/java/com/laosun/aluminium/test/trigger/"

add(C + "ArgentiStacksTest.java",
    r'''"勇气 (the target's HP percentage -- no per-target vocabulary needed, because the condition's subject "''',
    r'''"the trace Courage (勇气): the target's HP percentage -- no per-target vocabulary needed, because the condition's subject "''')

add(MS + "AglaeaMemospriteTest.java", r'''"「回复等同于其 50% 生命上限的生命值」"''',
    r'''"「回复等同于其 50% 生命上限的生命值」 (restores HP equal to 50% of its Max HP)"''')
add(MS + "AglaeaMemospriteTest.java", r'''"「若衣匠不在场，则召唤忆灵衣匠」"''',
    r'''"「若衣匠不在场，则召唤忆灵衣匠」 (if the Garmentmaker is not on the field, summons the memosprite Garmentmaker (忆灵衣匠))"''')
add(MS + "AglaeaMemospriteTest.java", r'''"「并使自身立即行动」"''',
    r'''"「并使自身立即行动」 (and makes herself act immediately)"''')
add(MS + "AglaeaMemospriteTest.java", r'''"「召唤忆灵衣匠」"''',
    r'''"「召唤忆灵衣匠」 (summons the memosprite Garmentmaker (忆灵衣匠))"''')
add(MS + "AglaeaMemospriteTest.java", r'''"「回复至上限」"''',
    r'''"「回复至上限」 (restores HP to the maximum)"''')
add(MS + "AglaeaMemospriteTest.java", r'''"「使自身」"''',
    r'''"\"makes herself\" (「使自身」)"''')
add(MS + "AglaeaMemospriteTest.java", r'''"and the stance is ANCHORED to it, which needs it to exist), and the stance 「阿格莱雅进入"''',
    r'''"and the stance is ANCHORED to it, which needs it to exist), and the stance \"Aglaea enters that stance\" (「阿格莱雅进入"''')
add(MS + "AglaeaMemospriteTest.java", r'''"【至高之姿】状态」 precedes 「并使自身立即行动」"''',
    r'''"【至高之姿】状态」) precedes \"and makes herself act immediately\" (「并使自身立即行动」)"''')
add(MS + "AglaeaMemospriteTest.java", r'''1.0, ultimate.getFirst().effects().get(1).getPercent(), EPS, "回复至上限");''',
    r'''1.0, ultimate.getFirst().effects().get(1).getPercent(), EPS, "restores HP to the maximum (回复至上限)");''')
add(MS + "AglaeaMemospriteTest.java", r'''"「阿格莱雅进入【至高之姿】状态」"''',
    r'''"「阿格莱雅进入【至高之姿】状态」 (Aglaea enters the Supreme Stance (【至高之姿】) state)"''')
add(MS + "EvernightMemospriteAggroTest.java", r'''"precondition: 长夜月's talent summons 长夜 at battle start"''',
    r'''"precondition: Evernight (长夜月)'s talent summons Evey (长夜) at battle start"''')
add(MS + "EvernightMemospriteAggroTest.java", r'''"「「长夜」被攻击的概率提高」 "''',
    r'''"\"Evey (「长夜」) is more likely to be attacked\" (「「长夜」被攻击的概率提高」) "''')
add(MS + "MemospriteSkillTest.java", r'''"「献予「纷争」之诗」is a support skill whose work is on the rule side, so the Rules entry is what makes it deliverable"''',
    r'''"\"the poem offered to Strife\" (「献予「纷争」之诗」) is a support skill whose work is on the rule side, so the Rules entry is what makes it deliverable"''')
add(MS + "MemospriteTest.java", r'''"「仇恨: 125」"''',
    r'''"\"aggro: 125\" (「仇恨: 125」)"''')
add(MS + "MemospriteTest.java", r'''"precondition: her 天赋 (talent) summons 「长夜」 at BATTLE_START"''',
    r'''"precondition: her Talent (天赋) summons Evey (「长夜」) at BATTLE_START"''')
add(MS + "MemospriteTest.java", r'''"「长夜」免疫控制类负面状态"''',
    r'''"Evey (「长夜」) is immune to Crowd Control debuffs (「长夜」免疫控制类负面状态)"''')
add(MS + "MemospriteTest.java", r'''"免疫控制类 says nothing about 持续伤害类"''',
    r'''"immunity to Crowd Control (免疫控制类) says nothing about DoT Debuffs (持续伤害类)"''')
add(MS + "MemospriteTest.java", r'''"…and the resistance it carries is a 控制类 one, read back off the unit this time"''',
    r'''"…and the resistance it carries is a Crowd Control (控制类) one, read back off the unit this time"''')
add(MS + "SummonCommandTest.java", r'''"\"make the memosprite '长夜' deal ... Ice damage to all enemies\" -- exactly the 90 of 141303's own stance_list, once"''',
    r'''"\"make the memosprite Evey (长夜) deal ... Ice damage to all enemies\" -- exactly the 90 of 141303's own stance_list, once"''')
add(MS + "SummonConditionTest.java", r'''"precondition: 长夜月's own rule brought 「长夜」 out"''',
    r'''"precondition: Evernight (长夜月)'s own rule brought Evey (「长夜」) out"''')
add("src/test/java/com/laosun/aluminium/test/content/relics/PenaconyLandOfTheDreamsTest.java",
    r'''"其他我方角色 excludes the wearer (false case)"''',
    r'''"other allies (其他我方角色) excludes the wearer -- the false case"''')

add(D + "DebuffChanceDataTest.java", r'''"Himeko 不完全燃烧: #1 → param_list[0]"''',
    r'''"Himeko Incomplete Combustion (不完全燃烧): #1 → param_list[0]"''')
add(D + "DebuffChanceDataTest.java", r'''"Welt 画地为牢: #1 → param_list[0]"''',
    r'''"Welt Gravitational Imprisonment (画地为牢): #1 → param_list[0]"''')
add(D + "DebuffChanceDataTest.java", r'''"Sampo 你最闪亮: #2 → param_list[1]; this is what proves the slot is read from the text"''',
    r'''"Sampo Shining Bright (你最闪亮): #2 → param_list[1]; this is what proves the slot is read from the text"''')
add(D + "DebuffChanceDataTest.java", r'''"Silver Wolf 等待程序响应: #4 → param_list[3] = 0.6"''',
    r'''"Silver Wolf Waiting for Program Response (等待程序响应): #4 → param_list[3] = 0.6"''')
add(D + "DebuffChanceDataTest.java", r'''"Black Swan 无端命运的机杼: #2 → param_list[1] = 0.5 (the text names it twice, both #2)"''',
    r'''"Black Swan Loom of Fate's Caprice (无端命运的机杼): #2 → param_list[1] = 0.5 (the text names it twice, both #2)"''')
add(D + "DebuffChanceDataTest.java", r'''"Boothill 绝命对峙: the description states no probability, so there is nothing to read"''',
    r'''"Boothill Standoff (绝命对峙): the description states no probability, so there is nothing to read"''')
add(D + "EnhancedSkillDataProbeTest.java",
    r'''"「强化普攻」是槽位 8（`data/skills.json` 的内层键是 1,2,3,4,6,7,8）——数据行的末位就是槽位：130108 / 111108"''',
    r'''"\"Enhanced Basic ATK\" (「强化普攻」) is slot 8 (the inner keys of `data/skills.json` are 1,2,3,4,6,7,8) -- the last digit of the data row is the slot: 130108 / 111108"''')
add(D + "RuleAmendmentTest.java", r'''"trace \"冰咒\" raises the ultimate's 0.5 to 0.65, so a 0.6 roll freezes"''',
    r'''"the \"Ice Spell\" (「冰咒」) trace raises the ultimate's 0.5 to 0.65, so a 0.6 roll freezes"''')
add(D + "TriggerDataBindingTest.java", r'''"华彩花腔 (Radiant Refrain): 战斗开始时自身行动提前25% (advances herself 25% at battle start)"''',
    r'''"Coloratura Cadenza (华彩花腔): \"advances her own action by 25% at battle start\" (战斗开始时自身行动提前25%)"''')
add(D + "TriggerDataBindingTest.java", r'''"模进乐段 (Sequence): 施放战技时额外恢复5点能量 (restores 5 extra energy when casting the Skill)"''',
    r'''"Sequential Passage (模进乐段): \"restores 5 extra Energy when casting the Skill\" (施放战技时额外恢复5点能量)"''')
add(D + "TriggerDataBindingTest.java", r'''"nothing in her file listens to 普攻 (basic attack) -- 施放战技时 (casting the Skill) is the Skill slot"''',
    r'''"nothing in her file listens to Basic ATK (普攻) -- \"when casting the Skill\" (施放战技时) is the Skill slot"''')

add(E + "ActorAttrScaleTest.java", r'''"[actor_attr] the actor 德谬歌 Max HP = "''',
    r'''"[actor_attr] the actor Demiurge (德谬歌) Max HP = "''')
add(E + "ActorAttrScaleTest.java", r'''"the raise is equal to #1% of **德谬歌**'s Max HP\" -- the ACTOR is the unit the share is of"''',
    r'''"the raise is equal to #1% of **Demiurge (德谬歌)**'s Max HP\" -- the ACTOR is the unit the share is of"''')
add(E + "BloodfeudMaxHpTest.java", r'''"「【血仇】状态下生命上限提高，数值等同于当前生命上限的 50%」"''',
    r'''"「【血仇】状态下生命上限提高，数值等同于当前生命上限的 50%」 (while in the Vendetta (【血仇】) state, Max HP is raised by an amount equal to 50% of the current Max HP)"''')
add(E + "BondmateHolderTest.java", r'''"「行动提前40%」: "''',
    r'''"\"action advance 40%\" (「行动提前40%」): "''')
add(E + "CastAppliedCountTest.java", r'''"星魂 1's rule is gone from characters/1001.json"''',
    r'''"Eidolon (星魂) 1's rule is gone from characters/1001.json"''')
add(E + "ControlImmunityTest.java", r'''"「卡厄斯兰那免疫控制类负面状态」"''',
    r'''"\"Khaslana (卡厄斯兰那) is immune to Crowd Control debuffs\" (「卡厄斯兰那免疫控制类负面状态」)"''')
add(E + "ControlTest.java", r'''"冻结 = 不能行动"''',
    r'''"Frozen (冻结) = cannot act (不能行动)"''')
add(E + "CoupDeMainTest.java", r'''"「消耗 6 点充能」—— ❗ 而两次施放（复制 + 原技能）"''',
    r'''"\"consumes 6 points of Charge\" (「消耗 6 点充能」)—— ❗ and two casts (the copy + the original Skill)"''')
add(E + "CoupDeMainTest.java", r'''"各给她 +1 点（【军功】那条：「施放普攻或战技时使刻律德菈获得 1 点充能」）"''',
    r'''"+1 point each (the Military Merit (【军功】) clause: \"when casting a Basic ATK or Skill, Cerydra gains 1 point of Charge\" (「施放普攻或战技时使刻律德菈获得 1 点充能」))"''')
add(E + "CoupDeMainTest.java", r'''"，所以净变化是 -4 ✓ (before="''',
    r'''", so the net change is -4 ✓ (before="''')
add(E + "CoupDeMainTest.java", r'''"「使【爵位】变回【军功】」"''',
    r'''"\"turns Peerage (【爵位】) back into Military Merit (【军功】)\" (「使【爵位】变回【军功】」)"''')
add(E + "CoupDeMainTest.java", r'''"“变回【军功】” -- the merit is still there"''',
    r'''"\"turns back into Military Merit (【军功】)\" (「变回【军功】」) -- the merit is still there"''')
add(E + "DamageBaseTest.java", r'''"「等同于…生命上限的伤害」"''',
    r'''"\"damage equal to ... Max HP\" (「等同于…生命上限的伤害」)"''')
add(E + "DamageBaseTest.java", r'''"「等同于砂金100%防御力的伤害」"''',
    r'''"\"damage equal to 100% of Aventurine (砂金)'s DEF\" (「等同于砂金100%防御力的伤害」)"''')
add(E + "DamageBaseTest.java", r'''"「等同于丹恒100%攻击力」"''',
    r'''"\"equal to 100% of Dan Heng (丹恒)'s ATK\" (「等同于丹恒100%攻击力」)"''')
add(E + "DamageScopeBoostTest.java", r'''"追加攻击 damage is not 普攻 damage, even when the follow-up came from one"''',
    r'''"follow-up attack (追加攻击) damage is not Basic ATK (普攻) damage, even when the follow-up came from one"''')
add(E + "DebuffTest.java", r'''"灼烧/触电/… are 持续伤害类负面状态"''',
    r'''"Burn (灼烧) / Shock (触电) / ... are DoT Debuffs (持续伤害类负面状态)"''')
add(E + "DebuffTest.java", r'''"嘲讽 forces the bearer's targeting -- negative on the one carrying it"''',
    r'''"Taunt (嘲讽) forces the bearer's targeting -- negative on the one carrying it"''')
add(E + "DebuffTest.java", r'''"易伤 is the defender-side debuff"''',
    r'''"Vulnerability (易伤) is the defender-side debuff"''')
add(E + "DebuffTest.java", r'''"减伤 sits on the defender too, but it is a POSITIVE effect -- where a buff sits does not "''',
    r'''"DMG Reduction (减伤) sits on the defender too, but it is a POSITIVE effect -- where a buff sits does not "''')
add(E + "DebuffTest.java", r'''"and the buff is untouched: 协奏 is not a negative effect"''',
    r'''"and the buff is untouched: Concerto (协奏) is not a negative effect"''')
add(E + "DotTest.java", r'''"Fire + DotBuff IS 灼烧: the engine's one translation is BuffManager.DOT_STATES"''',
    r'''"Fire + DotBuff IS Burn (灼烧): the engine's one translation is BuffManager.DOT_STATES"''')
add(E + "DragonPanelTest.java", r'''"document: 量子属性伤害"''',
    r'''"document: Quantum DMG (量子属性伤害)"''')
add(E + "DragonPanelTest.java", r'''"document: 全体攻击"''',
    r'''"document: AoE attack (全体攻击)"''')
add(E + "DragonPanelTest.java", r'''"document: 全体 30"''',
    r'''"document: AoE (全体) 30"''')
add(E + "EidolonSixExtendsSoulsteelTest.java", r'''"「使这些增益效果的持续时间全部延长 1 回合」"''',
    r'''"\"extends the duration of all these buffs by 1 turn\" (「使这些增益效果的持续时间全部延长 1 回合」)"''')
add(E + "EidolonSixExtendsSoulsteelTest.java", r'''"星魂 6 才有这一条"''',
    r'''"only Eidolon (星魂) 6 has this clause"''')
add(E + "ElationAccumulatorTest.java", r'''"单次获得能量时最多获得 240 点累计值 (a single energy gain adds at most 240 accumulated)"''',
    r'''"\"a single energy gain adds at most 240 accumulated points\" (单次获得能量时最多获得 240 点累计值)"''')
add(E + "ElationAmountCapTest.java", r'''"单次不超过 100 点"''',
    r'''"at most 100 points in a single gain (单次不超过 100 点)"''')
add(E + "ExtendAllBuffsTest.java", r'''"「使自身**所有**增益效果延长 1 回合」-- the buff came from 甲, and it must still be lengthened"''',
    r'''"\"extends **all** of her own buffs by 1 turn\" (「使自身**所有**增益效果延长 1 回合」) -- the buff came from unit A (甲), and it must still be lengthened"''')
add(E + "ExtendAllBuffsTest.java", r'''"⚠ the origin filter is the old behaviour: 乙’s rule may only lengthen 乙’s own buffs"''',
    r'''"⚠ the origin filter is the old behaviour: unit B's (乙’s) rule may only lengthen unit B's own buffs"''')
add(E + "ExtendAllBuffsTest.java", r'''"precondition: 甲 has a skill"''',
    r'''"precondition: unit A (甲) has a skill"''')
add(E + "ExtendAllBuffsTest.java", r'''"precondition: 乙 carries 甲’s buff"''',
    r'''"precondition: unit B (乙) carries unit A's (甲’s) buff"''')
add(E + "ExtendAllBuffsTest.java", r'''"precondition: 乙 has a skill"''',
    r'''"precondition: unit B (乙) has a skill"''')
add(E + "GentleRainTest.java", r'''"one rule covers both healers, because `actor is_ally` includes the 忆灵 (memosprite): "''',
    r'''"one rule covers both healers, because `actor is_ally` includes the memosprite (忆灵): "''')
add(E + "ImbibitorLunaeTest.java", r'''"「施放每段攻击后获得1层【亢心】…该效果可以叠加6层」"''',
    r'''"\"gains 1 stack of Righteous Heart (【亢心】) after each attack segment ... this effect can stack 6 times\" (「施放每段攻击后获得1层【亢心】…该效果可以叠加6层」)"''')
add(E + "KlaraTechniqueAggroTest.java", r'''"「克拉拉受到敌方攻击的概率提高」 "''',
    r'''"\"Clara (克拉拉) is more likely to be attacked by enemies\" (「克拉拉受到敌方攻击的概率提高」) "''')
add(E + "LethalHitTest.java", r'''"「卡厄斯兰那受到致命攻击时不会陷入无法战斗状态」"''',
    r'''"\"when Khaslana (卡厄斯兰那) takes a lethal hit, she does not fall into the unable-to-fight state\" (「卡厄斯兰那受到致命攻击时不会陷入无法战斗状态」)"''')
add(E + "LethalHitTest.java", r'''"「而是回复等同于自身生命上限 20% 的生命值」"''',
    r'''"\"instead restores HP equal to 20% of her own Max HP\" (「而是回复等同于自身生命上限 20% 的生命值」)"''')
add(E + "LethalHitTest.java", r'''"1104 的行迹救了她一次"''',
    r'''"the 1104 Traces (行迹) saved her once"''')
add(E + "LethalHitTest.java", r'''"「回复等同于自身生命上限 50% 的生命值」"''',
    r'''"\"restores HP equal to 50% of her own Max HP\" (「回复等同于自身生命上限 50% 的生命值」)"''')
add(E + "LightConeAggroTest.java", r'''"使装备者受到攻击的概率提高 -- and the data says the factor is 2"''',
    r'''"\"makes the wearer more likely to be attacked\" (使装备者受到攻击的概率提高) -- and the data says the factor is 2"''')
add(E + "LightConeBoostTest.java", r'''"终结技 damage is 28% at rank 1"''',
    r'''"Ultimate (终结技) damage is 28% at rank 1"''')
add(E + "LightConeMomentTest.java", r'''"同时使自身受到攻击的概率提高 -- the data says the factor is 2"''',
    r'''"\"and makes itself more likely to be attacked\" (同时使自身受到攻击的概率提高) -- the data says the factor is 2"''')
add(E + "LowHpAggroTraceTest.java", r'''"「则被敌方目标攻击的概率降低」: "''',
    r'''"\"then it is less likely to be attacked by enemy targets\" (「则被敌方目标攻击的概率降低」): "''')
add(E + "MilitaryMeritDefenceIgnoreTest.java", r'''"持有【军功】的角色无视 16% 防御"''',
    r'''"\"a character holding Military Merit (【军功】) ignores 16% DEF\" (持有【军功】的角色无视 16% 防御)"''')
add(E + "MimiCheerTest.java", r'''"「附上【迷迷的声援】」"''',
    r'''"\"applies Mem's Support (【迷迷的声援】)\" (「附上【迷迷的声援】」)"''')
add(E + "MooncocoonTest.java", r'''"「不会陷入无法战斗状态」"''',
    r'''"\"does not fall into the unable-to-fight state\" (「不会陷入无法战斗状态」)"''')
add(E + "MooncocoonTest.java", r'''"它真的停在 0 血 —— 那就是「延后」"''',
    r'''"it really stops at 0 HP —— that is the \"delay\" (「延后」)"''')
add(E + "MooncocoonTest.java", r'''"「获得【月茧】状态」"''',
    r'''"\"gains the Mooncocoon (【月茧】) state\" (「获得【月茧】状态」)"''')
add(E + "MooncocoonTest.java", r'''"没有人救它 ⇒ 它自己的回合结束时候倒下（且它确实行动过 ✓）"''',
    r'''"nobody saves it ⇒ it falls at the end of its own turn (and it did act ✓)"''')
add(E + "MooncocoonTest.java", r'''"生命值提高 ⇒ 【月茧】解除"''',
    r'''"HP raised ⇒ the Mooncocoon (【月茧】) is removed"''')
add(E + "MooncocoonTest.java", r'''"【月茧】已解除 ⇒ 它不再倒下"''',
    r'''"the Mooncocoon (【月茧】) is removed ⇒ it no longer falls"''')
add(E + "MortenaxZoneTest.java", r'''"「自身受到的伤害降低50%」"''',
    r'''"\"DMG taken is reduced by 50%\" (「自身受到的伤害降低50%」)"''')
add(E + "MortenaxZoneTest.java", r'''"「受到的治疗量提高50%」"''',
    r'''"\"healing received is raised by 50%\" (「受到的治疗量提高50%」)"''')
add(E + "MortenaxZoneTest.java", r'''"「结界持续期间，使敌方全体全属性抗性降低20%」"''',
    r'''"\"while the zone lasts, all enemies get 20% All-Type RES Reduction\" (「结界持续期间，使敌方全体全属性抗性降低20%」)"''')
add(E + "MortenaxZoneTest.java", r'''"星魂1 is the gate: without it the clause must not fire"''',
    r'''"Eidolon (星魂) 1 is the gate: without it the clause must not fire"''')
add(E + "OceanOdeEnergyTest.java", r'''"and the mark is consumed -- 消耗 means spent, not merely read"''',
    r'''"and the mark is consumed -- consume (消耗) means spent, not merely read"''')
add(E + "OdeOfGenesisTest.java", r'''"「攻击力提高，数值等同于德谬歌生命上限的 #1%」"''',
    r'''"\"ATK raised by an amount equal to #1% of Demiurge (德谬歌)'s Max HP\" (「攻击力提高，数值等同于德谬歌生命上限的 #1%」)"''')
add(E + "OdeOfGenesisTest.java", r'''"「暴击率提高，数值等同于德谬歌暴击率的 #2%」"''',
    r'''"\"CRIT Rate raised by an amount equal to #2% of Demiurge (德谬歌)'s CRIT Rate\" (「暴击率提高，数值等同于德谬歌暴击率的 #2%」)"''')
add(E + "OdeOfGenesisTest.java", r'''"[genesis] 迷迷 ("''',
    r'''"[genesis] Mem (迷迷) ("''')
add(E + "OdeOfRomanceStanceTest2.java", r'''"】状态」-- the Garmentmaker is back where it started"''',
    r'''"】状态」 (it lasts until Aglaea exits that state) -- the Garmentmaker is back where it started"''')
add(E + "OdeOfRomanceTest.java", r'''+ STATE + "】」");''',
    r'''+ STATE + "】」 (when cast on Aglaea, makes Aglaea gain that state)");''')
add(E + "OriginalDamageRiderTest.java", r'''"and 姬子's Eidolon 6 share (40%) lands as 40%, not as 40% x the zone factor"''',
    r'''"and Himeko (姬子)'s Eidolon 6 share (40%) lands as 40%, not as 40% x the zone factor"''')
add(E + "OtherAlliesTargetTest.java", r'''"「使除自身以外的队友立即行动」"''',
    r'''"\"makes allies other than herself act immediately\" (「使除自身以外的队友立即行动」)"''')
add(E + "PartyResourceTest.java", r'''"[party] 笑点 on the battle: "''',
    r'''"[party] Punchline (笑点) on the battle: "''')
add(E + "PeerageResPenTest.java", r'''"「持有【爵位】的角色…全属性抗性穿透提高 10.00%」"''',
    r'''"\"a character holding Peerage (【爵位】) ... gains 10.00% All-Type RES PEN\" (「持有【爵位】的角色…全属性抗性穿透提高 10.00%」)"''')
add(E + "PermansorTerraeTest.java", r'''"「使指定我方单体角色成为【同袍】」"''',
    r'''"\"makes a designated single ally the Bondmate (【同袍】)\" (「使指定我方单体角色成为【同袍】」)"''')
add(E + "PermansorTerraeTest.java", r'''"the Bondmate/shield rule AND (since 2026-10-02) the 神秀 (Divine Excellence) trace that buffs whoever holds 【同袍】 (the Bondmate) -- "''',
    r'''"the Bondmate/shield rule AND (since 2026-10-02) the Empyreanity (神秀) trace that buffs whoever holds the Bondmate (【同袍】) -- "''')
add(E + "PermansorTerraeTest.java", r'''"the level convention, the technique's 【同袍】 (「使用秘技后获得【同袍】」, gaining the Bondmate after using the technique), 葳蕤's 「行动提前40%」 and "''',
    r'''"the level convention, the technique's Bondmate (【同袍】) (\"gaining the Bondmate after using the Technique\" (「使用秘技后获得【同袍】」)), Sylvanity (葳蕤)'s \"action advance 40%\" (「行动提前40%」) and "''')
add(E + "PermansorTerraeTest.java", r'''"-- since 2026-10-02 -- the technique's auto-cast (「下一次战斗开始时自动对持有【同袍】的"''',
    r'''"-- since 2026-10-02 -- the technique's auto-cast, i.e. \"at the start of the next battle it automatically casts 1 Skill on the holder of the Bondmate (【同袍】)\" (「下一次战斗开始时自动对持有【同袍】的"''')
add(E + "PermansorTerraeTest.java", r'''"葳蕤's second half: 「【同袍】施放攻击时，丹恒•腾荒恢复6点能量」 (when the Bondmate attacks, Dan Heng - Permansor Terrae restores 6 energy)"''',
    r'''"Sylvanity (葳蕤)'s second half: \"when the Bondmate (【同袍】) casts an attack, Dan Heng - Permansor Terrae restores 6 Energy\" (「【同袍】施放攻击时，丹恒•腾荒恢复6点能量」)"''')
add(E + "RelicAbilityBatchTest.java", r'''"黑塔 is 智识, like the wearer: 「若至少存在一名与装备者命途相同的队友」"''',
    r'''"Herta (黑塔) is Erudition (智识), like the wearer: \"if there is at least one ally on the same Path as the wearer\" (「若至少存在一名与装备者命途相同的队友」)"''')
add(E + "RelicAbilityBatchTest.java", r'''"桂乃芬 is 虚无: the condition does not hold, so nothing is granted"''',
    r'''"Guinaifen (桂乃芬) is Nihility (虚无): the condition does not hold, so nothing is granted"''')
add(E + "RelicAbilityBatchTest.java", r'''"「大于等于 2400 时…提高 12%」"''',
    r'''"\"when it is greater than or equal to 2400 ... raise it by 12%\" (「大于等于 2400 时…提高 12%」)"''')
add(E + "ResistanceReductionTest.java", r'''"全属性抗性降低 20%: "''',
    r'''"All-Type RES Reduction 20% (全属性抗性降低 20%): "''')
add(E + "ResistanceReductionTest.java", r'''"「施放终结技时…敌方全体全属性抗性降低20%」"''',
    r'''"\"when casting the Ultimate ... all enemies get 20% All-Type RES Reduction\" (「施放终结技时…敌方全体全属性抗性降低20%」)"''')
add(E + "ResistanceReductionTest.java", r'''"「施放普攻时使目标的全属性抗性降低12%」"''',
    r'''"\"casting a Basic ATK lowers the target's All-Type RES by 12%\" (「施放普攻时使目标的全属性抗性降低12%」)"''')
add(E + "RolledAttributeModifierTest.java", r'''"「有100%的基础概率使该目标的全属性抗性降低10.00%」"''',
    r'''"\"a 100% base chance to lower that target's All-Type RES by 10.00%\" (「有100%的基础概率使该目标的全属性抗性降低10.00%」)"''')
add(E + "RolledAttributeModifierTest.java", r'''"「有100%的基础概率使敌方每个单体目标防御力降低20%」"''',
    r'''"\"a 100% base chance to lower each single enemy target's DEF by 20%\" (「有100%的基础概率使敌方每个单体目标防御力降低20%」)"''')
add(E + "RolledAttributeModifierTest.java", r'''"「有100%的基础概率使敌方每个单体目标攻击力降低25%」"''',
    r'''"\"a 100% base chance to lower each single enemy target's ATK by 25%\" (「有100%的基础概率使敌方每个单体目标攻击力降低25%」)"''')
add(E + "RolledAttributeModifierTest.java", r'''"「攻击命中时有75%的基础概率使受到攻击的敌方目标速度降低10%」"''',
    r'''"\"when an attack hits, a 75% base chance to lower the attacked enemy target's SPD by 10%\" (「攻击命中时有75%的基础概率使受到攻击的敌方目标速度降低10%」)"''')
add(E + "ShieldGrantedEventTest.java", r'''"「受到队友提供的…护盾时」"''',
    r'''"\"when receiving ... a shield provided by an ally\" (「受到队友提供的…护盾时」)"''')
add(E + "ShieldGrantedEventTest.java", r'''"大丽花's trace needs BOTH halves: 「治疗效果**或**护盾」 -- one alone is a trace that stays silent "''',
    r'''"The Dahlia (大丽花)'s trace needs BOTH halves: \"healing **or** a shield\" (「治疗效果**或**护盾」) -- one alone is a trace that stays silent "''')
add(E + "ShieldGrantedEventTest.java", r'''": 「单个回合内不可重复触发」"''',
    r'''": \"cannot be triggered more than once in a single turn\" (「单个回合内不可重复触发」)"''')
add(E + "ShieldGrantedEventTest.java", r'''": 「持续#4[i]回合」 = 3"''',
    r'''": \"lasts #4[i] turns\" (「持续#4[i]回合」) = 3"''')
add(E + "SiblingElationTest.java", r'''": 「施放攻击后，固定恢复10点能量」"''',
    r'''": \"after casting an attack, restores a fixed 10 Energy\" (「施放攻击后，固定恢复10点能量」)"''')
add(E + "SiblingElationTest.java", r'''": 终结技现在是四条 —— 暴伤 buff、无欢愉技时的行动提前、"''',
    r'''": the Ultimate now has four clauses -- the CRIT DMG buff, the action advance when there is no Elation Skill, "''')
add(E + "SiblingElationTest.java", r'''"获得 5 个笑点、以及「若目标拥有欢愉技…使其立即施放 1 次欢愉技」"''',
    r'''"gains 5 Punchline, and \"if the target has an Elation Skill ... makes it immediately cast 1 Elation Skill\" (「若目标拥有欢愉技…使其立即施放 1 次欢愉技」)"''')
add(E + "SiblingElationTest.java", r'''"「使其行动提前50%」: "''',
    r'''"\"advances its action by 50%\" (「使其行动提前50%」): "''')
add(E + "SiblingHarmonyTest.java", r'''"「为我方全体附上【伴舞】效果」"''',
    r'''"\"applies the Backup Dancer (【伴舞】) effect to all allies\" (「为我方全体附上【伴舞】效果」)"''')
add(E + "SiblingHarmonyTest.java", r'''"「使我方全体的击破特攻提高30%，持续2回合」"''',
    r'''"\"raises all allies' Break Effect by 30% for 2 turns\" (「使我方全体的击破特攻提高30%，持续2回合」)"''')
add(E + "SkillTargetCoreflameTest.java", r'''"「gains 1 point of [火种]」"''',
    r'''"「gains 1 point of [火种] (Kindling)」"''')
add(E + "SkyOdeSpendTest.java", r'''"战技 (slot 2) spends one"''',
    r'''"Skill (战技) (slot 2) spends one"''')
add(E + "SkyOdeSpendTest.java", r'''"终结技 (slot 3) spends another"''',
    r'''"Ultimate (终结技) (slot 3) spends another"''')
add(E + "SkyOdeStackTest.java", r'''"名点的角色不在场时，这条应该什么也不做"''',
    r'''"when the character the sentence names is not on the field, this clause should do nothing"''')
add(E + "SoftAggroWeightTest.java", r'''"precondition: 星魂 2's battle-start shield landed"''',
    r'''"precondition: Eidolon (星魂) 2's battle-start shield landed"''')
add(E + "SuperBreakTest.java", r'''"「转化为 1 次 X% 的超击破伤害」: the extra damage is proportional to X (and to the 削韧值, which is the same "''',
    r'''"\"converted into 1 instance of X% Super Break DMG\" (「转化为 1 次 X% 的超击破伤害」): the extra damage is proportional to X (and to the Toughness Reduction (削韧值), which is the same "''')
add(E + "TechniqueHarvestTest.java", r'''"「使用秘技后，下一次战斗开始时丹恒攻击力提高40%」: "''',
    r'''"\"after using the Technique, Dan Heng's ATK is raised by 40% at the start of the next battle\" (「使用秘技后，下一次战斗开始时丹恒攻击力提高40%」): "''')
add(E + "TimeOdeBoostTest.java", r'''"「「长夜」施放忆灵技【迷梦，流失，如露】时造成的伤害提高 #1%」-- the captured share"''',
    r'''"\"when Evey (「长夜」) casts the memosprite skill [迷梦，流失，如露], the DMG it deals is raised by #1%\" (「「长夜」施放忆灵技【迷梦，流失，如露】时造成的伤害提高 #1%」) -- the captured share"''')
add(E + "TransformationDispelsTest.java", r'''"「解除自身所有负面效果」"''',
    r'''"\"removes all of her own debuffs\" (「解除自身所有负面效果」)"''')
add(E + "TransformationEndClausesTest.java", r'''"「我方全体」-- and on the ally, which is what 全体 means"''',
    r'''"all allies (「我方全体」) -- and on the ally, which is what \"all\" (全体) means"''')
add(E + "TransformationEndClausesTest.java", r'''"「战斗开始时，获得 1 点【火种】」"''',
    r'''"\"at the start of battle, gains 1 point of Kindling (【火种】)\" (「战斗开始时，获得 1 点【火种】」)"''')
add(E + "TransformationEndClausesTest.java", r'''"「变身结束时，获得 3 点【火种】」"''',
    r'''"\"when the transformation ends, gains 3 points of Kindling (【火种】)\" (「变身结束时，获得 3 点【火种】」)"''')
add(E + "TransformationEndsOnLastCountdownTest.java", r'''"「最后 1 个倒计时回合…结束变身」"''',
    r'''"\"the last countdown turn ... ends the transformation\" (「最后 1 个倒计时回合…结束变身」)"''')
add(E + "TransformationScopedStatsTest.java", r'''"「攻击力提高 80%」"''',
    r'''"\"ATK raised by 80%\" (「攻击力提高 80%」)"''')
add(E + "TransformationScopedStatsTest.java", r'''"「生命上限提高 270%」"''',
    r'''"\"Max HP raised by 270%\" (「生命上限提高 270%」)"''')
add(E + "TransformationScopedStatsTest.java", r'''"「物理属性抗性穿透提高 20%」"''',
    r'''"\"Physical RES PEN raised by 20%\" (「物理属性抗性穿透提高 20%」)"''')
add(E + "TrinnonZoneTest.java", r'''"「结界持续期间，敌方目标受到的伤害提高30.00%」 -- "''',
    r'''"\"while the zone lasts, enemy targets take 30.00% more DMG\" (「结界持续期间，敌方目标受到的伤害提高30.00%」) -- "''')
add(E + "TrueDamageJudgeTest.java", r'''"真实伤害 (true damage) skips the defence zone: "''',
    r'''"True DMG (真实伤害) skips the defence zone: "''')
add(E + "ValorousFollowUpTest.java", r'''"the wearer's own follow-up must not mark 功勋"''',
    r'''"the wearer's own follow-up must not mark Merit (功勋)"''')
add(E + "WorldOdeExtraTurnsRefreshTest.java", r'''"[refresh] with the state: 变身 "''',
    r'''"[refresh] with the state: transformation (变身) "''')
add(E + "WorldOdeExtraTurnsRefreshTest.java", r'''", 【毁伤】 "''',
    r'''", Scourge (【毁伤】) "''')
add(E + "WorldOdeExtraTurnsRefreshTest.java", r'''" ; without it: 变身 "''',
    r'''" ; without it: transformation (变身) "''')

add(T + "CastSetupTest.java", r'''"\"make the memosprite '长夜' ... deal damage\": the damage is the memosprite's, so her cast swings nothing of its own"''',
    r'''"\"make the memosprite Evey (长夜) ... deal damage\": the damage is the memosprite's, so her cast swings nothing of its own"''')
add(T + "CastSkillTest.java", r'''"precondition: 停云's ULTRA deals damage at all ("''',
    r'''"precondition: Tingyun (停云)'s ULTRA deals damage at all ("''')
add(T + "CastSkillTest.java", r'''"precondition: 万敌's SKILL is the blast this case is about"''',
    r'''"precondition: Mydei (万敌)'s SKILL is the blast this case is about"''')
add(T + "CastSkillTest.java", r'''"and the cast's own SKILL_CAST event ran 神秀 in the same breath: +15% of his ATK, on the holder "''',
    r'''"and the cast's own SKILL_CAST event ran Empyreanity (神秀) in the same breath: +15% of his ATK, on the holder "''')
add(T + "DebuffOnConditionTest.java", r'''"防御力被降低 is not 防御力被提高 (false case)"''',
    r'''"DEF Reduction (防御力被降低) is not DEF Boost (防御力被提高) -- the false case"''')
add(T + "ShieldOriginConditionTest.java", r'''"「每回合开始时回复等同于各自4%生命上限+106」"''',
    r'''"\"at the start of every turn, restores HP equal to 4% of each one's Max HP + 106\" (「每回合开始时回复等同于各自4%生命上限+106」)"''')
add(T + "ShieldOriginConditionTest.java", r'''"only 「战技提供的护盾」 qualifies -- the 星魂 2 shield is hers too, and must not"''',
    r'''"only \"the shield the Skill provides\" (「战技提供的护盾」) qualifies -- the Eidolon (星魂) 2 shield is hers too, and must not"''')
add(T + "SkillAttributionTest.java", r'''"the talent pays for anybody's break, and 星魂 4 pays a second one for a break caused by her 战技"''',
    r'''"the talent pays for anybody's break, and Eidolon (星魂) 4 pays a second one for a break caused by her Skill (战技)"''')
add(T + "SkillAttributionTest.java", r'''"a 普攻 break is the talent's, not 星魂 4's -- and `actor == self` alone cannot tell them apart"''',
    r'''"a Basic ATK (普攻) break is the talent's, not Eidolon (星魂) 4's -- and `actor == self` alone cannot tell them apart"''')
add(T + "SkillAttributionTest.java", r'''"星魂 4's rule is gone from characters/1003.json"''',
    r'''"Eidolon (星魂) 4's rule is gone from characters/1003.json"''')
add(T + "TauntOpTest.java", r'''"「持续#N回合」"''',
    r'''"\"lasts #N turns\" (「持续#N回合」)"''')
add(T + "TriggerDamageTakenTest.java", r'''"positive = 受到的伤害提高 (damage taken raised) = vulnerability, a DEBUFF on the defender"''',
    r'''"positive = DMG taken increased (受到的伤害提高) = vulnerability, a DEBUFF on the defender"''')
add(T + "TriggerDamageTakenTest.java", r'''"negative = 受到的伤害降低 (damage taken lowered) = reduction, a BUFF on the defender"''',
    r'''"negative = DMG taken reduced (受到的伤害降低) = reduction, a BUFF on the defender"''')
add(T + "TriggerDealingDamageTest.java", r'''"a Fire DOT is 灼烧"''',
    r'''"a Fire DOT is Burn (灼烧)"''')
add(T + "TriggerDealingDamageTest.java", r'''"and it is not 触电"''',
    r'''"and it is not Shock (触电)"''')
add(T + "TriggerDealingDamageTest.java", r'''"a Thunder DOT is 触电"''',
    r'''"a Thunder DOT is Shock (触电)"''')
add(T + "TriggerTableTest.java", r'''"only her 华彩花腔 (Radiant Refrain) trace listens to BATTLE_START"''',
    r'''"only her Coloratura Cadenza (华彩花腔) trace listens to BATTLE_START"''')
add(T + "TriggerTableTest.java", r'''"nothing in her file listens to BASIC_ATTACK: 普攻 (basic attack) is not 战技 (Skill)"''',
    r'''"nothing in her file listens to BASIC_ATTACK: Basic ATK (普攻) is not Skill (战技)"''')
