package com.example.examplemod;

import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;

final class SteveAiScanFilters {

    private SteveAiScanFilters() {}

    static boolean isInterestingLookSeeBlock(BlockState state) {
        Block block = state.getBlock();

        return block == Blocks.CHEST
            || block == Blocks.TRAPPED_CHEST
            || block == Blocks.BELL
            || block == Blocks.HAY_BLOCK
            || block == Blocks.BLAST_FURNACE
            || block == Blocks.FURNACE
            || block == Blocks.CRAFTING_TABLE
            || block == Blocks.FARMLAND
            || block == Blocks.WATER
            || block == Blocks.BARREL
            || block == Blocks.SMOKER
            || block == Blocks.COMPOSTER
            || state.getBlock() instanceof BedBlock
            || state.getBlock() instanceof DoorBlock;
    }
}
