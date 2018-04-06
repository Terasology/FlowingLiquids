/*
 * Copyright 2016 MovingBlocks
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
import org.terasology.world.block.Block;
import org.terasology.world.block.BlockManager;
import org.terasology.world.OnChangedBlock;
import org.terasology.world.WorldProvider;

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
    private boolean evenTick;
    
    @Override
    public void initialise() {
        //evenUpdatePositions = ConcurrentHashMap.newKeySet();
        // oddUpdatePositions = ConcurrentHashMap.newKeySet();
        evenUpdatePositions = new LinkedHashSet();
         oddUpdatePositions = new LinkedHashSet();
        air = blockManager.getBlock(BlockManager.AIR_ID);
    }
    
    @ReceiveEvent
    public void blockUpdate(OnChangedBlock event, EntityRef blockEntity) {
        updateNear(event.getBlockPosition());
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
                evenUpdatePositions.add(pos);
            } else {
                oddUpdatePositions.add(pos);
            }
        }
    }
    
    @Override
    public void update(float delta) {
        evenTick = !evenTick;
        Iterator<Vector3i> updatePositions = (evenTick ? evenUpdatePositions : oddUpdatePositions).iterator();
        int numDone = 0;
        while(numDone < 3 && updatePositions.hasNext()){
            numDone++;
            Vector3i pos = updatePositions.next();
            updatePositions.remove();
            if(worldProvider.isBlockRelevant(pos)) {
                Block blockType = worldProvider.getBlock(pos);
                if(blockType.isLiquid()){
                    worldProvider.setBlock(pos, air);
                }
            }
        }
    }
}
