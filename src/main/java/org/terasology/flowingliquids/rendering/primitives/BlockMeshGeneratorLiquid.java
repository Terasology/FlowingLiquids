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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terasology.flowingliquids.world.block.LiquidData;
import org.terasology.gestalt.assets.ResourceUrn;
import org.terasology.math.JomlUtil;
import org.terasology.math.Side;
import org.terasology.math.geom.Vector2f;
import org.terasology.math.geom.Vector3f;
import org.terasology.math.geom.Vector3i;
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

import java.util.Arrays;

/**
 * As the default block mesh generator does not allow the mesh to depend on
 * the liquid value, this modified version must be used for liquids.
 */
public class BlockMeshGeneratorLiquid implements BlockMeshGenerator {
    private static final Logger logger = LoggerFactory.getLogger(BlockMeshGeneratorLiquid.class);
    
    private WorldAtlas worldAtlas;
    
    private Block block;
    private Mesh mesh;
    
    private int flowIx;
    
    public BlockMeshGeneratorLiquid(Block block, WorldAtlas worldAtlas, int flowIx) {
        this.block = block;
        this.worldAtlas = worldAtlas;
        this.flowIx = flowIx;
    }
    
    @Override
    public void generateChunkMesh(ChunkView view, ChunkMesh chunkMesh, int x, int y, int z) {
        
        ChunkVertexFlag vertexFlag = ChunkVertexFlag.NORMAL;
        if (block.isWater()) {
            vertexFlag = ChunkVertexFlag.WATER_SURFACE;
        }
        
        ChunkMesh.RenderType renderType = ChunkMesh.RenderType.TRANSLUCENT;

        if (!block.isTranslucent()) {
            renderType = ChunkMesh.RenderType.OPAQUE;
        }
        if (block.isWater()) {
            renderType = ChunkMesh.RenderType.WATER_AND_ICE;
        }

        Vector3i pos = new Vector3i(x,y,z);
        float[] renderHeight = getRenderHeight(view, pos);
        boolean suppressed = view.getBlock(pos.x, pos.y+1, pos.z) == block; // Render it as full even though it actually isn't.
        boolean full = suppressed || isFull(renderHeight);
        
        BlockAppearance appearance = block.getAppearance(null); //TODO: collect information the block wants, or avoid this entirely.
        for(Side side : Side.values()) {
            Vector3i adjacentPos = side.getAdjacentPos(pos);
            Block adjacentBlock = view.getBlock(adjacentPos);
            boolean adjacentSuppressed = view.getBlock(adjacentPos.x, adjacentPos.y+1, adjacentPos.z) == block;
            if(isSideVisibleForBlockTypes(adjacentBlock, adjacentSuppressed, block, full, suppressed, side)) {
//                Vector4f colorOffset = block.calcColorOffsetFor(BlockPart.fromSide(side), biome);
                BlockMeshPart basePart = appearance.getPart(BlockPart.fromSide(side));
                BlockMeshPart loweredPart = lowerPart(side, basePart, renderHeight, suppressed, adjacentBlock == block);
                loweredPart.appendTo(chunkMesh, x, y, z, renderType, vertexFlag);
            }
        }
    }
    
    // The height of the liquid block, as it is displayed.
    private float[] getRenderHeight(ChunkView view, Vector3i pos) {
        float[] heights = new float[4];
        int[] liquidCount = new int[4];
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                if (view.getBlock(pos.x+x, pos.y, pos.z+z) == block && view.getBlock(pos.x+x, pos.y+1, pos.z+z) != block) {
                    int height = LiquidData.getHeight((byte)view.getExtraData(flowIx, pos.x+x, pos.y, pos.z+z));
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
        Vector3f[] vertices  = new Vector3f[basePart.size()];
        Vector3f[] normals   = new Vector3f[basePart.size()];
        Vector2f[] texCoords = new Vector2f[basePart.size()];
        int[]      indices   = new      int[basePart.indicesSize()];
        for(int i=0; i<basePart.size(); i++) {
            vertices[i] = JomlUtil.from(basePart.getVertex(i));
            normals[i]  = JomlUtil.from(basePart.getNormal(i));
            texCoords[i]= JomlUtil.from(basePart.getTexCoord(i));
        }
        for(int i=0; i<basePart.indicesSize(); i++) {
            indices[i] = basePart.getIndex(i);
        }
        if(side == Side.TOP) {
            for (int i=0; i<vertices.length; i++) {
                float height = heights[(vertices[i].x > 0 ? 2 : 0) + (vertices[i].z > 0 ? 1 : 0)];
                vertices[i].add(0, height-1f, 0);
            }
        } else if(side != Side.BOTTOM && (!suppressed || matches)) {
            for (int i=0; i<vertices.length; i++) {
                if((vertices[i].y > 0) != suppressed) {
                    float height = heights[(vertices[i].x > 0 ? 2 : 0) + (vertices[i].z > 0 ? 1 : 0)];
                    vertices[i].add(0, height - (suppressed ? 0 : 1), 0);
                    texCoords[i].add(0, -(height - (suppressed ? 0 : 1)) * worldAtlas.getRelativeTileSize());
                }
            }
        }
        return new BlockMeshPart(
                teraMathToJomlArray(vertices), teraMathToJomlArray(normals), teraMathToJomlArray(texCoords), indices);
    }

    private org.joml.Vector3f[] teraMathToJomlArray(Vector3f[] vectors) {
        return Arrays.stream(vectors)
                .map(JomlUtil::from)
                .toArray(org.joml.Vector3f[]::new);
    }

    private org.joml.Vector2f[] teraMathToJomlArray(Vector2f[] vectors) {
        return Arrays.stream(vectors)
                .map(JomlUtil::from)
                .toArray(org.joml.Vector2f[]::new);
    }
    
    private boolean isSideVisibleForBlockTypes(Block blockToCheck, boolean adjacentSuppressed, Block currentBlock, boolean full, boolean suppressed, Side side) {
        if(side == Side.TOP && !full) {
            return true;
        } else if(blockToCheck == currentBlock) {
            return (side != Side.BOTTOM && side != Side.TOP && suppressed && !adjacentSuppressed);
        } else {
            return currentBlock.isWaving() != blockToCheck.isWaving()
                || blockToCheck.getMeshGenerator() == null
                || !blockToCheck.isFullSide(side.reverse())
                || (!currentBlock.isTranslucent() && blockToCheck.isTranslucent());
        }
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
