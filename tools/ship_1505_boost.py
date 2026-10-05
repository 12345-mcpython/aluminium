"""1505's eidolon boost: 50% more Aha-reward when the TRACE grants it (2026-10-02).

Document, verbatim (1505_绯英.html:127): 「暴击伤害提高 36%，触发行迹【瞰众乐】/行迹【开不败】的获得好活当赏效果时，
额外获得等同于本次获得的【好活当赏】50%/100% 的【好活当赏】。」

Every fact below was measured, and each cost a round:
  * only two rules grant 【好活当赏】: `p1505_technique_gift` (a flat 20, on her from battle start) and `p1505_energy_sync`
    (the trace: energy gained -> an equal amount of reward, capped at 100). The document names the TRACE, so the boost goes
    INSIDE `p1505_energy_sync` as a second effect -- which also makes recursion impossible;
  * `GAIN_RESOURCE` reads a share of the event (`amountFromEvent` x `amountPercent`);
  * resources are `holder.getResources()` -> `Resource` with `has/value`;
  * `ENERGY_GAINED` is emitted by `Battle.applyEnergyGain(target, gain)` (Battle:1378) -- NOT by `CanHit.gainEnergy`, which
    only credits the number. `EnergyGain` is `record EnergyGain(double amount, boolean affectedByEfficiency)` in
    `models.energy`.

Known deviation, written into the rule's own note: the 50% is taken of the ENERGY gained, while the document takes it of
the REWARD granted (capped at 100). They agree at or below the cap; above it this over-grants.

NOTE: an earlier revision of this file was lost to a `git checkout -- .` cleanup, so this one is committed the moment it is
written. The boost also reddened two existing judges; identify them before committing content.
ASCII only.
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
        effects = [e for e in (rule.get("do") or []) if not (isinstance(e, dict) and e.get("amountPercent") == 0.5)]
        effects.append({
            "op": "GAIN_RESOURCE",
            "resource": REWARD,
            "amountFromEvent": True,
            "amountPercent": 0.5,
        })
        rule["do"] = effects
        rule["note"] = ("⭐ 2026-10-02：**星魂**（文档 `:127`）「触发**行迹**…的获得好活当赏效果时，"
                        "额外获得等同于本次**50%**的【好活当赏】」。"
                        "⭐ 写在本条**内部**（而不是新触发器）—— 文档限定的是**行迹来源** ✓，"
                        "而全文只有两处会给【好活当赏】：秘技（`p1505_technique_gift`）与本条 ✓"
                        "⇒ 秘技那 20 点**不**被加成 ✓（已实测通过 ✓），且**不会自反** ✓。"
                        "⚠ **已知偏差**：50% 取自**本次能量**，而文档取自**本次获得的好活当赏**"
                        "（那个量已被 100 上限截断）⇒ 能量 ≤ 100 时两者相同 ✓，"
                        "> 100 时本实现会多发 ✗（已登记）。")
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
 * <p>⭐ FILE-DRIVEN and TWO-WAY: the trace's gain pays the boost, and the technique's own 20 pays nothing extra, because
 * the document names the TRACE as the qualifying source. Energy goes through `Battle.applyEnergyGain`, the only path
 * that announces `ENERGY_GAINED`.
 */
public class ElationRewardBoostTest {
    private static final int OWNER = 1505;
    private static final int MONSTER = 1002011;
    private static final String REWARD = "好活当赏";

    /** ⭐ 100 energy pays 100 reward plus the eidolon's 50%, on top of the technique's 20. */
    @Test
    public void theTraceGainPaysTheBoost() {
        Battle battle = battle();
        Character owner = (Character) battle.allies.get(0);
        Assertions.assertEquals(20, reward(owner), "precondition: only the technique's 20 is on her");
        owner.setCurrentEnergy(0);
        battle.applyEnergyGain(owner, new EnergyGain(100, false));
        battle.processRequests();
        Assertions.assertEquals(170, reward(owner),
                "100 energy -> 20 + 100 reward + the eidolon's 50% (got " + reward(owner) + ")");
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
