// Copyright 2020 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0

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
