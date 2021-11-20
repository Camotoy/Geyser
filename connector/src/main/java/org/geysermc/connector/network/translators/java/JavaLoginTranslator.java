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

package org.geysermc.connector.network.translators.java;

import com.github.steveice10.mc.protocol.data.game.entity.player.HandPreference;
import com.github.steveice10.mc.protocol.data.game.setting.ChatVisibility;
import com.github.steveice10.mc.protocol.data.game.setting.SkinPart;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.ClientboundLoginPacket;
import com.github.steveice10.mc.protocol.packet.ingame.serverbound.ServerboundClientInformationPacket;
import com.github.steveice10.mc.protocol.packet.ingame.serverbound.ServerboundCustomPayloadPacket;
import com.github.steveice10.opennbt.tag.builtin.CompoundTag;
import com.github.steveice10.opennbt.tag.builtin.ListTag;
import com.nukkitx.protocol.bedrock.data.GameRuleData;
import com.nukkitx.protocol.bedrock.data.PlayerPermission;
import com.nukkitx.protocol.bedrock.packet.AdventureSettingsPacket;
import com.nukkitx.protocol.bedrock.packet.GameRulesChangedPacket;
import com.nukkitx.protocol.bedrock.packet.SetPlayerGameTypePacket;
import org.geysermc.connector.common.AuthType;
import org.geysermc.connector.entity.player.PlayerEntity;
import org.geysermc.connector.network.session.GeyserSession;
import org.geysermc.connector.network.translators.PacketTranslator;
import org.geysermc.connector.network.translators.Translator;
import org.geysermc.connector.network.translators.world.BiomeTranslator;
import org.geysermc.connector.utils.ChunkUtils;
import org.geysermc.connector.utils.DimensionUtils;
import org.geysermc.connector.utils.MathUtils;
import org.geysermc.connector.utils.PluginMessageUtils;

import java.util.Arrays;
import java.util.List;

@Translator(packet = ClientboundLoginPacket.class)
public class JavaLoginTranslator extends PacketTranslator<ClientboundLoginPacket> {
    private static final List<SkinPart> SKIN_PART_VALUES = Arrays.asList(SkinPart.values());

    @Override
    public void translate(GeyserSession session, ClientboundLoginPacket packet) {
        // Load this information now so chunk information pre-loaded outside of the thread is also in sync
        CompoundTag dimensionTag = packet.getDimensionCodec();
        CompoundTag worldGen = dimensionTag.get("minecraft:worldgen/biome");
        ListTag serverBiomes = worldGen.get("value");
        session.setBiomeGlobalPalette(MathUtils.getGlobalPaletteForSize(serverBiomes.size()));

        session.getChunkCache().setHeightY(((Number) packet.getDimension().get("height").getValue()).intValue());

        session.executeInEventLoop(() -> translate(session, packet, serverBiomes));
    }

    public void translate(GeyserSession session, ClientboundLoginPacket packet, ListTag biomes) {
        PlayerEntity entity = session.getPlayerEntity();
        entity.setEntityId(packet.getEntityId());

        // If the player is already initialized and a join game packet is sent, they
        // are swapping servers
        String newDimension = DimensionUtils.getNewDimension(packet.getDimension());
        if (session.isSpawned()) {
            String fakeDim = DimensionUtils.getTemporaryDimension(session.getDimension(), newDimension);
            DimensionUtils.switchDimension(session, fakeDim);

            session.getWorldCache().removeScoreboard();
        }
        session.setWorldName(packet.getWorldName());

        BiomeTranslator.loadServerBiomes(session, biomes);
        session.getTagCache().clear();

        session.setGameMode(packet.getGameMode());

        boolean needsSpawnPacket = !session.isSentSpawnPacket();
        if (needsSpawnPacket) {
            // The player has yet to spawn so let's do that using some of the information in this Java packet
            session.setDimension(newDimension);
            session.connect();
        }

        AdventureSettingsPacket bedrockPacket = new AdventureSettingsPacket();
        bedrockPacket.setUniqueEntityId(session.getPlayerEntity().getGeyserId());
        bedrockPacket.setPlayerPermission(PlayerPermission.MEMBER);
        session.sendUpstreamPacket(bedrockPacket);

        if (!needsSpawnPacket) {
            SetPlayerGameTypePacket playerGameTypePacket = new SetPlayerGameTypePacket();
            playerGameTypePacket.setGamemode(packet.getGameMode().ordinal());
            session.sendUpstreamPacket(playerGameTypePacket);
        }

        entity.updateBedrockMetadata();

        // Send if client should show respawn screen
        GameRulesChangedPacket gamerulePacket = new GameRulesChangedPacket();
        gamerulePacket.getGameRules().add(new GameRuleData<>("doimmediaterespawn", !packet.isEnableRespawnScreen()));
        session.sendUpstreamPacket(gamerulePacket);

        session.setReducedDebugInfo(packet.isReducedDebugInfo());

        session.setRenderDistance(packet.getViewDistance());

        // We need to send our skin parts to the server otherwise java sees us with no hat, jacket etc
        String locale = session.getLocale();
        // TODO customize
        ServerboundClientInformationPacket infoPacket = new ServerboundClientInformationPacket(locale, (byte) session.getRenderDistance(), ChatVisibility.FULL, true, SKIN_PART_VALUES, HandPreference.RIGHT_HAND, false, true);
        session.sendDownstreamPacket(infoPacket);

        session.sendDownstreamPacket(new ServerboundCustomPayloadPacket("minecraft:brand", PluginMessageUtils.getGeyserBrandData()));

        // register the plugin messaging channels used in Floodgate
        if (session.getRemoteAuthType() == AuthType.FLOODGATE) {
            session.sendDownstreamPacket(new ServerboundCustomPayloadPacket("minecraft:register", PluginMessageUtils.getFloodgateRegisterData()));
        }

        if (!newDimension.equals(session.getDimension())) {
            DimensionUtils.switchDimension(session, newDimension);
        }

        ChunkUtils.loadDimensionTag(session, packet.getDimension());
    }
}
