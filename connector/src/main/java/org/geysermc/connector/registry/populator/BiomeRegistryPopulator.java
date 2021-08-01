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

package org.geysermc.connector.registry.populator;

import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.nukkitx.nbt.NbtMap;
import com.nukkitx.nbt.NbtMapBuilder;
import it.unimi.dsi.fastutil.ints.IntCollection;
import org.geysermc.connector.GeyserConnector;
import org.geysermc.connector.pack.datapack.BedrockServerBiome;
import org.geysermc.connector.pack.datapack.DatapackBiomeLoader;
import org.geysermc.connector.pack.datapack.GeyserDatapackCache;
import org.geysermc.connector.registry.Registries;
import org.geysermc.connector.registry.loader.RegistryLoaders;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class BiomeRegistryPopulator {

    public static void populate() {
        if (Registries.BIOMES_NBT != null) {
            // Reload protection
            return;
        }

        NbtMapBuilder builder = RegistryLoaders.NBT.load("bedrock/biome_definitions.dat").toBuilder();

        // Ideally, we cache datapacks so the randomly generated UUIDs can be preserved and players don't need to redownload the resource pack
        Path biomeSaveLocation = GeyserConnector.getInstance().getBootstrap().getConfigFolder().resolve("cached_datapacks.json");
        GeyserDatapackCache definitions;
        if (biomeSaveLocation.toFile().exists()) {
            try (InputStream stream = Files.newInputStream(biomeSaveLocation)) {
                definitions = GeyserConnector.JSON_MAPPER.readValue(stream, GeyserDatapackCache.class);
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
        } else {
            definitions = new GeyserDatapackCache();
        }

        List<File> datapacks = GeyserConnector.getInstance().getBootstrap().findDatapacks();

        List<String> obsoleteInfos = new ArrayList<>();
        for (Map.Entry<String, GeyserDatapackCache.DatapackInfo> entry : definitions.getDefinitions().entrySet()) {
            boolean contains = false;
            for (File file : datapacks) {
                if (file.getName().equals(entry.getKey())) {
                    contains = true;
                    break;
                }
            }

            if (!contains) {
                // File no longer exists; remove from cache
                obsoleteInfos.add(entry.getKey());
            }
        }
        for (String obsolete : obsoleteInfos) {
            definitions.getDefinitions().remove(obsolete);
        }

        List<BedrockServerBiome> serverBiomes = new ArrayList<>();
        if (GeyserConnector.getInstance().getConfig().isAddNonBedrockItems()) {
            for (File datapack : datapacks) {
                if (definitions.getIgnoredDatapacks().contains(datapack.getName())) {
                    // Don't process a datapack that we know doesn't contain anything relevant for us
                    continue;
                }

                try {
                    GeyserDatapackCache.DatapackInfo datapackInfo = definitions.getDefinitions().get(datapack.getName());
                    if (datapackInfo != null) {
                        // Load from the cache to re-use the Bedrock resource pack header
                        DatapackBiomeLoader.loadFromCache(datapackInfo);
                    } else {
                        // New datapack; let's generate information
                        datapackInfo = DatapackBiomeLoader.loadDatapack(datapack);
                        if (datapackInfo == null) {
                            definitions.getIgnoredDatapacks().add(datapack.getName());
                            continue;
                        }

                        definitions.getDefinitions().put(datapack.getName(), datapackInfo);
                    }
                    serverBiomes.addAll(datapackInfo.getServerBiomes());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        if (!definitions.getDefinitions().isEmpty() || !obsoleteInfos.isEmpty()) {
            // Obsolete infos not being empty means that the datapack cache is now empty
            try (OutputStream stream = Files.newOutputStream(biomeSaveLocation)) {
                GeyserConnector.JSON_MAPPER.writer(new DefaultPrettyPrinter()).writeValue(stream, definitions);
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
        }

        serverBiomes.sort(BedrockServerBiome::compareTo);

        if (!serverBiomes.isEmpty()) {
            for (BedrockServerBiome biome : serverBiomes) {
                registerBiome(builder, biome);
            }
        }

        Registries.BIOMES_NBT = builder.build();
    }

    /**
     * Register this Bedrock biome in the order that Bedrock expects it (starting at a certain value and in alphabetical order)
     */
    private static void registerBiome(NbtMapBuilder root, BedrockServerBiome bedrockBiome) {
        int bedrockId = -1;
        IntCollection takenValues = Registries.BIOME_IDENTIFIERS.get().values();

        for (int i = 189; i < Integer.MAX_VALUE; i++) {
            if (takenValues.contains(i)) {
                continue;
            }

            bedrockId = i;
            break;
        }

        if (bedrockId == -1) {
            throw new RuntimeException();
        }

        NbtMap nbt = bedrockBiome.toNbt();

        root.putCompound(bedrockBiome.getName(), nbt);

        Registries.BIOME_IDENTIFIERS.get().put(bedrockBiome.getName(), bedrockId);

        GeyserConnector.getInstance().getLogger().debug("Registered custom biome: " + bedrockBiome.getName());
    }
}
