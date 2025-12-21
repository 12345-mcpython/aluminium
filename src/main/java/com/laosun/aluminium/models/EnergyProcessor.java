package com.laosun.aluminium.models;

import com.laosun.aluminium.models.event.CanHitEvent;
import com.laosun.aluminium.models.event.MoveableEvent;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

// TODO: write it!
@AllArgsConstructor
@Getter
@Setter
public abstract class EnergyProcessor implements MoveableEvent, CanHitEvent {
    private double energy;
    private double maxEnergy;
}
