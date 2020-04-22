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

import com.hazelcast.logging.ILogger;
import com.hazelcast.logging.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;

/**
 * Responsible for fetching the discovery information from GCP APIs.
 */
class GcpClient {
    private static final ILogger LOGGER = Logger.getLogger(GcpDiscoveryStrategy.class);

    private static final int RETRIES = 10;
    private static final List<String> NON_RETRYABLE_KEYWORDS = asList("Private key json file not found");

    private final GcpMetadataApi gcpMetadataApi;
    private final GcpComputeApi gcpComputeApi;
    private final GcpAuthenticator gcpAuthenticator;

    private final String privateKeyPath;
    private final List<String> projects;
    private final List<String> zones;
    private final Label label;

    GcpClient(GcpMetadataApi gcpMetadataApi, GcpComputeApi gcpComputeApi, GcpAuthenticator gcpAuthenticator,
              GcpConfig gcpConfig) {
        this.gcpMetadataApi = gcpMetadataApi;
        this.gcpComputeApi = gcpComputeApi;
        this.gcpAuthenticator = gcpAuthenticator;

        this.privateKeyPath = gcpConfig.getPrivateKeyPath();
        this.projects = projectFromConfigOrMetadataApi(gcpConfig);
        this.zones = zonesFromConfigOrMetadataApi(gcpConfig);
        this.label = gcpConfig.getLabel();
    }

    private List<String> projectFromConfigOrMetadataApi(final GcpConfig gcpConfig) {
        if (!gcpConfig.getProjects().isEmpty()) {
            return gcpConfig.getProjects();
        }
        LOGGER.finest("Property 'projects' not configured, fetching the current GCP project");

        try {
            String projectFromMetadataApi = gcpMetadataApi.currentProject();
            // Check if valid project was retrieved
            if (projectFromMetadataApi.startsWith("<!DOCTYPE html")) {
                LOGGER.severe("Project name could not be retrieved. Please grant permissions "
                        + "to this service account if running from within the GCP network, "
                        + "or specify the project name in the configuration if running from outside the GCP network "
                        + "where your hazelcast cluster is.");
                throw new RuntimeException("Project could not be retrieved from GCP metadata API");
            }

            return singletonList(projectFromMetadataApi);
        } catch (Exception e) {
            LOGGER.finest("Exception is not a known error - proceeding to retry API call");
        }

        return singletonList(RetryUtils.retry(new Callable<String>() {
            @Override
            public String call() {
                return gcpMetadataApi.currentProject();
            }
        }, RETRIES));
    }

    private List<String> zonesFromConfigOrMetadataApi(final GcpConfig gcpConfig) {
        if (!gcpConfig.getZones().isEmpty()) {
            return gcpConfig.getZones();
        }
        LOGGER.finest("Property 'zones' not configured, fetching the current GCP zone");

        try {
            String currentZone = gcpMetadataApi.currentZone();

            if ("html>".equals(currentZone)) {
                LOGGER.severe("Zone could not be retrieved. Please grant permissions "
                        + "to this service account if running from within the GCP network, "
                        + "or specify the zone in the configuration if running from outside the GCP network "
                        + "where your hazelcast cluster is.");
                throw new RuntimeException("Zone could not be retrieved from GCP metadata API");
            }

            return singletonList(currentZone);
        } catch (Exception e) {
            LOGGER.finest("Exception is not a known error - proceeding to retry API call");
        }

        return singletonList(RetryUtils.retry(new Callable<String>() {
            @Override
            public String call() {
                return gcpMetadataApi.currentZone();
            }
        }, RETRIES));
    }

    List<GcpAddress> getAddresses() throws Exception {
        try {
            return RetryUtils.retry(new Callable<List<GcpAddress>>() {
                @Override
                public List<GcpAddress> call() throws Exception {
                    return fetchGcpAddresses();
                }
            }, RETRIES, NON_RETRYABLE_KEYWORDS);
        } catch (GcpConnectionException e) {
            if (e.getMessage() != null) {
                LOGGER.severe(e.getMessage(), e);
            }
        }
        return null;
    }

    private List<GcpAddress> fetchGcpAddresses() throws Exception {
        LOGGER.finest("Fetching OAuth Access Token");
        final String accessToken = fetchAccessToken();

        List<GcpAddress> result = new ArrayList<GcpAddress>();
        for (final String project : projects) {
            for (final String zone : zones) {
                LOGGER.finest(String.format("Fetching instances for project '%s' and zone '%s'", project, zone));
                List<GcpAddress> addresses = gcpComputeApi.instances(project, zone, label, accessToken);
                LOGGER.finest(String.format("Found the following instances for project '%s' and zone '%s': %s", project, zone,
                        addresses));
                result.addAll(addresses);
            }
        }
        return result;
    }

    private String fetchAccessToken() {
        if (privateKeyPath != null) {
            return gcpAuthenticator.refreshAccessToken(privateKeyPath);
        }
        return gcpMetadataApi.accessToken();
    }

    String getAvailabilityZone() {
        return gcpMetadataApi.currentZone();
    }
}
