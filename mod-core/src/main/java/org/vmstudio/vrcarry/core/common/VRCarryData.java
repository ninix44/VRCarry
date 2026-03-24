package org.vmstudio.vrcarry.core.common;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class VRCarryData {

    private CarryType type;
    private CompoundTag nbt;

    public VRCarryData(CompoundTag data) {
        this.type = data.contains("type") ? CarryType.valueOf(data.getString("type")) : CarryType.INVALID;
        this.nbt = data.copy();
    }

    public CompoundTag getNbt() {
        nbt.putString("type", type.name());
        return nbt;
    }

    public void setBlock(BlockState state, @Nullable BlockEntity blockEntity) {
        this.type = CarryType.BLOCK;

        if (state.hasProperty(BlockStateProperties.WATERLOGGED)) {
            state = state.setValue(BlockStateProperties.WATERLOGGED, false);
        }

        nbt.put("block_state", NbtUtils.writeBlockState(state));

        if (blockEntity != null && blockEntity.getLevel() != null) {
            nbt.put("block_entity", blockEntity.saveWithId());
        } else {
            nbt.remove("block_entity");
        }
    }

    public BlockState getBlockState() {
        if (!isCarrying(CarryType.BLOCK)) {
            throw new IllegalStateException("No carried block stored");
        }
        return NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), nbt.getCompound("block_state"));
    }

    @Nullable
    public BlockEntity getBlockEntity(BlockPos pos) {
        if (!isCarrying(CarryType.BLOCK) || !nbt.contains("block_entity")) {
            return null;
        }
        return BlockEntity.loadStatic(pos, getBlockState(), nbt.getCompound("block_entity"));
    }

    public void setBed(BlockState footState, BlockState headState, @Nullable UUID occupantUuid) {
        this.type = CarryType.BED;

        if (footState.hasProperty(BlockStateProperties.WATERLOGGED)) {
            footState = footState.setValue(BlockStateProperties.WATERLOGGED, false);
        }

        if (headState.hasProperty(BlockStateProperties.WATERLOGGED)) {
            headState = headState.setValue(BlockStateProperties.WATERLOGGED, false);
        }

        nbt.put("bed_foot_state", NbtUtils.writeBlockState(footState));
        nbt.put("bed_head_state", NbtUtils.writeBlockState(headState));

        if (occupantUuid != null) {
            nbt.putUUID("bed_occupant", occupantUuid);
        } else {
            nbt.remove("bed_occupant");
        }
    }

    public BlockState getBedFootState() {
        if (!isCarrying(CarryType.BED)) {
            throw new IllegalStateException("No carried bed stored");
        }
        return NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), nbt.getCompound("bed_foot_state"));
    }

    public BlockState getBedHeadState() {
        if (!isCarrying(CarryType.BED)) {
            throw new IllegalStateException("No carried bed stored");
        }
        return NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), nbt.getCompound("bed_head_state"));
    }

    @Nullable
    public UUID getBedOccupantUuid() {
        if (!isCarrying(CarryType.BED) || !nbt.hasUUID("bed_occupant")) {
            return null;
        }
        return nbt.getUUID("bed_occupant");
    }

    public boolean isCarrying() {
        return type != CarryType.INVALID;
    }

    public boolean isCarrying(CarryType carryType) {
        return type == carryType;
    }

    public void clear() {
        this.type = CarryType.INVALID;
        this.nbt = new CompoundTag();
    }

    public int getTick() {
        return nbt.contains("tick") ? nbt.getInt("tick") : -1;
    }

    public enum CarryType {
        BLOCK,
        BED,
        INVALID
    }
}
