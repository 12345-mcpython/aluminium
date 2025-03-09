package com.laosun.aluminium.gen.test;

import com.laosun.aluminium.beans.Model;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ModelTest {
    @Test
    public void testAdd() {
        Assertions.assertEquals(1, Model.TEST);
    }
}
