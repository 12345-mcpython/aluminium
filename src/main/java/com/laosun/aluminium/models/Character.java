package com.laosun.aluminium.models;

import com.laosun.aluminium.Constant;
import com.laosun.aluminium.beans.CharacterData;
import com.laosun.aluminium.beans.Translate;
import com.laosun.aluminium.enums.Camp;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.Path;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.exceptions.CharacterException;
import com.laosun.aluminium.models.skill.DefaultSkill;
import com.laosun.aluminium.models.skill.Skill;
import com.laosun.aluminium.models.skill.SkillTrace;
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
     * Character id (the key of {@code character_data.json}).
     *
     * <p>Why it exists: skill data is looked up by {@code cid} ({@code Constant.SKILLS.get(cid)}),
     * and the overworld basic attack / technique are **attached only at battle start**
     * (see {@code Battle#startBattle}) — by that moment the assembly point (装配点) is long gone,
     * so the character has to remember its own id.
     */
    private int cid;

    /**
     * This character's mechanics, as data (P8-7).
     *
     * <p>Never {@code null}: an unregistered character holds {@link TriggerTable#EMPTY}. That way
     * the interpreter treats "no mechanics" as a no-op instead of every call site null-checking, and
     * "not registered yet" stays an ordinary state rather than an error.
     */
    private TriggerTable triggerTable = TriggerTable.EMPTY;

    /**
     * The relic suit equipped on this character.
     */
    private RelicSuit relicSuit;
    /**
     * The weapon (light cone) equipped on this character.
     *
     * <p>⚠ <b>Set this through the builder, not through {@code setWeapon}.</b> The stat-sheet
     * pipeline consumes the weapon while {@code Builder#build()} runs (the calculator takes it as an
     * input), so assigning it to an <b>already-built</b> character changes this field but leaves the
     * sheet stale — the cone would appear equipped while contributing nothing. Use
     * {@code Character.builder().weapon(...)} or
     * {@link com.laosun.aluminium.utils.CharacterFactory#create(int, int, boolean, Weapon)}.
     *
     * <p>The Lombok setter stays only because the field is exposed through {@code @Setter}; it has no
     * caller in the project, and this note is the guard rail (the mistake is invisible at runtime,
     * so it cannot be left to "someone will notice").
     */
    private Weapon weapon;

    private EnumMap<SkillType, Integer> skillLevel;

    /**
     * Path (命途) (P5-1): determines the base aggro value, and thereby the probability that an enemy
     * selects this character. Parsed from {@code mt} in {@code character_data.json}; {@link Path#OTHER}
     * when the data is missing.
     */
    private Path path = Path.OTHER;

    /**
     * This character's own aggro value ({@code aggro} in {@code character_data.json}).
     *
     * <p>It is the game multiplier itself (Preservation 150 / Destruction 125 / others 100 /
     * Hunt · Erudition 75), so {@code Battle.aggroOf} prefers it and {@code path} only serves as the
     * fallback when that data is absent. {@code 0} = no data.
     */
    private int aggro;

    /**
     * Attack element (P8-1): comes from {@code attribute} in {@code character_data.json}.
     *
     * <p>⚠ That field is **all lowercase** ({@code "thunder"}), while {@code element} in
     * {@code skills.json} is capitalized ({@code "Thunder"}) — so parsing goes through
     * {@link DamageElement#fromString} (case-insensitive).
     *
     * <p>{@code null} = no data (this is the case for the placeholder characters made by
     * {@code fromAttributes}). A placeholder having no element is a **faithful reflection** of the data;
     * do not give it a fake element as a fallback.
     */
    private DamageElement element;

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
        this.path = other.path;
        this.aggro = other.aggro;
        this.element = other.element;
        this.cid = other.cid;
    }

    /**
     * Creates a character directly from pre-computed attributes.
     *
     * <p>⚠ <b>For tests / placeholders only; new code after P8 MUST NOT use it</b> (P8-1).
     * The characters it makes have no element, no path differences, an energy cap of 0, and skills that are
     * all {@link DefaultSkill} placeholders — for a real character use
     * {@link com.laosun.aluminium.utils.CharacterFactory#create(int, int)}.
     */
    public static Character fromAttributes(Translate name, DoubleValue[] attributes) {
        Character c = new Character(name, attributes);
        c.relicSuit = new RelicSuit();
        c.weapon = new Weapon(new Translate("EMPTY", "EMPTY"), "", 0, 0, 0, null, List.of());
        return c;
    }

    /**
     * Build a placeholder character straight from attribute values (tests only).
     *
     * <p>⚠ <b>New code after P8 MUST NOT use it</b>: the character it makes has no element, no path
     * differences, an energy cap of 0 (cannot cast an ultimate), and skills that are all
     * {@link DefaultSkill} placeholders.
     * For a real character use {@link com.laosun.aluminium.utils.CharacterFactory#create(int, int)}.
     */
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
        character.setSkillLevel(skillLevel);
        character.setSkills(skills);
        return character;
    }

    public void setSkillByClass(SkillType skillType, Function<Integer, ? extends Skill> skillFunction) {
        setSkill(skillType, skillFunction.apply(skillLevel.get(skillType)));
    }

    public void setSkillLevel(SkillType skillType, int skillLevel) {
        this.skillLevel.put(skillType, skillLevel);
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
        /**
         * An explicitly specified path; {@code null} = derive it from {@code mt} in the character data
         * (the default route).
         */
        private Path path;

        /**
         * The character's trigger table (P8-7); defaults to the empty table, which means
         * "no mechanics registered" -- an ordinary state, not an error.
         */
        private TriggerTable triggerTable = TriggerTable.EMPTY;

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
         * Explicitly specify the path (by default it is derived from {@code mt} in the character data;
         * normally there is no need to call this).
         */
        public Builder path(Path path) {
            this.path = path;
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
         * Attaches the character's trigger table (P8-7).
         *
         * <p>This is the assembly point for character mechanics: whoever builds the character (the
         * factories, or a test) decides which table it gets. The engine never looks a table up by
         * cid -- which is what keeps `Battle` free of character branches.
         *
         * @param triggerTable the table; {@code null} becomes the empty table
         */
        public Builder triggerTable(TriggerTable triggerTable) {
            this.triggerTable = triggerTable == null ? TriggerTable.EMPTY : triggerTable;
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
            SkillTrace.appendTo(SkillTrace.init(cid), calcData);
            Character character = new Character(characterData.name(), calcData.build());
            character.relicSuit = relicSuit;
            character.weapon = weapon;
            EnumMap<SkillType, Skill> skills = new EnumMap<>(SkillType.class);
            for (Map.Entry<SkillType, Integer> entry : skillLevel.entrySet()) {
                SkillType type = entry.getKey();
                int level = entry.getValue();
                // P8-2: every slot resolves **its own** skill_id (previously it was always 1, so all six
                // slots had the basic attack's data).
                // Only equip the "always-on character" slots: the overworld basic attack (6) / technique (7)
                // are overworld skills attached by Battle.startBattle(); the summon slot belongs to memosprites
                // (P9-4), so neither is equipped here.
                if (!type.isIntrinsic()) {
                    continue;
                }
                Integer slot = Constant.SKILL_SLOT.get(type);
                if (slot == null) {
                    continue;
                }
                skills.put(type, new DefaultSkill(cid, slot, level));
            }
            skills.putAll(customSkills);
            character.setSkills(skills);
            character.setSkillLevel(skillLevel);
            character.setLevel(level);
            character.setCid(cid);
            // P5-1: path and aggro come from the character data (mt = path string, aggro = the game multiplier itself)
            character.setPath(this.path != null ? this.path : Path.fromMt(characterData.mt()));
            character.setAggro(characterData.aggro() > 0 ? characterData.aggro() : 0);
            // P8-1: the element likewise comes from the character data (attribute is all lowercase; for the
            // parsing convention see DamageElement#fromString)
            character.setElement(DamageElement.fromString(characterData.attribute()));
            // P8-1: the energy cap follows the data. **A null MUST stay 0 (= no energy bar); it must NOT fall
            // back to 100** — 1407 遐蝶 is the only null in the whole data set, and a fallback would conjure
            // an energy bar for her out of thin air (P3-0 table A).
            character.setMaxEnergy(characterData.maxEnergy() != null ? characterData.maxEnergy() : 0);
            // P8-7: the character's mechanics as data. Always non-null -- an unregistered character
            // has the empty table, which is normal (the trigger interpreter treats it as a no-op and
            // Battle never has to null-check).
            character.setTriggerTable(triggerTable);
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
