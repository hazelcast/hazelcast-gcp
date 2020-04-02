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

    @Test
    public void testSetFromJson() throws Exception {
        GcpConnectionException gcpException = new GcpConnectionException(gcpConnectionExceptionJson);
        assertEquals(gcpException.getDomain(), gcpDomain);
        assertEquals(gcpException.getReason(), gcpReason);
        assertEquals(gcpException.getGcpMessage(), gcpMessage);
    }

    @Test
    public void testIsGcpConnectionException() throws Exception {
        assertTrue(GcpConnectionException.getIsGcpConnectionException(gcpConnectionExceptionJson));
        assertFalse(GcpConnectionException.getIsGcpConnectionException(restClientExceptionJson));
    }
}
