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

package org.geysermc.connector.pack.datapack;

import net.kyori.adventure.key.Key;
import org.geysermc.connector.GeyserConnector;
import org.geysermc.connector.pack.resourcepack.BedrockResourcePack;
import org.geysermc.connector.pack.resourcepack.ResourcePackManifest;
import org.geysermc.connector.pack.resourcepack.VirtualResourcePack;
import org.geysermc.connector.registry.Registries;
import org.geysermc.connector.utils.FileUtils;

import java.io.*;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class DatapackBiomeLoader {

    // short blurb: Bedrock needs biome information before they join the server
    public static GeyserDatapackCache.DatapackInfo loadDatapack(File file) throws Exception {
        if (!file.exists()) {
            throw new FileNotFoundException("Could not find the datapack file!");
        }

        boolean isDirectory = file.isDirectory();

        Map<String, BedrockFogDefinition> fogDefinitions = new HashMap<>();
        BedrockClientBiomes clientBiomes = new BedrockClientBiomes();
        List<BedrockServerBiome> serverBiomes = new ArrayList<>();

        // Not closeable if a directory
        FileSystem fileSystem = isDirectory ? file.toPath().getFileSystem() : FileSystems.newFileSystem(file.toPath(), DatapackBiomeLoader.class.getClassLoader());

        try {
            // We need to use things like Files.walk as zipped datapacks cannot use Path#toFile.
            Files.walk(isDirectory ? file.toPath() : fileSystem.getPath("data"), 1).forEach((dataPath -> {
                if ((isDirectory && dataPath.getParent().equals(file.toPath())) || (!isDirectory && dataPath.getParent() == null)) {
                    // The "data" path alone shows up here
                    return;
                }

                String namespace = dataPath.getFileName().toString();

                if ("minecraft".equals(namespace)) {
                    return; // For now...
                }

                try {
                    Path worldgenPath = dataPath.resolve("worldgen").resolve("biome");
                    if (!Files.exists(worldgenPath)) {
                        return;
                    }
                    Files.walk(worldgenPath).forEach(biomePath -> {
                        if (Files.isDirectory(biomePath) || !biomePath.toString().toLowerCase(Locale.ROOT).endsWith(".json")) {
                            return;
                        }

                        // This matches the Java identifier by removing the preceding worldgen/biome folders but still keeping a folder structure if present
                        String fileName = worldgenPath.relativize(biomePath).toString();
                        String biomeName = fileName.substring(0, fileName.length() - ".json".length());
                        Key key = Key.key(namespace, biomeName);

                        DatapackBiome datapackBiome;
                        try (InputStream stream = fileSystem.provider().newInputStream(biomePath)) {
                            datapackBiome = GeyserConnector.JSON_MAPPER.readValue(stream, DatapackBiome.class);
                        } catch (IOException e) {
                            e.printStackTrace();
                            return;
                        }

                        String fogColor = hexColorToString(datapackBiome.getEffects().getFogColor());
                        String waterColor = hexColorToString(datapackBiome.getEffects().getWaterColor());
                        String waterFogColor = hexColorToString(datapackBiome.getEffects().getWaterFogColor());

                        // Java supports any particle floating around, but Bedrock only supports a handful.
                        // If the particle in this biome happens to be one of the supported, let's enable it
                        float ash = 0f;
                        float blueSpores = 0f;
                        float redSpores = 0f;
                        float whiteAsh = 0f;

                        if (datapackBiome.getEffects().getParticle() != null && datapackBiome.getEffects().getParticle().getOptions() != null
                                && datapackBiome.getEffects().getParticle().getOptions().getType() != null) {
                            // Probability is based on Java's crimson spore count being 0.025 and Bedrock's being 0.25
                            switch (datapackBiome.getEffects().getParticle().getOptions().getType()) {
                                case "minecraft:ash":
                                    ash = datapackBiome.getEffects().getParticle().getProbability() * 10;
                                    break;
                                case "minecraft:crimson_spore":
                                    redSpores = datapackBiome.getEffects().getParticle().getProbability() * 10;
                                    break;
                                case "minecraft:warped_spore":
                                    blueSpores = datapackBiome.getEffects().getParticle().getProbability() * 10;
                                    break;
                                case "minecraft:white_ash":
                                    whiteAsh = datapackBiome.getEffects().getParticle().getProbability() * 10;
                                    break;
                            }
                        }

                        Map<String, BedrockFogDefinition.FogSettings.FogDistance> fogs = new HashMap<>();
                        if (fogColor != null) {
                            float fogStart;
                            float fogEnd;
                            String type;
                            if (datapackBiome.getCategory().equals("nether")) {
                                fogStart = 10.0f;
                                fogEnd = 96.0f;
                                type = "fixed";
                            } else {
                                fogStart = 1f;
                                fogEnd = 1f;
                                type = "render";
                            }

                            fogs.put("air", new BedrockFogDefinition.FogSettings.FogDistance(fogStart, fogEnd, fogColor, type));
                            fogs.put("weather", new BedrockFogDefinition.FogSettings.FogDistance(fogStart, fogEnd, fogColor, type));
                        }
                        if (waterFogColor != null) {
                            fogs.put("water", new BedrockFogDefinition.FogSettings.FogDistance(0.0f, 15.0f, waterFogColor, "fixed"));
                        }

                        BedrockFogDefinition fogDefinition = BedrockFogDefinition.from(key, fogs);
                        fogDefinitions.put(key.value() + "_fog_setting", fogDefinition);

                        BedrockClientBiome clientBiome = new BedrockClientBiome(fogDefinition.getFogSettings().getIdentifier().asString(), waterColor);
                        clientBiomes.getBiomes().put(key.asString(), clientBiome);

                        serverBiomes.add(new BedrockServerBiome(key.asString(), ash, blueSpores,
                                datapackBiome.getDownfall(), redSpores, datapackBiome.getTemperature(), whiteAsh));
                    });
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }));
        } finally {
            if (!isDirectory) {
                fileSystem.close();
            }
        }

        if (serverBiomes.isEmpty()) {
            // No need to worry about this datapack
            return null;
        }

        ResourcePackManifest manifest = ResourcePackManifest.createResource(file.getName(), "Resource pack generated by Geyser", new int[]{1, 0, 0});
        finalizeDatapackLoad(manifest, clientBiomes, fogDefinitions);

        return new GeyserDatapackCache.DatapackInfo(manifest, clientBiomes, serverBiomes, fogDefinitions);
    }

    public static void loadFromCache(GeyserDatapackCache.DatapackInfo datapackInfo) throws Exception {
        finalizeDatapackLoad(datapackInfo.getManifest(), datapackInfo.getClientBiomes(), datapackInfo.getFogDefinitions());
    }

    private static void finalizeDatapackLoad(ResourcePackManifest manifest, BedrockClientBiomes clientBiomes,
                                                                 Map<String, BedrockFogDefinition> fogDefinitions) throws Exception {
        ByteArrayOutputStream baos;
        try (ZipOutputStream outputStream = new ZipOutputStream(baos = new ByteArrayOutputStream())) {
            ZipEntry manifestEntry = new ZipEntry("manifest.json");
            outputStream.putNextEntry(manifestEntry);
            outputStream.write(GeyserConnector.JSON_MAPPER.writeValueAsBytes(manifest));
            outputStream.closeEntry();

            ZipEntry clientBiomeJson = new ZipEntry("biomes_client.json");
            outputStream.putNextEntry(clientBiomeJson);
            outputStream.write(GeyserConnector.JSON_MAPPER.writeValueAsBytes(clientBiomes));
            outputStream.closeEntry();

            for (Map.Entry<String, BedrockFogDefinition> fog : fogDefinitions.entrySet()) {
                ZipEntry fogEntry = new ZipEntry("fogs/" + fog.getKey() + ".json");
                outputStream.putNextEntry(fogEntry);
                outputStream.write(GeyserConnector.JSON_MAPPER.writeValueAsBytes(fog.getValue()));
                outputStream.closeEntry();
            }
        }

        byte[] data = baos.toByteArray();
        BedrockResourcePack resourcePack = new VirtualResourcePack(FileUtils.calculateSHA256(data), data, manifest,
                ResourcePackManifest.Version.fromArray(manifest.getHeader().getVersion()));

        Registries.RESOURCE_PACKS.get().put(manifest.getHeader().getUuid().toString(), resourcePack);

        GeyserConnector.getInstance().getLogger().debug("Registered virtual resource pack from datapack: " + manifest.getHeader().getName());
    }

    private static String hexColorToString(Integer color) {
        if (color == null) {
            return null;
        }

        int r = (color & (255 << 16)) >> 16;
        int g = (color & (255 << 8)) >> 8;
        int b = color & 255;

        return '#' + convertToHex(r) + convertToHex(g) + convertToHex(b);
    }

    private static String convertToHex(int color) {
        String hex = Integer.toHexString(color);
        if (hex.length() == 1) {
            return '0' + hex;
        }
        return hex;
    }
}
