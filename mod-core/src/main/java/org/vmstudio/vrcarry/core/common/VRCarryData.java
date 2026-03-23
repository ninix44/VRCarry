package org.vmstudio.vrcarry.core.common;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jetbrains.annotations.Nullable;

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
        INVALID
    }
}
