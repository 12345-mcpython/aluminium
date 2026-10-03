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
COREFLAME = "\u706b\u79cd"

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
                 "\n\u2b50 2026-10-02\uff08\u6587\u6863\uff0c\u767d\u5384\u7684\u5929\u8d4b\uff09\uff1a\u300c\u3010\u706b\u79cd\u3011\u8fbe\u5230 12 \u70b9\u65f6\u53ef\u6fc0\u6d3b\u7ec8\u7ed3\u6280\uff0c"
                 "**\u8fbe\u5230\u4e0a\u9650\u540e\u8fd8\u53ef\u6700\u591a\u6ea2\u51fa 3 \u70b9**\u300d\u2713")
res["note"] = ((res.get("note") or "") +
               "\n\u2b50 2026-10-02\uff1a**\u6ea2\u51fa\u4e0a\u9650 3** \u2713\uff08`\"overflow\": 3` \u2713 \u2014\u2014 \u672c\u5b57\u6bb5\u5df2\u6709\u51fa\u8d27\u8bfb\u8005\uff1a1506 \u7684\u3010\u9690\u85cf\u5206\u3011 \u2713\uff09"
               "\u21d2 \u706b\u79cd\u53ef\u8fbe **15** \u2713\u3002\u26a0 \u4ecd\u767b\u8bb0\uff1a\u201c**\u53d8\u8eab\u7ed3\u675f\u65f6\u4f1a\u57fa\u4e8e\u6ea2\u51fa\u70b9\u6570\u83b7\u5f97\u3010\u706b\u79cd\u3011**\u201d \u2717"
               "\u2014\u2014 \u5b83\u9700\u8981**\u4e00\u4e2a\u91cf\u7b49\u4e8e\u67d0\u8d44\u6e90\u5f53\u524d\u503c\u7684\u6548\u679c** \u2717\uff0c\u800c `EffectSpec` \u7684\u6570\u503c\u6765\u6e90\u53ea\u6709\u4e09\u8def \u2717\u3002")

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
 * 1408\uff1a\u300c\u3010\u706b\u79cd\u3011\u8fbe\u5230 12 \u70b9\u65f6\u53ef\u6fc0\u6d3b\u7ec8\u7ed3\u6280\uff0c**\u8fbe\u5230\u4e0a\u9650\u540e\u8fd8\u53ef\u6700\u591a\u6ea2\u51fa 3 \u70b9**\u300d (2026-10-02).
 *
 * <p>\u2b50 BOTH ENDS ARE PINNED: a value of 14 proves the pool really goes past its max of 12, and a ceiling of 15 proves the
 * overflow allowance is exactly three (\u300c\u6700\u591a\u6ea2\u51fa 3 \u70b9\u300d). Her skill grants two points per cast, so the casts are the dial.
 */
public class CoreflameOverflowTest {
    private static final int OWNER = 1408;
    private static final int MONSTER = 1002011;
    private static final String COREFLAME = "\u706b\u79cd";

    /** \u2b50 Past the maximum: seven casts reach fourteen, which a cap of twelve could never allow. */
    @Test
    public void thePoolGoesPastItsMaximum() {
        Assertions.assertEquals(14.0, afterCasts(7), 1e-9,
                "\u300c\u8fbe\u5230\u4e0a\u9650\u540e\u8fd8\u53ef\u6ea2\u51fa\u300d-- 14 is above the declared max of 12");
    }

    /** \u2b50 The allowance is exactly three: ten casts stop at fifteen. */
    @Test
    public void theAllowanceIsExactlyThree() {
        Assertions.assertEquals(15.0, afterCasts(10), 1e-9,
                "\u300c\u6700\u591a\u6ea2\u51fa 3 \u70b9\u300d-- 12 + 3 is the ceiling");
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
            SkillExecutor.execute(battle, skill, owner, List.of(owner));
            battle.processRequests();
        }
        return owner.getResources().value(COREFLAME);
    }
}
''')
print("ok   judge written")
