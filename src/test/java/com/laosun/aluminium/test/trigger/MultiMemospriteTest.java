package com.laosun.aluminium.test.trigger;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.data.Memosprites;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.entity.Summon;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * <b>Several memosprites per character</b> - the shape 1512 知更鸟•晴歌 needs for 「「晴空乐手」全员登台」.
 *
 * <p>The data file may hold one object (every other character) or an <b>array</b>; {@code Memosprites.of} keeps answering
 * the first so nothing that never needed more changes, and {@code SummonService.memosprite(Character, String)} brings out
 * a named one. The fixture ({@code game/memosprites/1001.json} in the TEST resources, two entries) exists because the shipped 1512 file still
 * holds only 贝茜 - the other two are a data gap, not an engine one.
 */
public class MultiMemospriteTest {

    private static final int OWNER = 1001;

    private static Battle battle() {
        Character owner = CharacterFactory.create(OWNER, 80);
        Battle battle = new Battle(List.of(owner), List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        return battle;
    }

    /**
     * The file's array is read as a LIST, and {@code of} still answers the first for the callers that want one.
     */
    @Test
    public void aFileMayStateSeveralAndOfStillAnswersTheFirst() {
        Memosprites.clearCache();
        List<com.laosun.aluminium.beans.MemospriteSpec> all = Memosprites.allOf(OWNER);

        Assertions.assertEquals(2, all.size(), "the fixture states two memosprites");
        Assertions.assertEquals(List.of("贝茜", "啾米"), all.stream()
                .map(com.laosun.aluminium.beans.MemospriteSpec::name).toList(), "in file order");
        Assertions.assertEquals("贝茜", Memosprites.of(OWNER).name(), "of() keeps its old meaning: the first");
    }

    /**
     * Two NAMED summons coexist; asking again for one that is already out answers it instead of adding a duplicate.
     */
    @Test
    public void twoNamesCoexistAndEachIsSummonedOnce() {
        Battle battle = battle();
        Character owner = battle.characters().getFirst();

        Summon first = battle.summonMemosprite(owner, "贝茜");
        Summon second = battle.summonMemosprite(owner, "啾米");

        Assertions.assertEquals("贝茜", first.getName());
        Assertions.assertEquals("啾米", second.getName());
        Assertions.assertEquals(2, battle.summonCountOf(owner), "both are on the field");
        Assertions.assertSame(first, battle.summonMemosprite(owner, "贝茜"),
                "the same name is not summoned twice");
        Assertions.assertEquals(2, battle.summonCountOf(owner), "and the count did not move");
    }

    /**
     * A name the file does not state is refused loudly, and the message lists the ones it does.
     */
    @Test
    public void anUnknownNameIsRefusedWithTheKnownOnes() {
        Battle battle = battle();
        Character owner = battle.characters().getFirst();

        IllegalArgumentException refused = Assertions.assertThrows(IllegalArgumentException.class,
                () -> battle.summonMemosprite(owner, "派丁"));
        Assertions.assertTrue(refused.getMessage().contains("贝茜") && refused.getMessage().contains("啾米"),
                "the refusal names what the file does state: " + refused.getMessage());
    }
}
