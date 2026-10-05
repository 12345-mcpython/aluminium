"""1505's eidolon, now on the capped-credit channel (2026-10-02).

Document, verbatim (1505_绯英.html:127): 「暴击伤害提高 36%，触发行迹【瞰众乐】/行迹【开不败】的获得好活当赏效果时，
额外获得等同于本次获得的【好活当赏】50%/100% 的【好活当赏】。」

The engine now has the channel this needs: `amountFromPrevious` (wired in 15d210c1) reads the amount the PREVIOUS effect
actually credited, so the trace's own 100-point cap is inside the base. 150 energy -> the trace credits 100 (capped) ->
the eidolon's 50% is 50 -> 150 in total, which is exactly what the existing guard `ElationAmountCapTest` expects.

Two readers ship here: the 50% (the eidolon) and the 100% (the same sentence's other half), so the capability is not
built for one reader alone.

Mutation plan: amountPercent 0.5 -> 0.25 must redden the judge. Whole suite first, always.
"""
import io
import json

DATA = "src/main/resources/characters/1505.json"
JUDGE = "src/test/java/com/laosun/aluminium/test/ElationRewardBoostTest.java"
TRACE_RULE = "p1505_energy_sync"
REWARD = "好活当赏"

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
        })
        rule["do"] = effects
        rule["note"] = ("⭐ 2026-10-02：**星魂**（文档 `:127`）「触发**行迹**…的获得好活当赏效果时，"
                        "额外获得等同于本次**50%**的【好活当赏】」。"
                        "⭐ 用 **`amountFromPrevious`** ✓（本段为此新建的通道 ✓）："
                        "它读的是**前一条效果已经过上限截断的入账量** ✓，"
                        "而不是原始事件量 ✓⇒ 所以能量 150 时的正确答案是 **100 + 50 = 150** ✓"
                        "（⚠ 而不是 100 + 75）。⭐ 写在本条**内部**（不新增触发器 ✓）⇒ 秘技那 20 点不被加成 ✓。")
        patched += 1
if patched != 1:
    raise SystemExit("expected exactly one trace rule, patched " + str(patched))

if isinstance(doc, dict):
    doc["rules"] = rules
else:
    doc = rules
json.dump(doc, io.open(DATA, "w", encoding="utf-8", newline="\n"), ensure_ascii=False, indent=2)
print("ok   1505.json: the eidolon reads the previous credited amount")

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
 * 「触发行迹…的获得好活当赏效果时，额外获得等同于本次的 50%」 — 1505 星魂 (2026-10-02).
 *
 * <p>⭐ FILE-DRIVEN and TWO-WAY: the trace pays the boost on the CAPPED credit (150 energy -> 100 capped -> +50), and the
 * technique's own flat 20 pays nothing extra because the document names the TRACE as the qualifying source.
 */
public class ElationRewardBoostTest {
    private static final int OWNER = 1505;
    private static final int MONSTER = 1002011;
    private static final String REWARD = "好活当赏";

    /** ⭐ 150 energy: the trace caps its own credit at 100, then the eidolon adds its 50%. */
    @Test
    public void theTraceGainPaysTheBoostOnTheCappedCredit() {
        Battle battle = battle();
        Character owner = (Character) battle.allies.get(0);
        Assertions.assertEquals(20, reward(owner), "precondition: only the technique's 20 is on her");
        owner.setCurrentEnergy(0);
        battle.applyEnergyGain(owner, new EnergyGain(150, false));
        battle.processRequests();
        Assertions.assertEquals(170, reward(owner),
                "150 energy -> 20 + 100 (capped) + 50% of the capped 100 (got " + reward(owner) + ")");
    }

    /** ⚠ The technique is not a trace, so its own 20 must not be boosted. */
    @Test
    public void theTechniqueGainIsNotBoosted() {
        Battle battle = battle();
        Character owner = (Character) battle.allies.get(0);
        Assertions.assertEquals(20, reward(owner),
                "the technique's flat 20 stays 20 (got " + reward(owner) + ")");
    }

    // ==================================================================

    private static Battle battle() {
        Character owner = CharacterFactory.create(OWNER, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(owner),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        return battle;
    }

    private static int reward(Character owner) {
        return owner.getResources().has(REWARD) ? owner.getResources().value(REWARD) : 0;
    }
}
''')
print("ok   judge written")
