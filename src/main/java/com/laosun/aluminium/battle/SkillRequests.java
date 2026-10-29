package com.laosun.aluminium.battle;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.CanHit;
import com.laosun.aluminium.skill.Skill;

import java.util.ArrayList;
import java.util.List;

/**
 * Skills waiting to be settled by the next {@code processRequests}.
 *
 * <p><b>Why this is separate.</b> "Request a skill" and "settle the queued skills" are one queue, and
 * the settle step is where the death check lives. Keeping the list next to the turn loop made the
 * queue look like turn state; here it is a small buffer with one drain operation.
 */
public final class SkillRequests {

    private final Battle battle;
    private final List<Battle.SkillRequest> pending = new ArrayList<>();

    /**
     * @param battle the battle the queued skills execute in
     */
    public SkillRequests(Battle battle) {
        this.battle = battle;
    }

    /**
     * Queues one skill; a null skill/user/target is ignored.
     *
     * @param skill  the skill
     * @param user   who casts it
     * @param target its targets
     */
    public void add(Skill skill, CanHit user, List<? extends CanHit> target) {
        if (skill == null || user == null || target == null) {
            return;
        }
        pending.add(new Battle.SkillRequest(skill, user, target));
    }

    /**
     * Executes everything queued, skipping requests whose caster died in the meantime.
     */
    public void settle() {
        if (pending.isEmpty()) {
            return;
        }
        List<Battle.SkillRequest> requests = new ArrayList<>(pending);
        pending.clear();
        for (Battle.SkillRequest request : requests) {
            if (request.object().isDeath()) {
                continue;
            }
            request.skill().execute(battle, request.object(), request.target());
        }
    }
}
