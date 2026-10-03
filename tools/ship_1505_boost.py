"""1505's eidolon boost: 50% more Aha-reward when the TRACE grants it (2026-10-02).

Document, verbatim (1505_绯英.html:127): 「暴击伤害提高 36%，触发行迹【瞰众乐】/行迹【开不败】的获得好活当赏效果时，
额外获得等同于本次获得的【好活当赏】50%/100% 的【好活当赏】。」

Measured facts that fix the shape:
  * only two rules in her file grant 【好活当赏】: `p1505_technique_gift` (technique, a flat 20) and `p1505_energy_sync`
    (the trace: energy gained -> an equal amount of reward, capped at 100). The document names the TRACE, so the boost
    belongs inside `p1505_energy_sync` -- as a second effect, which also makes recursion impossible;
  * `GAIN_RESOURCE` already reads a share of the event (`amountFromEvent` x `amountPercent`).

One honest deviation is documented in the rule's own note: the 50% is taken of the ENERGY gained, while the document
takes it of the REWARD granted (which is capped at 100). They agree at or below the cap; above it this over-grants.
ASCII only -- except the content itself, which is UTF-8 by construction.
"""
import io
import json

DATA = "src/main/resources/characters/1505.json"
JUDGE = "src/test/java/com/laosun/aluminium/test/ElationRewardBoostTest.java"
TRACE_RULE = "p1505_energy_sync"

doc = json.load(io.open(DATA, encoding="utf-8"))
rules = doc["rules"] if isinstance(doc, dict) else doc

patched = 0
for rule in rules:
    if isinstance(rule, dict) and rule.get("id") == TRACE_RULE:
        effects = [e for e in (rule.get("do") or []) if not (isinstance(e, dict) and e.get("amountPercent") == 0.5)]
        effects.append({
            "op": "GAIN_RESOURCE",
            "resource": "\u597d\u6d3b\u5f53\u8d4f",
            "amountFromEvent": True,
            "amountPercent": 0.5,
        })
        rule["do"] = effects
        rule["note"] = ("\u2b50 2026-10-02\uff1a**\u661f\u9b42**\uff08\u6587\u6863 `:127`\uff09\u300c\u89e6\u53d1**\u884c\u8ff9**\u2026\u7684\u83b7\u5f97\u597d\u6d3b\u5f53\u8d4f\u6548\u679c\u65f6\uff0c"
                        "\u989d\u5916\u83b7\u5f97\u7b49\u540c\u4e8e\u672c\u6b21**50%**\u7684\u3010\u597d\u6d3b\u5f53\u8d4f\u3011\u300d\u3002"
                        "\u2b50 \u5199\u5728\u672c\u6761**\u5185\u90e8**\uff08\u800c\u4e0d\u662f\u65b0\u89e6\u53d1\u5668\uff09\u2014\u2014 \u56e0\u4e3a\u6587\u6863\u9650\u5b9a\u7684\u662f"
                        "**\u884c\u8ff9\u6765\u6e90** \u2713\uff0c\u800c\u5168\u6587\u53ea\u6709\u4e24\u5904\u4f1a\u7ed9\u3010\u597d\u6d3b\u5f53\u8d4f\u3011\uff1a\u79d8\u6280\uff08`p1505_technique_gift`\uff09\u4e0e\u672c\u6761 \u2713"
                        "\u21d2 \u79d8\u6280\u90a3 20 \u70b9**\u4e0d**\u88ab\u52a0\u6210 \u2713\uff0c\u4e14**\u4e0d\u4f1a\u81ea\u53cd** \u2713\u3002"
                        "\u26a0 **\u5df2\u77e5\u504f\u5dee**\uff1a50% \u53d6\u81ea**\u672c\u6b21\u80fd\u91cf**\uff0c\u800c\u6587\u6863\u53d6\u81ea**\u672c\u6b21\u83b7\u5f97\u7684\u597d\u6d3b\u5f53\u8d4f**"
                        "\uff08\u90a3\u4e2a\u91cf\u5df2\u88ab 100 \u4e0a\u9650\u622a\u65ad\uff09\u21d2 \u80fd\u91cf \u2264 100 \u65f6\u4e24\u8005\u76f8\u540c \u2713\uff0c"
                        "> 100 \u65f6\u672c\u5b9e\u73b0\u4f1a\u591a\u53d1 \u2717\uff08\u5df2\u767b\u8bb0\uff09\u3002")
        patched += 1
if patched != 1:
    raise SystemExit("expected exactly one trace rule, patched " + str(patched))

if isinstance(doc, dict):
    doc["rules"] = rules
else:
    doc = rules
json.dump(doc, io.open(DATA, "w", encoding="utf-8", newline="\n"), ensure_ascii=False, indent=2)
print("ok   1505.json: the eidolon boost is inside the trace rule")

io.open(JUDGE, "w", encoding="utf-8", newline="").write('''package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * \u300c\u89e6\u53d1\u884c\u8ff9\u2026\u7684\u83b7\u5f97\u597d\u6d3b\u5f53\u8d4f\u6548\u679c\u65f6\uff0c\u989d\u5916\u83b7\u5f97\u7b49\u540c\u4e8e\u672c\u6b21\u7684 50%\u300d \u2014 1505 \u661f\u9b42 (2026-10-02).
 *
 * <p>\u2b50 FILE-DRIVEN and TWO-WAY: the trace's gain pays the boost, and the technique's own 20 pays nothing extra, because
 * the document names the TRACE as the qualifying source.
 */
public class ElationRewardBoostTest {
    private static final int OWNER = 1505;
    private static final int MONSTER = 1002011;
    private static final String REWARD = "\u597d\u6d3b\u5f53\u8d4f";

    /** \u2b50 A 100-point trace gain pays 100 reward plus its 50% again. */
    @Test
    public void theTraceGainPaysTheBoost() {
        Character owner = fresh();
        Assertions.assertEquals(0, reward(owner), "precondition: she starts with none");
        owner.getBattle().fireTriggers(TriggerEvent.ENERGY_GAINED, owner, owner, 100, 0);
        owner.getBattle().processRequests();
        Assertions.assertEquals(150, reward(owner),
                "100 energy -> 100 reward + the eidolon's 50% (got " + reward(owner) + ")");
    }

    /** \u26a0 The technique is not a trace, so its own 20 must not be boosted. */
    @Test
    public void theTechniqueGainIsNotBoosted() {
        Character owner = CharacterFactory.create(OWNER, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(owner),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        Assertions.assertEquals(20, reward(owner),
                "the technique's flat 20 stays 20 (got " + reward(owner) + ")");
    }

    // ==================================================================

    private static Character fresh() {
        Character owner = CharacterFactory.create(OWNER, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(owner),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        owner.setResource(REWARD, 0);
        return owner;
    }

    private static int reward(Character owner) {
        return owner.getResource(REWARD);
    }
}
''')
print("ok   judge written")
