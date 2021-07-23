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

package org.geysermc.connector.network.translators.bedrock;

import com.nukkitx.nbt.NbtMap;
import com.nukkitx.protocol.bedrock.data.entity.EntityData;
import org.geysermc.connector.entity.Entity;
import org.geysermc.connector.entity.living.ArmorStandEntity;
import org.geysermc.connector.network.session.GeyserSession;
import org.geysermc.connector.network.translators.PacketTranslator;
import org.geysermc.connector.network.translators.Translator;

import com.github.steveice10.mc.protocol.packet.ingame.client.ClientChatPacket;
import com.nukkitx.protocol.bedrock.packet.TextPacket;
import org.geysermc.connector.network.translators.chat.MessageTranslator;

@Translator(packet = TextPacket.class)
public class BedrockTextTranslator extends PacketTranslator<TextPacket> {
    private int number = 2;

    @Override
    public void translate(TextPacket packet, GeyserSession session) {
        String message = packet.getMessage();

        if (message.equals("beans")) {
            number = number == 1 ? 2 : 1;
            for (Entity entity : session.getEntityCache().getEntitiesByType(ArmorStandEntity.class)) {
                entity.getMetadata().putTag(EntityData.BASE_RUNTIME_ID, NbtMap.builder()
                        .putInt("property:number_enum_example", number)
                        .putBoolean("property:boolean_enum_example", false)
                        .putInt("property:number_range_example", 0)
                        .build());
                entity.updateBedrockMetadata(session);
            }
        }

        if (MessageTranslator.isTooLong(message, session)) {
            return;
        }

        ClientChatPacket chatPacket = new ClientChatPacket(message);
        session.sendDownstreamPacket(chatPacket);
    }
}
