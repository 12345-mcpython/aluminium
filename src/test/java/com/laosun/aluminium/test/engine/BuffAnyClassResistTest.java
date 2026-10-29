package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.enums.DebuffClass;
import com.laosun.aluminium.models.entity.Character;
import com.laosun.aluminium.models.buff.ClassResistBuff;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * {@code RESIST_DEBUFF} with NO class means 「任意负面状态」 - the spelling 1409's 小伊卡 needs for 「免疫负面效果」.
 *
 * <h2>Why the old behaviour was a hole</h2>
 * A state that belongs to no class (a stat-down, a vulnerability) has {@code debuffClass() == null}, and
 * {@code debuffResistOf(null)} used to answer {@code 0} before looking at anything - so such a state could not be
 * resisted by any rule, no matter what the text said. A null-class {@link ClassResistBuff} now covers every family,
 * and it still does not turn a <b>named</b> resistance into an all-family one (the regression case below).
 */
public class BuffAnyClassResistTest {

    private static final double EPS = 1e-9;

    @Test
    public void aNamedClassCoversOnlyItsOwnFamily() {
        Character unit = CharacterFactory.create(1001, 80);
        unit.getBuffManager().addBuff(new ClassResistBuff(DebuffClass.CONTROL, 1.0, 1));

        Assertions.assertEquals(1.0, unit.getBuffManager().debuffResistOf(DebuffClass.CONTROL), EPS,
                "the named class is covered");
        Assertions.assertEquals(0.0, unit.getBuffManager().debuffResistOf(DebuffClass.DOT), EPS,
                "another class is not");
        Assertions.assertEquals(0.0, unit.getBuffManager().debuffResistOf(null), EPS,
                "and a state of no class is not either -- that is what makes the fallback the fallback");
    }

    @Test
    public void aNullClassCoversEveryFamilyIncludingTheUnclassifiedOnes() {
        Character unit = CharacterFactory.create(1001, 80);
        unit.getBuffManager().addBuff(new ClassResistBuff(null, 1.0, 1, true));

        Assertions.assertEquals(1.0, unit.getBuffManager().debuffResistOf(DebuffClass.CONTROL), EPS, "control");
        Assertions.assertEquals(1.0, unit.getBuffManager().debuffResistOf(DebuffClass.DOT), EPS, "DoT");
        Assertions.assertEquals(1.0, unit.getBuffManager().debuffResistOf(null), EPS,
                "and a state that belongs to no class at all");
    }

    @Test
    public void aNullClassAndANamedOneAdd() {
        Character unit = CharacterFactory.create(1001, 80);
        unit.getBuffManager().addBuff(new ClassResistBuff(null, 0.5, 1, true));
        unit.getBuffManager().addBuff(new ClassResistBuff(DebuffClass.DOT, 0.5, 1, true));

        Assertions.assertEquals(1.0, unit.getBuffManager().debuffResistOf(DebuffClass.DOT), EPS,
                "two sources still add (clamped to 1), the any-class one contributing to every family");
        Assertions.assertEquals(0.5, unit.getBuffManager().debuffResistOf(DebuffClass.CONTROL), EPS,
                "and only the any-class one contributes to a family nobody else names");
    }
}
