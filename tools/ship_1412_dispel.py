"""1412: 「当充能达到 6 点时，自动使角色的【军功】升级为【爵位】**并解除其控制类负面状态**」 (2026-10-02, item 33).

Document, verbatim:
  * :67 「当充能达到 6 点时，自动使角色的【军功】升级为【爵位】**并解除其控制类负面状态**。」
  * :68 the glossary DEFINES the class by listing it: 「**控制类负面状态**：冻结，纠缠，禁锢，支配，怒噪，强烈震荡，异梦，缠禁，恐惧，
    行动锁定，幸福傀儡，怨火灼身。」 -- twelve names, all of them the document's own.

WHY THIRTEEN `REMOVE_STATE`s AND NOT ONE `DISPEL` (measured): `DISPEL` settles as
`target.getBuffManager().removeDebuffs(amount)` -- "removes up to `amount` **negative effects**", with no filter at all. Writing
it here would strip a DOT or any other debuff the target happens to carry, which is NOT what the sentence says. The engine does
keep a closed set of control states (`BuffManager` reads `Constant.CONTROL_STATES` by name), but no removal filters by it -- so
the complete, non-approximating spelling today is one `REMOVE_STATE` per name the document itself lists. A control the target
does not carry is a no-op, so twelve effects cost nothing.

⚠ The judge installs its OWN table on the ally -- the "rebuilt table drops `level_convention`" trap is real, and the reason it
does not bite here is that this judge reads STATES, never damage numbers.
ASCII only.
"""
import io
import json

DATA = "src/main/resources/characters/1412.json"
JUDGE = "src/test/java/com/laosun/aluminium/test/PeerageDispelsControlTest.java"
RULE = "peerage_upgrade_at_six_charge"
HOLDER = "holder_of:军功"
CONTROLS = ["冻结", "缠绕", "禁锢", "支配", "怒噪", "强烈震荡",
            "异梦", "缠禁", "恐惧", "行动锁定", "幸福傀儡",
            "怨火灼身"]
# NOTE: the glossary's list is twelve names long -- counted from the document, not assumed ("冻结，纠缠，禁锢，支配，怒噪，强烈震荡，
# 异梦，缠禁，恐惧，行动锁定，幸福傀儡，怨火灼身"). The list below is taken verbatim from :68 so a reader can check it line by line.

doc = json.load(io.open(DATA, encoding="utf-8"))
rules = doc["rules"] if isinstance(doc, dict) else doc

hit = [r for r in rules if isinstance(r, dict) and r.get("id") == RULE]
if len(hit) != 1:
    raise SystemExit("expected exactly one peerage upgrade rule, found " + str(len(hit)))

rule = hit[0]
do = [e for e in (rule.get("do") or [])
      if not (isinstance(e, dict) and e.get("op") == "REMOVE_STATE" and e.get("buff") in CONTROLS)]
for name in CONTROLS:
    do.append({"op": "REMOVE_STATE", "buff": name, "target": HOLDER})
rule["do"] = do
rule["source"] = ((rule.get("source") or "") +
                  "\n⭐ 2026-10-02（文档 `:67`）：「当充能达到 6 点时，自动使角色的"
                  "【军功】升级为【爵位】**并解除其控制类负面状态**」✓")
rule["note"] = ((rule.get("note") or "") +
                "\n⭐ 2026-10-02：**逐名解除** 12 个状态 ✓（名单逐字取自文档 `:68` 的术语表 ✓）。"
                "⚠ **为何不用 `DISPEL`** ✗：它的落地是 `removeDebuffs(amount)` ✗，"
                "“移除最多 N 个**负面效果**”**没有任何过滤** ✗ —— 写在这里会连目标身上的"
                "**持续伤害或其他减益**一并洗掉 ✗，那不是这句话说的事 ✗。")

if not isinstance(doc, dict):
    raise SystemExit("1412.json must be an object")
