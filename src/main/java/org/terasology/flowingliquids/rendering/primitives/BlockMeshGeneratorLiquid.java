// Copyright 2021 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0

package org.terasology.flowingliquids.rendering.primitives;

import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector3i;
import org.joml.Vector3ic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terasology.engine.math.Side;
import org.terasology.engine.rendering.primitives.BlockMeshShapeGenerator;
import org.terasology.engine.rendering.primitives.ChunkMesh;
import org.terasology.engine.rendering.primitives.ChunkVertexFlag;
import org.terasology.engine.world.ChunkView;
import org.terasology.engine.world.block.Block;
import org.terasology.engine.world.block.BlockAppearance;
import org.terasology.engine.world.block.BlockPart;
import org.terasology.engine.world.block.shapes.BlockMeshPart;
import org.terasology.engine.world.block.tiles.WorldAtlas;
import org.terasology.flowingliquids.world.block.LiquidData;
import org.terasology.gestalt.assets.ResourceUrn;
import org.terasology.nui.Color;
import org.terasology.nui.Colorc;

/**
 * As the default block mesh generator does not allow the mesh to depend on
 * the liquid value, this modified version must be used for liquids.
 */
public class BlockMeshGeneratorLiquid extends BlockMeshShapeGenerator {
    private static final Logger logger = LoggerFactory.getLogger(BlockMeshGeneratorLiquid.class);

    private final ResourceUrn baseUrn = new ResourceUrn("FlowingLiquids", "blockmesh");
    private final WorldAtlas worldAtlas;
    private final Block block;
    private int flowIx;

    public BlockMeshGeneratorLiquid(Block block, WorldAtlas worldAtlas, int flowIx) {
        this.block = block;
        this.worldAtlas = worldAtlas;
        this.flowIx = flowIx;
    }

    @Override
    public Block getBlock() {
        return block;
    }

    @Override
    public ResourceUrn baseUrn() {
        return baseUrn;
    }

    @Override
    public void generateChunkMesh(ChunkView view, ChunkMesh chunkMesh, int x, int y, int z) {

        ChunkVertexFlag vertexFlag = ChunkVertexFlag.NORMAL;
        if (block.isWater()) {
            vertexFlag = ChunkVertexFlag.WATER_SURFACE;
        }

        ChunkMesh.RenderType renderType = ChunkMesh.RenderType.TRANSLUCENT;
        Color colorCache = new Color();

        if (!block.isTranslucent()) {
            renderType = ChunkMesh.RenderType.OPAQUE;
        }
        if (block.isWater()) {
            renderType = ChunkMesh.RenderType.WATER_AND_ICE;
        }

        Vector3i pos = new Vector3i(x, y, z);
        float[] renderHeight = getRenderHeight(view, pos);
        boolean suppressed = view.getBlock(pos.x, pos.y + 1, pos.z) == block; // Render it as full even though it actually isn't.
        boolean full = suppressed || isFull(renderHeight);

        BlockAppearance appearance = block.getAppearance(null); //TODO: collect information the block wants, or avoid this entirely.
        for (Side side : Side.values()) {
            Vector3i adjacentPos = side.getAdjacentPos(pos, new Vector3i());
            Block adjacentBlock = view.getBlock(adjacentPos);
            boolean adjacentSuppressed = view.getBlock(adjacentPos.x, adjacentPos.y + 1, adjacentPos.z) == block;
            if (isSideVisibleForBlockTypes(adjacentBlock, adjacentSuppressed, block, full, suppressed, side)) {
                BlockMeshPart basePart = appearance.getPart(BlockPart.fromSide(side));
                BlockMeshPart loweredPart = lowerPart(side, basePart, renderHeight, suppressed, adjacentBlock == block);

                Colorc colorOffset = block.getColorOffset(BlockPart.fromSide(side));
                Colorc colorSource = block.getColorSource(BlockPart.fromSide(side)).calcColor(view, x, y, z);
                colorCache.setRed(colorSource.rf() * colorOffset.rf())
                    .setGreen(colorSource.gf() * colorOffset.gf())
                    .setBlue(colorSource.bf() * colorOffset.bf())
                    .setAlpha(colorSource.af() * colorOffset.af());
                loweredPart.appendTo(chunkMesh, view, x, y, z, renderType, colorCache, vertexFlag);
            }
        }
    }

