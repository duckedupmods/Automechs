package com.duckedupmods.automechs.entity.ai;

import java.util.EnumSet;

import com.duckedupmods.automechs.block.entity.DataRackBlockEntity;
import com.duckedupmods.automechs.entity.CacheCrawler;
import com.duckedupmods.automechs.registry.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;

/**
 * The Cache Crawler's whole job: roam the base, find the nearest Data Rack whose data is fragmented, scuttle
 * over to it, and defrag it in place. No assignment or linking — the bot autonomously services any rack that
 * needs it, which is exactly the storage fantasy (drop items in, they land scattered, the crawlers tidy them
 * up). It picks a fresh target whenever it goes idle, so a swarm naturally spreads across a rack wall.
 */
public class CrawlerDefragGoal extends Goal {

    /** How far the bot looks for fragmented racks (blocks). */
    private static final int SCAN_H = 8;
    private static final int SCAN_V = 4;
    /** Ticks of "work" spent on a rack before a defrag pass lands (the scuttle-and-compact animation). */
    private static final int WORK_TICKS = 40;
    /** Re-scan throttle while idle, so a swarm doesn't sweep the whole box every tick. */
    private static final int SCAN_COOLDOWN = 20;

    private final CacheCrawler crawler;
    private BlockPos target;
    private int workTimer;
    private int scanCooldown;

    public CrawlerDefragGoal(CacheCrawler crawler) {
        this.crawler = crawler;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    private DataRackBlockEntity rackAt(BlockPos pos) {
        Level level = this.crawler.level();
        if (!level.getBlockState(pos).is(ModBlocks.DATA_RACK.get())) {
            return null;
        }
        return level.getBlockEntity(pos) instanceof DataRackBlockEntity rack ? rack : null;
    }

    /** Find the nearest fragmented rack within scan range, or null. */
    private BlockPos findFragmentedRack() {
        Level level = this.crawler.level();
        BlockPos origin = this.crawler.blockPosition();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dy = -SCAN_V; dy <= SCAN_V; dy++) {
            for (int dx = -SCAN_H; dx <= SCAN_H; dx++) {
                for (int dz = -SCAN_H; dz <= SCAN_H; dz++) {
                    cursor.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    DataRackBlockEntity rack = rackAt(cursor);
                    if (rack == null || !rack.needsDefrag()) {
                        continue;
                    }
                    double d = this.crawler.distanceToSqr(cursor.getX() + 0.5, cursor.getY() + 0.5, cursor.getZ() + 0.5);
                    if (d < bestDist) {
                        bestDist = d;
                        best = cursor.immutable();
                    }
                }
            }
        }
        return best;
    }

    @Override
    public boolean canUse() {
        if (this.scanCooldown-- > 0) {
            return false;
        }
        this.scanCooldown = SCAN_COOLDOWN;
        this.target = findFragmentedRack();
        return this.target != null;
    }

    @Override
    public boolean canContinueToUse() {
        if (this.target == null) {
            return false;
        }
        DataRackBlockEntity rack = rackAt(this.target);
        return rack != null && rack.needsDefrag();
    }

    @Override
    public void start() {
        this.workTimer = 0;
        pathToTarget();
    }

    @Override
    public void stop() {
        this.target = null;
        this.workTimer = 0;
        this.crawler.setWorking(false);
        this.crawler.getNavigation().stop();
    }

    private void pathToTarget() {
        if (this.target != null) {
            this.crawler.getNavigation().moveTo(
                    this.target.getX() + 0.5, this.target.getY() + 0.5, this.target.getZ() + 0.5, 1.0D);
        }
    }

    @Override
    public void tick() {
        if (this.target == null) {
            return;
        }
        this.crawler.getLookControl().setLookAt(
                this.target.getX() + 0.5, this.target.getY() + 0.5, this.target.getZ() + 0.5);

        double distSqr = this.crawler.distanceToSqr(
                this.target.getX() + 0.5, this.target.getY() + 0.5, this.target.getZ() + 0.5);
        if (distSqr > 3.0D) {
            // Still travelling — keep a path live.
            this.crawler.setWorking(false);
            if (this.crawler.getNavigation().isDone()) {
                pathToTarget();
            }
            return;
        }

        // Arrived: scuttle in place and compact the rack.
        this.crawler.getNavigation().stop();
        this.crawler.setWorking(true);
        if (++this.workTimer >= WORK_TICKS) {
            this.workTimer = 0;
            DataRackBlockEntity rack = rackAt(this.target);
            if (rack != null) {
                rack.defrag();
            }
        }
    }
}
