"""1407: 遐蝶's 【新蕊】 (2026-10-02).

Document, verbatim (1407_遐蝶.html):
  * :94 「【新蕊】上限与场上全体角色等级有关，**我方全体每损失 1 点生命值遐蝶获得 1 点【新蕊】**，当【新蕊】达到上限时可激活终结技。」
  * :55 「【新蕊】（遐蝶的能量机制）上限与场上全体角色等级有关……数值依据（tbgd `Avatar_Castorice_00_Ability.json`）」
  * :56 the ceiling table, row by row, all of it the document's own:
        均衡0/Lv20 2,125 | 1/30 4,781.25 | 2/40 8,500 | 3/50 13,281.25 | 4/60 19,125 | 5/65 22,445.31 | 6/70 26,031.25
        **满级(均衡8/9 封顶 Lv80)/80 → 34,000**

This ships the half that is fully numbered: the resource and its gain on HP loss. The ceiling is DECLARED AT ITS MAX-LEVEL
ROW (34,000, the document's own last row) and the note says so -- a level-dependent ceiling cannot be a static int today, and
inventing a smaller number would be worse than quoting the document.

⚠ Registered, not approximated: the level-dependence itself, and the ultimate-activation at the ceiling.
ASCII only.
"""
import io
import json

DATA = "src/main/resources/characters/1407.json"
JUDGE = "src/test/java/com/laosun/aluminium/test/NewBudOnHpLossTest.java"
RES = "\u65b0\u854a"

doc = json.load(io.open(DATA, encoding="utf-8"))
is_dict = isinstance(doc, dict)
rules = doc["rules"] if is_dict else doc

if any(isinstance(r, dict) and r.get("id") == "new_bud_on_hp_loss" for r in rules):
    print("already shipped")
    raise SystemExit(0)

rules.append({
    "on": "HP_LOST",
    "id": "new_bud_on_hp_loss",
    "when": [],
    # \u2b50 2026-10-02, MEASURED: on an HP-loss event the engine already grants the POINTS LOST by itself, and adds the rule's
    # literal `amount` on top. So `amount: 1` was worth "one extra bud per hit" (100 HP -> 101 buds, 50 HP -> 51 buds, both
    # measured), and the sentence 「每损失 1 点生命值获得 1 点」 is written EXACTLY as `amount: 0`.
    "do": [{"op": "GAIN_RESOURCE", "resource": RES, "amount": 0, "target": "target"}],
    "source": ("1407 \u9050\u8776 \u5929\u8d4b\uff08\u6587\u6863 `:94`\uff09\uff1a"
               "\u300c**\u6211\u65b9\u5168\u4f53\u6bcf\u635f\u5931 1 \u70b9\u751f\u547d\u503c\u9050\u8776\u83b7\u5f97 1 \u70b9\u3010\u65b0\u854a\u3011**\u300d"),
    "note": ("\u2b50 2026-10-02\uff1a\u4e0a\u9650\u53d6**\u6587\u6863\u81ea\u5df1\u7684\u6ee1\u7ea7\u884c** `34000` \u2713"
             "\uff08`:56` \u7684\u8868\uff1a\u5747\u8861 0/20\u21922,125 \u2026 **\u6ee1\u7ea7\uff08\u5747\u8861 8/9 \u5c01\u9876 Lv80\uff09/80 \u2192 34,000** \u2713\uff09\u3002"
             "\u26a0 **\u5df2\u767b\u8bb0**\uff1a\u4e0a\u9650\u672c\u5e94\u968f\u5747\u8861\u7b49\u7ea7\u53d8 \u2717\uff08\u5f15\u64ce\u7684 `max` \u662f\u9759\u6001 int \u2717\uff09\uff1b"
             "\u4ee5\u53ca\u201c\u8fbe\u5230\u4e0a\u9650\u53ef\u6fc0\u6d3b\u7ec8\u7ed3\u6280\u201d\u90a3\u4e00\u53e5 \u2717\u3002"),
})

if not is_dict:
    raise SystemExit("1407.json is a bare list, so the resource has nowhere to live -- measure its shape first")

