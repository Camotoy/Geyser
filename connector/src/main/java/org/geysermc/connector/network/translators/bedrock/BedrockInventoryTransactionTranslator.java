/*
 * Copyright (c) 2019-2020 GeyserMC. http://geysermc.org
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

import com.github.steveice10.mc.protocol.data.game.entity.metadata.ItemStack;
import com.github.steveice10.mc.protocol.data.game.entity.metadata.Position;
import com.github.steveice10.mc.protocol.data.game.entity.player.GameMode;
import com.github.steveice10.mc.protocol.data.game.entity.player.Hand;
import com.github.steveice10.mc.protocol.data.game.entity.player.InteractAction;
import com.github.steveice10.mc.protocol.data.game.entity.player.PlayerAction;
import com.github.steveice10.mc.protocol.data.game.world.block.BlockFace;
import com.github.steveice10.mc.protocol.packet.ingame.client.player.ClientPlayerActionPacket;
import com.github.steveice10.mc.protocol.packet.ingame.client.player.ClientPlayerInteractEntityPacket;
import com.github.steveice10.mc.protocol.packet.ingame.client.player.ClientPlayerPlaceBlockPacket;
import com.github.steveice10.mc.protocol.packet.ingame.client.player.ClientPlayerUseItemPacket;
import com.nukkitx.math.vector.Vector3f;
import com.nukkitx.math.vector.Vector3i;
import com.nukkitx.protocol.bedrock.data.LevelEventType;
import com.nukkitx.protocol.bedrock.data.entity.EntityFlag;
import com.nukkitx.protocol.bedrock.data.inventory.ContainerType;
import com.nukkitx.protocol.bedrock.packet.ContainerOpenPacket;
import com.nukkitx.protocol.bedrock.packet.InventoryTransactionPacket;
import com.nukkitx.protocol.bedrock.packet.LevelEventPacket;
import com.nukkitx.protocol.bedrock.packet.UpdateBlockPacket;
import org.geysermc.connector.entity.CommandBlockMinecartEntity;
import org.geysermc.connector.entity.Entity;
import org.geysermc.connector.entity.ItemFrameEntity;
import org.geysermc.connector.entity.living.merchant.AbstractMerchantEntity;
import org.geysermc.connector.entity.type.EntityType;
import org.geysermc.connector.inventory.Inventory;
import org.geysermc.connector.network.session.GeyserSession;
import org.geysermc.connector.network.translators.PacketTranslator;
import org.geysermc.connector.network.translators.Translator;
import org.geysermc.connector.network.translators.inventory.InventoryTranslator;
import org.geysermc.connector.network.translators.item.ItemEntry;
import org.geysermc.connector.network.translators.item.ItemRegistry;
import org.geysermc.connector.network.translators.sound.EntitySoundInteractionHandler;
import org.geysermc.connector.network.translators.world.block.BlockTranslator;
import org.geysermc.connector.utils.InventoryUtils;

/**
 * BedrockInventoryTransactionTranslator handles most interactions between the client and the world,
 * or the client and their inventory.
 */
@Translator(packet = InventoryTransactionPacket.class)
public class BedrockInventoryTransactionTranslator extends PacketTranslator<InventoryTransactionPacket> {

