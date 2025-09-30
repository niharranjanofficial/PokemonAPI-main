package com.pokemon.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class PokemonApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(PokemonApiApplication.class, args);
    }

}
