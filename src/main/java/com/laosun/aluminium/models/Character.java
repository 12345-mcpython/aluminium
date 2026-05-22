package com.laosun.aluminium.models;

import com.laosun.aluminium.beans.CharacterData;
import com.laosun.aluminium.beans.Translate;
import com.laosun.aluminium.enums.Camp;
import com.laosun.aluminium.exceptions.CharacterException;
import com.laosun.aluminium.utils.CharacterDataProvider;
import com.laosun.aluminium.utils.ConstantCharacterDataProvider;
import com.laosun.aluminium.utils.LevelPromotionCalc;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString(callSuper = true)
public class Character extends CanHit {
    private RelicSuit relicSuit;
    private Weapon weapon;

    private Character(Translate name, double health, double defence, double attack, double speed) {
        super(name.english(), Camp.PLAYER, health, defence, attack, speed);
    }

    public static class Builder {
        private int cid;
        private int level = 1;
        private RelicSuit relicSuit = new RelicSuit();
        private Weapon weapon = new Weapon(new Translate("EMPTY", "EMPTY"), "", 0, 0, 0, null);
        private boolean isPromote = false;
        private ExtraBasicPromote extraBasicPromote = new ExtraBasicPromote();
        private CharacterDataProvider characterDataProvider = new ConstantCharacterDataProvider();

        public Builder isPromote() {
            this.isPromote = true;
            return this;
        }

        public Builder cid(int cid) {
            this.cid = cid;
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

        public Builder characterDataProvider(CharacterDataProvider characterDataProvider) {
            this.characterDataProvider = characterDataProvider;
            return this;
        }

        public Character build() {
            CharacterData characterData = validateAndGet(cid);
            double rate = LevelPromotionCalc.calcCharacterRate(level, isPromote);
            Calculator.CalcData calcData = new Calculator(characterData, weapon, relicSuit, extraBasicPromote).calculate(rate);
            Character character = new Character(characterData.name(), calcData.health, calcData.defence, calcData.attack, calcData.speed);
            character.relicSuit = relicSuit;
            character.weapon = weapon;
            return character;
        }

        private CharacterData validateAndGet(int cid) {
            if (cid == 0) {
                throw new CharacterException("cid can't be null or 0");
            }
            CharacterData cd = characterDataProvider.get(cid);
            if (cd == null) {
                throw new CharacterException.CharacterNotFoundException(String.format("Character '%s' not found", cid));
            }
            return cd;
        }
    }

    @AllArgsConstructor
    private static class Calculator {
        private CharacterData characterData;
        private Weapon weapon;
        private RelicSuit relicSuit;
        private ExtraBasicPromote extraBasicPromote;

        public CalcData calculate(double rate) {
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
            return new CalcData(baseHealth, baseDefence, baseAttack, baseSpeed);
        }

        @AllArgsConstructor
        public static class CalcData {
            private double health;
            private double defence;
            private double attack;
            private double speed;
        }
    }

}