package org.launchermc;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaInstallerTest {

    @TempDir
    Path temp;

    @Test
    void installDirIsPortable() {
        assertEquals(Path.of("java", "jre-25"), JavaInstaller.installDir(25));
    }

    @Test
    void downloadUrlMatchesAdoptiumApi() {
        String url = JavaInstaller.downloadUrl(25);
        assertTrue(url.contains("/25/ga/"));
        assertTrue(url.contains("/jre/hotspot/normal/eclipse"));
        assertTrue(url.contains("/windows/") || url.contains("/linux/") || url.contains("/mac/"));
        assertTrue(url.endsWith("/x64/jre/hotspot/normal/eclipse")
                || url.endsWith("/x86/jre/hotspot/normal/eclipse")
                || url.endsWith("/aarch64/jre/hotspot/normal/eclipse"));
    }

    @Test
    void extractsZipAndStripsRootDirectory() throws Exception {
        Path zip = temp.resolve("jre.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            out.putNextEntry(new ZipEntry("jdk-test/bin/java.exe"));
            out.write("binary".getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
            out.putNextEntry(new ZipEntry("jdk-test/lib/library.txt"));
            out.write("library".getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }

        Path target = temp.resolve("out");
        JavaInstaller.extractZip(zip, target);

        assertEquals("binary", Files.readString(target.resolve("bin/java.exe")));
        assertEquals("library", Files.readString(target.resolve("lib/library.txt")));
    }

    @Test
    void rejectsZipPathTraversal() throws Exception {
        Path zip = temp.resolve("evil.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            out.putNextEntry(new ZipEntry("root/../../evil.txt"));
            out.write("x".getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
        assertThrows(IOException.class,
                () -> JavaInstaller.extractZip(zip, temp.resolve("out")));
    }

    @Test
    void extractsTarGz() throws Exception {
        byte[] first = tarEntry("jdk-test/bin/java", "binary".getBytes(StandardCharsets.UTF_8),
                0755, '0');
        byte[] second = tarEntry("jdk-test/lib/library.txt",
                "library".getBytes(StandardCharsets.UTF_8), 0644, '0');
        byte[] end = new byte[1024];

        ByteArrayOutputStream tar = new ByteArrayOutputStream();
        tar.write(first);
        tar.write(second);
        tar.write(end);

        Path archive = temp.resolve("jre.tar.gz");
        try (OutputStream raw = Files.newOutputStream(archive);
             GZIPOutputStream gzip = new GZIPOutputStream(raw)) {
            gzip.write(tar.toByteArray());
        }

        Path target = temp.resolve("out");
        JavaInstaller.extractTarGz(archive, target);

        assertEquals("binary", Files.readString(target.resolve("bin/java")));
        assertEquals("library", Files.readString(target.resolve("lib/library.txt")));
    }

    private static byte[] tarEntry(String name, byte[] data, int mode, char type) {
        byte[] header = new byte[512];
        put(header, 0, name.getBytes(StandardCharsets.UTF_8));
        put(header, 100, octal(mode, 8));
        put(header, 124, octal(data.length, 12));
        header[156] = (byte) type;
        put(header, 257, "ustar".getBytes(StandardCharsets.US_ASCII));
        for (int i = 148; i < 156; i++) {
            header[i] = ' ';
        }
        int checksum = 0;
        for (byte b : header) {
            checksum += b & 0xff;
        }
        put(header, 148, octal(checksum, 7));
        header[155] = ' ';

        int padding = (512 - (data.length % 512)) % 512;
        byte[] result = new byte[512 + data.length + padding];
        System.arraycopy(header, 0, result, 0, 512);
        System.arraycopy(data, 0, result, 512, data.length);
        return result;
    }

    private static void put(byte[] target, int offset, byte[] value) {
        System.arraycopy(value, 0, target, offset, value.length);
    }

    private static byte[] octal(long value, int length) {
        String text = Long.toOctalString(value);
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < length - 1 - text.length(); i++) {
            result.append('0');
        }
        result.append(text);
        result.append('\0');
        return result.toString().getBytes(StandardCharsets.US_ASCII);
    }
}
