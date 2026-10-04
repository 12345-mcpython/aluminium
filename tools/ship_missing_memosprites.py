"""Two missing memosprite files, both stated in full by their own documents (round 1669).

Found while hunting the servant panel: the config's `ServantID`s (11402/11407/11409/11413/11415/11512) map exactly to the
characters whose documents say 「忆灵」, and `resources/memosprites/` has files for 1402/1407/1413/1512 but NOT for 1409 or 1415:

  * 1409 风堇, talent 「疗愈世间的晨曦」: 「忆灵**小伊卡**初始拥有等同于风堇 50% 生命上限的生命上限。」
  * 1415 昔涟, ultimate 「诗的「◦」誓约的「∞」」: 「**德谬歌**初始拥有等同于昔涟 100% 生命上限的生命上限。」

Both numbers are shares of the SUMMONER's max HP, which is the panel convention the other five files already use. Nothing is
invented: no speed/attack/aggro is stated for either, and none is written.
"""
import io
import json
import os
import sys

FILES = {
    "1409": {
        "name": "\u5c0f\u4f0a\u5361",
        "source": "1409 \u98ce\u5807 \u5929\u8d4b \u7597\u6108\u4e16\u95f4\u7684\u6668\u66e6 (140904): \u300c\u5fc6\u7075\u5c0f\u4f0a\u5361\u521d\u59cb\u62e5\u6709\u7b49\u540c\u4e8e\u98ce\u5807 **50% \u751f\u547d\u4e0a\u9650**\u7684\u751f\u547d\u4e0a\u9650\u3002\u300d",
        "note": "\u9762\u677f\u53ea\u5199**\u6587\u6863\u7ed9\u5230\u7684**\uff1a**\u751f\u547d\u4e0a\u9650 = \u98ce\u5807\u7684 50%** \u2713\uff08\u4e0e 1402/1407/1413/1512 \u540c\u4e00\u7ea6\u5b9a \u2713\uff09\u3002"
                "\u6587\u6863\u5bf9\u5c0f\u4f0a\u5361\u7684**\u901f\u5ea6\uff0f\u653b\u51fb\uff0f\u4ec7\u6068\u4e00\u5b57\u672a\u7ed9** \u2717 \u21d2 \u4e0d\u5199 \u2713\u3002",
        "panel": [{"attribute": "HEALTH", "percent": 0.5}],
    },
    "1415": {
        "name": "\u5fb7\u8c2c\u6b4c",
        "source": "1415 \u6614\u6d9f \u7ec8\u7ed3\u6280 \u8bd7\u7684\u300c\u25e6\u300d\u8a93\u7ea6\u7684\u300c\u221e\u300d (141504): \u300c\u5fb7\u8c2c\u6b4c\u521d\u59cb\u62e5\u6709\u7b49\u540c\u4e8e\u6614\u6d9f **100% \u751f\u547d\u4e0a\u9650**\u7684\u751f\u547d\u4e0a\u9650\u3002\u300d",
        "note": "\u9762\u677f\u53ea\u5199**\u6587\u6863\u7ed9\u5230\u7684**\uff1a**\u751f\u547d\u4e0a\u9650 = \u6614\u6d9f\u7684 100%** \u2713\u3002"
                "\u6587\u6863\u5bf9\u5fb7\u8c2c\u6b4c\u7684**\u901f\u5ea6\uff0f\u653b\u51fb\uff0f\u4ec7\u6068\u4e00\u5b57\u672a\u7ed9** \u2717 \u21d2 \u4e0d\u5199 \u2713\u3002"
                "\u26a0 \u540c\u53e5\u8fd8\u8bf4\u300c\u53ec\u5524\u5fc6\u7075\u5fb7\u8c2c\u6b4c\uff0c\u4f7f\u5176\u7acb\u5373\u83b7\u5f97 1 \u4e2a**\u989d\u5916\u56de\u5408**\u5e76\u6fc0\u6d3b\u5168\u4f53\u961f\u53cb\u7684\u7ec8\u7ed3\u6280\u2026\u5355\u573a\u6218\u6597\u4e2d\u53ea\u80fd\u65bd\u653e 1 \u6b21\u300d\u2717 "
                "\u2014\u2014 \u90a3\u4e9b\u5c5e\u4e8e\u5979\u7684\u7ec8\u7ed3\u6280\u89c4\u5219 \u2717 \u21d2 \u5728\u89d2\u8272\u6587\u4ef6\u91cc\u767b\u8bb0 \u2713\u3002",
        "panel": [{"attribute": "HEALTH", "percent": 1.0}],
    },
}

for cid, spec in FILES.items():
    path = "src/main/resources/memosprites/%s.json" % cid
    if os.path.exists(path):
        sys.exit("REFUSING: %s already exists" % path)
    with io.open(path, "w", encoding="utf-8", newline="\n") as handle:
        json.dump(spec, handle, ensure_ascii=False, indent=2)
        handle.write("\n")
    print("ok   wrote %s" % path)

JUDGE = "src/test/java/com/laosun/aluminium/test/MissingMemospritePanelsTest.java"
io.open(JUDGE, "w", encoding="utf-8", newline="").write('''package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.MemospriteSpec;
import com.laosun.aluminium.data.Memosprites;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Summon;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * Two memosprites that had no file even though their documents state the panel (2026-10-02).
 *
 * <p>Found through the config's ServantID range: 11409 is 1409's Little Ica ("Max HP equal to 50% of Hyacine's Max HP") and
 * 11415 is 1415's Demiurge ("Max HP equal to 100% of Cyrene's Max HP"). The other four ids in that range already had files.
 */
public class MissingMemospritePanelsTest {
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /** 1409's Little Ica: half of 风堇's max HP. */
    @Test
    public void hyacinesIcaHasHalfHerMaxHp() {
        assertPanel(1409, 0.5, "\\u5c0f\\u4f0a\\u5361");
    }

    /** 1415's Demiurge: all of 昔涟's max HP. */
    @Test
    public void cyrenesDemiurgeHasAllOfHers() {
        assertPanel(1415, 1.0, "\\u5fb7\\u8c2c\\u6b4c");
    }

    // ==================================================================

    private static void assertPanel(int cid, double share, String expectedName) {
        MemospriteSpec spec = Memosprites.of(cid);
        Assertions.assertNotNull(spec, "the file exists now");
        Assertions.assertEquals(expectedName, spec.name(), "the document names it");
        Assertions.assertEquals(share, spec.panel().getFirst().percent(), 1e-9, "the stated share");

        Character master = CharacterFactory.create(cid, LEVEL, false, null, null, 0);
        Battle battle = new Battle(List.of(master),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();

        Summon summoned = null;
        for (Summon candidate : battle.summonsOf(master)) {
            summoned = candidate;
        }
        Assertions.assertNotNull(summoned, "a SUMMON rule brings it out");
        double masterHp = master.getAttribute(AttributeType.HEALTH).get();
        double summonHp = summoned.getAttribute(AttributeType.HEALTH).get();
        System.out.println("[panel] " + spec.name() + " hp=" + summonHp + " master=" + masterHp + " share=" + (summonHp / masterHp));
        Assertions.assertEquals(share, summonHp / masterHp, 1e-6, "its max HP is that share of the summoner's");
    }
}
''')
print("ok   wrote the judge")
