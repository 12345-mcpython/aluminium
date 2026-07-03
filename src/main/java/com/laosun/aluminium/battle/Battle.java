package com.laosun.aluminium.battle;

import com.laosun.aluminium.Queue;
import com.laosun.aluminium.beans.Translate;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.Camp;
import com.laosun.aluminium.models.*;
import com.laosun.aluminium.models.Character;

import java.util.*;

/**
 * Turn-based battle engine with skill support.
 */
public class Battle {

    private final Queue queue;
    private final List<Character> playerTeam;
    private final List<Enemy> enemyTeam;
    private final Map<CanHit, List<Skill>> skillMap;
    private final Map<CanHit, Double> currentHp = new HashMap<>();
    private final Random random = new Random();

    private boolean isOver;
    private String winner;
    private boolean currentActorHasActed = true;
    private String lastLog;
    private String lastTargetName;
    private boolean lastTargetDead;

    public Battle(Queue playerQueue, Queue enemyQueue, Map<CanHit, List<Skill>> skillMap) {
        List<CanHit> all = new ArrayList<>();
        all.addAll(playerQueue.snapshot().stream().map(Signal::getCanHit).toList());
        all.addAll(enemyQueue.snapshot().stream().map(Signal::getCanHit).toList());
        this.queue = new Queue(all);

        this.playerTeam = all.stream().filter(c -> c instanceof Character).map(c -> (Character) c).toList();
        this.enemyTeam = all.stream().filter(c -> c instanceof Enemy).map(c -> (Enemy) c).toList();
        this.skillMap = skillMap;

        for (CanHit c : all) {
            DoubleValue health = c.getAttribute(AttributeType.HEALTH);
            currentHp.put(c, health != null ? health.get() : 0);
        }
    }

    // ─── lifecycle ─────────────────────────────────────────────────────

    public static Character createPlayer(String name, double health, double attack, double defence, double speed) {
        return Character.fromAttributes(new Translate(name, name), makeAttrs(health, attack, defence, speed));
    }

    public static Enemy createEnemy(String name, double health, double attack, double defence, double speed) {
        return new Enemy(name, Camp.ENEMY, makeAttrs(health, attack, defence, speed));
    }

    // ─── skills ────────────────────────────────────────────────────────

    public static DoubleValue[] makeAttrs(double health, double attack, double defence, double speed) {
        DoubleValue[] attrs = new DoubleValue[AttributeType.values().length];
        attrs[AttributeType.HEALTH.ordinal()] = new DoubleValue(health);
        attrs[AttributeType.ATTACK.ordinal()] = new DoubleValue(attack);
        attrs[AttributeType.DEFENCE.ordinal()] = new DoubleValue(defence);
        attrs[AttributeType.SPEED.ordinal()] = new DoubleValue(speed);
        return attrs;
    }

    public Result start() {
        queue.initialize();
        isOver = false;
        winner = null;
        currentActorHasActed = true;
        for (CanHit c : playerTeam) c.onBattleStart(this, c);
        for (CanHit c : enemyTeam) c.onBattleStart(this, c);
        return pushQueue();
    }

    public Result pushQueue() {
        if (isOver) return buildOverResult();
        if (!currentActorHasActed)
            throw new IllegalStateException("Current actor must act before pushing queue");
        queue.move();
        currentActorHasActed = false;
        CanHit actor = queue.getCurrentActor().getCanHit();
        if (isDead(actor)) {
            currentActorHasActed = true;
            return pushQueue();
        }
        actor.beforeMove(this, actor);
        return buildResult(actor, null);
    }

    public List<Skill> getSkills() {
        CanHit actor = queue.getCurrentActor().getCanHit();
        return actor != null ? skillMap.getOrDefault(actor, List.of()) : List.of();
    }

    // ─── internal ──────────────────────────────────────────────────────

    public Result useSkill(int skillIndex, List<Integer> targets) {
        CanHit actor = queue.getCurrentActor().getCanHit();
        if (!(actor instanceof Character))
            throw new IllegalStateException("useSkill() is for player characters only");
        return executeSkill(actor, getSkill(actor, skillIndex), targets);
    }

