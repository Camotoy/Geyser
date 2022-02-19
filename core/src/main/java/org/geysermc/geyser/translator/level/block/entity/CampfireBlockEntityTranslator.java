/*
 * Copyright (c) 2019-2022 GeyserMC. http://geysermc.org
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

package org.geysermc.geyser.translator.level.block.entity;

import com.github.steveice10.mc.protocol.data.game.level.block.BlockEntityType;
import com.github.steveice10.opennbt.tag.builtin.CompoundTag;
import com.github.steveice10.opennbt.tag.builtin.ListTag;
import com.github.steveice10.opennbt.tag.builtin.Tag;
import com.nukkitx.math.vector.Vector3i;
import com.nukkitx.nbt.NbtMap;
import com.nukkitx.nbt.NbtMapBuilder;
import org.geysermc.geyser.registry.type.ItemMapping;
import org.geysermc.geyser.session.GeyserSession;

@BlockEntity(type = BlockEntityType.CAMPFIRE)
public class CampfireBlockEntityTranslator extends BlockEntityTranslator {
    @Override
    public void translateTag(GeyserSession session, NbtMapBuilder builder, CompoundTag tag, int blockState) {
        ListTag items = tag.get("Items");
        int i = 0;
        for (Tag itemTag : items.getValue()) {
            builder.put("Item" + ++i, getItem(session, (CompoundTag) itemTag));
        }

        Vector3i position = Vector3i.from((int) builder.get("x"), (int) builder.get("y"), (int) builder.get("z"));
        if (i == 4) {
            // Campfire is full
            session.getCampfireCache().add(position);
        } else {
            session.getCampfireCache().remove(position);
        }
    }

    protected NbtMap getItem(GeyserSession session, CompoundTag tag) {
        ItemMapping mapping = session.getItemMappings().getMapping((String) tag.get("id").getValue());
        NbtMapBuilder tagBuilder = NbtMap.builder()
                .putString("Name", mapping.getBedrockIdentifier())
                .putByte("Count", (byte) tag.get("Count").getValue())
                .putShort("Damage", (short) mapping.getBedrockData());
        tagBuilder.put("tag", NbtMap.EMPTY);
        return tagBuilder.build();
    }
}
