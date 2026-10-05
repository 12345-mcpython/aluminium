"""`damage_is_additional`, and the passage ode's second clause that needs it (2026-10-02).

The keyword's mirror is `damage_is_attack`, whose own comment states the whole case: "additional damage is settled as a real instance with
`notCountsAsAttack()` set … and it fires `DEALING_DAMAGE` like any other instance — so a rule that reacts to 'my attack hit a burning target'
would react to its own additional damage, forever. ⚠ `!` cannot express the guard (negation is only for party conditions), so the guard is
stated positively." The COMPLEMENT of that guard is what 1415's ode of passage needs -- 「缇宝施放追加攻击触发缇宝的结界的附加伤害时，会额外造成 #1 次附加伤害」 --
and it must be a keyword of its own, because `TriggerTable` records the bug from folding the two together: with `!isCountsAsAttack()` inside
`damage_is_attack`, cone 23008's energy clause read +0.0.

So: a second bare keyword, positive in its own name, negative in what it tests.
"""
import io
import json
import sys

T = "src/main/java/com/laosun/aluminium/models/TriggerTable.java"
CHARS = "src/main/resources/characters/1403.json"
SKILLS = "src/main/resources/data/skills.json"

tab = io.open(T, encoding="utf-8").read()

# ---- 1) the constant, beside its twin ----
CONST_ANCHOR = '    static final String DAMAGE_IS_ATTACK = "damage_is_attack";'
CONST_NEW = '''    static final String DAMAGE_IS_ATTACK = "damage_is_attack";

    /**
     * The bare keyword {@code damage_is_additional}: "the instance being settled is ADDITIONAL damage".
     *
     * <p>⭐ The complement of {@link #DAMAGE_IS_ATTACK}, and it has to be its own keyword: that one is stated positively on purpose (`!` is
     * only for party conditions), so a rule that needs the OTHER side -- 1415's ode of passage, 「缇宝施放追加攻击触发缇宝的结界的附加伤害时，会额外造成
     * #1 次附加伤害」 -- had no way to say it. ⚠ The negation lives HERE, where the name announces it; `TriggerTable` records what happened
     * when it lived inside {@code damage_is_attack} instead: cone 23008's energy clause read +0.0, because every ordinary attack failed the
     * guard it was written to pass (2026-09-30).
     */
    static final String DAMAGE_IS_ADDITIONAL = "damage_is_additional";'''

# ---- 2) the parse site, mirroring the twin's block ----
PARSE_ANCHOR = "            return new DamageIsAttack(raw);\n        }"
PARSE_NEW = """            return new DamageIsAttack(raw);
        }

        // ⭐ The complement (2026-10-02): the instance is additional damage. Positive in its own name, negative in what it tests -- see
        // DAMAGE_IS_ADDITIONAL for why that is the honest spelling and not a double negative.
        if (text.trim().equalsIgnoreCase(DAMAGE_IS_ADDITIONAL)) {
            TriggerEvent event = TriggerEvent.fromString(spec.getOn());
            if (event == null || !DAMAGE_CARRYING_EVENTS.contains(event)) {
                throw new IllegalArgumentException(
                        "Condition '" + raw + "' asks whether the instance is additional damage, but " + spec.getOn()
                                + " carries no damage instance; it belongs on an event that settles one "
                                + "(source: " + spec.getSource() + ")");
            }
            return new DamageIsAdditional(raw);
        }"""

# ---- 3) the condition class, beside its twin ----
CLASS_ANCHOR = "    private static final class DamageElementIsSelf implements Condition {"
CLASS_NEW = """    /** ⭐ The complement of {@link #DAMAGE_IS_ATTACK}: the instance is additional damage, not an ordinary attack. */
    private static final class DamageIsAdditional implements Condition {
        private final String raw;

        DamageIsAdditional(String raw) {
            this.raw = raw;
        }

        @Override
        public boolean test(TriggerContext ctx) {
            // ⚠ The negation is correct HERE and was a bug next door: `Damage.countsAsAttack` defaults to true, so `!…` is exactly
            // "additional damage", which is what this keyword names. In `damage_is_attack` the same expression meant the opposite of
            // that keyword's contract (cone 23008's clause read +0.0).
            return ctx.damage() != null && !ctx.damage().isCountsAsAttack();
        }

        @Override
        public String source() {
            return raw;
        }
    }

""" + CLASS_ANCHOR

for body, old, label in ((tab, CONST_ANCHOR, "the constant"), (tab, PARSE_ANCHOR, "the parse site"),
                         (tab, CLASS_ANCHOR, "the class anchor")):
    n = body.count(old)
    print("anchor %-16s : %d" % (label, n))
    if n != 1:
        sys.exit("REFUSING: %s occurs %d times -- nothing written" % (label, n))

tab = tab.replace(CONST_ANCHOR, CONST_NEW).replace(PARSE_ANCHOR, PARSE_NEW).replace(CLASS_ANCHOR, CLASS_NEW)
io.open(T, "w", encoding="utf-8", newline="\n").write(tab)
print("ok   damage_is_additional is wired (constant, parse site, condition)")

# ---- 4) the passage ode's second clause ----
table = json.load(io.open(SKILLS, encoding="utf-8"))
rows = table["11415"]["15"].get("param_list") or []
if not rows or any(r[0] != 1 for r in rows):
    sys.exit("REFUSING: #1 is not 1 at every level")
print("ok   #1 is 1 at all %d levels, so times = 1 is what the data says" % len(rows))

doc = json.load(io.open(CHARS, encoding="utf-8"))
rules = doc if isinstance(doc, list) else doc.get("rules", [])
RULE_ID = "memosprite_ode_of_passage_extra_instance_when_the_zone_fires"
if any(r.get("id") == RULE_ID for r in rules):
    sys.exit("REFUSING: %s is already there" % RULE_ID)

rules.append({
    "id": RULE_ID,
    "on": "DAMAGE_SETTLED",
    "when": ["actor == self", "damage_is_additional"],
    "do": [{
        "op": "DAMAGE",
        "times": 1,                      # 「额外造成 #1 次」, and #1 is 1 at EVERY level
        "scale": "original_damage",      # of the instance that just settled -- the zone's own additional damage
        "percent": 1.0,
        "element": "Ice",                # 1415's zone states Ice (1415.json, the 24% rider)
        "target": "target",              # at the unit that took it
    }],
    "source": ("1415 昔涟 忆灵技能 14 「献予「门径」之诗」（数据槽位 15）："
               "「缇宝施放追加攻击触发缇宝的结界的附加伤害时，"
               "会**额外造成 #1 次附加伤害**。」"),
    "note": ("⭐ 三件都是**现成**的：`times`（DAMAGE op 已收，注释写着 \"settle N independent times\"）、"
             "`original_damage`（要求 `DAMAGE_SETTLED` + `element`，1003 已在用）、以及 `FOLLOW_UP`/`ADDITIONAL` 这条**唯一入口**。"
             "⭐ 而 `damage_is_additional` 是**本轮新增**的——它是 `damage_is_attack` 的补面，"
             "之所以必须是独立关键字，`TriggerTable` 里记着原因：否定只对队伍条件开放。"),
})

if isinstance(doc, list):
    out = rules
else:
    doc["rules"] = rules
    out = doc
io.open(CHARS, "w", encoding="utf-8", newline="\n").write(json.dumps(out, ensure_ascii=False, indent=2) + "\n")
print("ok   1403 now carries %s (%d rules)" % (RULE_ID, len(rules)))
