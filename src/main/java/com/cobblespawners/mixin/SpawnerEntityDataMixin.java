package com.cobblespawners.mixin;

import com.cobblespawners.api.SpawnerDataProvider;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.UUID;

@Mixin(Entity.class)
public class SpawnerEntityDataMixin implements SpawnerDataProvider {
	@Unique private static final String NBT_KEY = "CobbleSpawnerData";
	@Unique private static final String SPAWNER_POS_KEY = "SpawnerPos";
	@Unique private static final String SPAWNER_DIMENSION_KEY = "SpawnerDimension";
	@Unique private static final String SPECIES_NAME_KEY = "SpeciesName";
	@Unique private static final String SPAWN_TIME_KEY = "SpawnTime";
	@Unique private static final String SPAWNER_UUID_KEY = "SpawnerUUID";

	@Unique private BlockPos spawnerPos = null;
	@Unique private String spawnerDimension = "";
	@Unique private String speciesName = "";
	@Unique private long spawnTime = 0L;
	@Unique private UUID spawnerUUID = null;
	@Unique private boolean isFromSpawner = false;

	@Inject(method = "writeNbt", at = @At("HEAD"))
	private void writeSpawnerData(NbtCompound nbt, CallbackInfoReturnable<NbtCompound> cir) {
		if (isFromSpawner && spawnerPos != null && spawnerUUID != null) {
			NbtCompound spawnerData = new NbtCompound();
			spawnerData.putInt("X", spawnerPos.getX());
			spawnerData.putInt("Y", spawnerPos.getY());
			spawnerData.putInt("Z", spawnerPos.getZ());
			spawnerData.putString(SPAWNER_DIMENSION_KEY, spawnerDimension);
			spawnerData.putString(SPECIES_NAME_KEY, speciesName);
			spawnerData.putLong(SPAWN_TIME_KEY, spawnTime);
			spawnerData.putUuid(SPAWNER_UUID_KEY, spawnerUUID);
			nbt.put(NBT_KEY, spawnerData);
		}
	}

	@Inject(method = "readNbt", at = @At("HEAD"))
	private void readSpawnerData(NbtCompound nbt, CallbackInfo ci) {
		if (nbt != null && nbt.contains(NBT_KEY)) {
			NbtCompound spawnerData = nbt.getCompound(NBT_KEY);
			int x = spawnerData.getInt("X");
			int y = spawnerData.getInt("Y");
			int z = spawnerData.getInt("Z");
			spawnerPos = new BlockPos(x, y, z);
			spawnerDimension = spawnerData.getString(SPAWNER_DIMENSION_KEY);
			speciesName = spawnerData.getString(SPECIES_NAME_KEY);
			spawnTime = spawnerData.getLong(SPAWN_TIME_KEY);
			spawnerUUID = spawnerData.getUuid(SPAWNER_UUID_KEY);
			isFromSpawner = true;
		} else {
			resetData();
		}
	}

	@Unique
	private void resetData() {
		spawnerPos = null;
		spawnerDimension = "";
		speciesName = "";
		spawnTime = 0L;
		spawnerUUID = null;
		isFromSpawner = false;
	}

	@Override
	public void cobblespawners$setSpawnerData(BlockPos pos, String dimension, String species, UUID uuid) {
		this.spawnerPos = pos;
		this.spawnerDimension = dimension;
		this.speciesName = species;
		this.spawnerUUID = uuid;
		this.spawnTime = System.currentTimeMillis();
		this.isFromSpawner = pos != null;
	}

	@Override public BlockPos cobblespawners$getSpawnerPos() { return spawnerPos; }
	@Override public String cobblespawners$getSpawnerDimension() { return spawnerDimension; }
	@Override public String cobblespawners$getSpeciesName() { return speciesName; }
	@Override public UUID cobblespawners$getSpawnerUUID() { return spawnerUUID; }
	@Override public boolean cobblespawners$isFromSpawner() { return isFromSpawner; }
	@Override public long cobblespawners$getSpawnTime() { return spawnTime; }
}