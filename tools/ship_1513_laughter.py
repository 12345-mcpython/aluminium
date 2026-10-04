"""1513 砂金•戏浪: the 笑点 half of her three clauses (2026-10-02, item 55).

\u2b50\u2b50 WHY THIS IS A SHIPMENT AND NOT A CAPABILITY: the shared, party-scoped, UNCAPPED resource 「笑点」 already exists -- declared
in `1502.json` as `{"id": "笑点", "max": 2147483647, "scope": "PARTY"}` with a `source` that says exactly that ("数据里没有上限，
所以 max 用 Integer.MAX_VALUE"), and `1502`'s own rule already grants five of them, judged by `Character1502Test`.
What was missing was HER half: `1513.json` declares only 【热意】, so its three sentences could not hand out 笑点.

Document, verbatim (per-clause; the numbers are the sentences' own):
  * 天赋 「队友施放攻击后，砂金•戏浪获得 **1 点【热意】**以及 **1 个笑点**」
  * 战技 「获得 **4 个笑点**和 **4 点【热意】**」
  * 终结技 「获得 **6 个笑点**和 **8 点【热意】**。并使自身速度提高 30%，持续 4 回合」

\u26a0 The effect ORDER follows each sentence (笑点 first where the sentence says it first), which is the project's rule for a
sentence that lists two grants.

\u2b50 THE SECOND READER of the family, so this closes the「按数量…」拉 family's sibling: `EXPRESSION.md` §3's row about the elation
gauge was **half wrong** -- the gauge resource was already there, only her grants were not.
"""
import io
import json

DATA = "src/main/resources/characters/1513.json"
JUDGE = "src/test/java/com/laosun/aluminium/test/Character1513LaughterTest.java"
LAUGH = "\u7b11\u70b9"
FERVOR = "\u70ed\u610f"
GRANTS = {"talent_fervor_on_teammate_attack": 1, "skill_fervor": 4, "ult_fervor_and_speed": 6}

doc = json.load(io.open(DATA, encoding="utf-8"))
if not isinstance(doc, dict) or "rules" not in doc:
    raise SystemExit("1513.json is expected to be an object with rules")

resources = doc.setdefault("resources", [])
if any(r.get("id") == LAUGH for r in resources):
    raise SystemExit("1513 already declares " + LAUGH)
resources.append({
    "id": LAUGH,
    "max": 2147483647,
    "initial": 0,
    "scope": "PARTY",
    "source": ("1513 \u7802\u91d1\u2022\u620f\u6d6a \u5929\u8d4b\uff0f\u6218\u6280\uff0f\u7ec8\u7ed3\u6280\uff1a\u300c\u83b7\u5f97 1\uff0f4\uff0f6 \u4e2a\u7b11\u70b9\u300d\u3002"
               "\u26a0 \u4e0e `1502`\uff0f`1505` \u5171\u7528\u540c\u540d\u7684**\u961f\u4f0d\u7ea7**\u8ba1\u6570 \u2713\uff08\u6570\u636e\u91cc\u6ca1\u6709\u4e0a\u9650 \u21d2 "
               "\u540c `1502` \u7684\u5199\u6cd5\u7528 `Integer.MAX_VALUE` \u2713\uff09"),
})

patched = {}
for rule in doc["rules"]:
    if not isinstance(rule, dict):
        continue
    amount = GRANTS.get(rule.get("id"))
    if amount is None:
        continue
    ops = rule.setdefault("do", [])
    if any(e.get("resource") == LAUGH for e in ops):
        raise SystemExit("rule " + rule["id"] + " already grants " + LAUGH)
    grant = {"op": "GAIN_RESOURCE", "resource": LAUGH, "amount": amount}
    fervor_at = next((i for i, e in enumerate(ops) if e.get("resource") == FERVOR), None)
    # \u26a0 Order follows the sentence: the talent says 【热意】 first ("1 点【热意】以及 1 个笑点"), the other two say 笑点 first.
    if rule["id"] == "talent_fervor_on_teammate_attack" and fervor_at is not None:
        ops.insert(fervor_at + 1, grant)
    elif fervor_at is not None:
        ops.insert(fervor_at, grant)
    else:
        ops.append(grant)
    patched[rule["id"]] = amount

if set(patched) != set(GRANTS):
    raise SystemExit("expected all three rules, patched: " + str(sorted(patched)))
json.dump(doc, io.open(DATA, "w", encoding="utf-8", newline="\n"), ensure_ascii=False, indent=2)
print("ok   1513.json: declares %s and grants %s" % (LAUGH, patched))

