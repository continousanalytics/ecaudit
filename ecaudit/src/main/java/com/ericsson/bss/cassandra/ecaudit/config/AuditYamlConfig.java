/*
 * Copyright 2018 Telefonaktiebolaget LM Ericsson
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ericsson.bss.cassandra.ecaudit.config;

import java.util.Collections;
import java.util.List;

/**
 * Data class for configuration
 */
public final class AuditYamlConfig
{
    private static final List<String> DEFAULT_WHITELIST = Collections.emptyList();

    private boolean fromFile = true;

    // Configuration parameters
    public List<String> whitelist;
    public AuditYamlSlf4jConfig slf4j;

    static AuditYamlConfig createWithoutFile()
    {
        AuditYamlConfig auditYamlConfig = new AuditYamlConfig();
        auditYamlConfig.fromFile = false;
        return auditYamlConfig;
    }

    boolean isFromFile()
    {
        return fromFile;
    }

    /**
     * Get the user whitelist in this configuration
     *
     * @return the list of whitelisted users
     */
    public List<String> getWhitelist()
    {
        return whitelist != null ? Collections.unmodifiableList(whitelist) : DEFAULT_WHITELIST;
    }

    /**
     * @return the SLF4J configuration or {@code null} if not configured.
     */
    public AuditYamlSlf4jConfig getSlf4j()
    {
        return slf4j;
    }
}
