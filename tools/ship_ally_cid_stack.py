"""A selector that names a character by cid, and 1415's sky-ode stack sentence (2026-10-02).

Reader (slot 19's first sentence): 「德谬歌施放忆灵技时，使风堇获得2层【献予「天空」之诗】。」 The rule lives in the memosprite's own file, where `self` is the MASTER (measured), so it
must reach ANOTHER character -- and the selector set had nothing that names one: `party_first` is a position, `next_ally` is a position, `lowest_hp_ally` and
`random_ally_below_half_energy` are predicates. None of them means 「风堇」.

So: a PREFIX family. `requireSelectorSpelling` is a closed set for good reason (a typo used to fall back to "the owner" silently), so the family is admitted explicitly by prefix,
and the resolver gets one branch before its exact-name switch. `Character` gains the cid reader.
"""
import io
import sys

CANHIT = "src/main/java/com/laosun/aluminium/models/CanHit.java"
CHAR = "src/main/java/com/laosun/aluminium/models/Character.java"
INT = "src/main/java/com/laosun/aluminium/models/TriggerInterpreter.java"
MEMOSPRITE = "src/main/resources/characters/1415.json"
SE = "src/main/resources/data/skill_effects.json"
SLOT = 19
MARK = "\u732e\u4e88\u300c\u5929\u7a7a\u300d\u4e4b\u8bd7"
CID = 1409

canhit = io.open(CANHIT, encoding="utf-8").read()
char = io.open(CHAR, encoding="utf-8").read()
interp = io.open(INT, encoding="utf-8").read()

# ---- 1) the cid reader on Character ----
CID_ANCHOR = "    private int cid;"
CID_NEW = """    private int cid;

    /**
     * This character's own data id (its file name under {@code resources/characters/}) (2026-10-02).
     *
     * <p>Reader: the {@code ally_cid:<cid>} selector -- 「德谬歌施放忆灵技时，使<b>风堇</b>获得2层…」 names a character, and nothing in the selector set could.
     */
    public int getCid() {
        return cid;
    }"""

# ---- 2) the selector family: spelling ----
SPELL_ANCHOR = "        requireSelectorSpelling(effect.getTarget(), \"target\", op, spec);"
SPELL_NEW = """        // \u2b50 A selector FAMILY, admitted by prefix (2026-10-02): `ally_cid:<cid>` names a character outright. The closed set below stays closed --
        // this is an explicit door, not a fallback, which is why it is checked BEFORE the membership test.
        if (effect.getTarget().startsWith(ALLY_CID_PREFIX)) {
            String rest = effect.getTarget().substring(ALLY_CID_PREFIX.length()).trim();
            try {
                Integer.parseInt(rest);
            } catch (NumberFormatException notANumber) {
                throw new IllegalArgumentException(
                        "Op " + op + " names \\"" + effect.getTarget() + "\\", whose part after \\"" + ALLY_CID_PREFIX
                                + "\\" must be a cid (source: " + spec.getSource() + ")");
            }
            return;
        }
        requireSelectorSpelling(effect.getTarget(), "target", op, spec);"""

# ---- 3) the selector family: resolution ----
RESOLVE_ANCHOR = "    private static CanHit resolveSelector(String selector, EffectSpec effect, TriggerContext ctx) {"
RESOLVE_NEW = """    /** \u2b50 \u300c\u5411**\u98ce\u5807**\u2026\u300d: a selector that names a character by cid (2026-10-02). */
    static final String ALLY_CID_PREFIX = "ally_cid:";

    /** The party member whose cid is this one, or {@code null} when nobody matches. */
    private static CanHit allyWithCid(TriggerContext ctx, int cid) {
        if (ctx.battle() == null) {
            return null;
        }
        for (Character member : ctx.battle().characters) {
            if (member.getCid() == cid) {
                return member;
            }
        }
        return null;
    }

""" + RESOLVE_ANCHOR

# the branch itself, right at the top of the switch's method
BODY_ANCHOR = """        return switch (selector) {
            case "self" -> ctx.owner();"""
