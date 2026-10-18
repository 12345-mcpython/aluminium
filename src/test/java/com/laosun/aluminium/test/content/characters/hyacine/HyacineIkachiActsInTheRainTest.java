package com.laosun.aluminium.test.content.characters.hyacine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.Summon;
import com.laosun.aluminium.models.skill.SkillExecutor;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/** 1409 忆灵技2: 雨过天晴下，风堇施放技能后小伊卡立即获得额外回合并自动施放忆灵技１. */
public class HyacineIkachiActsInTheRainTest {

    @Test
    public void itActsAndCastsItsOwnSkillOneRightAfterHerSkill() {
        Character hyacine = CharacterFactory.create(1409, 80);
        Battle battle = new Battle(List.of(hyacine, CharacterFactory.create(1001, 80)),
                List.of(BattleFixture.dummy()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        SkillExecutor.execute(battle, hyacine.getSkills().get(SkillType.ULTRA), hyacine,
                List.of(battle.enemies.getFirst()));
        battle.processRequests();
        Summon ika = (Summon) battle.memospriteOf(hyacine);
        Assertions.assertNotNull(ika, "precondition: 小伊卡 is out");

        List<Damage> settled = new ArrayList<>();
        battle.addDamageObserver((damage, applied, hpLoss, shieldAbsorbed, died) -> settled.add(damage));
        SkillExecutor.execute(battle, hyacine.getSkills().get(SkillType.SKILL), hyacine,
                List.of(battle.enemies.getFirst()));
        battle.processRequests();

        Assertions.assertTrue(settled.stream().anyMatch(d -> d.getAttacker() == ika),
                "「并自动施放【乌云乌云快走开！】」: an instance from the memosprite settled (instances="
                        + settled.size() + ")");
    }
}
