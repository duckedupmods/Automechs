package com.duckedupmods.automechs.block.entity;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

import com.duckedupmods.automechs.block.PowerConduitBlock;
import com.duckedupmods.automechs.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;

/**
 * Backs the Power Conduit. Conduits are <em>bufferless wires</em>: they never store FE. When energy is
 * pushed into any conduit (by the Combustion Dynamo, the Charging Pad, or any other mod's cable/generator),
 * that conduit floods the whole connected conduit network with a breadth-first search, collects every
 * machine on it that can accept power, and delivers the energy directly to those machines in a single pass.
 *
 * <p>Because the network is recomputed from scratch on every push, the system is inherently robust to
 * topology changes: breaking a cable simply makes the next flood stop at the gap (nothing is lost, because
 * conduits hold no energy), and placing one reconnects instantly. This mirrors how real transmitter-network
 * mods move power, without the fragility of per-tile buffer diffusion.
 *
 * <p>The "network distributes in one pass" approach is inspired by Mekanism's transmitter networks
 * (MIT-licensed, https://github.com/mekanism/Mekanism). This is an original, much simpler implementation
 * — no code is copied — but credit to Mekanism for the design idea.
 */
public class PowerConduitBlockEntity extends BlockEntity {

    /** Max FE a single conduit node will pass through per push. */
    public static final int MAX_THROUGHPUT = 5_000;
    /** Safety cap on how many conduits a single flood will walk (runaway-network backstop). */
    private static final int MAX_NETWORK = 4_096;
    /** Ticks the conduit stays visually "lit" after the last energy moved (debounces the glow). */
    private static final int LIT_TICKS = 5;

    /** Pass-through energy face: accepts FE and immediately routes it across the network; stores nothing. */
    private final class PassThrough implements IEnergyStorage {
        @Override
        public int receiveEnergy(int toReceive, boolean simulate) {
            if (level == null || level.isClientSide || toReceive <= 0) {
                return 0;
            }
            return distribute(Math.min(toReceive, MAX_THROUGHPUT), simulate);
        }

        @Override
        public int extractEnergy(int toExtract, boolean simulate) {
            return 0;
        }

        @Override
        public int getEnergyStored() {
            return 0;
        }

        @Override
        public int getMaxEnergyStored() {
            return MAX_THROUGHPUT;
        }

        @Override
        public boolean canExtract() {
            return false;
        }

        @Override
        public boolean canReceive() {
            return true;
        }
    }

    private final PassThrough energy = new PassThrough();
    private int litCooldown;

    public PowerConduitBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.POWER_CONDUIT.get(), pos, state);
    }

    public IEnergyStorage getEnergy() {
        return this.energy;
    }

    /**
     * Flood the connected conduit network from this node, gather every machine on it that can accept power,
     * and hand out up to {@code amount} FE across them. Returns the total actually delivered. When not a
     * simulation and energy moved, lights every conduit on the network for the flow animation.
     */
    private int distribute(int amount, boolean simulate) {
        Set<BlockPos> seen = new HashSet<>();
        List<PowerConduitBlockEntity> members = new ArrayList<>();
        LinkedHashMap<BlockPos, IEnergyStorage> sinks = new LinkedHashMap<>();

        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(this.worldPosition);
        seen.add(this.worldPosition);
        members.add(this);

        while (!queue.isEmpty() && seen.size() <= MAX_NETWORK) {
            BlockPos cur = queue.poll();
            for (Direction dir : Direction.values()) {
                BlockPos np = cur.relative(dir);
                BlockEntity nbe = this.level.getBlockEntity(np);
                if (nbe instanceof PowerConduitBlockEntity conduit) {
                    if (seen.add(np)) {
                        queue.add(np);
                        members.add(conduit);
                    }
                    continue;
                }
                if (sinks.containsKey(np)) {
                    continue;
                }
                IEnergyStorage cap = this.level.getCapability(Capabilities.EnergyStorage.BLOCK, np, dir.getOpposite());
                if (cap != null && cap.canReceive()) {
                    sinks.put(np, cap);
                }
            }
        }

        // A non-simulated push means a charged source is feeding this network right now: pressurize the
        // whole thing so the energy is visible in every cable, even if the machines are momentarily full
        // (or there are none yet). The glow fades a few ticks after the source stops offering. This is the
        // "RF is a fluid that fills the pipes while the generator has charge" behaviour.
        if (!simulate) {
            for (PowerConduitBlockEntity member : members) {
                member.markPowered();
            }
        }

        if (sinks.isEmpty()) {
            return 0;
        }

        int remaining = amount;
        int delivered = 0;
        List<IEnergyStorage> targets = new ArrayList<>(sinks.values());
        boolean progress = true;
        while (remaining > 0 && progress && !targets.isEmpty()) {
            progress = false;
            int share = Math.max(1, remaining / targets.size());
            for (Iterator<IEnergyStorage> it = targets.iterator(); it.hasNext() && remaining > 0;) {
                IEnergyStorage sink = it.next();
                int offer = Math.min(share, remaining);
                int accepted = sink.receiveEnergy(offer, simulate);
                if (accepted > 0) {
                    delivered += accepted;
                    remaining -= accepted;
                    progress = true;
                }
                if (accepted < offer) {
                    it.remove();   // sink is full this tick; stop offering to it
                }
            }
        }
        return delivered;
    }

    /** Refresh the flow glow and light this conduit's core (called for every conduit carrying power). */
    private void markPowered() {
        this.litCooldown = LIT_TICKS;
        BlockState state = getBlockState();
        if (state.hasProperty(PowerConduitBlock.POWERED) && !state.getValue(PowerConduitBlock.POWERED)) {
            this.level.setBlock(this.worldPosition, state.setValue(PowerConduitBlock.POWERED, true), Block.UPDATE_CLIENTS);
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, PowerConduitBlockEntity be) {
        // The conduit only ticks to fade its flow glow; all transport happens inside distribute().
        if (be.litCooldown > 0) {
            be.litCooldown--;
            if (be.litCooldown == 0 && state.getValue(PowerConduitBlock.POWERED)) {
                level.setBlock(pos, state.setValue(PowerConduitBlock.POWERED, false), Block.UPDATE_CLIENTS);
            }
        }
    }
}
