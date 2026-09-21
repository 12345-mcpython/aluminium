package com.laosun.aluminium.models;

import com.laosun.aluminium.beans.CharacterData;
import com.laosun.aluminium.beans.Translate;
import com.laosun.aluminium.enums.Camp;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.Path;
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

    /**
     * 命途（P5-1）：决定基础仇恨值，进而决定敌人选中该角色的概率。
     * 由 {@code character_data.json} 的 {@code mt} 解析，缺数据时 {@link Path#OTHER}。
     */
    private Path path = Path.OTHER;

    /**
     * 角色自身的仇恨值（{@code character_data.json} 的 {@code aggro}）。
     *
     * <p>它就是游戏倍率本身（存护 150 / 毁灭 125 / 其他 100 / 巡猎·智识 75），
     * 所以 {@code Battle.aggroOf} 优先用它，{@code path} 只作为没有该数据时的兜底。
     * {@code 0} = 没有数据。
     */
    private int aggro;

    /**
     * 攻击元素（P8-1）：来自 {@code character_data.json} 的 {@code attribute}。
     *
     * <p>⚠ 该字段是**全小写**的（{@code "thunder"}），而 {@code skills.json} 的
     * {@code element} 是首字母大写（{@code "Thunder"}）—— 所以解析走
     * {@link DamageElement#fromString}（大小写不敏感）。
     *
     * <p>{@code null} = 没有数据（{@code fromAttributes} 造的占位角色就是这样）。
     * 占位角色没有元素是**如实反映**，不要给它兜一个假元素。
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
    }

    /**
     * Creates a character directly from pre-computed attributes.
     *
     * <p>⚠ <b>仅测试 / 占位用，P8 之后新代码禁止使用</b>（P8-1）。
     * 它造出来的角色没有元素、没有命途差异、能量上限为 0、技能全是
     * {@link DefaultSkill} 占位 —— 真实角色请用
     * {@link com.laosun.aluminium.utils.CharacterFactory#create(int, int)}。
     */
    public static Character fromAttributes(Translate name, DoubleValue[] attributes) {
        Character c = new Character(name, attributes);
        c.relicSuit = new RelicSuit();
        c.weapon = new Weapon(new Translate("EMPTY", "EMPTY"), "", 0, 0, 0, null, List.of());
        return c;
    }

    /**
     * 从属性值直接造一个占位角色（仅测试用）。
     *
     * <p>⚠ <b>P8 之后新代码禁止使用</b>：造出来的角色没有元素、没有命途差异、
     * 能量上限为 0（放不出终结技）、技能全是 {@link DefaultSkill} 占位。
     * 真实角色请用 {@link com.laosun.aluminium.utils.CharacterFactory#create(int, int)}。
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
         * 显式指定的命途；{@code null} = 用角色数据里的 {@code mt} 推导（默认路径）。
         */
        private Path path;

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
         * 显式指定命途（默认从角色数据的 {@code mt} 推导，一般不用调）。
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
            character.setLevel(level);
            // P5-1：命途与仇恨来自角色数据（mt=命途字符串，aggro=游戏倍率本身）
            character.setPath(this.path != null ? this.path : Path.fromMt(characterData.mt()));
            character.setAggro(characterData.aggro() > 0 ? characterData.aggro() : 0);
            // P8-1：元素同样来自角色数据（attribute 是全小写，解析口径见 DamageElement#fromString）
            character.setElement(DamageElement.fromString(characterData.attribute()));
            // P8-1：能量上限按数据来。**null 必须保持 0（= 没有能量条），不能兜底成 100** ——
            // 全数据里只有 1407 遐蝶是 null，兜底会凭空给她造出一条能量条（P3-0 A 表）。
            character.setMaxEnergy(characterData.maxEnergy() != null ? characterData.maxEnergy() : 0);
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
