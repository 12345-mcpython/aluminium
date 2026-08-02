package com.laosun.aluminium.models.kit;

import java.util.List;

/**
 * All hand-written kits of the 13** series (1.3+ 角色).
 */
public final class ThirteenStarKits {

    private ThirteenStarKits() {
    }

    public static List<CharacterKit> all() {
        return List.of(
                new GallagherKit(),
                new ArgentiKit(),
                new RuanMeiKit(),
                new AventurineKit(),
                new DrRatioKit(),
                new SparkleKit(),
                new BlackSwanKit(),
                new AcheronKit(),
                new RobinKit(),
                new FireflyKit(),
                new MishaKit(),
                new SundayKit(),
                new JadeKit(),
                new BoothillKit(),
                new RappaKit(),
                new DahliaKit());
    }
}
