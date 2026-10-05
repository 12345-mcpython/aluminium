"""1217: 「该效果单场战斗中可以触发 **2** 次」 -- the trigger limit the shipped rule was missing (2026-10-02, item 52).

Why this is a fix and not a new capability: item 47 shipped the save, and the SAME sentence carries the limit --
  「当藐藐拥有【禳命】时，若我方目标受到致命攻击，不会陷入无法战斗状态，并立即回复等同于其自身生命上限 **50%** 的生命值。
    **该效果单场战斗中可以触发 2 次**。」
`param` read from the data: `eidolons.json -> 1217/2/param = [0.5, 2]` -> #1 = 0.5 (the heal) and #2 = **2** (the count).

⭐⭐ THE SPELLING ALREADY EXISTS IN THE TREE, and it was named for exactly this sentence family -- `TriggerInterpreter:762`:
  *"「每当我方目标…施放 2 次…后」／「**触发 2 次后自动解除**」: a **counter**, which is a named stack buff (see `StackBuff`)"*
so nothing new is built here: a counter is `ADD_STACK` plus a `self_stacks:<name>` condition, copied from shipped `1111.json`
(`ADD_STACK` at :10, `"self_stacks:斗志 >= 2"` at :69). The counter lives on the rule's OWNER (her), which is what
`self_stacks:` reads.
"""
import io
import json

DATA = "src/main/resources/characters/1217.json"
JUDGE = "src/test/java/com/laosun/aluminium/test/TalismanSavesAnAllyTest.java"
RULE = "e2_saves_an_ally_with_the_talisman"
COUNTER = "救主计数"          # the battle-long counter: how many times the save has fired
LIMIT = 2

doc = json.load(io.open(DATA, encoding="utf-8"))
if not isinstance(doc, list):
    raise SystemExit("1217.json is expected to be a bare list")
rules = [r for r in doc if isinstance(r, dict) and r.get("id") == RULE]
if len(rules) != 1:
    raise SystemExit("expected exactly one shipped %s rule, found %d" % (RULE, len(rules)))
rule = rules[0]

condition = "self_stacks:%s < %d" % (COUNTER, LIMIT)
if condition in rule["when"]:
    raise SystemExit("the limit is already stated")
rule["when"].append(condition)
rule["do"].append({
    "op": "ADD_STACK",
    "buff": COUNTER,
    "amount": 1,
    "max_stacks": LIMIT,
    "target": "self",
    "permanent": True,
})
rule["source"] = ("1217 藿藿 星魂 2 「判官书符，镇尾锁灵」：「当**藿藿拥有【"
                  "禳命】时**，若**我方目标**受到致命攻击，不会陷入**无法战斗"
                  "状态**，并立即回复等同于**其自身**生命上限 **50%** 的生命值。"
                  "**该效果单场战斗中可以触发 2 次**」（`param = [0.5, 2]` ⇒ #1 = 50% ✓，#2 = 2 次 ✓）")
rule["note"] = ("⭐ 2026-10-02（第 52 件）：⭐ **补上同一句里的上限** ✓。"
                "⚠ 上限就在原句里（「该效果单场战斗中可以触发 **2** 次」），"
                "而 `param` 的 #2 = **2** ✓。⭐ **拼法是现成的** ✓：计数器 = `ADD_STACK` + "
                "`self_stacks:<名>` 条件（样板 `1111.json` 的【斗志】✓；而 `TriggerInterpreter:762` 的注释"
                "**点名了这一句**：「触发 2 次后自动解除」⇒ 计数器 ✓）。"
                "⚠ 计数器写在**规则主人自己**身上 ✓（`self_stacks:` 读的就是规则主人 ✓）。"
                "⚠ **无需引擎改动** ✓（本来以为要加 `per_battle` 字段 ✗，已回滚 ✓）。"
                "⚠ **仍登记**：「使【禳命】的持续回合数**减 1**」✗（`EXTEND_BUFF` 只收正数 ✗）。")

