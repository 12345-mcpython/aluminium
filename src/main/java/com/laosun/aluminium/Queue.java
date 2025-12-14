package com.laosun.aluminium;

import com.laosun.aluminium.enums.Camp;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Moveable;
import lombok.Getter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 战斗队列：基于速度计算行动顺序，支持动态调整（模仿HSR回合制）
 */
@Getter
@ToString
public final class Queue {
    private static final double ACTION_THRESHOLD = 10000.0; // 行动所需长度阈值
    private final List<CanHit> combatants = new ArrayList<>(); // 所有战斗角色（统一管理）
    private final List<Moveable> actionQueue = new ArrayList<>(); // 排序后的行动队列

    // 构造器
    public Queue() {}
    public Queue(List<CanHit> initialCombatants) {
        addCombatants(initialCombatants);
    }

    // ------------------- 角色管理 -------------------
    public void addCombatant(CanHit combatant) {
        if (combatant != null && !combatants.contains(combatant)) {
            combatants.add(combatant);
            actionQueue.add(combatant);
            calcActionTimes(); // 新增角色后重新排序
        }
    }

    public void addCombatants(List<CanHit> combatants) {
        combatants.forEach(this::addCombatant);
    }

    public void removeCombatant(CanHit combatant) {
        combatants.remove(combatant);
        actionQueue.remove(combatant);
    }

    // ------------------- 队列计算 -------------------
    /** 初始化队列：重置所有角色的行动长度和时间 */
    public void initialize() {
        actionQueue.forEach(m -> {
            m.setLength(0);
            m.setTime(0);
        });
        calcActionTimes();
    }

    /** 计算所有角色的下次行动时间 */
    public void calcActionTimes() {
        actionQueue.forEach(moveable -> {
            if (moveable.getSpeed() <= 0) {
                moveable.setTime(Double.MAX_VALUE); // 速度为0无法行动
                return;
            }
            // 下次行动时间 = 剩余所需长度 / 速度
            double remaining = ACTION_THRESHOLD - moveable.getLength();
            moveable.setTime(remaining / moveable.getSpeed());
        });
        // 按行动时间升序排序（时间越短越先行动）
        actionQueue.sort(Comparator.comparingDouble(Moveable::getTime));
    }

    /** 推进时间到下一个角色行动 */
    public double progressTime() {
        if (actionQueue.isEmpty()) return 0;
        Moveable next = actionQueue.getFirst();
        double timePassed = next.getTime();

        // 所有角色积累行动长度 = 时间 * 速度
        actionQueue.forEach(m -> {
            if (m.getSpeed() > 0) {
                m.setLength(Math.min(ACTION_THRESHOLD, m.getLength() + timePassed * m.getSpeed()));
            }
        });
        calcActionTimes();
        return timePassed;
    }

    // ------------------- 工具方法 -------------------
    /** 获取下一个行动的角色 */
    public CanHit getNextCombatant() {
        return actionQueue.stream()
                .filter(m -> m instanceof CanHit)
                .map(m -> (CanHit) m)
                .findFirst()
                .orElse(null);
    }

    /** 角色行动后重置行动长度 */
    public void resetCombatantLength(CanHit combatant) {
        if (combatant != null && actionQueue.contains(combatant)) {
            combatant.setLength(0);
            calcActionTimes();
        }
    }

    /** 根据阵营获取存活角色 */
    public List<CanHit> getAliveByCamp(Camp camp) {
        return combatants.stream()
                .filter(CanHit::isAlive)
                .filter(c -> c.getCamp() == camp)
                .collect(Collectors.toList());
    }

    /** 检查阵营是否全灭 */
    public boolean isCampWiped(Camp camp) {
        return getAliveByCamp(camp).isEmpty();
    }

    /** 动态调整角色速度（如buff效果） */
    public void adjustSpeed(CanHit combatant, double delta) {
        if (combatant != null) {
            combatant.setSpeed(Math.max(1, combatant.getSpeed() + delta)); // 速度最低为1
            calcActionTimes();
        }
    }

    /** 打印队列状态（调试用） */
    public void printStatus() {
        System.out.println("\n=== STATUS ===");
        System.out.printf("%-10s %-8s %-8s %-8s %-8s%n",
                "CHARACTER NAME", "CAMP", "HEALTH", "LENGTH", "TIME");
        actionQueue.forEach(m -> {
            if (m instanceof CanHit c) {
                System.out.printf("%-10s %-8s %-8.0f %-8.0f %-8.1f%n",
                        c.getName(), c.getCamp().name(), c.getInBattleHealth(),
                        c.getLength(), c.getTime());
            }
        });
        System.out.println("====================\n");
    }
}