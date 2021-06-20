/*
 * Copyright (c) 2019-2021 GeyserMC. http://geysermc.org
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *
 * @author GeyserMC
 * @link https://github.com/GeyserMC/Geyser
 */

package org.geysermc.connector.network.session.cache;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;

public class LodestoneTracker {

    private final Int2ObjectMap<LodestonePos> lodestones = new Int2ObjectOpenHashMap<>();

    /**
     * Store the given coordinates and dimensions
     *
     * @param x The X position of the Lodestone
     * @param y The Y position of the Lodestone
     * @param z The Z position of the Lodestone
     * @param dim The dimension containing of the Lodestone
     * @return The id in the Map
     */
    public int store(int x, int y, int z, String dim) {
        LodestonePos pos = new LodestonePos(x, y, z, dim);

        for (Int2ObjectMap.Entry<LodestonePos> lodestone : lodestones.int2ObjectEntrySet()) {
            if (lodestone.getValue().equals(pos)) {
                return lodestone.getIntKey();
            }
        }

        // Otherwise, the position doesn't already exist
        // Start at 1 as 0 seems to not work
        int id = lodestones.size() + 1;
        lodestones.put(id, pos);

        return id;
    }

    /**
     * Get the lodestone data
     *
     * @param id The ID to get the data for
     * @return The stored data
     */
    public LodestonePos getPos(int id) {
        return lodestones.get(id);
    }

    @Getter
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class LodestonePos {
        final int x;
        final int y;
        final int z;
        final String dimension;
    }
}