json.dump(doc, io.open(DATA, "w", encoding="utf-8", newline="\n"), ensure_ascii=False, indent=2)
print("ok   1217.json: the e2 save now fires at most twice per battle")

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
 * 1217：「当藿藿拥有【禳命】时，若我方目标受到致命攻击…立即回复等同于其自身生命上限 50% 的生命值。
 * 该效果单场战斗中可以触发 2 次」 (2026-10-02).
 *
 * <p>⭐ ONE VARIABLE per test: the eidolon rank, or the NUMBER OF LETHAL BLOWS. Same party, same skill (which is what puts
 * 【禳命】 on her), same blow.
 */
public class TalismanSavesAnAllyTest {
    private static final int HUOHUO = 1217;
    private static final int ALLY = 1002;
    private static final int MONSTER = 1002011;
    private static final String STATE = "禳命";

    /** ⭐ At E2 the ally survives, at HALF OF ITS OWN Max HP. */
    @Test
    public void atEidolonTwoTheAllySurvives() {
        double[] result = afterLethalBlows(2, 1);
        Assertions.assertTrue(result[0] > 0, "「不会陷入无法战斗状态」 (hp " + result[0] + ")");
        Assertions.assertEquals(result[1] * 0.50, result[0], result[1] * 0.01,
                "「回复等同于**其自身**生命上限 50%」-- the VICTIM’s own, not the healer’s");
    }

    /** ⚠ Below E2 the ally falls. */
    @Test
    public void belowEidolonTwoTheAllyFalls() {
        Assertions.assertTrue(afterLethalBlows(0, 1)[0] <= 0, "星魂 2 才有这一条");
    }

    /**
     * ⭐⭐ 「该效果单场战斗中可以触发 **2** 次」: the first two blows are answered, the third is not.
     *
     * <p>⭐ The count is a shipped spelling, not a new capability: a counter is `ADD_STACK` plus a `self_stacks:` condition
     * (sample: 1111's 【斗志】). ⚠ The two answered blows each leave her ALLY at half of ITS OWN Max HP, so the third
     * blow is lethal again -- the assertion is about the count, not about a first-blow-only effect.
     */
    @Test
    public void theLimitIsTwoBlowsInOneBattle() {
        double[] one = afterLethalBlows(2, 1);
        double[] two = afterLethalBlows(2, 2);
        double[] three = afterLethalBlows(2, 3);
        Assertions.assertTrue(one[0] > 0, "第一次被救");
        Assertions.assertTrue(two[0] > 0, "第二次仍被救（「可以触发 2 次」）");
        Assertions.assertTrue(three[0] <= 0,
                "第三次**不再**被救（「可以触发 2 次」的上限）");
    }

    // ==================================================================

    /** { the ally's HP after the blows, the ally's Max HP }. */
    private static double[] afterLethalBlows(int eidolon, int blows) {
        Character her = CharacterFactory.create(HUOHUO, 80, false, null, null, eidolon);
        Character ally = CharacterFactory.create(ALLY, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(her, ally),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();

        // Her skill is what puts 【禳命】 on her (2 turns, ticking on her own turns).
        Skill skill = her.getSkills().get(SkillType.SKILL);
        Assertions.assertNotNull(skill, "precondition: she has a skill");
        SkillExecutor.execute(battle, skill, her, List.of(battle.enemies.getFirst()));
        battle.processRequests();
        Assertions.assertTrue(her.getBuffManager().hasState(STATE), "precondition: 【禳命】 is on her");

        for (int blow = 0; blow < blows; blow++) {
            if (ally.isDeath()) {
                break;
            }
            battle.applyTrueDamage(battle.enemies.getFirst(), ally, DamageElement.ICE, ally.getCurrentHp() * 2.0);
            battle.processRequests();
        }
        return new double[]{ally.isDeath() ? 0 : ally.getCurrentHp(), ally.getMaxHp()};
    }
}
''')
print("ok   judge rewritten: the original two assertions, plus the third blow")
