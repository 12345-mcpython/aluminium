"""Ship the ≥100 gate and the 150-charge clause (round 7 of the goal).

Measured: the DSL's `!` prefix is parsed (L1187) and refuses anything that is not a `PartyCondition`, and `has_state` READS the
unit's buffs -- which is exactly that family. So the gate needs no new vocabulary; if it is refused, the load says so loudly.

Two sentences ship:
  * 「充能达到 100 时消耗 100 点充能进入【血仇】状态」 gains `!self has_state 血仇`, because entering a state you are already in should
    not drain the charge again -- measured: without it the charge can never reach 150, which makes the second sentence unreachable;
  * 「【血仇】状态期间充能达到 150 点时，万敌立即获得 1 个额外回合并自动施放【弑神登神】」 -- spend 150, extra turn, swap slot 11, cast.
"""
import io
import json
import sys

CHAR = "src/main/resources/characters/1404.json"
JUDGE = "src/test/java/com/laosun/aluminium/test/MydeiBloodfeudSkillsTest.java"
MUTATOR = "tools/mut_1404_gate.py"
NEW_ID = "bloodfeud_godslayer_at_a_hundred_and_fifty"

with io.open(CHAR, encoding="utf-8") as handle:
    doc = json.load(handle)
rules = doc["rules"]

# ---- 1. the gate
hundred = next(entry for entry in rules
               if isinstance(entry, dict) and entry.get("id") == "talent_enters_bloodfeud_at_a_hundred")
GATE = "!self has_state 血仇"
if GATE in hundred["when"]:
    sys.exit("REFUSING: the gate is already there")
hundred["when"] = list(hundred["when"]) + [GATE]
hundred["note"] = (str(hundred.get("note")) + " "
                   "⭐ **2026-10-02 补上 `!self has_state 血仇`** ✓：❗ 实测——没有它时，"
                   "这条在**已在【血仇】中**仍会再次消耗 100 点 ✗ ⇒ 充能**永远攒不到 150** ✗"
                   "（而同一条天赋里的下一句正需要 150 ✓）。⚠ 否定的写法："
                   "`!` 前缀早就被解析 ✓（只允许否定 `PartyCondition` ✓，而 `has_state` 读的就是单位的增益 ✓）。")

# ---- 2. the 150-charge clause
if any(isinstance(entry, dict) and entry.get("id") == NEW_ID for entry in rules):
    sys.exit("REFUSING: the 150-charge rule is already there")
rules.append({
    "on": "RESOURCE_CHANGED",
    "id": NEW_ID,
    "when": ["resource_changed:天赋充能", "self has_state 血仇", "self_resource:天赋充能 >= 150"],
    "do": [
        {"op": "SPEND_RESOURCE", "resource": "天赋充能", "amount": 150},
        {"op": "EXTRA_TURN", "target": "self"},
        {"op": "REPLACE_SKILL", "skill": "SKILL", "skill_id": 11, "turns": 1, "target": "self"},
        {"op": "CAST_SKILL", "skill": "SKILL", "target": "target"},
    ],
    "source": ("1404 万敌 天赋 以血还血 (140404)：「【血仇】状态期间充能达到 **150** 点时，"
               "万敌立即获得 1 个额外回合并自动施放【弑神登神】」"),
    "note": (
        "「充能达到 **150** 点时，立即获得 1 个**额外回合**并**自动施放【弑神登神】**」⇒ 四个已有词 ✓："
        "`SPEND_RESOURCE{150}` ✓（【弑神登神】自己那一行就写着「消耗 150 点充能」 ✓）；`EXTRA_TURN` ✓"
        "（已出货：`1102` 希儿、`1309` 知更鸟 ✓）；`REPLACE_SKILL{skill_id: **11**, turns: 1}` ✓ —— "
        "槽 11 的破韧 **90/60** 与语料给【弑神登神】的逐字相符 ✓，而**换装会传到被命令的施放** ✓"
        "（同等级对比：有换装 767.585 vs 无换装 628.024 ✓）；`CAST_SKILL{SKILL}` ✓。"
        "⚠ 仍登记 ✓：「下一次【弑神登神】**优先攻击指定敌方单体**」（终结技标记的目标 ✗）"
        "与 `1415` 的忆灵技能 8「使其自动施放1次**不消耗充能**的【弑神登神】」✗。"),
})

with io.open(CHAR, "w", encoding="utf-8", newline="\n") as handle:
    json.dump(doc, handle, ensure_ascii=False, indent=2)
    handle.write("\n")
print("ok   1404: the gate is in and the 150-charge clause ships")

