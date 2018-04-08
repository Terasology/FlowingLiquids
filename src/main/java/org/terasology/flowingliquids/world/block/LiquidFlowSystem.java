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

import java.util.Iterator;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.terasology.entitySystem.entity.EntityRef;
import org.terasology.entitySystem.event.ReceiveEvent;
import org.terasology.entitySystem.systems.BaseComponentSystem;
import org.terasology.entitySystem.systems.RegisterSystem;
import org.terasology.entitySystem.systems.RegisterMode;
import org.terasology.entitySystem.systems.UpdateSubscriberSystem;
import org.terasology.math.geom.Vector3i;
import org.terasology.math.Side;
import org.terasology.registry.In;
import org.terasology.world.OnChangedBlock;
import org.terasology.world.WorldProvider;
import org.terasology.world.block.Block;
import org.terasology.world.block.BlockComponent;
import org.terasology.world.block.BlockManager;
import org.terasology.world.block.items.OnBlockItemPlaced;
import org.terasology.world.block.items.BlockItemComponent;
import org.terasology.world.chunks.ChunkConstants;
import org.terasology.world.chunks.event.OnChunkLoaded;

@RegisterSystem(RegisterMode.AUTHORITY)
public class LiquidFlowSystem extends BaseComponentSystem implements UpdateSubscriberSystem {

    private static final Logger logger = LoggerFactory.getLogger(LiquidFlowSystem.class);
    
    @In
    private WorldProvider worldProvider;
    
    @In
    private BlockManager blockManager;
    private Block air;
    
    private Set<Vector3i> evenUpdatePositions;
    private Set<Vector3i> oddUpdatePositions;
    private Set<Vector3i> newEvenUpdatePositions;
    private Set<Vector3i> newOddUpdatePositions;
    private boolean evenTick;
    
    private static int MAX_LIQUID_HEIGHT = 8;
    
    @Override
    public void initialise() {
        evenUpdatePositions = Collections.synchronizedSet(new LinkedHashSet());
         oddUpdatePositions = Collections.synchronizedSet(new LinkedHashSet());
        newEvenUpdatePositions = Collections.synchronizedSet(new LinkedHashSet());
         newOddUpdatePositions = Collections.synchronizedSet(new LinkedHashSet());
        air = blockManager.getBlock(BlockManager.AIR_ID);
    }
    
    @ReceiveEvent
    public void blockUpdate(OnChangedBlock event, EntityRef blockEntity) {
        updateNear(event.getBlockPosition());
    }
    
    @ReceiveEvent
    public void liquidPlaced(OnBlockItemPlaced event, EntityRef blockEntity, BlockItemComponent blockComponent) {
        if(blockComponent.blockFamily.getArchetypeBlock().isLiquid()){
            addPos(event.getPosition());
        }
    }
    
    @ReceiveEvent
    public void onChunkLoaded(OnChunkLoaded event, EntityRef entity){
        Vector3i chunkPos = new Vector3i(event.getChunkPos());
        chunkPos.mul(ChunkConstants.SIZE_X, ChunkConstants.SIZE_Y, ChunkConstants.SIZE_Z);
        for(int x = -1; x < ChunkConstants.SIZE_X + 1; x++) {
            for(int y = -1; y < ChunkConstants.SIZE_Y + 1; y++) {
                for(int z = -1; z < ChunkConstants.SIZE_Z + 1; z++) {
                    Vector3i pos = new Vector3i(chunkPos);
                    pos.add(x,y,z);
                    addPos(pos);
                }
            }
        }
    }
    
    private void updateNear(Vector3i pos) {
        addPos(pos);
        for(Side side : Side.values()) {
            addPos(side.getAdjacentPos(pos));
        }
    }
    
    private void addPos(Vector3i pos){
        if(worldProvider.isBlockRelevant(pos) && worldProvider.getBlock(pos).isLiquid()) {
            if((pos.x() + pos.y() + pos.z()) % 2 == 0) {
                newEvenUpdatePositions.add(pos);
            } else {
                newOddUpdatePositions.add(pos);
            }
        }
    }
    
