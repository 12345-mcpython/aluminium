package com.laosun.aluminium.models.kit;

import java.util.List;

/**
 * All hand-written kits of the 12** series (1.2+ 角色).
 */
public final class TwelveStarKits {

    private TwelveStarKits() {
    }

    public static List<CharacterKit> all() {
        return List.of(
                new QingqueKit(),
                new TingyunKit(),
                new LuochaKit(),
                new JingYuanKit(),
                new BladeKit(),
                new SushangKit(),
                new YukongKit(),
                new FuXuanKit(),
                new YanqingKit(),
                new GuinaifenKit(),
                new BailuKit(),
                new JingliuKit(),
                new DanHengILKit(),
                new XueyiKit(),
                new HanyaKit(),
                new HuohuoKit(),
                new JiaoqiuKit(),
                new FeixiaoKit(),
                new YunliKit(),
                new LingshaKit(),
                new MozeKit(),
                new MarchHuntKit(),
                new FugueKit());
    }
}
