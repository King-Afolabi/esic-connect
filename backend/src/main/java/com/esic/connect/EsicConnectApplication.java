package com.esic.connect;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Point d'entrée du back-end ESIC Connect.
 *
 * L'application est un monolithe modulaire (voir docs/03-architecture.md §5) :
 * chaque sous-package direct de {@code com.esic.connect} constitue un module
 * métier vérifié par Spring Modulith (voir {@code ModularityTests}).
 */
@SpringBootApplication
public class EsicConnectApplication {

    public static void main(String[] args) {
        SpringApplication.run(EsicConnectApplication.class, args);
    }
}
