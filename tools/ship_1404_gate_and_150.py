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
GATE = "!self has_state \u8840\u4ec7"
if GATE in hundred["when"]:
    sys.exit("REFUSING: the gate is already there")
hundred["when"] = list(hundred["when"]) + [GATE]
hundred["note"] = (str(hundred.get("note")) + " "
                   "\u2b50 **2026-10-02 \u8865\u4e0a `!self has_state \u8840\u4ec7`** \u2713\uff1a\u2757 \u5b9e\u6d4b\u2014\u2014\u6ca1\u6709\u5b83\u65f6\uff0c"
                   "\u8fd9\u6761\u5728**\u5df2\u5728\u3010\u8840\u4ec7\u3011\u4e2d**\u4ecd\u4f1a\u518d\u6b21\u6d88\u8017 100 \u70b9 \u2717 \u21d2 \u5145\u80fd**\u6c38\u8fdc\u6512\u4e0d\u5230 150** \u2717"
                   "\uff08\u800c\u540c\u4e00\u6761\u5929\u8d4b\u91cc\u7684\u4e0b\u4e00\u53e5\u6b63\u9700\u8981 150 \u2713\uff09\u3002\u26a0 \u5426\u5b9a\u7684\u5199\u6cd5\uff1a"
                   "`!` \u524d\u7f00\u65e9\u5c31\u88ab\u89e3\u6790 \u2713\uff08\u53ea\u5141\u8bb8\u5426\u5b9a `PartyCondition` \u2713\uff0c\u800c `has_state` \u8bfb\u7684\u5c31\u662f\u5355\u4f4d\u7684\u589e\u76ca \u2713\uff09\u3002")

# ---- 2. the 150-charge clause
if any(isinstance(entry, dict) and entry.get("id") == NEW_ID for entry in rules):
    sys.exit("REFUSING: the 150-charge rule is already there")
rules.append({
    "on": "RESOURCE_CHANGED",
    "id": NEW_ID,
    "when": ["resource_changed:\u5929\u8d4b\u5145\u80fd", "self has_state \u8840\u4ec7", "self_resource:\u5929\u8d4b\u5145\u80fd >= 150"],
    "do": [
        {"op": "SPEND_RESOURCE", "resource": "\u5929\u8d4b\u5145\u80fd", "amount": 150},
        {"op": "EXTRA_TURN", "target": "self"},
        {"op": "REPLACE_SKILL", "skill": "SKILL", "skill_id": 11, "turns": 1, "target": "self"},
        {"op": "CAST_SKILL", "skill": "SKILL", "target": "target"},
    ],
    "source": ("1404 \u4e07\u654c \u5929\u8d4b \u4ee5\u8840\u8fd8\u8840 (140404)\uff1a\u300c\u3010\u8840\u4ec7\u3011\u72b6\u6001\u671f\u95f4\u5145\u80fd\u8fbe\u5230 **150** \u70b9\u65f6\uff0c"
               "\u4e07\u654c\u7acb\u5373\u83b7\u5f97 1 \u4e2a\u989d\u5916\u56de\u5408\u5e76\u81ea\u52a8\u65bd\u653e\u3010\u5f11\u795e\u767b\u795e\u3011\u300d"),
    "note": (
        "\u300c\u5145\u80fd\u8fbe\u5230 **150** \u70b9\u65f6\uff0c\u7acb\u5373\u83b7\u5f97 1 \u4e2a**\u989d\u5916\u56de\u5408**\u5e76**\u81ea\u52a8\u65bd\u653e\u3010\u5f11\u795e\u767b\u795e\u3011**\u300d\u21d2 \u56db\u4e2a\u5df2\u6709\u8bcd \u2713\uff1a"
        "`SPEND_RESOURCE{150}` \u2713\uff08\u3010\u5f11\u795e\u767b\u795e\u3011\u81ea\u5df1\u90a3\u4e00\u884c\u5c31\u5199\u7740\u300c\u6d88\u8017 150 \u70b9\u5145\u80fd\u300d \u2713\uff09\uff1b`EXTRA_TURN` \u2713"
        "\uff08\u5df2\u51fa\u8d27\uff1a`1102` \u5e0c\u513f\u3001`1309` \u77e5\u66f4\u9e1f \u2713\uff09\uff1b`REPLACE_SKILL{skill_id: **11**, turns: 1}` \u2713 \u2014\u2014 "
        "\u69fd 11 \u7684\u7834\u97e7 **90/60** \u4e0e\u8bed\u6599\u7ed9\u3010\u5f11\u795e\u767b\u795e\u3011\u7684\u9010\u5b57\u76f8\u7b26 \u2713\uff0c\u800c**\u6362\u88c5\u4f1a\u4f20\u5230\u88ab\u547d\u4ee4\u7684\u65bd\u653e** \u2713"
        "\uff08\u540c\u7b49\u7ea7\u5bf9\u6bd4\uff1a\u6709\u6362\u88c5 767.585 vs \u65e0\u6362\u88c5 628.024 \u2713\uff09\uff1b`CAST_SKILL{SKILL}` \u2713\u3002"
        "\u26a0 \u4ecd\u767b\u8bb0 \u2713\uff1a\u300c\u4e0b\u4e00\u6b21\u3010\u5f11\u795e\u767b\u795e\u3011**\u4f18\u5148\u653b\u51fb\u6307\u5b9a\u654c\u65b9\u5355\u4f53**\u300d\uff08\u7ec8\u7ed3\u6280\u6807\u8bb0\u7684\u76ee\u6807 \u2717\uff09"
        "\u4e0e `1415` \u7684\u5fc6\u7075\u6280\u80fd 8\u300c\u4f7f\u5176\u81ea\u52a8\u65bd\u653e1\u6b21**\u4e0d\u6d88\u8017\u5145\u80fd**\u7684\u3010\u5f11\u795e\u767b\u795e\u3011\u300d\u2717\u3002"),
})

with io.open(CHAR, "w", encoding="utf-8", newline="\n") as handle:
    json.dump(doc, handle, ensure_ascii=False, indent=2)
    handle.write("\n")
print("ok   1404: the gate is in and the 150-charge clause ships")

# ---- 3. the readings
text = io.open(JUDGE, encoding="utf-8").read()
ADD = '''
    /** \u2b50 The gate: a charge that arrives WHILE 【\u8840\u4ec7】 is already on must not be drained again. */
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
                "\u300c\u5145\u80fd\u8fbe\u5230 100 \u65f6\u6d88\u8017 100 \u70b9\u5145\u80fd\u8fdb\u5165\u3010\u8840\u4ec7\u3011\u72b6\u6001\u300d-- already in it, so nothing is drained again; "
                        + "without this the charge can never reach 150 and the next sentence is unreachable");
    }

    /** \u300c\u5145\u80fd\u8fbe\u5230 150 \u70b9\u65f6\uff0c\u7acb\u5373\u83b7\u5f97 1 \u4e2a\u989d\u5916\u56de\u5408\u5e76\u81ea\u52a8\u65bd\u653e\u3010\u5f11\u795e\u767b\u795e\u3011\u300d. */
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

        Assertions.assertEquals(0, charge, "\u300c\u6d88\u8017 150 \u70b9\u5145\u80fd\u300d");
        Assertions.assertTrue(enemyAfter < enemyBefore, "\u3010\u5f11\u795e\u767b\u795e\u3011 was cast");
        Assertions.assertEquals(11, him.getSkills().get(SkillType.SKILL).getSkillSlot(),
                "the cast ran \u69fd 11, which is the row the sentence names");
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
