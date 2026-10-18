package com.laosun.aluminium.test.content.characters.robinsummeretto;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.skill.SkillExecutor;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * 1512 知更鸟•晴歌's memosprite aura: 「「晴空乐手」在场时，根据成员数量使敌方全体受到的伤害提高
 * 11.2% / 16.8% / 22.4%」 (11512 SkillP01). The document's wording is <b>cumulative</b>: one member 11.2%, two
 * 28% (0.112 + 0.168), three 50.4%. Two members plus light cone 23042 at superimposition 5 (36%) is the 64% a real
 * capture shows on every hit.
 *
 * <p>What this measures: with one memosprite on the field, an ally's damage is 11.2% higher than before it arrived.
 */
public class MemospriteAuraTest {

    private static final int ROBIN = 1512;
    private static final int ALLY = 1001;

    @Test
    public void oneMemospriteOnTheFieldRaisesDamageTakenByElevenPointTwoPercent() {
        Character robin = CharacterFactory.create(ROBIN, 80);
        Character ally = CharacterFactory.create(ALLY, 80);
        Battle battle = new Battle(List.of(robin, ally), List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();

        double before = allyHit(battle, ally);
        battle.summonMemosprite(robin);
        battle.processRequests();
        double after = allyHit(battle, ally);

        Assertions.assertTrue(before > 0 && after > 0, "precondition: both hits land");
        Assertions.assertEquals(1.112, after / before, 1.112 * 1e-6,
                "one 「晴空乐手」 on the field adds the document's 11.2% (before=" + before + ", after=" + after + ")");
    }

    private static double allyHit(Battle battle, Character ally) {
        double before = battle.enemies.get(0).getCurrentHp();
        SkillExecutor.execute(battle, ally.getSkills().get(SkillType.COMMON), ally,
                List.of(battle.enemies.get(0)));
        battle.processRequests();
        return before - battle.enemies.get(0).getCurrentHp();
    }
}
