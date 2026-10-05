"""1404: 【血仇】-- entering it, and the lethal blow that ends it (2026-10-02, item 49).

Document, verbatim (1404 万敌 天赋「以血还血 / Blood for Blood」, read from the data as UTF-8 -- the console mangles it):
  「每损失1%生命值积攒1点充能，最多积攒200点。**充能达到100时消耗100点充能进入【血仇】状态**并回复等同于万敌 **#1**% 生命上限的生命值，同时**行动提前100%**。
   【血仇】状态下生命上限提高，数值等同于当前生命上限的 **#5**%，**防御力保持为0**。自身回合开始时自动施放【弑王成王】。
   【血仇】状态期间充能达到 **#3** 点时，万敌立即获得1个额外回合并自动施放【弑神登神】。
   【血仇】状态期间，万敌**受到致命攻击时不会陷入无法战斗状态，但会清空充能退出【血仇】状态**并回复等同于自身 **#4**% 生命上限的生命值。」

PARAMETERS (measured, `skills.json` -> 1404/4/param_list, first row): `[0.15, 0, 150, 0.5, 0.5]` -> #1 = 0.15, #3 = 150,
#4 = 0.5, #5 = 0.5. The project authors rules at the FIRST row and raises skill levels with `level_convention` (which 1404 already
has), so the first row is the right one here.

⭐ WHY THE EXIT IS EXACT: the full text names exactly ONE way out of 【血仇】 -- this lethal clause -- so `permanent: true` plus an
explicit `REMOVE_STATE` here is the sentence, not a shortcut.

⚠ REGISTERED, not shipped, from the same paragraph: the state's own stat block (「生命上限提高，数值等同于**当前**生命上限的 #5%」 and
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
STATE = "血仇"
CHARGE = "天赋充能"

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
    "source": ("1404 万敌 天赋 以血还血 (140404)：「**充能达到 100 时消耗 100 点充能进入"
               "【血仇】状态**并回复等同于万敌 **15%** 生命上限的生命值，同时**行动提前 100%**」"),
    "note": ("⭐ 2026-10-02：**进入【血仇】** ✓（阈值 100 ✓／消耗 100 ✓／治疗 15% ✓／"
             "行动提前 100% ✓）。⚠ **仍登记**：状态内的属性块（生命上限提高、**防御力保持为 0** ✗）"
             "与「每损失 1% 生命积攒 1 点充能」✗（比值无效果层拼法 ✗）。"),
})
rules.append({
    "on": "LETHAL_DAMAGE",
    "id": LEAVE,
    "when": ["actor == self", "self has_state " + STATE],
    "do": [{"op": "SPEND_RESOURCE", "resource": CHARGE, "spendAll": True},
           {"op": "REMOVE_STATE", "buff": STATE, "target": "self"},
           {"op": "HEAL", "scale": "owner_max_hp", "percent": 0.50, "target": "self"}],
    "source": ("1404 万敌 天赋 以血还血 (140404)：「【血仇】状态期间，万敌**受到致命攻击时"
               "不会陷入无法战斗状态，但会清空充能退出【血仇】状态**并回复等同于自身 **50%** 生命上限的生命值」"),
    "note": ("⭐ 2026-10-02：⭐ **`LETHAL_DAMAGE` 的第六位读者** ✓，而且是**唯一会退出一个状态的** ✓。"
             "❗ **“清空充能”用 `spendAll`** ✓（已出货拼法，首个读者是 `1513` ✓）。"
             "❗ **为何 `permanent` 是准确的** ✓：全文只给了**一个**出口（就是本句 ✓）。"),
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
 * 1404：【血仇】——进入它，以及结束它的致命一击 (2026-10-02).
 *
 * <p>⭐ ONE VARIABLE: whether 【血仇】 is on when the lethal blow lands. His ultimate grants 20 charge, so five of them reach the
 * hundred the entry clause needs -- no test-only shortcut into his resource.
 */
public class BloodfeudTest {
    private static final int MYDEI = 1404;
    private static final int MONSTER = 1002011;
    private static final String STATE = "血仇";
    private static final String CHARGE = "天赋充能";

    /** ⭐ A hundred charge enters 【血仇】, and a lethal blow then leaves it -- at half his Max HP. */
    @Test
    public void aLethalBlowEndsItAndHeSurvives() {
        Character him = CharacterFactory.create(MYDEI, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(him),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();

        chargeToAHundred(battle, him);
        Assertions.assertTrue(him.getBuffManager().hasState(STATE),
                "「充能达到 100 时消耗 100 点充能进入【血仇】状态」");

        battle.applyTrueDamage(battle.enemies.getFirst(), him, DamageElement.ICE, him.getCurrentHp() * 2.0);
        battle.processRequests();

        Assertions.assertFalse(him.isDeath(), "「不会陷入无法战斗状态」");
        Assertions.assertEquals(him.getMaxHp() * 0.50, him.getCurrentHp(), him.getMaxHp() * 0.01,
                "「回复等同于自身 50% 生命上限的生命值」");
        Assertions.assertFalse(him.getBuffManager().hasState(STATE), "「退出【血仇】状态」");
        Assertions.assertEquals(0.0, him.getResources().value(CHARGE), 1e-9, "「清空充能」");
    }

    /** ⚠ Without 【血仇】 the same blow kills him -- the clause is the state's, not his. */
    @Test
    public void withoutBloodfeudTheBlowKills() {
        Character him = CharacterFactory.create(MYDEI, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(him),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        Assertions.assertFalse(him.getBuffManager().hasState(STATE), "precondition: no 【血仇】 yet");

        battle.applyTrueDamage(battle.enemies.getFirst(), him, DamageElement.ICE, him.getCurrentHp() * 2.0);
        battle.processRequests();
        Assertions.assertTrue(him.isDeath(), "【血仇】状态期间才有这一条");
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
