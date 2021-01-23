/*
 * Copyright 2018 MovingBlocks
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.terasology.flowingliquids.rendering.primitives;

import org.joml.Vector2f;
import org.joml.Vector4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terasology.assets.ResourceUrn;
import org.terasology.flowingliquids.world.block.LiquidData;
import org.terasology.math.Side;
import org.terasology.rendering.assets.mesh.Mesh;
import org.terasology.rendering.primitives.BlockMeshGenerator;
import org.terasology.rendering.primitives.ChunkMesh;
import org.terasology.rendering.primitives.ChunkVertexFlag;
import org.terasology.rendering.primitives.Tessellator;
import org.terasology.world.ChunkView;
import org.terasology.world.block.Block;
import org.terasology.world.block.BlockAppearance;
import org.terasology.world.block.BlockPart;
import org.terasology.world.block.shapes.BlockMeshPart;
import org.terasology.world.block.tiles.WorldAtlas;

/**
 * As the default block mesh generator does not allow the mesh to depend on
 * the liquid value, this modified version must be used for FlowingLiquids:DebugLiquid.
 * As it's only used for one type of block, values are hard-coded in.
 */
public class BlockMeshGeneratorDebugLiquid implements BlockMeshGenerator {
    private static final Logger logger = LoggerFactory.getLogger(BlockMeshGeneratorDebugLiquid.class);
    private static final float TEX_COORD_SCALE = 1 / (1 - 2 / 128f); //Compensates for the default calculations in mapTexCoords.

    private Mesh mesh;
    private final Block block;
    private final int flowIx;
    private final Vector2f[] textureOffsets;

    public BlockMeshGeneratorDebugLiquid(Block block, WorldAtlas worldAtlas, int flowIx) {
        this.block = block;
        this.flowIx = flowIx;
        textureOffsets = new Vector2f[17];
        ResourceUrn baseTile = new ResourceUrn("FlowingLiquids:DebugLiquid1");
        Vector2f baseOffset = worldAtlas.getTexCoords(baseTile, true).mul(-1).add(-TEX_COORD_SCALE / 128, -TEX_COORD_SCALE / 128);
        for (int i = 1; i <= LiquidData.MAX_HEIGHT; i++) {
            ResourceUrn tile = new ResourceUrn("FlowingLiquids:DebugLiquid" + i);
            textureOffsets[i] = worldAtlas.getTexCoords(tile, true).add(baseOffset);
        }
    }

    @Override
    public void generateChunkMesh(ChunkView view, ChunkMesh chunkMesh, int x, int y, int z) {
        org.joml.Vector3i pos = new org.joml.Vector3i(x, y, z);
        int fluidHeight = LiquidData.getHeight((byte) view.getExtraData(flowIx, pos));
        BlockAppearance appearance = block.getAppearance(null); //I know it's DebugLiquid, which doesn't vary its appearance.
        for (Side side : Side.values()) {
            if (isSideVisibleForBlockTypes(view.getBlock(side.getAdjacentPos(pos, new org.joml.Vector3i())), block, side)) {
                BlockMeshPart basePart = appearance.getPart(BlockPart.fromSide(side));
                BlockMeshPart labelledPart = basePart.mapTexCoords(textureOffsets[fluidHeight], TEX_COORD_SCALE, 1);
                labelledPart.appendTo(chunkMesh, x, y, z, ChunkMesh.RenderType.OPAQUE, ChunkVertexFlag.NORMAL);
            }
        }
    }

    private boolean isSideVisibleForBlockTypes(Block blockToCheck, Block currentBlock, Side side) {
        return currentBlock.isWaving() != blockToCheck.isWaving() || blockToCheck.getMeshGenerator() == null
            || !blockToCheck.isFullSide(side.reverse()) || (!currentBlock.isTranslucent() && blockToCheck.isTranslucent());
    }

    @Override
    public Mesh getStandaloneMesh() {
        if (mesh == null || mesh.isDisposed()) {
            generateMesh();
        }
        return mesh;
    }

    private void generateMesh() {
        Tessellator tessellator = new Tessellator();
        for (BlockPart dir : BlockPart.values()) {
            BlockMeshPart part = block.getPrimaryAppearance().getPart(dir);
            if (part != null) {
                if (block.isDoubleSided()) {
                    tessellator.addMeshPartDoubleSided(part);
                } else {
                    tessellator.addMeshPart(part);
                }
            }
        }
        mesh = tessellator.generateMesh(new ResourceUrn("engine", "blockmesh", block.getURI().toString()));
    }
}
