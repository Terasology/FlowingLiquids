// Copyright 2021 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0

package org.terasology.flowingliquids.world.block;

import org.terasology.gestalt.entitysystem.component.Component;

/**
 * Specifies that a liquid is able to flow into and destroy a particular block,
 * and optionally create a different block in the process.
 */
public class LiquidSmooshingReactionComponent implements Component<LiquidSmooshingReactionComponent> {
    public String liquid;

    /** The block that gets smooshed */
    public String block;

    /** The block produced by the reaction, or null if the block is simply destroyed */
    public String product;

    /** The amount of liquid required (on average) to produce the product block, in units of whole blocks. */
    public float liquidRequired = 1;

    /** If the block that gets smooshed is a liquid, how much of that second liquid is required (on average) to produce the product block. */
    public float otherLiquidRequired = 1;

    /** If the block that is smooshed is also a liquid, whether the reverse reaction (`block` flowing into `liquid`) is also possible. */
    public boolean reversible;

    @Override
    public void copy(LiquidSmooshingReactionComponent other) {
        this.liquid = other.liquid;
        this.block = other.block;
        this.product = other.product;
        this.liquidRequired = other.liquidRequired;
        this.otherLiquidRequired = other.otherLiquidRequired;
        this.reversible = other.reversible;
    }
}
