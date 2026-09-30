package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1406 Cipher, from her own file (2026-09-29, round 207): the Patron reaction (with its per-turn limiter) and the Skill's own attack share.
 */
public class CipherTest {
    private static final int CIPHER = 1406;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /** \u26a0 The reaction needs the Patron mark, and its 150% is compared with a hand-built 300% reference in the same pipeline. */
    @Test
    public void theReactionNeedsThePatronAndDealsItsShare() {
        double unmarked = reactionLoss(true, false);
        double marked = reactionLoss(true, true);
        double reference = reactionLoss(false, true);

        Assertions.assertEquals(0.0, unmarked, 1e-9,
                "\u300c\u3010\u8001\u4e3b\u987e\u3011\u53d7\u5230\u6211\u65b9\u5176\u4ed6\u76ee\u6807\u653b\u51fb\u540e\u300d -- without the mark, nothing");
        Assertions.assertTrue(marked > 0, "with the mark, the follow-up lands");
        Assertions.assertEquals(0.5, marked / reference, 0.05,
                "150% against a hand-built 300% reference: " + marked + " vs " + reference);
    }

    /** \u26a0 「使赛飞儿的攻击力提高30%」 -- a share of her own BASE attack. */
    @Test
    public void theSkillRaisesHerOwnAttack() {
        Character cipher = CharacterFactory.create(CIPHER, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(cipher, ally), List.of(enemy), fixed());
        battle.startBattle();
        double before = cipher.getAttribute(AttributeType.ATTACK).get();
        double base = cipher.getAttribute(AttributeType.ATTACK).baseValue();

        battle.castImmediate(cipher.getSkills().get(com.laosun.aluminium.enums.SkillType.SKILL), cipher, List.of(enemy));

        Assertions.assertEquals(base * 0.3, cipher.getAttribute(AttributeType.ATTACK).get() - before, base * 0.3 * 0.02,
                "\u300c\u4f7f\u8d5b\u98de\u513f\u7684\u653b\u51fb\u529b\u63d0\u9ad830%\u300d of the BASE: base " + base);
    }

    /** Fires a teammate's attack at the enemy; optionally marks it first, optionally uses a hand-built 300% reference rule. */
    private static double reactionLoss(boolean shipped, boolean mark) {
        Character cipher = CharacterFactory.create(CIPHER, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        if (!shipped) {
            EffectSpec effect = new EffectSpec();
            TriggerSpecs.set(effect, "op", "DAMAGE");
            TriggerSpecs.set(effect, "scale", "self_attr:ATTACK");
            TriggerSpecs.set(effect, "percent", 3.0);
            TriggerSpecs.set(effect, "element", "Quantum");
            TriggerSpecs.set(effect, "target", "target");
            EffectSpec markEffect = new EffectSpec();
            TriggerSpecs.set(markEffect, "op", "APPLY_BUFF");
            TriggerSpecs.set(markEffect, "buff", "\u8001\u4e3b\u987e");
            TriggerSpecs.set(markEffect, "permanent", true);
            TriggerSpecs.set(markEffect, "target", "target");
            cipher.setTriggerTable(new TriggerTable(CIPHER, List.of(
                    TriggerSpecs.rule(TriggerEvent.SKILL_CAST.name(), List.of("actor == self"), markEffect),
                    TriggerSpecs.rule(TriggerEvent.ALLY_ATTACK.name(),
                            List.of("actor is_other_ally", "target has_state \u8001\u4e3b\u987e"), effect))));
        }
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(cipher, ally), List.of(enemy), fixed());
        battle.startBattle();
        if (mark) {
            // Applied directly in BOTH paths: casting the Skill would also raise her ATTACK by 30% (its own rule), and the reference cannot
            // reproduce that without reproducing the whole rule. One difference between the paths is the point.
            enemy.getBuffManager().addBuff(new com.laosun.aluminium.models.buff.StateBuff("\u8001\u4e3b\u987e", 1, true));
        }
        double before = enemy.getCurrentHp();
        battle.fireTriggers(TriggerEvent.ALLY_ATTACK, ally, enemy, 0, 0);
        return before - enemy.getCurrentHp();
    }

    /**
     * ⚠ Returns 1.0, i.e. NEVER crits (2026-09-29, round 242). It used to return 0.0, which forced every hit to crit -- and that silently
     * coupled this test to 1406's 追加攻击 crit-damage clause: the shipped path carries it, the hand-built reference (which REPLACES her
     * trigger table) does not, so the 150%/300% comparison drifted from 0.5 to 0.833 the moment that clause shipped. The test's subject is
     * the BASE SHARE, so measuring it without crits is both the minimal fix and the more honest reading.
     */
    private static Random fixed() {
        return new Random() {
            @Override
            public double nextDouble() {
                return 1.0;
            }
        };
    }
}
