/**
 * File: SteveAiScanFilters.java
 *
 * Main intent:
 * Defines SteveAiScanFilters functionality for the NPCoo mod codebase.
 *
 * Methods (what each does, with input/output):
 * 1) {@code {}(...)}:
 *    Purpose: Implements {} logic in this file.
 *    Input: none.
 *    Output: SteveAiScanFilters() {}.
 * 2) {@code state)(...)}:
 *    Purpose: Implements state) logic in this file.
 *    Input: BlockState state.
 *    Output: boolean.
 */
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
