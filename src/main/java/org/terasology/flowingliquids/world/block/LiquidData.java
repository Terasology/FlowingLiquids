// Copyright 2020 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0

package org.terasology.flowingliquids.world.block;

import org.terasology.engine.math.Side;

/**
 * Utility class for encoding and decoding liquid data.
 */
public class LiquidData {
    public static final int MAX_HEIGHT = 16;
    public static final int MAX_RATE = 2;
    public static final int MAX_DOWN_RATE = 4;
    public static final byte FULL = (byte) 0b0_000_0000;
    public static final String EXTRA_DATA_NAME = "flowingLiquids.flow";

    /**
     * Extracts the amount of liquid in the block from a byte of liquid data.
     * @param status The packed liquid data
     * @return The height as an int
     */
    public static int getHeight(byte status){
        return (int) MAX_HEIGHT - (status & 0b0_000_1111);
    }

    /**
     * Replaces the height in a byte of liquid data with a different height.
     * @param status The byte to modify
     * @param height The height to store
     * @return The modified byte
     */
    public static byte setHeight(byte status, int height) {
        if (height < 1 || height > MAX_HEIGHT) {
            throw new IllegalArgumentException("Liquid heights are constrained to the range 1 to " + MAX_HEIGHT + ".");
        }
        return (byte) ((status & ~0b0_000_1111) | (MAX_HEIGHT - height));
    }

    /**
     * Extracts the direction of flow from a byte of liquid data.
     * @param status The packed liquid data
     * @return The direction, or null if there is no flow
     */
    public static Side getDirection(byte status) {
        int sideData = sideData(status);
        if (sideData == 0) {
            return null;
        } else if (sideData < 7) {
            return Side.values()[sideData-1];
        } else { // sideData == 7
            return Side.BOTTOM;
        }
    }

    /**
     * Replaces the flow direction in a byte of liquid data with a different direction, and resets the rate to 1 or 0.
     * @param status The byte to modify
     * @param side The direction to store, or null for no flow
     * @return The modified byte
     */
    public static byte setDirection(byte status, Side side) {
        int sideData = side == null ? 0 : side.ordinal()+1;
        return (byte) ((status & ~0b1_111_0000) | (sideData << 4));
    }

    /**
     * Extracts the flow rate from a byte of liquid data.
     * @param status The packed liquid data
     * @return The flow rate as an int
     */
    public static int getRate(byte status) {
        int rateData = (status & 0b1_000_0000) >> 7;
        int sideData = sideData(status);
        if (sideData == 0) {
            return 0;
        } else if (sideData < 7) {
            return rateData + 1;
        } else { // sideData == 7
            return rateData + 3;
        }
    }

    /**
     * Replaces the flow rate in a byte of liquid data with a different rate.
     * @param status The byte to modify
     * @param rate The rate to store
     * @return The modified byte
     */
    public static byte setRate(byte status, int rate) {
        int sideData = sideData(status);
        if (rate == 0) {
            return setDirection(status, null);
        } else if (sideData == 0) {
            throw new IllegalArgumentException("Can't set rate > 0 as there is no current direction.");
        } else if ((rate == 1 || rate == 2) && sideData < 7) {
            return (byte) ((status & ~0b1_000_0000) | (rate - 1 << 7));
        } else if ((rate == 1 || rate == 2) && sideData == 7) {
            return (byte) ((status & ~0b1_111_0000) | (rate - 1 << 7) | ((Side.BOTTOM.ordinal() + 1) << 4));
        } else if ((rate == 3 || rate == 4) && (sideData == 7 || sideData == (Side.BOTTOM.ordinal()+1))) {
            return (byte) ((status & ~0b1_111_0000) | (rate - 3 << 7) | (7 << 4));
        } else {
            throw new IllegalArgumentException("Liquid rates are constrained to the range 0 to 2 (or 4 for downwards). Was "+rate);
        }
    }

    /**
     * Extracts the 3 bits relevant to flow direction from a byte of liquid data.
     * @param status The packed liquid data
     * @return The index of the flow direction: up, down, left, right, forward, backward, none, quickly down
     */
    private static int sideData(byte status) {
        return (status & 0b0_111_0000) >> 4;
    }
}
