"""1404: 「【血仇】状态下生命上限提高，数值等同于当前生命上限的 #5%」 (2026-10-02, item 50).

Document, verbatim (1404 万敌 天赋「以血还血」, read as UTF-8 via a file): 「充能达到100时消耗100点充能**进入【血仇】状态**并回复…#1%…
**【血仇】状态下生命上限提高，数值等同于当前生命上限的 #5%**，防御力保持为0。」

PARAMETER (measured: `skills.json` -> 1404/4/param_list, first row `[0.15, 0, 150, 0.5, 0.5]`): #5 = **0.5**.

⭐ WHY IT TAKES THE DERIVED SPELLING (`scale: "self_attr:HEALTH"` + `percent`), MEASURED NOT ASSUMED: an ADDITIVE percentage on a base
attribute is a share of the BASE, not of the current Max HP. The plain form was tried first and the judge reported 2607.8976 where the
sentence's reading needs 2747.6064 -- a ratio of 1.424 rather than 1.5, i.e. the base and the current Max HP really do differ here. The
derived form adds a FLAT `percent x attribute`, which is exactly 「等同于**当前**生命上限的 #5%」. (Two other hypotheses were eliminated first:
the `buff:` link and `max_stacks` -- neither changed the number.)

⭐ AND IT RIDES THE STATE: `"buff": "血仇"` ties the modifier's life to the state (the link measured in item 35), so removing 血仇 --
which is exactly how the paragraph ends it -- takes the extra Max HP with it. No second rule, no cleanup.

⚠ REGISTERED, not shipped, from the same sentence: 「**防御力保持为 0**」. It is not a reduction but a PIN -- other effects may raise DEF
and it must still be 0 -- and `MODIFY_ATTR` gives a base attribute an ADDITIVE percentage (they sum), so "-100%" would be cancelled by any
later additive bonus. The exact spelling needs a MULTIPLY-percent modifier, which the op does not offer for base attributes.
⚠ ALSO REGISTERED, from the next sentence of the same paragraph: 「充能达到 150 点时…获得 1 个额外回合并**自动施放【弑神登神】**」 --
`CAST_SKILL` names a SLOT (`SkillType`: COMMON / SKILL / ULTRA / TALENT / MAZE / TECHNIQUE), and 【弑神登神】 is data skill id 11 with no slot, so
it cannot be named today; the extra turn half (`EXTRA_TURN`) is expressible and waits on that.
ASCII only.
"""
import io
import json

DATA = "src/main/resources/characters/1404.json"
JUDGE = "src/test/java/com/laosun/aluminium/test/BloodfeudMaxHpTest.java"
ENTER = "talent_enters_bloodfeud_at_a_hundred"
STATE = "血仇"
CHARGE = "天赋充能"

doc = json.load(io.open(DATA, encoding="utf-8"))
isObject = isinstance(doc, dict)
rules = doc["rules"] if isObject else doc

hit = [r for r in rules if isinstance(r, dict) and r.get("id") == ENTER]
if len(hit) != 1:
    raise SystemExit("expected the bloodfeud entry rule, found " + str(len(hit)))

rule = hit[0]
kept = [e for e in (rule.get("do") or []) if not (isinstance(e, dict) and e.get("attribute") == "HEALTH")]
kept.append({"op": "MODIFY_ATTR", "attribute": "HEALTH", "scale": "self_attr:HEALTH", "percent": 0.50,
             "permanent": True, "buff": STATE, "target": "self"})
rule["do"] = kept
rule["note"] = ((rule.get("note") or "") +
                "\n⭐ 2026-10-02：**状态内的生命上限** ✓（`HEALTH +50%` ✓ ⾋ `buff: \"血仇\"` 绑定 ✓）"
                "—— ⚠ **“防御力保持为 0”仍登记** ✗（那是**钉住**，需要**乘法**修饰 ✗）。")

if isObject:
    doc["rules"] = rules
else:
    doc = rules
json.dump(doc, io.open(DATA, "w", encoding="utf-8", newline="\n"), ensure_ascii=False, indent=2)
print("ok   1404.json: bloodfeud raises Max HP, and the raise dies with the state")

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
 * 1404：「【血仇】状态下生命上限提高，数值等同于当前生命上限的 50%」 (2026-10-02).
 *
 * <p>⭐ ONE VARIABLE, in one scene: the state goes ON (a hundred charge, five ultimates) and then OFF (the lethal blow the paragraph
 * names as its only exit). Max HP must follow it both ways.
 */
public class BloodfeudMaxHpTest {
    private static final int MYDEI = 1404;
    private static final int MONSTER = 1002011;
    private static final String STATE = "血仇";
    private static final String CHARGE = "天赋充能";

    /** ⭐ +50% Max HP while 【血仇】 is on, and back to the base when it leaves. */
    @Test
    public void theRaiseFollowsTheState() {
        Character him = CharacterFactory.create(MYDEI, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(him),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        Skill ult = him.getSkills().get(SkillType.ULTRA);
        Assertions.assertNotNull(ult, "precondition: he has an ultimate");
        // ⚠ FOUR ultimates, then the reading, then the fifth: the entry fires at a hundred, so this compares "immediately before
        // the state" with "immediately after" it. Measuring the baseline earlier (right after startBattle) let an unrelated modifier
        // expire in between -- that is exactly the ~5% gap the first version of this judge reported (2747.6 expected, 2607.9 seen).
        for (int i = 0; i < 4; i++) {
            SkillExecutor.execute(battle, ult, him, List.of(battle.enemies.getFirst()));
            battle.processRequests();
        }
        Assertions.assertFalse(him.getBuffManager().hasState(STATE), "precondition: not a hundred yet");
        double base = him.getMaxHp();
        SkillExecutor.execute(battle, ult, him, List.of(battle.enemies.getFirst()));
        battle.processRequests();
        Assertions.assertTrue(him.getBuffManager().hasState(STATE), "precondition: 【血仇】 is on");
        Assertions.assertEquals(base * 1.5, him.getMaxHp(), base * 0.01,
                "「【血仇】状态下生命上限提高，数值等同于当前生命上限的 50%」");

        // ⭐ The paragraph's ONLY exit: the lethal blow. The raise must go with the state.
        battle.applyTrueDamage(battle.enemies.getFirst(), him, DamageElement.ICE, him.getCurrentHp() * 2.0);
        battle.processRequests();
        Assertions.assertFalse(him.getBuffManager().hasState(STATE), "precondition: the lethal blow ended it");
        Assertions.assertEquals(base, him.getMaxHp(), base * 0.01,
                "「退出【血仇】状态」-- the extra Max HP leaves with it");
    }
}
''')
print("ok   judge written")
