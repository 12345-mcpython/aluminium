"""1404: 【血仇】-- entering it, and the lethal blow that ends it (2026-10-02, item 49).

Document, verbatim (1404 万敌 天赋「以血还血 / Blood for Blood」, read from the data as UTF-8 -- the console mangles it):
  「每损失1%生命值积攒1点充能，最多积攒200点。**充能达到100时消耗100点充能进入【血仇】状态**并回复等同于万敌 **#1**% 生命上限的生命值，同时**行动提前100%**。
   【血仇】状态下生命上限提高，数值等同于当前生命上限的 **#5**%，**防御力保持为0**。自身回合开始时自动施放【弑王成王】。
   【血仇】状态期间充能达到 **#3** 点时，万敌立即获得1个额外回合并自动施放【弑神登神】。
   【血仇】状态期间，万敌**受到致命攻击时不会陷入无法战斗状态，但会清空充能退出【血仇】状态**并回复等同于自身 **#4**% 生命上限的生命值。」

PARAMETERS (measured, `skills.json` -> 1404/4/param_list, first row): `[0.15, 0, 150, 0.5, 0.5]` -> #1 = 0.15, #3 = 150,
#4 = 0.5, #5 = 0.5. The project authors rules at the FIRST row and raises skill levels with `level_convention` (which 1404 already
has), so the first row is the right one here.

\u2b50 WHY THE EXIT IS EXACT: the full text names exactly ONE way out of 【血仇】 -- this lethal clause -- so `permanent: true` plus an
explicit `REMOVE_STATE` here is the sentence, not a shortcut.

\u26a0 REGISTERED, not shipped, from the same paragraph: the state's own stat block (「生命上限提高，数值等同于**当前**生命上限的 #5%」 and
「**防御力保持为0**」), and 「每损失1%生命值积攒1点充能」 (a ratio of HP lost to max HP, which has no effect-level spelling), and
「充能达到 #3 点时…获得 1 个额外回合并自动施放【弑神登神】」.
ASCII only.
"""
import io
import json

DATA = "src/main/resources/characters/1404.json"
JUDGE = "src/test/java/com/laosun/aluminium/test/BloodfeudTest.java"
ENTER = "talent_enters_bloodfeud_at_a_hundred"
LEAVE = "bloodfeud_survives_a_lethal_blow_and_ends"
STATE = "\u8840\u4ec7"
CHARGE = "\u5929\u8d4b\u5145\u80fd"

doc = json.load(io.open(DATA, encoding="utf-8"))
isObject = isinstance(doc, dict)
rules = doc["rules"] if isObject else doc
rules = [r for r in rules if not (isinstance(r, dict) and r.get("id") in (ENTER, LEAVE))]

if len(rules) != 4:
    raise SystemExit("expected the four shipped 1404 rules, found " + str(len(rules)))

