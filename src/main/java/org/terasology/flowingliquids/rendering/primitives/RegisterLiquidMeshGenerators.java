// Copyright 2020 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0

package org.terasology.flowingliquids.rendering.primitives;

import org.terasology.entitySystem.systems.BaseComponentSystem;
import org.terasology.entitySystem.systems.RegisterMode;
import org.terasology.entitySystem.systems.RegisterSystem;
import org.terasology.math.Side;
import org.terasology.registry.In;
import org.terasology.world.block.Block;
import org.terasology.world.block.BlockManager;
import org.terasology.world.block.tiles.WorldAtlas;
import org.terasology.world.chunks.blockdata.ExtraBlockDataManager;

@RegisterSystem(RegisterMode.CLIENT)
public class RegisterLiquidMeshGenerators extends BaseComponentSystem {

    @In
    private BlockManager blockManager;

    @In
    private WorldAtlas worldAtlas;

    @In
    private ExtraBlockDataManager extraDataManager;
    private int flowIx;

    public void initialise(){}
    public void preBegin(){}
    public void postBegin() {
        flowIx = extraDataManager.getSlotNumber("flowingLiquids.flow");
        Block debugLiquid = blockManager.getBlock("FlowingLiquids:DebugLiquid");
        debugLiquid.setMeshGenerator(new BlockMeshGeneratorDebugLiquid(debugLiquid, worldAtlas, flowIx));
        for(Block block : blockManager.listRegisteredBlocks()) {
            if(block.isLiquid() && block != debugLiquid) {
                block.setMeshGenerator(new BlockMeshGeneratorLiquid(block, worldAtlas, flowIx));
                for (Side side : Side.values()) {
                    // The rendered shapes won't have full sides, even if the basic shape does.
                    block.setFullSide(side, false);
                }
            }
        }
    }
    public void preSave(){}
    public void postSave(){}
    public void shutdown(){}
}
