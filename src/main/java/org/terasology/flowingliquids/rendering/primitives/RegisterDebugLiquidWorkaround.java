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

import org.terasology.entitySystem.systems.BaseComponentSystem;
import org.terasology.entitySystem.systems.RegisterMode;
import org.terasology.entitySystem.systems.RegisterSystem;
import org.terasology.registry.In;
import org.terasology.world.block.BlockManager;

/**
 * Workaround for issue #3491. Triggers registration of debugLiquid on headless
 * servers, so that it is accessible to clients for RegisterLiquidMeshGenerators.
 */
@RegisterSystem(RegisterMode.ALWAYS)
public class RegisterDebugLiquidWorkaround extends BaseComponentSystem {
    
    @In
    private BlockManager blockManager;
    
    public void initialise(){
        blockManager.getBlock("FlowingLiquids:DebugLiquid");
    }
    public void preBegin() {}
    public void postBegin(){}
    public void preSave(){}
    public void postSave(){}
    public void shutdown(){}
}
