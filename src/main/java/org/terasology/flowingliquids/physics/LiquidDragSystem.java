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

package org.terasology.flowingliquids.physics;

import java.math.RoundingMode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.terasology.entitySystem.entity.EntityManager;
import org.terasology.entitySystem.entity.EntityRef;
import org.terasology.entitySystem.systems.BaseComponentSystem;
import org.terasology.entitySystem.systems.RegisterSystem;
import org.terasology.entitySystem.systems.RegisterMode;
import org.terasology.entitySystem.systems.UpdateSubscriberSystem;
import org.terasology.logic.characters.CharacterMovementComponent;
import org.terasology.logic.characters.CharacterImpulseEvent;
import org.terasology.logic.location.LocationComponent;
import org.terasology.math.Region3i;
import org.terasology.math.Side;
import org.terasology.math.geom.Vector3i;
import org.terasology.math.geom.Vector3f;
import org.terasology.registry.In;
import org.terasology.world.WorldProvider;
import org.terasology.world.block.Block;
import org.terasology.world.block.BlockManager;

import org.terasology.flowingliquids.world.block.LiquidData;

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
    
    @Override
    public void initialise() {}
    
    @Override
    public void update(float delta) {
        for (EntityRef entity : entityManager.getEntitiesWith(CharacterMovementComponent.class, LocationComponent.class)) {
            float charHeight = entity.getComponent(CharacterMovementComponent.class).height;
            Vector3f pos = entity.getComponent(LocationComponent.class).getWorldPosition();
            int numSamples = (int) charHeight+1;
            pos.add(0, -0.5f * charHeight * (1 + 1f/numSamples), 0);
            Vector3f force = new Vector3f();
            for (int i = 0; i < numSamples; i++) {
                pos.add(0, charHeight/numSamples, 0);
                Vector3i blockPos = new Vector3i(pos, RoundingMode.HALF_UP);
                Block block = worldProvider.getBlock(blockPos);
                if (block.isLiquid()) {
                    byte status = worldProvider.getRawLiquid(blockPos);
                    int rate = LiquidData.getRate(status);
                    if (rate > 0) {
                        Side direction = LiquidData.getDirection(status);
                        Vector3f flow = direction.getVector3i().toVector3f();
                        flow.mul(rate*block.getMass());
                        force.add(flow);
                    }
                }
            }
            force.mul(delta*0.4f/numSamples);
            entity.send(new CharacterImpulseEvent(force));
        }
    }
}
