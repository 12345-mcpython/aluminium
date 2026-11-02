package com.laosun.aluminium.test.content.characters.aventurinewaveflair;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * 1513 砂金•戏浪: the four clauses this round could write, measured off the panel.
 *
 * <p>Its {@code _gaps} was a pointer to the ledger, and the ledger said 「未逐句核」 — so nothing pinned these numbers. What
 * stays uncompiled is registered by the mechanism it needs: threshold crossings (星魂 1/2), a cast counter plus a permanent
 * replacement (星魂 6), the two branches of 行迹 纵享惊涛, and the elation skill's 「10 × 18%」 segment (an engine/data gap).
 */
public class AventurineWaveflairClauseCoverageTest {

    private static final int WAVEFLAIR = 1513;
    private static final int ALLY = 1002;

    /**
     * 星魂 1 (first half): 「全属性抗性穿透提高 24%」.
     */
    @Test
    public void eidolon1RaisesAllTypeResistancePenetration() {
        Assertions.assertEquals(0.0, attributeAt(0, AttributeType.DAMAGE_PENETRATION, 0), 1e-9, "control: nothing at E0");
        Assertions.assertEquals(0.24, attributeAt(1, AttributeType.DAMAGE_PENETRATION, 0), 1e-9,
                "「全属性抗性穿透提高 24%」");
    }

    /**
     * 星魂 6 (first half): 「造成的欢愉伤害增笑 25%」 — the amp attribute, not the elation-boost one.
     */
    @Test
    public void eidolon6RaisesTheElationDamageAmp() {
        Assertions.assertEquals(0.0, attributeAt(0, AttributeType.ELATION_DAMAGE_AMP, 0), 1e-9, "control: nothing at E0");
        Assertions.assertEquals(0.25, attributeAt(6, AttributeType.ELATION_DAMAGE_AMP, 0), 1e-9,
                "「欢愉伤害增笑 25%」");
    }

    /**
     * 星魂 4: 「施放战技时，使我方全体造成的伤害无视敌方目标 18% 的防御力，持续 3 回合」.
     *
     * <p>Measured on a TEAMMATE, because the sentence says 「我方全体」: the ally's {@code DEFENCE_IGNORE} goes 0 → 0.18 after her
     * Skill, and nothing moves at E3.
     */
    @Test
    public void eidolon4GivesTheWholeSideDefenceIgnoreAfterHerSkill() {
        Fixture e4 = new Fixture(4);
        Assertions.assertEquals(0.0, e4.ally.getAttribute(AttributeType.DEFENCE_IGNORE).get(), 1e-9,
                "precondition: nothing before the Skill");
        e4.castSkill();
        Assertions.assertEquals(0.18, e4.ally.getAttribute(AttributeType.DEFENCE_IGNORE).get(), 1e-9,
                "「我方全体…无视敌方目标 18% 的防御力」 — the ally carries it too");

        Fixture e3 = new Fixture(3);
        e3.castSkill();
        Assertions.assertEquals(0.0, e3.ally.getAttribute(AttributeType.DEFENCE_IGNORE).get(), 1e-9,
                "control: it is Eidolon 4");
    }

    /**
     * 行迹 极乐派对: 「速度 ≥140 时，欢愉度提高 30%，之后每超过 1 点速度额外提高 1%，最多计入 200 点」.
     *
     * <p>Measured at three speeds: 130 ⇒ <b>0</b> (the condition is false), 150 ⇒ <b>0.4</b> (0.3 + 10 × 0.01), and 400 ⇒
     * <b>2.3</b> (0.3 + the 200-point cap of 2.0).
     */
    @Test
    public void theJoyfulPartyTraceScalesElationWithExtraSpeed() {
        double below = elationAt(130);
        Assertions.assertEquals(below, elationAt(139), 1e-9, "below 140 the clause does not apply");
        Assertions.assertEquals(below + 0.4, elationAt(150), 1e-9,
                "「之后每超过 1 点速度…额外提高 1%」 — 10 points over");
        Assertions.assertEquals(below + 2.3, elationAt(400), 1e-9,
                "「最多计入 200 点超出的速度」 — the cap is 2.0");
    }

    // ==================================================================

    private static double attributeAt(int eidolon, AttributeType type, int speedBonus) {
        Character owner = CharacterFactory.create(WAVEFLAIR, 80, false, null, null, eidolon);
        if (speedBonus != 0) {
            owner.getAttribute(AttributeType.SPEED).addBase(speedBonus);
        }
        Enemy enemy = BattleFixture.enemy(BattleFixture.MONSTER_ID, BattleFixture.MONSTER_LEVEL);
        Battle battle = new Battle(List.of(owner), List.of(enemy), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        return owner.getAttribute(type).get();
    }

    /** Elation boost at a target speed: the base panel is raised to it before the battle starts. */
    private static double elationAt(int speed) {
        Character owner = CharacterFactory.create(WAVEFLAIR, 80);
        owner.getAttribute(AttributeType.SPEED).addBase(speed - owner.getAttribute(AttributeType.SPEED).get());
        Enemy enemy = BattleFixture.enemy(BattleFixture.MONSTER_ID, BattleFixture.MONSTER_LEVEL);
        Battle battle = new Battle(List.of(owner), List.of(enemy), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        return owner.getAttribute(AttributeType.ELATION_DAMAGE_BOOST).get();
    }

    private static final class Fixture {
        private final Character owner;
        private final Character ally = CharacterFactory.create(ALLY, 80);
        private final Enemy enemy = BattleFixture.enemy(BattleFixture.MONSTER_ID, BattleFixture.MONSTER_LEVEL);
        private final Battle battle;

        private Fixture(int eidolon) {
            owner = CharacterFactory.create(WAVEFLAIR, 80, false, null, null, eidolon);
            battle = new Battle(List.of(owner, ally), List.of(enemy), BattleFixture.seeded());
            battle.startBattle();
            battle.processRequests();
        }

        private void castSkill() {
            battle.castImmediate(owner.getSkills().get(SkillType.SKILL), owner, List.of(enemy));
            battle.processRequests();
        }
    }
}

