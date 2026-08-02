package com.laosun.aluminium.models.kit;

import java.util.List;

/**
 * All hand-written kits of the 14** series (3.x 角色).
 */
public final class FourteenStarKits {

    private FourteenStarKits() {
    }

    public static List<CharacterKit> all() {
        return List.of(
                new TheHertaKit(),
                new AglaeaKit(),
                new TribbieKit(),
                new MydeiKit(),
                new AnaxaKit(),
                new CipherKit(),
                new CastoriceKit(),
                new PhainonKit(),
                new HyacineKit(),
                new HysilensKit(),
                new CerydraKit(),
                new EvernightKit(),
                new DanHengTerraeKit(),
                new CyreneKit());
    }
}
