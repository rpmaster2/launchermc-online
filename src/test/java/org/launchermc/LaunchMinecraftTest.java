package org.launchermc;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LaunchMinecraftTest {

    @Test
    void launchwrapperRequiresExactlyJava8() throws Exception {
        withVersion("test-launchwrapper", """
                {"mainClass":"net.minecraft.launchwrapper.Launch"}
                """, () -> {
            LaunchMinecraft.JavaRequirement requirement =
                    LaunchMinecraft.requirement("test-launchwrapper");
            assertEquals(8, requirement.major());
            assertTrue(requirement.exact());
        });
    }

    @Test
    void modernVersionDeclaresJavaMajor() throws Exception {
        withVersion("test-modern-java", """
                {"mainClass":"net.minecraft.client.main.Main","javaVersion":{"majorVersion":25}}
                """, () -> {
            LaunchMinecraft.JavaRequirement requirement =
                    LaunchMinecraft.requirement("test-modern-java");
            assertEquals(25, requirement.major());
            assertFalse(requirement.exact());
        });
    }

    @Test
    void oldVersionWithoutJavaVersionFallsBackToJava8() throws Exception {
        withVersion("test-old-java", """
                {"mainClass":"net.minecraft.client.main.Main",
                 "releaseTime":"2013-09-19T15:00:00+00:00"}
                """, () -> {
            LaunchMinecraft.JavaRequirement requirement =
                    LaunchMinecraft.requirement("test-old-java");
            assertEquals(8, requirement.major());
            assertTrue(requirement.exact());
        });
    }

    @Test
    void missingVersionHasNoRequirement() {
        LaunchMinecraft.JavaRequirement requirement =
                LaunchMinecraft.requirement("definitely-not-installed");
        assertEquals(-1, requirement.major());
        assertFalse(requirement.exact());
    }

    private static void withVersion(String id, String json, ThrowingRunnable body) throws Exception {
        Path dir = Download.versionDir(id);
        Files.createDirectories(dir);
        Path meta = dir.resolve(id + ".json");
        Files.writeString(meta, json, StandardCharsets.UTF_8);
        try {
            body.run();
        } finally {
            Files.deleteIfExists(meta);
            Files.deleteIfExists(dir);
        }
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
