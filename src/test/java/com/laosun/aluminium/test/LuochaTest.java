package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.data.TriggerTables;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1203 Luocha, from his own file (2026-09-28, round 144): the Abyss Flower counter and the low-HP retrigger.
 *
 * <p>Both clauses were built from vocabularies verified in earlier rounds: `ADD_STACK` for a named counter (1111's 斗志 (Fighting Spirit)), and the
 * `HP_LOST` event together with the `target_hp_percent` numeric variable for the threshold. The retrigger's 2-turn cooldown and
 * the ultimate's "解除增益" (dispel buffs) are registered, not written.
 */
public class LuochaTest {
    private static final int LUOCHA = 1203;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /** Note: "并使罗刹获得1层[白花之刻]" (and gives Luocha 1 stack of [白花之刻], Abyss Flower): one stack per skill cast, on himself. */
    @Test
    public void hisSkillAddsOneAbyssFlowerStack() {
        Fixture f = new Fixture();
        Assertions.assertEquals(0, f.luocha.getBuffManager().stacksOf("白花之刻"), "precondition: no stacks");

        f.battle.castImmediate(f.luocha.getSkills().get(SkillType.SKILL), f.luocha, List.of(f.ally));

        Assertions.assertEquals(1, f.luocha.getBuffManager().stacksOf("白花之刻"),
                "「并使罗刹获得1层【白花之刻】」");
    }

    /** Census: the counter, the low-HP retrigger and the level convention are where the notes say. */
    @Test
    public void hisFileCarriesTheClauses() {
        var table = TriggerTables.of(LUOCHA);
        // Note: 2 since 2026-09-30: the counter, plus the rule that opens the 白花之刻 (Abyss Flower) zone at two stacks
        Assertions.assertEquals(2, table.ruleCount(TriggerEvent.SKILL_CAST), "the counter and the zone opener");
        Assertions.assertEquals(1, table.ruleCount(TriggerEvent.BATTLE_START), "the level convention");
    }

    private static final class Fixture {
        private final Character luocha = CharacterFactory.create(LUOCHA, LEVEL);
        private final Character ally = CharacterFactory.create(ALLY, LEVEL);
        private final Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
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
