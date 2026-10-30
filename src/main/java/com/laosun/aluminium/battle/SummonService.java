package com.laosun.aluminium.battle;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.GameData;
import com.laosun.aluminium.enemy.SummonFactory;
import com.laosun.aluminium.entity.CanHit;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.entity.Summon;
import com.laosun.aluminium.enums.Camp;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Bringing a summon onto the field, and answering "which summons does this unit own".
 *
 * <p><b>Why this is a separate object.</b> {@code Battle} used to own both the creation rules (master
 * validation, camp, pending entry, the {@code SUMMONED} marker) and the roster queries. The rules are
 * one subject: a summon is placed in its master's camp, its master link is the only ownership fact, and
 * the queries all walk that link. Keeping them next to the turn/cast code made both harder to read;
 * this object owns the rules, {@code Battle} keeps the event plumbing.
 */
public final class SummonService {

    private final Battle battle;
    private final GameData data;
    private final Consumer<Summon> onSummoned;

    /**
     * @param battle     the battle the summon joins
     * @param data       the data pack the summon is read from
     * @param onSummoned records the summon for the {@code SUMMONED} event, fired after scheduling
     */
    public SummonService(Battle battle, GameData data, Consumer<Summon> onSummoned) {
        this.battle = battle;
        this.data = data;
        this.onSummoned = onSummoned;
    }

    /**
     * Brings a summon onto the field, in its master's camp.
     *
     * @param master         the unit calling the summon
     * @param summonId       the summon's own monster id
     * @param hardLevelGroup the stage's hard level group
     * @return the summon, already in its camp's roster and queued to enter the action bar
     */
    public Summon summon(CanHit master, int summonId, int hardLevelGroup) {
        if (master == null) {
            throw new IllegalArgumentException("A summon (" + summonId + ") needs a master to belong to");
        }
        if (master.isDeath()) {
            throw new IllegalArgumentException(
                    "A dead master cannot summon (" + master.getName() + " -> " + summonId + "): the summon "
                            + "would enter already orphaned, and the very next removeDeadCombatants would "
                            + "take it straight back out, so the call could never do anything");
        }
        if (master.getCamp() != Camp.ENEMY && master.getCamp() != Camp.PLAYER) {
            throw new IllegalArgumentException(
                    "A summon must join a camp that has a roster (master " + master.getName() + " is "
                            + master.getCamp() + ", summon " + summonId + "): CAMP.NEUTRAL has none, and "
                            + "putting it in either list would silently decide who it fights for");
        }
        Summon summon = SummonFactory.create(data, summonId, master.getLevel(), hardLevelGroup, master.getCamp());
        summon.setMaster(master);
        battle.addCombatant(summon);
        onSummoned.accept(summon);
        return summon;
    }

    /**
     * Brings a character's memosprite onto the field.
     *
     * @param master the summoning character
     * @return the memosprite, or the one already standing
     */
    public Summon memosprite(Character master) {
        if (master == null) {
            throw new IllegalArgumentException("A memosprite needs a summoner");
        }
        if (master.isDeath()) {
            throw new IllegalArgumentException(
                    "A dead character cannot summon a memosprite (" + master.getName() + "): it would enter "
                            + "already orphaned, and the very next removeDeadCombatants would take it "
                            + "straight back out");
        }
        Summon existing = memospriteOf(master);
        if (existing != null) {
            return existing;
        }
        Summon memosprite = SummonFactory.memosprite(master, battle::partyResourceValue);
        memosprite.setMaster(master);
        battle.addCombatant(memosprite);
onSummoned.accept(memosprite);
        return memosprite;
    }

