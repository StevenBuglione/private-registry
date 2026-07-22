package com.stevenbuglione.registry;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
public class PrivateRegistryApplication {

    public static void main(String[] args) {
        var application = SpringApplication.run(PrivateRegistryApplication.class, args);
        if (application.getEnvironment()
                .getProperty("registry.seed.exit-after-completion", Boolean.class, false)) {
            System.exit(SpringApplication.exit(application, () -> 0));
        }
    }
}
