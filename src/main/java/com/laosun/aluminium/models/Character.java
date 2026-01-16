package com.laosun.aluminium.models;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.Constant;
import com.laosun.aluminium.beans.CharacterData;
import com.laosun.aluminium.beans.Translate;
import com.laosun.aluminium.enums.Camp;
import com.laosun.aluminium.enums.SkillAttackType;
import com.laosun.aluminium.enums.SkillEffectType;
import com.laosun.aluminium.exceptions.CharacterException;
import com.laosun.aluminium.utils.LevelPromotionCalc;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@ToString(callSuper = true)
public class Character extends CanHit {
    private double energyRegenerationRate = 1;
    private double maxEnergy;
    private EnergyProcessor energyProcessor;
    private RelicSuit relicSuit;
    private Weapon weapon;
    private static Skill tempSkill = new Skill(0, "Test", "Test", null, null, null, null) {
        @Override
        public boolean performSkill(CanHit performer, List<CanHit> accepter) {
            return true;
        }
    };
    private CharacterSkills characterSkills = new CharacterSkills(tempSkill, tempSkill, tempSkill, tempSkill, tempSkill, tempSkill);


    public Character(String name, Camp camp, double health, double defence, double attack, double speed) {
        super(name, camp, health, defence, attack, speed);
        setEnergyProcessor(new DefaultEnergyProcessor(0, getMaxEnergy()));
    }

    public Character(Translate name, Camp camp, double health, double defence, double attack, double speed) {
        super(name.english(), camp, health, defence, attack, speed);
    }

    public static Character build(int cid, int level, boolean isPromote, RelicSuit suit, Weapon weapon, CharacterSkills characterSkills) {
        return build(cid, level, isPromote, suit, weapon, characterSkills, new ExtraBasicPromote());
    }

    public static Character build(int cid, int level, boolean isPromote, RelicSuit suit, Weapon weapon, CharacterSkills characterSkills, ExtraBasicPromote extraBasicPromote) {
        CharacterData characterData = Constant.CHARACTERS.get(cid);
        if (characterData == null) {
            throw new CharacterException.CharacterNotFoundException(String.format("Character '%s' not found", cid));
        }
        double rate = LevelPromotionCalc.calcCharacterRate(level, isPromote);
        double baseHealth = characterData.health() * rate + weapon.getHealth();
        double baseDefence = characterData.defence() * rate + weapon.getDefence();
        double baseAttack = characterData.attack() * rate + weapon.getAttack();
        double baseSpeed = characterData.speed();
        IO.println(rate);
        Map<String, Double> relicValue = suit.calcTotalValue();
        baseHealth = baseHealth * (1 + relicValue.get("health_percent") + extraBasicPromote.healthPercent()) + relicValue.get("health") + extraBasicPromote.health();
        baseDefence = baseDefence * (1 + relicValue.get("defence_percent") + extraBasicPromote.defencePercent()) + relicValue.get("defence") + extraBasicPromote.defence();
        baseAttack = baseAttack * (1 + relicValue.get("attack_percent") + extraBasicPromote.attackPercent()) + relicValue.get("attack") + extraBasicPromote.attack();
        baseSpeed = baseSpeed * (1 + extraBasicPromote.speedPercent()) + relicValue.get("speed") + extraBasicPromote.speed();

        Character a = new Character(characterData.name(), Camp.PLAYER, baseHealth, baseDefence, baseAttack, baseSpeed);
        a.relicSuit = suit;
        a.maxEnergy = characterData.maxEnergy();
        a.characterSkills = characterSkills;
        a.weapon = weapon;
        return a;
    }

    // TODO: write it!
    public static class DefaultEnergyProcessor extends EnergyProcessor {
        public DefaultEnergyProcessor(double energy, double maxEnergy) {
            super(energy, maxEnergy);
        }
    }
}