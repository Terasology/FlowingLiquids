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
 
import com.google.common.collect.Maps;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import org.terasology.assets.ResourceUrn;
import org.terasology.math.Side;
import org.terasology.math.geom.Vector3i;
import org.terasology.math.geom.Vector4f;
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

/**
 * As the default block mesh generator does not allow the mesh to depend on
 * the liquid value, this modified version must be used for FlowingLiquids:DebugLiquid.
 * As it's only used for one type of block, values are hard-coded in.
 */
public class BlockMeshGeneratorDebugLiquid implements BlockMeshGenerator {
    private static final Logger logger = LoggerFactory.getLogger(BlockMeshGeneratorDebugLiquid.class);
    
    private Block block;
    private Mesh mesh;
    private WorldAtlas worldAtlas;
    
    private static Vector4f colourOffset = new Vector4f(1,1,1,1);
    private static final float texCoordScale = 1/(1-2/128); //Compensates for the default calculations in mapTexCoords.
    
    public BlockMeshGeneratorDebugLiquid(Block block, WorldAtlas worldAtlas) {
        this.block = block;
        this.worldAtlas = worldAtlas;
    }
    
    @Override
    public void generateChunkMesh(ChunkView view, ChunkMesh chunkMesh, int x, int y, int z) {
        Vector3i pos = new Vector3i(x,y,z);
        int fluidHeight = 8 - (7 & view.getRawLiquid(pos));
        BlockAppearance appearance = block.getAppearance(null); //I know it's DebugLiquid, which doesn't vary its appearance.
        for(Side side : Side.values()) {
            if(isSideVisibleForBlockTypes(view.getBlock(side.getAdjacentPos(pos)), block, side)) {
                BlockMeshPart basePart = appearance.getPart(BlockPart.fromSide(side));
                BlockMeshPart labelledPart = basePart.mapTexCoords(getTexPos(fluidHeight), texCoordScale);
                labelledPart.appendTo(chunkMesh, x, y, z, colourOffset, ChunkMesh.RenderType.OPAQUE, ChunkVertexFlag.NORMAL);
            }
        }
    }
    
    private Vector2f getTexPos(int height) {
        ResourceUrn tile = new ResourceUrn("FlowingLiquids:DebugLiquid"+height);
        ResourceUrn baseTile = new ResourceUrn("FlowingLiquids:DebugLiquid1");
        return worldAtlas.getTexCoords(tile, true).add(worldAtlas.getTexCoords(baseTile, true).scale(-1)).add(new Vector2f(-texCoordScale/128/2, -texCoordScale/128));
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
