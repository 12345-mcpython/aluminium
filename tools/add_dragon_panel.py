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
NEWBUD = "新蕊"

spec = {
    "name": "死龙•玻吕刻斯",
    "source": ("1407 逍蝶 战技 骸爪，冥龙之环拥 (140703): "
               "「召唤忆灵死龙使其行动提前 100%，同时展开境界【遗世冥域】…"
               "死龙初始拥有 165 点速度以及等同于【新蕊】上限 100%」"),
    "note": ("⭐ 面板两项都是**文档原话** ✓：速度 **165** 是常数 ✓"
             "（`flat` ✓），生命是**以【新蕊】为基数**的百分比 ✓"
             "（`source: \"resource:新蕊\"` ✓ + `percent: 1.0` ✓）—— 这是 "
             "`SummonFactory.panelOf` 那支 `resource:` 分支的**第一个读者** ✓。"
             "⚠ **本文件只写面板，不写 `attack`** ✓："
             "忆灵技能 1–8（`:311`–`:527`）是**另外的句子** ✓，"
             "而 `shape` 认哪些字还没查 ✗（一个猜出来的 shape 会是"
             "“没有症状的错数” ✗）⇒ **登记** ✓，下一步先数 shape 词汇 ✓。"
             "⚠ **两个比例** ✗：战技说 **100%** ✓、秘技说 **50%** ✓"
             "（`1407_逍蝶.md:242` ✓）—— 而一份 spec 只能给**一个** `percent` ✗"
             "⇒ 本件取**战技的 100%** ✓ 并在此登记那个 50% ✓"
             "（需要“哪个入口召唤”这个决断 ✗）。"),
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
 * The dead dragon's panel (2026-10-02): speed 165 flat, health = 100% of the 【新蕊】 cap.
 *
 * <p>The first reader of the resource-based panel: the same spec yields a different health for a different resource
 * value, which is exactly what the sentence says.
 */
public class DragonPanelTest {
    /** ⭐ The panel is a share of a battle-level RESOURCE, and the flat speed rides beside it. */
    @Test
    public void theDragonPanelFollowsNewbud() {
        MemospriteSpec spec = Memosprites.of(1407);
        Assertions.assertNotNull(spec, "the dragon's spec must load from resources/memosprites/1407.json");
        Character master = CharacterFactory.create(1407, 80, false, null, null, 0);

        Summon small = SummonFactory.memosprite(master, spec, name -> 1000);
        Assertions.assertEquals(1000, small.getAttribute(AttributeType.HEALTH).get(), 1e-6,
                "100% of a 1000-point 【新蕊】");

        Summon large = SummonFactory.memosprite(master, spec, name -> 34000);
        Assertions.assertEquals(34000, large.getAttribute(AttributeType.HEALTH).get(), 1e-6,
                "and 100% of a 34,000-point one -- the panel follows the resource");
        Assertions.assertEquals(165, large.getAttribute(AttributeType.SPEED).get(), 1e-6,
                "the document states 165 speed, flat");
    }
}
''')
print("ok   judge written")
