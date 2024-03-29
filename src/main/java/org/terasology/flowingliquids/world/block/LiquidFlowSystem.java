// Copyright 2020 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0

package org.terasology.flowingliquids.world.block;

import org.joml.Vector3i;
import org.joml.Vector3ic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terasology.engine.entitySystem.entity.EntityRef;
import org.terasology.engine.entitySystem.prefab.Prefab;
import org.terasology.engine.entitySystem.prefab.PrefabManager;
import org.terasology.engine.entitySystem.systems.BaseComponentSystem;
import org.terasology.engine.entitySystem.systems.RegisterMode;
import org.terasology.engine.entitySystem.systems.RegisterSystem;
import org.terasology.engine.entitySystem.systems.UpdateSubscriberSystem;
import org.terasology.engine.logic.health.DestroyEvent;
import org.terasology.engine.math.Side;
import org.terasology.engine.registry.In;
import org.terasology.engine.world.BlockEntityRegistry;
import org.terasology.engine.world.OnChangedBlock;
import org.terasology.engine.world.WorldProvider;
import org.terasology.engine.world.block.Block;
import org.terasology.engine.world.block.BlockManager;
import org.terasology.engine.world.block.BlockRegionc;
import org.terasology.engine.world.block.family.BlockFamily;
import org.terasology.engine.world.block.items.BlockItemComponent;
import org.terasology.engine.world.block.items.OnBlockItemPlaced;
import org.terasology.engine.world.chunks.Chunks;
import org.terasology.engine.world.chunks.blockdata.ExtraBlockDataManager;
import org.terasology.engine.world.chunks.blockdata.ExtraDataSystem;
import org.terasology.engine.world.chunks.blockdata.RegisterExtraData;
import org.terasology.engine.world.chunks.event.OnChunkLoaded;
import org.terasology.gestalt.entitysystem.event.ReceiveEvent;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.terasology.flowingliquids.world.block.LiquidData.getHeight;

@RegisterSystem(RegisterMode.AUTHORITY)
@ExtraDataSystem
public class LiquidFlowSystem extends BaseComponentSystem implements UpdateSubscriberSystem {

    private static final Logger logger = LoggerFactory.getLogger(LiquidFlowSystem.class);

    private Random rand;

    @In
    private WorldProvider worldProvider;

    @In
    private BlockManager blockManager;
    private Block air;

    @In
    private ExtraBlockDataManager extraDataManager;
    private int flowIx;

    @In
    private PrefabManager prefabManager;
    private Prefab smooshingDamageType;

    @In
    private BlockEntityRegistry blockEntityRegistry;

    private Map<Block, Map<BlockFamily, LiquidSmooshingReactionComponent>> smooshingReactions;

    private Set<Vector3i> evenUpdatePositions;
    private Set<Vector3i> oddUpdatePositions;
    private Set<Vector3i> newEvenUpdatePositions;
    private Set<Vector3i> newOddUpdatePositions;
    private boolean evenTick;
    private float timeSinceUpdate;
    private static final float UPDATE_INTERVAL = 0.5f;

    @RegisterExtraData(name = LiquidData.EXTRA_DATA_NAME, bitSize = 8)
    public static boolean hasFlowData(Block block) {
        return block.isLiquid();
    }

