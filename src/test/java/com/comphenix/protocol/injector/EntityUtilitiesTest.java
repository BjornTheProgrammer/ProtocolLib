package com.comphenix.protocol.injector;

import com.comphenix.protocol.BukkitInitialization;

import org.junit.jupiter.api.BeforeAll;

public class EntityUtilitiesTest {

    @BeforeAll
    public static void beforeClass() {
        BukkitInitialization.initializeAll();
    }

    /*
    @Test
    public void testReflection() {
        CraftWorld bukkit = mock(CraftWorld.class);
        ServerLevel world = mock(ServerLevel.class);
        when(bukkit.getHandle()).thenReturn(world);

        ChunkProviderServer provider = mock(ChunkProviderServer.class);
		when(world.l()).thenReturn(provider);

        PlayerChunkMap chunkMap = mock(PlayerChunkMap.class);
        Field chunkMapField = FuzzyReflection.fromClass(ChunkProviderServer.class, true)
                .getField(FuzzyFieldContract.newBuilder().typeExact(PlayerChunkMap.class).build());
        setFinalField(provider, chunkMapField, chunkMap);

        CraftEntity bukkitEntity = mock(CraftEntity.class);
        Entity fakeEntity = mock(Entity.class);
        when(fakeEntity.getBukkitEntity()).thenReturn(bukkitEntity);

        EntityTracker tracker = mock(EntityTracker.class);
        Field trackerField = FuzzyReflection.fromClass(EntityTracker.class, true)
                .getField(FuzzyFieldContract.newBuilder().typeExact(Entity.class).build());
        setFinalField(tracker, trackerField, fakeEntity);

        Int2ObjectMap<EntityTracker> trackerMap = new Int2ObjectOpenHashMap<>();
        trackerMap.put(1, tracker);
        Field trackedEntitiesField = FuzzyReflection.fromClass(PlayerChunkMap.class, true)
                .getField(FuzzyFieldContract.newBuilder().typeExact(Int2ObjectMap.class).build());
        setFinalField(chunkMap, trackedEntitiesField, trackerMap);
    }
    */
}