    @Override
    public void translate(InventoryTransactionPacket packet, GeyserSession session) {
        switch (packet.getTransactionType()) {
            case NORMAL:
                Inventory inventory = session.getInventoryCache().getOpenInventory();
                if (inventory == null) inventory = session.getInventory();
                InventoryTranslator.INVENTORY_TRANSLATORS.get(inventory.getWindowType()).translateActions(session, inventory, packet.getActions());
                break;
            case INVENTORY_MISMATCH:
                Inventory inv = session.getInventoryCache().getOpenInventory();
                if (inv == null) inv = session.getInventory();
                InventoryTranslator.INVENTORY_TRANSLATORS.get(inv.getWindowType()).updateInventory(session, inv);
                InventoryUtils.updateCursor(session);
                break;
            case ITEM_USE:
                switch (packet.getActionType()) {
                    case 0:
                        System.out.println(packet);
                        Vector3i blockPos = packet.getBlockPosition();
                        // TODO: Find a better way to do this?
                        switch (packet.getBlockFace()) {
                            case 0:
                                blockPos = blockPos.sub(0, 1, 0);
                                break;
                            case 1:
                                blockPos = blockPos.add(0, 1, 0);
                                break;
                            case 2:
                                blockPos = blockPos.sub(0, 0, 1);
                                break;
                            case 3:
                                blockPos = blockPos.add(0, 0, 1);
                                break;
                            case 4:
                                blockPos = blockPos.sub(1, 0, 0);
                                break;
                            case 5:
                                blockPos = blockPos.add(1, 0, 0);
                                break;
                        }

                        boolean isValidInteraction = isValidInteraction(session, blockPos);
                        if (!isValidInteraction && session.getConnector().getConfig().isCacheChunks()) {
                            int cachedBlock = BlockTranslator.getBedrockBlockId(session.getConnector().getWorldManager().getBlockAt(session, blockPos));
                            if (packet.getBlockRuntimeId() != cachedBlock) {
                                // The block the player sent is invalid, revert it.
                                UpdateBlockPacket updateBlockPacket = new UpdateBlockPacket();
                                updateBlockPacket.setBlockPosition(blockPos);
                                updateBlockPacket.setDataLayer(0);
                                updateBlockPacket.setRuntimeId(cachedBlock);
                                session.sendUpstreamPacket(updateBlockPacket);
                            }
                        }
                        if (!isValidInteraction) break;

                        // Bedrock sends block interact code for a Java entity so we send entity code back to Java
                        if (BlockTranslator.isItemFrame(packet.getBlockRuntimeId()) &&
                                session.getEntityCache().getEntityByJavaId(ItemFrameEntity.getItemFrameEntityId(session, packet.getBlockPosition())) != null) {
                            Vector3f vector = packet.getClickPosition();
                            ClientPlayerInteractEntityPacket interactPacket = new ClientPlayerInteractEntityPacket((int) ItemFrameEntity.getItemFrameEntityId(session, packet.getBlockPosition()),
                                    InteractAction.INTERACT, Hand.MAIN_HAND, session.isSneaking());
                            ClientPlayerInteractEntityPacket interactAtPacket = new ClientPlayerInteractEntityPacket((int) ItemFrameEntity.getItemFrameEntityId(session, packet.getBlockPosition()),
                                    InteractAction.INTERACT_AT, vector.getX(), vector.getY(), vector.getZ(), Hand.MAIN_HAND, session.isSneaking());
                            session.sendDownstreamPacket(interactPacket);
                            session.sendDownstreamPacket(interactAtPacket);
                            break;
                        }

                        ClientPlayerPlaceBlockPacket blockPacket = new ClientPlayerPlaceBlockPacket(
                                new Position(packet.getBlockPosition().getX(), packet.getBlockPosition().getY(), packet.getBlockPosition().getZ()),
                                BlockFace.values()[packet.getBlockFace()],
                                Hand.MAIN_HAND,
                                packet.getClickPosition().getX(), packet.getClickPosition().getY(), packet.getClickPosition().getZ(),
                                false);
                        session.sendDownstreamPacket(blockPacket);

                        // Otherwise boats will not be able to be placed in survival and buckets wont work on mobile
                        // Check actions, otherwise buckets may be activated when block inventories are accessed
                        if (packet.getItemInHand() != null && (packet.getItemInHand().getId() == ItemRegistry.BOAT.getBedrockId() ||
                                packet.getItemInHand().getId() == ItemRegistry.BUCKET.getBedrockId()) && !packet.getActions().isEmpty()) {
                           ClientPlayerUseItemPacket itemPacket = new ClientPlayerUseItemPacket(Hand.MAIN_HAND);
                           session.sendDownstreamPacket(itemPacket);
                        }

                        if (packet.getActions().isEmpty()) {
                            if (session.getOpPermissionLevel() >= 2 && session.getGameMode() == GameMode.CREATIVE) {
                                // Otherwise insufficient permissions
                                int blockState = BlockTranslator.getJavaBlockState(packet.getBlockRuntimeId());
                                String blockName = BlockTranslator.getJavaIdBlockMap().inverse().getOrDefault(blockState, "");
                                // In the future this can be used for structure blocks too, however not all elements
                                // are available in each GUI
                                if (blockName.contains("jigsaw")) {
                                    ContainerOpenPacket openPacket = new ContainerOpenPacket();
                                    openPacket.setBlockPosition(packet.getBlockPosition());
                                    openPacket.setId((byte) 1);
                                    openPacket.setType(ContainerType.JIGSAW_EDITOR);
                                    openPacket.setUniqueEntityId(-1);
                                    session.sendUpstreamPacket(openPacket);
                                }
                            }
                        }

                        ItemEntry handItem = ItemRegistry.getItem(packet.getItemInHand());
                        if (handItem.isBlock()) {
                            session.setLastBlockPlacePosition(blockPos);
                            session.setLastBlockPlacedId(handItem.getJavaIdentifier());
                        }
                        session.setLastInteractionPosition(packet.getBlockPosition());
                        session.setInteracting(true);
                        break;
                    case 1:
                        ItemStack shieldSlot = session.getInventory().getItem(session.getInventory().getHeldItemSlot() + 36);
                        // Handled in Entity.java
                        if (shieldSlot != null && shieldSlot.getId() == ItemRegistry.SHIELD.getJavaId()) {
                            break;
                        }

                        // Handled in ITEM_USE
                        if (packet.getItemInHand() != null && packet.getItemInHand().getId() == ItemRegistry.BUCKET.getBedrockId()) {
                            break;
                        }

                        ClientPlayerUseItemPacket useItemPacket = new ClientPlayerUseItemPacket(Hand.MAIN_HAND);
                        session.sendDownstreamPacket(useItemPacket);
                        // Used for sleeping in beds
                        session.setLastInteractionPosition(packet.getBlockPosition());
                        break;
                    case 2:
                        boolean isValidBreakInteraction = isValidInteraction(session, packet.getBlockPosition());
                        if (!isValidBreakInteraction && session.getConnector().getConfig().isCacheChunks()) {
                            // Revert the broken block on the client's end
                            int correctBlockState = session.getConnector().getWorldManager().getBlockAt(session, packet.getBlockPosition());
                            UpdateBlockPacket updateBlockPacket = new UpdateBlockPacket();
                            updateBlockPacket.setBlockPosition(packet.getBlockPosition());
                            updateBlockPacket.setDataLayer(0);
                            updateBlockPacket.setRuntimeId(BlockTranslator.getBedrockBlockId(correctBlockState));
                            session.sendUpstreamPacket(updateBlockPacket);
                        }
                        if (!isValidBreakInteraction) break;
                        int blockState = session.getConnector().getWorldManager().getBlockAt(session, packet.getBlockPosition());
                        double blockHardness = BlockTranslator.JAVA_RUNTIME_ID_TO_HARDNESS.get(blockState);
                        if (session.getGameMode() == GameMode.CREATIVE || (session.getConnector().getConfig().isCacheChunks() && blockHardness == 0)) {
                            session.setLastBlockPlacedId(null);
                            session.setLastBlockPlacePosition(null);

                            LevelEventPacket blockBreakPacket = new LevelEventPacket();
                            blockBreakPacket.setType(LevelEventType.PARTICLE_DESTROY_BLOCK);
                            blockBreakPacket.setPosition(packet.getBlockPosition().toFloat());
                            blockBreakPacket.setData(BlockTranslator.getBedrockBlockId(blockState));
                            session.sendUpstreamPacket(blockBreakPacket);
                        }

                        if (ItemFrameEntity.positionContainsItemFrame(session, packet.getBlockPosition()) &&
                                session.getEntityCache().getEntityByJavaId(ItemFrameEntity.getItemFrameEntityId(session, packet.getBlockPosition())) != null) {
                            ClientPlayerInteractEntityPacket attackPacket = new ClientPlayerInteractEntityPacket((int) ItemFrameEntity.getItemFrameEntityId(session, packet.getBlockPosition()),
                                    InteractAction.ATTACK, session.isSneaking());
                            session.sendDownstreamPacket(attackPacket);
                            break;
                        }

                        PlayerAction action = session.getGameMode() == GameMode.CREATIVE ? PlayerAction.START_DIGGING : PlayerAction.FINISH_DIGGING;
                        Position pos = new Position(packet.getBlockPosition().getX(), packet.getBlockPosition().getY(), packet.getBlockPosition().getZ());
                        ClientPlayerActionPacket breakPacket = new ClientPlayerActionPacket(action, pos, BlockFace.values()[packet.getBlockFace()]);
                        session.sendDownstreamPacket(breakPacket);
                        break;
                }
                break;
            case ITEM_RELEASE:
                if (packet.getActionType() == 0) {
                    // Followed to the Minecraft Protocol specification outlined at wiki.vg
                    ClientPlayerActionPacket releaseItemPacket = new ClientPlayerActionPacket(PlayerAction.RELEASE_USE_ITEM, new Position(0,0,0),
                            BlockFace.DOWN);
                    session.sendDownstreamPacket(releaseItemPacket);
                }
                break;
            case ITEM_USE_ON_ENTITY:
                Entity entity = session.getEntityCache().getEntityByGeyserId(packet.getRuntimeEntityId());
                if (entity == null)
                    return;

                //https://wiki.vg/Protocol#Interact_Entity
                switch (packet.getActionType()) {
                    case 0: //Interact
                        if (entity instanceof CommandBlockMinecartEntity) {
                            // The UI is handled client-side on Java Edition
                            // Ensure OP permission level and gamemode is appropriate
                            if (session.getOpPermissionLevel() < 2 || session.getGameMode() != GameMode.CREATIVE) return;
                            ContainerOpenPacket openPacket = new ContainerOpenPacket();
                            openPacket.setBlockPosition(Vector3i.ZERO);
                            openPacket.setId((byte) 1);
                            openPacket.setType(ContainerType.COMMAND_BLOCK);
                            openPacket.setUniqueEntityId(entity.getGeyserId());
                            session.sendUpstreamPacket(openPacket);
                            break;
                        }
                        Vector3f vector = packet.getClickPosition();
                        ClientPlayerInteractEntityPacket interactPacket = new ClientPlayerInteractEntityPacket((int) entity.getEntityId(),
                                InteractAction.INTERACT, Hand.MAIN_HAND, session.isSneaking());
                        ClientPlayerInteractEntityPacket interactAtPacket = new ClientPlayerInteractEntityPacket((int) entity.getEntityId(),
                                InteractAction.INTERACT_AT, vector.getX(), vector.getY(), vector.getZ(), Hand.MAIN_HAND, session.isSneaking());
                        session.sendDownstreamPacket(interactPacket);
                        session.sendDownstreamPacket(interactAtPacket);

                        EntitySoundInteractionHandler.handleEntityInteraction(session, vector, entity);

                        if (entity instanceof AbstractMerchantEntity) {
                            session.setLastInteractedVillagerEid(packet.getRuntimeEntityId());
                        }
                        break;
                    case 1: //Attack
                        ClientPlayerInteractEntityPacket attackPacket = new ClientPlayerInteractEntityPacket((int) entity.getEntityId(),
                                InteractAction.ATTACK, session.isSneaking());
                        session.sendDownstreamPacket(attackPacket);
                        break;
                }
                break;
        }
    }