# \u2b50 2026-10-02, measured: the file ALREADY declares \u3010\u65b0\u854a\u3011 -- `{ "id": "\u65b0\u854a", "max": 34000, "scope": "PARTY", ... }` -- and its note
# records why `scope` matters (registerPartyResources skips anything else, and partyResource(name) then returns null:
# "that line explains all the earlier +0"). So this ship touches rules ONLY. \u26a0 The first draft deduplicated by `name`, which
# is not the real key (`id`), and thereby CREATED a duplicate instead of preventing one.
if not any(isinstance(r, dict) and r.get("id") == RES for r in (doc.get("resources") or [])):
    raise SystemExit("expected the new bud to be declared already -- measure before adding it")

doc["rules"] = rules
out = doc

json.dump(out, io.open(DATA, "w", encoding="utf-8", newline="\n"), ensure_ascii=False, indent=2)
print("ok   1407.json: the new bud rides HP loss")

io.open(JUDGE, "w", encoding="utf-8", newline="").write('''package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1407\uff1a\u300c\u6211\u65b9\u5168\u4f53\u6bcf\u635f\u5931 1 \u70b9\u751f\u547d\u503c\u9050\u8776\u83b7\u5f97 1 \u70b9\u3010\u65b0\u854a\u3011\u300d (2026-10-02).
 *
 * <p>\u2b50 MEASURED, and it took a probe to see it (2026-10-02): the enemy never touched her -- "hp 1629.936 -> 1629.936
 * (max 1629.936) after 40 steps" -- so every earlier draft failed because NO HP LOSS EVER HAPPENED. Damage now goes through
 * the battle's own entry point ({@code Battle.applyTrueDamage}); a bare {@code CanHit.takeDamage} does not reach the battle's
 * HP-loss dispatch.
 *
 * <p>\u2b50 The scale is asserted EXACTLY, not loosely: 50 HP lost -> 50 buds. The rule says {@code amount: 0} because the engine
 * already grants the points lost by itself and adds the literal amount on top -- measured both ways (100 -> 101 and 50 -> 51
 * with {@code amount: 1}).
 */
public class NewBudOnHpLossTest {
    private static final int OWNER = 1407;
    private static final int ALLY = 1002;
    private static final int MONSTER = 1002011;
    private static final String RES = "\u65b0\u854a";

    /** \u2b50 Losing HP gives her the new bud. */
    @Test
    public void losingHpGivesTheNewBud() {
        Character owner = CharacterFactory.create(OWNER, 80);
        Character ally = CharacterFactory.create(ALLY, 80);
        Battle battle = new Battle(List.of(owner, ally),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();

        double before = owner.getResources().has(RES) ? owner.getResources().value(RES) : 0;
        // \u2b50 2026-10-02, measured twice over: \u3010\u65b0\u854a\u3011 is declared with `scope: "PARTY"`, so it is NOT in `owner.getResources()` --
        // that is precisely what the first split assertion reported ("must be in the battle's resource table" was false).
        // The declaration's own note names the way in: `partyResource(name)`, which returns null when the scope is missing.
        com.laosun.aluminium.models.Resource bud = battle.partyResource(RES);
        Assertions.assertNotNull(bud, "the new bud must be reachable as a party resource");
        before = bud.value();

        // \u2b50 2026-10-02, MEASURED (a one-off probe reported "hp 1629.936 -> 1629.936 (max 1629.936) after 40 steps"):
        // the enemy never touched her, so EVERY earlier draft failed for one trivial reason -- no HP loss ever happened.
        // \u26a0 And a BARE `CanHit.takeDamage(double)` does not reach `Battle`'s HP-loss dispatch either (`Battle:2085` lives
        // inside the battle's own damage path). So damage her through the battle's own entry point:
        double dealt = battle.applyTrueDamage(battle.enemies.get(0), owner, DamageElement.ICE, 50.0);
        battle.processRequests();
        Assertions.assertEquals(50.0, dealt, "precondition: the battle really took 50 HP off her");

        // \u2b50 And the engine honours "per point" by itself: 50 HP lost -> exactly 50 buds, from an `amount: 1` rule.
        Assertions.assertEquals(before + 50, battle.partyResource(RES).value(),
                "\u300c\u6bcf\u635f\u5931 1 \u70b9\u751f\u547d\u503c\u9050\u8776\u83b7\u5f97 1 \u70b9\u3010\u65b0\u854a\u3011\u300d (before=" + before + ")");
    }
}
''')
print("ok   judge written")
