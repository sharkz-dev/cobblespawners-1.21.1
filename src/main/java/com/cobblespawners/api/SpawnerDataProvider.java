package com.cobblespawners.api;

import net.minecraft.util.math.BlockPos;
import java.util.UUID;

public interface SpawnerDataProvider {
    void cobblespawners$setSpawnerData(BlockPos pos, String dimension, String species, UUID spawnerUUID);
    BlockPos cobblespawners$getSpawnerPos();
    String cobblespawners$getSpawnerDimension();
    String cobblespawners$getSpeciesName();
    UUID cobblespawners$getSpawnerUUID();
    boolean cobblespawners$isFromSpawner();
    long cobblespawners$getSpawnTime();
}
