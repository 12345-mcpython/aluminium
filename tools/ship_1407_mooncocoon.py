"""1407 遐蝶 仓库技「月茧之庇」: a lethal blow is DEFERRED, and the carrier falls at its own turn's end (2026-10-02, item 54).

Document, verbatim (the ability page, read as UTF-8):
  「战斗中，若我方角色受到致命攻击，则本次行动中所有受到致命攻击的我方角色**获得【月茧】状态**。
    【月茧】状态下的角色会**暂时延后陷入无法战斗状态**，且**可以正常行动**。
    若**行动后**、**下一次回合开始前**当前生命值**提高或获得护盾**，则**解除【月茧】状态**，否则将**立即陷入无法战斗状态**。」
`param` read from the data: the ability's own row is `[0.1]`, \u26a0 and the sentence uses no number at all -- this is a pure mechanism,
which is why the trace carries no percentage.

\u2b50 WHAT THIS NEEDED (and what it did NOT): two events the engine already had -- `HEALED` and `SHIELD_GRANTED`, which are
exactly 「当前生命值提高或获得护盾」 -- plus ONE new capability: a state that makes the engine HOLD a death instead of
committing it, and commit it at the carrier's TURN_END. \u26a0 The commit point is the turn's END on purpose: the sentence says
the carrier 「可以正常行动」, so a turn-start commit would kill it before it acts.

\u26a0 \u300c\u4ed3\u5e93\u6280\u300d (a passive that applies once the character is owned, even off-team) is only half-modelled: the engine fires a
character's rules when that character is in the party. The off-team half is registered, not approximated.
"""
import io
import json
import os

DATA = "src/main/resources/characters/1407.json"
JUDGE = "src/test/java/com/laosun/aluminium/test/MooncocoonTest.java"
STATE = "\u6708\u8327"
SOURCE = ("1407 \u9050\u8776 \u4ed3\u5e93\u6280 \u300c\u6708\u8327\u4e4b\u5e87 / Sanctuary of Mooncocoon\u300d\uff1a\u300c\u6218\u6597\u4e2d\uff0c\u82e5\u6211\u65b9\u89d2\u8272\u53d7\u5230\u81f4\u547d\u653b\u51fb\uff0c"
          "\u5219\u672c\u6b21\u884c\u52a8\u4e2d\u6240\u6709\u53d7\u5230\u81f4\u547d\u653b\u51fb\u7684\u6211\u65b9\u89d2\u8272**\u83b7\u5f97\u3010\u6708\u8327\u3011\u72b6\u6001**\u3002\u3010\u6708\u8327\u3011\u72b6\u6001\u4e0b\u7684\u89d2\u8272\u4f1a"
          "**\u6682\u65f6\u5ef6\u540e\u9677\u5165\u65e0\u6cd5\u6218\u6597\u72b6\u6001**\uff0c\u4e14**\u53ef\u4ee5\u6b63\u5e38\u884c\u52a8**\u3002\u82e5**\u884c\u52a8\u540e**\u3001**\u4e0b\u4e00\u6b21\u56de\u5408\u5f00\u59cb\u524d**"
          "\u5f53\u524d\u751f\u547d\u503c**\u63d0\u9ad8\u6216\u83b7\u5f97\u62a4\u76fe**\uff0c\u5219**\u89e3\u9664\u3010\u6708\u8327\u3011\u72b6\u6001**\uff0c\u5426\u5219\u5c06**\u7acb\u5373\u9677\u5165\u65e0\u6cd5\u6218\u6597\u72b6\u6001**\u300d")
