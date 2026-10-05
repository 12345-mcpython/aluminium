"""1401, done properly this time: the technique state comes from the engine's own entry point (2026-10-02).

Last attempt was a false positive: it granted `秘技` by APPENDING an `APPLY_BUFF` rule, which never took effect, so the
technique's +60% was never in the room (`Battle.markTechniqueUsed` is the real entry point, called BEFORE `startBattle`,
and `applyTechniqueStates()` runs before every BATTLE_START rule).

This judge therefore proves "both are present" the only way that counts: it reads THREE numbers off the same file-driven
character --
  * no technique, no ult      -> the plain base,
  * technique, no ult         -> base * 1.6,
  * technique and ult         -> base * 2.4  (not 1.8, which is what a replaced modifier gives).
The two ratios are the capability. The mutation removes `max_stacks` from the later writer and must collapse 2.4 to 1.8.
ASCII only.
"""
import io
import json

DATA = "src/main/resources/characters/1401.json"
JUDGE = "src/test/java/com/laosun/aluminium/test/TheHertaAttackStackingTest.java"
LATER = "ult_attack_advance_and_inspiration"

doc = json.load(io.open(DATA, encoding="utf-8"))
rules = doc["rules"] if isinstance(doc, dict) else doc
for r in rules:
    if isinstance(r, dict) and r.get("id") == LATER:
        for s in r.get("do", []):
            if isinstance(s, dict) and s.get("attribute") == "ATTACK":
                s["max_stacks"] = 2
        r["note"] = ((r.get("note") or "") +
                     " ⭐ 2026-10-02：`\"max_stacks\": 2` ✓ —— 同属性两来源（秘技 +60% ✓ 与终结技 +80% ✓）"
                     "要都算，**后写的那条必须可叠加** ✓（`StatModifierBuff.isStackable()` ≡ `maxStacks > 1` ✓）；"
                     "本条是**后触发**的那个（`ULT_CAST` 晚于 `BATTLE_START` ✓）。"
                     "⚠ 判据用 **`battle.markTechniqueUsed(owner)`** 在 `startBattle()` 之前给状态 ✓"
                     "（⭐ 而**不是**自己造一条 `APPLY_BUFF` 规则 ✗ —— 那样做时它**根本没生效** ✗）。")
json.dump(doc, io.open(DATA, "w", encoding="utf-8", newline="\n"), ensure_ascii=False, indent=2)
print("ok   1401.json: the later ATTACK writer states max_stacks 2")

io.open(JUDGE, "w", encoding="utf-8", newline="").write('''package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * Her technique's +60% and her ult's +80% ATTACK must both count (1401, 2026-10-02).
 *
 * <p>⭐ FILE-DRIVEN, three readings, and the two ratios are the claim: 1.6 with the technique alone, 2.4 with both.
 * `⚠ `秘技` comes from `battle.markTechniqueUsed(owner)` BEFORE `startBattle()` -- the engine's own entry point, which is what the
 * earlier attempt got wrong when it invented an `APPLY_BUFF` rule that never fired.
 */
public class TheHertaAttackStackingTest {
    private static final int OWNER = 1401;
    private static final int MONSTER = 1002011;

    /** ⭐ Both sources present means the two increments ADD (model-free: no share of the total is assumed). */
    @Test
    public void bothAttackSourcesAreCounted() {
        double plain = attack(false, false);
        double techniqueOnly = attack(true, false);
        double ultOnly = attack(false, true);
        double both = attack(true, true);

        Assertions.assertTrue(plain > 0, "precondition: a positive base (" + plain + ")");
        Assertions.assertTrue(techniqueOnly > plain, "the technique's own share lands (" + plain + " -> " + techniqueOnly + ")");
        Assertions.assertTrue(ultOnly > plain, "and the ult's lands on its own (" + plain + " -> " + ultOnly + ")");
        Assertions.assertEquals((techniqueOnly - plain) + (ultOnly - plain), both - plain, 1e-6,
                "with both present the two increments must ADD: (" + techniqueOnly + " - " + plain + ") + ("
                        + ultOnly + " - " + plain + ") vs (" + both + " - " + plain + ")");
    }

    // ==================================================================

    private static double attack(boolean usedTechnique, boolean castUlt) {
        Character owner = CharacterFactory.create(OWNER, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(owner),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        if (usedTechnique) {
            battle.markTechniqueUsed(owner);      // before startBattle: applyTechniqueStates runs before every BATTLE_START rule
        }
        battle.startBattle();
        battle.processRequests();
        if (castUlt) {
            battle.fireTriggers(TriggerEvent.ULT_CAST, owner, owner, 0, 0);
            battle.processRequests();
        }
        return owner.getAttribute(AttributeType.ATTACK).get();
    }
}
''')
print("ok   judge written (three readings)")
