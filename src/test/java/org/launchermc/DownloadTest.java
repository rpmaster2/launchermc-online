package org.launchermc;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DownloadTest {

    @Test
    void logConfigPathFromVersionJson() {
        JsonObject details = JsonParser.parseString("""
                {"logging":{"client":{"argument":"-Dlog4j.configurationFile=${path}",
                 "file":{"id":"client-1.12.xml","sha1":"abc","size":100,
                         "url":"https://example.invalid/client-1.12.xml"}}}}
                """).getAsJsonObject();
        Path path = Download.logConfigPath(details);
        assertNotNull(path);
        assertEquals("client-1.12.xml", path.getFileName().toString());
        assertTrue(path.toString().replace('\\', '/').contains("log_configs/"));
    }

    @Test
    void noLoggingBlockMeansNoConfig() {
        assertNull(Download.logConfigPath(JsonParser.parseString("{}").getAsJsonObject()));
    }

    @Test
    void notInstalledVersionIsNotComplete() {
        assertFalse(Download.isComplete("definitely-not-installed-version"));
        assertFalse(Download.isInstalled("definitely-not-installed-version"));
    }

    @Test
    void mavenArtifactBuildsPathAndUrl() {
        JsonObject library = JsonParser.parseString("""
                {"name":"org.ow2.asm:asm:9.10.1","url":"https://maven.fabricmc.net/",
                 "sha1":"abc","size":123}
                """).getAsJsonObject();
        JsonObject artifact = Download.mavenArtifact(library);
        assertEquals("org/ow2/asm/asm/9.10.1/asm-9.10.1.jar",
                artifact.get("path").getAsString());
        assertEquals("https://maven.fabricmc.net/org/ow2/asm/asm/9.10.1/asm-9.10.1.jar",
                artifact.get("url").getAsString());
        assertEquals("abc", artifact.get("sha1").getAsString());
    }

    @Test
    void inheritedVersionUsesParentClientJar() throws Exception {
        Path parentDir = Download.versionDir("test-parent");
        Path childDir = Download.versionDir("test-child");
        Files.createDirectories(parentDir);
        Files.createDirectories(childDir);
        Files.writeString(parentDir.resolve("test-parent.json"), "{\"id\":\"test-parent\"}");
        Files.writeString(parentDir.resolve("test-parent-client.jar"), "client");
        Files.writeString(childDir.resolve("test-child.json"),
                "{\"id\":\"test-child\",\"inheritsFrom\":\"test-parent\"}");
        try {
            assertEquals("test-parent", Download.parentVersion("test-child"));
            assertEquals(parentDir.resolve("test-parent-client.jar"), Download.clientJar("test-child"));
            assertTrue(Download.isInstalled("test-child"));
        } finally {
            Files.deleteIfExists(childDir.resolve("test-child.json"));
            Files.deleteIfExists(parentDir.resolve("test-parent-client.jar"));
            Files.deleteIfExists(parentDir.resolve("test-parent.json"));
            Files.deleteIfExists(childDir);
            Files.deleteIfExists(parentDir);
        }
    }
    @Test
    void librariesAreSharedAcrossVersions() {
        assertEquals(Path.of("versions", "libraries", "com/example/lib.jar"),
                Download.libraryPath("com/example/lib.jar"));
        assertEquals(Path.of("versions", "1.21.8", "libraries", "com/example/lib.jar"),
                Download.legacyLibraryPath("1.21.8", "com/example/lib.jar"));
        assertFalse(Download.libraryExists("definitely-not-installed-version",
                "com/example/missing.jar"));
    }
}
