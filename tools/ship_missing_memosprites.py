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
        "name": "小伊卡",
        "source": "1409 风堇 天赋 疗愈世间的晨曦 (140904): 「忆灵小伊卡初始拥有等同于风堇 **50% 生命上限**的生命上限。」",
        "note": "面板只写**文档给到的**：**生命上限 = 风堇的 50%** ✓（与 1402/1407/1413/1512 同一约定 ✓）。"
                "文档对小伊卡的**速度／攻击／仇恨一字未给** ✗ ⇒ 不写 ✓。",
        "panel": [{"attribute": "HEALTH", "percent": 0.5}],
    },
    "1415": {
        "name": "德谬歌",
        "source": "1415 昔涟 终结技 诗的「◦」誓约的「∞」 (141504): 「德谬歌初始拥有等同于昔涟 **100% 生命上限**的生命上限。」",
        "note": "面板只写**文档给到的**：**生命上限 = 昔涟的 100%** ✓。"
                "文档对德谬歌的**速度／攻击／仇恨一字未给** ✗ ⇒ 不写 ✓。"
                "⚠ 同句还说「召唤忆灵德谬歌，使其立即获得 1 个**额外回合**并激活全体队友的终结技…单场战斗中只能施放 1 次」✗ "
                "—— 那些属于她的终结技规则 ✗ ⇒ 在角色文件里登记 ✓。",
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
