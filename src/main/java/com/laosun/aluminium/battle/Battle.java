package com.laosun.aluminium.battle;

import com.laosun.aluminium.Queue;
import com.laosun.aluminium.beans.Translate;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.Camp;
import com.laosun.aluminium.models.*;
import com.laosun.aluminium.models.Character;

import java.util.*;

/**
 * Minimal turn-based battle engine.
 *
 * <pre>{@code
 * Battle b = new Battle(playerQueue, enemyQueue);
 * Result r = b.start();
 * r = b.pushQueue();           // advance to next actor
 * r = b.attack(0);             // player attacks enemy[0]
 * r = b.pushQueue();
 * r = b.attackRandom();        // enemy attacks random player
 * }</pre>
 */
public class Battle {

    private final Queue queue;
    private final List<Character> playerTeam;
    private final List<Enemy> enemyTeam;
    private final Map<CanHit, Double> currentHp = new HashMap<>();
    private final Random rng = new Random();

    private boolean over;
    private String winner;
    private boolean currentActorHasActed = true;

    public Battle(Queue playerQueue, Queue enemyQueue) {
        List<CanHit> all = new ArrayList<>();
        all.addAll(playerQueue.snapshot().stream().map(Signal::getCanHit).toList());
        all.addAll(enemyQueue.snapshot().stream().map(Signal::getCanHit).toList());
        this.queue = new Queue(all);

        this.playerTeam = all.stream().filter(c -> c instanceof Character).map(c -> (Character) c).toList();
        this.enemyTeam = all.stream().filter(c -> c instanceof Enemy).map(c -> (Enemy) c).toList();

        for (CanHit c : all) {
            DoubleValue dv = c.getAttribute(AttributeType.HEALTH);
            currentHp.put(c, dv != null ? dv.get() : 0);
        }
    }

    // ─── lifecycle ─────────────────────────────────────────────────────

    public Result start() {
        queue.initialize();
        over = false;
        winner = null;
        currentActorHasActed = true;
        return pushQueue();
    }

    public Result pushQueue() {
        if (over) return buildOverResult();
        if (!currentActorHasActed) {
            throw new IllegalStateException("Current actor must act before pushing queue");
        }
        queue.move();
        currentActorHasActed = false;

        CanHit actor = queue.getCurrentActor().getCanHit();
        if (isDead(actor)) {
            currentActorHasActed = true;
            return pushQueue();
        }
        return buildResult(actor, 0, null);
    }

    // ─── actions ───────────────────────────────────────────────────────

    public Result attack(int enemyIndex) {
        CanHit actor = queue.getCurrentActor().getCanHit();
        if (!(actor instanceof Character)) {
            throw new IllegalStateException("attack() is for player characters only");
        }
        if (enemyIndex < 0 || enemyIndex >= enemyTeam.size()) {
            throw new IllegalArgumentException("Invalid enemy index: " + enemyIndex);
        }
        CanHit target = enemyTeam.get(enemyIndex);
        if (isDead(target)) {
            throw new IllegalArgumentException("Target is already dead: " + target.getName());
        }
        return executeAction(actor, target);
    }

    public Result attackRandom() {
        CanHit actor = queue.getCurrentActor().getCanHit();
        if (!(actor instanceof Enemy)) {
            throw new IllegalStateException("attackRandom() is for enemies only");
        }
        List<Character> alive = alivePlayers();
        if (alive.isEmpty()) return buildOverResult();
        return executeAction(actor, alive.get(rng.nextInt(alive.size())));
    }

    // ─── internal ──────────────────────────────────────────────────────

    private Result executeAction(CanHit actor, CanHit target) {
        double dmg = calcDamage(actor, target);
        applyDamage(target, dmg);
        if (isDead(target)) {
            queue.removeCombatant(target);
        }
        queue.setTopZero();
        currentActorHasActed = true;
        checkOver();
        return buildResult(actor, dmg, target);
    }

    private double calcDamage(CanHit attacker, CanHit defender) {
        double atk = getAttr(attacker, AttributeType.ATTACK);
        double def = getAttr(defender, AttributeType.DEFENCE);
        double defFactor = 1.0 - def / (def + 200 + 10 * 80);
        return atk * defFactor;
    }

    private void applyDamage(CanHit target, double damage) {
        currentHp.put(target, Math.max(0, getHp(target) - damage));
    }

    private void checkOver() {
        if (alivePlayers().isEmpty()) { over = true; winner = "enemy"; }
        if (aliveEnemies().isEmpty())  { over = true; winner = "player"; }
    }

    private Result buildResult(CanHit actor, double dmg, CanHit target) {
        return new Result(
                over, winner,
                over ? null : actor,
                over ? null : (actor instanceof Character ? "player" : "enemy"),
                dmg,
                target != null ? target.getName() : null,
                target != null && isDead(target),
                teamSnapshot(playerTeam),
                teamSnapshot(enemyTeam));
    }

    private Result buildOverResult() {
        checkOver();
        return new Result(true, winner, null, null, 0, null, false,
                teamSnapshot(playerTeam), teamSnapshot(enemyTeam));
    }

    // ─── helpers ───────────────────────────────────────────────────────

    private double getHp(CanHit c)     { return currentHp.getOrDefault(c, 0.0); }
    private boolean isDead(CanHit c)   { return getHp(c) <= 0; }

    private double getAttr(CanHit c, AttributeType type) {
        var dv = c.getAttribute(type);
        return dv != null ? dv.get() : 0;
    }

    private List<Character> alivePlayers() { return playerTeam.stream().filter(c -> !isDead(c)).toList(); }
    private List<Enemy> aliveEnemies()     { return enemyTeam.stream().filter(c -> !isDead(c)).toList(); }

    private List<TeamSnapshot> teamSnapshot(List<? extends CanHit> team) {
        return team.stream().map(c -> new TeamSnapshot(
                c.getName(), getHp(c), getAttr(c, AttributeType.HEALTH), !isDead(c))).toList();
    }

    // ─── nested records ────────────────────────────────────────────────

    public record Result(
            boolean over, String winner,
            CanHit currentActor, String actorType,
            double damage, String targetName, boolean targetDead,
            List<TeamSnapshot> playerTeam, List<TeamSnapshot> enemyTeam) {}

    public record TeamSnapshot(String name, double currentHp, double maxHp, boolean alive) {}

    // ─── factory ───────────────────────────────────────────────────────

    public static Character createPlayer(String name, double hp, double atk, double def, double spd) {
        return Character.fromAttributes(new Translate(name, name), makeAttrs(hp, atk, def, spd));
    }

    public static Enemy createEnemy(String name, double hp, double atk, double def, double spd) {
        return new Enemy(name, Camp.ENEMY, makeAttrs(hp, atk, def, spd));
    }

    private static DoubleValue[] makeAttrs(double hp, double atk, double def, double spd) {
        DoubleValue[] attrs = new DoubleValue[AttributeType.values().length];
        attrs[AttributeType.HEALTH.ordinal()] = new DoubleValue(hp);
        attrs[AttributeType.ATTACK.ordinal()] = new DoubleValue(atk);
        attrs[AttributeType.DEFENCE.ordinal()] = new DoubleValue(def);
        attrs[AttributeType.SPEED.ordinal()] = new DoubleValue(spd);
        return attrs;
    }
}