    @Override
    public void initialise() {
        evenUpdatePositions = Collections.synchronizedSet(new LinkedHashSet());
        oddUpdatePositions = Collections.synchronizedSet(new LinkedHashSet());
        newEvenUpdatePositions = Collections.synchronizedSet(new LinkedHashSet());
        newOddUpdatePositions = Collections.synchronizedSet(new LinkedHashSet());
        air = blockManager.getBlock(BlockManager.AIR_ID);
        flowIx = extraDataManager.getSlotNumber(LiquidData.EXTRA_DATA_NAME);
        smooshingDamageType = prefabManager.getPrefab("flowingLiquids:smooshingDamage");
        smooshingReactions = new HashMap<>();
        for (Prefab prefab : prefabManager.listPrefabs(LiquidSmooshingReactionComponent.class)) {
            LiquidSmooshingReactionComponent reaction = prefab.getComponent(LiquidSmooshingReactionComponent.class);
            if (blockManager.getBlock(reaction.liquid) == air || blockManager.getBlock(reaction.block) == air) {
                // Possibly the module containing the required blocks isn't actually loaded, so the reaction can be safely ignored.
                continue;
            }
            addReaction(reaction);
            if (reaction.reversible) {
                LiquidSmooshingReactionComponent reversed = new LiquidSmooshingReactionComponent();
                reversed.liquid = reaction.block;
                reversed.block = reaction.liquid;
                reversed.product = reaction.product;
                reversed.liquidRequired = reaction.otherLiquidRequired;
                reversed.otherLiquidRequired = reaction.liquidRequired;
                addReaction(reversed);
            }
        }
        rand = new Random();
    }

    private void addReaction(LiquidSmooshingReactionComponent reaction) {
        Block liquid = blockManager.getBlock(reaction.liquid);
        BlockFamily block = blockManager.getBlockFamily(reaction.block);
        if (!smooshingReactions.containsKey(liquid)) {
            smooshingReactions.put(liquid, new HashMap<>());
        }
        smooshingReactions.get(liquid).put(block, reaction);
    }

    /**
     * Called every time a block is changed. This means that the type of the block has changed.
     *
     * @param event The block change event
     * @param blockEntity The entity of the block being changed
     */
    @ReceiveEvent
    public void blockUpdate(OnChangedBlock event, EntityRef blockEntity) {
        updateNear(event.getBlockPosition());
    }

    /**
     * Called whenever a block is placed.
     *
     * @param event The block placed event
     * @param blockEntity The entity being placed
     * @param blockComponent The block item component on the entity
     */
    @ReceiveEvent
    public void liquidPlaced(OnBlockItemPlaced event, EntityRef blockEntity, BlockItemComponent blockComponent) {
        if (blockComponent.blockFamily.getArchetypeBlock().isLiquid()) {
            worldProvider.setExtraData(flowIx, event.getPosition(), LiquidData.FULL);
            addPos(event.getPosition());
        }
    }

    /**
     * Called every time a chunk is loaded.
     *
     * @param event The loading event
     * @param entity The world entity sending the event
     */
    @ReceiveEvent
    public void onChunkLoaded(OnChunkLoaded event, EntityRef entity) {
        org.joml.Vector3i chunkPos = new org.joml.Vector3i(event.getChunkPos());
        chunkPos.mul(Chunks.SIZE_X, Chunks.SIZE_Y, Chunks.SIZE_Z);
        for (int x = -1; x < Chunks.SIZE_X + 1; x++) {
            for (int y = -1; y < Chunks.SIZE_Y + 1; y++) {
                for (int z = -1; z < Chunks.SIZE_Z + 1; z++) {
                    Vector3i pos = chunkPos.add(x, y, z, new Vector3i());
                    addPos(pos);
                }
            }
        }
    }

