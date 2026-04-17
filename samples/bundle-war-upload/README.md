# Maven WAR Upload Sample (bundle-war-upload)

This sample demonstrates how to upload a WAR file directly to a Liberty server endpoint using the CICS Bundle Maven Plugin's WAR upload feature. This approach deploys WARs directly to Liberty using the WAR upload REST API.

## Key Features
- Direct WAR upload to Liberty server (no CICS bundle required)
- HTTP multipart file upload with streaming (handles large files efficiently)
- Automatic retry with exponential backoff
- HTTP redirect handling
- Basic authentication and JWT Bearer token support
- Progress logging

## Prerequisites
Ensure your Liberty server has the WAR upload feature enabled and configured. You'll need:
- The Liberty server URL endpoint (e.g., `http://server:port/com.ibm.cics.wlp.appdeploy/uploadApp`)
- Valid credentials (username and password) with appropriate permissions
- Application ID and context root for your application

## Configuration

### Basic Authentication
Edit the `pom.xml` file and configure the `libertyWarUpload` section:

```xml
<plugin>
    <groupId>com.ibm.cics</groupId>
    <artifactId>cics-bundle-maven-plugin</artifactId>
    <version>2.0.1-SNAPSHOT</version>
    <executions>
        <execution>
            <goals>
                <goal>upload-war</goal>
            </goals>
        </execution>
    </executions>
    <configuration>
        <libertyWarUpload>
            <serverUrl>http://your-server:port/com.ibm.cics.wlp.appdeploy/uploadApp</serverUrl>
            <appId>demo-war-upload</appId>
            <contextRoot>/demo-war-upload</contextRoot>
            <roleName>User</roleName>
            <userName>${cics.user}</userName>
            <password>${cics.password}</password>
        </libertyWarUpload>
    </configuration>
</plugin>
```

### JWT Bearer Token Authentication
Alternatively, use JWT Bearer token:

```xml
<libertyWarUpload>
    <serverUrl>http://your-server:port/com.ibm.cics.wlp.appdeploy/uploadApp</serverUrl>
    <appId>demo-war-upload</appId>
    <contextRoot>/demo-war-upload</contextRoot>
    <roleName>User</roleName>
    <bearerToken>${cics.token}</bearerToken>
</libertyWarUpload>
```

### Credentials Management
Store credentials in your Maven `settings.xml` or pass them as system properties:

**Using Maven settings.xml:**
```xml
<settings>
    <profiles>
        <profile>
            <id>cics</id>
            <properties>
                <cics.user>your-username</cics.user>
                <cics.password>your-password</cics.password>
            </properties>
        </profile>
    </profiles>
</settings>
```

**Using command line:**
```bash
mvn clean package cics-bundle:upload-war -Dcics.user=username -Dcics.password=password
```

## Building and Uploading

### Build the WAR file
```bash
mvn clean package
```

### Upload WAR to Liberty
```bash
mvn cics-bundle:upload-war
```

Or combine both steps:
```bash
mvn clean package cics-bundle:upload-war
```

The upload goal will:
1. Build the WAR file (if not already built)
2. Upload it to the configured Liberty server endpoint
3. Handle HTTP redirects automatically
4. Retry on failure (up to 3 attempts)
5. Display upload progress and server response

### Example Output
```
[INFO] === Upload WAR to Liberty ===
[INFO] Uploading WAR file: demo-war-upload-1.0.0.war
[INFO] File size: 0.04 MB
[INFO] Target server: http://server:12372/com.ibm.cics.wlp.appdeploy/uploadApp
[INFO] Using Basic Authentication
[INFO] Response: 302 - Found
[INFO] Following redirect to: https://server:12373/com.ibm.cics.wlp.appdeploy/uploadApp?appId=...
[INFO] Redirect response: 200 - OK
[INFO] Server response: Application uploaded and configured successfully.
[INFO] ✓ WAR file uploaded successfully!
```

## Configuration Options

| Property | Required | Description | Example |
|----------|----------|-------------|---------|
| `serverUrl` | Yes | Liberty server upload endpoint | `http://server:9080/uploadApp` |
| `appId` | Yes | Application identifier | `myapp` |
| `contextRoot` | Yes | Application context root | `/myapp` |
| `roleName` | No | Security role name (default: "User") | `User` |
| `userName` | Conditional* | Authentication username | `admin` |
| `password` | Conditional* | Authentication password | `password` |
| `bearerToken` | Conditional* | JWT Bearer token | `eyJhbGc...` |

*Either `userName`/`password` OR `bearerToken` must be provided.

## Troubleshooting

### SSL Certificate Errors
If you encounter SSL certificate errors, ensure your Java truststore includes the Liberty server's certificate. For development/testing only, you can configure Maven to skip SSL verification (not recommended for production).

### Authentication Failures
- Verify username and password are correct
- Ensure the user has appropriate permissions on the Liberty server
- Check that the `roleName` matches the configured security role

### Connection Timeouts
- Verify the server URL is correct and accessible
- Check network connectivity and firewall rules
- Ensure the Liberty server is running and the upload endpoint is enabled

## What's Next
After successful upload, visit your application at:
```
http://your-server:port/your-context-root
```

For this sample:
```
http://your-server:port/demo-war-upload