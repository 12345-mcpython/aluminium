package com.laosun.aluminium.models;

import com.laosun.aluminium.Constant;
import com.laosun.aluminium.beans.CharacterData;
import com.laosun.aluminium.beans.Translate;
import com.laosun.aluminium.enums.Camp;
import com.laosun.aluminium.exceptions.CharacterException;
import com.laosun.aluminium.utils.LevelPromotionCalc;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString(callSuper = true)
public class Character extends CanHit {
    private double maxEnergy;
    private RelicSuit relicSuit;
    private Weapon weapon;

    private Character(Translate name, double health, double defence, double attack, double speed) {
        super(name.english(), Camp.PLAYER, health, defence, attack, speed);
    }

    public static class Builder {
        private int cid;
        private Translate name;
        private double health;
        private double defence;
        private double attack;
        private int level;
        private double maxEnergy;
        private RelicSuit relicSuit;
        private Weapon weapon;
        private boolean isPromote;
        private ExtraBasicPromote extraBasicPromote;

        public Builder promote() {
            this.isPromote = true;
            return this;
        }

        public Builder cid(int cid) {
            this.cid = cid;
            return this;
        }

        public Builder name(Translate name) {
            this.name = name;
            return this;
        }

        public Builder health(double health) {
            this.health = health;
            return this;
        }

        public Builder defence(double defence) {
            this.defence = defence;
            return this;
        }

        public Builder attack(double attack) {
            this.attack = attack;
            return this;
        }

        public Builder basicData(double health, double defence, double attack) {
            this.health = health;
            this.defence = defence;
            this.attack = attack;
            return this;
        }

        public Builder extraValue(ExtraBasicPromote extraBasicPromote) {
            this.extraBasicPromote = extraBasicPromote;
            return this;
        }

        public Builder relicSuit(RelicSuit relicSuit) {
            this.relicSuit = relicSuit;
            return this;
        }

        public Builder weapon(Weapon weapon) {
            this.weapon = weapon;
            return this;
        }

        public Builder level(int level) {
            this.level = level;
            return this;
        }

        public Character build() {
            CharacterData characterData = Constant.CHARACTERS.get(cid);
            if (characterData == null) {
                throw new CharacterException.CharacterNotFoundException(String.format("Character '%s' not found", cid));
            }
            double rate = LevelPromotionCalc.calcCharacterRate(level, isPromote);
            double baseHealth = characterData.health() * rate + weapon.getHealth();
            double baseDefence = characterData.defence() * rate + weapon.getDefence();
            double baseAttack = characterData.attack() * rate + weapon.getAttack();
            double baseSpeed = characterData.speed();
            Object2DoubleOpenHashMap<String> relicValue = new Object2DoubleOpenHashMap<>();
            relicSuit.calcTotalValue(relicValue);
            baseHealth = baseHealth * (1 + relicValue.getOrDefault("health_percent", 0.0) + extraBasicPromote.healthPercent()) + relicValue.getOrDefault("health", 0.0) + extraBasicPromote.health();
            baseDefence = baseDefence * (1 + relicValue.getOrDefault("defence_percent", 0.0) + extraBasicPromote.defencePercent()) + relicValue.getOrDefault("defence", 0.0) + extraBasicPromote.defence();
            baseAttack = baseAttack * (1 + relicValue.getOrDefault("attack_percent", 0.0) + extraBasicPromote.attackPercent()) + relicValue.getOrDefault("attack", 0.0) + extraBasicPromote.attack();
            baseSpeed = baseSpeed * (1 + extraBasicPromote.speedPercent()) + relicValue.getOrDefault("speed", 0.0) + extraBasicPromote.speed();

            Character a = new Character(characterData.name(), baseHealth, baseDefence, baseAttack, baseSpeed);
            a.relicSuit = relicSuit;
            a.weapon = weapon;
            return a;
        }
    }
}