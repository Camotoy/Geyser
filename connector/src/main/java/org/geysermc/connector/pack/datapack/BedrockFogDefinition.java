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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import net.kyori.adventure.key.Key;

import java.util.HashMap;
import java.util.Map;

@NoArgsConstructor // For deserialization
public class BedrockFogDefinition {
    @JsonProperty("format_version")
    public final String formatVersion = "1.16.100";
    @Getter
    @JsonProperty("minecraft:fog_settings")
    private FogSettings fogSettings;

    private BedrockFogDefinition(FogSettings fogSettings) {
        this.fogSettings = fogSettings;
    }

    public static BedrockFogDefinition from(Key key, Map<String, FogSettings.FogDistance> fogs) {
        FogSettings fogSettings = new FogSettings(Key.key(key.namespace(), "fog_" + key.value()));
        fogSettings.getDistance().putAll(fogs);

        return new BedrockFogDefinition(fogSettings);
    }

    @NoArgsConstructor // For deserialization
    public static class FogSettings {
        @JsonProperty("description")
        private final Map<String, String> description = new HashMap<>();
        @Getter
        private final Map<String, FogDistance> distance = new HashMap<>();
        @Getter
        @JsonIgnore
        private Key identifier;

        public FogSettings(Key identifier) {
            this.identifier = identifier;
            description.put("identifier", identifier.asString());
        }

        @AllArgsConstructor
        @NoArgsConstructor // For deserialization
        public static class FogDistance {
            @JsonProperty("fog_start")
            private float fogStart;
            @JsonProperty("fog_end")
            private float fogEnd;
            @JsonProperty("fog_color")
            private String fogColor;
            @JsonProperty("render_distance_type")
            private String renderDistanceType;
        }
    }
}
