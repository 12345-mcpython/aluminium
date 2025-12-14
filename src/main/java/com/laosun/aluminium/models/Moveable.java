package com.laosun.aluminium.models;

import com.laosun.aluminium.models.event.MoveableEvent;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 可行动角色基类：管理行动长度、时间、速度
 */
@Getter
@Setter
@ToString
public abstract class Moveable implements MoveableEvent, Comparable<Moveable> {
    private double length; // 行动长度（积累到10000触发行动）
    private double time;   // 下次行动时间
    private double speed;  // 行动速度（影响长度积累速度）

    public Moveable(double speed) {
        this.length = 0;
        this.time = 0;
        this.speed = speed;
    }

    /** 移动（积累行动长度） */
    public void move(double length) {
        this.length += length;
    }

    /** 比较器：按下次行动时间升序排序 */
    @Override
    public int compareTo(Moveable o) {
        return Double.compare(this.time, o.getTime());
    }
}