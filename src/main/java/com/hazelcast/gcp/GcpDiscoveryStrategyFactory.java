/*
 * Copyright 2020 Hazelcast Inc.
 *
 * Licensed under the Hazelcast Community License (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at
 *
 * http://hazelcast.com/hazelcast-community-license
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package com.hazelcast.gcp;

import com.hazelcast.config.properties.PropertyDefinition;
import com.hazelcast.logging.ILogger;
import com.hazelcast.spi.discovery.DiscoveryNode;
import com.hazelcast.spi.discovery.DiscoveryStrategy;
import com.hazelcast.spi.discovery.DiscoveryStrategyFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Factory class which returns {@link GcpDiscoveryStrategy} to Discovery SPI.
 */
public class GcpDiscoveryStrategyFactory
        implements DiscoveryStrategyFactory {
    @Override
    public Class<? extends DiscoveryStrategy> getDiscoveryStrategyType() {
        return GcpDiscoveryStrategy.class;
    }

    @Override
    public DiscoveryStrategy newDiscoveryStrategy(DiscoveryNode discoveryNode, ILogger logger,
                                                  Map<String, Comparable> properties) {
        return new GcpDiscoveryStrategy(properties);
    }

    @Override
    public Collection<PropertyDefinition> getConfigurationProperties() {
        List<PropertyDefinition> result = new ArrayList<PropertyDefinition>();
        for (GcpProperties property : GcpProperties.values()) {
            result.add(property.getDefinition());
        }
        return result;
    }

    @Override
    public boolean isAutoDetectionApplicable() {
        return googleInternalDnsConfigured() && metadataFlavorGoogle();
    }


    private String readFileContents(String fileName) {
        InputStream is = null;
        try {
            File file = new File(fileName);
            byte[] data = new byte[(int) file.length()];
            is = new FileInputStream(file);
            is.read(data);
            return new String(data, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Could not get " + fileName, e);
        } finally {
            IOUtil.closeResource(is);
        }
    }

    private boolean googleInternalDnsConfigured() {
        return readFileContents("/etc/resolv.conf").contains("google.internal") ||
                readFileContents("/etc/hosts").contains("google.internal");
    }

    private boolean metadataFlavorGoogle() {
        return isEndpointAvailable("metadata.google.internal");
    }

    private boolean isEndpointAvailable(String url) {
        return !RestClient.create(url)
                .get()
                .isEmpty();
    }

    @Override
    public DiscoveryStrategyLevel discoveryStrategyLevel() {
        return DiscoveryStrategyLevel.CLOUD_VM;
    }


}
