package org.springframework.boot.autoconfigure.domain;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Binary-compatible stand-in for the Spring Boot 3 {@code @EntityScan} annotation.
 *
 * <p>yaci-store 2.0.7 (the latest stable release, and still the line compiled for Spring Boot 3)
 * places {@code @EntityScan} from this package on {@code StoreConfiguration},
 * {@code UtxoStoreConfiguration} and {@code YaciStoreAutoConfiguration}. Boot 4 moved the
 * annotation to {@code org.springframework.boot.persistence.autoconfigure.EntityScan} and does not
 * ship this type, so those classes cannot be read without it.
 *
 * <p>This annotation is intentionally not meta-annotated with Boot 4's {@code @EntityScan}.
 * Boot 4's registrar only reads the new annotation type, and importing it from here would fail
 * when the annotated class does not carry that type. The packages yaci-store declares are
 * registered again with the Boot 4 annotation from {@code YaciStoreSpringBoot4Bridge}.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface EntityScan {

    String[] value() default {};

    String[] basePackages() default {};

    Class<?>[] basePackageClasses() default {};
}
