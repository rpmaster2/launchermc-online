package org.launchermc;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaRuntimeTest {

    @Test
    void parsesLegacyVersion() {
        assertEquals(8, JavaRuntime.parseMajor("1.8.0_491"));
    }

    @Test
    void parsesModernVersion() {
        assertEquals(21, JavaRuntime.parseMajor("21.0.13.1+2"));
        assertEquals(24, JavaRuntime.parseMajor("24.0.2"));
        assertEquals(25, JavaRuntime.parseMajor("25-ea"));
    }

    @Test
    void rejectsUnknownVersion() {
        assertEquals(-1, JavaRuntime.parseMajor(""));
        assertEquals(-1, JavaRuntime.parseMajor("unknown"));
    }

    @Test
    void parsesProbeOutput() {
        String output = """
                Property settings:
                    java.home = C:\\Program Files\\Java\\jdk-21
                    java.version = 21.0.13.1
                    sun.arch.data.model = 64

                openjdk version "21.0.13.1" 2025-10-21
                OpenJDK Runtime Environment Temurin-21.0.13.1+2 (build 21.0.13.1+2)
                OpenJDK 64-Bit Server VM Temurin-21.0.13.1+2 (build 21.0.13.1+2, mixed mode)
                """;
        JavaRuntime.Info info = JavaRuntime.parseProbeOutput(output);
        assertTrue(info.detected());
        assertEquals(21, info.major());
        assertEquals(64, info.bits());
    }

    @Test
    void parsesLegacyJavaVersionOutput() {
        String output = """
                java version "1.8.0_491"
                Java(TM) SE Runtime Environment (build 1.8.0_491-b12)
                Java HotSpot(TM) Client VM (build 25.491-b09, mixed mode)
                """;
        JavaRuntime.Info info = JavaRuntime.parseProbeOutput(output);
        assertTrue(info.detected());
        assertEquals(8, info.major());
        assertEquals(32, info.bits());
    }

    @Test
    void findsCurrentJavaHome() {
        Path executable = JavaRuntime.executableFor(Path.of(System.getProperty("java.home")));
        assertNotNull(executable);
        assertTrue(Files.isRegularFile(executable));
    }
}
