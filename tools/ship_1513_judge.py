"""Regenerate the 1513 laughter judge only (discipline: one ship script writes one kind of file).

Fix: the first version looked her and her teammate up by a `getCharacterId()` that does not exist. The scene now hands
back the very objects it created, which removes the lookup entirely.
"""
import io

JUDGE = "src/test/java/com/laosun/aluminium/test/Character1513LaughterTest.java"

io.open(JUDGE, "w", encoding="utf-8", newline="").write('''package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1513：「获得 1／4／6 个笑点」—— 三句各自给的那一半 (2026-10-02).
 *
 * <p>⭐⭐ 笑点 is a PARTY-scoped, uncapped counter that ALREADY existed (declared by 1502 as `max: 2147483647`); these
 * readings are about HER grants, each the sentence's own number. ⚠ The counter is shared, so the sum test is the point --
 * that is what 「队伍级」 means, and `partyResourceValue` is the accessor `Character1502Test` already uses.
 */
public class Character1513LaughterTest {
    private static final int AVENTURINE = 1513;
    private static final int ALLY = 1002;
    private static final int MONSTER = 1002011;
    private static final String LAUGH = "\\u7b11\\u70b9";

    /** 「战技…获得 4 个笑点」 */
    @Test
    public void herSkillGivesFour() {
        Scene scene = fight();
        Assertions.assertEquals(0, scene.battle.partyResourceValue(LAUGH), "the battle starts with none");
        scene.battle.castImmediate(scene.her.getSkills().get(SkillType.SKILL), scene.her, List.of());
        Assertions.assertEquals(4, scene.battle.partyResourceValue(LAUGH), "\\u300c\\u83b7\\u5f97 4 \\u4e2a\\u7b11\\u70b9\\u300d");
    }

    /** 「终结技…获得 6 个笑点」 */
    @Test
    public void herUltimateGivesSix() {
        Scene scene = fight();
        scene.battle.castImmediate(scene.her.getSkills().get(SkillType.ULTRA), scene.her, List.of());
        Assertions.assertEquals(6, scene.battle.partyResourceValue(LAUGH), "\\u300c\\u83b7\\u5f97 6 \\u4e2a\\u7b11\\u70b9\\u300d");
    }

    /** 「队友施放攻击后…以及 1 个笑点」 -- a REAL teammate attack, not a hand-fired event. */
    @Test
    public void aTeammateAttackGivesOne() {
        Scene scene = fight();
        scene.battle.castImmediate(scene.mate.getSkills().get(SkillType.COMMON), scene.mate,
                List.of(scene.battle.enemies.getFirst()));
        Assertions.assertEquals(1, scene.battle.partyResourceValue(LAUGH), "\\u300c\\u4ee5\\u53ca 1 \\u4e2a\\u7b11\\u70b9\\u300d");
    }

    /** ⭐ THE SHARED COUNTER: all three in one battle sum, because 笑点 is party-scoped. */
    @Test
    public void theCounterIsSharedAcrossTheParty() {
        Scene scene = fight();
        scene.battle.castImmediate(scene.her.getSkills().get(SkillType.SKILL), scene.her, List.of());
        scene.battle.castImmediate(scene.her.getSkills().get(SkillType.ULTRA), scene.her, List.of());
        scene.battle.castImmediate(scene.mate.getSkills().get(SkillType.COMMON), scene.mate,
                List.of(scene.battle.enemies.getFirst()));
        Assertions.assertEquals(11, scene.battle.partyResourceValue(LAUGH), "4 + 6 + 1 on the SHARED counter");
    }

    // ==================================================================

    private static final class Scene {
        final Battle battle;
        final Character her;
        final Character mate;

        Scene(Battle battle, Character her, Character mate) {
            this.battle = battle;
            this.her = her;
            this.mate = mate;
        }
    }

    private static Scene fight() {
        Character her = CharacterFactory.create(AVENTURINE, 80, false, null, null, 0);
        Character mate = CharacterFactory.create(ALLY, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(her, mate),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        return new Scene(battle, her, mate);
    }
}
''')
print("ok   judge regenerated without any id lookup")
