package com.laosun.aluminium.test;

import com.laosun.aluminium.models.DoubleValue;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.laosun.aluminium.models.DoubleValue.Modifier.ModifierSource.TEST;
import static com.laosun.aluminium.models.DoubleValue.Modifier.ModifierSource.UNKNOWN;

public class DoubleValueTest {
    @Test
    public void test() {
        DoubleValue dw = new DoubleValue(100);
        Assertions.assertEquals(100, dw.get(), 0.0001, dw.toString());
        dw.addModifier(DoubleValue.Modifier.pure(100, TEST));
        Assertions.assertEquals(200, dw.get(), 0.0001, dw.toString());
        dw.addModifier(DoubleValue.Modifier.addPercent(0.05));
        Assertions.assertEquals(205, dw.get(), 0.0001, dw.toString());
        dw.removeModifier(dw.filterBySource(TEST).getFirst());
        Assertions.assertEquals(105, dw.get(), 0.0001, dw.toString());
        dw.addModifier(DoubleValue.Modifier.multiplyPercent(0.1));
        Assertions.assertEquals(115.5, dw.get(), 0.0001, dw.toString());
        dw.clearModifiers();
        Assertions.assertEquals(100, dw.get(), 0.0001, dw.toString());
    }

    @Test
    public void baseRecomputation() {
        DoubleValue dw = new DoubleValue(100);
        dw.addModifier(DoubleValue.Modifier.addPercent(0.5, TEST));

        dw.base(200);

        Assertions.assertEquals(300, dw.get(), 0.0001, dw.toString());
        dw.addBase(100);
        Assertions.assertEquals(450, dw.get(), 0.0001, dw.toString());
    }

    @Test
    public void cloneIsIndependent() {
        DoubleValue dw = new DoubleValue(100);
        dw.addModifier(DoubleValue.Modifier.pure(50, TEST));

        DoubleValue copy = dw.clone();
        copy.removeModifier(copy.filterBySource(TEST).getFirst());

        Assertions.assertEquals(150, dw.get(), 0.0001, dw.toString());
        Assertions.assertEquals(100, copy.get(), 0.0001, copy.toString());
    }

    @Test
    public void findModifierBySourceAndId() {
        DoubleValue dw = new DoubleValue(100);
        dw.addModifier(DoubleValue.Modifier.addPercent(0.1, TEST, 7));
        dw.addModifier(DoubleValue.Modifier.multiplyPercent(0.2, UNKNOWN, 8));

        DoubleValue.Modifier found = dw.findFirstBySourceAndId(TEST, 7);
        Assertions.assertNotNull(found);
        Assertions.assertEquals(0.1, found.getValue(), 1e-9);
        Assertions.assertNull(dw.findFirstBySourceAndId(TEST, 8));
        Assertions.assertNull(dw.findFirstBySourceAndId(TEST, 42));
    }

    @Test
    public void zeroValue() {
        DoubleValue zero = DoubleValue.zero();
        Assertions.assertEquals(0, zero.get(), 0.0001, zero.toString());

        zero.addModifier(DoubleValue.Modifier.pure(5, TEST));
        Assertions.assertEquals(5, zero.get(), 0.0001, zero.toString());
    }

    @Test
    public void modifierListIsSnapshot() {
        DoubleValue dw = new DoubleValue(100);
        dw.addModifier(DoubleValue.Modifier.pure(10, TEST));

        List<DoubleValue.Modifier> snapshot = dw.filterBySource(TEST);
        dw.clearModifiers();

        Assertions.assertEquals(1, snapshot.size());
        Assertions.assertEquals(0, dw.filterBySource(TEST).size());
    }
}