    public Result useRandomSkill() {
        CanHit actor = queue.getCurrentActor().getCanHit();
        if (!(actor instanceof Enemy))
            throw new IllegalStateException("useRandomSkill() is for enemies only");
        List<Skill> skills = skillMap.getOrDefault(actor, List.of());
        if (skills.isEmpty()) throw new IllegalStateException("No skills for " + actor.getName());
        Skill skill = skills.get(random.nextInt(skills.size()));
        List<Integer> targets = autoTarget(actor, skill);
        if (targets.isEmpty()) return buildOverResult();
        return executeSkill(actor, skill, targets);
    }

    public void printQueue() {
        queue.printActionQueue();
    }

    private Skill getSkill(CanHit actor, int index) {
        List<Skill> skills = skillMap.getOrDefault(actor, List.of());
        if (index < 0 || index >= skills.size())
            throw new IllegalArgumentException("Invalid skill index: " + index);
        return skills.get(index);
    }

    private List<Integer> autoTarget(CanHit actor, Skill skill) {
        List<Integer> result = new ArrayList<>();
        for (Skill.SkillEffect effect : skill.effects()) {
            switch (effect.scope()) {
                case SINGLE_ENEMY, THREE_ENEMIES -> {
                    List<Enemy> alive = aliveEnemies();
                    if (!alive.isEmpty()) result.add(enemyTeam.indexOf(alive.get(random.nextInt(alive.size()))));
                }
                case ALL_ENEMIES -> aliveEnemies().forEach(e -> result.add(enemyTeam.indexOf(e)));
                case SINGLE_ALLY, THREE_ALLIES -> {
                    List<Character> alive = alivePlayers();
                    if (!alive.isEmpty()) result.add(playerTeam.indexOf(alive.get(random.nextInt(alive.size()))));
                }
                case ALL_ALLIES -> alivePlayers().forEach(p -> result.add(playerTeam.indexOf(p)));
                case SELF -> result.add(actor instanceof Character a
                        ? playerTeam.indexOf(a) : enemyTeam.indexOf(actor));
            }
        }
        return result.stream().distinct().toList();
    }

    private Result executeSkill(CanHit actor, Skill skill, List<Integer> targetIndices) {
        List<String> logs = new ArrayList<>();
        List<CanHit> affected = new ArrayList<>();
        int consumedIndices = 0;

        for (Skill.SkillEffect effect : skill.effects()) {
            List<CanHit> targets = resolveTargets(actor, effect, targetIndices, consumedIndices);
            double value = calcValue(actor, effect);

            for (CanHit target : targets) {
                actor.onAttack(this, actor, List.of(target));
                target.onBeAttacked(this, target, actor);
                affected.add(target);

                switch (effect.effect()) {
                    case DAMAGE -> {
                        double defence = getAttr(target, AttributeType.DEFENCE);
                        double defenceFactor = 1.0 - defence / (defence + 200 + 10 * 80);
                        double damage = value * defenceFactor;
                        applyDamage(target, damage);
                        logs.add(String.format("%s -> %s: %.0f dmg", actor.getName(), target.getName(), damage));
                    }
                    case HEAL -> {
                        double maxHp = getAttr(target, AttributeType.HEALTH);
                        double newHp = Math.min(maxHp, getHp(target) + value);
                        currentHp.put(target, newHp);
                        logs.add(String.format("%s -> %s: +%.0f heal (HP: %.0f)", actor.getName(), target.getName(), value, newHp));
                    }
                    case ADVANCE -> {
                        double percent = Math.min(1.0, effect.multiplier());
                        queue.advanceActionByPercent(target, percent);
                        logs.add(String.format("%s advances %s by %.0f%%", actor.getName(), target.getName(), percent * 100));
                    }
                }
            }
            consumedIndices += effect.targetCount() > 0 ? effect.targetCount() : targets.size();
        }

        for (CanHit target : affected) {
            if (isDead(target)) {
                boolean revive = false;
                if (target instanceof Character) revive = !target.onCharacterKilled(this, actor, target);
                else if (target instanceof Enemy) revive = !target.onEnemyKilled(this, actor, target);
                if (revive) currentHp.put(target, 1.0);
                else queue.removeCombatant(target);
            }
        }

        queue.setTopZero();
        currentActorHasActed = true;
        checkOver();
        actor.afterMove(this, actor);

        lastLog = String.join(", ", logs);
        lastTargetName = affected.isEmpty() ? null : affected.getLast().getName();
        lastTargetDead = affected.stream().anyMatch(this::isDead);
        return buildResult(actor, skill);
    }

