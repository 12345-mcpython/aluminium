package com.laosun.aluminium.buff;

import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.entity.CanHit;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.skill.Skill;

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
 * <p>⚠ <b>A swap is identified by its SLOT</b> ({@link #isSameKind}): "拥有强化普攻<b>和</b>强化欢愉技" (1506 Silver Wolf
 * LV.999) is two swaps up at the same time, and the class-level answer this used to inherit made the SECOND one evict the
 * first - measured: her enhanced basic reverted to the ordinary attack the moment the Elation swap landed, while the notes
 * above describe exactly the nesting that eviction prevented.
 *
 * <p>Note: It is a buff on our own unit, not a debuff: {@code isDebuff()} stays {@code false}, so "解除负面效果" (dispel negative effects) can never take
 * an enhanced attack away.
 */
public class SkillSwapBuff extends AbstractBuff {
    private final SkillType slot;
    private final Skill replacement;

    /**
     * What that slot held before this swap; captured on apply so it can be put back on remove.
     */
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

    /**
     * The slot this swap owns (readable by tests and by future "which skill is equipped" conditions).
     */
    public SkillType getSlot() {
        return slot;
    }

    /**
     * The skill installed into the slot.
     */
    public Skill getReplacement() {
        return replacement;
    }

    /**
     * Two swaps are the same kind when they own the <b>same slot</b> - not merely when they are both swaps.
     *
     * <p>Reader: 1506 Silver Wolf LV.999's 「拥有强化普攻<b>和</b>强化欢愉技」, which installs two swaps in one Ultimate. With
     * the class-level default the second evicted the first (the manager removes same-kind buffs before adding), so her
     * enhanced basic silently reverted - and the symptom was a slot that read 1 instead of 8, with nothing else to see.
     *
     * <p>Same slot still means replace: re-swapping a slot refreshes it rather than nesting two replacements that would then
     * have to be unwound in order.
     */
    @Override
    public boolean isSameKind(AbstractBuff other) {
        return other instanceof SkillSwapBuff swap && swap.slot == this.slot;
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