rules.append({
    "on": "RESOURCE_CHANGED",
    "id": ENTER,
    "when": ["resource_changed:" + CHARGE, "self_resource:" + CHARGE + " >= 100"],
    "do": [{"op": "SPEND_RESOURCE", "resource": CHARGE, "amount": 100},
           {"op": "APPLY_BUFF", "buff": STATE, "permanent": True, "target": "self"},
           {"op": "HEAL", "scale": "owner_max_hp", "percent": 0.15, "target": "self"},
           {"op": "ADVANCE", "percent": 1.0, "target": "self"}],
    "source": ("1404 \u4e07\u654c \u5929\u8d4b \u4ee5\u8840\u8fd8\u8840 (140404)\uff1a\u300c**\u5145\u80fd\u8fbe\u5230 100 \u65f6\u6d88\u8017 100 \u70b9\u5145\u80fd\u8fdb\u5165"
               "\u3010\u8840\u4ec7\u3011\u72b6\u6001**\u5e76\u56de\u590d\u7b49\u540c\u4e8e\u4e07\u654c **15%** \u751f\u547d\u4e0a\u9650\u7684\u751f\u547d\u503c\uff0c\u540c\u65f6**\u884c\u52a8\u63d0\u524d 100%**\u300d"),
    "note": ("\u2b50 2026-10-02\uff1a**\u8fdb\u5165\u3010\u8840\u4ec7\u3011** \u2713\uff08\u9608\u503c 100 \u2713\uff0f\u6d88\u8017 100 \u2713\uff0f\u6cbb\u7597 15% \u2713\uff0f"
             "\u884c\u52a8\u63d0\u524d 100% \u2713\uff09\u3002\u26a0 **\u4ecd\u767b\u8bb0**\uff1a\u72b6\u6001\u5185\u7684\u5c5e\u6027\u5757\uff08\u751f\u547d\u4e0a\u9650\u63d0\u9ad8\u3001**\u9632\u5fa1\u529b\u4fdd\u6301\u4e3a 0** \u2717\uff09"
             "\u4e0e\u300c\u6bcf\u635f\u5931 1% \u751f\u547d\u79ef\u6512 1 \u70b9\u5145\u80fd\u300d\u2717\uff08\u6bd4\u503c\u65e0\u6548\u679c\u5c42\u62fc\u6cd5 \u2717\uff09\u3002"),
})
rules.append({
    "on": "LETHAL_DAMAGE",
    "id": LEAVE,
    "when": ["actor == self", "self has_state " + STATE],
    "do": [{"op": "SPEND_RESOURCE", "resource": CHARGE, "spendAll": True},
           {"op": "REMOVE_STATE", "buff": STATE, "target": "self"},
           {"op": "HEAL", "scale": "owner_max_hp", "percent": 0.50, "target": "self"}],
    "source": ("1404 \u4e07\u654c \u5929\u8d4b \u4ee5\u8840\u8fd8\u8840 (140404)\uff1a\u300c\u3010\u8840\u4ec7\u3011\u72b6\u6001\u671f\u95f4\uff0c\u4e07\u654c**\u53d7\u5230\u81f4\u547d\u653b\u51fb\u65f6"
               "\u4e0d\u4f1a\u9677\u5165\u65e0\u6cd5\u6218\u6597\u72b6\u6001\uff0c\u4f46\u4f1a\u6e05\u7a7a\u5145\u80fd\u9000\u51fa\u3010\u8840\u4ec7\u3011\u72b6\u6001**\u5e76\u56de\u590d\u7b49\u540c\u4e8e\u81ea\u8eab **50%** \u751f\u547d\u4e0a\u9650\u7684\u751f\u547d\u503c\u300d"),
    "note": ("\u2b50 2026-10-02\uff1a\u2b50 **`LETHAL_DAMAGE` \u7684\u7b2c\u516d\u4f4d\u8bfb\u8005** \u2713\uff0c\u800c\u4e14\u662f**\u552f\u4e00\u5e26"**\u9000\u51fa\u4e00\u4e2a\u72b6\u6001**"\u7684** \u2713\u3002"
             "\u2757 **\u201c\u6e05\u7a7a\u5145\u80fd\u201d\u7528 `spendAll`** \u2713\uff08\u5df2\u51fa\u8d27\u62fc\u6cd5\uff0c\u9996\u4e2a\u8bfb\u8005\u662f `1513` \u2713\uff09\u3002"
             "\u2757 **\u4e3a\u4f55 `permanent` \u662f\u51c6\u786e\u7684** \u2713\uff1a\u5168\u6587\u53ea\u7ed9\u4e86**\u4e00\u4e2a**\u51fa\u53e3\uff08\u5c31\u662f\u672c\u53e5 \u2713\uff09\u3002"),
})

if isObject:
    doc["rules"] = rules
else:
    doc = rules
json.dump(doc, io.open(DATA, "w", encoding="utf-8", newline="\n"), ensure_ascii=False, indent=2)
print("ok   1404.json: bloodfeud entered at a hundred, ended by a lethal blow")

