"""A panel can state a zero the DATA states, when an ability supplies it (2026-10-02, item 65).

Three readers, all measured:
  * `memosprites/1409.json` -- 小伊卡, HP = 0.5 (document and the game table agree: HPInherit "#1" of skill 140904);
  * `memosprites/1415.json` -- 德谬歌, HP = 1.0 (same agreement: HPInherit "#1" of 141503);
  * objective ①-b -- 1415's memosprite skill 「奇袭结束后，使刻律德菈获得 1 点充能」 needs her memosprite on the field.

The blocker was measured, not guessed: `ExcelOutput/AvatarServantConfig.json` gives BOTH of them `SpeedBase: "0"` and
`SpeedInherit: "0"`, and their own `ConfigCharacter/Servant/*.json` carries no literal speed -- only a
`SyncPropertyExceptList` that keeps Speed OUT of the summoner sync. So the game's own data says the spawned speed is 0 and an
ability moves it; the validator refused that as "0 speed never acts".

So the panel gains one honest spelling, `"by_ability": true`, which says "this attribute is not stated here because an ability
sets it" -- as opposed to silently accepting 0. The flag is the claim; the note is the record.
"""
import io
import json
import os
import sys

SPEC = "src/main/java/com/laosun/aluminium/beans/MemospriteSpec.java"
DATA = "src/main/java/com/laosun/aluminium/data/Memosprites.java"


def patch(path, old, new, label, count=1):
    text = io.open(path, encoding="utf-8").read()
    found = text.count(old)
    if found != count:
        sys.stderr.write("REFUSING %s: the anchor appears %d times\n" % (label, found))
        raise SystemExit(1)
    io.open(path, "w", encoding="utf-8", newline="\n").write(text.replace(old, new, count))
    print("ok   %s" % label)


patch(
    SPEC,
    """    public record Panel(@SerializedName("attribute") String attribute,
                        @SerializedName("percent") Double percent,
                        @SerializedName("flat") Double flat,
                        @SerializedName("source") String source) {

        /**
         * The attribute-derived panel, which is every panel shipped before 2026-10-02: no {@code source}.
         */
        public Panel(String attribute, Double percent, Double flat) {
            this(attribute, percent, flat, null);
        }""",
    """    public record Panel(@SerializedName("attribute") String attribute,
                        @SerializedName("percent") Double percent,
                        @SerializedName("flat") Double flat,
                        @SerializedName("source") String source,
                        @SerializedName("by_ability") Boolean byAbility) {

        /**
         * The attribute-derived panel, which is every panel shipped before 2026-10-02: no {@code source}.
         */
        public Panel(String attribute, Double percent, Double flat) {
            this(attribute, percent, flat, null, null);
        }""",
    "Panel.byAbility: the spelling",
)

patch(
    DATA,
    """        if ((attribute == AttributeType.HEALTH || attribute == AttributeType.SPEED)
                && !(positive(entry.percent()) || positive(entry.flat()))) {""",
    """        // \u26a0 A zero the DATA states is not the same as a value nobody stated (2026-10-02). Measured: the game's own
        // `AvatarServantConfig.json` gives 小伊卡 and 德谬歌 `SpeedBase "0"` and `SpeedInherit "0"`, and their servant configs
        // keep Speed OUT of the summoner sync -- an ability moves it. `by_ability` is how a file says that, so a zero can be
        // written down instead of being silently accepted (`positive` alone would hide it).
        if ((attribute == AttributeType.HEALTH || attribute == AttributeType.SPEED)
                && !(positive(entry.percent()) || positive(entry.flat()))
                && !Boolean.TRUE.equals(entry.byAbility())) {""",
    "the zero check honours the flag",
)

