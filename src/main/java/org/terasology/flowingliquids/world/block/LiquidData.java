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

/**
 * Utility class for encoding and decoding liquid data.
 */
public class LiquidData {
    public static final int MAX_HEIGHT = 8;
    public static final byte FULL = (byte) 7;

    /**
     * Converts a byte height to an integer. The result has a max value of 7
     * @param status The byte to convert
     * @return The height as an int.
     */
    public static int getHeight(byte status){
        return (int) (status & 7)+1;
    }

    /**
     * Converts an integer height to a byte height
     * @param status The byte height to store with
     * @param height The height to store
     * @return The height as a byte
     */
    public static byte setHeight(byte status, int height) {
        if(height < 1 || height > 8)
            throw new IllegalArgumentException("Liquid heights are constrained to the range 1 to 8.");
        return (byte) ((status & ~7) | (height-1));
    }
}