    @Override
    public void update(float delta) {
        evenTick = !evenTick;
        if(evenUpdatePositions.isEmpty() && oddUpdatePositions.isEmpty()) {
            Set <Vector3i> temp = evenUpdatePositions;
            evenUpdatePositions = newEvenUpdatePositions;
            newEvenUpdatePositions = temp;
            temp = oddUpdatePositions;
            oddUpdatePositions = newOddUpdatePositions;
            newOddUpdatePositions = temp;
        }
        Iterator<Vector3i> updatePositions = (evenTick ? evenUpdatePositions : oddUpdatePositions).iterator();
        int numDone = 0;
        while(numDone < 10 && updatePositions.hasNext()){
            Vector3i pos = updatePositions.next();
            updatePositions.remove();
            if(worldProvider.isBlockRelevant(pos)) {
                Block blockType = worldProvider.getBlock(pos);
                if(blockType.isLiquid()){
                    numDone++;
                    byte blockStatus = worldProvider.getRawLiquid(pos);
                    Vector3i below = Side.BOTTOM.getAdjacentPos(pos);
                    Block belowBlock = worldProvider.getBlock(below);
                    if(canSmoosh(blockType, belowBlock)) {
                        worldProvider.setBlock(below, blockType);
                        worldProvider.setRawLiquid(below, blockStatus, (byte)0);
                        worldProvider.setBlock(pos, air);
                        worldProvider.setRawLiquid(pos, (byte)0, blockStatus);
                        continue;
                    } else if(belowBlock == blockType) {
                        byte belowBlockStatus = worldProvider.getRawLiquid(below);
                        int belowBlockHeight = getHeight(belowBlockStatus);
                        if(belowBlockHeight < MAX_LIQUID_HEIGHT){
                            int height = getHeight(blockStatus);
                            if(height + belowBlockHeight <= MAX_LIQUID_HEIGHT) {
                                worldProvider.setRawLiquid(below, setHeight(belowBlockStatus, belowBlockHeight + height), belowBlockStatus);
                                worldProvider.setBlock(pos, air);
                                worldProvider.setRawLiquid(pos, (byte)0, blockStatus);
                            } else {
                                worldProvider.setRawLiquid(below, setHeight(belowBlockStatus, MAX_LIQUID_HEIGHT), belowBlockStatus);
                                worldProvider.setRawLiquid(pos, setHeight(blockStatus, belowBlockHeight + height - MAX_LIQUID_HEIGHT), blockStatus);
                                updateNear(pos);
                            }
                            continue;
                        }
                    }
                    Vector3i lowestAdj = null;
                    int lowestHeight = MAX_LIQUID_HEIGHT;
                    int highestHeight = 0;
                    for(Side side : Side.horizontalSides()) {
                        Vector3i adj = side.getAdjacentPos(pos);
                        Block adjBlock = worldProvider.getBlock(adj);
                        int height;
                        if(adjBlock == blockType) {
                            height = getHeight(worldProvider.getRawLiquid(adj));
                            if(height == MAX_LIQUID_HEIGHT) {
                                Vector3i above = Side.TOP.getAdjacentPos(adj);
                                if(worldProvider.getBlock(above) == blockType) {
                                    height += getHeight(worldProvider.getRawLiquid(above));
                                }
                            }
                        } else if(canSmoosh(blockType, adjBlock)) {
                            height = 0;
                            Vector3i belowAdj = Side.BOTTOM.getAdjacentPos(adj);
                            Block belowAdjBlock = worldProvider.getBlock(belowAdj);
                            if(belowAdjBlock == blockType) {
                                height = getHeight(worldProvider.getRawLiquid(belowAdj)) - MAX_LIQUID_HEIGHT;
                            } else if(canSmoosh(blockType, belowAdjBlock)) {
                                height = -MAX_LIQUID_HEIGHT;
                            }
                        } else {
                            continue; //Can't flow that way.
                        }
                        if(height < lowestHeight) {
                            lowestHeight = height;
                            lowestAdj = adj;
                        }
                        if(height > highestHeight) {
                            highestHeight = height;
                        }
                    }
                    int height = getHeight(blockStatus);
                    if(lowestHeight < height - 1 || lowestHeight < height && highestHeight > height) {
                        byte adjStatus = worldProvider.getRawLiquid(lowestAdj);
                        if(worldProvider.getBlock(lowestAdj) == blockType){
                            worldProvider.setRawLiquid(lowestAdj, setHeight(adjStatus, lowestHeight+1), adjStatus);
                            updateNear(lowestAdj);
                        } else {
                            worldProvider.setBlock(lowestAdj, blockType);
                            worldProvider.setRawLiquid(lowestAdj, setHeight((byte)0, 1), adjStatus);
                        }
                        if(height > 1) {
                            worldProvider.setRawLiquid(pos, setHeight(blockStatus, height-1), blockStatus);
                            updateNear(pos);
                        } else {
                            worldProvider.setBlock(pos, air);
                            worldProvider.setRawLiquid(pos, (byte)0, blockStatus);
                        }
                    }
                }
            }
        }
    }
    
    // Is the liquid able to destroy this other block by flowing into it?
    private boolean canSmoosh(Block liquid, Block replacing){
        return replacing == air;
    }
    
    private static int getHeight(byte status){
        return (int) (status & 7)+1;
    }
    
    private static byte setHeight(byte status, int height){
        if(height < 1 || height > 8)
            throw new IllegalArgumentException("Liquid heights are constrained to the range 1 to 8.");
        return (byte) ((status & ~7) | (height-1));
    }
}