    @Override
    public void update(float delta) {
        randomUpdate();
        timeSinceUpdate += delta;
        if (evenTick && evenUpdatePositions.isEmpty() && timeSinceUpdate > UPDATE_INTERVAL / 2) {
            evenTick = false;
            timeSinceUpdate = 0;
            Set<Vector3i> temp = oddUpdatePositions;
            oddUpdatePositions = newOddUpdatePositions;
            newOddUpdatePositions = temp;
        }
        if (!evenTick && oddUpdatePositions.isEmpty() && timeSinceUpdate > UPDATE_INTERVAL / 2) {
            evenTick = true;
            timeSinceUpdate = 0;
            Set<Vector3i> temp = evenUpdatePositions;
            evenUpdatePositions = newEvenUpdatePositions;
            newEvenUpdatePositions = temp;
        }
        Iterator<Vector3i> updatePositions = (evenTick ? evenUpdatePositions : oddUpdatePositions).iterator();
        int numDone = 0;
        while (numDone < 10 && updatePositions.hasNext()) {
            Vector3i pos = updatePositions.next();
            updatePositions.remove();
            if (worldProvider.isBlockRelevant(pos)) {
                numDone++;
                Block blockType = worldProvider.getBlock(pos);
                byte blockStatus = (byte) worldProvider.getExtraData(flowIx, pos);
                int startHeight = 0;
                Side startDirection = null;
                int startRate = 0;

                if (blockType.isLiquid()) {
                    startHeight = getHeight(blockStatus);
                    startDirection = LiquidData.getDirection(blockStatus);
                    startRate = LiquidData.getRate(blockStatus);
                }

                int height = startHeight;
                height -= startRate;
                if (height < 0) {
                    throw new IllegalStateException("Liquid outflow greater than existing volume.");
                }

                //TODO: consider this in a varied order, but with top always first.
                boolean smooshed = false;
                for (Side side : Side.values()) {
                    Vector3i adjPos = side.getAdjacentPos(pos, new Vector3i());
                    Block adjBlock = worldProvider.getBlock(adjPos);
                    byte adjStatus = (byte) worldProvider.getExtraData(flowIx, adjPos);
                    if (adjBlock.isLiquid() && side.reverse() == LiquidData.getDirection(adjStatus)) {
                        if (adjBlock == blockType) {
                            int rate = LiquidData.getRate(adjStatus);
                            if (rate + height > LiquidData.MAX_HEIGHT) {
                                worldProvider.setExtraData(flowIx, adjPos, LiquidData.setRate(adjStatus, LiquidData.MAX_HEIGHT - height));
                                height = LiquidData.MAX_HEIGHT;
                                addPos(adjPos);
                            } else {
                                height += rate;
                            }
                        } else if (canSmoosh(adjBlock, blockType)) {
                            LiquidSmooshingReactionComponent reaction = getSmooshingReaction(adjBlock, blockType);
                            if (reaction == null || reaction.product == null) {
                                if (blockType != air) {
                                    blockEntityRegistry.getBlockEntityAt(pos).send(new DestroyEvent(EntityRef.NULL, EntityRef.NULL, smooshingDamageType));
                                }
                                if (worldProvider.getBlock(pos) == air) { // Check the event didn't get cancelled or something.
                                    blockType = adjBlock;
                                    worldProvider.setBlock(pos, adjBlock);
                                    height = LiquidData.getRate(adjStatus);
                                    smooshed = true;
                                }
                            } else {
                                float otherSufficiency = LiquidData.getRate(adjStatus) / (float) LiquidData.MAX_HEIGHT / reaction.liquidRequired;
                                float thisSufficiency = blockType.isLiquid() ? height / (float) LiquidData.MAX_HEIGHT / reaction.otherLiquidRequired : 1f;
                                // There's a much more efficient way of doing this without the loop, but this way is clearer.
                                boolean thisSufficient = false;
                                boolean otherSufficient = false;
                                while (!thisSufficient && !otherSufficient) {
                                    thisSufficient = rand.nextFloat() < thisSufficiency;
                                    otherSufficient = rand.nextFloat() < otherSufficiency;
                                }

                                if (thisSufficient && otherSufficient) {
                                    blockType = blockManager.getBlock(reaction.product);
                                    if (otherSufficiency > 1 && rand.nextFloat() < 1 / otherSufficiency) {
                                        worldProvider.setExtraData(flowIx, adjPos, LiquidData.setRate(adjStatus, 0));
                                    }
                                    worldProvider.setBlock(pos, blockType);
                                    if (blockType.isLiquid()) {
                                        height = LiquidData.MAX_HEIGHT;
                                    }
                                } else if (otherSufficient) {
                                    // consume this block but not the liquid flowing in.
                                    worldProvider.setExtraData(flowIx, adjPos, LiquidData.setRate(adjStatus, 0));
                                    blockType = air;
                                    worldProvider.setBlock(pos, air);
                                } // In the other case, thisSufficient && !otherSufficient, consume the liquid flowing in but not this block.
                            }
                        } else {
                            worldProvider.setExtraData(flowIx, adjPos, LiquidData.setRate(adjStatus, 0));
                            addPos(adjPos);
                        }
                    }
                }

                if (height == 0) {
                    if (blockType.isLiquid()) {
                        worldProvider.setBlock(pos, air);
                        worldProvider.setExtraData(flowIx, pos, 0);
                    } else {
                        numDone--;
                    }
                    if (startDirection != null) {
                        addPos(startDirection.getAdjacentPos(pos, new Vector3i()));
                    }
                    continue;
                }

                Side direction = null;
                int rate = 0;
                int maxRate = LiquidData.MAX_DOWN_RATE;

                Vector3i below = Side.BOTTOM.getAdjacentPos(pos, new Vector3i());
                Block belowBlock = worldProvider.getBlock(below);
                if (canSmoosh(blockType, belowBlock)) {
                    direction = Side.BOTTOM;
                    rate = LiquidData.MAX_DOWN_RATE;
                } else if (blockType == belowBlock) {
                    direction = Side.BOTTOM;
                    byte belowStatus = (byte) worldProvider.getExtraData(flowIx, below);
                    rate = LiquidData.MAX_HEIGHT - getHeight(belowStatus);
                    maxRate = rate + LiquidData.getRate(belowStatus);
                    if (rate > LiquidData.MAX_DOWN_RATE) {
                        rate = LiquidData.MAX_DOWN_RATE;
                    }
                }
                if (rate == 0) {
                    int lowestHeight = LiquidData.MAX_HEIGHT + 1;
                    int lowestRate = 0;
                    Side lowestSide = null;
                    for (Side side : Side.horizontalSides()) {
                        Vector3i adjPos = side.getAdjacentPos(pos, new Vector3i());
                        Block adjBlock = worldProvider.getBlock(adjPos);
                        int adjHeight;
                        int adjRate = 0;
                        if (adjBlock == blockType) {
                            byte adjStatus = (byte) worldProvider.getExtraData(flowIx, adjPos);
                            adjHeight = getHeight(adjStatus);
                            adjRate = LiquidData.getRate(adjStatus);
                        } else if (canSmoosh(blockType, adjBlock)) {
                            Block belowAdjBlock = worldProvider.getBlock(Side.BOTTOM.getAdjacentPos(adjPos, new Vector3i()));
                            if (canSmoosh(blockType, belowAdjBlock)) {
                                adjHeight = -1;
                            } else if (blockType == belowAdjBlock && getHeight((byte) worldProvider.getExtraData(flowIx, Side.BOTTOM.getAdjacentPos(adjPos, new Vector3i()))) < LiquidData.MAX_HEIGHT) {
                                adjHeight = -1;
                            } else {
                                adjHeight = 0;
                            }
                        } else {
                            adjHeight = LiquidData.MAX_HEIGHT + 1;
                        }
                        if (adjHeight < lowestHeight || (side == startDirection && !smooshed && adjHeight <= lowestHeight)) {
                            lowestHeight = adjHeight;
                            lowestSide = side;
                            lowestRate = adjRate;
                        }
                    }
                    maxRate = height - lowestHeight + lowestRate;
                    rate = maxRate - 1;
                    if (maxRate > LiquidData.MAX_RATE) {
                        maxRate = LiquidData.MAX_RATE;
                    }
                    direction = lowestSide;
                }
                if (direction == startDirection && !smooshed && rate < startRate) {
                    rate = startRate;
                }
                if (rate > maxRate) {
                    rate = maxRate;
                }
                if (rate > height) {
                    rate = height;
                } else if (rate < 0) {
                    rate = 0;
                }

                byte newStatus = LiquidData.setRate(
                    LiquidData.setDirection(
                        LiquidData.setHeight(
                            blockStatus,
                            height),
                        direction),
                    rate);
                if (newStatus != blockStatus || smooshed) {
                    worldProvider.setExtraData(flowIx, pos, newStatus);
                    updateNear(pos);
                    if (direction != startDirection || rate != startRate) {
                        if (direction != null) {
                            doAddPos(direction.getAdjacentPos(pos, new Vector3i()));
                        }
                        if (startDirection != null) {
                            addPos(startDirection.getAdjacentPos(pos, new Vector3i()));
                        }
                    }
                } else {
                    numDone--;
                }
            }
        }
    }

