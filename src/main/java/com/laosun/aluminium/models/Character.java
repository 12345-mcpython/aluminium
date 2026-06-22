package com.laosun.aluminium.models;

import com.laosun.aluminium.beans.CharacterData;
import com.laosun.aluminium.beans.Translate;
import com.laosun.aluminium.enums.Camp;
import com.laosun.aluminium.exceptions.CharacterException;
import com.laosun.aluminium.utils.AttributeBuilder;
import com.laosun.aluminium.utils.CharacterDataProvider;
import com.laosun.aluminium.utils.ConstantCharacterDataProvider;
import com.laosun.aluminium.utils.LevelPromotionCalc;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.List;

import static com.laosun.aluminium.enums.AttributeType.*;
import static com.laosun.aluminium.models.DoubleValue.Modifier.ModifierSource.BASE;

/**
 * A player-controlled character with full combat stats.
 *
 * <p>Characters are built via the {@link Builder} pattern. The builder:
 * <ol>
 *   <li>Loads base character data from the data source</li>
 *   <li>Applies level/promotion scaling to HP, ATK, DEF</li>
 *   <li>Accumulates base stats, weapon stats, relic suit attributes,
 *   skill point bonuses, and extra promotion bonuses into an
 *   {@link AttributeBuilder}</li>
 *   <li>Produces a final {@link DoubleValue} array indexed by {@code AttributeType.ordinal()}</li>
 * </ol>
 *
 * <p>Usage:
 * <pre>{@code
 * Character character = Character.builder()
 *     .cid(1409)
 *     .level(80)
 *     .relicSuit(mySuit)
 *     .weapon(myWeapon)
 *     .extraValue(myPromote)
 *     .build();
 * }</pre>
 */
@Getter
@Setter
@ToString(callSuper = true)
public class Character extends CanHit {
    /** The relic suit equipped on this character. */
    private RelicSuit relicSuit;
    /** The weapon (light cone) equipped on this character. */
    private Weapon weapon;

    private Character(Translate name, DoubleValue[] attributes) {
        super(name.english(), Camp.PLAYER, attributes);
    }

    /**
     * Creates a character directly from pre-computed attributes (for testing / quick setup).
     */
    public static Character fromAttributes(Translate name, DoubleValue[] attributes) {
        Character ch = new Character(name, attributes);
        ch.relicSuit = new RelicSuit();
        ch.weapon = new Weapon(new Translate("EMPTY", "EMPTY"), "", 0, 0, 0, null, List.of());
        return ch;
    }

    /**
     * Creates a new builder for constructing a character.
     *
     * @return a fresh builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Fluent builder for constructing a {@link Character} with full combat stats.
     */
    public static class Builder {
        private int cid;
        private int level = 1;
        private RelicSuit relicSuit;
        private Weapon weapon;
        private boolean isPromote = false;
        private ExtraBasicPromote extraBasicPromote = new ExtraBasicPromote();
        private CharacterDataProvider characterDataProvider = new ConstantCharacterDataProvider();

        /**
         * Marks the character as promoted (ascended) at their current level.
         */
        public Builder isPromote() {
            this.isPromote = true;
            return this;
        }

        /**
         * Sets the character ID.
         *
         * @param cid the game character ID
         */
        public Builder cid(int cid) {
            this.cid = cid;
            return this;
        }

        /**
         * Sets extra flat/percentage bonuses (from traces, handguards, etc.).
         */
        public Builder extraValue(ExtraBasicPromote extraBasicPromote) {
            this.extraBasicPromote = extraBasicPromote;
            return this;
        }

        /**
         * Equips a relic suit on this character.
         */
        public Builder relicSuit(RelicSuit relicSuit) {
            this.relicSuit = relicSuit;
            return this;
        }

        /**
         * Equips a weapon on this character.
         */
        public Builder weapon(Weapon weapon) {
            this.weapon = weapon;
            return this;
        }

        /**
         * Sets the character level.
         *
         * @param level character level (1-80)
         */
        public Builder level(int level) {
            this.level = level;
            return this;
        }

        /**
         * Sets a custom data provider for character base stats (for testing).
         */
        public Builder characterDataProvider(CharacterDataProvider characterDataProvider) {
            this.characterDataProvider = characterDataProvider;
            return this;
        }

        /**
         * Builds the character with all accumulated configuration.
         *
         * @return the fully computed character
         * @throws CharacterException if the character ID is zero or not found
         */
        public Character build() {
            CharacterData characterData = validateAndGet(cid);
            double rate = LevelPromotionCalc.calcCharacterRate(level, isPromote);
            RelicSuit suit = relicSuit != null ? relicSuit : new RelicSuit();
            Weapon wp = weapon != null ? weapon : new Weapon(new Translate("EMPTY", "EMPTY"), "", 0, 0, 0, null, List.of());
            AttributeBuilder calcData = new Calculator(characterData, wp, suit, extraBasicPromote).calculate(rate);
            SkillPoint.appendTo(SkillPoint.init(cid), calcData);
            Character character = new Character(characterData.name(), calcData.build());
            character.relicSuit = suit;
            character.weapon = wp;
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

    /**
     * Internal calculator that orchestrates the attribute computation pipeline.
     */
    @AllArgsConstructor
    private static class Calculator {
        private CharacterData characterData;
        private Weapon weapon;
        private RelicSuit relicSuit;
        private ExtraBasicPromote extraBasicPromote;

        /**
         * Executes the full calculation pipeline.
         *
         * @param rate the level scaling multiplier
         * @return the populated attribute builder ready for final commit
         */
        private AttributeBuilder calculate(double rate) {
            AttributeBuilder atb = new AttributeBuilder();
            atb.setBase(HEALTH, characterData.health() * rate)
                    .setBase(ATTACK, characterData.attack() * rate)
                    .setBase(DEFENCE, characterData.defence() * rate)
                    .setBase(SPEED, characterData.speed());
            atb.addBase(HEALTH, weapon.getHealth())
                    .addBase(DEFENCE, weapon.getDefence())
                    .addBase(ATTACK, weapon.getAttack());
            relicSuit.appendTo(atb);
            weapon.appendTo(atb);
            extraBasicPromote.appendTo(atb);
            atb.addPercentPoint(CRIT_CHANCE, characterData.critChance(), BASE);
            atb.addPercentPoint(CRIT_ATTACK, characterData.critAttack(), BASE);
            return atb;
        }
    }

}
