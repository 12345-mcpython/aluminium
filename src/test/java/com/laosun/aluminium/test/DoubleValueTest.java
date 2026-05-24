package com.laosun.aluminium.test;

import com.laosun.aluminium.models.DoubleValue;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static com.laosun.aluminium.models.DoubleValue.Modifier.ModifierSource.TEST;

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
    }
}
