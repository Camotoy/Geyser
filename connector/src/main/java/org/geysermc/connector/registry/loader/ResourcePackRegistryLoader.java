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

package org.geysermc.connector.registry.loader;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.geysermc.connector.GeyserConnector;
import org.geysermc.connector.pack.resourcepack.FileResourcePack;
import org.geysermc.connector.pack.resourcepack.BedrockResourcePack;
import org.geysermc.connector.pack.resourcepack.ResourcePackManifest;
import org.geysermc.connector.utils.FileUtils;
import org.geysermc.connector.utils.LanguageUtils;

import java.io.File;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ResourcePackRegistryLoader implements RegistryLoader<Object, Map<String, BedrockResourcePack>> {

    /**
     * Loop through the packs directory and locate valid resource pack files
     */
    @Override
    public Map<String, BedrockResourcePack> load(Object input) {
        File directory = GeyserConnector.getInstance().getBootstrap().getConfigFolder().resolve("packs").toFile();

        if (!directory.exists()) {
            directory.mkdir();

            // As we just created the directory it will be empty
            return new Object2ObjectOpenHashMap<>();
        }

        Map<String, BedrockResourcePack> packs = new Object2ObjectOpenHashMap<>();

        for (File file : directory.listFiles()) {
            if (file.getName().endsWith(".zip") || file.getName().endsWith(".mcpack")) {

                byte[] sha256 = FileUtils.calculateSHA256(file);

                Stream<? extends ZipEntry> stream = null;
                try {
                    ZipFile zip = new ZipFile(file);

                    stream = zip.stream();
                    stream.forEach((x) -> {
                        if (x.getName().contains("manifest.json")) {
                            try {
                                ResourcePackManifest manifest = FileUtils.loadJson(zip.getInputStream(x), ResourcePackManifest.class);
                                // Sometimes a pack_manifest file is present and not in a valid format,
                                // but a manifest file is, so we null check through that one
                                if (manifest.getHeader().getUuid() != null) {
                                    packs.put(manifest.getHeader().getUuid().toString(), new FileResourcePack(
                                            sha256, file, manifest,
                                            ResourcePackManifest.Version.fromArray(manifest.getHeader().getVersion())));
                                    GeyserConnector.getInstance().getLogger().debug("New resource pack registered: " + file.getName());
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    });
                } catch (Exception e) {
                    GeyserConnector.getInstance().getLogger().error(LanguageUtils.getLocaleStringLog("geyser.resource_pack.broken", file.getName()));
                    e.printStackTrace();
                } finally {
                    if (stream != null) {
                        stream.close();
                    }
                }
            }
        }

        return packs;
    }
}
