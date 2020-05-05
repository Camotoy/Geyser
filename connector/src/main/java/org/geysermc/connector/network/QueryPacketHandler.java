/*
 * Copyright (c) 2019-2020 GeyserMC. http://geysermc.org
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *  THE SOFTWARE.
 *
 *  @author GeyserMC
 *  @link https://github.com/GeyserMC/Geyser
 *
 */

package org.geysermc.connector.network;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.DatagramPacket;
import org.geysermc.common.ping.GeyserPingInfo;
import org.geysermc.common.ping.IGeyserPingPassthrough;
import org.geysermc.connector.GeyserConnector;
import org.geysermc.connector.utils.Binary;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

public class QueryPacketHandler {

    public static final byte HANDSHAKE = 0x09;
    public static final byte STATISTICS = 0x00;

    private GeyserConnector connector;
    private InetSocketAddress sender;
    private byte type;
    private int sessionId;
    private int payload;
    private byte[] token;

    /**
     * The Query packet handler instance
     * @param connector Geyser Connector
     * @param sender The Sender IP/Port for the Query
     * @param bytes The Query data
     */
    public QueryPacketHandler(GeyserConnector connector, InetSocketAddress sender, byte[] bytes) {
        if(bytes.length == 0 || !isQueryPacket(bytes))
            return;

        this.connector = connector;
        this.sender = sender;
        this.type = bytes[2];
        this.sessionId = Binary.readInt(Binary.subBytes(bytes, 3, 4));
        if(bytes.length > 7)
            this.payload = Binary.readInt(Binary.subBytes(bytes, 7, 4));

        regenerateToken();
        handle();
    }

    /**
     * Checks the packet is in fact a query packet
     * @param bytes Query data
     * @return
     */
    private boolean isQueryPacket(byte[] bytes) {
        return Binary.readShort(Binary.subBytes(bytes, 0, 2)) == 65277;
    }

    /**
     * Handles the query
     */
    private void handle() {
        switch (type) {
            case HANDSHAKE:
                sendToken();
            case STATISTICS:
                sendQueryData();
        }
    }

    /**
     * Sends the token to the sender
     */
    private void sendToken() {
        ByteBuf reply = ByteBufAllocator.DEFAULT.ioBuffer(10);
        reply.writeByte(HANDSHAKE);
        reply.writeInt(sessionId);
        reply.writeBytes(getTokenString(this.token, this.sender.getAddress()));
        reply.writeByte(0);

        sendPacket(reply);
    }

    /**
     * Sends the query data to the sender
     */
    private void sendQueryData() {
        ByteBuf reply = ByteBufAllocator.DEFAULT.ioBuffer(64);
        reply.writeByte(STATISTICS);
        reply.writeInt(sessionId);

        // Game Info
        reply.writeBytes(getGameData());

        // Players
        reply.writeBytes(new byte[]{0x00, 0x01});
        reply.writeBytes("player_".getBytes());
        reply.writeBytes(new byte[]{0x00, 0x00});
        reply.writeBytes("james090500".getBytes());
        reply.writeByte((byte) 0x00);

        reply.writeByte((byte) 0x00);
        sendPacket(reply);
    }

    /**
     * Gets the game data for the query
     * @return
     */
    private byte[] getGameData() {
        ByteArrayOutputStream query = new ByteArrayOutputStream();

        GeyserPingInfo pingInfo = null;
        String currentPlayerCount;
        String maxPlayerCount;

        //If ping pass through is enabled lets get players from the server
        if (connector.getConfig().isPingPassthrough()) {
            IGeyserPingPassthrough pingPassthrough = connector.getBootstrap().getGeyserPingPassthrough();
            pingInfo = pingPassthrough.getPingInformation();
            currentPlayerCount = String.valueOf(pingInfo.currentPlayerCount);
            maxPlayerCount = String.valueOf(pingInfo.maxPlayerCount);
        } else {
            currentPlayerCount = String.valueOf(connector.getPlayers().size());
            maxPlayerCount = String.valueOf(connector.getConfig().getMaxPlayers());
        }

        //Create a hashmap of all game data needed in the query
        HashMap<String, String> gameData = new HashMap<String, String>();
        gameData.put("hostname", connector.getConfig().getBedrock().getMotd1());
        gameData.put("gametype", "SMP");
        gameData.put("game_id", "MINECRAFT");
        gameData.put("version", connector.BEDROCK_PACKET_CODEC.getMinecraftVersion());
        gameData.put("plugins", "");
        gameData.put("map", connector.NAME);
        gameData.put("numplayers", currentPlayerCount);
        gameData.put("maxplayers", maxPlayerCount);
        gameData.put("hostport", String.valueOf(connector.getConfig().getBedrock().getPort()));
        gameData.put("hostip", connector.getConfig().getBedrock().getAddress());

        try {
            //Blank Buffer Bytes
            query.write("GeyserMC".getBytes());
            query.write((byte) 0x00);
            query.write((byte) 128);
            query.write((byte) 0x00);

            //Fills the game data
            for(Map.Entry<String, String> entry : gameData.entrySet()) {
                query.write(entry.getKey().getBytes());
                query.write((byte) 0x00);
                query.write(entry.getValue().getBytes());
                query.write((byte) 0x00);
            }
            return query.toByteArray();
        } catch (IOException e) {
            e.printStackTrace();
            return new byte[0];
        }
    }

    /**
     * Sends a packet to the sender
     * @param data packet data
     */
    private void sendPacket(ByteBuf data) {
        connector.getBedrockServer().getRakNet().send(sender, data);
    }

    /**
     * Regenerates a token
     */
    public void regenerateToken() {
        byte[] token = new byte[16];
        for (int i = 0; i < 16; i++) {
            token[i] = (byte) new Random().nextInt(255);
        }

        for (int i = 0; i < 16; i++) {
            System.out.print(token[i] + ", ");
        }

        this.token = token;
    }

    /**
     * ALL CODE BELOW IS NUKKIT, THIS WILL NEED REPLACING
     */
    public static byte[] getTokenString(String token, InetAddress address) {
        return getTokenString(token.getBytes(StandardCharsets.UTF_8), address);
    }

    public static byte[] getTokenString(byte[] token, InetAddress address) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            digest.update(address.toString().getBytes(StandardCharsets.UTF_8));
            digest.update(token);
            return Arrays.copyOf(digest.digest(), 4);
        } catch (NoSuchAlgorithmException e) {
            return ByteBuffer.allocate(4).putInt(ThreadLocalRandom.current().nextInt()).array();
        }
    }
}