PANELS = {
    "1409": {
        "name": "\u5c0f\u4f0a\u5361",
        "source": "1409 \u98ce\u5807 \u5929\u8d4b \u7597\u6108\u4e16\u95f4\u7684\u6668\u66e6 (140904): \u300c\u5fc6\u7075\u5c0f\u4f0a\u5361\u521d\u59cb\u62e5\u6709\u7b49\u540c\u4e8e\u98ce\u5807 50% \u751f\u547d\u4e0a\u9650\u7684\u751f\u547d\u4e0a\u9650\u3002\u300d"
                  "\uff0b \u6e38\u620f\u8868 `ExcelOutput/AvatarServantConfig.json` \u7b2c 11409 \u884c\uff1a`HPInherit: \"#1\"`\uff08\u6280\u80fd 140904 \u7684\u53c2\u6570 #1 = 0.5 \u2713\uff09\u3001"
                  "`SpeedBase: \"0\"`\u3001`SpeedInherit: \"0\"`\u3001`Aggro: 100`\u3002",
        "note": "\u751f\u547d\uff1a\u6587\u6863 **50%** \u2713\u3001\u6e38\u620f\u8868 `HPInherit #1` = **0.5** \u2713 \u2014\u2014 \u4e24\u8fb9\u4e00\u81f4 \u2713\u2713\u3002"
                "\u901f\u5ea6\uff1a\u6e38\u620f\u8868\u5bf9\u5b83**\u7ed9\u7684\u5c31\u662f 0** \u2717\uff08`SpeedBase` \u4e0e `SpeedInherit` \u5747\u4e3a `\"0\"` \u2713\uff09\uff0c"
                "\u4e14\u5b83\u81ea\u5df1\u7684 `ConfigCharacter/Servant/Servant_HyacineServant_00_Config.json` \u91cc**\u6ca1\u6709\u4efb\u4f55\u5b57\u9762\u901f\u5ea6** \u2717\uff0c"
                "\u53ea\u628a `Speed` \u5217\u8fdb `SyncPropertyExceptList`\uff08**\u4e0d\u8ddf\u968f\u53ec\u5524\u8005** \u2713\uff09\u21d2 \u5373**\u7531\u80fd\u529b\u63a8\u52a8** \u2713 \u21d2 \u5199\u6210 `flat: 0` \u2713 **\uff0b `by_ability: true`** \u2713"
                "\uff08\u26a0 \u800c\u4e0d\u662f\u8ba9\u5b83\u9759\u9ed8\u5730\u63a5\u53d7 0 \u2717\uff09\u3002⚠ \u767b\u8bb0\uff1a\u63a8\u52a8\u5b83\u901f\u5ea6\u7684**\u90a3\u6761\u80fd\u529b**\u5c1a\u672a\u8bfb \u2717\u3002"
                "\u4ec7\u6068\uff1a\u6e38\u620f\u8868\u7ed9 **100** \u2713\uff08= \u5f15\u64ce\u9ed8\u8ba4 \u2713\uff09\u21d2 \u4e0d\u5199 \u2713\u3002",
        "panel": [{"attribute": "HEALTH", "percent": 0.5},
                  {"attribute": "SPEED", "flat": 0, "by_ability": True}],
    },
    "1415": {
        "name": "\u5fb7\u8c2c\u6b4c",
        "source": "1415 \u6614\u6d9f \u7ec8\u7ed3\u6280 \u8bd7\u7684\u300c\u25e6\u300d\u8a93\u7ea6\u7684\u300c\u221e\u300d (141503/141504): \u300c\u5fb7\u8c2c\u6b4c\u521d\u59cb\u62e5\u6709\u7b49\u540c\u4e8e\u6614\u6d9f 100% \u751f\u547d\u4e0a\u9650\u7684\u751f\u547d\u4e0a\u9650\u3002\u300d"
                  "\uff0b \u6e38\u620f\u8868\u7b2c 11415 \u884c\uff1a`HPInherit: \"#1\"`\uff08\u6280\u80fd 141503 \u7684\u53c2\u6570 #1 = 1 \u2713\uff09\u3001`SpeedBase: \"0\"`\u3001`SpeedInherit: \"0\"`\u3001`Aggro: 100`\u3002",
        "note": "\u751f\u547d\uff1a\u6587\u6863 **100%** \u2713\u3001\u6e38\u620f\u8868 `HPInherit #1` = **1** \u2713 \u2014\u2014 \u4e00\u81f4 \u2713\u2713\u3002"
                "\u901f\u5ea6\uff1a\u540c 1409 \u2014\u2014 \u6e38\u620f\u8868\u7ed9\u7684\u5c31\u662f **0** \u2717\uff0c\u4e14\u81ea\u5df1\u7684\u4f8d\u4ece\u914d\u7f6e\u91cc\u65e0\u5b57\u9762\u901f\u5ea6 \u2717\uff0c`Speed` \u540c\u6837\u5728 `SyncPropertyExceptList` \u91cc \u2713"
                "\u21d2 \u5199\u6210 `flat: 0` \u2713 **\uff0b `by_ability: true`** \u2713\u3002\u4ec7\u6068 **100** \u2713 \u21d2 \u4e0d\u5199 \u2713\u3002"
                "\u26a0 \u672c\u4efd\u540c\u65f6\u662f\u76ee\u6807 \u2460-b \u7684\u524d\u63d0 \u2713\uff08\u5979\u7684\u5fc6\u7075\u6280\u80fd\u300c\u5947\u88ad\u7ed3\u675f\u540e\uff0c\u4f7f\u523b\u5f8b\u5fb7\u83c8\u83b7\u5f97 1 \u70b9\u5145\u80fd\u300d\u9700\u5979\u5728\u573a \u2713\uff09\u3002",
        "panel": [{"attribute": "HEALTH", "percent": 1.0},
                  {"attribute": "SPEED", "flat": 0, "by_ability": True}],
    },
}