    /**
     * Brings one <b>named</b> memosprite of a character onto the field - 1512 知更鸟•晴歌 summons three (贝茜 / 啾米 / 派丁)
     * and 「「晴空乐手」全员登台」 is a state about <b>all three</b> being out, so "one memosprite per character" was the wrong
     * shape for her.
     *
     * <p>Unlike {@link #memosprite(Character)}, this does not stop at the first one standing: each NAME is summoned at most
     * once (a second call answers the one already out), and different names coexist.
     *
     * @param master the summoning character
     * @param name   which of its memosprites, by the name its file states
     * @return the memosprite that is now on the field
     */
    public Summon memosprite(Character master, String name) {
        if (master == null) {
            throw new IllegalArgumentException("A memosprite needs a summoner");
        }
        if (name == null || name.isBlank()) {
            return memosprite(master);
        }
        var spec = com.laosun.aluminium.data.Memosprites.allOf(master.getCid()).stream()
                .filter(each -> name.trim().equals(each.name()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Character " + master.getName() + " (" + master.getCid() + ") has no memosprite named \""
                                + name + "\"; its file states "
                                + com.laosun.aluminium.data.Memosprites.allOf(master.getCid()).stream()
                                .map(com.laosun.aluminium.beans.MemospriteSpec::name).toList()));
        for (CanHit each : summonsOf(master)) {
            if (each instanceof Summon summon && name.trim().equals(summon.getName())) {
                return summon;
            }
        }
        if (master.isDeath()) {
            throw new IllegalArgumentException(
                    "A dead character cannot summon a memosprite (" + master.getName() + ")");
        }
        Summon summoned = SummonFactory.memosprite(master, spec);
        summoned.setMaster(master);
        battle.addCombatant(summoned);
        onSummoned.accept(summoned);
        return summoned;
    }

    /**
     * Brings <b>one more instance</b> of a character's memosprite onto the field - 「「晴空乐手」全员登台」 means several
     * instances of the SAME memosprite stand at once (measured from the recorded fight: the vulnerability aura's number
     * matches two of them, and the servants' own row prices 1/2/3 members separately).
     *
     * <p>Unlike {@link #memosprite(Character)}, this never answers an existing one: a second call is the second instance.
     *
     * @param master the summoning character
     * @return the newly placed instance
     */
    public Summon memospriteInstance(Character master) {
        if (master == null) {
            throw new IllegalArgumentException("A memosprite needs a summoner");
        }
        if (master.isDeath()) {
            throw new IllegalArgumentException(
                    "A dead character cannot summon a memosprite (" + master.getName() + ")");
        }
        Summon summoned = SummonFactory.memosprite(master, battle::partyResourceValue);
        summoned.setMaster(master);
        battle.addCombatant(summoned);
        onSummoned.accept(summoned);
        return summoned;
    }

    /**
     * Brings a character's servant onto the field.
     *
     * @param master the summoning character
     * @return the servant, already placed
     */
    public Summon servant(Character master) {
        if (master == null) {
            throw new IllegalArgumentException("A servant needs a summoner");
        }
        if (master.isDeath()) {
            throw new IllegalArgumentException(
                    "A dead character cannot summon a servant (" + master.getName() + "): it would enter "
                            + "already orphaned, and the very next removeDeadCombatants would take it "
                            + "straight back out");
        }
        Summon servant = SummonFactory.servant(master, battle::partyResourceValue);
        servant.setMaster(master);
        battle.addCombatant(servant);
        onSummoned.accept(servant);
        return servant;
    }

    /**
     * The master's living memosprite on the field, or {@code null}.
     *
     * @param master the summoner
     * @return the living memosprite, or {@code null}
     */
    public Summon memospriteOf(CanHit master) {
        for (CanHit unit : battle.allies()) {
            if (unit instanceof Summon summon && summon.getMaster() == master && !summon.isDeath()) {
                return summon;
            }
        }
        return null;
    }

    /**
     * Every living summon this unit owns, looking in its own camp, in roster order.
     *
     * @param master the summoner (may be {@code null})
     * @return the living summons, never {@code null}
     */
    public List<Summon> summonsOf(CanHit master) {
        if (master == null) {
            return List.of();
        }
        List<Summon> owned = new ArrayList<>();
        for (CanHit unit : campOf(master)) {
            if (unit instanceof Summon summon && summon.getMaster() == master && !summon.isDeath()) {
                owned.add(summon);
            }
        }
        return List.copyOf(owned);
    }

    /**
     * The first living summon this unit owns, or {@code null}.
     *
     * @param master the summoner (may be {@code null})
     * @return the first living summon, or {@code null}
     */
    public Summon summonOf(CanHit master) {
        List<Summon> owned = summonsOf(master);
        return owned.isEmpty() ? null : owned.getFirst();
    }

    /**
     * @param master the summoner (may be {@code null})
     * @return how many living summons it owns
     */
    public int summonCountOf(CanHit master) {
        return summonsOf(master).size();
    }

    /**
     * The roster a unit belongs to, by camp.
     */
    private List<CanHit> campOf(CanHit unit) {
        return unit.getCamp() == Camp.PLAYER ? battle.allies() : battle.enemies();
    }
}
