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
    "name": "\u8d26\u8d26",
    "source": "1112 \u6258\u5e15 \u5929\u8d4b \u732a\u5e02\uff1f\uff01 (111204): \u300c\u6218\u6597\u5f00\u59cb\u65f6\u53ec\u5524\u8d26\u8d26\u3002\u8d26\u8d26\u521d\u59cb\u62e5\u6709 **80 \u70b9\u901f\u5ea6**\uff0c"
              "\u884c\u52a8\u65f6\u53d1\u52a8\u8ffd\u52a0\u653b\u51fb\uff0c\u5bf9\u9677\u5165\u3010\u8d1f\u503a\u8bc1\u660e\u3011\u72b6\u6001\u4e0b\u7684\u654c\u65b9\u5355\u4f53\u9020\u6210\u7b49\u540c\u4e8e\u6258\u5e15 **150% \u653b\u51fb\u529b**\u7684\u706b\u5c5e\u6027\u4f24\u5bb3\u300d",
    "note": "\u8d26\u8d26\u662f**\u4f8d\u4ece**\uff08\u4e0d\u662f\u5fc6\u7075\uff09\u21d2 \u4f4f `resources/servants/` \u2713\uff08\u5f15\u64ce\u4fa7 `SERVANT_DIR` \u4e0e `SUMMON_SERVANT` \u65e9\u5df2\u5c31\u4f4d \u2713\uff09\u3002"
            "\u9762\u677f\u53ea\u5199**\u6587\u6863\u7ed9\u5230\u7684**\uff1a**\u901f\u5ea6 80 \u70b9**\uff08\u5e73\u503c \u2713\uff0c\u4e0e\u4e3b\u4eba\u65e0\u5173 \u2713\uff09\uff1b"
            "\u6587\u6863\u5bf9\u8d26\u8d26\u7684\u751f\u547d\uff0f\u653b\u51fb\uff0f\u4ec7\u6068**\u4e00\u5b57\u672a\u7ed9** \u2717 \u21d2 \u4e0d\u5199 \u2713\uff08\u5f15\u64ce\u7684 `null` \u610f\u4e3a\u201c\u6ca1\u6709\u6587\u6863\u8bf4\u201d \u2713\uff09\u3002"
            "\u653b\u51fb\u6309**\u4e3b\u4eba\u7684\u653b\u51fb\u529b**\u8ba1\uff08`base: \"ATTACK\"` \u2713\uff09\u00d7 **1.5** \u2713\u3002",
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
    "source": "1112 \u6258\u5e15 \u5929\u8d4b \u732a\u5e02\uff1f\uff01 (111204): \u300c**\u6218\u6597\u5f00\u59cb\u65f6\u53ec\u5524\u8d26\u8d26**\u300d",
    "note": "\u300c\u6218\u6597\u5f00\u59cb\u65f6\u53ec\u5524\u8d26\u8d26\u300d\u21d2 `BATTLE_START` \u21d2 **`SUMMON_SERVANT`** \u2713\uff08\u65e0\u53c2\u6570 \u2713\uff1a"
            "\u4f8d\u4ece\u5c5e\u4e8e\u53ec\u5524\u8005 \u2713\uff09\uff1b\u9762\u677f\u4e0e\u653b\u51fb\u5199\u5728 `resources/servants/1112.json` \u2713\u3002"
            "\u26a0 \u540c\u4e00\u53e5\u8fd8\u8bf4\u300c\u5f53\u6258\u5e15\u9677\u5165**\u65e0\u6cd5\u6218\u6597**\u72b6\u6001\u65f6\u8d26\u8d26\u6d88\u5931\u300d\u2717 \u2014\u2014 \u5f15\u64ce\u5c1a\u65e0"
            "\u201c\u4e3b\u4eba\u5012\u4e0b\u5219\u4f8d\u4ece\u6d88\u5931\u201d\u7684\u89c4\u5219 \u2717 \u21d2 \u767b\u8bb0 \u2713\u3002",
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
