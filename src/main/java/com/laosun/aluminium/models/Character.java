package com.laosun.aluminium.models;

import com.laosun.aluminium.beans.CharacterData;
import com.laosun.aluminium.beans.Translate;
import com.laosun.aluminium.enums.Camp;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.exceptions.CharacterException;
import com.laosun.aluminium.utils.AttributeBuilder;
import com.laosun.aluminium.utils.CharacterDataProvider;
import com.laosun.aluminium.utils.ConstantCharacterDataProvider;
import com.laosun.aluminium.utils.LevelPromotionCalc;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

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
    /**
     * The relic suit equipped on this character.
     */
    private RelicSuit relicSuit;
    /**
     * The weapon (light cone) equipped on this character.
     */
    private Weapon weapon;

    private EnumMap<SkillType, Integer> skillLevel;

    protected Character(Translate name, DoubleValue[] attributes) {
        super(name.english(), Camp.PLAYER, attributes);
    }

    /**
     * Copy construction
     *
     * @param other what you want to copy
     */
    public Character(Character other) {
        super(other);
        this.relicSuit = other.relicSuit != null ? other.relicSuit.clone() : null;
        this.weapon = other.weapon != null ? other.weapon.clone() : null;
        this.skillLevel = other.skillLevel != null ? other.skillLevel.clone() : null;
    }

    /**
     * Creates a character directly from pre-computed attributes (for testing / quick setup).
     */
    public static Character fromAttributes(Translate name, DoubleValue[] attributes) {
        Character c = new Character(name, attributes);
        c.relicSuit = new RelicSuit();
        c.weapon = new Weapon(new Translate("EMPTY", "EMPTY"), "", 0, 0, 0, null, List.of());
        return c;
    }

    public static Character fromAttributes(String name, double health, double defence, double attack, double speed) {
        AttributeBuilder attributeBuilder = new AttributeBuilder();
        attributeBuilder.setBase(HEALTH, health);
        attributeBuilder.setBase(DEFENCE, defence);
        attributeBuilder.setBase(ATTACK, attack);
        attributeBuilder.setBase(SPEED, speed);
        // CONSTRUCT TEST SKILL
        EnumMap<SkillType, Integer> skillLevel = new EnumMap<>(SkillType.class);
        EnumMap<SkillType, Skill> skills = new EnumMap<>(SkillType.class);
        Arrays.stream(SkillType.values()).forEach(t -> skillLevel.put(t, 1));
        for (Map.Entry<SkillType, Integer> entry : skillLevel.entrySet()) {
            SkillType type = entry.getKey();
            int level = entry.getValue();
            int skillId = 1;
            skills.put(type, new DefaultSkill(1001, skillId, level));
        }
        // END
        Character character = new Character(new Translate(name, name), attributeBuilder.build());
        character.setSkills(skills);
        return character;
    }

    public void setSkillByClass(SkillType skillType, Function<Integer, ? extends Skill> skillFunction) {
        setSkill(skillType, skillFunction.apply(skillLevel.get(skillType)));
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
        private RelicSuit relicSuit = new RelicSuit();
        private Weapon weapon = new Weapon(new Translate("EMPTY", "EMPTY"), "", 0, 0, 0, null, List.of());
        private boolean isPromote = false;
        private ExtraBasicPromote extraBasicPromote = new ExtraBasicPromote();
        private CharacterDataProvider characterDataProvider = new ConstantCharacterDataProvider();

        private final EnumMap<SkillType, Integer> skillLevel = new EnumMap<>(SkillType.class);

        private final EnumMap<SkillType, Skill> customSkills = new EnumMap<>(SkillType.class);

        // init default skill level for 1
        {
            Arrays.stream(SkillType.values()).forEach(t -> skillLevel.put(t, 1));
        }

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

        public Builder skillLevel(SkillType skillType) {
            skillLevel.put(skillType, skillLevel.get(skillType) + 1);
            return this;
        }

        public Builder skill(SkillType skillType, Skill skill) {
            customSkills.put(skillType, skill);
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
            AttributeBuilder calcData = new Calculator(characterData, weapon, relicSuit, extraBasicPromote).calculate(rate);
            SkillPoint.appendTo(SkillPoint.init(cid), calcData);
            Character character = new Character(characterData.name(), calcData.build());
            character.relicSuit = relicSuit;
            character.weapon = weapon;
            EnumMap<SkillType, Skill> skills = new EnumMap<>(SkillType.class);
            for (Map.Entry<SkillType, Integer> entry : skillLevel.entrySet()) {
                SkillType type = entry.getKey();
                int level = entry.getValue();
                // WRITE 1 for placeholder will change TODO
                // Future will not have placeholder skill
                skills.put(type, new DefaultSkill(cid, 1, level));
            }
            skills.putAll(customSkills);
            character.setSkills(skills);
            character.setSkillLevel(skillLevel);
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
