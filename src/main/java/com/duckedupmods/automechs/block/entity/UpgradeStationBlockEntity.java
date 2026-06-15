package com.duckedupmods.automechs.block.entity;

import java.util.ArrayList;
import java.util.List;

import com.duckedupmods.automechs.Config;
import com.duckedupmods.automechs.block.UpgradeStationBlock;
import com.duckedupmods.automechs.entity.MechRole;
import com.duckedupmods.automechs.item.MechChassisItem;
import com.duckedupmods.automechs.item.UpgradeModuleItem;
import com.duckedupmods.automechs.item.UpgradeType;
import com.duckedupmods.automechs.menu.UpgradeStationMenu;
import com.duckedupmods.automechs.registry.ModBlockEntities;
import com.duckedupmods.automechs.registry.ModDataComponents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Backs the Upgrade Station. Slot 0 holds a single Mech Chassis, slot 1 holds an upgrade module. While
 * powered it runs an upgrade cycle; on completion it raises that upgrade's level on the chassis (a data
 * component, capped at {@link UpgradeType#maxLevel()}) and consumes one module. A maxed-out upgrade (or
 * a missing chassis/module) keeps the station idle so nothing is wasted.
 */
public class UpgradeStationBlockEntity extends BlockEntity implements GeoBlockEntity, MenuProvider {

    public static final int SLOT_CHASSIS = 0;
    public static final int SLOT_MODULE = 1;
    public static final int ENERGY_CAPACITY = 50_000;
    public static final int ENERGY_MAX_RECEIVE = 5_000;
    public static final int MAX_PROGRESS = 100;

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WORK = RawAnimation.begin().thenLoop("work");

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    // Client-synced state for the mech-on-pedestal preview.
    private boolean clientHasChassis;
    private String clientRoleId;
    // Server-side change-detection so we only push an update packet on a transition.
    private boolean lastHasChassis;
    private String lastRoleId = "";

