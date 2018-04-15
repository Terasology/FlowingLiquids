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

package org.terasology.flowingliquids.world.block;

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

import static org.terasology.flowingliquids.world.block.LiquidData.getHeight;
import static org.terasology.flowingliquids.world.block.LiquidData.setHeight;

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
    private float timeSinceUpdate;
    private static final float UPDATE_INTERVAL = 0.5f;
    
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
            worldProvider.setRawLiquid(event.getPosition(), LiquidData.FULL, (byte) 0);
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
        if(worldProvider.getBlock(pos).isLiquid()) {
            doAddPos(pos);
        }
    }
    
    private void doAddPos(Vector3i pos){
        if(worldProvider.isBlockRelevant(pos)) {
            if((pos.x() + pos.y() + pos.z()) % 2 == 0) {
                newEvenUpdatePositions.add(pos);
            } else {
                newOddUpdatePositions.add(pos);
            }
        }
    }
    
    @Override
    public void update(float delta) {
        timeSinceUpdate += delta;
        if(evenTick && evenUpdatePositions.isEmpty() && timeSinceUpdate > UPDATE_INTERVAL/2) {
            //logger.info("Odd  "+newOddUpdatePositions.size());
            evenTick = false;
            timeSinceUpdate = 0;
            Set <Vector3i> temp = oddUpdatePositions;
            oddUpdatePositions = newOddUpdatePositions;
            newOddUpdatePositions = temp;
        }
        if(!evenTick && oddUpdatePositions.isEmpty() && timeSinceUpdate > UPDATE_INTERVAL/2) {
            //logger.info("Even "+newEvenUpdatePositions.size());
            evenTick = true;
            timeSinceUpdate = 0;
            Set <Vector3i> temp = evenUpdatePositions;
            evenUpdatePositions = newEvenUpdatePositions;
            newEvenUpdatePositions = temp;
        }
        Iterator<Vector3i> updatePositions = (evenTick ? evenUpdatePositions : oddUpdatePositions).iterator();
        int numDone = 0;
        boolean debug = false; //evenUpdatePositions.size() + oddUpdatePositions.size() < 20;
        while(numDone < 10 && updatePositions.hasNext()){
            Vector3i pos = updatePositions.next();
            updatePositions.remove();
            if(worldProvider.isBlockRelevant(pos)) {
                numDone++;
                Block blockType = worldProvider.getBlock(pos);
                byte blockStatus = worldProvider.getRawLiquid(pos);
                int startHeight = 0;
                Side startDirection = null;
                int startRate = 0;
                
                if(blockType.isLiquid()) {
                    startHeight = getHeight(blockStatus);
                    startDirection = LiquidData.getDirection(blockStatus);
                    startRate = LiquidData.getRate(blockStatus);
                }
                if(debug) logger.info("Pondering "+pos+", "+blockType.getDisplayName()+" with h"+startHeight+"r"+startRate+"d"+startDirection);
                
                int height = startHeight;
                height -= startRate;
                if(height < 0) {
                    throw new IllegalStateException("Liquid outflow greater than existing volume.");
                }
                
                //TODO: consider this in a varied order, but with top always first.
                boolean smooshed = false;
                for(Side side : Side.values()) {
                    Vector3i adjPos = side.getAdjacentPos(pos);
                    Block adjBlock = worldProvider.getBlock(adjPos);
                    byte adjStatus = worldProvider.getRawLiquid(adjPos);
                    if(adjBlock.isLiquid() && side.reverse() == LiquidData.getDirection(adjStatus)) {
                        if(adjBlock == blockType) {
                            int rate = LiquidData.getRate(adjStatus);
                            if(rate + height > LiquidData.MAX_HEIGHT) {
                                worldProvider.setRawLiquid(adjPos, LiquidData.setRate(adjStatus, LiquidData.MAX_HEIGHT - height), adjStatus);
                                height = LiquidData.MAX_HEIGHT;
                                addPos(adjPos);
                            } else {
                                height += rate;
                            }
                        } else if(canSmoosh(adjBlock, blockType)) {
                            blockType = adjBlock;
                            worldProvider.setBlock(pos, adjBlock);
                            height = LiquidData.getRate(adjStatus);
                            smooshed = true;
                        } else {
                            worldProvider.setRawLiquid(adjPos, LiquidData.setRate(adjStatus, 0), adjStatus);
                            addPos(adjPos);
                        }
                    }
                }
                
                if(height == 0) {
                    if(blockType.isLiquid()) {
                        worldProvider.setBlock(pos, air);
                        worldProvider.setRawLiquid(pos, (byte)0, blockStatus);
                    } else {
                        numDone--;
                    }
                    if(startDirection != null) {
                        addPos(startDirection.getAdjacentPos(pos));
                    }
                    continue;
                }
                
                Side direction = null;
                int rate = 0;
                int maxRate = LiquidData.MAX_DOWN_RATE;
                
                Vector3i below = Side.BOTTOM.getAdjacentPos(pos);
                Block belowBlock = worldProvider.getBlock(below);
                if(canSmoosh(blockType, belowBlock)) {
                    direction = Side.BOTTOM;
                    rate = LiquidData.MAX_DOWN_RATE;
                } else if(blockType == belowBlock) {
                    direction = Side.BOTTOM;
                    byte belowStatus = worldProvider.getRawLiquid(below);
                    rate = LiquidData.MAX_HEIGHT - getHeight(belowStatus);
                    maxRate = rate + LiquidData.getRate(belowStatus);
                    if(rate > LiquidData.MAX_DOWN_RATE) {
                        rate = LiquidData.MAX_DOWN_RATE;
                    }
                }
                if(rate == 0) {
                    int lowestHeight = LiquidData.MAX_HEIGHT + 1;
                    int lowestRate = 0;
                    Side lowestSide = null;
                    for(Side side : Side.horizontalSides()) {
                        Vector3i adjPos = side.getAdjacentPos(pos);
                        Block adjBlock = worldProvider.getBlock(adjPos);
                        int adjHeight;
                        int adjRate = 0;
                        if(adjBlock == blockType) {
                            byte adjStatus = worldProvider.getRawLiquid(adjPos);
                            adjHeight = getHeight(adjStatus);
                            adjRate = LiquidData.getRate(adjStatus);
                        } else if(canSmoosh(blockType, adjBlock)) {
                            adjHeight = 0;
                        } else {
                            adjHeight = LiquidData.MAX_HEIGHT + 1;
                        }
                        if(adjHeight < lowestHeight || (side == startDirection && !smooshed && adjHeight <= lowestHeight)) {
                            lowestHeight = adjHeight;
                            lowestSide = side;
                            lowestRate = adjRate;
                        }
                    }
                    rate = (height - lowestHeight)/2;
                    maxRate = height-lowestHeight+lowestRate;
                    if(rate > LiquidData.MAX_RATE) {
                        rate = LiquidData.MAX_RATE;
                    }
                    direction = lowestSide;
                }
                if(direction == startDirection && !smooshed && rate < startRate) {
                    rate = startRate;
                }
                if(rate > maxRate) {
                    rate = maxRate;
                }
                if(rate > height) {
                    rate = height;
                } else if(rate < 0) {
                    rate = 0;
                }
                
                byte newStatus = LiquidData.setRate(
                    LiquidData.setDirection(
                        LiquidData.setHeight(
                            blockStatus,
                            height),
                        direction),
                    rate);
                if(newStatus != blockStatus || smooshed) {
                    worldProvider.setRawLiquid(pos, newStatus, blockStatus);
                    updateNear(pos);
                    if(direction != startDirection || rate != startRate) {
                        if(direction != null) {
                            doAddPos(direction.getAdjacentPos(pos));
                        }
                        if(startDirection != null) {
                            addPos(startDirection.getAdjacentPos(pos));
                        }
                    }
                } else {
                    numDone--;
                }
            }
        }
    }
    
    // Is the liquid able to destroy this other block by flowing into it?
    private boolean canSmoosh(Block liquid, Block replacing){
        return replacing == air;
    }
}
