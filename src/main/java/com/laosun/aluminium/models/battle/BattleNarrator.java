package com.laosun.aluminium.models.battle;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.models.entity.CanHit;
import com.laosun.aluminium.models.entity.Character;
import com.laosun.aluminium.models.Signal;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.skill.Skill;

import java.util.ArrayList;
import java.util.List;

/**
 * Prints a driven battle as it happens: what each unit chose, and what changed.
 *
 * <p><b>Why this is a listener rather than part of the driver.</b> {@link BattleDriver} owns the turn cycle, so a
 * front end can no longer run its own loop around it -- but a front end still has to narrate. Implementing
 * {@link BattleListener} keeps both halves honest: the driver decides nothing about presentation, and this class
 * cannot skip a turn, re-time the action bar, or change a number. Every method is a notification and none returns
 * a value the driver acts on.
 *
 * <p><b>What it reports, and why that is the interesting part.</b> A turn announces the actor's state <i>before</i>
 * it acts, then the decision, then what moved by the end of that same turn. So the reader can see that the damage,
 * the energy and the action bar all changed because of something the policy asked for -- the numbers are read
 * straight off the battle rather than recomputed here.
 *
 * <p><b>Attribution is deliberately not guessed.</b> It reports a per-turn before/after instead of naming which hit
 * did what: an ultimate, a follow-up, a damage over time tick and a break can all settle inside one turn, so a
 * narrator that blamed the most recent action would be inventing a causal claim the battle never made.
 */
public class BattleNarrator implements BattleListener {

    private static final String RULE = "─".repeat(78);

    /**
     * Where the narration goes.
     *
     * <p>Injected rather than always {@code System.out} so a test can capture it without swapping the global
     * stream, which would be visible to every other test running in the same JVM.
     */
    private final java.io.PrintStream out;

    /**
     * Narrates to standard output.
     */
    public BattleNarrator() {
        this(System.out);
    }

    /**
     * Narrates to the given stream.
     *
     * @param out where to write; {@code null} means standard output
     */
    public BattleNarrator(java.io.PrintStream out) {
        this.out = out == null ? System.out : out;
    }

    /**
     * One unit's numbers, so the end of a turn can show what moved.
     *
     * @param unit      the unit
     * @param hp        current HP
     * @param energy    current energy
     * @param toughness current toughness, or {@code -1} for a unit that cannot be broken
     */
    private record Snapshot(CanHit unit, double hp, double energy, double toughness) {
    }

    /**
     * The battle being narrated. {@link CanHit} does not know its own battle, so the driver is the one that says.
     */
    private Battle battle;

    /**
     * The units' numbers at the end of the previous turn.
     */
    private final List<Snapshot> previous = new ArrayList<>();

    /**
     * Whether the current turn produced a cast, so the turn's report can say it did not.
     */
    private boolean actedThisTurn;

    /**
     * Prints the opening state: both camps, and every unit's numbers.
     *
     * @param driver the driver, started
     */
    @Override
    public void onBattleStart(BattleDriver driver) {
        battle = driver.battle();
        StringBuilder sb = new StringBuilder();
        sb.append("=".repeat(78)).append('\n');
        sb.append("Battle — driven headlessly by BattleDriver\n");
        sb.append("=".repeat(78)).append('\n');
        sb.append("Party   ").append(names(battle.characters())).append('\n');
        sb.append("Enemies ").append(names(battle.enemyUnits())).append('\n');
        sb.append('\n');
        for (Character hero : battle.characters()) {
            sb.append(String.format("[Ally]  %-14s HP %s/%s  ATK %s  SPD %s  Energy %s/%s%n",
                    hero.getName(), num(hero.getCurrentHp()), num(hero.getMaxHp()),
                    num(attr(hero, AttributeType.ATTACK)), num(attr(hero, AttributeType.SPEED)),
                    num(hero.getCurrentEnergy()), num(hero.getMaxEnergy())));
        }
        for (Enemy enemy : battle.enemyUnits()) {
            sb.append(String.format("[Enemy] %-14s HP %s/%s  ATK %s  SPD %s  Toughness %s/%s  Weak %s%n",
                    enemy.getName(), num(enemy.getCurrentHp()), num(enemy.getMaxHp()),
                    num(attr(enemy, AttributeType.ATTACK)), num(attr(enemy, AttributeType.SPEED)),
                    num(enemy.getStance()), num(enemy.getMaxStance()), enemy.getStanceWeak()));
        }
        out.print(sb);
        remember();
    }

