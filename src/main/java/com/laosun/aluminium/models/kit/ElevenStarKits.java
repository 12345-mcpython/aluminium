package com.laosun.aluminium.models.kit;

import java.util.List;

/**
 * All hand-written kits of the 11** series (1.1 角色).
 */
public final class ElevenStarKits {

    private ElevenStarKits() {
    }

    public static List<CharacterKit> all() {
        return List.of(
                new BronyaKit(),
                new SeeleKit(),
                new ServalKit(),
                new GepardKit(),
                new NatashaKit(),
                new PelaKit(),
                new ClaraKit(),
                new SampoKit(),
                new HookKit(),
                new LynxKit(),
                new LukaKit(),
                new TopazKit());
    }
}
