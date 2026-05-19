package com.ibm.cics.cbmp;

/*-
 * #%L
 * CICS Bundle Maven Plugin
 * %%
 * Copyright (C) 2026 IBM Corp.
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

/**
 * Configuration class for uploading WAR files directly to Liberty server endpoints.
 * This bypasses CICS bundle creation and deploys WARs directly to Liberty.
 */
public class LibertyWarUploadConfig {
    
    /**
     * Liberty server endpoint URL (e.g., "http://localhost:9080/uploadApp")
     */
    private String serverUrl = "";
    
    /**
     * Inline Liberty application XML.
     * If specified, this takes precedence over applicationXmlLocation.
     */
    private String applicationXml = "";
    
    /**
     * File location for Liberty application XML.
     * The plugin reads this file and sends its content to the server.
     */
    private String applicationXmlLocation = "";
    
    /**
     * Username for Basic Authentication (optional if using JWT token)
     */
    private String userName = "";
    
    /**
     * Password for Basic Authentication (optional if using JWT token)
     */
    private String password = "";
    
    /**
     * JWT Bearer token for authentication (alternative to userName/password)
     */
    private String bearerToken = "";
    
    /**
     * Connection timeout in milliseconds (default: 30000ms = 30 seconds)
     * Time to wait for establishing TCP connection to the server
     */
    private int connectTimeout = 30000;
    
    /**
     * Read timeout in milliseconds (default: 300000ms = 5 minutes)
     * Time to wait for server response after sending the request
     */
    private int readTimeout = 300000;
    

    // Getters
    public String getServerUrl() {
        return serverUrl;
    }

    
    public String getApplicationXml() {
        return applicationXml;
    }

    public String getApplicationXmlLocation() {
        return applicationXmlLocation;
    }

    public String getUserName() {
        return userName;
    }

    public String getPassword() {
        return password;
    }

    public String getBearerToken() {
        return bearerToken;
    }
    
    public int getConnectTimeout() {
        return connectTimeout;
    }
    
    public int getReadTimeout() {
        return readTimeout;
    }
    
}

// Made with Bob
