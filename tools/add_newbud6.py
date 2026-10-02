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
NEWBUD = "\u65b0\u854a"

doc = json.load(io.open(PATH, encoding="utf-8"))
rules = doc if isinstance(doc, list) else doc.get("rules", [])
rules = [r for r in rules if not (isinstance(r, dict) and r.get("id") in
                                  ("talent_newbud_per_hp_lost", "diagnostic_newbud_constant"))]
rules.append({
    "on": "HP_LOST",
    "id": "talent_newbud_per_hp_lost",
    "when": ["target == self"],
    "do": [{"op": "GAIN_RESOURCE", "resource": NEWBUD, "amountFromEvent": True}],
    "source": ("1407 \u900d\u8776 \u5929\u8d4b \u638c\u5fc3\u6ea1\u8fc7\u7684\u8352\u829c (140704): "
               "\u300c\u3010\u65b0\u854a\u3011\u4e0a\u9650\u4e0e\u573a\u4e0a\u5168\u4f53\u89d2\u8272\u7b49\u7ea7\u6709\u5173\uff0c"
               "\u6211\u65b9\u5168\u4f53\u6bcf\u635f\u5931 1 \u70b9\u751f\u547d\u503c\u900d\u8776\u83b7\u5f97 1 \u70b9\u3010\u65b0\u854a\u3011\u300d"),
    "note": ("\u2b50 \u7528\u5df2\u6709\u7684 `amountFromEvent` \u2713\uff08\u540c 1506 \u2713\uff09\uff1b"
             "`when: [\"target == self\"]` \u7167 `Battle.applyDamage:1344` \u7684\u6ce8\u91ca\u5199 \u2713\u3002"
             "\u26a0 \u4ec5\u300c\u672c\u4eba\u90a3\u4e00\u534a\u300d \u2713\uff1a\u300c\u6211\u65b9\u5168\u4f53\u300d\u90a3\u534a\u770b\u6d3e\u53d1 \u2713\u3002"),
})
out = {"resources": [{
    "id": NEWBUD,
    "max": 34000,
    "scope": "PARTY",
    "source": ("1407 \u900d\u8776 \u5929\u8d4b \u638c\u5fc3\u6ea1\u8fc7\u7684\u8352\u829c (140704) \u4e0e\u300c\u673a\u5236\u5907\u6ce8\u300d\uff1a"
               "\u4f9d\u636e tbgd `Avatar_Castorice_00_Ability.json` \u7684 `Castorice_Passive_MaxCount` "
               "\u8868\u8fbe\u5f0f `5.3125 \u00d7 \u961f\u4f0d\u6700\u9ad8\u7b49\u7ea7\u00b2`\uff08\u7ed3\u679c \u2264 2000 \u65f6\u94b3\u4e3a 2000\uff09"),
    "note": ("\u2b50 `\"scope\": \"PARTY\"` **\u662f\u5fc5\u9700\u7684** \u2713\uff1a"
             "`Battle.registerPartyResources`\uff08`:701`\uff09\u4f1a**\u8df3\u8fc7**\u6240\u6709 `scope` \u4e0d\u662f `PARTY` \u7684\u58f0\u660e \u2713"
             "\uff08`:707`\uff09\u21d2 \u6ca1\u6709\u5b83\u5c31\u8fdb\u4e0d\u4e86\u6218\u6597\u7684\u8d44\u6e90\u8868 \u2717\uff0c"
             "`partyResource(\u540d)` \u4f1a\u8fd4\u56de **null** \u2717 \u2014\u2014 \u8fd9\u4e00\u884c\u89e3\u91ca\u4e86\u524d\u9762\u5168\u90e8\u7684 +0 \u2713\u3002"
             "\u26a0 \u4e0a\u9650 **34,000** = \u6587\u6863\u516c\u5f0f\u5728**\u6ee1\u7ea7 Lv80** \u7684\u503c \u2713"
             "\uff08\u6587\u6863\u8868\uff1a\u5747\u8861 0 \u21d2 2,125 \u2026 \u6ee1\u7ea7 \u21d2 34,000 \u2713\uff09\uff1b"
             "\u26a0 \u8d44\u6e90\u53ea\u80fd\u58f0\u660e**\u4e00\u4e2a** `max` \u2717 \u800c\u771f\u5b9e\u4e0a\u9650\u968f\u7b49\u7ea7\u53d8 \u2717"
             "\u21d2 \u53d6\u6ee1\u7ea7\u503c\u5e76\u5728\u6b64\u8bf4\u660e \u2713\u3002"),
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
 * \u3010\u65b0\u854a\u3011 (2026-10-02): \u300c\u6211\u65b9\u5168\u4f53\u6bcf\u635f\u5931 1 \u70b9\u751f\u547d\u503c\u900d\u8776\u83b7\u5f97 1 \u70b9\u3010\u65b0\u854a\u3011\u300d, the carrier's own half.
 *
 * <p>Three facts bound the number: the battle knows the resource (a PARTY scope is what puts it in the registry), the hit
 * really lands through `Battle.applyDamage`, and the gain equals the loss.
 */
public class NewbudResourceTest {
    private static final int CASTORICE = 1407;
    private static final int MONSTER = 1002011;

    /** \u2b50 One point of health lost is one point of Newbud. */
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
