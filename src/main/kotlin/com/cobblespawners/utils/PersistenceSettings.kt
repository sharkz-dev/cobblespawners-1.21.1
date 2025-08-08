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
        if (legendaryPersistent && species.legendary) return true

        // Verificar si es Ultra Beast y la configuración está activada
        if (ultraBeastPersistent && species.ultraBeast) return true

        // Verificar si es mítico y la configuración está activada
        if (mythicalPersistent && species.mythical) return true

        // Verificar aspectos personalizados
        val aspectsLowerCase = aspects.map { it.lowercase() }
        if (customPersistentLabels.any { label ->
                aspectsLowerCase.contains(label.lowercase())
            }) return true

        return false
    }
}