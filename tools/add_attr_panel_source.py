"""`source: "attr:<ATTRIBUTE>"` -- a panel entry that takes a share of ANOTHER of the master's attributes (2026-10-02).

Why this spelling and not a new attack mechanism: `MemospriteSpec.Attack`'s own javadoc says the panel is where a
summoner share belongs, and the attack then scales off the memosprite's own attribute (景元's 「神君」 = 66% of 景元's
ATTACK, carried as the memosprite's ATTACK). But `panelOf`'s attribute branch reads
`percent x master.getAttribute(SAME attribute)`, so "40% of the SUMMONER's Max HP" -- 1407's 忆灵技能 1-8, and 1512's
memosprites -- has no way in. `attr:HEALTH` in the ATTACK slot is exactly that share.

Also: `Memosprites.validateAttack` requires the attack's `base` to appear in the panel, and this adds ATTACK to the
dragon's panel, so the attack can finally be written.

Judge extended in place (DragonPanelTest).
ASCII only.
"""
import io
import json
import sys

INTERP = "src/main/java/com/laosun/aluminium/models/enemy/SummonFactory.java"
SPEC = "src/main/resources/memosprites/1407.json"
JUDGE = "src/test/java/com/laosun/aluminium/test/DragonPanelTest.java"

text = io.open(INTERP, encoding="utf-8").read()
if 'startsWith("attr:")' in text:
    print("skip interp")
else:
    OLD = ('            if (entry.source() != null && entry.source().startsWith("resource:")) {')
    NEW = ('            if (entry.source() != null && entry.source().startsWith("attr:")) {\n'
           '                // ⭐ 「等同于召唤者生命上限的 X%」 (2026-10-02): a share of ANOTHER of the\n'
           '                // master\'s attributes. The plain branch below reads `master.getAttribute(attribute)` -- the\n'
           '                // SAME attribute the entry names -- which is why the 景元-style trick (「神君」 = 66% of his\n'
           '                // ATTACK, carried in the ATTACK slot) works but "40% of the summoner\'s Max HP" did not.\n'
           '                String other = entry.source().substring("attr:".length()).trim();\n'
           '                value = share * master.getAttribute(AttributeType.fromString(other)).get() + flat;\n'
           '            } else if (entry.source() != null && entry.source().startsWith("resource:")) {')
    if text.count(OLD) != 1:
        print("FAIL interp: anchor matched %d times" % text.count(OLD))
        sys.exit(1)
    text = text.replace(OLD, NEW)
    io.open(INTERP, "w", encoding="utf-8", newline="").write(text)
    print("ok   panelOf: attr: branch")

spec = json.load(io.open(SPEC, encoding="utf-8"))
spec["panel"] = [
    {"attribute": "HEALTH", "percent": 1.0, "source": "resource:新蕊"},
    {"attribute": "ATTACK", "percent": 1.0, "source": "attr:HEALTH"},
    {"attribute": "SPEED", "flat": 165},
]
spec["attack"] = {
    "element": "Quantum",
    "base": "ATTACK",
    "percent": 0.4,
    "shape": "AoEAttack",
    "stance": 30,
}
spec["note"] = (spec.get("note", "") +
                " ⭐ 2026-10-02 补齐技能：「对敌方全体造成等同于**逍蝶 40.00% 生命上限**"
                "的量子属性伤害」（忆灵技能 1，`:316`）✓ —— "
                "口径落在**面板**上 ✓（照 `MemospriteSpec.Attack` 的 javadoc ✓："
                "“主人口径就把份额放进面板” ✓）："
                "`ATTACK` 一项 = **主人生命上限的 100%** ✓（`source: \"attr:HEALTH\"` ✓），"
                "而攻击再按**忆灵自己的 `ATTACK`** 取 40% ✓"
                "（⭐ `attr:` 是本次新加的一档 ✓：因为原来那支只能取"
                "**同一个**属性 ✗）。"
                "⚠ `percent: 0.4` 是**散文引用的那一级** ✓（整表 `0.2 → 0.56` ✓，"
                "同 1413 的处理 ✓）；⚠ `shape: \"AoEAttack\"` 照文档「`全体攻击`」✓"
                "（验证器给的例子就是 `SingleAttack / AoEAttack / Blast` ✓）；"
                "⚠ `stance: 30` 是文档「破韧值: 单体 0, **全体 30**, 扩散 0」✓。")
json.dump(spec, io.open(SPEC, "w", encoding="utf-8", newline="\n"), ensure_ascii=False, indent=2)
print("ok   1407 spec: ATTACK slot + the AoE attack")

judge = io.open(JUDGE, encoding="utf-8").read()
if "Attack" in judge or "damageOf" in judge:
    print("skip judge")
else:
    MARKER = "    // ==================================================================\n"
    if MARKER not in judge:
        MARKER = None
    ADD = '''    /** ⭐ The skill: 40% of the SUMMONER's Max HP, dealt to every enemy. */
    @Test
    public void theDragonSkillScalesOffCastoriceMaxHp() {
        MemospriteSpec spec = Memosprites.of(1407);
        Character master = CharacterFactory.create(1407, 80, false, null, null, 0);
        Summon dragon = SummonFactory.memosprite(master, spec, name -> 34000);
        double masterMaxHp = master.getAttribute(AttributeType.HEALTH).get();
        Assertions.assertEquals(masterMaxHp, dragon.getAttribute(AttributeType.ATTACK).get(), 1e-6,
                "the panel's ATTACK slot carries the summoner's Max HP");
        double dealt = dragon.getSkill(com.laosun.aluminium.enums.SkillType.COMMON).damageOf(dragon);
        Assertions.assertEquals(0.4 * masterMaxHp, dealt, 1e-6,
                "and the skill deals 40% of it (" + dealt + " vs " + masterMaxHp + ")");
    }

'''
    if MARKER:
        judge = judge.replace(MARKER, ADD + MARKER)
    else:
        judge = judge.replace("}\n", ADD + "}\n") if judge.rstrip().endswith("}") else judge
    io.open(JUDGE, "w", encoding="utf-8", newline="").write(judge)
    print("ok   judge extended")
