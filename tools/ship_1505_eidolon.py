"""Ship 1505's eidolon on the effect-level rank gate (2026-10-02).

Document, verbatim (1505_绯英.html:127): 「暴击伤害提高 36%，触发行迹【瞰众乐】/行迹【开不败】的获得好活当赏效果时，
额外获得等同于本次获得的【好活当赏】50%/100% 的【好活当赏】。」

The two engine halves are already in the tree:
  * `amountFromPrevious` (15d210c1 + d319386d): the share is taken of what the PREVIOUS effect actually credited, so the
    trace's own 100-point ceiling is inside the base -- 150 energy gives 100, and 50% of it is 50;
  * effect-level `min_eidolon` (6ab86820): the eidolon half carries `min_eidolon: 1` while the base half of the SAME rule
    does not, which a rule-level gate could never express (it switches off the whole rule) and a separate rule cannot
    either (it would lose `previousCredited`, and re-deriving gives min(75, 100) = 75 instead of 50).

The judge is two-way by construction:
  * rank 1: 150 energy -> 20 (technique) + 100 (capped) + 50 = 170;
  * rank 0: the base behaves exactly as the existing guards expect -- 100 for the same gain, no eidolon share.
ASCII only.
"""
import io
import json

DATA = "src/main/resources/characters/1505.json"
JUDGE = "src/test/java/com/laosun/aluminium/test/ElationRewardBoostTest.java"
TRACE_RULE = "p1505_energy_sync"
REWARD = "\u597d\u6d3b\u5f53\u8d4f"

doc = json.load(io.open(DATA, encoding="utf-8"))
rules = doc["rules"] if isinstance(doc, dict) else doc

patched = 0
for rule in rules:
    if isinstance(rule, dict) and rule.get("id") == TRACE_RULE:
        effects = [e for e in (rule.get("do") or [])
                   if not (isinstance(e, dict) and e.get("amountFromPrevious"))]
        effects.append({
            "op": "GAIN_RESOURCE",
            "resource": REWARD,
            "amountFromPrevious": True,
            "amountPercent": 0.5,
            "min_eidolon": 1,
        })
        rule["do"] = effects
        rule["note"] = ("\u2b50 2026-10-02\uff1a**\u661f\u9b42**\uff08\u6587\u6863 `:127`\uff09\u300c\u89e6\u53d1**\u884c\u8ff9**\u2026\u7684\u83b7\u5f97\u597d\u6d3b\u5f53\u8d4f\u6548\u679c\u65f6\uff0c"
                        "\u989d\u5916\u83b7\u5f97\u7b49\u540c\u4e8e\u672c\u6b21**50%**\u7684\u3010\u597d\u6d3b\u5f53\u8d4f\u3011\u300d\u3002"
                        "\u2b50 \u4e24\u4e2a\u5f15\u64ce\u534a\u8fb9\uff1a**`amountFromPrevious`**\uff08\u53d6**\u5df2\u622a\u65ad**\u7684\u5165\u8d26\u91cf \u2713\uff09"
                        "\u5341 **\u6548\u679c\u7ea7 `min_eidolon`**\uff08**\u53ea\u5173\u8fd9\u4e00\u6761\u6548\u679c** \u2713\uff09\u3002"
                        "\u26a0 \u4e3a\u4ec0\u4e48\u4e0d\u80fd\u7528\u522b\u7684\u5199\u6cd5\uff1a\u89c4\u5219\u7ea7 `min_eidolon` \u4f1a\u8fde**\u57fa\u7840\u90a3\u534a**\u4e00\u8d77\u5173 \u2717\uff1b"
                        "\u53e6\u8d77\u4e00\u6761\u89c4\u5219\u5219\u4e22\u6389 `previousCredited`\uff0c\u800c\u81ea\u5df1\u91cd\u7b97\u4f1a\u5f97 `min(75,100)=75` \u2717\uff08\u8981\u7684\u662f 50 \u2713\uff09\u3002")
        patched += 1
if patched != 1:
    raise SystemExit("expected exactly one trace rule, patched " + str(patched))

if isinstance(doc, dict):
    doc["rules"] = rules
else:
    doc = rules
json.dump(doc, io.open(DATA, "w", encoding="utf-8", newline="\n"), ensure_ascii=False, indent=2)
print("ok   1505.json: the eidolon half is rank-gated")

io.open(JUDGE, "w", encoding="utf-8", newline="").write('''package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.energy.EnergyGain;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * \u300c\u89e6\u53d1\u884c\u8ff9\u2026\u7684\u83b7\u5f97\u597d\u6d3b\u5f53\u8d4f\u6548\u679c\u65f6\uff0c\u989d\u5916\u83b7\u5f97\u7b49\u540c\u4e8e\u672c\u6b21\u7684 50%\u300d \u2014 1505 \u661f\u9b42 (2026-10-02).
 *
 * <p>\u2b50 FILE-DRIVEN and TWO-WAY on the EIDOLON RANK itself: at rank 1 the trace's capped credit gains its 50% on top; at
 * rank 0 the same gain credits exactly the capped amount, which is what the older guards already assert.
 */
public class ElationRewardBoostTest {
    private static final int OWNER = 1505;
    private static final int MONSTER = 1002011;
    private static final String REWARD = "\u597d\u6d3b\u5f53\u8d4f";

    /** \u2b50 With the Eidolon: 150 energy -> 20 + 100 (capped) + 50. */
    @Test
    public void theEidolonAddsHalfOfTheCappedCredit() {
        Assertions.assertEquals(170, rewardAfterGain(1, 150),
                "rank 1: 20 (technique) + 100 (capped) + 50 (the eidolon's half)");
    }

    /** \u26a0 Without it: the base trace only, exactly as the older guards expect. */
    @Test
    public void theBaseIsUntouchedWithoutTheEidolon() {
        Assertions.assertEquals(120, rewardAfterGain(0, 150),
                "rank 0: 20 (technique) + 100 (capped), no eidolon share");
    }

    /** \u26a0 The technique is not a trace, so its own 20 must not be boosted at any rank. */
    @Test
    public void theTechniqueGainIsNotBoosted() {
        Assertions.assertEquals(20, rewardAfterGain(1, 0),
                "the technique's flat 20 stays 20 even with the Eidolon");
    }

    // ==================================================================

    private static int rewardAfterGain(int eidolonRank, int energy) {
        Character owner = CharacterFactory.create(OWNER, 80, false, null, null, eidolonRank);
        Battle battle = new Battle(List.of(owner),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        if (energy > 0) {
            owner.setCurrentEnergy(0);
            battle.applyEnergyGain(owner, new EnergyGain(energy, false));
            battle.processRequests();
        }
        return owner.getResources().has(REWARD) ? owner.getResources().value(REWARD) : 0;
    }
}
''')
print("ok   judge written")