NOTE = ("\u2b50 2026-10-02\uff08\u7b2c 54 \u4ef6\uff09\uff1a\u2b50 **\u5ef6\u540e\u6b7b\u4ea1** \u2713 \u2014\u2014 \u539f\u53e5\u8bf4\u7684\u662f\u300c\u6682\u65f6\u5ef6\u540e\u9677\u5165\u65e0\u6cd5\u6218\u6597\u72b6\u6001\u300d\u2713"
        "\uff0c\u800c**\u4e0d\u662f**\u56de\u8840 \u2717 \u21d2 \u2757 \u53d7\u5bb3\u8005\u771f\u7684\u505c\u5728 **0 \u8840**\uff08\u90a3\u5c31\u662f\u300c\u5ef6\u540e\u300d\u672c\u8eab \u2713\uff09\uff0c**\u6551\u5b83\u7684\u662f\u72b6\u6001\u6d88\u5931** \u2713\u3002"
        "\u2b50 \u4e24\u4e2a\u4e8b\u4ef6\u662f\u73b0\u6210\u7684 \u2713\uff08`HEALED` \u2713 \u4e0e `SHIELD_GRANTED` \u2713 = \u300c\u751f\u547d\u503c\u63d0\u9ad8\u6216\u83b7\u5f97\u62a4\u76fe\u300d\u2713\uff09"
        "\uff0c\u800c**\u65b0\u80fd\u529b\u53ea\u6709\u4e00\u4ef6**\uff1a\u4e00\u4e2a\u4f7f\u5f15\u64ce**\u6301\u6709**\u6b7b\u4ea1\u800c\u4e0d\u63d0\u4ea4\u7684\u72b6\u6001 \u2713\uff08`defers_death` \u2713 / `DeferredDeathBuff` \u2713\uff09\u3002"
        "\u26a0 \u63d0\u4ea4\u70b9\u5728\u8be5\u5355\u4f4d\u7684 **`TURN_END`** \u2713 \u2014\u2014 \u56e0\u4e3a\u539f\u53e5\u8bf4\u300c\u53ef\u4ee5**\u6b63\u5e38\u884c\u52a8**\u300d\u2713\uff0c"
        "\u5728\u56de\u5408**\u5f00\u59cb**\u524d\u63d0\u4ea4\u4f1a\u8ba9\u5b83\u6ca1\u884c\u52a8\u5c31\u5012 \u2717\uff08\u6211\u7b2c\u4e00\u7248\u5c31\u662f\u90a3\u4e48\u5199\u7684 \u2717\uff0c\u5df2\u6539 \u2713\uff09\u3002"
        "\u26a0 **\u4ed3\u5e93\u6280\u53ea\u5b9e\u73b0\u4e00\u534a** \u2717\uff1a\u5f15\u64ce\u53ea\u5728\u8be5\u89d2\u8272**\u5728\u961f**\u65f6\u53d1\u5b83\u7684\u8868 \u2713\uff1b\u300c\u83b7\u5f97\u8be5\u89d2\u8272\u540e\uff08\u4e0d\u4e0a\u573a\u4e5f\u751f\u6548\uff09\u300d\u90a3\u4e00\u534a\u5df2\u767b\u8bb0 \u2717\u3002")

rules = [
    {
        "on": "LETHAL_DAMAGE",
        "id": "mooncocoon_holds_the_blow",
        "when": ["actor is_ally"],
        "do": [{"op": "APPLY_BUFF", "buff": STATE, "defers_death": True, "permanent": True, "target": "target"}],
        "source": SOURCE,
        "note": NOTE + " \u26a0 \u300c\u672c\u6b21\u884c\u52a8\u4e2d**\u6240\u6709**\u300d\u7531**\u9010\u4e2a\u53d7\u5bb3\u8005\u5404\u81ea\u89e6\u53d1**\u5b9e\u73b0 \u2713\uff08\u4e0e\u7b2c 51 \u4ef6\u540c\u4e00\u7ed3\u8bba \u2713\uff09\u3002",
    },
    {
        "on": "HEALED",
        "id": "mooncocoon_a_heal_ends_it",
        "when": ["target has_state " + STATE],
        "do": [{"op": "REMOVE_STATE", "buff": STATE, "target": "target"}],
        "source": SOURCE,
        "note": "\u300c\u82e5\u2026\u5f53\u524d\u751f\u547d\u503c**\u63d0\u9ad8**\u2026\u5219\u89e3\u9664\u3010\u6708\u8327\u3011\u72b6\u6001\u300d \u2713 \u2014\u2014 \u7528\u73b0\u6210\u7684 `HEALED` \u4e8b\u4ef6 \u2713"
                "\uff08`actor` = \u63d0\u4f9b\u8005\u3001`target` = \u53d7\u60e0\u8005 \u2713\uff09\u3002\u26a0 \u5b88\u536b\u5199\u6210 `target has_state \u6708\u8327` \u2713\uff1a\u53ea\u6709**\u8fd8\u5728\u5ef6\u540e\u4e2d**\u7684\u90a3\u4e2a\u5355\u4f4d\u624d\u4f1a\u88ab\u89e3\u9664 \u2713\u3002",
    },
    {
        "on": "SHIELD_GRANTED",
        "id": "mooncocoon_a_shield_ends_it",
        "when": ["target has_state " + STATE],
        "do": [{"op": "REMOVE_STATE", "buff": STATE, "target": "target"}],
        "source": SOURCE,
        "note": "\u300c\u2026\u6216**\u83b7\u5f97\u62a4\u76fe**\uff0c\u5219\u89e3\u9664\u3010\u6708\u8327\u3011\u72b6\u6001\u300d \u2713 \u2014\u2014 \u7528\u73b0\u6210\u7684 `SHIELD_GRANTED` \u4e8b\u4ef6 \u2713\u3002"
                "\u26a0 \u62a4\u76fe\u8ddf\u6cbb\u7597\u662f**\u4e24\u6761\u72ec\u7acb\u7684\u9000\u51fa\u8def** \u2713\uff08\u539f\u53e5\u7684\u300c\u6216\u300d\u2713\uff09\u3002",
    },
]

