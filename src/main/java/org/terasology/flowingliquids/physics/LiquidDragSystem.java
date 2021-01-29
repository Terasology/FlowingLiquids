// Copyright 2020 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0

package org.terasology.flowingliquids.physics;

import org.joml.RoundingMode;
import org.joml.Vector3f;
import org.joml.Vector3i;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terasology.entitySystem.entity.EntityManager;
import org.terasology.entitySystem.entity.EntityRef;
import org.terasology.entitySystem.systems.BaseComponentSystem;
import org.terasology.entitySystem.systems.RegisterMode;
import org.terasology.entitySystem.systems.RegisterSystem;
import org.terasology.entitySystem.systems.UpdateSubscriberSystem;
import org.terasology.flowingliquids.world.block.LiquidData;
import org.terasology.logic.characters.CharacterImpulseEvent;
import org.terasology.logic.characters.CharacterMovementComponent;
import org.terasology.logic.location.LocationComponent;
import org.terasology.math.Side;
import org.terasology.registry.In;
import org.terasology.world.WorldProvider;
import org.terasology.world.block.Block;
import org.terasology.world.chunks.blockdata.ExtraBlockDataManager;


/**
 * Moves characters along with the flow while they're submerged in liquid.
 */
@RegisterSystem(RegisterMode.AUTHORITY)
public class LiquidDragSystem extends BaseComponentSystem implements UpdateSubscriberSystem {

    private static final Logger logger = LoggerFactory.getLogger(LiquidDragSystem.class);

    @In
    private WorldProvider worldProvider;

    @In
    private EntityManager entityManager;

    @In
    private ExtraBlockDataManager extraDataManager;
    private int flowIx;

    @Override
    public void initialise() {
        flowIx = extraDataManager.getSlotNumber("flowingLiquids.flow");
    }

    @Override
    public void update(float delta) {
        for (EntityRef entity : entityManager.getEntitiesWith(CharacterMovementComponent.class, LocationComponent.class)) {
            float charHeight = entity.getComponent(CharacterMovementComponent.class).height;
            Vector3f pos = entity.getComponent(LocationComponent.class).getWorldPosition(new Vector3f());
            int numSamples = (int) charHeight + 1;
            pos.add(0, -0.5f * charHeight * (1 + 1f / numSamples), 0);
            Vector3f force = new Vector3f();
            for (int i = 0; i < numSamples; i++) {
                pos.add(0, charHeight / numSamples, 0);
                Vector3i blockPos = new Vector3i(pos, RoundingMode.HALF_UP);
                Block block = worldProvider.getBlock(blockPos);
                if (block.isLiquid()) {
                    byte status = (byte) worldProvider.getExtraData(flowIx, blockPos);
                    int rate = LiquidData.getRate(status);
                    if (rate > 0) {
                        Side direction = LiquidData.getDirection(status);
                        Vector3f flow = new Vector3f(direction.direction());
                        flow.mul(rate * block.getMass());
                        force.add(flow);
                    }
                }
            }
            force.mul(delta * 0.4f / numSamples);
            entity.send(new CharacterImpulseEvent(force));
        }
    }
}
