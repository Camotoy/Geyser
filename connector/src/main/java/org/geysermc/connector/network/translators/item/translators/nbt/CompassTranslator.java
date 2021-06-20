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

package org.geysermc.connector.network.translators.item.translators.nbt;

import com.github.steveice10.opennbt.tag.builtin.CompoundTag;
import com.github.steveice10.opennbt.tag.builtin.IntTag;
import com.github.steveice10.opennbt.tag.builtin.StringTag;
import org.geysermc.connector.network.session.GeyserSession;
import org.geysermc.connector.network.translators.ItemRemapper;
import org.geysermc.connector.network.translators.item.ItemEntry;
import org.geysermc.connector.network.translators.item.ItemRegistry;
import org.geysermc.connector.network.translators.item.NbtItemStackTranslator;

import java.util.List;
import java.util.stream.Collectors;

@ItemRemapper
public class CompassTranslator extends NbtItemStackTranslator {

    private final List<ItemEntry> appliedItems;

    public CompassTranslator() {
        appliedItems = ItemRegistry.ITEM_ENTRIES.values().stream().filter(entry -> entry.getJavaIdentifier().endsWith("compass")).collect(Collectors.toList());
    }

    @Override
    public void translateToBedrock(GeyserSession session, CompoundTag itemTag, ItemEntry itemEntry) {
        // Get the lodestone pos
        CompoundTag lodestonePos = itemTag.get("LodestonePos");
        if (lodestonePos != null) {
            // Get all info needed for tracking
            int x = ((IntTag) lodestonePos.get("X")).getValue();
            int y = ((IntTag) lodestonePos.get("Y")).getValue();
            int z = ((IntTag) lodestonePos.get("Z")).getValue();
            String dim = ((StringTag) itemTag.get("LodestoneDimension")).getValue();

            // Store the info
            int trackID = session.getLodestoneTracker().store(x, y, z, dim);

            // Set the bedrock tracking ID
            itemTag.put(new IntTag("trackingHandle", trackID));
        } else if (itemEntry.getBedrockIdentifier().equals("minecraft:lodestone_compass")) {
            // The lodestone was removed; just set the tracking id to 0
            itemTag.put(new IntTag("trackingHandle", 0));
        }
    }

    @Override
    public void translateToJava(CompoundTag itemTag, ItemEntry itemEntry) {
        // As of 1.17, this function cannot be called on any item from a player inventory that was given NBT
        // Basically, there should be no way we need to reverse translate the position
    }

    @Override
    public boolean acceptItem(ItemEntry itemEntry) {
        return appliedItems.contains(itemEntry);
    }
}
