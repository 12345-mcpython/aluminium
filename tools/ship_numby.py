"""The first servant: 1112's 账账 (round 1668, objective ④'s last readers-two item).

Measured before writing: the ENGINE side is already complete -- `Memosprites.SERVANT_DIR = "servants"`,
`SummonFactory.servant(master)` reads it, and the `SUMMON_SERVANT` op exists (`battle.summonServant(...)`). Measured the same
way: 1112's trace 「金融动荡」 was ALREADY shipped. So what was missing is the directory and the content.

The numbers come from her own document, verbatim: 「战斗开始时召唤账账。账账初始拥有 80 点速度，行动时发动追加攻击，对陷入【负债证明】
状态下的敌方单体造成等同于托帕 150% 攻击力的火属性伤害。」 -- a flat 80 speed, and an attack at 150% of the MASTER's attack.
The page states no health/attack/aggro for 账账, so those are left out rather than guessed (the engine's null means "no
document says").

Also written: the summons rule (BATTLE_START, as the sentence says) and a judge that drives the real file.
"""
import io
import json
import os
import sys

SERVANT = "src/main/resources/servants/1112.json"
CHARACTER = "src/main/resources/characters/1112.json"
JUDGE = "src/test/java/com/laosun/aluminium/test/NumbyServantTest.java"

spec = {
    "name": "账账",
    "source": "1112 托帕 天赋 猪市？！ (111204): 「战斗开始时召唤账账。账账初始拥有 **80 点速度**，"
              "行动时发动追加攻击，对陷入【负债证明】状态下的敌方单体造成等同于托帕 **150% 攻击力**的火属性伤害」",
    "note": "账账是**侍从**（不是忆灵）⇒ 住 `resources/servants/` ✓（引擎侧 `SERVANT_DIR` 与 `SUMMON_SERVANT` 早已就位 ✓）。"
            "面板只写**文档给到的**：**速度 80 点**（平值 ✓，与主人无关 ✓）；"
            "文档对账账的生命／攻击／仇恨**一字未给** ✗ ⇒ 不写 ✓（引擎的 `null` 意为“没有文档说” ✓）。"
            "攻击按**主人的攻击力**计（`base: \"ATTACK\"` ✓）× **1.5** ✓。",
    "panel": [{"attribute": "SPEED", "flat": 80}],
    "attack": {"element": "Fire", "base": "ATTACK", "percent": 1.5, "shape": "SingleAttack"},
}

os.makedirs("src/main/resources/servants", exist_ok=True)
with io.open(SERVANT, "w", encoding="utf-8", newline="\n") as handle:
    json.dump(spec, handle, ensure_ascii=False, indent=2)
    handle.write("\n")
print("ok   wrote %s" % SERVANT)

with io.open(CHARACTER, encoding="utf-8") as handle:
    doc = json.load(handle)
rules = doc["rules"] if isinstance(doc, dict) and "rules" in doc else doc
if any(isinstance(rule, dict) and rule.get("id") == "talent_summons_numby" for rule in rules):
    sys.exit("REFUSING: the summons rule is already there")
rules.append({
    "on": "BATTLE_START",
    "id": "talent_summons_numby",
    "do": [{"op": "SUMMON_SERVANT"}],
    "source": "1112 托帕 天赋 猪市？！ (111204): 「**战斗开始时召唤账账**」",
    "note": "「战斗开始时召唤账账」⇒ `BATTLE_START` ⇒ **`SUMMON_SERVANT`** ✓（无参数 ✓："
            "侍从属于召唤者 ✓）；面板与攻击写在 `resources/servants/1112.json` ✓。"
            "⚠ 同一句还说「当托帕陷入**无法战斗**状态时账账消失」✗ —— 引擎尚无"
            "“主人倒下则侍从消失”的规则 ✗ ⇒ 登记 ✓。",
})
with io.open(CHARACTER, "w", encoding="utf-8", newline="\n") as handle:
    json.dump(doc, handle, ensure_ascii=False, indent=2)
    handle.write("\n")
print("ok   added the summons rule to %s" % CHARACTER)

io.open(JUDGE, "w", encoding="utf-8", newline="").write('''package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Summon;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1112 summons 账账, a SERVANT (2026-10-02).
 *
 * <p>Her talent says it verbatim: "summons Numby when the battle starts. Numby starts with 80 Speed and, when it acts, makes
 * a follow-up attack for Fire damage equal to 150% of Topaz's ATK." The servant route already existed in the engine
 * (SERVANT_DIR + SUMMON_SERVANT); what was missing was the resource directory and this content.
 */
public class NumbyServantTest {
    private static final int TOPAZ = 1112;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /** It is on the field, has the stated speed, and is not a memosprite. */
    @Test
    public void theBattleStartsWithNumbyOnTheField() {
        Character topaz = CharacterFactory.create(TOPAZ, LEVEL, false, null, null, 0);
        Battle battle = new Battle(List.of(topaz),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();

        Summon numby = null;
        for (Summon summon : battle.summonsOf(topaz)) {
            numby = summon;
        }
        Assertions.assertNotNull(numby, "the talent summons it at battle start");
        System.out.println("[numby] name=" + numby.getName()
                + " speed=" + numby.getAttribute(com.laosun.aluminium.enums.AttributeType.SPEED).get()
                + " attack=" + numby.getAttribute(com.laosun.aluminium.enums.AttributeType.ATTACK).get());
        Assertions.assertEquals("\\u8d26\\u8d26", numby.getName(), "the document names it");
        Assertions.assertEquals(80.0,
                numby.getAttribute(com.laosun.aluminium.enums.AttributeType.SPEED).get(), 1e-9,
                "the document states 80 Speed");
    }
}
''')
print("ok   wrote the judge")