    /**
     * Announces the turn, with the actor's state as it enters it.
     *
     * @param actor the unit whose turn it is
     * @param turn  1-based actor-turn count
     */
    @Override
    public void onTurnStart(CanHit actor, int turn) {
        actedThisTurn = false;
        out.println();
        out.println(RULE);
        out.printf("── Turn %d (round %d) — %s%n", turn, battle == null ? 0 : battle.getRound(),
                actor.getName());
        out.println("── before: " + describe(actor));
        out.println("── action bar: " + actionBar());
    }

    /**
     * Reports what the policy chose.
     *
     * @param actor    the acting unit
     * @param decision the choice
     */
    @Override
    public void onAction(CanHit actor, SkillPolicy.Decision decision) {
        actedThisTurn = true;
        String targets = String.join(", ", decision.targets().stream().map(CanHit::getName).toList());
        String note = decision.note() == null || decision.note().isBlank() ? "" : "  (" + decision.note() + ")";
        out.printf("   casts %s → %s%s%n", skillName(decision.skill()), targets, note);
    }

    /**
     * Reports what moved during the turn, and the action bar after it.
     *
     * @param actor the unit whose turn it was
     * @param turn  the 1-based turn number
     */
    @Override
    public void onTurnEnd(CanHit actor, int turn) {
        if (!actedThisTurn) {
            out.println("   (no action — dead, controlled, or nothing legal to cast)");
        }

        List<Snapshot> now = new ArrayList<>();
        for (CanHit unit : everyone()) {
            now.add(snapshot(unit));
        }
        for (Snapshot after : now) {
            Snapshot before = find(after.unit());
            if (before == null) {
                continue;
            }
            out.println("   · " + after.unit().getName() + " " + delta(before, after));
        }

        out.println("   action bar: " + actionBar());
        previous.clear();
        previous.addAll(now);
    }

    /**
     * Prints how the battle ended, with the final state of both camps.
     *
     * @param result the outcome
     */
    @Override
    public void onBattleEnd(BattleDriver.Result result) {
        out.println();
        out.println(RULE);
        for (Enemy enemy : battle.enemyUnits()) {
            out.printf("Final: %-14s HP %s/%s%s%n", enemy.getName(),
                    num(enemy.getCurrentHp()), num(enemy.getMaxHp()),
                    enemy.isDeath() ? "  [dead]" : "");
        }
        for (Character hero : battle.characters()) {
            out.printf("Final: %-14s HP %s/%s%s%n", hero.getName(),
                    num(hero.getCurrentHp()), num(hero.getMaxHp()),
                    hero.isDeath() ? "  [dead]" : "");
        }
        out.printf("Result: %s — %s, %d actor-turns, %d rounds%n",
                result.outcome(), result.status(), result.turns(), result.rounds());
        out.println(RULE);
    }

    /**
     * What one unit's numbers did between two snapshots, as a readable phrase.
     *
     * @param before the earlier snapshot
     * @param after  the later snapshot
     * @return the phrase
     */
    private static String delta(Snapshot before, Snapshot after) {
        List<String> parts = new ArrayList<>();
        double hp = after.hp() - before.hp();
        if (Math.abs(hp) >= 0.5) {
            parts.add(String.format("HP %s→%s (%s%s)", num(before.hp()), num(after.hp()),
                    hp > 0 ? "+" : "", num(hp)));
        }
        double energy = after.energy() - before.energy();
        if (Math.abs(energy) >= 0.5) {
            parts.add(String.format("energy %s→%s", num(before.energy()), num(after.energy())));
        }
        if (before.toughness() >= 0 && Math.abs(after.toughness() - before.toughness()) >= 0.5) {
            parts.add(String.format("toughness %s→%s", num(before.toughness()), num(after.toughness())));
        }
        if (after.unit().isDeath() && !before.unit().isDeath()) {
            parts.add("DEFEATED");
        }
        return parts.isEmpty() ? "unchanged" : String.join("; ", parts);
    }

