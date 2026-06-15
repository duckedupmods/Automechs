package com.duckedupmods.automechs.entity;

import net.neoforged.neoforge.energy.EnergyStorage;

/**
 * Energy buffer for a mech. It can be charged from outside (a Charging Pad pushes FE in via
 * {@code receiveEnergy}) but does not expose extraction — the mech spends energy internally for its
 * own work via {@link #consume(int)}.
 */
public class MechEnergyStorage extends EnergyStorage {

    public MechEnergyStorage(int capacity, int maxReceive) {
        super(capacity, maxReceive, 0);
    }

    /** Directly set stored energy (used when loading from save data), clamped to capacity. */
    public void setEnergy(int value) {
        this.energy = Math.max(0, Math.min(this.capacity, value));
    }

    /** Resize the buffer (the Capacity upgrade), keeping stored energy within the new bound. */
    public void setCapacity(int capacity) {
        this.capacity = Math.max(1, capacity);
        if (this.energy > this.capacity) {
            this.energy = this.capacity;
        }
    }

    /** Spend energy for the mech's own work. Returns false (and spends nothing) if insufficient. */
    public boolean consume(int amount) {
        if (amount <= 0) {
            return true;
        }
        if (this.energy < amount) {
            return false;
        }
        this.energy -= amount;
        return true;
    }
}