    /**
     * Set random liquid blocks in motion in every loaded chunk, to spread out piles of liquid and hopefully trigger
     * cascades.
     */
    private void randomUpdate() {
        for (BlockRegionc region : worldProvider.getRelevantRegions()) {
            for (int i = 0; i < 10; i++) {
                int x = region.minX() + rand.nextInt(region.getSizeX());
                int y = region.minY() + rand.nextInt(region.getSizeY());
                int z = region.minZ() + rand.nextInt(region.getSizeZ());
                if (((x + y + z) % 2 == 0) != evenTick) {
                    z += 1;
                }
                Vector3i pos = new Vector3i(x, y, z);
                Block block = worldProvider.getBlock(pos);
                if (block.isLiquid()) {
                    byte status = (byte) worldProvider.getExtraData(flowIx, pos);
                    if (LiquidData.getRate(status) == 0) {
                        Side direction = Side.horizontalSides().get(rand.nextInt(4));
                        Vector3i adjPos = direction.getAdjacentPos(pos, new Vector3i());
                        Block adjBlock = worldProvider.getBlock(adjPos);
                        if (adjBlock == block && getHeight((byte) worldProvider.getExtraData(flowIx, adjPos)) < getHeight(status) || canSmoosh(block, adjBlock)) {
                            worldProvider.setExtraData(flowIx, pos, LiquidData.setDirection(status, direction));
                            doAddPos(pos);
                            doAddPos(adjPos);
                        }
                    }
                }
            }
        }
    }