BODY_NEW = """        if (selector.startsWith(ALLY_CID_PREFIX)) {
            int cid = Integer.parseInt(selector.substring(ALLY_CID_PREFIX.length()).trim());
            return require(allyWithCid(ctx, cid), selector, ctx);
        }
        return switch (selector) {
            case "self" -> ctx.owner();"""

for body, old, label in ((interp, SPELL_ANCHOR, "the spelling check"),
                         (interp, RESOLVE_ANCHOR, "the resolver"), (interp, BODY_ANCHOR, "the switch head")):
    n = body.count(old)
    print("anchor %-20s : %d" % (label, n))
    if n != 1:
        sys.exit("REFUSING: %s occurs %d times -- nothing written" % (label, n))

# \u26a0 `private int cid;` appears TWICE in Character (the field and the Builder's own) -- only the FIRST is the field
n = char.count(CID_ANCHOR)
print("anchor %-20s : %d (replacing the first only)" % ("the cid field", n))
if n < 1:
    sys.exit("REFUSING: the cid field was not found")
io.open(CHAR, "w", encoding="utf-8", newline="\n").write(char.replace(CID_ANCHOR, CID_NEW, 1))
interp = interp.replace(SPELL_ANCHOR, SPELL_NEW).replace(RESOLVE_ANCHOR, RESOLVE_NEW).replace(BODY_ANCHOR, BODY_NEW)
io.open(INT, "w", encoding="utf-8", newline="\n").write(interp)
print("ok   ally_cid:<cid> is wired (spelling + resolver + Character.getCid)")

# ---- 4) the content: the sky ode's first sentence ----
doc = io.open(MEMOSPRITE, encoding="utf-8")
import json
doc = json.load(io.open(MEMOSPRITE, encoding="utf-8"))
rules = doc if isinstance(doc, list) else doc.get("rules", [])
RULE_ID = "memosprite_ode_of_sky_stacks_hyacine_when_it_casts"
if any(r.get("id") == RULE_ID for r in rules):
    sys.exit("REFUSING: %s is already there" % RULE_ID)

rules.append({
    "id": RULE_ID,
    "on": "CAST_SETUP",
    "when": ["actor is_summon"],
    "do": [{
        "op": "ADD_STACK",
        "buff": MARK,
        "amount": 2,
        "permanent": True,
        "target": "ally_cid:" + str(CID),
    }],
    "source": ("1415 \u6614\u6d9f \u5fc6\u7075\u6280\u80fd 19 \u300c\u732e\u4e88\u300c\u5929\u7a7a\u300d\u4e4b\u8bd7\u300d\uff08\u6570\u636e\u69fd\u4f4d 19\uff0cSkillID 1141519\uff09\uff1a"
               "\u300c**\u5fb7\u8c2c\u6b4c\u65bd\u653e\u5fc6\u7075\u6280\u65f6\uff0c\u4f7f\u98ce\u5807\u83b7\u5f972\u5c42\u3010" + MARK + "\u3011**\u3002\u300d"),
    "note": ("\u2b50 \u539f\u53e5\u70b9\u540d**\u98ce\u5807**\uff0c\u800c\u9009\u62e9\u5668\u96c6\u5408\u91cc\u6ca1\u6709\u201c\u70b9\u540d\u67d0\u4e2a\u89d2\u8272\u201d\u7684\u4e1c\u897f\uff08**\u5b9e\u6d4b**\uff1a`party_first`\u2192\u4f4d\u7f6e\u3001"
             "`next_ally`\u2192\u4f4d\u7f6e\u3001`lowest_hp_ally`/`random_ally_below_half_energy`\u2192\u8c13\u8bcd\uff09\u21d2 **\u672c\u8f6e\u52a0\u4e86 `ally_cid:<cid>`**\u3002"
             "\u2b50 `ADD_STACK` \u8981\u6c42 `buff` + \u6301\u7eed\uff08**\u5b9e\u6d4b**\uff09\uff0c\u6240\u4ee5 `permanent: true`\uff1b\u800c\u201c2 \u5c42\u201d\u5c31\u662f\u539f\u53e5\u7684\u6570\u5b57\u3002"),
})
if isinstance(doc, list):
    out = rules
