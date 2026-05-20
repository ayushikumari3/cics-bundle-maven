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

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Maven Mojo that uploads a WAR file to Liberty server using HTTP chunked transfer encoding.
 * Uses streaming to handle large files without loading entire file into memory.
 */
@Mojo(name = "upload-war", defaultPhase = LifecyclePhase.DEPLOY)
public class UploadWarToLibertyMojo extends AbstractMojo {

    // Retry configuration
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 1000L;
    
    // Buffer size for streaming file upload (8KB chunks)
    private static final int BUFFER_SIZE = 8192;
    
    // HTTP redirect status codes
    private static final int[] REDIRECT_STATUS_CODES = {301, 302, 303, 307, 308};
    
    // HTTP success status codes
    private static final int[] SUCCESS_STATUS_CODES = {200, 201};
    
    // Validation error messages
    private static final String MISSING_SERVER_URL = "Specify serverUrl for Liberty WAR upload";
    private static final String MISSING_APPLICATION_XML = "Specify either applicationXml or applicationXmlLocation for Liberty WAR upload";

    private static final String UPLOAD_CONFIG_EXCEPTION =
        "Please specify Liberty WAR upload configuration in pom.xml.\n\n" +
        "Example with inline applicationXml and Basic Authentication:\n" +
        "<configuration>\n" +
        "  <libertyWarUpload>\n" +
        "    <serverUrl>https://localhost:9080/com.ibm.cics.wlp.appdeploy/uploadApp</serverUrl>\n" +
        "    <applicationXml><![CDATA[<application id=\"myapp\" location=\"myapp.war\" type=\"war\">...</application>]]></applicationXml>\n" +
        "    <userName>username</userName>\n" +
        "    <password>password</password>\n" +
        "  </libertyWarUpload>\n" +
        "</configuration>\n\n" +
        "Example with applicationXmlLocation and JWT Token:\n" +
        "<configuration>\n" +
        "  <libertyWarUpload>\n" +
        "    <serverUrl>https://localhost:9080/com.ibm.cics.wlp.appdeploy/uploadApp</serverUrl>\n" +
        "    <applicationXmlLocation>${project.basedir}/src/main/liberty/application.xml</applicationXmlLocation>\n" +
        "    <bearerToken>your-jwt-token</bearerToken>\n" +
        "  </libertyWarUpload>\n" +
        "</configuration>\n\n" +
        "Example with No Security (requires SEC=NO on server):\n" +
        "<configuration>\n" +
        "  <libertyWarUpload>\n" +
        "    <serverUrl>http://localhost:9080/com.ibm.cics.wlp.appdeploy/uploadApp</serverUrl>\n" +
        "    <applicationXmlLocation>${project.basedir}/src/main/liberty/application.xml</applicationXmlLocation>\n" +
        "    <!-- No userName, password, or bearerToken - sends request without authentication -->\n" +
        "  </libertyWarUpload>\n" +
        "</configuration>";

    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    private MavenProject project;

    @Parameter(defaultValue = "${project.build.directory}", required = true, readonly = true)
    private File buildDir;

    /**
     * Configuration for Liberty WAR upload
     */
    @Parameter
    private LibertyWarUploadConfig libertyWarUpload;

    /**
     * The WAR file to upload. Defaults to the project's artifact file.
     */
    @Parameter(defaultValue = "${project.build.directory}/${project.build.finalName}.war")
    private File warFile;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        getLog().info("=== Upload WAR to Liberty ===");

        validateConfiguration();

        File war = validateWarFile();
        logUploadInfo(war);

