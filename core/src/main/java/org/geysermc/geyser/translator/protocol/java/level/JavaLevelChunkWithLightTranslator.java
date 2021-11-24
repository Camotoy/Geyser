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

package org.geysermc.geyser.translator.protocol.java.level;

import com.github.steveice10.mc.protocol.data.game.chunk.ChunkSection;
import com.github.steveice10.mc.protocol.data.game.chunk.DataPalette;
import com.github.steveice10.mc.protocol.data.game.level.block.BlockEntityInfo;
import com.github.steveice10.mc.protocol.data.game.level.block.BlockEntityType;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.level.ClientboundLevelChunkWithLightPacket;
import com.github.steveice10.opennbt.tag.builtin.CompoundTag;
import com.github.steveice10.packetlib.io.NetInput;
import com.github.steveice10.packetlib.io.stream.StreamNetInput;
import com.nukkitx.nbt.NBTOutputStream;
import com.nukkitx.nbt.NbtMap;
import com.nukkitx.nbt.NbtUtils;
import com.nukkitx.network.VarInts;
import com.nukkitx.protocol.bedrock.packet.LevelChunkPacket;
import com.nukkitx.protocol.bedrock.v475.Bedrock_v475;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufOutputStream;
import org.geysermc.geyser.level.block.BlockStateValues;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.translator.level.BiomeTranslator;
import org.geysermc.geyser.translator.level.block.entity.BlockEntityTranslator;
import org.geysermc.geyser.translator.level.block.entity.SkullBlockEntityTranslator;
import org.geysermc.geyser.translator.protocol.PacketTranslator;
import org.geysermc.geyser.translator.protocol.Translator;
import org.geysermc.geyser.util.BlockEntityUtils;
import org.geysermc.geyser.util.ChunkUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

import static org.geysermc.geyser.util.ChunkUtils.*;

@Translator(packet = ClientboundLevelChunkWithLightPacket.class)
public class JavaLevelChunkWithLightTranslator extends PacketTranslator<ClientboundLevelChunkWithLightPacket> {

