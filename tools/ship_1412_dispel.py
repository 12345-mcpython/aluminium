"""1412: 「当充能达到 6 点时，自动使角色的【军功】升级为【爵位】**并解除其控制类负面状态**」 (2026-10-02, item 33).

Document, verbatim:
  * :67 「当充能达到 6 点时，自动使角色的【军功】升级为【爵位】**并解除其控制类负面状态**。」
  * :68 the glossary DEFINES the class by listing it: 「**控制类负面状态**：冻结，纠缠，禁锢，支配，怒噪，强烈震荡，异梦，缠禁，恐惧，
    行动锁定，幸福傀儡，怨火灼身。」 -- thirteen names, all of them the document's own.

WHY THIRTEEN `REMOVE_STATE`s AND NOT ONE `DISPEL` (measured): `DISPEL` settles as
`target.getBuffManager().removeDebuffs(amount)` -- "removes up to `amount` **negative effects**", with no filter at all. Writing
it here would strip a DOT or any other debuff the target happens to carry, which is NOT what the sentence says. The engine does
keep a closed set of control states (`BuffManager` reads `Constant.CONTROL_STATES` by name), but no removal filters by it -- so
the complete, non-approximating spelling today is one `REMOVE_STATE` per name the document itself lists. A control the target
does not carry is a no-op, so thirteen effects cost nothing.

\u26a0 The judge installs its OWN table on the ally -- the "rebuilt table drops `level_convention`" trap is real, and the reason it
does not bite here is that this judge reads STATES, never damage numbers.
ASCII only.
"""
import io
import json

DATA = "src/main/resources/characters/1412.json"
JUDGE = "src/test/java/com/laosun/aluminium/test/PeerageDispelsControlTest.java"
RULE = "peerage_upgrade_at_six_charge"
HOLDER = "holder_of:\u519b\u529f"
CONTROLS = ["\u51bb\u7ed3", "\u7f20\u7ed5", "\u7981\u9522", "\u652f\u914d", "\u6012\u566a", "\u5f3a\u70c8\u9707\u8361",
            "\u5f02\u68a6", "\u7f20\u7981", "\u6050\u60e7", "\u884c\u52a8\u9501\u5b9a", "\u5e78\u798f\u5080\u5121",
            "\u6028\u706b\u707c\u8eab"]
# NOTE: the glossary lists thirteen; 缠禁 (above) is the twelfth and 怨火灼身 the thirteenth. The list is taken verbatim from
# :68 so that a reader can check it against the document line by line.

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
                  "\n\u2b50 2026-10-02\uff08\u6587\u6863 `:67`\uff09\uff1a\u300c\u5f53\u5145\u80fd\u8fbe\u5230 6 \u70b9\u65f6\uff0c\u81ea\u52a8\u4f7f\u89d2\u8272\u7684"
                  "\u3010\u519b\u529f\u3011\u5347\u7ea7\u4e3a\u3010\u7235\u4f4d\u3011**\u5e76\u89e3\u9664\u5176\u63a7\u5236\u7c7b\u8d1f\u9762\u72b6\u6001**\u300d\u2713")
rule["note"] = ((rule.get("note") or "") +
                "\n\u2b50 2026-10-02\uff1a**\u9010\u540d\u89e3\u9664** 12 \u4e2a\u72b6\u6001 \u2713\uff08\u540d\u5355\u9010\u5b57\u53d6\u81ea\u6587\u6863 `:68` \u7684\u672f\u8bed\u8868 \u2713\uff09\u3002"
                "\u26a0 **\u4e3a\u4f55\u4e0d\u7528 `DISPEL`** \u2717\uff1a\u5b83\u7684\u843d\u5730\u662f `removeDebuffs(amount)` \u2717\uff0c"
                "\u201c\u79fb\u9664\u6700\u591a N \u4e2a**\u8d1f\u9762\u6548\u679c**\u201d**\u6ca1\u6709\u4efb\u4f55\u8fc7\u6ee4** \u2717 \u2014\u2014 \u5199\u5728\u8fd9\u91cc\u4f1a\u8fde\u76ee\u6807\u8eab\u4e0a\u7684"
                "**\u6301\u7eed\u4f24\u5bb3\u6216\u5176\u4ed6\u51cf\u76ca**\u4e00\u5e76\u6d17\u6389 \u2717\uff0c\u90a3\u4e0d\u662f\u8fd9\u53e5\u8bdd\u8bf4\u7684\u4e8b \u2717\u3002")

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
 * 1412\uff1a\u300c\u5f53\u5145\u80fd\u8fbe\u5230 6 \u70b9\u65f6\uff0c\u81ea\u52a8\u4f7f\u89d2\u8272\u7684\u3010\u519b\u529f\u3011\u5347\u7ea7\u4e3a\u3010\u7235\u4f4d\u3011**\u5e76\u89e3\u9664\u5176\u63a7\u5236\u7c7b\u8d1f\u9762\u72b6\u6001**\u300d (2026-10-02).
 *
 * <p>\u2b50 THE TWO-WAY THAT PROVES "control class" AND NOT "any debuff": the ally carries \u51bb\u7ed3 (a control, per the document's
 * own glossary) AND \u7535\u89e6 (a DOT, not a control). Promotion must remove the first and LEAVE THE SECOND -- a `DISPEL` would
 * have wiped both, which is exactly the approximation this judge exists to catch.
 *
 * <p>\u26a0 The ally's table is rebuilt here on purpose. GAPS records that a rebuilt table drops `level_convention` and once caused a
 * factor-2 misreading -- but that trap is about DAMAGE numbers, and this judge reads STATES only.
 */
public class PeerageDispelsControlTest {
    private static final int OWNER = 1412;
    private static final int ALLY = 1002;
    private static final int MONSTER = 1002011;
    private static final String FREEZE = "\u51bb\u7ed3";
    private static final String SHOCK = "\u7535\u89e6";
    private static final String PEERAGE = "\u7235\u4f4d";

    /** \u2b50 Promotion strips the control and keeps everything else. */
    @Test
    public void promotionDispelsTheControlOnly() {
        Assertions.assertFalse(stateAfter(FREEZE, 6), "\u300c\u89e3\u9664\u5176\u63a7\u5236\u7c7b\u8d1f\u9762\u72b6\u6001\u300d -- \u51bb\u7ed3 must be gone");
        Assertions.assertTrue(stateAfter(SHOCK, 6), "\u26a0 a DOT is not a control, so it must survive");
    }

    /** \u26a0 Below the threshold there is no promotion, so the control stays. */
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

        // \u26a0 The ally's own table is replaced: this judge needs the ally to CARRY a control and a DOT, and no shipped file
        // gives it one. The state names are the document's (\u51bb\u7ed3 from the control list, \u7535\u89e6 as a non-control debuff).
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
