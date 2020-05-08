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

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

public class GcpConnectionExceptionTest {
    private static final String gcpDomain = "global";
    private static final String gcpReason = "insufficientPermissions";
    private static final String gcpMessage = "Insufficient Permission: Request had insufficient authentication scopes.";
    private static final String gcpConnectionExceptionJson = "{\n" +
            " \"error\": {\n" +
            "  \"errors\": [\n" +
            "   {\n" +
            "    \"domain\": \"global\",\n" +
            "    \"reason\": \"insufficientPermissions\",\n" +
            "    \"message\": \"Insufficient Permission: Request had insufficient authentication scopes.\"\n" +
            "   }\n" +
            "  ],\n" +
            "  \"code\": 403,\n" +
            "  \"message\": \"Insufficient Permission: Request had insufficient authentication scopes.\"\n" +
            " }\n" +
            "}";
    private static final String restClientExceptionJson = "{\n" +
            " \"error\": {\n" +
            "  \"restError\": " +
            "   {\n" +
            "    \"message\": \"Insufficient Permission: Request had insufficient authentication scopes.\"\n" +
            "   }\n" +
            " }\n" +
            "}";
    public static final String S_GCP_ERROR_INSUFFICIENT_PERMISSION_SCOPE = "Insufficient Permission: "
            + "Request had insufficient authentication scopes.";
    public static final String S_GCP_URL = "https://www.googleapis.com/compute/v1/prjoects/hazelcast-270703/zones/"
            + "us-east1-b/instances";
    public static final String S_GCP_FORMATTED_EXCEPTION_MESSAGE = "Your service account does not have permissions to "
            + "access " + S_GCP_URL + ". Please ensure the API access scope for Compute Engine is at least read-only.";

    @Test
    public void testSetFromJson() {
        GcpConnectionException gcpException = new GcpConnectionException(gcpConnectionExceptionJson);
        assertEquals(gcpDomain, gcpException.getDomain());
        assertEquals(gcpReason, gcpException.getReason());
        assertEquals(gcpMessage, gcpException.getGcpMessage());
    }

    @Test
    public void testIsGcpConnectionException() {
        assertTrue(GcpConnectionException.getIsGcpConnectionException(gcpConnectionExceptionJson));
        assertFalse(GcpConnectionException.getIsGcpConnectionException(restClientExceptionJson));
    }

    @Test
    public void testGetMessage() {
        GcpConnectionException gcpException = new GcpConnectionException();
        gcpException.setGcpMessage(S_GCP_ERROR_INSUFFICIENT_PERMISSION_SCOPE);
        gcpException.setUrl(S_GCP_URL);
        assertEquals(S_GCP_FORMATTED_EXCEPTION_MESSAGE, gcpException.getMessage());
    }
}
