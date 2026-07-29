package com.laosun.aluminium;

import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.models.*;
import com.laosun.aluminium.models.Character;

import java.util.ArrayList;
import java.util.List;

public class Battle {
    public Queue queue;

    public Signal currentMove;

    public ArrayList<CanHit> addRequestItems = new ArrayList<>();

    public ArrayList<AdvanceRequest> advanceRequests = new ArrayList<>();

    private final ArrayList<SkillRequest> skillRequests = new ArrayList<>();

    public record AdvanceRequest(CanHit object, double rate) {
    }

    public record SkillRequest(Skill skill, CanHit object, List<CanHit> target) {
    }

    public Battle(List<Character> characterQueue, List<Enemy> enemyQueue) {
        queue = new Queue();
        queue.addCombatants(characterQueue);
        queue.addCombatants(enemyQueue);
        queue.initialize();
    }

    public void startBattle() {
        for (Signal signal : queue.snapshot()) {
            signal.getCanHit().onBattleStart(this);
        }
        processAddRequests();
        processAdvanceRequests();
    }

    public void stepForward() {
        queue.move();
        currentMove = queue.getCurrentActor();
    }

    public void beforeMove() {
        if (currentMove == null) {
            return;
        }
        currentMove.getCanHit().beforeMove();
    }

    public void performAction() {
        if (currentMove == null) {
            return;
        }

        if (currentMove.getCanHit().isDeath()) {
            return;
        }
        // TODO: Skill
    }

    public boolean useSkill(Skill skill, List<CanHit> target) {
        if (currentMove == null || skill == null || target == null) {
            return false;
        }
        CanHit user = currentMove.getCanHit();
        if (user.isDeath()) {
            return false;
        }
        skillRequest(skill, user, target);
        return true;
    }

    public void afterMove() {
        if (currentMove == null) {
            return;
        }

        CanHit actor = currentMove.getCanHit();

        if (actor.isDeath()) {
            queue.removeCombatant(actor);
        } else {
            queue.setTopZero();
        }

        currentMove = null;
        processSkillRequests();
        processAddRequests();
        processAdvanceRequests();
    }


    /**
     * Add CanHit request for avoiding CME
     *
     * @param canHit add request object
     */
    public void addRequest(CanHit canHit) {
        addRequestItems.add(canHit);
    }

    public void skillRequest(Skill skill, CanHit user, List<CanHit> target) {
        if (skill == null || user == null || target == null) {
            return;
        }
        skillRequests.add(new SkillRequest(skill, user, target));
    }

    private void processSkillRequests() {
        if (skillRequests.isEmpty()) {
            return;
        }
        List<SkillRequest> requests = new ArrayList<>(skillRequests);
        skillRequests.clear();

        for (SkillRequest req : requests) {
            if (req.object.isDeath()) {
                continue;
            }
            req.skill().execute(this, req.object(), req.target());
        }
    }

    private void processAddRequests() {
        if (addRequestItems.isEmpty()) {
            return;
        }
        for (CanHit canHit : addRequestItems) {
            queue.addCombatant(canHit);
        }
        addRequestItems.clear();
    }

    public void advanceRequest(CanHit canHit, double rate) {
        if (canHit != null && rate >= 0 && rate <= 1) {
            advanceRequests.add(new AdvanceRequest(canHit, rate));
        }
    }

    private void processAdvanceRequests() {
        if (advanceRequests.isEmpty()) {
            return;
        }
        for (AdvanceRequest advanceRequest : advanceRequests) {
            queue.advanceActionByPercent(advanceRequest.object, advanceRequest.rate);
        }
        advanceRequests.clear();
    }

    public List<Signal> getQueueSnapshot() {
        return queue.snapshot();
    }

    public void printBattle() {
        queue.printActionQueue();
    }
}