for cid, spec in PANELS.items():
    path = "src/main/resources/memosprites/%s.json" % cid
    if os.path.exists(path):
        sys.exit("REFUSING: %s already exists" % path)
    with io.open(path, "w", encoding="utf-8", newline="\n") as handle:
        json.dump(spec, handle, ensure_ascii=False, indent=2)
        handle.write("\n")
    print("ok   wrote %s" % path)

JUDGE = "src/test/java/com/laosun/aluminium/test/ZeroSpeedByAbilityTest.java"
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
 * A zero the DATA states can be written down (2026-10-02).
 *
 * <p>The game's own servant table gives 小伊卡 (11409) and 德谬歌 (11415) SpeedBase "0" and SpeedInherit "0", and keeps Speed
 * out of the summoner sync, so an ability moves it. Without the flag the loader refused the file; with it the summon enters
 * play, which is what the documents and the table together describe.
 */
public class ZeroSpeedByAbilityTest {
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /** 1409's Little Ica enters play: 50% of her health, and the speed the data states (0, moved by an ability). */
    @Test
    public void theIcaEntersPlayAtTheStatedSpeed() {
        MemospriteSpec spec = Memosprites.of(1409);
        Assertions.assertEquals(Boolean.TRUE, spec.panel().get(1).byAbility(), "the file says an ability moves it");
        Character unit = summonOf(1409);
        Assertions.assertEquals(0.0, unit.getAttribute(AttributeType.SPEED).get(), 1e-9,
                "the table states 0 for this servant");
        System.out.println("[zero-speed] " + spec.name() + " speed="
                + unit.getAttribute(AttributeType.SPEED).get()
                + " hp=" + unit.getAttribute(AttributeType.HEALTH).get());
    }

    /** 1415's Demiurge: all of her health, same zero-speed statement. */
    @Test
    public void theDemiurgeEntersPlayToo() {
        MemospriteSpec spec = Memosprites.of(1415);
        Assertions.assertEquals(1.0, spec.panel().getFirst().percent(), 1e-9, "HPInherit #1 of 141503 is 1");
        Character unit = summonOf(1415);
        Assertions.assertNotNull(unit, "it is on the field");
    }

    // ==================================================================

    private static Character summonOf(int cid) {
        Character master = CharacterFactory.create(cid, LEVEL, false, null, null, 0);
        Battle battle = new Battle(List.of(master),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        Summon found = null;
        for (Summon candidate : battle.summonsOf(master)) {
            found = candidate;
        }
        Assertions.assertNotNull(found, "a SUMMON rule brings it out");
        return found;
    }
}
''')
print("ok   wrote the judge")