io.open(JUDGE, "w", encoding="utf-8", newline="").write('''package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1513\uff1a\u300c\u83b7\u5f97 1\uff0f4\uff0f6 \u4e2a\u7b11\u70b9\u300d\u2014\u2014 \u4e09\u53e5\u5404\u81ea\u7ed9\u7684\u90a3\u4e00\u534a (2026-10-02).
 *
 * <p>\u2b50\u2b50 \u7b11\u70b9 is a PARTY-scoped, uncapped counter that already existed (declared by 1502); these three readings are about
 * HER grants, and each one is the sentence's own number. \u26a0 The counter is shared, so the assertions are cumulative on purpose --
 * that IS what 「队伍级」 means, and `partyResourceValue` is the accessor `Character1502Test` already uses.
 */
public class Character1513LaughterTest {
    private static final int AVENTURINE = 1513;
    private static final int ALLY = 1002;
    private static final int MONSTER = 1002011;
    private static final String LAUGH = "\\u7b11\\u70b9";

    /** \u300c\u6218\u6280\u2026\u83b7\u5f97 4 \u4e2a\u7b11\u70b9\u300d */
    @Test
    public void herSkillGivesFour() {
        Battle battle = fight();
        Assertions.assertEquals(0, battle.partyResourceValue(LAUGH), "the battle starts with none");
        battle.castImmediate(battle.allies.stream().map(a -> (Character) a).filter(c -> c.getCharacterId() == AVENTURINE)
                .findFirst().orElseThrow().getSkills().get(SkillType.SKILL),
                her(battle), List.of());
        Assertions.assertEquals(4, battle.partyResourceValue(LAUGH), "\\u300c\\u83b7\\u5f97 4 \\u4e2a\\u7b11\\u70b9\\u300d");
    }

    /** \u300c\u7ec8\u7ed3\u6280\u2026\u83b7\u5f97 6 \u4e2a\u7b11\u70b9\u300d */
    @Test
    public void herUltimateGivesSix() {
        Battle battle = fight();
        battle.castImmediate(her(battle).getSkills().get(SkillType.ULTRA), her(battle), List.of());
        Assertions.assertEquals(6, battle.partyResourceValue(LAUGH), "\\u300c\\u83b7\\u5f97 6 \\u4e2a\\u7b11\\u70b9\\u300d");
    }

    /** \u300c\u961f\u53cb\u65bd\u653e\u653b\u51fb\u540e\u2026\u4ee5\u53ca 1 \u4e2a\u7b11\u70b9\u300d -- a REAL teammate attack, not a hand-fired event. */
    @Test
    public void aTeammateAttackGivesOne() {
        Battle battle = fight();
        battle.castImmediate(teammate(battle).getSkills().get(SkillType.COMMON), teammate(battle),
                List.of(battle.enemies.getFirst()));
        Assertions.assertEquals(1, battle.partyResourceValue(LAUGH), "\\u300c\\u4ee5\\u53ca 1 \\u4e2a\\u7b11\\u70b9\\u300d");
    }

    /** \u2b50 THE SHARED COUNTER: all three in one battle sum, because 笑点 is party-scoped. */
    @Test
    public void theCounterIsSharedAcrossTheParty() {
        Battle battle = fight();
        battle.castImmediate(her(battle).getSkills().get(SkillType.SKILL), her(battle), List.of());
        battle.castImmediate(her(battle).getSkills().get(SkillType.ULTRA), her(battle), List.of());
        battle.castImmediate(teammate(battle).getSkills().get(SkillType.COMMON), teammate(battle),
                List.of(battle.enemies.getFirst()));
        Assertions.assertEquals(11, battle.partyResourceValue(LAUGH), "4 + 6 + 1 on the SHARED counter");
    }

    // ==================================================================

    private static Battle fight() {
        Character her = CharacterFactory.create(AVENTURINE, 80, false, null, null, 0);
        Character mate = CharacterFactory.create(ALLY, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(her, mate),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        return battle;
    }

    private static Character her(Battle battle) {
        return (Character) battle.allies.stream()
                .filter(a -> ((Character) a).getCharacterId() == AVENTURINE).findFirst().orElseThrow();
    }

    private static Character teammate(Battle battle) {
        return (Character) battle.allies.stream()
                .filter(a -> ((Character) a).getCharacterId() == ALLY).findFirst().orElseThrow();
    }
}
''')
print("ok   judge written")
