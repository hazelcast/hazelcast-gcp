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

import com.hazelcast.internal.json.Json;
import com.hazelcast.internal.json.JsonObject;
import com.hazelcast.internal.json.JsonArray;
import com.hazelcast.internal.json.JsonValue;

import com.hazelcast.logging.ILogger;
import com.hazelcast.logging.Logger;

/**
 * Exception to indicate an issue when pinging GCP API.
 *
 * A list of errors can be found at: {@see https://cloud.google.com/resource-manager/docs/core_errors}.
 */

public class GcpConnectionException extends RuntimeException {
    /**
     * Static error string returned when there are insufficient permissions to access the Compute Engine via the API
     */
    public static final String S_GCP_ERROR_INSUFFICIENT_PERMISSION_SCOPE = "Insufficient Permission: "
            + "Request had insufficient authentication scopes.";
    /**
     * Static error string returned when service account doesn't have IAM role to access 'compute.instances.list'
     */
    public static final String S_GCP_ERROR_COMPUTE_INSTANCES_LIST = "Required 'compute.instances.list' permission for";

    private static final ILogger LOGGER = Logger.getLogger(GcpConnectionException.class);

    private String domain;
    private String reason;
    private String gcpMessage;
    private int errorCode;
    private String url;

    public GcpConnectionException(String jsonResponse) {
        super("Failure in executing GCP API request");

        setFromJson(jsonResponse);
    }

    public GcpConnectionException(String jsonResponse, String url) {
        super("Failure in executing GCP API request at: " + url);
        this.url = url;

        setFromJson(jsonResponse);
    }

    private void setFromJson(String jsonResponse) {
        JsonObject gcpErrorJson = Json.parse(jsonResponse).asObject().get("error").asObject();
        JsonArray gcpErrorList = toJsonArray(gcpErrorJson.get("errors"));
        JsonObject gcpError = gcpErrorList.get(0).asObject();
        this.domain = gcpError.get("domain").asString();
        this.reason = gcpError.get("reason").asString();
        this.gcpMessage = gcpError.get("message").asString();
        this.errorCode = gcpErrorJson.get("code").asInt();
    }

    public String getDomain() {
        return this.domain;
    }
    public String getReason() {
        return this.reason;
    }
    public String getGcpMessage() {
        return this.gcpMessage;
    }
    public int getErrorCode() {
        return this.errorCode;
    }
    public String getUrl() {
        return this.url;
    }

    public static Boolean getIsGcpConnectionException(String responseString) {
        JsonValue gcpErrorValue = Json.parse(responseString).asObject().get("error");
        if (gcpErrorValue != null) {
            JsonArray gcpErrorList = toJsonArray(gcpErrorValue.asObject().get("errors"));
            if (!gcpErrorList.isEmpty()) {
                String gcpErrorMessage = gcpErrorList.get(0).asObject().get("message").asString();
                if (gcpErrorMessage != null) {
                    return Boolean.TRUE;
                }
            }
        }
        return Boolean.FALSE;
    }

    public void logErrorAndThrowException() {
        if (GcpConnectionException.S_GCP_ERROR_INSUFFICIENT_PERMISSION_SCOPE.equals(getGcpMessage())) {
            LOGGER.severe(String.format("Your service account does not have permissions to access %s. "
                    + "Please ensure the API access scope for Compute Engine is at least read-only.", getUrl()), this);
        } else if (getGcpMessage() != null
                && getGcpMessage().startsWith(GcpConnectionException.S_GCP_ERROR_COMPUTE_INSTANCES_LIST)) {
            LOGGER.severe("Your service account does not have permissions to access \"compute.instances.list\"."
                    + " Please grant the missing IAM roles.", this);
        }

        throw this;
    }

    private static JsonArray toJsonArray(JsonValue jsonValue) {
        if (jsonValue == null || jsonValue.isNull()) {
            return new JsonArray();
        } else {
            return jsonValue.asArray();
        }
    }

}
