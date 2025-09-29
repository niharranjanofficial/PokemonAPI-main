package com.pokemon.api.controller;

import com.pokemon.api.model.Pokemon;
import com.pokemon.api.service.PokeApiService;
import com.pokemon.api.service.PokemonCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/pokemon")
@RequiredArgsConstructor
@CrossOrigin(origins = "*") // For frontend development
public class PokemonController {

    private final PokemonCacheService cacheService;

    @GetMapping
    public ResponseEntity<List<Pokemon>> getAllPokemon(
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "100") int limit) {

        List<Pokemon> pokemonBatch = cacheService.getPokemonBatch(offset, limit);
        return ResponseEntity.ok(pokemonBatch);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Pokemon> getPokemonById(@PathVariable Long id) {
        try {
            // Use cache service instead of direct API call
            List<Pokemon> pokemonList = cacheService.getPokemonBatch((int)(id - 1), 1);
            if (pokemonList.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(pokemonList.get(0));
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }


    @GetMapping("/{id}/details")
    public ResponseEntity<Pokemon> getPokemonDetails(@PathVariable Long id) {
        return getPokemonById(id);
    }

    @PostMapping("/cache/preload")
    public ResponseEntity<String> triggerCachePreload() {
        new Thread(cacheService::preloadPokemonCache).start();
        return ResponseEntity.accepted().body("Cache preload started");
    }
}