package org.cardanofoundation.tools.adahandle.resolver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan({"org.cardanofoundation.tools.adahandle.resolver.repository", "org.cardanofoundation.tools.adahandle.resolver.storage"})
public class AdaHandleResolverApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdaHandleResolverApplication.class, args);
    }

}
