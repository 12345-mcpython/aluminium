package com.laosun.aluminium.models.kit;

import java.util.List;

/**
 * All hand-written kits of the 10** series (1.0 角色).
 */
public final class TenStarKits {

    private TenStarKits() {
    }

    public static List<CharacterKit> all() {
        return List.of(
                new March7thKit(),
                new DanHengKit(),
                new HimekoKit(),
                new WeltKit(),
                new KafkaKit(),
                new SilverWolfKit(),
                new ArlanKit(),
                new AstaKit(),
                new HertaKit());
    }
}
