package com.laosun.aluminium.models.kit;

import java.util.List;

/**
 * All hand-written kits of the 15** series (4.0 欢愉 + 特殊角色).
 */
public final class FifteenStarKits {

    private FifteenStarKits() {
    }

    public static List<CharacterKit> all() {
        return List.of(
                new SparxieKit(),
                new YaoGuangKit(),
                new AshveilKit(),
                new EvanesciaKit(),
                new SilverWolf999Kit(),
                new MortenaxBladeKit(),
                new HimekoNovaKit());
    }
}
