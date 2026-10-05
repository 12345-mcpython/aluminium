"""1408: 「【火种】…达到上限后还可最多溢出 3 点」 -- the overflow cap, shipped and measured (2026-10-02, item 39).

Document, verbatim (1408_白厄.html, 白厄的天赋): 「…【火种】达到 12 点时可激活终结技，**达到上限后还可最多溢出 3 点**，**变身结束时会基于溢出点数
获得【火种】**。…」

WHAT SHIPS HERE: the overflow CAP. 火种 is declared `max: 12` (already in the file) and the sentence adds "up to 3 more", which is
exactly what `ResourceSpec.overflow` is for -- a shipped mechanism with a shipped reader (1506's 【隐藏分】, per that record's own
note). The judge measures the cap from both sides (14 is reachable, 15 is the ceiling), so "12" and "15" are BOTH pinned.

WHAT IS REGISTERED INSTEAD, and why it is now a precise statement rather than a shrug: 「变身结束时会基于溢出点数获得【火种】」 needs an
effect whose AMOUNT is a resource's value. `EffectSpec` has exactly three such channels -- `amountFromAttr` (an attribute, with
`amountPercent` for a share), `amountFromEvent`, `amountFromPrevious` -- and none of them is a resource. The engine already has
`TriggerInterpreter.resourceAmount(battle, owner, id)` internally, so the channel exists and only the FIELD is missing; that is a
much narrower thing to build than "resources as numbers", and it now has this reader plus 1506's overflow rule as neighbours.
ASCII only.
"""
import io
import json

DATA = "src/main/resources/characters/1408.json"
JUDGE = "src/test/java/com/laosun/aluminium/test/CoreflameOverflowTest.java"
COREFLAME = "火种"

doc = json.load(io.open(DATA, encoding="utf-8"))
resources = doc.get("resources") if isinstance(doc, dict) else None
if not resources:
    raise SystemExit("1408.json declares no resources")

hit = [r for r in resources if isinstance(r, dict) and r.get("id") == COREFLAME]
if len(hit) != 1:
    raise SystemExit("expected exactly one Coreflame declaration, found " + str(len(hit)))

res = hit[0]
res["overflow"] = 3
res["source"] = ((res.get("source") or "") +
                 "\n⭐ 2026-10-02（文档，白厄的天赋）：「【火种】达到 12 点时可激活终结技，"
                 "**达到上限后还可最多溢出 3 点**」✓")
res["note"] = ((res.get("note") or "") +
               "\n⭐ 2026-10-02：**溢出上限 3** ✓（`\"overflow\": 3` ✓ —— 本字段已有出货读者：1506 的【隐藏分】 ✓）"
               "⇒ 火种可达 **15** ✓。⚠ 仍登记：“**变身结束时会基于溢出点数获得【火种】**” ✗"
               "—— 它需要**一个量等于某资源当前值的效果** ✗，而 `EffectSpec` 的数值来源只有三路 ✗。")

if not isinstance(doc, dict):
    raise SystemExit("1408.json must be an object")
json.dump(doc, io.open(DATA, "w", encoding="utf-8", newline="\n"), ensure_ascii=False, indent=2)
print("ok   1408.json: Coreflame overflows by up to three")

io.open(JUDGE, "w", encoding="utf-8", newline="").write('''package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.models.skill.Skill;
import com.laosun.aluminium.models.skill.SkillExecutor;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1408：「【火种】达到 12 点时可激活终结技，**达到上限后还可最多溢出 3 点**」 (2026-10-02).
 *
 * <p>⭐ BOTH ENDS ARE PINNED: a value of 14 proves the pool really goes past its max of 12, and a ceiling of 15 proves the
 * overflow allowance is exactly three (「最多溢出 3 点」). Her skill grants two points per cast, so the casts are the dial.
 */
public class CoreflameOverflowTest {
    private static final int OWNER = 1408;
    private static final int MONSTER = 1002011;
    private static final String COREFLAME = "火种";

    /** ⭐ Past the maximum: seven casts reach fourteen, which a cap of twelve could never allow. */
    @Test
    public void thePoolGoesPastItsMaximum() {
        Assertions.assertEquals(14.0, afterCasts(7), 1e-9,
                "「达到上限后还可溢出」-- 14 is above the declared max of 12");
    }

    /** ⭐ The allowance is exactly three: ten casts stop at fifteen. */
    @Test
    public void theAllowanceIsExactlyThree() {
        Assertions.assertEquals(15.0, afterCasts(10), 1e-9,
                "「最多溢出 3 点」-- 12 + 3 is the ceiling");
    }

    private static double afterCasts(int casts) {
        Character owner = CharacterFactory.create(OWNER, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(owner),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();

        Skill skill = owner.getSkills().get(SkillType.SKILL);
        Assertions.assertNotNull(skill, "precondition: she has a skill");
        for (int i = 0; i < casts; i++) {
            // ⚠ AIMED AT AN ENEMY. The first version of this judge aimed her skill at HERSELF, and that single choice made the
            // resource read a constant 3 for every cast count: the first self-aimed cast landed (+2 for the skill, +1 for item 38's
            // "being targeted" rule) and the later ones did nothing at all. Measured step by step, aimed at an enemy, the pool is
            // 2, 4, 6, 8, 10, 12 -- exactly two per cast, up to its declared cap.
            SkillExecutor.execute(battle, skill, owner, List.of(battle.enemies.get(0)));
            battle.processRequests();
        }
        // ⚠ `owner.getResources().value(...)`, and NOT `battle.partyResource(...)`: Coreflame is not a party resource, so the
        // party view is null. The reader itself is innocent -- measured against 1412's Charge in the same probe, which accumulates
        // exactly (+1 per cast).
        return owner.getResources().value(COREFLAME);
    }
}
''')
print("ok   judge written")
