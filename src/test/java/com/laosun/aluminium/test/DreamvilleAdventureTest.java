package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillCategory;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.utils.CharacterFactory;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * Light cone 21036: whichever of basic / skill / ultimate the wearer cast LAST decides which category the whole party's damage
 * is lifted by 12%, and only that one is ever up.
 *
 * <p>"Only the latest" is built from the engine's own removal rule: a stat modifier is matched BY ATTRIBUTE
 * ({@code BuffManager.isNamed} answers a StatModifierBuff by its attribute, and a StateBuff by its name), so each rule removes the
 * other two attributes' modifiers with REMOVE_STACK. No new capability was needed -- the pieces were already there.
 */
public class DreamvilleAdventureTest {
    private static final int CONE = 21036;
    private static final int WEARER = 1205;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    private Character wearer;
    private Character ally;
    private Enemy enemy;
    private Battle battle;

    private void build(boolean withCone) {
        wearer = withCone
                ? CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, 1))
                : CharacterFactory.create(WEARER, LEVEL);
        ally = CharacterFactory.create(ALLY, LEVEL);
        enemy = EnemyFactory.create(MONSTER, 90, 1);
        battle = new Battle(List.of(wearer, ally), List.of(enemy), new Random(0));
        battle.startBattle();
    }

    private void cast(SkillCategory category) {
        battle.fireTriggers(TriggerEvent.CAST_SETUP, wearer, (com.laosun.aluminium.models.CanHit) enemy, 0, 0,
                category);
    }

    private String boosts() {
        return "basic=" + ally.getAttribute(AttributeType.BASIC_ATTACK_DAMAGE_BOOST).get()
                + " skill=" + ally.getAttribute(AttributeType.SKILL_DAMAGE_BOOST).get()
                + " ult=" + ally.getAttribute(AttributeType.ULTIMATE_DAMAGE_BOOST).get();
    }

    @Test
    public void onlyTheLatestCategoryIsUp() {
        build(true);
        cast(SkillCategory.NORMAL);
        String afterBasic = boosts();
        cast(SkillCategory.BPSKILL);
        String afterSkill = boosts();
        cast(SkillCategory.ULTRA);
        String afterUlt = boosts();
        System.out.println("[21036] party boosts after basic: " + afterBasic + " ; after skill: " + afterSkill
                + " ; after ultimate: " + afterUlt);
        Assertions.assertEquals("basic=0.12 skill=0.0 ult=0.0", afterBasic, "a basic attack lifts basics only");
        Assertions.assertEquals("basic=0.0 skill=0.12 ult=0.0", afterSkill, "a skill REPLACES it");
        Assertions.assertEquals("basic=0.0 skill=0.0 ult=0.12", afterUlt, "and an ultimate replaces that");
    }

    @Test
    public void withoutTheConeNothingIsUp() {
        build(false);
        cast(SkillCategory.BPSKILL);
        System.out.println("[21036] without the cone: " + boosts());
        Assertions.assertEquals("basic=0.0 skill=0.0 ult=0.0", boosts(), "no cone, no 童心 (false case)");
    }

    @Test
    public void anAllyCastingDoesNotMatter() {
        build(true);
        battle.fireTriggers(TriggerEvent.CAST_SETUP, (com.laosun.aluminium.models.CanHit) ally,
                (com.laosun.aluminium.models.CanHit) enemy, 0, 0, SkillCategory.BPSKILL);
        System.out.println("[21036] after an ALLY casts: " + boosts());
        Assertions.assertEquals("basic=0.0 skill=0.0 ult=0.0", boosts(), "actor == self (false case)");
    }
}
