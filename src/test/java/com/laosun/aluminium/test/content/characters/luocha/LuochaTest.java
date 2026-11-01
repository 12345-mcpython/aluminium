package com.laosun.aluminium.test.content.characters.luocha;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.data.TriggerTables;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1203 Luocha, from his own file: the Abyss Flower counter, the low-HP retrigger, and the clauses that used to be
 * registered as missing.
 *
 * <p>Every clause is built from vocabulary that already existed: {@code ADD_STACK} for the named counter (1111's
 * Fighting Will (斗志)), the {@code HP_LOST} event with the {@code target_hp_percent} numeric variable for the
 * threshold crossing, {@code REMOVE_BUFF} for the ultimate's 「解除敌方全体1个增益效果」, and {@code RESIST_DEBUFF}
 * for 行过幽谷's control resistance -- the same sentence 1013, 1107 and 1109 carry.
 *
 * <p>⚠ Still NOT covered, and deliberately so: the Field's (结界) 2-turn duration and its removal when Luocha is
 * knocked down. A 结界 has no lifetime in this engine yet, so it is recorded rather than faked -- see ROADMAP.
 */
public class LuochaTest {
    private static final int LUOCHA = 1203;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;

    /**
     * Note: "并使罗刹获得1层[白花之刻]" (and gives Luocha 1 stack of [白花之刻], Abyss Flower): one stack per skill cast, on himself.
     */
    @Test
    public void hisSkillAddsOneAbyssFlowerStack() {
        Fixture f = new Fixture();
        Assertions.assertEquals(0, f.luocha.getBuffManager().stacksOf("白花之刻"), "precondition: no stacks");

        f.battle.castImmediate(f.luocha.getSkills().get(SkillType.SKILL), f.luocha, List.of(f.ally));

        Assertions.assertEquals(1, f.luocha.getBuffManager().stacksOf("白花之刻"),
                "「并使罗刹获得1层【白花之刻】」 (and Luocha (罗刹) gains 1 stack of Abyss Flower (【白花之刻】))");
    }

    /**
     * Census: which event each clause hangs on, so a rule cannot silently move or vanish.
     */
    @Test
    public void hisFileCarriesTheClauses() {
        var table = TriggerTables.of(LUOCHA);
        // The counter, the zone opener that consumes it, and 行迹 浸池苏生's dispel.
        // 星魂 2's two halves moved OFF this event on 2026-10-31: the heal bonus registers once at BATTLE_START (the
        // mechanism's own contract) and the shield belongs on CAST_SETUP, because SKILL_CAST fires AFTER the heal and the
        // target is already back above 50% by then (its >= 50% gate would always hold).
        Assertions.assertEquals(4, table.ruleCount(TriggerEvent.SKILL_CAST),
                "the counter, the zone opener, the dispel and Eidolon 1 aura while the zone is up");
        // The level convention, 行迹 行过幽谷's control resistance, 秘技 愚者的悲悯's field, 星魂 5, and 星魂 2's heal bonus.
        Assertions.assertEquals(6, table.ruleCount(TriggerEvent.BATTLE_START),
                "the level convention, the control resistance, the technique, Eidolons 2/3/5 levels and the heal bonus");
        // 星魂 2's shield half: decided before settlement, when the target's HP is still the pre-heal value.
        Assertions.assertEquals(1, table.ruleCount(TriggerEvent.CAST_SETUP), "Eidolon 2's shield");
        // 星魂 6's resistance reduction and the ultimate's own dispel + stack.
        Assertions.assertEquals(2, table.ruleCount(TriggerEvent.ULT_CAST),
                "Eidolon 6's resistance reduction and the ultimate's dispel");
        // The talent's heal to the attacker and 行迹 浇灌尘身's heal to the others share one trigger.
        Assertions.assertEquals(2, table.ruleCount(TriggerEvent.ALLY_ATTACK),
                "the talent's heal to the attacker and the trace's heal to the others");
        Assertions.assertEquals(1, table.ruleCount(TriggerEvent.HP_LOST), "the low-HP retrigger");
        // 「当罗刹陷入无法战斗状态时，结界也会被解除」 -- the one clause addressed to the unit leaving the fight.
        Assertions.assertEquals(1, table.ruleCount(TriggerEvent.DEATH), "the field's death clause");
    }

    private static final class Fixture {
        private final Character luocha = CharacterFactory.create(LUOCHA, LEVEL);
        private final Character ally = CharacterFactory.create(ALLY, LEVEL);
        private final Enemy enemy = BattleFixture.monster();
        private final Battle battle = new Battle(List.of(luocha, ally), List.of(enemy), new Random() {
            @Override
            public double nextDouble() {
                return 0.0;
            }
        });

        private Fixture() {
            battle.startBattle();
        }
    }
}


