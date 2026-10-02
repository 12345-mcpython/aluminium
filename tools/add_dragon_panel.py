"""The dead dragon's panel (2026-10-02): `memosprites/1407.json` + its judge.

Sentence: 1407's skill 「召唤忆灵死龙使其行动提前 100%…**死龙初始拥有 165 点速度以及等同于【新蕊】上限 100%**」, and the
technique 「…死龙拥有等同于【新蕊】上限 **50%**」.

This ships the PANEL only, because that is the sentence: the dragon's skills (忆灵技能 1-8) are separate sentences whose
`shape` vocabulary has not been checked yet, and an attack with a guessed shape would be a wrong number with no symptom.
The 50% technique variant is registered in the note -- a spec states ONE percent, and which entrance summons the dragon
is a decision this project has not made yet.

The panel is the first reader of the resource-based panel shipped earlier (`SummonFactory.panelOf`'s `resource:` branch)
and of 【新蕊】 itself (shipped an hour ago).
ASCII only.
"""
import io
import json
import os

PATH = "src/main/resources/memosprites/1407.json"
JUDGE = "src/test/java/com/laosun/aluminium/test/DragonPanelTest.java"
NEWBUD = "\u65b0\u854a"

spec = {
    "name": "\u6b7b\u9f99\u2022\u73bb\u5415\u523b\u65af",
    "source": ("1407 \u900d\u8776 \u6218\u6280 \u9ab8\u722a\uff0c\u51a5\u9f99\u4e4b\u73af\u62e5 (140703): "
               "\u300c\u53ec\u5524\u5fc6\u7075\u6b7b\u9f99\u4f7f\u5176\u884c\u52a8\u63d0\u524d 100%\uff0c\u540c\u65f6\u5c55\u5f00\u5883\u754c\u3010\u9057\u4e16\u51a5\u57df\u3011\u2026"
               "\u6b7b\u9f99\u521d\u59cb\u62e5\u6709 165 \u70b9\u901f\u5ea6\u4ee5\u53ca\u7b49\u540c\u4e8e\u3010\u65b0\u854a\u3011\u4e0a\u9650 100%\u300d"),
    "note": ("\u2b50 \u9762\u677f\u4e24\u9879\u90fd\u662f**\u6587\u6863\u539f\u8bdd** \u2713\uff1a\u901f\u5ea6 **165** \u662f\u5e38\u6570 \u2713"
             "\uff08`flat` \u2713\uff09\uff0c\u751f\u547d\u662f**\u4ee5\u3010\u65b0\u854a\u3011\u4e3a\u57fa\u6570**\u7684\u767e\u5206\u6bd4 \u2713"
             "\uff08`source: \"resource:\u65b0\u854a\"` \u2713 + `percent: 1.0` \u2713\uff09\u2014\u2014 \u8fd9\u662f "
             "`SummonFactory.panelOf` \u90a3\u652f `resource:` \u5206\u652f\u7684**\u7b2c\u4e00\u4e2a\u8bfb\u8005** \u2713\u3002"
             "\u26a0 **\u672c\u6587\u4ef6\u53ea\u5199\u9762\u677f\uff0c\u4e0d\u5199 `attack`** \u2713\uff1a"
             "\u5fc6\u7075\u6280\u80fd 1\u20138\uff08`:311`\u2013`:527`\uff09\u662f**\u53e6\u5916\u7684\u53e5\u5b50** \u2713\uff0c"
             "\u800c `shape` \u8ba4\u54ea\u4e9b\u5b57\u8fd8\u6ca1\u67e5 \u2717\uff08\u4e00\u4e2a\u731c\u51fa\u6765\u7684 shape \u4f1a\u662f"
             "\u201c\u6ca1\u6709\u75c7\u72b6\u7684\u9519\u6570\u201d \u2717\uff09\u21d2 **\u767b\u8bb0** \u2713\uff0c\u4e0b\u4e00\u6b65\u5148\u6570 shape \u8bcd\u6c47 \u2713\u3002"
             "\u26a0 **\u4e24\u4e2a\u6bd4\u4f8b** \u2717\uff1a\u6218\u6280\u8bf4 **100%** \u2713\u3001\u79d8\u6280\u8bf4 **50%** \u2713"
             "\uff08`1407_\u900d\u8776.md:242` \u2713\uff09\u2014\u2014 \u800c\u4e00\u4efd spec \u53ea\u80fd\u7ed9**\u4e00\u4e2a** `percent` \u2717"
             "\u21d2 \u672c\u4ef6\u53d6**\u6218\u6280\u7684 100%** \u2713 \u5e76\u5728\u6b64\u767b\u8bb0\u90a3\u4e2a 50% \u2713"
             "\uff08\u9700\u8981\u201c\u54ea\u4e2a\u5165\u53e3\u53ec\u5524\u201d\u8fd9\u4e2a\u51b3\u65ad \u2717\uff09\u3002"),
    "aggro": 100,
    "panel": [
        {"attribute": "HEALTH", "percent": 1.0, "source": "resource:" + NEWBUD},
        {"attribute": "SPEED", "flat": 165},
    ],
}
os.makedirs(os.path.dirname(PATH), exist_ok=True)
json.dump(spec, io.open(PATH, "w", encoding="utf-8", newline="\n"), ensure_ascii=False, indent=2)
print("ok   memosprites/1407.json written")

io.open(JUDGE, "w", encoding="utf-8", newline="").write('''package com.laosun.aluminium.test;

import com.laosun.aluminium.beans.MemospriteSpec;
import com.laosun.aluminium.data.Memosprites;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.Summon;
import com.laosun.aluminium.models.enemy.SummonFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * The dead dragon's panel (2026-10-02): speed 165 flat, health = 100% of the 【\u65b0\u854a】 cap.
 *
 * <p>The first reader of the resource-based panel: the same spec yields a different health for a different resource
 * value, which is exactly what the sentence says.
 */
public class DragonPanelTest {
    /** \u2b50 The panel is a share of a battle-level RESOURCE, and the flat speed rides beside it. */
    @Test
    public void theDragonPanelFollowsNewbud() {
        MemospriteSpec spec = Memosprites.of(1407);
        Assertions.assertNotNull(spec, "the dragon's spec must load from resources/memosprites/1407.json");
        Character master = CharacterFactory.create(1407, 80, false, null, null, 0);

        Summon small = SummonFactory.memosprite(master, spec, name -> 1000);
        Assertions.assertEquals(1000, small.getAttribute(AttributeType.HEALTH).get(), 1e-6,
                "100% of a 1000-point 【\u65b0\u854a】");

        Summon large = SummonFactory.memosprite(master, spec, name -> 34000);
        Assertions.assertEquals(34000, large.getAttribute(AttributeType.HEALTH).get(), 1e-6,
                "and 100% of a 34,000-point one -- the panel follows the resource");
        Assertions.assertEquals(165, large.getAttribute(AttributeType.SPEED).get(), 1e-6,
                "the document states 165 speed, flat");
    }
}
''')
print("ok   judge written")
