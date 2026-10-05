"""Judge for the ultimate's Elation branch (item 58). Judge-only.

Three readings of one sentence (`data/skills.json` 8009/8010 slot 3):
  「获得 5 个笑点 … 若目标拥有欢愉技，目标额外获得 10 点【好活当赏】，并使其立即施放 1 次…欢愉技」
  (a) the party's 笑点 counter rises by five;
  (b) the TARGET's 【好活当赏】 rises by ten -- and 好活当赏 is per-holder, so this also proves the grant found the
      target's own copy rather than the caster's;
  (c) the commanded cast really happened: the ultimate itself deals no damage (its row's #1 is a crit-damage share), so
      any HP the enemy loses is the Elation skill's 8+1 instances. That is also the mutation's target.
"""
import io

JUDGE = "src/test/java/com/laosun/aluminium/test/UltElationBranchTest.java"

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
 * 若目标拥有欢愉技：目标额外获得 **10** 点【好活当赏】，并**使其立即施放 1 次**…欢愉技 (2026-10-02).
 *
 * <p>⭐ The caster is 8009 and the target is 1505: 1505 is one of the nine 欢愉技 holders (data slot 20) and its own file
 * declares 【好活当赏】, so every half of the sentence has somewhere to land.
 *
 * <p>⚠ The ultimate deals NO damage of its own -- its first parameter is the crit-damage share it grants -- so the enemy's HP
 * loss below is the commanded Elation cast and nothing else.
 */
public class UltElationBranchTest {
    private static final int CASTER = 8009;
    private static final int TARGET = 1505;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final String LAUGH = "\\u7b11\\u70b9";
    private static final String GIFT = "\\u597d\\u6d3b\\u5f53\\u8d4f";

    /** 「获得 5 个笑点」 -- onto the SHARED party counter. */
    @Test
    public void theUltimateGivesFiveLaughs() {
        Scene scene = fight();
        Assertions.assertEquals(0, scene.battle.partyResourceValue(LAUGH), "the battle starts with none");
        cast(scene);
        Assertions.assertEquals(5, scene.battle.partyResourceValue(LAUGH), "\\u300c\\u83b7\\u5f97 5 \\u4e2a\\u7b11\\u70b9\\u300d");
    }

    /** 「目标额外获得 10 点【好活当赏】」 -- on the TARGET, not the caster. */
    @Test
    public void theTargetGainsTenGift() {
        Scene scene = fight();
        Assertions.assertEquals(0, scene.target.getResources().value(GIFT), "the target starts with none");
        cast(scene);
        Assertions.assertEquals(10, scene.target.getResources().value(GIFT),
                "\\u300c\\u76ee\\u6807\\u989d\\u5916\\u83b7\\u5f97 10 \\u70b9\\u3010\\u597d\\u6d3b\\u5f53\\u8d4f\\u3011\\u300d");
        Assertions.assertEquals(0, scene.caster.getResources().value(GIFT), "而\\u4e0d\\u662f\\u65bd\\u653e\\u8005\\u81ea\\u5df1\\u7684");
    }

    /** 「并使其立即施放 1 次\\u2026\\u6b22\\u6109\\u6280\\u300d -- the commanded cast really lands on the enemy. */
    @Test
    public void theCommandedElationCastLands() {
        Scene scene = fight();
        double before = scene.enemy.getCurrentHp();
        cast(scene);
        double lost = before - scene.enemy.getCurrentHp();
        System.out.println("[8009] the commanded Elation cast took " + lost + " HP from the enemy");
        Assertions.assertTrue(lost > 0,
                "\\u82e5\\u76ee\\u6807\\u62e5\\u6709\\u6b22\\u6109\\u6280\\uff0c\\u4f7f\\u5176\\u7acb\\u5373\\u65bd\\u653e 1 \\u6b21\\u6b22\\u6109\\u6280");
    }

    // ==================================================================

    private static final class Scene {
        final Battle battle;
        final Character caster;
        final Character target;
        final com.laosun.aluminium.models.enemy.Enemy enemy;

        Scene(Battle battle, Character caster, Character target, com.laosun.aluminium.models.enemy.Enemy enemy) {
            this.battle = battle;
            this.caster = caster;
            this.target = target;
            this.enemy = enemy;
        }
    }

    private static Scene fight() {
        Character caster = CharacterFactory.create(CASTER, LEVEL, false, null, null, 0);
        Character target = CharacterFactory.create(TARGET, LEVEL, false, null, null, 0);
        var enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(caster, target), List.of(enemy), new Random(0));
        battle.startBattle();
        battle.processRequests();
        return new Scene(battle, caster, target, enemy);
    }

    private static void cast(Scene scene) {
        scene.battle.castImmediate(scene.caster.getSkills().get(SkillType.ULTRA), scene.caster,
                List.of(scene.target));
        scene.battle.processRequests();
    }
}
''')
print("ok   judge written")
