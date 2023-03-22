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

package org.geysermc.geyser.platform.spigot.world.manager;

import com.github.steveice10.mc.protocol.data.game.level.block.BlockEntityInfo;
import com.github.steveice10.opennbt.tag.builtin.CompoundTag;
import com.nukkitx.nbt.NbtMap;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.geysermc.erosion.bukkit.BukkitLecterns;
import org.geysermc.erosion.bukkit.BukkitUtils;
import org.geysermc.erosion.bukkit.PickBlockUtils;
import org.geysermc.geyser.level.GameRule;
import org.geysermc.geyser.level.WorldManager;
import org.geysermc.geyser.level.block.BlockStateValues;
import org.geysermc.geyser.registry.BlockRegistries;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.util.BlockEntityUtils;
import org.jetbrains.annotations.Nullable;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * The base world manager to use when there is no supported NMS revision
 */
public class GeyserSpigotWorldManager extends WorldManager {
    private final Plugin plugin;
    private final BukkitLecterns lecterns;

    public GeyserSpigotWorldManager(Plugin plugin) {
        this.plugin = plugin;
        this.lecterns = new BukkitLecterns(plugin);
    }

    @Override
    public int getBlockAt(GeyserSession session, int x, int y, int z) {
        Player bukkitPlayer;
        if ((bukkitPlayer = Bukkit.getPlayer(session.getPlayerEntity().getUsername())) == null) {
            return BlockStateValues.JAVA_AIR_ID;
        }
        World world = bukkitPlayer.getWorld();
        if (!world.isChunkLoaded(x >> 4, z >> 4)) {
            // If the chunk isn't loaded, how could we even be here?
            return BlockStateValues.JAVA_AIR_ID;
        }

        return getBlockNetworkId(world.getBlockAt(x, y, z));
    }

    public int getBlockNetworkId(Block block) {
        return BlockRegistries.JAVA_IDENTIFIERS.getOrDefault(block.getBlockData().getAsString(), BlockStateValues.JAVA_AIR_ID);
    }

    @Override
    public boolean hasOwnChunkCache() {
        return true;
    }

    @Override
    public void sendLecternData(GeyserSession session, int x, int y, int z) {
        // Run as a task to prevent async issues
        Bukkit.getScheduler().runTask(this.plugin, () -> {
            Player bukkitPlayer;
            if ((bukkitPlayer = Bukkit.getPlayer(session.getPlayerEntity().getUsername())) == null) {
                return;
            }

            Block block = bukkitPlayer.getWorld().getBlockAt(x, y, z);
            sendLecternData(session, block, false);
        });
    }

    public void sendLecternData(GeyserSession session, int x, int z, List<BlockEntityInfo> blockEntityInfos) {
        Bukkit.getScheduler().runTask(this.plugin, () -> {
            Player bukkitPlayer;
            if ((bukkitPlayer = Bukkit.getPlayer(session.getPlayerEntity().getUsername())) == null) {
                return;
            }

            // TODO
            World world = bukkitPlayer.getWorld();
            if (!world.isChunkLoaded(x, z)) {
                return;
            }
            Chunk chunk = world.getChunkAt(x, z);
            for (int i = 0; i < blockEntityInfos.size(); i++) {
                BlockEntityInfo info = blockEntityInfos.get(i);
                Block block = chunk.getBlock(info.getX(), info.getY(), info.getZ());
                sendLecternData(session, block, true);
            }
        });
    }

    private void sendLecternData(GeyserSession session, Block block, boolean isChunkLoad) {
        NbtMap blockEntityTag = this.lecterns.getLecternData(block, isChunkLoad);
        if (blockEntityTag != null) {
            BlockEntityUtils.updateBlockEntity(session, blockEntityTag, BukkitUtils.getVector(block.getLocation()));
        }
    }

    @Override
    public boolean shouldExpectLecternHandled(GeyserSession session) {
        return true;
    }

    public boolean getGameRuleBool(GeyserSession session, GameRule gameRule) {
        String value = Bukkit.getPlayer(session.getPlayerEntity().getUsername()).getWorld().getGameRuleValue(gameRule.getJavaID());
        if (!value.isEmpty()) {
            return Boolean.parseBoolean(value);
        }
        return gameRule.getDefaultBooleanValue();
    }

    @Override
    public int getGameRuleInt(GeyserSession session, GameRule gameRule) {
        String value = Bukkit.getPlayer(session.getPlayerEntity().getUsername()).getWorld().getGameRuleValue(gameRule.getJavaID());
        if (!value.isEmpty()) {
            return Integer.parseInt(value);
        }
        return gameRule.getDefaultIntValue();
    }

    @Override
    public boolean hasPermission(GeyserSession session, String permission) {
        return Bukkit.getPlayer(session.getPlayerEntity().getUsername()).hasPermission(permission);
    }

    @Nonnull
    @Override
    public CompletableFuture<@Nullable CompoundTag> getPickItemNbt(GeyserSession session, int x, int y, int z, boolean addNbtData) {
        CompletableFuture<@Nullable CompoundTag> future = new CompletableFuture<>();
        // Paper 1.19.3 complains about async access otherwise.
        // java.lang.IllegalStateException: Tile is null, asynchronous access?
        Bukkit.getScheduler().runTask(this.plugin, () -> {
            Player bukkitPlayer;
            if ((bukkitPlayer = Bukkit.getPlayer(session.getPlayerEntity().getUuid())) == null) {
                future.complete(null);
                return;
            }

            future.complete(PickBlockUtils.pickBlock(bukkitPlayer, x, y, z));
        });
        return future;
    }

    /**
     * This should be set to true if we are post-1.13 but before the latest version, and we should convert the old block state id
     * to the current one.
     *
     * @return whether there is a difference between client block state and server block state that requires extra processing
     */
    public boolean isLegacy() {
        return false;
    }
}
