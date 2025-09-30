package com.pokemon.api.service;

import com.pokemon.api.model.Pokemon;
import com.pokemon.api.model.PokemonSprites;
import com.pokemon.api.model.PokemonStat;
import com.pokemon.api.response.PokeApiResponse;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Slf4j
@Service
public class PokeApiService {

    private final WebClient webClient;
    private final ExecutorService executorService;

    @Value("${pokeapi.max-pokemon:1025}")
    private int maxPokemon;

    public PokeApiService(WebClient.Builder webClientBuilder,
                          @Value("${pokeapi.base-url}") String baseUrl) {
        this.webClient = webClientBuilder
                .baseUrl(baseUrl)
                .codecs(configurer -> configurer
                        .defaultCodecs()
                        .maxInMemorySize(10 * 1024 * 1024)) // Increase buffer size
                .build();
        this.executorService = Executors.newFixedThreadPool(10); // For parallel fetching
    }

    public Pokemon getPokemonById(Long id) {
        log.info("Fetching Pokémon data from PokeAPI for ID: {}", id);

        PokeApiResponse response = webClient.get()
                .uri("/pokemon/{id}", id)
                .retrieve()
                .bodyToMono(PokeApiResponse.class)
                .block();

        return mapToPokemon(response);
    }

    public List<Pokemon> getAllPokemon() {
        log.info("Fetching all Pokémon data from PokeAPI");

        // First, get the list of all Pokémon with basic info
        PokemonListResponse listResponse = webClient.get()
                .uri("/pokemon?limit={limit}", maxPokemon)
                .retrieve()
                .bodyToMono(PokemonListResponse.class)
                .block();

        if (listResponse == null || listResponse.getResults() == null) {
            return List.of();
        }

        // Fetch detailed information for each Pokémon in parallel
        List<CompletableFuture<Pokemon>> futures = listResponse.getResults().stream()
                .map(pokemonRef -> CompletableFuture.supplyAsync(() -> {
                    try {
                        String[] urlParts = pokemonRef.getUrl().split("/");
                        Long pokemonId = Long.parseLong(urlParts[urlParts.length - 1]);
                        return getPokemonById(pokemonId); // This will use cache if available
                    } catch (Exception e) {
                        log.warn("Failed to fetch Pokémon from URL {}: {}", pokemonRef.getUrl(), e.getMessage());
                        return null;
                    }
                }, executorService))
                .toList();

        // Wait for all completions and collect results
        return futures.stream()
                .map(CompletableFuture::join)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private Pokemon mapToPokemon(PokeApiResponse response) {
        Pokemon pokemon = new Pokemon();
        pokemon.setId(response.getId());
        pokemon.setName(capitalizeName(response.getName()));
        pokemon.setTypes(response.getTypes().stream()
                .map(type -> type.getType().getName())
                .toList());
        pokemon.setHeight(response.getHeight());
        pokemon.setWeight(response.getWeight());

        // Map sprites
        PokemonSprites sprites = new PokemonSprites();
        sprites.setFrontDefault(response.getSprites().getFrontDefault());
        sprites.setBackDefault(response.getSprites().getBackDefault());
        sprites.setOfficialArtwork(response.getSprites().getOther().getOfficialArtwork().getFrontDefault());
        pokemon.setSprites(sprites);

        // Map stats
        pokemon.setStats(response.getStats().stream()
                .map(stat -> {
                    PokemonStat pokemonStat = new PokemonStat();
                    pokemonStat.setName(stat.getStat().getName());
                    pokemonStat.setBaseStat(stat.getBaseStat());
                    return pokemonStat;
                })
                .toList());

        // Set region based on Pokémon ID ranges
        pokemon.setRegion(determineRegion(response.getId()));

        // Calculate weaknesses (simplified for MVP)
        pokemon.setWeaknesses(calculateWeaknesses(response.getTypes()));

        return pokemon;
    }

    private String capitalizeName(String name) {
        return name.substring(0, 1).toUpperCase() + name.substring(1);
    }

    private String determineRegion(Long pokemonId) {
        if (pokemonId <= 151) return "Kanto";
        else if (pokemonId <= 251) return "Johto";
        else if (pokemonId <= 386) return "Hoenn";
        else if (pokemonId <= 493) return "Sinnoh";
        else if (pokemonId <= 649) return "Unova";
        else if (pokemonId <= 721) return "Kalos";
        else if (pokemonId <= 809) return "Alola";
        else if (pokemonId <= 905) return "Galar";
        else return "Paldea";
    }

   private static final Map<String, List<String>> TYPE_WEAKNESSES = Map.ofEntries(
            Map.entry("normal", List.of("Fighting")),
            Map.entry("fire", List.of("Water", "Ground", "Rock")),
            Map.entry("water", List.of("Electric", "Grass")),
            Map.entry("electric", List.of("Ground")),
            Map.entry("grass", List.of("Fire", "Ice", "Poison", "Flying", "Bug")),
            Map.entry("ice", List.of("Fire", "Fighting", "Rock", "Steel")),
            Map.entry("fighting", List.of("Flying", "Psychic", "Fairy")),
            Map.entry("poison", List.of("Ground", "Psychic")),
            Map.entry("ground", List.of("Water", "Grass", "Ice")),
            Map.entry("flying", List.of("Electric", "Ice", "Rock")),
            Map.entry("psychic", List.of("Bug", "Ghost", "Dark")),
            Map.entry("bug", List.of("Fire", "Flying", "Rock")),
            Map.entry("rock", List.of("Water", "Grass", "Fighting", "Ground", "Steel")),
            Map.entry("ghost", List.of("Ghost", "Dark")),
            Map.entry("dragon", List.of("Ice", "Dragon", "Fairy")),
            Map.entry("dark", List.of("Fighting", "Bug", "Fairy")),
            Map.entry("steel", List.of("Fire", "Fighting", "Ground")),
            Map.entry("fairy", List.of("Poison", "Steel"))
    );
    private List<String> calculateWeaknesses(List<PokeApiResponse.PokemonType> types) {
        return types.stream()
                .flatMap(type -> TYPE_WEAKNESSES.getOrDefault(type.getType().getName().toLowerCase(), List.of()).stream())
                .distinct()
                .toList();
    }

    @Data
    private static class PokemonListResponse {
        private List<NamedAPIResource> results;

        @Data
        public static class NamedAPIResource {
            private String name;
            private String url;
        }
    }
}
