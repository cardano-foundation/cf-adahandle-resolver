package org.cardanofoundation.tools.adahandle.resolver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EnableJpaRepositories
@EntityScan
public class AdaHandleResolverApplication {

    public static void main(String[] args) {
        // JavaScript plugins run on GraalJS's interpreter on a standard JDK, which is fine for short scripts.
        // Without this, GraalJS prints a warning about it for every plugin. -D on the command line still wins.
        if (System.getProperty("polyglot.engine.WarnInterpreterOnly") == null) {
            System.setProperty("polyglot.engine.WarnInterpreterOnly", "false");
        }
        SpringApplication.run(AdaHandleResolverApplication.class, args);
    }

}
