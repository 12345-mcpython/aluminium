"""The dragon's 忆灵技能 1 (2026-10-02): the spec's attack block + its judge.

Sentence (1407_遐蝶.md:316): 「对敌方全体造成等同于遐蝶40.00%生命上限的量子属性伤害」, 破韧值 单体 0 / 全体 30, and the
per-level table runs 0.2 (Lv1) -> 0.56 (Lv10); the prose quotes 0.4, so 0.4 is what is written, stated in the note (the
treatment 1413's spec documents).

The base: `ATTACK` on this memosprite, whose panel slot carries the SUMMONER's Max HP (`source: "attr:HEALTH"`, shipped
and judged last round) -- which is why the hit is 40% of Castorice's Max HP. `Memosprites.validateAttack` requires the
attack's base to appear in the panel, which it now does.

The judge states what only this data can: the spec loads, its attack block is what the document says, and the panel slot
the attack scales off really carries the summoner's Max HP. The arithmetic (base x multiplier) is the engine's existing
SkillData path, unchanged by this work.
ASCII only.
"""
import io
import json
import os

for junk in ("tools/.keep",):
    if os.path.exists(junk):
        os.remove(junk)
        print("removed " + junk)

SPEC = "src/main/resources/memosprites/1407.json"
JUDGE = "src/test/java/com/laosun/aluminium/test/DragonPanelTest.java"

spec = json.load(io.open(SPEC, encoding="utf-8"))
spec["attack"] = {
    "element": "Quantum",
    "base": "ATTACK",
    "percent": 0.4,
    "shape": "AoEAttack",
    "stance": 30,
}
spec["note"] = (spec.get("note", "").replace(
    " \u26a0 2026-10-02\uff1a**\u6280\u80fd\u672c\u8eab\u6682\u4e0d\u5199** \u2713 \u2014\u2014 \u9762\u677f\u7684 ATTACK \u4e00\u9879\uff08\u4e3b\u4eba\u751f\u547d\u4e0a\u9650\uff09\u5df2\u7ecf\u8fdb\u53bb\u5e76\u88ab\u5224\u636e\u9a8c\u8fc7 \u2713\uff0c\u4f46 `EnemySkill` \u90a3\u4e00\u8fb9\u53d6\u57fa\u6570\u7684\u65b9\u6cd5\u540d\u8fd8\u6ca1\u8ba4\u5b9a \u2717\uff08\u5224\u636e\u62a5\u4e0d\u5230\u7b26\u53f7 \u2717\uff09\u21d2 \u5148\u767b\u8bb0 \u2713\uff0c\u4e0b\u4e00\u6b65\u8bfb `EnemySkill` \u7684\u53d6\u6570 API \u518d\u8865 \u2713\u3002", "")
    + " \u2b50 2026-10-02 \u8865\u9f50**\u5fc6\u7075\u6280\u80fd 1**\uff08`:316` \u2713\uff09\uff1a`element Quantum` \u2713\u3001"
      "`base ATTACK` \u2713\uff08\u672c\u5fc6\u7075\u7684 ATTACK \u69fd\u5b58\u7684\u662f**\u4e3b\u4eba\u751f\u547d\u4e0a\u9650** \u2713\uff09\u3001"
      "`percent 0.4` \u2713\uff08\u6563\u6587\u5f15\u7528\u7684\u90a3\u4e00\u7ea7 \u2713\uff0c\u6574\u8868 `0.2 \u2192 0.56` \u2713\uff0c\u540c 1413 \u7684\u5904\u7406 \u2713\uff09\u3001"
      "`shape AoEAttack` \u2713\uff08\u6587\u6863\u300c\u5168\u4f53\u653b\u51fb\u300d\u2713\uff0c\u9a8c\u8bc1\u5668\u7ed9\u7684\u4f8b\u5b50\u5c31\u662f `SingleAttack / AoEAttack / Blast` \u2713\uff09\u3001"
      "`stance 30` \u2713\uff08\u6587\u6863\u300c\u7834\u97e7\u503c: \u5355\u4f53 0, **\u5168\u4f53 30**, \u6269\u6563 0\u300d\u2713\uff09\u3002"
      "\u26a0 \u6563\u6587\u8bf4 40.00% \u800c\u8868\u7ed9 Lv10 = 0.56 \u2717 \u2014\u2014 \u672c\u4ef6\u53d6**\u6563\u6587\u90a3\u4e00\u7ea7** \u2713\uff0c\u4e0e 1413 \u540c\u4f8b \u2713\u3002")
json.dump(spec, io.open(SPEC, "w", encoding="utf-8", newline="\n"), ensure_ascii=False, indent=2)
print("ok   the attack block is back, with the level choice stated")

judge = io.open(JUDGE, encoding="utf-8").read()
if "theDragonSkillIsWhatTheDocumentSays" in judge:
    print("skip judge")
else:
    ADD = '''    /** \u2b50 忆灵技能 1 as the document states it, and the panel slot it scales off. */
    @Test
    public void theDragonSkillIsWhatTheDocumentSays() {
        MemospriteSpec spec = Memosprites.of(1407);
        Assertions.assertNotNull(spec.attack(), "the skill must be stated (忆灵技能 1)");
        Assertions.assertEquals("Quantum", spec.attack().element(), "document: \u91cf\u5b50\u5c5e\u6027\u4f24\u5bb3");
        Assertions.assertEquals("ATTACK", spec.attack().base(), "scales off this memosprite's ATTACK slot");
        Assertions.assertEquals(0.4, spec.attack().percent(), 1e-9, "40.00%, the level the prose quotes");
        Assertions.assertEquals("AoEAttack", spec.attack().shape(), "document: \u5168\u4f53\u653b\u51fb");
        Assertions.assertEquals(30, spec.attack().stance(), 1e-9, "document: \u5168\u4f53 30");

        Character master = CharacterFactory.create(1407, 80, false, null, null, 0);
        Summon dragon = SummonFactory.memosprite(master, spec, name -> 34000);
        Assertions.assertEquals(master.getAttribute(AttributeType.HEALTH).get(),
                dragon.getAttribute(AttributeType.ATTACK).get(), 1e-6,
                "and that slot carries the summoner's Max HP, so the hit is 40% of it");
    }

'''
    judge = judge.rstrip()
    assert judge.endswith("}")
    judge = judge[:-1].rstrip() + "\n\n" + ADD + "}\n"
    io.open(JUDGE, "w", encoding="utf-8", newline="").write(judge)
    print("ok   judge extended")
