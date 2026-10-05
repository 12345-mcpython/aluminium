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
    """        // ⚠ A zero the DATA states is not the same as a value nobody stated (2026-10-02). Measured: the game's own
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
        "name": "小伊卡",
        "source": "1409 风堇 天赋 疗愈世间的晨曦 (140904): 「忆灵小伊卡初始拥有等同于风堇 50% 生命上限的生命上限。」"
                  "＋ 游戏表 `ExcelOutput/AvatarServantConfig.json` 第 11409 行：`HPInherit: \"#1\"`（技能 140904 的参数 #1 = 0.5 ✓）、"
                  "`SpeedBase: \"0\"`、`SpeedInherit: \"0\"`、`Aggro: 100`。",
        "note": "生命：文档 **50%** ✓、游戏表 `HPInherit #1` = **0.5** ✓ —— 两边一致 ✓✓。"
                "速度：游戏表对它**给的就是 0** ✗（`SpeedBase` 与 `SpeedInherit` 均为 `\"0\"` ✓），"
                "且它自己的 `ConfigCharacter/Servant/Servant_HyacineServant_00_Config.json` 里**没有任何字面速度** ✗，"
                "只把 `Speed` 列进 `SyncPropertyExceptList`（**不跟随召唤者** ✓）⇒ 即**由能力推动** ✓ ⇒ 写成 `flat: 0` ✓ **＋ `by_ability: true`** ✓"
                "（⚠ 而不是让它静默地接受 0 ✗）。⚠ 登记：推动它速度的**那条能力**尚未读 ✗。"
                "仇恨：游戏表给 **100** ✓（= 引擎默认 ✓）⇒ 不写 ✓。",
        "panel": [{"attribute": "HEALTH", "percent": 0.5},
                  {"attribute": "SPEED", "flat": 0, "by_ability": True}],
    },
    "1415": {
        "name": "德谬歌",
        "source": "1415 昔涟 终结技 诗的「◦」誓约的「∞」 (141503/141504): 「德谬歌初始拥有等同于昔涟 100% 生命上限的生命上限。」"
                  "＋ 游戏表第 11415 行：`HPInherit: \"#1\"`（技能 141503 的参数 #1 = 1 ✓）、`SpeedBase: \"0\"`、`SpeedInherit: \"0\"`、`Aggro: 100`。",
        "note": "生命：文档 **100%** ✓、游戏表 `HPInherit #1` = **1** ✓ —— 一致 ✓✓。"
                "速度：同 1409 —— 游戏表给的就是 **0** ✗，且自己的侍从配置里无字面速度 ✗，`Speed` 同样在 `SyncPropertyExceptList` 里 ✓"
                "⇒ 写成 `flat: 0` ✓ **＋ `by_ability: true`** ✓。仇恨 **100** ✓ ⇒ 不写 ✓。"
                "⚠ 本份同时是目标 ①-b 的前提 ✓（她的忆灵技能「奇袭结束后，使刻律德菈获得 1 点充能」需她在场 ✓）。",
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