        try {
            uploadWarWithRetry(war);
            getLog().info("✓ WAR file uploaded successfully!");
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to upload WAR file: " + e.getMessage(), e);
        }
    }

    /**
     * Validates that the WAR file exists and is readable.
     */
    private File validateWarFile() throws MojoExecutionException {
        if (!warFile.exists()) {
            throw new MojoExecutionException("WAR file does not exist: '" + warFile.getAbsolutePath() + "'");
        }
        return warFile;
    }

    /**
     * Logs upload information including file name, size, and target server.
     */
    private void logUploadInfo(File war) {
        double fileSizeMB = war.length() / (1024.0 * 1024.0);
        getLog().info("Uploading WAR file: " + war.getName());
        getLog().info(String.format("File size: %.2f MB", fileSizeMB));
        getLog().info("Target server: " + libertyWarUpload.getServerUrl());
    }

    /**
     * Uploads WAR file with exponential backoff retry logic.
     */
    private void uploadWarWithRetry(File war) throws Exception {
        Exception lastException = null;
        
        for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                uploadWarFile(war);
                return; // Success
            } catch (Exception e) {
                lastException = e;
                if (attempt < MAX_RETRY_ATTEMPTS) {
                    long delayMs = RETRY_DELAY_MS * attempt;
                    getLog().warn("Upload attempt " + attempt + " failed: " + e.getMessage() + 
                                  ". Retrying in " + delayMs + "ms...");
                    Thread.sleep(delayMs);
                }
            }
        }
        
        throw new MojoExecutionException("Upload failed after " + MAX_RETRY_ATTEMPTS + " attempts", lastException);
    }

    /**
     * Uploads WAR file using HTTP chunked transfer encoding with raw binary stream.
     * Handles HTTP redirects manually for POST requests.
     */
    private void uploadWarFile(File war) throws Exception {
        String urlWithParams = buildUrlWithParams(libertyWarUpload.getServerUrl(), resolveApplicationXml());
        
        HttpURLConnection connection = createConnection(urlWithParams);
        streamWarFile(connection, war);
        
        int responseCode = connection.getResponseCode();
        getLog().info("Response: " + responseCode + " - " + connection.getResponseMessage());
        
        // Handle HTTP redirects manually for POST with body
        if (isRedirect(responseCode)) {
            connection = handleRedirect(connection, war);
            responseCode = connection.getResponseCode();
        }
        
        validateResponse(connection, responseCode);
        logResponseBody(connection);
    }

    /**
     * Streams WAR file content directly using chunked transfer encoding.
     * Uses 8KB buffer to read and write file data efficiently without loading into memory.
     */
    private void streamWarFile(HttpURLConnection connection, File war) throws IOException {
        try (OutputStream outputStream = connection.getOutputStream();
             FileInputStream fileInput = new FileInputStream(war)) {
            
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            long totalBytes = 0;
            long lastLoggedMB = 0;
            
            while ((bytesRead = fileInput.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
                totalBytes += bytesRead;
                
                // Log progress every 100MB for large files
                long currentMB = totalBytes / (1024 * 1024);
                if (currentMB - lastLoggedMB >= 100) {
                    getLog().info("Uploaded: " + currentMB + "MB");
                    lastLoggedMB = currentMB;
                }
            }
            
            getLog().info(String.format("Total uploaded: %.2f MB", totalBytes / (1024.0 * 1024.0)));
        }
    }

    /**
     * Handles HTTP redirect by creating new connection and re-uploading.
     */
    private HttpURLConnection handleRedirect(HttpURLConnection originalConnection, File war) throws Exception {
        String redirectUrl = originalConnection.getHeaderField("Location");
        if (redirectUrl == null) {
            throw new MojoExecutionException("Redirect response missing Location header");
        }
        
        getLog().info("Following redirect to: " + redirectUrl);
        originalConnection.disconnect();
        
        String redirectUrlWithParams = buildUrlWithParams(redirectUrl, resolveApplicationXml());
        HttpURLConnection newConnection = createConnection(redirectUrlWithParams);
        streamWarFile(newConnection, war);
        
        getLog().info("Redirect response: " + newConnection.getResponseCode() + " - " + newConnection.getResponseMessage());
        return newConnection;
    }

    /**
     * Validates the HTTP response code.
     */
    private void validateResponse(HttpURLConnection connection, int responseCode) throws Exception {
        if (!isSuccess(responseCode)) {
            throw new MojoExecutionException("Upload failed with code " + responseCode + ": " + getErrorMessage(connection));
        }
    }

    /**
     * Builds URL with query parameters for Liberty upload.
     * If URL already contains applicationXml (from redirect), returns as-is.
     */
    private String buildUrlWithParams(String baseUrl, String applicationXml) throws Exception {
        if (baseUrl.contains("applicationXml=")) {
            return baseUrl;
        }
        
        String separator = baseUrl.contains("?") ? "&" : "?";
        String params = "applicationXml=" + urlEncode(applicationXml);
        
        return baseUrl + separator + params;
    }

    /**
     * Creates HTTP connection with chunked transfer encoding and authentication.
     */
    private HttpURLConnection createConnection(String url) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setInstanceFollowRedirects(false);  // Handle redirects manually for POST
        
        // Set timeout configurations
        int connectTimeout = libertyWarUpload.getConnectTimeout();
        int readTimeout = libertyWarUpload.getReadTimeout();
        
        connection.setConnectTimeout(connectTimeout);  // Time to establish connection
        connection.setReadTimeout(readTimeout);        // Time to wait for response
        
        getLog().info("Timeout configuration - Connect: " + connectTimeout + "ms, Read: " + readTimeout + "ms");
        
        connection.setRequestProperty("Content-Type", "application/octet-stream");
        connection.setRequestProperty("Transfer-Encoding", "chunked");
        connection.setChunkedStreamingMode(BUFFER_SIZE);  // Enable chunked streaming
        
        addAuthentication(connection);
        
        return connection;
    }

    /**
     * Adds authentication header to connection (Basic Auth or Bearer Token).
     * If no credentials are provided, no Authorization header is added.
     */
    private void addAuthentication(HttpURLConnection connection) {
        String bearerToken = libertyWarUpload.getBearerToken();
        String userName = libertyWarUpload.getUserName();
        String password = libertyWarUpload.getPassword();
        
        if (bearerToken != null && !bearerToken.isEmpty()) {
            // Use JWT Bearer token
            connection.setRequestProperty("Authorization", "Bearer " + bearerToken);
            getLog().info("Using JWT Bearer token authentication");
        } else if (userName != null && !userName.isEmpty() && password != null && !password.isEmpty()) {
            // Use Basic Authentication
            String credentials = userName + ":" + password;
            String encodedCredentials = Base64.getEncoder().encodeToString(credentials.getBytes());
            connection.setRequestProperty("Authorization", "Basic " + encodedCredentials);
            getLog().info("Using Basic Authentication");
        } else {
            // No credentials provided - send request without authentication
            getLog().info("No authentication - sending request without Authorization header");
        }
    }

    /**
     * Logs the HTTP response body if available.
     */
    private void logResponseBody(HttpURLConnection connection) {
        try {
            if (connection.getInputStream() != null) {
                java.util.Scanner scanner = new java.util.Scanner(connection.getInputStream()).useDelimiter("\\A");
                String responseBody = scanner.hasNext() ? scanner.next() : "";
                if (!responseBody.isEmpty()) {
                    getLog().info("Server response: " + responseBody);
                }
            }
        } catch (Exception e) {
            // Ignore
        }
    }

    /**
     * Extracts error message from HTTP connection.
     */
    private String getErrorMessage(HttpURLConnection connection) {
        try {
            if (connection.getErrorStream() != null) {
                java.util.Scanner scanner = new java.util.Scanner(connection.getErrorStream()).useDelimiter("\\A");
                return scanner.hasNext() ? scanner.next() : connection.getResponseMessage();
            }
        } catch (Exception e) {
            // Ignore
        }
        try {
            return connection.getResponseMessage();
        } catch (Exception e) {
            return "Unknown error";
        }
    }

    /**
     * URL-encodes a string value.
     */
    private String urlEncode(String value) throws Exception {
        return URLEncoder.encode(value, "UTF-8");
    }

    /**
     * Checks if response code is a redirect.
     */
    private boolean isRedirect(int code) {
        for (int redirectCode : REDIRECT_STATUS_CODES) {
            if (code == redirectCode) return true;
        }
        return false;
    }

    /**
     * Checks if response code is a success.
     */
    private boolean isSuccess(int code) {
        for (int successCode : SUCCESS_STATUS_CODES) {
            if (code == successCode) return true;
        }
        return false;
    }

    /**
     * Validates that all required configuration properties are set.
     */
    private void validateConfiguration() throws MojoExecutionException {
        if (libertyWarUpload == null) {
            throw new MojoExecutionException(UPLOAD_CONFIG_EXCEPTION);
        }

        StringBuilder errors = new StringBuilder();

        if (libertyWarUpload.getServerUrl() == null || libertyWarUpload.getServerUrl().isEmpty()) {
            errors.append(MISSING_SERVER_URL).append("\n");
        }
        boolean hasInlineApplicationXml = libertyWarUpload.getApplicationXml() != null &&
                                          !libertyWarUpload.getApplicationXml().trim().isEmpty();
        boolean hasApplicationXmlLocation = libertyWarUpload.getApplicationXmlLocation() != null &&
                                            !libertyWarUpload.getApplicationXmlLocation().trim().isEmpty();

        if (!hasInlineApplicationXml && !hasApplicationXmlLocation) {
            errors.append(MISSING_APPLICATION_XML).append("\n");
        }

        if (hasInlineApplicationXml && hasApplicationXmlLocation) {
            getLog().warn("Both applicationXml and applicationXmlLocation provided. Inline applicationXml will be used.");
        }
        
        // Authentication is optional - validate only if credentials are provided
        boolean hasBasicAuth = libertyWarUpload.getUserName() != null && !libertyWarUpload.getUserName().isEmpty() &&
                               libertyWarUpload.getPassword() != null && !libertyWarUpload.getPassword().isEmpty();
        boolean hasBearerToken = libertyWarUpload.getBearerToken() != null && !libertyWarUpload.getBearerToken().isEmpty();
        boolean hasPartialBasicAuth = (libertyWarUpload.getUserName() != null && !libertyWarUpload.getUserName().isEmpty() &&
                                       (libertyWarUpload.getPassword() == null || libertyWarUpload.getPassword().isEmpty())) ||
                                      ((libertyWarUpload.getUserName() == null || libertyWarUpload.getUserName().isEmpty()) &&
                                       libertyWarUpload.getPassword() != null && !libertyWarUpload.getPassword().isEmpty());
        
        // Only error on partial Basic Auth if there's no bearerToken (since bearerToken takes precedence)
        if (hasPartialBasicAuth && !hasBearerToken) {
            errors.append("Incomplete Basic Authentication. Provide both userName and password, or use bearerToken, or omit all credentials for no-security mode (SEC=NO).").append("\n");
        }
        
        if (hasBasicAuth && hasBearerToken) {
            getLog().warn("Both Basic Auth and Bearer Token provided. Bearer Token will be used.");
        }
        
        if (hasPartialBasicAuth && hasBearerToken) {
            getLog().warn("Partial Basic Auth credentials provided but will be ignored. Bearer Token will be used.");
        }
        
        if (!hasBasicAuth && !hasBearerToken && !hasPartialBasicAuth) {
            getLog().warn("No authentication credentials provided. Request will be sent without authentication. Server must be configured with SEC=NO.");
        }

        if (errors.length() > 0) {
            throw new MojoExecutionException(errors.toString() + "\n" + UPLOAD_CONFIG_EXCEPTION);
        }
    }

    private String resolveApplicationXml() throws MojoExecutionException {
        String inlineApplicationXml = libertyWarUpload.getApplicationXml();
        if (inlineApplicationXml != null && !inlineApplicationXml.trim().isEmpty()) {
            return inlineApplicationXml.trim();
        }

        String applicationXmlLocation = libertyWarUpload.getApplicationXmlLocation();
        File applicationXmlFile = new File(applicationXmlLocation);
        if (!applicationXmlFile.isAbsolute()) {
            applicationXmlFile = new File(project.getBasedir(), applicationXmlLocation);
        }

        if (!applicationXmlFile.exists()) {
            throw new MojoExecutionException("applicationXmlLocation does not exist: " + applicationXmlFile.getAbsolutePath());
        }

        if (!applicationXmlFile.isFile()) {
            throw new MojoExecutionException("applicationXmlLocation is not a file: " + applicationXmlFile.getAbsolutePath());
        }

        StringBuilder xml = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(applicationXmlFile, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                xml.append(line).append('\n');
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to read applicationXmlLocation: " + applicationXmlFile.getAbsolutePath(), e);
        }

        return xml.toString().trim();
    }
}

// Made with Bob
