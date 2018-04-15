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

    @Override
    public void initialise() {
        evenUpdatePositions = Collections.synchronizedSet(new LinkedHashSet());
        oddUpdatePositions = Collections.synchronizedSet(new LinkedHashSet());
        newEvenUpdatePositions = Collections.synchronizedSet(new LinkedHashSet());
        newOddUpdatePositions = Collections.synchronizedSet(new LinkedHashSet());
        air = blockManager.getBlock(BlockManager.AIR_ID);
    }

    /**
     * Called every time a block is changed.
     * This means that the type of the block has changed
     *
     * @param event       THe event itself
     * @param blockEntity The entity of the block being changed
     */
    @ReceiveEvent
    public void blockUpdate(OnChangedBlock event, EntityRef blockEntity) {
        updateNear(event.getBlockPosition());
    }

    /**
     * Called whenever a block is placed
     *
     * @param event          The block placed event
     * @param blockEntity    The entity being placed
     * @param blockComponent The block item component on the entity
     */
    @ReceiveEvent
    public void liquidPlaced(OnBlockItemPlaced event, EntityRef blockEntity, BlockItemComponent blockComponent) {
        if (blockComponent.blockFamily.getArchetypeBlock().isLiquid()) {
            worldProvider.setRawLiquid(event.getPosition(), LiquidData.FULL, (byte) 0);
            addPos(event.getPosition());
        }
    }

    /**
     * Called every time a chunk is loaded
     *
     * @param event  The loading event
     * @param entity The world entity sending the event
     */
    @ReceiveEvent
    public void onChunkLoaded(OnChunkLoaded event, EntityRef entity) {
        Vector3i chunkPos = new Vector3i(event.getChunkPos());
        chunkPos.mul(ChunkConstants.SIZE_X, ChunkConstants.SIZE_Y, ChunkConstants.SIZE_Z);
        for (int x = -1; x < ChunkConstants.SIZE_X + 1; x++) {
            for (int y = -1; y < ChunkConstants.SIZE_Y + 1; y++) {
                for (int z = -1; z < ChunkConstants.SIZE_Z + 1; z++) {
                    Vector3i pos = new Vector3i(chunkPos);
                    pos.add(x, y, z);
                    addPos(pos);
                }
            }
        }
    }

    /**
     * Collect a list of positions to update
     *
     * @param pos The initial position to check
     */
    private void updateNear(Vector3i pos) {
        addPos(pos);
        for (Side side : Side.values()) {
            addPos(side.getAdjacentPos(pos));
        }
    }

    /**
     * Add a position to be checked
     *
     * @param pos The position to add
     */
    private void addPos(Vector3i pos) {
        if (worldProvider.isBlockRelevant(pos) && worldProvider.getBlock(pos).isLiquid()) {
            if ((pos.x() + pos.y() + pos.z()) % 2 == 0) {
                newEvenUpdatePositions.add(pos);
            } else {
                newOddUpdatePositions.add(pos);
            }
        }
    }

    /**
     * Flow the liquid into the new position
     *
     * @param pos       The position of the liquid
     * @param blockType The block type of the liquid
     * @param intoPos   The new position to place it.
     */
    private void flowIntoBlock(Vector3i pos, Block blockType, Vector3i intoPos) {
        byte blockStatus = worldProvider.getRawLiquid(pos);
        worldProvider.setBlock(intoPos, blockType);
        worldProvider.setRawLiquid(intoPos, blockStatus, (byte) 0);
        worldProvider.setBlock(pos, air);
        worldProvider.setRawLiquid(pos, (byte) 0, blockStatus);
    }

    /**
     * Attempt to merge the liquid into another liquid block
     *
     * @param pos         The liquid position
     * @param liquidLevel The liquid level
     * @param intoPos     The location to flow into
     * @return True if the liquid fit in, false otherwise.
     */
    private boolean mergeIntoBlock(Vector3i pos, byte liquidLevel, Vector3i intoPos) {
        byte belowBlockStatus = worldProvider.getRawLiquid(intoPos);
        int belowBlockHeight = getHeight(belowBlockStatus);
        if (belowBlockHeight < LiquidData.MAX_HEIGHT) {
            int height = getHeight(liquidLevel);
            /* If the current block and the one below can be combined */
            if (height + belowBlockHeight <= LiquidData.MAX_HEIGHT) {
                worldProvider.setRawLiquid(intoPos, setHeight(belowBlockStatus, belowBlockHeight + height), belowBlockStatus);
                worldProvider.setBlock(pos, air);
                worldProvider.setRawLiquid(pos, (byte) 0, liquidLevel);
                /* If the two cannot be combined, merge what will */
            } else {
                worldProvider.setRawLiquid(intoPos, setHeight(belowBlockStatus, LiquidData.MAX_HEIGHT), belowBlockStatus);
                worldProvider.setRawLiquid(pos, setHeight(liquidLevel, belowBlockHeight + height - LiquidData.MAX_HEIGHT), liquidLevel);
                updateNear(pos);
            }
            return true;
        }
        return false;
    }

    /**
     * Flows the liquid into the lowest side
     * @param pos The position of the liquid
     * @param liquidType The type  of the liquid
     * @param liquidLevel The level of the liquid
     * @param lowestPos The position of the lowest side
     * @param lowestHeight The height of the lowest side
     * @param highestHeight The height of the highest side
     * @return
     */
    private boolean flowIntoLowest(Vector3i pos, Block liquidType, byte liquidLevel, Vector3i lowestPos, int lowestHeight, int highestHeight) {
        int liquidHeight = getHeight(liquidLevel);
        if (lowestHeight < liquidHeight - 1 || lowestHeight < liquidHeight && highestHeight > liquidHeight) {
            byte adjStatus = worldProvider.getRawLiquid(lowestPos);
            if (worldProvider.getBlock(lowestPos) == liquidType) {
                worldProvider.setRawLiquid(lowestPos, setHeight(adjStatus, lowestHeight + 1), adjStatus);
                updateNear(lowestPos);
            } else {
                worldProvider.setBlock(lowestPos, liquidType);
                worldProvider.setRawLiquid(lowestPos, setHeight(LiquidData.FULL, 1), adjStatus);
            }
            if (liquidHeight > 1) {
                worldProvider.setRawLiquid(pos, setHeight(liquidLevel, liquidHeight - 1), liquidLevel);
                updateNear(pos);
            } else {
                worldProvider.setBlock(pos, air);
                worldProvider.setRawLiquid(pos, (byte) 0, liquidLevel);
            }
            return true;
        } else {
            return false;
        }
    }

    /**
     * Get the height of the liquid at the side
     * @param adjBlock The block being checked
     * @param liquidType The liquid type
     * @param adj The position being tested
     * @return The height of the liquid, or null if it cannot flow there.
     */
    private Integer processSide(Block adjBlock, Block liquidType, Vector3i adj) {
        int height;
        /* If the adjacent block is the same liquid they can merge*/
        if (adjBlock == liquidType) {
            height = getHeight(worldProvider.getRawLiquid(adj));
            if (height == LiquidData.MAX_HEIGHT) {
                Vector3i above = Side.TOP.getAdjacentPos(adj);
                if (worldProvider.getBlock(above) == liquidType) {
                    height += getHeight(worldProvider.getRawLiquid(above));
                }
            }
            /* If the adjacent block can be flowed into */
        } else if (canSmoosh(liquidType, adjBlock)) {
            height = 0;
            Vector3i belowAdj = Side.BOTTOM.getAdjacentPos(adj);
            Block belowAdjBlock = worldProvider.getBlock(belowAdj);
            if (belowAdjBlock == liquidType) {
                height = getHeight(worldProvider.getRawLiquid(belowAdj)) - LiquidData.MAX_HEIGHT;
            } else if (canSmoosh(liquidType, belowAdjBlock)) {
                height = -LiquidData.MAX_HEIGHT;
            }
            /* The block cannot be flowed into */
        } else {
            return null;
        }
        return height;
    }

    private boolean flowLiquidIntoSides(Vector3i pos, Block liquidType, byte blockStatus) {
        Vector3i lowestAdj = null;
        int lowestHeight = LiquidData.MAX_HEIGHT;
        int highestHeight = 0;
        /* Find the lowest and highest sides */
        for (Side side : Side.horizontalSides()) {
            Vector3i adj = side.getAdjacentPos(pos);
            Block adjBlock = worldProvider.getBlock(adj);
            Integer height = processSide(adjBlock, liquidType, adj);
            if (height == null) {
                continue;
            }
            if (height < lowestHeight) {
                lowestHeight = height;
                lowestAdj = adj;
            }
            if (height > highestHeight) {
                highestHeight = height;
            }
        }
        return flowIntoLowest(pos, liquidType, blockStatus, lowestAdj, lowestHeight, highestHeight);
    }

    /**
     * Attempts to process the flow for the liquid at the position
     * @param pos The position of the liquid
     * @return True if the liquid was processed. False otherwise.
     */
    private boolean processPosition(Vector3i pos) {
        if (worldProvider.isBlockRelevant(pos)) {
            Block blockType = worldProvider.getBlock(pos);
            if (blockType.isLiquid()) {
                byte blockStatus = worldProvider.getRawLiquid(pos);
                Vector3i below = Side.BOTTOM.getAdjacentPos(pos);
                Block belowBlock = worldProvider.getBlock(below);

                if (canSmoosh(blockType, belowBlock)) {
                    flowIntoBlock(pos, blockType, below);
                    return true;
                } else if (belowBlock == blockType) {
                    boolean result = mergeIntoBlock(pos, blockStatus, below);
                    if (result) {
                        return true;
                    }
                }
                return flowLiquidIntoSides(pos, blockType, blockStatus);
            }
        }
        return false;
    }

    @Override
    public void update(float delta) {
        evenTick = !evenTick;
        if (evenUpdatePositions.isEmpty() && oddUpdatePositions.isEmpty()) {
            Set<Vector3i> temp = evenUpdatePositions;
            evenUpdatePositions = newEvenUpdatePositions;
            newEvenUpdatePositions = temp;
            temp = oddUpdatePositions;
            oddUpdatePositions = newOddUpdatePositions;
            newOddUpdatePositions = temp;
        }
        Iterator<Vector3i> updatePositions = (evenTick ? evenUpdatePositions : oddUpdatePositions).iterator();
        int numDone = 0;
        while (numDone < 10 && updatePositions.hasNext()) {
            Vector3i pos = updatePositions.next();
            updatePositions.remove();
            boolean result = processPosition(pos);
            numDone += result ? 1 : 0;
        }
    }

    // Is the liquid able to destroy this other block by flowing into it?
    private boolean canSmoosh(Block liquid, Block replacing) {
        return replacing == air;
    }
}
