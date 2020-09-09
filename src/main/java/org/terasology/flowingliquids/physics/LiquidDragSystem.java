// Copyright 2020 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0

package org.terasology.flowingliquids.physics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terasology.engine.entitySystem.entity.EntityManager;
import org.terasology.engine.entitySystem.entity.EntityRef;
import org.terasology.engine.entitySystem.systems.BaseComponentSystem;
import org.terasology.engine.entitySystem.systems.RegisterMode;
import org.terasology.engine.entitySystem.systems.RegisterSystem;
import org.terasology.engine.entitySystem.systems.UpdateSubscriberSystem;
import org.terasology.engine.logic.characters.CharacterImpulseEvent;
import org.terasology.engine.logic.characters.CharacterMovementComponent;
import org.terasology.engine.logic.location.LocationComponent;
import org.terasology.engine.math.Side;
import org.terasology.engine.registry.In;
import org.terasology.engine.world.WorldProvider;
import org.terasology.engine.world.block.Block;
import org.terasology.engine.world.chunks.blockdata.ExtraBlockDataManager;
import org.terasology.flowingliquids.world.block.LiquidData;
import org.terasology.math.geom.Vector3f;
import org.terasology.math.geom.Vector3i;

import java.math.RoundingMode;

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
        for (EntityRef entity : entityManager.getEntitiesWith(CharacterMovementComponent.class,
                LocationComponent.class)) {
            float charHeight = entity.getComponent(CharacterMovementComponent.class).height;
            Vector3f pos = entity.getComponent(LocationComponent.class).getWorldPosition();
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
                        Vector3f flow = direction.getVector3i().toVector3f();
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