else:
    doc["rules"] = rules
    out = doc
io.open(MEMOSPRITE, "w", encoding="utf-8", newline="\n").write(json.dumps(out, ensure_ascii=False, indent=2) + "\n")
print("ok   1415 now carries %s (%d rules)" % (RULE_ID, len(rules)))

effects = json.load(io.open(SE, encoding="utf-8"))
if str(SLOT) in effects.get("11415", {}):
    effects["11415"][str(SLOT)]["note"] = (effects["11415"][str(SLOT)].get("note", "") +
                                           " \u2b50 2026-10-02\uff1a\u540c\u4e00\u6761\u6280\u80fd\u7684**\u53e6\u4e00\u53e5**\uff08\u5c42\u6570\uff09\u4e5f\u5df2\u6210\u53e5\u3002")
    io.open(SE, "w", encoding="utf-8", newline="\n").write(json.dumps(effects, ensure_ascii=False, indent=2) + "\n")
    print("ok   skill_effects.json: 11415/%d note updated (the entry already existed)" % SLOT)

JUDGE = '''package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.models.skill.SkillExecutor;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1415's memosprite skill 19, first sentence: \u300c\u5fb7\u8c2c\u6b4c\u65bd\u653e\u5fc6\u7075\u6280\u65f6\uff0c\u4f7f\u98ce\u5807\u83b7\u5f972\u5c42\u3010\u732e\u4e88\u300c\u5929\u7a7a\u300d\u4e4b\u8bd7\u3011\u300d (2026-10-02).
 *
 * <p>\u2b50 TWO-SIDED, which is the whole point of the new `ally_cid:` selector: the NAMED character gets 2 stacks, and a different ally present in the same battle gets 0. A
 * selector that quietly fell back to "the owner" -- or to "everybody" -- would pass one half and fail the other.
 */
public class SkyOdeStackTest {
    private static final int LEVEL = 80;
    private static final int CYRENE = 1415;
    private static final int HYACINE = 1409;
    private static final int OTHER = 1002;
    private static final int MONSTER = 1002011;
    private static final String MARK = "\\u732e\\u4e88\\u300c\\u5929\\u7a7a\\u300d\\u4e4b\\u8bd7";

    @Test
    public void theNamedCharacterGetsTheStacksAndNobodyElseDoes() {
        Character cyrene = CharacterFactory.create(CYRENE, LEVEL);
        Character hyacine = CharacterFactory.create(HYACINE, LEVEL);
        Character other = CharacterFactory.create(OTHER, LEVEL);
        Battle battle = new Battle(List.of(cyrene, hyacine, other),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        cyrene = battle.characters.get(0);
        hyacine = battle.characters.get(1);
        other = battle.characters.get(2);

        var demiurge = battle.summonServant(cyrene);
        battle.processRequests();
        Assertions.assertNotNull(demiurge, "precondition: the memosprite is out");
        Assertions.assertNotNull(demiurge.skillAt(1), "precondition: slot 1 exists");

        SkillExecutor.execute(battle, demiurge.skillAt(1), demiurge, List.of(battle.enemies.getFirst()));
        battle.processRequests();

        int named = hyacine.getBuffManager().stacksOf(MARK);
        int bystander = other.getBuffManager().stacksOf(MARK);
        System.out.println("[sky_stacks] the named character has " + named + " ; the other ally has " + bystander);

        Assertions.assertEquals(2, named,
                "\\u300c\\u4f7f\\u98ce\\u5807\\u83b7\\u5f97 2 \\u5c42\\u3010\\u732e\\u4e88\\u300c\\u5929\\u7a7a\\u300d\\u4e4b\\u8bd7\\u3011\\u300d-- the named one, and the sentence own count");
        Assertions.assertEquals(0, bystander,
                "and nobody else -- that is what `ally_cid:` is for, and why a fallback to the owner would be wrong");
    }
}
'''
io.open("src/test/java/com/laosun/aluminium/test/SkyOdeStackTest.java", "w", encoding="utf-8", newline="\n").write(JUDGE)
print("ok   judge written")
