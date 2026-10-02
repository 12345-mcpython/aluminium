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
           '                // \u2b50 \u300c\u7b49\u540c\u4e8e\u53ec\u5524\u8005\u751f\u547d\u4e0a\u9650\u7684 X%\u300d (2026-10-02): a share of ANOTHER of the\n'
           '                // master\'s attributes. The plain branch below reads `master.getAttribute(attribute)` -- the\n'
           '                // SAME attribute the entry names -- which is why the 景元-style trick (\u300c\u795e\u541b\u300d = 66% of his\n'
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
    {"attribute": "HEALTH", "percent": 1.0, "source": "resource:\u65b0\u854a"},
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
                " \u2b50 2026-10-02 \u8865\u9f50\u6280\u80fd\uff1a\u300c\u5bf9\u654c\u65b9\u5168\u4f53\u9020\u6210\u7b49\u540c\u4e8e**\u900d\u8776 40.00% \u751f\u547d\u4e0a\u9650**"
                "\u7684\u91cf\u5b50\u5c5e\u6027\u4f24\u5bb3\u300d\uff08\u5fc6\u7075\u6280\u80fd 1\uff0c`:316`\uff09\u2713 \u2014\u2014 "
                "\u53e3\u5f84\u843d\u5728**\u9762\u677f**\u4e0a \u2713\uff08\u7167 `MemospriteSpec.Attack` \u7684 javadoc \u2713\uff1a"
                "\u201c\u4e3b\u4eba\u53e3\u5f84\u5c31\u628a\u4efd\u989d\u653e\u8fdb\u9762\u677f\u201d \u2713\uff09\uff1a"
                "`ATTACK` \u4e00\u9879 = **\u4e3b\u4eba\u751f\u547d\u4e0a\u9650\u7684 100%** \u2713\uff08`source: \"attr:HEALTH\"` \u2713\uff09\uff0c"
                "\u800c\u653b\u51fb\u518d\u6309**\u5fc6\u7075\u81ea\u5df1\u7684 `ATTACK`** \u53d6 40% \u2713"
                "\uff08\u2b50 `attr:` \u662f\u672c\u6b21\u65b0\u52a0\u7684\u4e00\u6863 \u2713\uff1a\u56e0\u4e3a\u539f\u6765\u90a3\u652f\u53ea\u80fd\u53d6"
                "**\u540c\u4e00\u4e2a**\u5c5e\u6027 \u2717\uff09\u3002"
                "\u26a0 `percent: 0.4` \u662f**\u6563\u6587\u5f15\u7528\u7684\u90a3\u4e00\u7ea7** \u2713\uff08\u6574\u8868 `0.2 \u2192 0.56` \u2713\uff0c"
                "\u540c 1413 \u7684\u5904\u7406 \u2713\uff09\uff1b\u26a0 `shape: \"AoEAttack\"` \u7167\u6587\u6863\u300c`\u5168\u4f53\u653b\u51fb`\u300d\u2713"
                "\uff08\u9a8c\u8bc1\u5668\u7ed9\u7684\u4f8b\u5b50\u5c31\u662f `SingleAttack / AoEAttack / Blast` \u2713\uff09\uff1b"
                "\u26a0 `stance: 30` \u662f\u6587\u6863\u300c\u7834\u97e7\u503c: \u5355\u4f53 0, **\u5168\u4f53 30**, \u6269\u6563 0\u300d\u2713\u3002")
json.dump(spec, io.open(SPEC, "w", encoding="utf-8", newline="\n"), ensure_ascii=False, indent=2)
print("ok   1407 spec: ATTACK slot + the AoE attack")

judge = io.open(JUDGE, encoding="utf-8").read()
if "Attack" in judge or "damageOf" in judge:
    print("skip judge")
else:
    MARKER = "    // ==================================================================\n"
    if MARKER not in judge:
        MARKER = None
    ADD = '''    /** \u2b50 The skill: 40% of the SUMMONER's Max HP, dealt to every enemy. */
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