    /**
     * Can the liquid flow replace the block
     *
     * @param liquid The liquid flowing
     * @param replacing The block the liquid is replacing
     * @return True if it can, false otherwise
     */
    private boolean canSmoosh(Block liquid, Block replacing) {
        if (replacing == air || (replacing.isPenetrable() && !replacing.isLiquid())) {
            return true;
        } else if (!smooshingReactions.containsKey(liquid)) {
            return false;
        } else {
            return smooshingReactions.get(liquid).containsKey(replacing.getBlockFamily());
        }
    }

    /**
     * Get the details of the interaction when these blocks meet, or null for the default reaction.
     */
    private LiquidSmooshingReactionComponent getSmooshingReaction(Block liquid, Block replacing) {
        return smooshingReactions.containsKey(liquid) ? smooshingReactions.get(liquid).get(replacing.getBlockFamily()) : null;
    }

    /**
     * Add a position to be checked.
     *
     * @param pos The position to add
     */
    private void addPos(Vector3ic pos) {
        if (worldProvider.getBlock(pos).isLiquid()) {
            doAddPos(pos);
        }
    }

    /**
     * Add a position to be checked, even if it isn't occupied by liquid.
     *
     * @param pos The position to add
     */
    private void doAddPos(Vector3ic pos) {
        if (worldProvider.isBlockRelevant(pos)) {
            if ((pos.x() + pos.y() + pos.z()) % 2 == 0) {
                newEvenUpdatePositions.add(new Vector3i(pos));
            } else {
                newOddUpdatePositions.add(new Vector3i(pos));
            }
        }
    }

    /**
     * Notify a block and its neighbours of an update.
     *
     * @param pos The initial position to check
     */
    private void updateNear(Vector3ic pos) {
        addPos(pos);
        for (Side side : Side.values()) {
            addPos(side.getAdjacentPos(pos, new Vector3i()));
        }
    }
}