# ---- 3. the readings
text = io.open(JUDGE, encoding="utf-8").read()
ADD = '''
    /** ⭐ The gate: a charge that arrives WHILE 【血仇】 is already on must not be drained again. */
    @Test
    public void aChargeArrivingInBloodfeudIsNotDrainedAgain() {
        Character him = CharacterFactory.create(MYDEI, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(him),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        him.getBuffManager().addBuff(new StateBuff(BLOODFEUD, 9, true));
        battle.processRequests();

        him.getResources().gain(CHARGE, 100);
        battle.noteChangedResource(CHARGE);
        battle.fireTriggers(TriggerEvent.RESOURCE_CHANGED, him, battle.enemies.getFirst(), 0, 100);
        battle.processRequests();
        int charge = him.getResources().value(CHARGE);
        System.out.println("[mydei-skills] charge after entering bloodfeud with 100 = " + charge);

        Assertions.assertEquals(100, charge,
                "「充能达到 100 时消耗 100 点充能进入【血仇】状态」-- already in it, so nothing is drained again; "
                        + "without this the charge can never reach 150 and the next sentence is unreachable");
    }

    /** 「充能达到 150 点时，立即获得 1 个额外回合并自动施放【弑神登神】」. */
    @Test
    public void theHundredAndFiftyChargeCastsGodslayer() {
        Character him = CharacterFactory.create(MYDEI, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(him),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        him.getBuffManager().addBuff(new StateBuff(BLOODFEUD, 9, true));
        battle.processRequests();

        him.getResources().gain(CHARGE, 150);
        battle.noteChangedResource(CHARGE);
        double enemyBefore = battle.enemies.getFirst().getCurrentHp();
        battle.fireTriggers(TriggerEvent.RESOURCE_CHANGED, him, battle.enemies.getFirst(), 0, 150);
        battle.processRequests();
        int charge = him.getResources().value(CHARGE);
        double enemyAfter = battle.enemies.getFirst().getCurrentHp();
        System.out.println("[mydei-skills] 150: charge " + charge + " ; enemy " + enemyBefore + " -> " + enemyAfter
                + " ; SKILL slot now=" + him.getSkills().get(SkillType.SKILL).getSkillSlot());

        Assertions.assertEquals(0, charge, "「消耗 150 点充能」");
        Assertions.assertTrue(enemyAfter < enemyBefore, "【弑神登神】 was cast");
        Assertions.assertEquals(11, him.getSkills().get(SkillType.SKILL).getSkillSlot(),
                "the cast ran 槽 11, which is the row the sentence names");
    }
}
'''
if "theHundredAndFiftyChargeCastsGodslayer" in text:
    sys.exit("REFUSING: the readings are already there")
text = text.rstrip()
text = text[:-1].rstrip() + "\n" + ADD
io.open(JUDGE, "w", encoding="utf-8", newline="").write(text)
print("ok   the two readings are written")

io.open(MUTATOR, "w", encoding="utf-8", newline="\n").write('''"""Mutants for the gate and the 150-charge clause (round 7 of the goal).

  a: the `!self has_state 血仇` gate is removed -> the charge arriving in 【血仇】 is drained again;
  b: the charge threshold becomes 160          -> nothing fires at 150.
"""
import io
import json
import sys

CHAR = "src/main/resources/characters/1404.json"
mode = (sys.argv[1] if len(sys.argv) > 1 else "").strip().lower()
if mode not in ("a", "b", "off", "on"):
    sys.exit("usage: mut_1404_gate.py a|b|off|on")

GATE = "!self has_state \\u8840\\u4ec7"
with io.open(CHAR, encoding="utf-8") as handle:
    doc = json.load(handle)
hundred = next(entry for entry in doc["rules"]
               if isinstance(entry, dict) and entry.get("id") == "talent_enters_bloodfeud_at_a_hundred")
fifty = next(entry for entry in doc["rules"]
             if isinstance(entry, dict) and entry.get("id") == "bloodfeud_godslayer_at_a_hundred_and_fifty")
THRESHOLD = "self_resource:\\u5929\\u8d4b\\u5145\\u80fd >= 150"

if mode == "on":
    if GATE not in hundred["when"]:
        hundred["when"] = list(hundred["when"]) + [GATE]
    fifty["when"] = [THRESHOLD if ">= 1" in str(term) else term for term in fifty["when"]]
    print("restored: the gate and the 150 threshold")
else:
    if GATE not in hundred["when"]:
        sys.exit("REFUSING: the gate is not there")
    if mode in ("a", "off"):
        hundred["when"] = [term for term in hundred["when"] if term != GATE]
    if mode in ("b", "off"):
        fifty["when"] = [("self_resource:\\u5929\\u8d4b\\u5145\\u80fd >= 160" if ">= 1" in str(term) else term)
                         for term in fifty["when"]]
    print("MUTATION %s" % mode)

with io.open(CHAR, "w", encoding="utf-8", newline="\\n") as handle:
    json.dump(doc, handle, ensure_ascii=False, indent=2)
    handle.write("\\n")
''')
print("ok   the mutator is written")
