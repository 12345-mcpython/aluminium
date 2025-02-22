package com.laosun.aluminium.test;

import com.laosun.aluminium.models.Character;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class CharacterTest {
    @Test
    public void test() {
        Character c1 = new Character("test 1", 100, 100, 100, 100, 100);
        Assertions.assertEquals("test 1", c1.getName());
    }
}
