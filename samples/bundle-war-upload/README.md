# Maven WAR Upload Sample (bundle-war-upload)

This sample demonstrates how to upload a WAR file directly to a Liberty server endpoint using the CICS Bundle Maven Plugin's WAR upload feature. This approach deploys WARs directly to Liberty using the WAR upload REST API.

The sample uses the current upload contract:
- the WAR archive is sent as the raw HTTP request body
- the plugin resolves the full Liberty `<application ...>` definition from either inline `applicationXml` or `applicationXmlLocation`

## Key Features
- Direct WAR upload to Liberty server (no CICS bundle required)
- HTTP chunked upload with streaming (handles large files efficiently)
- Full Liberty `<application>` definition supplied inline or from a file
- Automatic retry with exponential backoff
- HTTP redirect handling
- Basic authentication and JWT Bearer token support
- Progress logging

## Prerequisites
Ensure your Liberty server has the WAR upload feature enabled and configured. You'll need:
- The Liberty server URL endpoint (e.g., `https://cics-server:port/com.ibm.cics.wlp.appdeploy/uploadApp`)
- Valid credentials (username/password or JWT bearer token) with appropriate permissions
- A Liberty application definition supplied inline in the plugin configuration or from a local file, for example `src/main/resources/application.xml`

## Configuration

### Basic Authentication
Edit the `pom.xml` file and configure the `libertyWarUpload` section.

Using inline `applicationXml`:
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
            <serverUrl>https://cics-server:port/com.ibm.cics.wlp.appdeploy/uploadApp</serverUrl>
            <applicationXml><![CDATA[
                <application id="demo-war-upload" location="demo-war-upload.war" type="war">
                    <context-root>/demo-war-upload</context-root>
                </application>
            ]]></applicationXml>
            <userName>${cics.user}</userName>
            <password>${cics.password}</password>
        </libertyWarUpload>
    </configuration>
</plugin>
```

Using `applicationXmlLocation`:
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
            <serverUrl>https://cics-server:port/com.ibm.cics.wlp.appdeploy/uploadApp</serverUrl>
            <applicationXmlLocation>${project.basedir}/src/main/resources/application.xml</applicationXmlLocation>
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
    <serverUrl>https://cics-server:port/com.ibm.cics.wlp.appdeploy/uploadApp</serverUrl>
    <applicationXmlLocation>${project.basedir}/src/main/resources/application.xml</applicationXmlLocation>
    <bearerToken>${cics.token}</bearerToken>
</libertyWarUpload>
```

You can also use inline `applicationXml` with JWT authentication in the same way.

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
2. Resolve the Liberty `<application>` definition from inline `applicationXml` or `applicationXmlLocation`
3. Upload the WAR to the configured Liberty server endpoint using HTTP chunked transfer encoding
4. Stream the WAR without loading the full archive into memory
5. Send the application definition to the Liberty upload endpoint as `applicationXml`
6. Handle HTTP redirects automatically
7. Retry on failure (up to 3 attempts)
8. Display upload progress and server response

### Example Output
```
[INFO] === Upload WAR to Liberty ===
[INFO] Uploading WAR file: demo-war-upload-1.0.0.war
[INFO] File size: 0.04 MB
[INFO] Target server: http://server:12372/com.ibm.cics.wlp.appdeploy/uploadApp
[INFO] Using Basic Authentication
[INFO] Response: 302 - Found
[INFO] Following redirect to: https://server:12373/com.ibm.cics.wlp.appdeploy/uploadApp?applicationXml=...
[INFO] Redirect response: 200 - OK
[INFO] Server response: Application uploaded and configured successfully.
[INFO] ✓ WAR file uploaded successfully!
```

## Configuration Options

| Property | Required | Description | Example |
|----------|----------|-------------|---------|
| `serverUrl` | Yes | Liberty server upload endpoint | `https://cics-server:port/com.ibm.cics.wlp.appdeploy/uploadApp` |
| `applicationXml` | Yes* | Inline full Liberty `<application ...>` definition | `<![CDATA[<application ...>...</application>]]>` |
| `applicationXmlLocation` | Yes* | Path to the full Liberty `<application ...>` definition | `${project.basedir}/src/main/resources/application.xml` |
| `userName` | Conditional** | Authentication username | `admin` |
| `password` | Conditional** | Authentication password | `password` |
| `bearerToken` | Conditional** | JWT Bearer token | `eyJhbGc...` |

*Specify either `applicationXml` or `applicationXmlLocation`. If both are supplied, `applicationXml` takes precedence.
**Either `userName`/`password` OR `bearerToken` must be provided.

Example `application.xml` used by this sample:

```xml
<application id="demo-war-upload" location="demo-war-upload.war" type="war">
  <context-root>/demo-war-upload</context-root>
  <classloader delegation="parentLast"/>
  <application-bnd>
    <security-role name="User">
      <user name="authenticatedUser"/>
    </security-role>
  </application-bnd>
</application>
```

## Troubleshooting

### SSL Certificate Errors
If you encounter SSL certificate errors, ensure your Java truststore includes the Liberty server's certificate. For development/testing only, you can configure Maven to skip SSL verification (not recommended for production).

### Authentication Failures
- Verify username/password or JWT token are correct
- Ensure the user has appropriate permissions on the Liberty server
- Check the authenticated user is permitted by the application security mapping in `application.xml`

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