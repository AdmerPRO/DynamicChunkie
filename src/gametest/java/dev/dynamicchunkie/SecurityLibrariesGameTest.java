package dev.dynamicchunkie;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.jpountz.lz4.LZ4Factory;

/** Verify actual client class origins, not just Gradle's requested dependency versions. */
public final class SecurityLibrariesGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        context.runOnClient(client -> {
            requireOrigin(LZ4Factory.class, "lz4-java", "lz4");
            requireOrigin(com.google.common.collect.ImmutableList.class, "guava", "guava");
            requireOrigin(org.apache.logging.log4j.LogManager.class, "log4j-api", "log4j");
            requireOrigin(org.apache.logging.log4j.core.LoggerContext.class, "log4j-core", "log4j");
            String netty = expected("netty");
            var versions = io.netty.util.Version.identify();
            if (versions.isEmpty()) throw new AssertionError("Netty version metadata is missing");
            versions.forEach((artifact, version) -> {
                if (!artifact.startsWith("netty-tcnative") && !netty.equals(version.artifactVersion()))
                    throw new AssertionError("Unaligned Netty module: " + artifact + "=" + version.artifactVersion());
            });
            byte[] input = "DynamicChunkie compression regression\n".repeat(256).getBytes(StandardCharsets.UTF_8);
            var factory = LZ4Factory.fastestInstance();
            byte[] compressed = factory.fastCompressor().compress(input);
            byte[] restored = factory.fastDecompressor().decompress(compressed, input.length);
            if (!Arrays.equals(input, restored)) throw new AssertionError("Patched LZ4 round trip failed");
            DynamicChunkie.LOG.info("Security library regression passed: patched LZ4/Guava/Log4j origins, aligned Netty and compression round trip");
        });
    }

    private static String expected(String key) {
        String value = System.getProperty("dynamicchunkie.security." + key);
        if (value == null) throw new AssertionError("Missing security test version: " + key);
        return value;
    }

    private static void requireOrigin(Class<?> type, String artifact, String key) {
        String source = String.valueOf(type.getProtectionDomain().getCodeSource().getLocation());
        if (!source.endsWith("/" + artifact + "-" + expected(key) + ".jar"))
            throw new AssertionError("Unexpected runtime library for " + type.getName() + ": " + source);
    }
}
