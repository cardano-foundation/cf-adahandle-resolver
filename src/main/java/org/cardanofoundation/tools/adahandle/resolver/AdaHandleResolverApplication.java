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
        SpringApplication.run(AdaHandleResolverApplication.class, args);
    }

}