    // The height of the liquid block, as it is displayed.
    private float[] getRenderHeight(ChunkView view, Vector3ic pos) {
        float[] heights = new float[4];
        int[] liquidCount = new int[4];
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                if (view.getBlock(pos.x() + x, pos.y(), pos.z() + z) == block && view.getBlock(pos.x() + x, pos.y() + 1, pos.z() + z) != block) {
                    int height = LiquidData.getHeight((byte) view.getExtraData(flowIx, pos.x() + x, pos.y(), pos.z() + z));
                    for (int i = 0; i < 4; i++) {
                        if (((i < 2) ? (x <= 0) : (x >= 0)) && ((i % 2 == 0) ? (z <= 0) : (z >= 0))) {
                            liquidCount[i]++;
                            heights[i] += height / (float) LiquidData.MAX_HEIGHT;
                        }
                    }
                }
            }
        }
        for (int i = 0; i < 4; i++) {
            heights[i] /= liquidCount[i];
        }
        return heights;
    }

    private boolean isFull(float[] heights) {
        for (float height : heights) {
            if (height < 1) {
                return false;
            }
        }
        return true;
    }

    private BlockMeshPart lowerPart(Side side, BlockMeshPart basePart, float[] heights, boolean suppressed, boolean matches) {
        Vector3f[] vertices = new Vector3f[basePart.size()];
        Vector3f[] normals = new Vector3f[basePart.size()];
        Vector2f[] texCoords = new Vector2f[basePart.size()];
        int[] indices = new int[basePart.indicesSize()];
        for (int i = 0; i < basePart.size(); i++) {
            vertices[i]  = new Vector3f(basePart.getVertex(i));
            normals[i]   = new Vector3f(basePart.getNormal(i));
            texCoords[i] = new Vector2f(basePart.getTexCoord(i));
        }
        for (int i = 0; i < basePart.indicesSize(); i++) {
            indices[i] = basePart.getIndex(i);
        }
        if (side == Side.TOP) {
            for (int i = 0; i < vertices.length; i++) {
                float height = heights[(vertices[i].x > 0 ? 2 : 0) + (vertices[i].z > 0 ? 1 : 0)];
                vertices[i].add(0, height - 1f, 0);
            }
        } else if (side != Side.BOTTOM && (!suppressed || matches)) {
            for (int i = 0; i < vertices.length; i++) {
                if ((vertices[i].y > 0) != suppressed) {
                    float height = heights[(vertices[i].x > 0 ? 2 : 0) + (vertices[i].z > 0 ? 1 : 0)];
                    vertices[i].add(0, height - (suppressed ? 0 : 1), 0);
                    texCoords[i].add(0, -(height - (suppressed ? 0 : 1)) * worldAtlas.getRelativeTileSize());
                }
            }
        }
        return new BlockMeshPart(
            vertices, normals, texCoords, indices);
    }

    private boolean isSideVisibleForBlockTypes(Block blockToCheck, boolean adjacentSuppressed, Block currentBlock, boolean full, boolean suppressed, Side side) {
        if (side == Side.TOP && !full) {
            return true;
        } else if (blockToCheck == currentBlock) {
            return (side != Side.BOTTOM && side != Side.TOP && suppressed && !adjacentSuppressed);
        } else if (blockToCheck.getURI().toString().equals("engine:unloaded")) {
            return false;
        } else {
            return currentBlock.isWaving() != blockToCheck.isWaving()
                || blockToCheck.getMeshGenerator() == null
                || !blockToCheck.isFullSide(side.reverse())
                || (!currentBlock.isTranslucent() && blockToCheck.isTranslucent());
        }
    }

}
