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
    
    public static int getHeight(byte status){
        return (int) (status & 7)+1;
    }
    
    public static byte setHeight(byte status, int height){
        if(height < 1 || height > 8)
            throw new IllegalArgumentException("Liquid heights are constrained to the range 1 to 8.");
        return (byte) ((status & ~7) | (height-1));
    }
}