    /**
     * Determine if the current interaction is valid for a vanilla Java client
     * <br>
     * This is needed because mobile reach is insanely different.
     * @param session the current Geyser session of the player
     * @param position the position of the block
     * @return true if the interaction would be valid in Java Edition.
     */
    private boolean isValidInteraction(GeyserSession session, Vector3i position) {
        System.out.println("Block position: " + position);
        Vector3f measuredPosition = position.toFloat().add(0.5, 0.5, 0.5);
        // Determine the reach limit
        float distanceLimit;
        if (session.getGameMode() == GameMode.CREATIVE) {
            distanceLimit = 5.6f;
        } else {
            distanceLimit = 5.2f;
        }
        float offset;
        if (session.getPlayerEntity().getMetadata().getFlags().getFlag(EntityFlag.GLIDING) ||
            session.getPlayerEntity().getMetadata().getFlags().getFlag(EntityFlag.SWIMMING)) {
            offset = 0.4f;
        } else if (session.getPlayerEntity().getMetadata().getFlags().getFlag(EntityFlag.SNEAKING)) {
            offset = 1.27f;
        } else {
            offset = EntityType.PLAYER.getOffset();
        }
        Vector3f playerPosition = session.getPlayerEntity().getPosition().add(0, offset, 0);
        float distance = playerPosition.distance(measuredPosition);
        boolean valid = distance - distanceLimit <= 0;
        System.out.println("Was interaction valid? " + valid + " distance " + (distance - distanceLimit));
        return valid;
//        Vector2f directionPlane = (Vector2f.from((float) (-Math.cos(Math.toRadians(session.getPlayerEntity().getRotation().getY())
//                - Math.PI / 2)), (float) (-Math.sin(Math.toRadians(session.getPlayerEntity().getRotation().getY()) - Math.PI / 2)))).normalize();
//        double dot = directionPlane.dot(Vector2f.from(session.getPlayerEntity().getPosition().getX(), session.getPlayerEntity().getPosition().getY()));
//        double blockDot = directionPlane.dot(measuredPosition.getX(), measuredPosition.getZ());
//        boolean valid = (blockDot - dot) >= -5.6f;
//        System.out.println("Is valid? " + valid + " " + blockDot + " " + dot);
//        return valid;
    }
}
