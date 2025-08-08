// File: PersistenceSettings.kt
package com.cobblespawners.utils

/**
 * Configuración para la persistencia de Pokémon spawneados
 */
data class PersistenceSettings(
    var makePersistent: Boolean = false,
    var legendaryPersistent: Boolean = true,
    var ultraBeastPersistent: Boolean = true,
    var mythicalPersistent: Boolean = true,
    var customPersistentLabels: Set<String> = setOf("boss", "rare", "special")
) {

    /**
     * Verifica si un Pokémon debe ser persistente basado en sus características
     */
    fun shouldBePersistent(species: com.cobblemon.mod.common.pokemon.Species, aspects: Set<String>): Boolean {
        // Si está explícitamente configurado para ser persistente
        if (makePersistent) return true

        // Verificar si es legendario y la configuración está activada
        if (legendaryPersistent && isLegendarySpecies(species)) return true

        // Verificar si es Ultra Beast y la configuración está activada
        if (ultraBeastPersistent && isUltraBeastSpecies(species)) return true

        // Verificar si es mítico y la configuración está activada
        if (mythicalPersistent && isMythicalSpecies(species)) return true

        // Verificar aspectos personalizados
        val aspectsLowerCase = aspects.map { it.lowercase() }
        if (customPersistentLabels.any { label ->
                aspectsLowerCase.contains(label.lowercase())
            }) return true

        return false
    }

    // Funciones auxiliares para verificar tipos de especies
    private fun isLegendarySpecies(species: com.cobblemon.mod.common.pokemon.Species): Boolean {
        return try {
            // Intenta acceder a la propiedad legendary usando diferentes métodos
            val field = species.javaClass.getDeclaredField("legendary")
            field.isAccessible = true
            field.getBoolean(species)
        } catch (e: Exception) {
            // Si no existe, verifica por etiquetas u otros métodos
            species.labels.any { it.equals("legendary", ignoreCase = true) } ||
                    species.name.lowercase() in LEGENDARY_POKEMON_NAMES
        }
    }

    private fun isUltraBeastSpecies(species: com.cobblemon.mod.common.pokemon.Species): Boolean {
        return try {
            val field = species.javaClass.getDeclaredField("ultraBeast")
            field.isAccessible = true
            field.getBoolean(species)
        } catch (e: Exception) {
            species.labels.any { it.equals("ultra_beast", ignoreCase = true) } ||
                    species.name.lowercase() in ULTRA_BEAST_POKEMON_NAMES
        }
    }

    private fun isMythicalSpecies(species: com.cobblemon.mod.common.pokemon.Species): Boolean {
        return try {
            val field = species.javaClass.getDeclaredField("mythical")
            field.isAccessible = true
            field.getBoolean(species)
        } catch (e: Exception) {
            species.labels.any { it.equals("mythical", ignoreCase = true) } ||
                    species.name.lowercase() in MYTHICAL_POKEMON_NAMES
        }
    }

    companion object {
        // Listas de fallback si las propiedades no están disponibles
        private val LEGENDARY_POKEMON_NAMES = setOf(
            "articuno", "zapdos", "moltres", "mewtwo", "mew",
            "raikou", "entei", "suicune", "lugia", "ho_oh",
            "regirock", "regice", "registeel", "latias", "latios",
            "kyogre", "groudon", "rayquaza", "uxie", "mesprit",
            "azelf", "dialga", "palkia", "heatran", "regigigas",
            "giratina", "cresselia", "cobalion", "terrakion",
            "virizion", "tornadus", "thundurus", "reshiram",
            "zekrom", "landorus", "kyurem", "xerneas", "yveltal",
            "zygarde", "tapu_koko", "tapu_lele", "tapu_bulu",
            "tapu_fini", "cosmog", "cosmoem", "solgaleo", "lunala",
            "necrozma", "zacian", "zamazenta", "eternatus",
            "kubfu", "urshifu", "regieleki", "regidrago",
            "glastrier", "spectrier", "calyrex"
        )

        private val ULTRA_BEAST_POKEMON_NAMES = setOf(
            "nihilego", "buzzwole", "pheromosa", "xurkitree",
            "celesteela", "kartana", "guzzlord", "necrozma",
            "poipole", "naganadel", "stakataka", "blacephalon"
        )

        private val MYTHICAL_POKEMON_NAMES = setOf(
            "mew", "celebi", "jirachi", "deoxys", "phione", "manaphy",
            "darkrai", "shaymin", "arceus", "victini", "keldeo",
            "meloetta", "genesect", "diancie", "hoopa", "volcanion",
            "magearna", "marshadow", "zeraora", "meltan", "melmetal",
            "zarude", "pecharunt"
        )
    }
}