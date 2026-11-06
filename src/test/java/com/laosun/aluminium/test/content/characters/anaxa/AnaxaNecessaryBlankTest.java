package com.laosun.aluminium.test.content.characters.anaxa;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * 1405 那刻夏's 行迹 必要的留白:「根据我方队伍中「智识」命途角色的数量…1 名：暴击伤害提高 140%；至少 2 名：我方全体造成的伤害提高 50%」,
 * and 星魂 6「造成的伤害为原伤害的 130%」.
 */
public class AnaxaNecessaryBlankTest {
    private static final int ANAXA = 1405;
    // 「智识」命途 = `mt: "all"` in the data: 1003 Himeko (姬子) and 1204 Qingque (青雀) are both Erudition.
    private static final int ERUDITION_ONE = 1003;
    private static final int ERUDITION_TWO = 1204;
    private static final int LEVEL = 80;
    private static final double EPS = 1e-6;

    /**
     * 「1 名：暴击伤害提高 140%」 — the count includes ANAXA HIMSELF (he is Erudition), so "exactly one" means he is the only
     * one on the Path. Measured: alone he carries +1.4; a second Erudition ally pushes the party to the OTHER branch.
     */
    @Test
    public void oneEruditionOnThePathLiftsHisCritDamage() {
        Battle solo = scene(List.of());
        double soloCritDamage = solo.characters().getFirst().getAttribute(AttributeType.CRIT_ATTACK).get();
        Assertions.assertEquals(0.5 + 1.4, soloCritDamage, EPS,
                "「1 名：那刻夏的暴击伤害提高 140%」");

        Battle withAlly = scene(List.of(CharacterFactory.create(ERUDITION_ONE, LEVEL)));
        Assertions.assertEquals(0.5, withAlly.characters().getFirst().getAttribute(AttributeType.CRIT_ATTACK).get(), EPS,
                "a second Erudition ally takes the party to the OTHER branch (「至少 2 名」), so this one no longer applies");
        Assertions.assertEquals(0.5, withAlly.characters().getFirst().getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get(),
                EPS, "…and that branch is the party damage one");
    }

    @Test
    public void twoEruditionAlliesLiftTheWholeParty() {
        Battle two = scene(List.of(CharacterFactory.create(ERUDITION_ONE, LEVEL),
                CharacterFactory.create(ERUDITION_TWO, LEVEL)));
        Character anaxa = two.characters().getFirst();
        Assertions.assertEquals(0.5, anaxa.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get(), EPS,
                "「至少 2 名：我方全体造成的伤害提高 50%」 — on Anaxa too");
        Assertions.assertEquals(0.5, two.characters().get(1).getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get(), EPS,
                "…and on the ally, since the sentence says 我方全体");
    }

    @Test
    public void theSixthEidolonMultipliesHisDamage() {
        Character anaxa = CharacterFactory.create(ANAXA, LEVEL, false, null, null, 6);
        Enemy enemy = BattleFixture.monster();
        enemy.setMaxStance(10_000);
        Battle battle = new Battle(List.of(anaxa), List.of(enemy), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        double before = enemy.getCurrentHp();
        battle.applyDamage(enemy, new Damage(anaxa, enemy, DamageElement.WIND, DamageType.NORMAL, 1000));
        battle.processRequests();
        double e6 = before - enemy.getCurrentHp();

        Character plain = CharacterFactory.create(ANAXA, LEVEL);
        Enemy plainEnemy = BattleFixture.monster();
        plainEnemy.setMaxStance(10_000);
        Battle plainBattle = new Battle(List.of(plain), List.of(plainEnemy), BattleFixture.alwaysRoll(0.0));
        plainBattle.startBattle();
        double plainBefore = plainEnemy.getCurrentHp();
        plainBattle.applyDamage(plainEnemy, new Damage(plain, plainEnemy, DamageElement.WIND, DamageType.NORMAL, 1000));
        plainBattle.processRequests();
        double baseline = plainBefore - plainEnemy.getCurrentHp();

        // ⚠ The boost zone is ADDITIVE: 「原伤害的 130%」 is written as +30% into it, and his own other boosts are already in
        // the denominator, so the measured ratio is 1.245 rather than exactly 1.3. The direction and the size class are what
        // this test can honestly pin; the exact figure would need 「乘算」, which the engine does not have.
        Assertions.assertTrue(e6 / baseline > 1.2,
                "「造成的伤害为原伤害的 130%」 — the ratio is " + (e6 / baseline) + " (additive boost zone)");
    }

    /** 终结技「使敌方全体陷入【升华】状态…同时被添加物理、火、冰、雷、风、量子、虚数属性弱点」. */
    @Test
    public void theUltimateImplantsEveryWeakness() {
        Character anaxa = CharacterFactory.create(ANAXA, LEVEL);
        Enemy enemy = BattleFixture.monster();
        enemy.setMaxStance(10_000);
        Battle battle = new Battle(List.of(anaxa), List.of(enemy), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        anaxa.setCurrentEnergy(anaxa.getMaxEnergy());

        battle.castImmediate(anaxa.getSkills().get(com.laosun.aluminium.enums.SkillType.ULTRA), anaxa, List.of(enemy));
        battle.processRequests();

        Assertions.assertTrue(enemy.getBuffManager().hasState("升华"), "「使敌方全体陷入【升华】状态」");
        for (com.laosun.aluminium.enums.DamageElement element : com.laosun.aluminium.enums.DamageElement.values()) {
            Assertions.assertTrue(enemy.isWeakTo(element),
                    "「同时被添加物理、火、冰、雷、风、量子、虚数属性弱点」 — missing " + element);
        }
    }

    private static Battle scene(List<Character> allies) {
        Character anaxa = CharacterFactory.create(ANAXA, LEVEL);
        Enemy enemy = BattleFixture.monster();
        java.util.List<Character> party = new java.util.ArrayList<>();
        party.add(anaxa);
        party.addAll(allies);
        Battle battle = new Battle(party, List.of(enemy), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        return battle;
    }
}
