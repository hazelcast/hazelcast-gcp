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

import com.hazelcast.core.HazelcastException;
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
    private static final List<String> NON_RETRYABLE_KEYWORDS = asList("Private key json file not found",
            "Your service account does not have permissions", "Project could not be retrieved",
            "Zone could not be retrieved");

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

        return singletonList(RetryUtils.retry(new Callable<String>() {
            @Override
            public String call() {
                return validateRetrievedProject(gcpMetadataApi.currentProject());
            }
        }, RETRIES, NON_RETRYABLE_KEYWORDS));
    }

    private List<String> zonesFromConfigOrMetadataApi(final GcpConfig gcpConfig) {
        if (!gcpConfig.getZones().isEmpty()) {
            return gcpConfig.getZones();
        }
        LOGGER.finest("Property 'zones' not configured, fetching the current GCP zone");

        return singletonList(RetryUtils.retry(new Callable<String>() {
            @Override
            public String call() {
                return validateRetrievedZone(gcpMetadataApi.currentZone());
            }
        }, RETRIES, NON_RETRYABLE_KEYWORDS));
    }

    List<GcpAddress> getAddresses() {
        try {
            return RetryUtils.retry(new Callable<List<GcpAddress>>() {
                @Override
                public List<GcpAddress> call() {
                    return fetchGcpAddresses();
                }
            }, RETRIES, NON_RETRYABLE_KEYWORDS);
        } catch (GcpConnectionException e) {
            if (e.getMessage() != null) {
                LOGGER.severe(e.getMessage());
            }
            throw e;
        }
    }

    private List<GcpAddress> fetchGcpAddresses() {
        LOGGER.finest("Fetching OAuth Access Token");
        final String accessToken = fetchAccessToken();

        List<GcpAddress> result = new ArrayList<GcpAddress>();
        for (final String project : projects) {
            for (final String zone : zones) {
                try {
                    LOGGER.finest(String.format("Fetching instances for project '%s' and zone '%s'", project, zone));
                    List<GcpAddress> addresses = gcpComputeApi.instances(project, zone, label, accessToken);
                    LOGGER.finest(String.format("Found the following instances for project '%s' and zone '%s': %s", project, zone,
                            addresses));
                    result.addAll(addresses);
                } catch (RestClientException e) {
                    // Parse exception message and convert to GcpConnectionException if it should be of this type
                    if (GcpConnectionException.getIsGcpConnectionException(e.getMessage())) {
                        throw new GcpConnectionException(e.getMessage(), e.getUrl());
                    }
                    throw e;
                }
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

    String validateRetrievedProject(String projectFromMetadataApi) {
        if (projectFromMetadataApi.startsWith("<!DOCTYPE html")) {
            LOGGER.severe("Project name could not be retrieved. Please specify the 'projects' property if running "
                    + "from outside GCP network");
            throw new HazelcastException("Project could not be retrieved from GCP config");
        }
        return projectFromMetadataApi;
    }

    String validateRetrievedZone(String zoneFromMetadataApi) {
        if ("html>".equals(zoneFromMetadataApi)) {
            LOGGER.severe("Zone could not be retrieved. Please specify the 'zones' property if running from outside "
                    + "GCP network");
            throw new HazelcastException("Zone could not be retrieved from GCP config");
        }
        return zoneFromMetadataApi;
    }
}
