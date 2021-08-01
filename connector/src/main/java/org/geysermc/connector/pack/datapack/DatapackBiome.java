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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.ToString;

@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
@ToString
public class DatapackBiome {
    private Effects effects;
    private String precipitation;
    private float temperature;
    private float downfall;
    private String category;
    private int depth;
    private float scale;

    @Getter
    @JsonIgnoreProperties(ignoreUnknown = true)
    @ToString
    public static class Effects {
        @JsonProperty("sky_color")
        private Integer skyColor;
        @JsonProperty("fog_color")
        private Integer fogColor;
        @JsonProperty("foliage_color")
        private Integer foliageColor;
        @JsonProperty("grass_color") // Can't be used at the moment - no property to match it to
        private Integer grassColor;
        @JsonProperty("water_color")
        private Integer waterColor;
        @JsonProperty("water_fog_color")
        private Integer waterFogColor;
        private Particle particle;

        @Getter
        @JsonIgnoreProperties(ignoreUnknown = true)
        @ToString
        public static class Particle {
            private Options options;
            private float probability;

            @Getter
            @JsonIgnoreProperties(ignoreUnknown = true)
            @ToString
            public static class Options {
                private String type;
                // The following four are present in dust particles
                private Float r;
                private Float g;
                private Float b;
                private Integer scale;
            }
        }
    }
}
