package com.laosun.aluminium.utils;

import com.laosun.aluminium.Constant;
import com.laosun.aluminium.beans.CharacterData;
import com.laosun.aluminium.exceptions.CharacterException;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.energy.EnergyProvider;
import com.laosun.aluminium.models.energy.NoConventionalEnergyProvider;

import java.util.Set;

/**
 * Character factory (P8-1): build a **real character** with one call.
 *
 * <pre>{@code
 * Character jingYuan = CharacterFactory.create(1204, 80);
 * jingYuan.getElement();      // THUNDER
 * jingYuan.getPath();         // ERUDITION
 * jingYuan.getMaxEnergy();    // 130
 * jingYuan.getAggro();        // 75
 * }</pre>
 *
 * <p>It is essentially a thin wrapper around {@code Character.builder()} — the stat pipeline
 * (level scaling / light cone / relics / traces / extra bonuses) was already complete in P2; what
 * P8-1 added is the **character identity fields**: element, path, aggro, max energy (see
 * {@code Character.Builder#build()}).
 *
 * <p>Division of labour with {@link Character#fromAttributes}: that one is the test/placeholder
 * entry point (no element, no path, max energy 0, all skills placeholders); this one is the real
 * character entry point. **After P8 all new code uses this one.**
 *
 * <p><b>Skill assembly is wired up in P8-2</b>: a character built by {@code create()} carries
 * {@code DefaultSkill} with the **real slot mapping** (basic attack 1 / skill 2 / ultimate 3 /
 * talent 4); there is only one mapping table ({@code Constant.SKILL_SLOT}) and the assembly point is
 * {@code Character.Builder#build()} — see {@code engine.md} §7.2. Map basic attack (6) / technique (7)
 * are not installed here; they are attached by {@code Battle.startBattle()}.
 *
 * <p>⚠ This class **only handles character identity and resources** and does not touch skill
 * multipliers: follow-up attacks/summons are P8-3/P9-4.
 */
public final class CharacterFactory {
    /**
     * Characters that go through **stacks/special resources** instead of conventional energy (the
     * "capabilities the engine does not have yet" bucket of the P8-0 three-way split).
     *
     * <p>What they accumulate in the game is 【追忆】 (Reminiscence) / 【新蕊】 (New Bud) / 【火种】
     * (Kindling) / points; conventional energy gain is meaningless for them. And {@code castUltra}
     * only looks at {@code currentEnergy >= maxEnergy}, so without blocking them they could fill the
     * bar by "getting hit" and fire an ultimate that should not exist (Acheron (黄泉) caps at 9).
     *
     * <p>Putting the check at the assembly point is explicitly allowed by P8-0 (the provider registry /
     * the assembly point are the only places where a cid may appear). Once P8-8's {@code Resource}
     * lands, this table evolves into a "character → resource implementation" registry.
     */
    private static final Set<Integer> SPECIAL_RESOURCE_CHARACTERS = Set.of(
            1220,   // Feixiao (飞霄): stacks (ultimate threshold 6, cap 12)
            1308,   // Acheron (黄泉): stacks (cap 9)
            1407,   // Castorice (遐蝶): 【新蕊】 (max_energy is null; there was never an energy bar)
            1408,   // Phainon (白厄): 【火种】 (cap 12)
            1415,   // Cyrene (昔涟): 【追忆】 (see engine.md §9.5)
            1506    // Silver Wolf LV.999 (银狼LV.999): the Elation (欢愉) system
    );

    /** The "not credited" provider shared by the characters above (stateless, so shareable). */
    private static final EnergyProvider NO_CONVENTIONAL_ENERGY = new NoConventionalEnergyProvider();

    private CharacterFactory() {
    }

    /**
     * Build a fully promoted real character.
     *
     * <p>"Fully promoted" means promoted up to the cap for the current level (Lv80 → promoted 6
     * times), which is exactly the {@code true} in
     * {@code LevelPromotionCalc.calcCharacterRate(level, true)}.
     * Jing Yuan (景元) Lv80 promoted HP is exactly = {@code 158.4 × 7.35 = 1164.24}, matching the game.
     *
     * @param cid   character id (see {@code character_data.json})
     * @param level level (1-80)
     * @return the real character
     * @throws CharacterException if the character does not exist
     */
    public static Character create(int cid, int level) {
        return create(cid, level, true);
    }

    /**
     * Build a real character.
     *
     * @param cid      character id
     * @param level    level (1-80)
     * @param promoted whether the character is promoted ({@code false} = not promoted, lower stat
     *                 sheet; for the difference between the two see
     *                 {@link LevelPromotionCalc#calcCharacterRate(int, boolean)})
     * @return the real character
     * @throws CharacterException if the character does not exist
     */
    public static Character create(int cid, int level, boolean promoted) {
        Character.Builder builder = Character.builder().cid(cid).level(level);
        if (promoted) {
            builder = builder.isPromote();
        }
        Character character = builder.build();
        // stack/special-resource characters: swap out conventional energy gain (otherwise they could fill the bar just by getting hit and fire an ultimate they should not have)
        if (SPECIAL_RESOURCE_CHARACTERS.contains(cid)) {
            character.setEnergyProvider(NO_CONVENTIONAL_ENERGY);
        }
        return character;
    }

    /**
     * Whether this character goes through stacks/special resources (rather than conventional energy).
     *
     * <p>Gives the caller a "ask first, then wire up" hook, and also lets tests and the future P8-8
     * registry reuse the same table.
     */
    public static boolean usesSpecialResource(int cid) {
        return SPECIAL_RESOURCE_CHARACTERS.contains(cid);
    }

    /**
     * Whether the character exists (whether this id is in the data).
     *
     * <p>Gives the caller an "ask first, then build" hook, so it does not have to probe by catching
     * {@link CharacterException}.
     */
    public static boolean exists(int cid) {
        return Constant.CHARACTERS.containsKey(cid);
    }

    /**
     * Get the raw character data (without computing the stat sheet).
     *
     * @param cid character id
     * @return the data row
     * @throws CharacterException if the character does not exist
     */
    public static CharacterData data(int cid) {
        CharacterData data = Constant.CHARACTERS.get(cid);
        if (data == null) {
            throw new CharacterException.CharacterNotFoundException(
                    String.format("Character '%s' not found", cid));
        }
        return data;
    }
}