io.open(JUDGE, "w", encoding="utf-8", newline="").write('''package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.DamageElement;
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
 * 1404\uff1a\u3010\u8840\u4ec7\u3011\u2014\u2014\u8fdb\u5165\u5b83\uff0c\u4ee5\u53ca\u7ed3\u675f\u5b83\u7684\u81f4\u547d\u4e00\u51fb (2026-10-02).
 *
 * <p>\u2b50 ONE VARIABLE: whether \u3010\u8840\u4ec7\u3011 is on when the lethal blow lands. His ultimate grants 20 charge, so five of them reach the
 * hundred the entry clause needs -- no test-only shortcut into his resource.
 */
public class BloodfeudTest {
    private static final int MYDEI = 1404;
    private static final int MONSTER = 1002011;
    private static final String STATE = "\u8840\u4ec7";
    private static final String CHARGE = "\u5929\u8d4b\u5145\u80fd";

    /** \u2b50 A hundred charge enters \u3010\u8840\u4ec7\u3011, and a lethal blow then leaves it -- at half his Max HP. */
    @Test
    public void aLethalBlowEndsItAndHeSurvives() {
        Character him = CharacterFactory.create(MYDEI, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(him),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();

        chargeToAHundred(battle, him);
        Assertions.assertTrue(him.getBuffManager().hasState(STATE),
                "\u300c\u5145\u80fd\u8fbe\u5230 100 \u65f6\u6d88\u8017 100 \u70b9\u5145\u80fd\u8fdb\u5165\u3010\u8840\u4ec7\u3011\u72b6\u6001\u300d");

        battle.applyTrueDamage(battle.enemies.getFirst(), him, DamageElement.ICE, him.getCurrentHp() * 2.0);
        battle.processRequests();

        Assertions.assertFalse(him.isDeath(), "\u300c\u4e0d\u4f1a\u9677\u5165\u65e0\u6cd5\u6218\u6597\u72b6\u6001\u300d");
        Assertions.assertEquals(him.getMaxHp() * 0.50, him.getCurrentHp(), him.getMaxHp() * 0.01,
                "\u300c\u56de\u590d\u7b49\u540c\u4e8e\u81ea\u8eab 50% \u751f\u547d\u4e0a\u9650\u7684\u751f\u547d\u503c\u300d");
        Assertions.assertFalse(him.getBuffManager().hasState(STATE), "\u300c\u9000\u51fa\u3010\u8840\u4ec7\u3011\u72b6\u6001\u300d");
        Assertions.assertEquals(0.0, him.getResources().value(CHARGE), 1e-9, "\u300c\u6e05\u7a7a\u5145\u80fd\u300d");
    }

    /** \u26a0 Without \u3010\u8840\u4ec7\u3011 the same blow kills him -- the clause is the state's, not his. */
    @Test
    public void withoutBloodfeudTheBlowKills() {
        Character him = CharacterFactory.create(MYDEI, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(him),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        Assertions.assertFalse(him.getBuffManager().hasState(STATE), "precondition: no \u3010\u8840\u4ec7\u3011 yet");

        battle.applyTrueDamage(battle.enemies.getFirst(), him, DamageElement.ICE, him.getCurrentHp() * 2.0);
        battle.processRequests();
        Assertions.assertTrue(him.isDeath(), "\u3010\u8840\u4ec7\u3011\u72b6\u6001\u671f\u95f4\u624d\u6709\u8fd9\u4e00\u6761");
    }

    // ==================================================================

    /** his ultimate grants 20 charge, so five casts reach a hundred. */
    private static void chargeToAHundred(Battle battle, Character him) {
        Skill ult = him.getSkills().get(SkillType.ULTRA);
        Assertions.assertNotNull(ult, "precondition: he has an ultimate");
        for (int i = 0; i < 5; i++) {
            SkillExecutor.execute(battle, ult, him, List.of(battle.enemies.getFirst()));
            battle.processRequests();
        }
    }
}
''')
print("ok   judge written")