    @Override
    public void translate(GeyserSession session, ClientboundLevelChunkWithLightPacket packet) {
        if (session.isSpawned()) {
            ChunkUtils.updateChunkPosition(session, session.getPlayerEntity().getPosition().toInt());
        }

        // Ensure that, if the player is using lower world heights, the position is not offset
        int yOffset = session.getChunkCache().getChunkMinY();
        int chunkSize = session.getChunkCache().getChunkHeightY();
        int biomeGlobalPalette = session.getBiomeGlobalPalette();

        // Temporarily stores compound tags of Bedrock-only block entities
        List<NbtMap> bedrockOnlyBlockEntities = new ArrayList<>();
        DataPalette[] javaChunks = new DataPalette[chunkSize];
        DataPalette[] javaBiomes = new DataPalette[chunkSize];

        BitSet waterloggedPaletteIds = new BitSet();
        BitSet pistonOrFlowerPaletteIds = new BitSet();

        boolean overworld = session.getChunkCache().isExtendedHeight();
        int maxBedrockSectionY = ((overworld ? MAXIMUM_ACCEPTED_HEIGHT_OVERWORLD : MAXIMUM_ACCEPTED_HEIGHT) >> 4) - 1;

        int sectionCount;
        byte[] payload;
        ByteBuf byteBuf = null;
        //GeyserChunkSection[] sections = new GeyserChunkSection[javaChunks.length - (yOffset + ((overworld ? MINIMUM_ACCEPTED_HEIGHT_OVERWORLD : MINIMUM_ACCEPTED_HEIGHT) >> 4))];

        try {
            NetInput in = new StreamNetInput(new ByteArrayInputStream(packet.getChunkData()));
            for (int sectionY = 0; sectionY < chunkSize; sectionY++) {
                ChunkSection javaSection = ChunkSection.read(in, biomeGlobalPalette);
                javaChunks[sectionY] = javaSection.getChunkData();
                javaBiomes[sectionY] = javaSection.getBiomeData();

                int bedrockSectionY = sectionY + (yOffset - ((overworld ? MINIMUM_ACCEPTED_HEIGHT_OVERWORLD : MINIMUM_ACCEPTED_HEIGHT) >> 4));
                if (bedrockSectionY < 0 || maxBedrockSectionY < bedrockSectionY) {
                    // Ignore this chunk section since it goes outside the bounds accepted by the Bedrock client
                    continue;
                }

                // TODO do we need to do a pass for Bedrock only block entities
            }

            session.getChunkCache().addToCache(packet.getX(), packet.getZ(), javaChunks);

            BlockEntityInfo[] blockEntities = packet.getBlockEntities();
            NbtMap[] bedrockBlockEntities = new NbtMap[blockEntities.length + bedrockOnlyBlockEntities.size()];
            int blockEntityCount = 0;
            final int chunkBlockX = packet.getX() << 4;
            final int chunkBlockZ = packet.getZ() << 4;
            while (blockEntityCount < blockEntities.length) {
                BlockEntityInfo blockEntity = blockEntities[blockEntityCount];
                CompoundTag tag = blockEntity.getNbt();
                BlockEntityType type = blockEntity.getType();
                int x = blockEntity.getX(); // Relative to chunk
                int y = blockEntity.getY();
                int z = blockEntity.getZ(); // Relative to chunk

                // Get the Java block state ID from block entity position
                DataPalette section = javaChunks[(y >> 4) - yOffset];
                int blockState = section.get(x, y & 0xF, z);

                if (type == BlockEntityType.LECTERN && BlockStateValues.getLecternBookStates().get(blockState)) {
                    // If getLecternBookStates is false, let's just treat it like a normal block entity
                    bedrockBlockEntities[blockEntityCount++] = session.getGeyser().getWorldManager().getLecternDataAt(
                            session, x + chunkBlockX, y, z + chunkBlockZ, true);
                    continue;
                }

                BlockEntityTranslator blockEntityTranslator = BlockEntityUtils.getBlockEntityTranslator(type);
                bedrockBlockEntities[blockEntityCount] = blockEntityTranslator.getBlockEntityTag(type, x + chunkBlockX, y, z + chunkBlockZ, tag, blockState);

                // Check for custom skulls
                if (session.getPreferencesCache().showCustomSkulls() && type == BlockEntityType.SKULL && tag != null && tag.contains("SkullOwner")) {
                    SkullBlockEntityTranslator.spawnPlayer(session, tag, x + chunkBlockX, y, z + chunkBlockZ, blockState);
                }
                blockEntityCount++;
            }

            // Append Bedrock-exclusive block entities to output array
            for (NbtMap tag : bedrockOnlyBlockEntities) {
                bedrockBlockEntities[blockEntityCount] = tag;
                blockEntityCount++;
            }

            // Find highest section
            /*sectionCount = sections.length - 1;
            while (sectionCount >= 0 && sections[sectionCount] == null) {
                sectionCount--;
            }
            sectionCount++;*/

            // Estimate chunk size
            int size = 0;
            /*for (int i = 0; i < sectionCount; i++) {
                GeyserChunkSection section = sections[i];
                if (section != null) {
                    size += section.estimateNetworkSize();
                } else {
                    size += SERIALIZED_CHUNK_DATA.length;
                }
            }*/
            size += ChunkUtils.EMPTY_CHUNK_DATA.length; // Consists only of biome data
            size += 1; // Border blocks
            size += 1; // Extra data length (always 0)
            size += bedrockBlockEntities.length * 64; // Conservative estimate of 64 bytes per tile entity

            // Allocate output buffer
            byteBuf = ByteBufAllocator.DEFAULT.buffer(size);
            /*for (int i = 0; i < sectionCount; i++) {
                GeyserChunkSection section = sections[i];
                if (section != null) {
                    section.writeToNetwork(byteBuf);
                } else {
                    byteBuf.writeBytes(SERIALIZED_CHUNK_DATA);
                }
            }*/

            // At this point we're dealing with Bedrock chunk sections
            int dimensionOffset = (overworld ? MINIMUM_ACCEPTED_HEIGHT_OVERWORLD : MINIMUM_ACCEPTED_HEIGHT) >> 4;
            for (int i = 0; i < javaChunks.length; i++) {
                int biomeYOffset = dimensionOffset + i;
                if (biomeYOffset < yOffset) {
                    // Ignore this biome section since it goes above or below the height of the Java world
                    byteBuf.writeBytes(ChunkUtils.EMPTY_BIOME_DATA);
                    continue;
                }
                BiomeTranslator.toNewBedrockBiome(session, javaBiomes[i + (dimensionOffset - yOffset)]).writeToNetwork(byteBuf);
            }

            // As of 1.17.10, Bedrock hardcodes to always read 32 biome sections
            // As of 1.18, this hardcode was lowered to 25
            if (session.getUpstream().getProtocolVersion() >= Bedrock_v475.V475_CODEC.getProtocolVersion()) {
                int remainingEmptyBiomes = 25 - javaChunks.length;
                for (int i = 0; i < remainingEmptyBiomes; i++) {
                    // A header that says to carry on the biome data from the previous chunk
                    // This notably fixes biomes in the End
                    byteBuf.writeByte((127 << 1) | 1);
                }
            } else {
                int remainingEmptyBiomes = 32 - javaChunks.length;
                for (int i = 0; i < remainingEmptyBiomes; i++) {
                    byteBuf.writeBytes(ChunkUtils.EMPTY_BIOME_DATA);
                }
            }

            byteBuf.writeByte(0); // Border blocks - Edu edition only
            VarInts.writeUnsignedInt(byteBuf, 0); // extra data length, 0 for now

            // Encode tile entities into buffer
            NBTOutputStream nbtStream = NbtUtils.createNetworkWriter(new ByteBufOutputStream(byteBuf));
            for (NbtMap blockEntity : bedrockBlockEntities) {
                nbtStream.writeTag(blockEntity);
            }

            // Copy data into byte[], because the protocol lib really likes things that are s l o w
            byteBuf.readBytes(payload = new byte[byteBuf.readableBytes()]);
        } catch (IOException e) {
            session.getGeyser().getLogger().error("IO error while encoding chunk", e);
            return;
        } finally {
            if (byteBuf != null) {
                byteBuf.release(); // Release buffer to allow buffer pooling to be useful
            }
        }

        LevelChunkPacket levelChunkPacket = new LevelChunkPacket();
        // We want the client to deliberately request any chunks it wants
        levelChunkPacket.setSubChunksLength(-1);
        levelChunkPacket.setCachingEnabled(false);
        levelChunkPacket.setChunkX(packet.getX());
        levelChunkPacket.setChunkZ(packet.getZ());
        levelChunkPacket.setData(payload);
        session.sendUpstreamPacket(levelChunkPacket);

        /*for (Map.Entry<Vector3i, ItemFrameEntity> entry : session.getItemFrameCache().entrySet()) {
            Vector3i position = entry.getKey();
            if ((position.getX() >> 4) == packet.getX() && (position.getZ() >> 4) == packet.getZ()) {
                // Update this item frame so it doesn't get lost in the abyss
                //TODO optimize
                entry.getValue().updateBlock(true);
            }
        }*/
    }
}