    private List<CanHit> resolveTargets(CanHit actor, Skill.SkillEffect effect, List<Integer> indices, int offset) {
        return switch (effect.scope()) {
            case SINGLE_ENEMY -> {
                int index = offset < indices.size() ? indices.get(offset) : -1;
                yield index >= 0 && index < enemyTeam.size() && !isDead(enemyTeam.get(index))
                        ? List.of(enemyTeam.get(index)) : List.of();
            }
            case THREE_ENEMIES -> {
                int center = offset < indices.size() ? indices.get(offset) : -1;
                yield blastTargets(enemyTeam, center);
            }
            case ALL_ENEMIES -> aliveEnemies().stream().map(e -> (CanHit) e).toList();
            case SINGLE_ALLY -> {
                int index = offset < indices.size() ? indices.get(offset) : -1;
                yield index >= 0 && index < playerTeam.size() && !isDead(playerTeam.get(index))
                        ? List.of(playerTeam.get(index)) : List.of();
            }
            case THREE_ALLIES -> {
                int center = offset < indices.size() ? indices.get(offset) : -1;
                yield blastTargets(playerTeam, center);
            }
            case ALL_ALLIES -> alivePlayers().stream().map(p -> (CanHit) p).toList();
            case SELF -> List.of(actor);
        };
    }

    private <T extends CanHit> List<CanHit> blastTargets(List<T> team, int center) {
        List<CanHit> result = new ArrayList<>();
        for (int i = center - 1; i <= center + 1; i++) {
            if (i >= 0 && i < team.size() && !isDead(team.get(i)))
                result.add(team.get(i));
        }
        return result;
    }

    private double calcValue(CanHit actor, Skill.SkillEffect effect) {
        double base = switch (effect.scale()) {
            case ATK -> getAttr(actor, AttributeType.ATTACK);
            case DEF -> getAttr(actor, AttributeType.DEFENCE);
            case HP -> getAttr(actor, AttributeType.HEALTH);
        };
        return base * effect.multiplier();
    }

    private void applyDamage(CanHit target, double damage) {
        currentHp.put(target, Math.max(0, getHp(target) - damage));
    }

    // ─── helpers ───────────────────────────────────────────────────────

    private void checkOver() {
        if (alivePlayers().isEmpty()) {
            isOver = true;
            winner = "enemy";
        }
        if (aliveEnemies().isEmpty()) {
            isOver = true;
            winner = "player";
        }
    }

    private Result buildResult(CanHit actor, Skill used) {
        String type = actor instanceof Character ? "player" : "enemy";
        List<Skill> skills = isOver ? List.of() : skillMap.getOrDefault(actor, List.of());
        return new Result(isOver, winner, isOver ? null : actor, type, skills,
                lastLog, lastTargetName, lastTargetDead,
                teamSnapshot(playerTeam), teamSnapshot(enemyTeam));
    }

    private Result buildOverResult() {
        checkOver();
        return new Result(true, winner, null, null, List.of(), null, null, false,
                teamSnapshot(playerTeam), teamSnapshot(enemyTeam));
    }

    private double getHp(CanHit c) {
        return currentHp.getOrDefault(c, 0.0);
    }

    private boolean isDead(CanHit c) {
        return getHp(c) <= 0;
    }

    private double getAttr(CanHit c, AttributeType type) {
        var value = c.getAttribute(type);
        return value != null ? value.get() : 0;
    }

    // ─── records ───────────────────────────────────────────────────────

    private List<Character> alivePlayers() {
        return playerTeam.stream().filter(c -> !isDead(c)).toList();
    }

    private List<Enemy> aliveEnemies() {
        return enemyTeam.stream().filter(c -> !isDead(c)).toList();
    }

    // ─── factory ───────────────────────────────────────────────────────

    private List<TeamSnapshot> teamSnapshot(List<? extends CanHit> team) {
        return team.stream().map(c -> new TeamSnapshot(
                c.getName(), getHp(c), getAttr(c, AttributeType.HEALTH), !isDead(c))).toList();
    }

    public record Result(
            boolean over, String winner,
            CanHit currentActor, String actorType, List<Skill> skills,
            String log, String targetName, boolean targetDead,
            List<TeamSnapshot> playerTeam, List<TeamSnapshot> enemyTeam) {
    }

    public record TeamSnapshot(String name, double currentHp, double maxHp, boolean alive) {
    }
}
