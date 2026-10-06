package com.laosun.aluminium.models.buff;

import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.skill.Skill;

/**
 * A skill-slot swap carried by a buff: "将下一次普攻强化为[酒花奔涌]" (turns the next basic attack into [酒花奔涌]) (1301 Gallagher).
 *
 * <p><b>Why a buff.</b> The swap needs a <i>lifetime</i>, and this engine already has exactly the lifetimes the documents
 * use - `turns`, `permanent`, and `until` ("下一次普攻" is `until: next_attack`). Riding on {@link AbstractBuff} means the
 * swap expires, is cleaned up, and is undone by the same machinery as every other timed effect, rather than by a second
 * mechanism that would have to be kept in step with the first.
 *
 * <p>Note: It installs the replacement into the owner's own {@code EnumMap<SkillType, Skill>} - the map the action flow reads
 * from ({@code Character.getSkills()}) - and restores <b>whatever was there</b> when it leaves, which is what makes two
 * overlapping swaps safe: the second one captures the first one's replacement as its "original".
 *
 * <p>Note: It is a buff on our own unit, not a debuff: {@code isDebuff()} stays {@code false}, so "解除负面效果" (dispel negative effects) can never take
 * an enhanced attack away.
 */
public class SkillSwapBuff extends AbstractBuff {
    private final SkillType slot;
    private final Skill replacement;

    /** What that slot held before this swap; captured on apply so it can be put back on remove. */
    private Skill original;

    public SkillSwapBuff(SkillType slot, Skill replacement) {
        super(Integer.MAX_VALUE, false, true);            // the LIFETIME decides; this buff never ticks itself away
        this.slot = slot;
        this.replacement = replacement;
    }

    @Override
    public boolean canAct() {
        return false;
    }

    /** The slot this swap owns (readable by tests and by future "which skill is equipped" conditions). */
    public SkillType getSlot() {
        return slot;
    }

    /** The skill installed into the slot. */
    public Skill getReplacement() {
        return replacement;
    }

    @Override
    public void applyEffect(CanHit target) {
        if (target instanceof Character character) {
            original = character.getSkills().get(slot);   // capture first: two overlapping swaps must nest, not clobber
            character.getSkills().put(slot, replacement);
        }
    }

    @Override
    public void removeBuff(CanHit target) {
        if (target instanceof Character character && original != null) {
            character.getSkills().put(slot, original);
        }
    }

    @Override
    public void tickEffect(CanHit target) {
        // No counter of its own: the lifetime (turns / until / permanent) is what ends this.
    }
}
