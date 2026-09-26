package com.laosun.aluminium.models.enemy;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EnemySkillData;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.SkillEffectType;
import com.laosun.aluminium.models.*;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.skill.Skill;
import com.laosun.aluminium.data.SkillData;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Enemy skill (P5-3): data-driven from {@code enemy_skills.json}, not hard-coded.
 *
 * <p>Difference from character skills: it does **not** go through {@link SkillData}/the multiplier
 * table (that is the character-skill structure); instead it deals damage directly as
 * "attack × multiplier × hits". That is why {@link #getData()} returns {@code null} and
 * {@link #execute} is fully custom — and also why {@code SkillExecutor} must not be reused for it
 * (the character-skill toughness-reduction/shape-dispatch logic does not apply to enemies: enemies
 * do not attack the toughness bar).
 *
 * <p>Each hit goes through {@link Battle#applyDamage} independently: **each hit rolls crit and
 * settles on its own** (consistent with character skills).
 *
 * <p>⚠ For the origin of the multipliers see {@link EnemySkillData}: the data source has no enemy
 * skill table, so these values are guesses.
 *
 * @param element    damage element (already resolved at construction time from the data / the
 *                   monster's {@code stance_type}, never null)
 * @param multiplier multiplier (damage base = attack × multiplier)
 * @param hits       number of hits (at least 1)
 * @param type       damage type
 */
public class EnemySkill extends Skill {

    @Getter
    private final DamageElement element;
    @Getter
    private final double multiplier;
    @Getter
    private final int hits;
    private final DamageType type;
    private final SkillEffectType effect;

    public EnemySkill(DamageElement element, double multiplier, int hits, DamageType type) {
        this(element, multiplier, hits, type, SkillEffectType.SINGLE_ATTACK);
    }

    /**
     * @param effect the skill's shape; {@code null} = single target, which is what every pre-P9-2
     *               entry relied on, so the default keeps them bit-for-bit unchanged
     */
    public EnemySkill(DamageElement element, double multiplier, int hits, DamageType type,
                      SkillEffectType effect) {
        this.element = element == null ? DamageElement.PHYSICAL : element;
        this.multiplier = multiplier;
        this.hits = Math.max(1, hits);
        this.type = type == null ? DamageType.NORMAL : type;
        this.effect = effect == null ? SkillEffectType.SINGLE_ATTACK : effect;
    }

    @Override
    public int getLevel() {
        return 1;
    }

    /**
     * {@inheritDoc}
     *
     * @return always {@code null}: enemy skills do not use the character multiplier table; the
     * execution logic lives entirely in {@link #execute}
     */
    @Override
    public SkillData getData() {
        return null;
    }

    /**
     * Applies the skill to whoever its shape says it reaches: the primary target ({@code SingleAttack}),
     * everyone on our side ({@code AoEAttack}), or the primary target plus its neighbours
     * ({@code Blast}). {@link #hits} segments land on <b>each</b> of them, each settling independently
     * (so each rolls crit on its own).
     *
     * <p>P9-2: before this, every enemy skill hit the primary target — a multi-target enemy skill in the
     * data had no way to reach a second character, so an AoE would silently under-hit. The dispatch
     * mirrors {@code SkillExecutor}'s character-skill shapes so the two sides read the same way, without
     * sharing code: enemies do not reduce toughness and do not expand parameters, which is why
     * {@link #execute} stays custom (see the class Javadoc).
     *
     * @param battle the battle in progress
     * @param user   the applier (an enemy)
     * @param target the list chosen by the caller (only the first is used, as the main target)
     */
    @Override
    public void execute(Battle battle, CanHit user, List<? extends CanHit> target) {
        if (target == null || target.isEmpty()) {
            return;
        }
        CanHit victim = target.getFirst();
        if (victim == null || victim.isDeath()) {
            return;
        }
        for (CanHit struck : struckBy(victim, battle)) {
            strike(battle, user, struck);
        }
    }

    /**
     * Who this skill reaches, given the caller's main target.
     */
    private List<CanHit> struckBy(CanHit mainTarget, Battle battle) {
        // ⚠ Deliberately NOT filtered to the living here. A dead character is simply struck for zero
        // segments by strike()'s own isDeath() check, which is the one guard that matters -- and it is
        // the one a test can reach. An earlier version filtered here too, and mutation testing showed
        // the outer filter changed no observable outcome (removing it left every test green), i.e. it
        // was an untestable second guard for the same fact. One guard, exercised.
        List<Character> team = battle.characters.stream()
                .filter(Objects::nonNull)
                .toList();
        return switch (effect) {
            case AOE_ATTACK -> List.copyOf(team);
            case BLAST -> {
                int center = team.indexOf(mainTarget);
                if (center < 0) {
                    yield List.of(mainTarget);       // not one of ours: fall back to the single target
                }
                List<CanHit> reached = new ArrayList<>();
                reached.add(team.get(center));
                if (center > 0) {
                    reached.add(team.get(center - 1));
                }
                if (center < team.size() - 1) {
                    reached.add(team.get(center + 1));
                }
                yield reached;
            }
            default -> List.of(mainTarget);
        };
    }

    /**
     * Lands {@link #hits} segments on one character.
     */
    private void strike(Battle battle, CanHit user, CanHit victim) {
        double base = user.getAttribute(AttributeType.ATTACK).get() * multiplier;
        for (int i = 0; i < hits; i++) {
            if (victim.isDeath()) {
                break;                               // once killed mid-way, stop hitting (no overkill on a corpse)
            }
            battle.applyDamage(victim, new Damage(user, victim, element, type, base));
        }
    }
}
