// File: PokemonPersistenceMixin.java
package com.cobblespawners.mixin;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblespawners.api.SpawnerDataProvider;
import com.cobblespawners.utils.CobbleSpawnersConfig;
import com.cobblespawners.utils.PokemonSpawnEntry;
import net.minecraft.entity.SpawnReason;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PokemonEntity.class)
public class PokemonPersistenceMixin {

    /**
     * Intercepta el método canImmediatelyDespawn para evitar que ciertos Pokémon despawneen
     */
    @Inject(method = "canImmediatelyDespawn", at = @At("HEAD"), cancellable = true)
    private void preventLegendaryDespawn(double distanceSquared, CallbackInfoReturnable<Boolean> cir) {
        PokemonEntity pokemonEntity = (PokemonEntity) (Object) this;

        // Solo aplicar a Pokémon spawneados por nuestros spawners
        if (pokemonEntity instanceof SpawnerDataProvider provider && provider.cobblespawners$isFromSpawner()) {
            try {
                var spawnerPos = provider.cobblespawners$getSpawnerPos();
                var speciesName = provider.cobblespawners$getSpeciesName();

                if (spawnerPos != null && speciesName != null) {
                    var spawnerData = CobbleSpawnersConfig.INSTANCE.getSpawner(spawnerPos);
                    if (spawnerData != null) {
                        // Buscar la entrada del Pokémon específico
                        var pokemon = pokemonEntity.getPokemon();
                        var species = pokemon.getSpecies();
                        var formName = pokemon.getForm().getName();
                        var aspects = pokemon.getAspects();

                        // Normalizar el nombre del form
                        if (formName.equals("Standard")) formName = "Normal";

                        // Buscar la entrada correspondiente
                        for (PokemonSpawnEntry entry : spawnerData.getSelectedPokemon()) {
                            boolean speciesMatch = entry.getPokemonName().equalsIgnoreCase(species.showdownId());
                            boolean formMatch = (entry.getFormName() == null && formName.equals("Normal")) ||
                                    (entry.getFormName() != null && entry.getFormName().equalsIgnoreCase(formName));

                            if (speciesMatch && formMatch) {
                                // Verificar si debe ser persistente
                                var persistenceSettings = entry.getPersistenceSettings();
                                if (persistenceSettings != null &&
                                        persistenceSettings.shouldBePersistent(species, aspects)) {
                                    // Hacer persistente el Pokémon
                                    pokemonEntity.setPersistent();
                                    // Cancelar el despawn inmediato
                                    cir.setReturnValue(false);
                                    return;
                                }
                                break;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                // Log error but don't crash
                System.err.println("Error checking Pokemon persistence: " + e.getMessage());
            }
        }
    }

    /**
     * Intercepta shouldSave para asegurar que los Pokémon persistentes se guarden
     */
    @Inject(method = "shouldSave", at = @At("HEAD"), cancellable = true)
    private void ensurePersistentSave(CallbackInfoReturnable<Boolean> cir) {
        PokemonEntity pokemonEntity = (PokemonEntity) (Object) this;

        if (pokemonEntity instanceof SpawnerDataProvider provider && provider.cobblespawners$isFromSpawner()) {
            try {
                var spawnerPos = provider.cobblespawners$getSpawnerPos();
                if (spawnerPos != null) {
                    var spawnerData = CobbleSpawnersConfig.INSTANCE.getSpawner(spawnerPos);
                    if (spawnerData != null) {
                        var pokemon = pokemonEntity.getPokemon();
                        var species = pokemon.getSpecies();
                        var formName = pokemon.getForm().getName();
                        var aspects = pokemon.getAspects();

                        if (formName.equals("Standard")) formName = "Normal";

                        for (PokemonSpawnEntry entry : spawnerData.getSelectedPokemon()) {
                            boolean speciesMatch = entry.getPokemonName().equalsIgnoreCase(species.showdownId());
                            boolean formMatch = (entry.getFormName() == null && formName.equals("Normal")) ||
                                    (entry.getFormName() != null && entry.getFormName().equalsIgnoreCase(formName));

                            if (speciesMatch && formMatch) {
                                var persistenceSettings = entry.getPersistenceSettings();
                                if (persistenceSettings != null &&
                                        persistenceSettings.shouldBePersistent(species, aspects)) {
                                    // Forzar que se guarde
                                    cir.setReturnValue(true);
                                    return;
                                }
                                break;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("Error checking Pokemon save persistence: " + e.getMessage());
            }
        }
    }
}