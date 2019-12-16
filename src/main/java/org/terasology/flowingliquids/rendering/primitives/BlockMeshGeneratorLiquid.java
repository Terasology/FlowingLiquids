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
 
import com.google.common.collect.Maps;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import org.terasology.assets.ResourceUrn;
import org.terasology.math.Side;
import org.terasology.math.geom.Vector3i;
import org.terasology.math.geom.Vector4f;
import org.terasology.math.geom.Vector3f;
import org.terasology.math.geom.Vector2f;
import org.terasology.registry.In;
import org.terasology.rendering.assets.mesh.Mesh;
import org.terasology.rendering.primitives.BlockMeshGenerator;
import org.terasology.rendering.primitives.ChunkMesh;
import org.terasology.rendering.primitives.ChunkVertexFlag;
import org.terasology.rendering.primitives.Tessellator;
import org.terasology.registry.CoreRegistry;
import org.terasology.world.ChunkView;
import org.terasology.world.block.Block;
import org.terasology.world.block.BlockAppearance;
import org.terasology.world.block.BlockPart;
import org.terasology.world.block.shapes.BlockMeshPart;
import org.terasology.world.block.tiles.BlockTile;
import org.terasology.world.block.tiles.WorldAtlas;

import org.terasology.flowingliquids.world.block.LiquidData;
import org.terasology.world.generation.Region;

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
    public void generateChunkMesh(ChunkView view, ChunkMesh chunkMesh, Region worldData, int x, int y, int z) {
        final Block selfBlock = view.getBlock(x, y, z);
        Vector3i pos = new Vector3i(x,y,z);
        float renderHeight = getRenderHeight(view, pos);
        
        ChunkVertexFlag vertexFlag = ChunkVertexFlag.NORMAL;
        ChunkMesh.RenderType renderType = ChunkMesh.RenderType.TRANSLUCENT;

        if (!selfBlock.isTranslucent()) {
            renderType = ChunkMesh.RenderType.OPAQUE;
        }

        if (selfBlock.isWater()) {
            if (renderHeight == 1) {
                vertexFlag = ChunkVertexFlag.WATER;
            } else {
                vertexFlag = ChunkVertexFlag.WATER_SURFACE;
            }
            renderType = ChunkMesh.RenderType.WATER_AND_ICE;
        }
        
        BlockAppearance appearance = block.getAppearance(null); //TODO: collect information the block wants, or avoid this entirely.
        for(Side side : Side.values()) {
            Vector3i adjacentPos = side.getAdjacentPos(pos);
            Block adjacentBlock = view.getBlock(adjacentPos);
            float adjacentHeight = getRenderHeight(view, adjacentPos);
            if(adjacentBlock != block) {
                adjacentHeight = 0;
            }
            if(isSideVisibleForBlockTypes(adjacentBlock, adjacentHeight, block, renderHeight, side)) {
//                Vector4f colorOffset = block.calcColorOffsetFor(BlockPart.fromSide(side), biome);
                BlockMeshPart basePart = appearance.getPart(BlockPart.fromSide(side));
                BlockMeshPart loweredPart = lowerPart(side, basePart, renderHeight, adjacentHeight);
                Vector4f colorOffset = selfBlock.getColorOffset(BlockPart.fromSide(side));
                Vector4f colorSource = selfBlock.getColorSource(BlockPart.fromSide(side)).calcColor(worldData, x, y, z);
                Vector4f colorResult = new Vector4f(colorSource.x * colorOffset.x, colorSource.y * colorOffset.y, colorSource.z * colorOffset.z, colorSource.w * colorOffset.w);
                loweredPart.appendTo(chunkMesh, x, y, z, renderType, colorResult, vertexFlag);
            }
        }
    }
    
    // The height of the liquid block, as it is displayed.
    private float getRenderHeight(ChunkView view, Vector3i pos) {
        int height = LiquidData.getHeight((byte)view.getExtraData(flowIx, pos));
        float renderHeight = height / (float) LiquidData.MAX_HEIGHT;
        Block above = view.getBlock(Side.TOP.getAdjacentPos(pos));
        if(!above.isLiquid() && !above.isFullSide(Side.BOTTOM)) {
            renderHeight *= 0.9;
        }
        return renderHeight;
    }
    
    private BlockMeshPart lowerPart(Side side, BlockMeshPart basePart, float height, float otherHeight) {
        Vector3f[] vertices  = new Vector3f[basePart.size()];
        Vector3f[] normals   = new Vector3f[basePart.size()];
        Vector2f[] texCoords = new Vector2f[basePart.size()];
        int[]      indices   = new      int[basePart.indicesSize()];
        for(int i=0; i<basePart.size(); i++) {
            vertices[i] = new Vector3f(basePart.getVertex(i));
            normals[i]  = new Vector3f(basePart.getNormal(i));
            texCoords[i]= new Vector2f(basePart.getTexCoord(i));
        }
        for(int i=0; i<basePart.indicesSize(); i++) {
            indices[i] = basePart.getIndex(i);
        }
        if(side == Side.TOP) {
            for(int i=0; i<vertices.length; i++) {
                vertices[i].add(0, height-1f, 0);
            }
        } else if(side != Side.BOTTOM) {
            for(int i=0; i<vertices.length; i++) {
                if(vertices[i].y > 0) {
                    vertices[i].add(0, height-1f, 0);
                    texCoords[i].add(0, -(height-1) * worldAtlas.getRelativeTileSize());
                } else {
                    vertices[i].add(0, otherHeight, 0);
                    texCoords[i].add(0, -otherHeight * worldAtlas.getRelativeTileSize());
                }
            }
        }
        return new BlockMeshPart(vertices, normals, texCoords, indices);
    }
    
    private boolean isSideVisibleForBlockTypes(Block blockToCheck, float heightToCheck, Block currentBlock, float currentHeight, Side side) {
        if(side == Side.TOP && currentHeight < 1) {
            return true;
        } else if(blockToCheck == currentBlock) {
            return !side.isVertical() && heightToCheck < currentHeight
                || side == Side.BOTTOM && heightToCheck < 1;
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
