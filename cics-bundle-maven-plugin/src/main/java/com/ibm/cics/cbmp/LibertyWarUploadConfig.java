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
     * Application ID for the WAR deployment
     */
    private String appId = "";
    
    /**
     * Context root for the deployed application
     */
    private String contextRoot = "";
    
    /**
     * Role name for the deployment (default: "User")
     */
    private String roleName = "User";
    
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

    // Getters and Setters
    
    public String getServerUrl() {
        return serverUrl;
    }

    public void setServerUrl(String serverUrl) {
        this.serverUrl = serverUrl;
    }

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getContextRoot() {
        return contextRoot;
    }

    public void setContextRoot(String contextRoot) {
        this.contextRoot = contextRoot;
    }

    public String getRoleName() {
        return roleName;
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getBearerToken() {
        return bearerToken;
    }

    public void setBearerToken(String bearerToken) {
        this.bearerToken = bearerToken;
    }
}

// Made with Bob