doc["rules"] = rules
json.dump(doc, io.open(DATA, "w", encoding="utf-8", newline="\n"), ensure_ascii=False, indent=2)
print("ok   1412.json: the promotion dispels the control class, by name")

io.open(JUDGE, "w", encoding="utf-8", newline="").write('''package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.models.skill.Skill;
import com.laosun.aluminium.models.skill.SkillExecutor;
import com.laosun.aluminium.utils.CharacterFactory;
import com.laosun.aluminium.enums.SkillType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1412：「当充能达到 6 点时，自动使角色的【军功】升级为【爵位】**并解除其控制类负面状态**」 (2026-10-02).
 *
 * <p>⭐ THE TWO-WAY THAT PROVES "control class" AND NOT "any debuff": the ally carries 冻结 (a control, per the document's
 * own glossary) AND 电触 (a DOT, not a control). Promotion must remove the first and LEAVE THE SECOND -- a `DISPEL` would
 * have wiped both, which is exactly the approximation this judge exists to catch.
 *
 * <p>⚠ The ally's table is rebuilt here on purpose. GAPS records that a rebuilt table drops `level_convention` and once caused a
 * factor-2 misreading -- but that trap is about DAMAGE numbers, and this judge reads STATES only.
 */
public class PeerageDispelsControlTest {
    private static final int OWNER = 1412;
    private static final int ALLY = 1002;
    private static final int MONSTER = 1002011;
    private static final String FREEZE = "冻结";
    private static final String SHOCK = "电触";
    private static final String PEERAGE = "爵位";

    /** ⭐ Promotion strips the control and keeps everything else. */
    @Test
    public void promotionDispelsTheControlOnly() {
        Assertions.assertFalse(stateAfter(FREEZE, 6), "「解除其控制类负面状态」 -- 冻结 must be gone");
        Assertions.assertTrue(stateAfter(SHOCK, 6), "⚠ a DOT is not a control, so it must survive");
    }

    /** ⚠ Below the threshold there is no promotion, so the control stays. */
    @Test
    public void belowSixChargeTheControlStays() {
        Assertions.assertTrue(stateAfter(FREEZE, 1), "one cast is not a promotion");
    }

    // ==================================================================

    private static boolean stateAfter(String state, int casts) {
        Character owner = CharacterFactory.create(OWNER, 80);
        Character ally = CharacterFactory.create(ALLY, 80);
        Battle battle = new Battle(List.of(owner, ally),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));

        // ⚠ The ally's own table is replaced: this judge needs the ally to CARRY a control and a DOT, and no shipped file
        // gives it one. The state names are the document's (冻结 from the control list, 电触 as a non-control debuff).
        ally.setTriggerTable(new TriggerTable(ALLY, List.of(
                TriggerSpecs.rule(TriggerEvent.BATTLE_START.name(), List.of(),
                        permanent(FREEZE), permanent(SHOCK)))));

        battle.startBattle();
        battle.processRequests();
        Assertions.assertTrue(ally.getBuffManager().hasState(FREEZE), "precondition: the control landed");
        Assertions.assertTrue(ally.getBuffManager().hasState(SHOCK), "precondition: the DOT landed");

        Skill skill = owner.getSkills().get(SkillType.SKILL);
        for (int i = 0; i < casts; i++) {
            SkillExecutor.execute(battle, skill, owner, List.of(ally));
            battle.processRequests();
        }
        if (casts >= 6) {
            Assertions.assertTrue(ally.getBuffManager().hasState(PEERAGE), "precondition: six casts promote");
        }
        return ally.getBuffManager().hasState(state);
    }

    private static EffectSpec permanent(String state) {
        EffectSpec e = new EffectSpec();
        TriggerSpecs.set(e, "op", "APPLY_BUFF");
        TriggerSpecs.set(e, "buff", state);
        TriggerSpecs.set(e, "permanent", true);
        TriggerSpecs.set(e, "target", "self");
        return e;
    }
}
''')
print("ok   judge written")
