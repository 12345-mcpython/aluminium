package com.laosun.aluminium.test.content.lightcones;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.Summon;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.models.skill.Skill;
import com.laosun.aluminium.models.skill.SkillExecutor;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Light cone 23042's vulnerability clause, measured: 「装备者的忆灵施放忆灵技时，使敌方全体受到的伤害提高 #4%，
 * 持续 #5 回合」 - at superimposition 5 that is +36% for 2 turns.
 *
 * <h2>What makes this test meaningful</h2>
 * It fails unless the memosprite really casts through the engine (a data-driven memosprite keeps its skills in
 * {@code Summon.skillsByDataSlot}) AND that cast raises {@code SUMMON_ATTACK}. Before the engine fix, that event was
 * only raised from {@code EnemySkill.execute}, so a clause of the form "when the wearer's memosprite uses its skill"
 * was dead: the rule fired never, and the number stayed at 1.0x.
 */
public class Cone23042TriggerTest {

    private static final int ROBIN = 1512;
    private static final int ALLY = 1001;
    private static final int CONE = 23042;
    private static final int SUPERIMPOSITION = 5;
    private static final double EXPECTED = 0.36;

    @Test
    public void theMemospriteSkillRaisesDamageTakenByThirtySixPercent() {
        Character robin = CharacterFactory.create(ROBIN, 80, true, Weapon.build(CONE, 80, false, SUPERIMPOSITION));
        Character ally = CharacterFactory.create(ALLY, 80);
        Battle battle = new Battle(List.of(robin, ally), List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        battle.summonMemosprite(robin);
        battle.processRequests();
        Summon memo = (Summon) battle.memospriteOf(robin);
        Assertions.assertNotNull(memo, "precondition: the memosprite is on the field");
        Skill memoSkill = memo.skillAt(1);
        Assertions.assertNotNull(memoSkill, "precondition: her memosprite has a skill in data slot 1");

        double before = allyDamage(battle, ally);
        SkillExecutor.execute(battle, memoSkill, memo, List.of(battle.enemies.get(0)));
        battle.processRequests();
        double after = allyDamage(battle, ally);

        Assertions.assertTrue(before > 0 && after > 0, "precondition: both ally hits land");
        Assertions.assertEquals(1.0 + EXPECTED, after / before, (1.0 + EXPECTED) * 1e-6,
                "the memosprite's skill raises the damage the ALLY deals by the cone's 36% "
                        + "(before=" + before + ", after=" + after + ")");
    }

    /** One basic attack from the ally, measured as the enemy's HP loss. */
    private static double allyDamage(Battle battle, Character ally) {
        List<Damage> settled = new ArrayList<>();
        battle.addDamageObserver((damage, applied, hpLoss, shieldAbsorbed, died) -> settled.add(damage));
        double before = battle.enemies.get(0).getCurrentHp();
        SkillExecutor.execute(battle, ally.getSkills().get(SkillType.COMMON), ally,
                List.of(battle.enemies.get(0)));
        battle.processRequests();
        return before - battle.enemies.get(0).getCurrentHp();
    }
}