    private final ItemStackHandler items = new ItemStackHandler(2) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return switch (slot) {
                case SLOT_CHASSIS -> stack.getItem() instanceof MechChassisItem;
                case SLOT_MODULE -> {
                    if (!(stack.getItem() instanceof UpgradeModuleItem module)) {
                        yield false;
                    }
                    // If a chassis is mounted, only accept modules that role can actually use.
                    ItemStack chassis = getStackInSlot(SLOT_CHASSIS);
                    yield !(chassis.getItem() instanceof MechChassisItem)
                            || module.type().appliesTo(UpgradeStationBlockEntity.this.roleOf(chassis));
                }
                default -> false;
            };
        }
    };

    /** Receive-only buffer fed by conduits. */
    private static final class StationEnergy extends EnergyStorage {
        private StationEnergy() {
            super(ENERGY_CAPACITY, ENERGY_MAX_RECEIVE, 0);
        }

        void setStored(int value) {
            this.energy = Math.max(0, Math.min(this.capacity, value));
        }

        boolean consume(int amount) {
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

    private final StationEnergy energy = new StationEnergy();
    private int progress;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            // index 2/3 carry the FE buffer as two shorts (ContainerData syncs shorts; see menu).
            return switch (index) {
                case 0 -> progress;
                case 1 -> MAX_PROGRESS;
                case 2 -> energy.getEnergyStored() & 0xFFFF;
                default -> (energy.getEnergyStored() >>> 16) & 0xFFFF;
            };
        }

        @Override
        public void set(int index, int value) {
            // Client-side sync: energy arrives as two shorts (low/high 16 bits); store it so get() reflects it.
            switch (index) {
                case 0 -> progress = value;
                case 2 -> energy.setStored((energy.getEnergyStored() & ~0xFFFF) | (value & 0xFFFF));
                case 3 -> energy.setStored(((value & 0xFFFF) << 16) | (energy.getEnergyStored() & 0xFFFF));
                default -> { }
            }
        }

        @Override
        public int getCount() {
            return 4;
        }
    };

    public UpgradeStationBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.UPGRADE_STATION.get(), pos, state);
    }

    public IItemHandler getItems() {
        return this.items;
    }

    public IEnergyStorage getEnergy() {
        return this.energy;
    }

    public ContainerData getData() {
        return this.data;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, UpgradeStationBlockEntity be) {
        ItemStack chassis = be.items.getStackInSlot(SLOT_CHASSIS);
        ItemStack module = be.items.getStackInSlot(SLOT_MODULE);

        if (chassis.getCount() == 1 && module.getItem() instanceof UpgradeModuleItem moduleItem
                && be.canApply(chassis, moduleItem.type())) {
            if (be.energy.consume(Config.UPGRADE_FE_PER_TICK.get())) {
                be.progress++;
                if (be.progress >= MAX_PROGRESS) {
                    be.applyUpgrade(chassis, moduleItem.type());
                    be.items.extractItem(SLOT_MODULE, 1, false);
                    be.progress = 0;
                    level.playSound(null, pos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.5F, 1.4F);
                }
                be.setChanged();
            }
        } else if (be.progress != 0) {
            be.progress = 0;
            be.setChanged();
        }

        // Spin the rings up while a cycle runs (drives the GeckoLib idle/work controller).
        boolean working = be.progress > 0;
        if (state.getValue(UpgradeStationBlock.WORKING) != working) {
            level.setBlock(pos, state.setValue(UpgradeStationBlock.WORKING, working), Block.UPDATE_CLIENTS);
        }

        // Preview sync: tell clients whenever the mounted chassis (or its role) changes, so the
        // mech-on-pedestal preview appears/updates.
        boolean hasChassis = chassis.getItem() instanceof MechChassisItem;
        String roleId = hasChassis ? be.roleIdOf(chassis) : "";
        if (hasChassis != be.lastHasChassis || !roleId.equals(be.lastRoleId)) {
            be.lastHasChassis = hasChassis;
            be.lastRoleId = roleId;
            level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
        }
    }

    /** The effective role id of a chassis: its role component, else its tier's default role (so a chassis
     *  with no role component — e.g. a creative/crafted one — still previews as its own T2/T3 model). */
    private String roleIdOf(ItemStack chassis) {
        if (!(chassis.getItem() instanceof MechChassisItem)) {
            return "";
        }
        return roleOf(chassis).id();
    }

    /** Whether to draw the mech-on-pedestal preview (client reads synced state; server reads the slot). */
    public boolean isPreviewActive() {
        if (this.level != null && this.level.isClientSide) {
            return this.clientHasChassis;
        }
        return this.items.getStackInSlot(SLOT_CHASSIS).getItem() instanceof MechChassisItem;
    }

    /** Role of the mounted chassis, for picking the preview mech's model/texture. */
    public MechRole getPreviewRole() {
        String id = this.level != null && this.level.isClientSide
                ? this.clientRoleId
                : roleIdOf(this.items.getStackInSlot(SLOT_CHASSIS));
        return id != null && !id.isEmpty() ? MechRole.byId(id) : MechRole.MINING;
    }

    /** True if the chassis can still take another level of this upgrade. */
    private boolean canApply(ItemStack chassis, UpgradeType type) {
        if (!type.appliesTo(roleOf(chassis))) {
            return false; // module does nothing for this role
        }
        // Fortune and Silk Touch are mutually exclusive, like vanilla.
        if (type == UpgradeType.FORTUNE && chassis.getOrDefault(UpgradeType.SILK_TOUCH.component().get(), 0) > 0) {
            return false;
        }
        if (type == UpgradeType.SILK_TOUCH && chassis.getOrDefault(UpgradeType.FORTUNE.component().get(), 0) > 0) {
            return false;
        }
        int level = chassis.getOrDefault(type.component().get(), 0);
        return level < type.maxLevel();
    }

    /** The role of a chassis stack (its role component, else the chassis tier's default role). */
    private MechRole roleOf(ItemStack chassis) {
        String id = chassis.get(ModDataComponents.MECH_ROLE.get());
        if (id != null && !id.isEmpty()) {
            return MechRole.byId(id);
        }
        if (chassis.getItem() instanceof MechChassisItem mc) {
            return MechRole.defaultForTier(mc.getTier());
        }
        return MechRole.MINING;
    }

    private void applyUpgrade(ItemStack chassis, UpgradeType type) {
        int level = chassis.getOrDefault(type.component().get(), 0);
        chassis.set(type.component().get(), Math.min(type.maxLevel(), level + 1));
    }

    /** Items to scatter when the block is broken. */
    public List<ItemStack> getDropContents() {
        List<ItemStack> drops = new ArrayList<>();
        for (int slot = 0; slot < this.items.getSlots(); slot++) {
            // Extract (not just read) so the item leaves the handler: the world gets it exactly once, even
            // if the block entity were to outlive this call. Only ever called on block removal.
            ItemStack stack = this.items.extractItem(slot, Integer.MAX_VALUE, false);
            if (!stack.isEmpty()) {
                drops.add(stack);
            }
        }
        return drops;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.automechs.upgrade_station");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new UpgradeStationMenu(containerId, playerInventory, this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Items", this.items.serializeNBT(registries));
        tag.putInt("Progress", this.progress);
        tag.putInt("Energy", this.energy.getEnergyStored());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Items")) {
            this.items.deserializeNBT(registries, tag.getCompound("Items"));
        }
        this.progress = tag.getInt("Progress");
        this.energy.setStored(tag.getInt("Energy"));
    }

    // ---- client sync for the mech-on-pedestal preview -----------------------

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        ItemStack chassis = this.items.getStackInSlot(SLOT_CHASSIS);
        tag.putBoolean("HasChassis", chassis.getItem() instanceof MechChassisItem);
        tag.putString("RoleId", roleIdOf(chassis));
        return tag;
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection net,
            net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider registries) {
        CompoundTag tag = pkt.getTag();
        if (tag != null) {
            this.clientHasChassis = tag.getBoolean("HasChassis");
            this.clientRoleId = tag.getString("RoleId");
        }
    }

    // ---- GeckoLib ------------------------------------------------------------

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "rings", 5, state ->
                state.setAndContinue(this.getBlockState().getValue(UpgradeStationBlock.WORKING) ? WORK : IDLE)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.geoCache;
    }
}
