"""1501: when the Aha moment ends, Sparkle gets an extra turn (2026-10-02).

Document, verbatim (1501_火花.html:145): 「**阿哈时刻结束时使火花获得 1 个【额外回合】和 2 个【爆点】**。每消耗 1 个【爆点】使自身暴击伤害提高
10%，持续 2 回合，最多叠加 4 层。」

This ships the half that needs no number and no new op:
  * the trigger is a moment this arc already announces -- an explicit removal of 「阿哈时刻」 (item 20);
  * the op is wired: `TriggerInterpreter:1165` is `case "EXTRA_TURN" -> battle.grantExtraTurn(resolveTarget(effect, ctx));`
    and the op table documents it at :53 as "optional `target` | ✅ wired";
  * the observable already exists: `Battle.getExtraTurnActor()` (Battle:872) is public.

⚠ The 【爆点】 half is registered, not guessed: it is a resource whose ceiling lives in a compiled expression, the same wall
as 1412's `#2`, 1408's 【毁伤】 and 1501's own 【笑点】.

Judge shape (no content duplication): 1501 has no Aha-moment creator of her own, so the APPLIER is hand-built on the ALLY --
`APPLY_BUFF{阿哈时刻, target: all_allies}` -- which leaves HER real table (the reader under test) untouched. Then the judge
removes the state by hand and asks whether the battle now owes her an extra turn.
ASCII only.
"""
import io
import json

DATA = "src/main/resources/characters/1501.json"
JUDGE = "src/test/java/com/laosun/aluminium/test/AhaEndExtraTurnTest.java"
RULE = "aha_end_extra_turn"
MOMENT = "\u963f\u54c8\u65f6\u523b"

doc = json.load(io.open(DATA, encoding="utf-8"))
rules = doc["rules"] if isinstance(doc, dict) else doc
rules = [r for r in rules if not (isinstance(r, dict) and r.get("id") == RULE)]

rules.append({
    "on": "STATE_ENDED",
    "id": RULE,
    "when": ["self state_ended " + MOMENT],
    "do": [{"op": "EXTRA_TURN", "target": "self"}],
    "source": ("1501 \u706b\u82b1 \u884c\u8ff9\uff08\u6587\u6863 `:145`\uff09\uff1a"
               "\u300c**\u963f\u54c8\u65f6\u523b\u7ed3\u675f\u65f6\u4f7f\u706b\u82b1\u83b7\u5f97 1 \u4e2a\u3010\u989d\u5916\u56de\u5408\u3011**\u548c 2 \u4e2a\u3010\u7206\u70b9\u3011\u300d"),
    "note": ("\u2b50 2026-10-02\uff1a\u89e6\u53d1 **\u5df2\u901a** \u2713\uff08\u663e\u5f0f\u79fb\u9664\u4f1a\u516c\u544a `STATE_ENDED` \u2713 \u2014\u2014 \u672c\u6bb5\u7b2c 20 \u4ef6\uff09\uff1b"
             "\u91cf\u7528 **`EXTRA_TURN`** \u2713\uff08\u5df2\u63a5\u7ebf \u2713\uff1a`TriggerInterpreter:1165` \u76f4\u63a5\u8c03 `battle.grantExtraTurn(...)` \u2713\uff09\u3002"
             "\u26a0 **\u5df2\u767b\u8bb0**\uff1a\u540c\u53e5\u7684\u3010\u7206\u70b9\u3011\uff08\u8d44\u6e90\u4e0a\u9650\u5728\u7f16\u8bd1\u8868\u8fbe\u5f0f\u91cc \u2717\uff09\u3002"),
})

if not isinstance(doc, dict):
    raise SystemExit("1501.json must be an object")
doc["rules"] = rules
json.dump(doc, io.open(DATA, "w", encoding="utf-8", newline="\n"), ensure_ascii=False, indent=2)
print("ok   1501.json: the Aha ending grants an extra turn")

io.open(JUDGE, "w", encoding="utf-8", newline="").write('''package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1501\uff1a\u300c\u963f\u54c8\u65f6\u523b\u7ed3\u675f\u65f6\u4f7f\u706b\u82b1\u83b7\u5f97 1 \u4e2a\u3010\u989d\u5916\u56de\u5408\u3011\u300d (2026-10-02).
 *
 * <p>\u2b50 FILE-DRIVEN, with the applier on the ALLY: 1501 has no Aha-moment creator of her own, and rebuilding HER table would
 * destroy the very reader under test. The ally lays the state on the whole camp instead, and the judge then ends it by hand.
 */
public class AhaEndExtraTurnTest {
    private static final int OWNER = 1501;
    private static final int ALLY = 1002;
    private static final int MONSTER = 1002011;
    private static final String MOMENT = "\u963f\u54c8\u65f6\u523b";

    /** \u2b50 Ending the moment owes her an extra turn. */
    @Test
    public void endingTheMomentGrantsAnExtraTurn() {
        Character owner = CharacterFactory.create(OWNER, 80);
        Character ally = CharacterFactory.create(ALLY, 80);
        Battle battle = new Battle(List.of(owner, ally),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();

        EffectSpec lay = new EffectSpec();
        TriggerSpecs.set(lay, "op", "APPLY_BUFF");
        TriggerSpecs.set(lay, "buff", MOMENT);
        TriggerSpecs.set(lay, "permanent", true);
        TriggerSpecs.set(lay, "target", "all_allies");
        ally.setTriggerTable(new TriggerTable(ALLY, List.of(
                TriggerSpecs.rule(TriggerEvent.SKILL_CAST.name(), List.of(), lay))));
        battle.fireTriggers(TriggerEvent.SKILL_CAST, ally, owner, 0, 0);
        battle.processRequests();
        Assertions.assertTrue(owner.getBuffManager().hasState(MOMENT), "precondition: the moment is on her");

        owner.getBuffManager().removeState(MOMENT);
        battle.processRequests();
        Assertions.assertSame(owner, battle.getExtraTurnActor(),
                "the reader must owe HER the extra turn");
    }
}
''')
print("ok   judge written")
