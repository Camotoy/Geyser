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

package org.geysermc.connector.network.translators.item.translators;

import com.github.steveice10.mc.protocol.data.game.entity.metadata.ItemStack;
import com.github.steveice10.opennbt.tag.builtin.ByteTag;
import com.github.steveice10.opennbt.tag.builtin.Tag;
import com.nukkitx.protocol.bedrock.data.inventory.ItemData;
import org.geysermc.connector.network.translators.ItemRemapper;
import org.geysermc.connector.network.translators.item.ItemEntry;
import org.geysermc.connector.network.translators.item.ItemRegistry;
import org.geysermc.connector.network.translators.item.ItemTranslator;

import java.util.List;
import java.util.stream.Collectors;

@ItemRemapper
public class CompassTranslator extends ItemTranslator {

    private final List<ItemEntry> appliedItems;

    public CompassTranslator() {
        appliedItems = ItemRegistry.ITEM_ENTRIES.values().stream().filter(entry -> entry.getJavaIdentifier().endsWith("compass")).collect(Collectors.toList());
    }

    @Override
    public ItemData.Builder translateToBedrock(ItemStack itemStack, ItemEntry itemEntry) {
        if (itemStack.getNbt() == null) return super.translateToBedrock(itemStack, itemEntry);

        Tag lodestoneTag = itemStack.getNbt().get("LodestoneTracked");
        if (lodestoneTag instanceof ByteTag) {
            // Get the fake lodestonecompass entry
            itemEntry = ItemRegistry.getItemEntry("minecraft:lodestone_compass");
            // The NBT will be translated in nbt/CompassTranslator in order to use session variables
        }

        return super.translateToBedrock(itemStack, itemEntry);
    }

    @Override
    public ItemStack translateToJava(ItemData itemData, ItemEntry itemEntry) {
        if (itemEntry.getBedrockIdentifier().equals("minecraft:lodestone_compass")) {
            // Revert the entry back to the compass
            itemEntry = ItemRegistry.getItemEntry("minecraft:compass");
        }

        return super.translateToJava(itemData, itemEntry);
    }

    @Override
    public List<ItemEntry> getAppliedItems() {
        return appliedItems;
    }
}
