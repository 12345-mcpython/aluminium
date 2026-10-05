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
    " ⚠ 2026-10-02：**技能本身暂不写** ✓ —— 面板的 ATTACK 一项（主人生命上限）已经进去并被判据验过 ✓，但 `EnemySkill` 那一边取基数的方法名还没认定 ✗（判据报不到符号 ✗）⇒ 先登记 ✓，下一步读 `EnemySkill` 的取数 API 再补 ✓。", "")
    + " ⭐ 2026-10-02 补齐**忆灵技能 1**（`:316` ✓）：`element Quantum` ✓、"
      "`base ATTACK` ✓（本忆灵的 ATTACK 槽存的是**主人生命上限** ✓）、"
      "`percent 0.4` ✓（散文引用的那一级 ✓，整表 `0.2 → 0.56` ✓，同 1413 的处理 ✓）、"
      "`shape AoEAttack` ✓（文档「全体攻击」✓，验证器给的例子就是 `SingleAttack / AoEAttack / Blast` ✓）、"
      "`stance 30` ✓（文档「破韧值: 单体 0, **全体 30**, 扩散 0」✓）。"
      "⚠ 散文说 40.00% 而表给 Lv10 = 0.56 ✗ —— 本件取**散文那一级** ✓，与 1413 同例 ✓。")
json.dump(spec, io.open(SPEC, "w", encoding="utf-8", newline="\n"), ensure_ascii=False, indent=2)
print("ok   the attack block is back, with the level choice stated")

judge = io.open(JUDGE, encoding="utf-8").read()
if "theDragonSkillIsWhatTheDocumentSays" in judge:
    print("skip judge")
else:
    ADD = '''    /** ⭐ 忆灵技能 1 as the document states it, and the panel slot it scales off. */
    @Test
    public void theDragonSkillIsWhatTheDocumentSays() {
        MemospriteSpec spec = Memosprites.of(1407);
        Assertions.assertNotNull(spec.attack(), "the skill must be stated (忆灵技能 1)");
        Assertions.assertEquals("Quantum", spec.attack().element(), "document: 量子属性伤害");
        Assertions.assertEquals("ATTACK", spec.attack().base(), "scales off this memosprite's ATTACK slot");
        Assertions.assertEquals(0.4, spec.attack().percent(), 1e-9, "40.00%, the level the prose quotes");
        Assertions.assertEquals("AoEAttack", spec.attack().shape(), "document: 全体攻击");
        Assertions.assertEquals(30, spec.attack().stance(), 1e-9, "document: 全体 30");

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
