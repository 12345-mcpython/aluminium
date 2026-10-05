"""【新蕊】, fifth attempt -- with the one field that was missing (2026-10-02).

`Battle.registerPartyResources` (`:701`) skips every spec whose `scope` is not `"PARTY"` (`:707`) -- so a declaration
without it never enters the battle's registry, `partyResource(name)` returns null, and the writer's gain has nowhere to
go. That single line explains all of the +0s.

Data: 1407.json gains 【新蕊】 with `"scope": "PARTY"` (max 34000 = the document's `5.3125 x level^2` at Lv80) and the
writer on `HP_LOST` with `when: ["target == self"]` (the condition the engine's own comment names) and the EXISTING
`amount_from_event` spelling.
ASCII only.
"""
import io
import json

PATH = "src/main/resources/characters/1407.json"
JUDGE = "src/test/java/com/laosun/aluminium/test/NewbudResourceTest.java"
NEWBUD = "新蕊"

doc = json.load(io.open(PATH, encoding="utf-8"))
rules = doc if isinstance(doc, list) else doc.get("rules", [])
rules = [r for r in rules if not (isinstance(r, dict) and r.get("id") in
                                  ("talent_newbud_per_hp_lost", "diagnostic_newbud_constant"))]
rules.append({
    "on": "HP_LOST",
    "id": "talent_newbud_per_hp_lost",
    "when": ["target == self"],
    "do": [{"op": "GAIN_RESOURCE", "resource": NEWBUD, "amountFromEvent": True}],
    "source": ("1407 逍蝶 天赋 掌心溡过的荒芜 (140704): "
               "「【新蕊】上限与场上全体角色等级有关，"
               "我方全体每损失 1 点生命值逍蝶获得 1 点【新蕊】」"),
    "note": ("⭐ 用已有的 `amountFromEvent` ✓（同 1506 ✓）；"
             "`when: [\"target == self\"]` 照 `Battle.applyDamage:1344` 的注释写 ✓。"
             "⚠ 仅「本人那一半」 ✓：「我方全体」那半看派发 ✓。"),
})
out = {"resources": [{
    "id": NEWBUD,
    "max": 34000,
    "scope": "PARTY",
    "source": ("1407 逍蝶 天赋 掌心溡过的荒芜 (140704) 与「机制备注」："
               "依据 tbgd `Avatar_Castorice_00_Ability.json` 的 `Castorice_Passive_MaxCount` "
               "表达式 `5.3125 × 队伍最高等级²`（结果 ≤ 2000 时钳为 2000）"),
    "note": ("⭐ `\"scope\": \"PARTY\"` **是必需的** ✓："
             "`Battle.registerPartyResources`（`:701`）会**跳过**所有 `scope` 不是 `PARTY` 的声明 ✓"
             "（`:707`）⇒ 没有它就进不了战斗的资源表 ✗，"
             "`partyResource(名)` 会返回 **null** ✗ —— 这一行解释了前面全部的 +0 ✓。"
             "⚠ 上限 **34,000** = 文档公式在**满级 Lv80** 的值 ✓"
             "（文档表：均衡 0 ⇒ 2,125 … 满级 ⇒ 34,000 ✓）；"
             "⚠ 资源只能声明**一个** `max` ✗ 而真实上限随等级变 ✗"
             "⇒ 取满级值并在此说明 ✓。"),
}], "rules": rules}
json.dump(out, io.open(PATH, "w", encoding="utf-8", newline="\n"), ensure_ascii=False, indent=2)
print("ok   1407.json: 新蕊 declared with scope PARTY + the writer")

io.open(JUDGE, "w", encoding="utf-8", newline="").write('''package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 【新蕊】 (2026-10-02): 「我方全体每损失 1 点生命值逍蝶获得 1 点【新蕊】」, the carrier's own half.
 *
 * <p>Three facts bound the number: the battle knows the resource (a PARTY scope is what puts it in the registry), the hit
 * really lands through `Battle.applyDamage`, and the gain equals the loss.
 */
public class NewbudResourceTest {
    private static final int CASTORICE = 1407;
    private static final int MONSTER = 1002011;

    /** ⭐ One point of health lost is one point of Newbud. */
    @Test
    public void aPointLostIsAPointOfNewbud() {
        Character castorice = CharacterFactory.create(CASTORICE, 80, false, null, null, 0);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(castorice), List.of(enemy), new Random(0));
        battle.startBattle();
        Assertions.assertNotNull(battle.partyResource("\\u65b0\\u854a"),
                "the battle must know 【新蕊】 (does the declaration say scope PARTY?)");
        int before = battle.partyResourceValue("\\u65b0\\u854a");
        double hpBefore = castorice.getCurrentHp();
        battle.applyDamage(castorice, new Damage(enemy, castorice, DamageElement.FIRE, DamageType.NORMAL, 300));
        battle.processRequests();
        double lost = hpBefore - castorice.getCurrentHp();
        Assertions.assertTrue(lost > 0, "precondition: the hit landed (" + lost + ")");
        Assertions.assertEquals((int) Math.round(lost), battle.partyResourceValue("\\u65b0\\u854a") - before,
                "one point of Newbud per point lost (" + lost + " lost)");
    }
}
''')
print("ok   judge written")
