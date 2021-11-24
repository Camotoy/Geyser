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

package org.geysermc.geyser.translator.protocol.bedrock.world;

import com.github.steveice10.mc.protocol.data.game.chunk.DataPalette;
import com.nukkitx.math.vector.Vector3i;
import com.nukkitx.protocol.bedrock.data.HeightMapDataType;
import com.nukkitx.protocol.bedrock.data.SubChunkRequestResult;
import com.nukkitx.protocol.bedrock.packet.SubChunkPacket;
import com.nukkitx.protocol.bedrock.packet.SubChunkRequestPacket;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import org.geysermc.geyser.level.chunk.GeyserChunk;
import org.geysermc.geyser.level.chunk.GeyserChunkSection;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.translator.protocol.PacketTranslator;
import org.geysermc.geyser.translator.protocol.Translator;
import org.geysermc.geyser.util.ChunkUtils;
import org.geysermc.geyser.util.DimensionUtils;

@Translator(packet = SubChunkRequestPacket.class)
public class BedrockSubChunkRequestTranslator extends PacketTranslator<SubChunkRequestPacket> {

    @Override
    public void translate(GeyserSession session, SubChunkRequestPacket packet) {
        SubChunkPacket responsePacket = new SubChunkPacket();
        Vector3i chunkPosition = packet.getSubChunkPosition();
        responsePacket.setSubChunkPosition(chunkPosition);
        responsePacket.setHeightMapType(HeightMapDataType.NO_DATA);
        int bedrockDimension = DimensionUtils.javaToBedrock(session.getDimension());
        if (packet.getDimension() != bedrockDimension) {
            // We can't provide chunks for this
            responsePacket.setResult(SubChunkRequestResult.INVALID_DIMENSION);
            responsePacket.setData(new byte[0]);
            session.sendUpstreamPacket(responsePacket);
            return;
        }

        GeyserChunk chunk = session.getChunkCache().getChunk(chunkPosition.getX(), chunkPosition.getZ());
        if (chunk == null) {
            responsePacket.setResult(SubChunkRequestResult.CHUNK_NOT_FOUND);
            responsePacket.setData(new byte[0]);
            session.sendUpstreamPacket(responsePacket);
            return;
        }
        int javaYOffset = session.getChunkCache().getChunkMinY();
        int bedrockYOffset = (bedrockDimension == 0 ? ChunkUtils.MINIMUM_ACCEPTED_HEIGHT_OVERWORLD : ChunkUtils.MINIMUM_ACCEPTED_HEIGHT) >> 4;
        //todo figure out maths
        int chunkOffset = chunkPosition.getY() - bedrockYOffset;
        if (chunkOffset >= chunk.sections().length) {
            responsePacket.setResult(SubChunkRequestResult.INDEX_OUT_OF_BOUNDS);
            responsePacket.setData(new byte[0]);
            session.sendUpstreamPacket(responsePacket);
            return;
        }
        DataPalette palette = chunk.sections()[chunkOffset];
        GeyserChunkSection bedrockSection = ChunkUtils.translateChunkToBedrock(session, palette);

        responsePacket.setResult(SubChunkRequestResult.SUCCESS);
        if (bedrockSection != null) {
            ByteBuf byteBuf = ByteBufAllocator.DEFAULT.buffer(bedrockSection.estimateNetworkSize());
            byte[] data;
            try {
                bedrockSection.writeToNetwork(byteBuf);
                byteBuf.readBytes(data = new byte[byteBuf.readableBytes()]);
            } finally {
                byteBuf.release();
            }
            responsePacket.setData(data);
        } else {
            responsePacket.setData(ChunkUtils.EMPTY_BIOME_DATA);
        }
        session.sendUpstreamPacket(responsePacket);
    }
}
