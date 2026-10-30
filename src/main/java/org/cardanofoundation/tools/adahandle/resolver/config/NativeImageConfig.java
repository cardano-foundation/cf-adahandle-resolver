package org.cardanofoundation.tools.adahandle.resolver.config;

import com.bloxbean.cardano.yaci.store.common.config.StoreProperties;
import com.bloxbean.cardano.yaci.store.core.storage.impl.CursorRepository;
import com.bloxbean.cardano.yaci.store.core.storage.impl.CursorStorageImpl;
import com.bloxbean.cardano.yaci.store.utxo.storage.impl.AddressStorageImpl;
import org.flywaydb.core.api.Location;
import org.flywaydb.core.api.ResourceProvider;
import org.flywaydb.core.api.resource.LoadableResource;
import org.flywaydb.core.internal.resource.classpath.ClassPathResource;
import org.jooq.DSLContext;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;
import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.core.NativeDetector;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Support for running as a GraalVM native image (see the "native" Maven profile and Dockerfile.native).
 * The Flyway provider and runtime hints only apply in a native image; the storage beans below are the
 * same beans yaci-store would create, declared by their implementation type.
 */
@Configuration
@ImportRuntimeHints(NativeImageConfig.Hints.class)
public class NativeImageConfig {

    /**
     * Spring Boot's native Flyway resource provider lists a classpath location in the first jar
     * that contains it only. yaci-store ships migrations for the same location (e.g. db/store/h2)
     * in several jars (yaci-store-core, yaci-store-utxo), so the others would be silently skipped.
     * Boot only installs its provider when none is set, so this one takes precedence.
     */
    @Bean
    public FlywayConfigurationCustomizer allJarsMigrationResourceProvider() {
        return configuration -> {
            if (NativeDetector.inNativeImage()) {
                configuration.resourceProvider(new AllJarsResourceProvider(configuration.getClassLoader(),
                        Arrays.asList(configuration.getLocations()), configuration.getEncoding()));
            }
        };
    }

    /*
     * yaci-store declares these @Transactional beans by their interface type (CursorStorage,
     * AddressStorage), so AOT can't pregenerate the CGLIB proxy of the implementation class and a
     * native image can't generate it at runtime. Declaring the implementation type here (yaci-store's
     * definitions are @ConditionalOnMissingBean) lets AOT generate the proxies. Same beans on the JVM.
     */
    @Bean
    public CursorStorageImpl cursorStorage(CursorRepository cursorRepository) {
        return new CursorStorageImpl(cursorRepository);
    }

    @Bean
    public AddressStorageImpl addressStorage(DSLContext dslContext, StoreProperties storeProperties) {
        return new AddressStorageImpl(dslContext, storeProperties);
    }

    static class AllJarsResourceProvider implements ResourceProvider {
        private final ClassLoader classLoader;
        private final Collection<Location> locations;
        private final Charset encoding;
        private Map<String, LoadableResource> resources;

        AllJarsResourceProvider(ClassLoader classLoader, Collection<Location> locations, Charset encoding) {
            this.classLoader = classLoader;
            this.locations = locations;
            this.encoding = encoding;
        }

        @Override
        public LoadableResource getResource(String name) {
            if (classLoader.getResource(name) == null) {
                return null;
            }
            return new ClassPathResource(null, name, classLoader, encoding);
        }

        @Override
        public synchronized Collection<LoadableResource> getResources(String prefix, String[] suffixes) {
            if (resources == null) {
                resources = findAll();
            }
            List<LoadableResource> result = new ArrayList<>();
            for (LoadableResource resource : resources.values()) {
                String filename = resource.getFilename();
                if (filename.startsWith(prefix) && Arrays.stream(suffixes).anyMatch(filename::endsWith)) {
                    result.add(resource);
                }
            }
            return result;
        }

        private Map<String, LoadableResource> findAll() {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver(classLoader);
            Map<String, LoadableResource> found = new LinkedHashMap<>();
            for (Location location : locations) {
                if (!location.isClassPath()) {
                    continue;
                }
                try {
                    // classpath*: visits the location in every jar, not only the first one.
                    for (Resource resource : resolver.getResources("classpath*:" + location.getPath() + "/*")) {
                        String filename = resource.getFilename();
                        if (filename == null || filename.isEmpty()) {
                            continue;
                        }
                        String path = location.getPath() + "/" + filename;
                        found.putIfAbsent(path, new ClassPathResource(location, path, classLoader, encoding));
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException("Failed to list migrations in " + location.getDescriptor(), e);
                }
            }
            return found;
        }
    }

    static class Hints implements RuntimeHintsRegistrar {
        @Override
        public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
            // Netty (yaci's node client) reads java.nio.Bits.UNALIGNED through Unsafe while initializing;
            // on the JVM this bypasses reflection, so the tracing agent doesn't record it.
            hints.reflection().registerType(TypeReference.of("java.nio.Bits"), type -> type.withField("UNALIGNED"));
            // Flyway migrations from all jars and vendors (a trace only sees the vendor it ran on).
            hints.resources().registerPattern("db/store/**");
            hints.resources().registerPattern("db/migration/**");
        }
    }
}