if os.path.exists(DATA):
    doc = json.load(io.open(DATA, encoding="utf-8"))
    existing = doc["rules"] if isinstance(doc, dict) else doc
    kept = [r for r in existing if not (isinstance(r, dict) and r.get("id", "").startswith("mooncocoon_"))]
    kept.extend(rules)
    if isinstance(doc, dict):
        doc["rules"] = kept
        out = doc
    else:
        out = kept
else:
    out = rules
json.dump(out, io.open(DATA, "w", encoding="utf-8", newline="\n"), ensure_ascii=False, indent=2)
print("ok   1407.json: three mooncocoon rules")

io.open(JUDGE, "w", encoding="utf-8", newline="").write('''package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Signal;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.models.skill.Skill;
import com.laosun.aluminium.models.skill.SkillExecutor;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1407\uff1a\u300c\u82e5\u6211\u65b9\u89d2\u8272\u53d7\u5230\u81f4\u547d\u653b\u51fb\uff0c\u5219\u2026\u83b7\u5f97\u3010\u6708\u8327\u3011\u72b6\u6001\u3002\u3010\u6708\u8327\u3011\u72b6\u6001\u4e0b\u7684\u89d2\u8272\u4f1a\u6682\u65f6\u5ef6\u540e\u9677\u5165\u65e0\u6cd5\u6218\u6597\u72b6\u6001\uff0c
 * \u4e14\u53ef\u4ee5\u6b63\u5e38\u884c\u52a8\u3002\u82e5\u884c\u52a8\u540e\u3001\u4e0b\u4e00\u6b21\u56de\u5408\u5f00\u59cb\u524d\u5f53\u524d\u751f\u547d\u503c\u63d0\u9ad8\u6216\u83b7\u5f97\u62a4\u76fe\uff0c\u5219\u89e3\u9664\u3010\u6708\u8327\u3011\u72b6\u6001\uff0c\u5426\u5219\u5c06\u7acb\u5373\u9677\u5165\u65e0\u6cd5\u6218\u6597\u72b6\u6001\u300d (2026-10-02).
 *
 * <p>\u2b50 THREE READINGS OF ONE SENTENCE: it does not fall; it falls once its turn is over; a heal before that saves it.
 */
public class MooncocoonTest {
    private static final int CASTORICE_BANNER = 1407;
    private static final int HEALER = 1211;
    private static final int VICTIM = 1002;
    private static final int MONSTER = 1002011;
    private static final String STATE = "\u6708\u8327";

    /** \u2b50\u2b50 \u300c\u6682\u65f6\u5ef6\u540e\u9677\u5165\u65e0\u6cd5\u6218\u6597\u72b6\u6001\u300d: the blow is held -- the victim is ALIVE, at zero HP. */
    @Test
    public void theBlowIsHeld() {
        Battle battle = fight();
        Character victim = battle.getAllies().get(1);
        strike(battle, victim);
        Assertions.assertFalse(victim.isDeath(), "\u300c\u4e0d\u4f1a\u9677\u5165\u65e0\u6cd5\u6218\u6597\u72b6\u6001\u300d");
        Assertions.assertEquals(0.0, victim.getCurrentHp(), 1e-9, "\u5b83\u771f\u7684\u505c\u5728 0 \u8840 \u2014\u2014 \u90a3\u5c31\u662f\u300c\u5ef6\u540e\u300d");
        Assertions.assertTrue(victim.getBuffManager().hasState(STATE), "\u300c\u83b7\u5f97\u3010\u6708\u8327\u3011\u72b6\u6001\u300d");
    }

    /** \u2b50\u2b50 \u300c\u884c\u52a8\u540e\u2026\u5426\u5219\u5c06\u7acb\u5373\u9677\u5165\u65e0\u6cd5\u6218\u6597\u72b6\u6001\u300d: nothing saved it, so its own turn's END commits the death. */
    @Test
    public void itsOwnTurnEndsIt() {
        Battle battle = fight();
        Character victim = battle.getAllies().get(1);
        strike(battle, victim);
        Assertions.assertFalse(victim.isDeath(), "precondition: the blow was held");
        takeItsTurn(battle, victim);
        Assertions.assertTrue(victim.isDeath(), "\u6ca1\u6709\u4eba\u6551\u5b83 \u21d2 \u5b83\u81ea\u5df1\u7684\u56de\u5408\u7ed3\u675f\u65f6\u5012\u4e0b");
    }

    /** \u2b50\u2b50 \u300c\u82e5\u884c\u52a8\u540e\u2026\u5f53\u524d\u751f\u547d\u503c\u63d0\u9ad8\u2026\u5219\u89e3\u9664\u3010\u6708\u8327\u3011\u72b6\u6001\u300d: a real heal from a real healer saves it. */
    @Test
    public void aHealBeforeItsTurnSaves() {
        Battle battle = fight();
        Character victim = battle.getAllies().get(2);
        Character healer = battle.getAllies().get(1);
        strike(battle, victim);
        Assertions.assertTrue(victim.getBuffManager().hasState(STATE), "precondition: it carries the trace");
        Skill heal = healer.getSkills().get(SkillType.SKILL);
        Assertions.assertNotNull(heal, "precondition: the healer has a skill");
        SkillExecutor.execute(battle, heal, healer, List.of(victim));
        battle.processRequests();
        Assertions.assertFalse(victim.getBuffManager().hasState(STATE),
                "\u751f\u547d\u503c\u63d0\u9ad8 \u21d2 \u3010\u6708\u8327\u3011\u89e3\u9664");
        takeItsTurn(battle, victim);
        Assertions.assertFalse(victim.isDeath(), "\u3010\u6708\u8327\u3011\u5df2\u89e3\u9664 \u21d2 \u5b83\u4e0d\u518d\u5012\u4e0b");
    }

    // ==================================================================

    /** 1407 (the passive's owner), 1211 (the healer) and the victim. */
    private static Battle fight() {
        Character owner = CharacterFactory.create(CASTORICE_BANNER, 80, false, null, null, 0);
        Character healer = CharacterFactory.create(HEALER, 80, false, null, null, 0);
        Character victim = CharacterFactory.create(VICTIM, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(owner, healer, victim),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        return battle;
    }

    private static void strike(Battle battle, Character victim) {
        battle.applyTrueDamage(battle.enemies.getFirst(), victim, DamageElement.ICE, victim.getMaxHp() * 2.0);
        battle.processRequests();
    }

    /** \u26a0 A WHOLE turn: the early tick on `beforeMove`, the late one and TURN_END on `afterMove`. */
    private static void takeItsTurn(Battle battle, Character unit) {
        Signal signal = battle.queue.snapshot().stream()
                .filter(candidate -> candidate.getCanHit() == unit).findFirst()
                .orElseThrow(() -> new AssertionError("precondition: the unit is in the queue"));
        battle.currentMove = signal;
        battle.beforeMove();
        battle.afterMove();
        battle.processRequests();
    }
}
''')
print("ok   judge written")
