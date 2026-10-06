package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.SkillCategory;
import com.laosun.aluminium.models.Damage;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * A damage instance carries the hit set of the attack behind it.
 *
 * <p>The reader for "a random one of the enemies HIT by this attack": a {@code DEALING_DAMAGE} rule is handed the
 * damage instance, so the fact must live on the instance -- the pattern {@code setSkillKey} and {@code setStance} already
 * follow. Empty means unknown, and a selector must fail rather than guess.
 */
public class HitSetOnDamageTest {

    private Damage instance() {
        return new Damage(null, null, DamageElement.PHYSICAL, DamageType.NORMAL, 100.0, SkillCategory.UNSPECIFIED);
    }

    @Test
    public void aFreshInstanceHasAnEmptyHitSet() {
        Assertions.assertTrue(instance().hitTargets().isEmpty(), "unknown until the caller says otherwise");
    }

    @Test
    public void theRecordedHitSetIsImmutable() {
        Damage damage = instance();
        damage.setHitTargets(java.util.Set.of());
        boolean immutable;
        try {
            damage.hitTargets().add(null);
            immutable = false;
        } catch (UnsupportedOperationException e) {
            immutable = true;
        }
        Assertions.assertTrue(immutable, "the returned set refuses mutation");
    }
}