    /**
     * One line describing a unit's live state.
     *
     * @param unit the unit
     * @return the description
     */
    private static String describe(CanHit unit) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("HP %s/%s", num(unit.getCurrentHp()), num(unit.getMaxHp())));
        if (unit.hasEnergyBar()) {
            sb.append(String.format("  energy %s/%s", num(unit.getCurrentEnergy()), num(unit.getMaxEnergy())));
        }
        if (unit instanceof Enemy enemy && enemy.hasToughnessBar()) {
            sb.append(String.format("  toughness %s/%s", num(enemy.getStance()), num(enemy.getMaxStance())));
        }
        if (unit instanceof Character) {
            sb.append(String.format("  shield %s", num(unit.getShield())));
        }
        return sb.toString();
    }

    /**
     * The action bar, soonest first.
     *
     * @return the rendering
     */
    private String actionBar() {
        if (battle == null || battle.getQueueSnapshot() == null) {
            return "(none)";
        }
        List<String> parts = new ArrayList<>();
        for (Signal signal : battle.getQueueSnapshot()) {
            if (signal.getCanHit() == null) {
                continue;
            }
            parts.add(signal.getCanHit().getName() + "(" + num(battle.queue().getTimeRemaining(signal)) + ")");
        }
        return String.join(" → ", parts);
    }

    /**
     * Every unit on the field, both camps.
     *
     * @return the units
     */
    private List<CanHit> everyone() {
        List<CanHit> all = new ArrayList<>();
        if (battle == null) {
            return all;
        }
        all.addAll(battle.characters());
        all.addAll(battle.enemyUnits());
        return all;
    }

    /**
     * Records every unit's numbers, as the baseline for the next turn's report.
     */
    private void remember() {
        previous.clear();
        for (CanHit unit : everyone()) {
            previous.add(snapshot(unit));
        }
    }

    /**
     * A unit's current numbers.
     *
     * @param unit the unit
     * @return the snapshot
     */
    private static Snapshot snapshot(CanHit unit) {
        double toughness = unit instanceof Enemy enemy && enemy.hasToughnessBar() ? enemy.getStance() : -1;
        return new Snapshot(unit, unit.getCurrentHp(), unit.getCurrentEnergy(), toughness);
    }

    /**
     * The recorded snapshot for a unit, matched by identity.
     *
     * @param unit the unit
     * @return the snapshot, or {@code null}
     */
    private Snapshot find(CanHit unit) {
        for (Snapshot s : previous) {
            if (s.unit() == unit) {
                return s;
            }
        }
        return null;
    }

    /**
     * An attribute's current value, or {@code NaN} when the unit has no such attribute.
     *
     * @param unit the unit
     * @param type the attribute
     * @return the value
     */
    private static double attr(CanHit unit, AttributeType type) {
        var value = unit.getAttribute(type);
        return value == null ? Double.NaN : value.get();
    }

    /**
     * A skill's display label.
     *
     * <p>Built from the category and the skill's own id, because the data carries <b>no skill name</b>:
     * {@code Skill} exposes only {@code getCid()} / {@code getSkillSlot()} / {@code getData()}, and the data's
     * {@code skillType} is a category ("Normal", "BPSkill", ...) rather than a display name. Printing the id keeps
     * the output checkable against {@code skills.json}, which a missing or guessed name would not be.
     *
     * @param skill the skill
     * @return the label
     */
    private static String skillName(Skill skill) {
        if (skill == null) {
            return "(no skill)";
        }
        String category = skill.getData() == null ? "?" : String.valueOf(skill.getData().getCategory());
        return "[" + category + " " + skill.getCid() + "]";
    }

    /**
     * Comma-separated names.
     *
     * @param units the units
     * @return the list
     */
    private static String names(List<? extends CanHit> units) {
        List<String> out = new ArrayList<>();
        for (CanHit unit : units) {
            out.add(unit.getName());
        }
        return String.join(" / ", out);
    }

    /**
     * A number without a pointless decimal tail.
     *
     * @param value the number
     * @return the rendering
     */
    private static String num(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return String.valueOf(value);
        }
        if (value == Math.rint(value) && Math.abs(value) < 1e15) {
            return String.valueOf((long) value);
        }
        return String.format("%.1f", value);
    }
}
