package com.comphenix.protocol;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import com.comphenix.protocol.reflect.accessors.Accessors;
import com.comphenix.protocol.reflect.accessors.FieldAccessor;
import com.comphenix.protocol.utility.MinecraftReflectionTestUtil;

import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.MoreExecutors;
import net.minecraft.SharedConstants;
import net.minecraft.commands.Commands.CommandSelection;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.server.WorldLoader;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.server.packs.resources.CloseableResourceManager;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import org.apache.logging.log4j.LogManager;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_21_R1.CraftLootTable;
import org.bukkit.craftbukkit.v1_21_R1.CraftRegistry;
import org.bukkit.craftbukkit.v1_21_R1.CraftServer;
import org.bukkit.craftbukkit.v1_21_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_21_R1.inventory.CraftItemFactory;
import org.bukkit.craftbukkit.v1_21_R1.tag.CraftBlockTag;
import org.bukkit.craftbukkit.v1_21_R1.tag.CraftEntityTag;
import org.bukkit.craftbukkit.v1_21_R1.tag.CraftFluidTag;
import org.bukkit.craftbukkit.v1_21_R1.tag.CraftItemTag;
import org.bukkit.craftbukkit.v1_21_R1.util.CraftMagicNumbers;
import org.bukkit.craftbukkit.v1_21_R1.util.CraftNamespacedKey;
import org.bukkit.craftbukkit.v1_21_R1.util.Versioning;
import org.spigotmc.SpigotWorldConfig;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Used to ensure that ProtocolLib and Bukkit is prepared to be tested.
 *
 * @author Kristian
 */
public class BukkitInitialization {

    private static final BukkitInitialization instance = new BukkitInitialization();
    private boolean initialized;
    private boolean packaged;

    private BukkitInitialization() {
        System.out.println("Created new BukkitInitialization on " + Thread.currentThread().getName());
    }

    /**
     * Statically initializes the mock server for unit testing
     */
    public static void initializeAll() {
        instance.initialize();
    }

    private static final Object initLock = new Object();

    /**
     * Initialize Bukkit and ProtocolLib such that we can perform unit testing
     */
    private void initialize() {
        if (initialized) {
            return;
        }

        synchronized (initLock) {
            if (initialized) {
                return;
            } else {
                initialized = true;
            }

            try {
                LogManager.getLogger();
            } catch (Throwable ex) {
                // Happens only on my Jenkins, but if it errors here it works when it matters
                ex.printStackTrace();
            }

            instance.setPackage();

            // Minecraft Data Init
            SharedConstants.tryDetectVersion();
            Bootstrap.bootStrap();

            PackRepository resourcePackRepository = ServerPacksSource.createVanillaTrustedRepository();
            resourcePackRepository.reload();

            CloseableResourceManager resourceManager = new MultiPackResourceManager(
                PackType.SERVER_DATA,
                resourcePackRepository.getAvailablePacks().stream().map(Pack::open).collect(Collectors.toList())
            );
            LayeredRegistryAccess<RegistryLayer> layeredRegistryAccess = RegistryLayer.createRegistryAccess();
            layeredRegistryAccess = WorldLoader.loadAndReplaceLayer(resourceManager, layeredRegistryAccess, RegistryLayer.WORLDGEN, RegistryDataLoader.WORLDGEN_REGISTRIES);
            RegistryAccess.Frozen registryCustom = layeredRegistryAccess.compositeAccess().freeze();

            ReloadableServerResources dataPackResources = ReloadableServerResources.loadResources(
                resourceManager,
                layeredRegistryAccess,
                FeatureFlags.REGISTRY.allFlags(),
                CommandSelection.DEDICATED,
                0,
                MoreExecutors.directExecutor(),
                MoreExecutors.directExecutor()
            ).join();
            dataPackResources.updateRegistryTags();

            try {
                RegistryAccess.class.getName();
            } catch (Throwable ex) {
                ex.printStackTrace();
            }

            String releaseTarget = MinecraftReflectionTestUtil.RELEASE_TARGET;
            String serverVersion = CraftServer.class.getPackage().getImplementationVersion();

            // Mock the server object
            CraftServer mockedServer = mock(CraftServer.class);
            DedicatedServer mockedGameServer = mock(DedicatedServer.class);

            when(mockedGameServer.registryAccess()).thenReturn(registryCustom);

            when(mockedServer.getLogger()).thenReturn(java.util.logging.Logger.getLogger("Minecraft"));
            when(mockedServer.getName()).thenReturn("Mock Server");
            when(mockedServer.getVersion()).thenReturn(serverVersion + " (MC: " + releaseTarget + ")");
            when(mockedServer.getBukkitVersion()).thenReturn(Versioning.getBukkitVersion());
            when(mockedServer.getServer()).thenReturn(mockedGameServer);

            when(mockedServer.isPrimaryThread()).thenReturn(true);
            when(mockedServer.getItemFactory()).thenReturn(CraftItemFactory.instance());
            when(mockedServer.getUnsafe()).thenReturn(CraftMagicNumbers.INSTANCE);
            when(mockedServer.getLootTable(any())).thenAnswer(invocation -> {
                NamespacedKey key = invocation.getArgument(0);
                return new CraftLootTable(key, dataPackResources.fullRegistries().getLootTable(CraftLootTable.bukkitKeyToMinecraft(key)));
            });

            when(mockedServer.getRegistry(any())).thenAnswer(invocation -> {
                Class<? extends Keyed> registryType = invocation.getArgument(0);

                try {
                    return CraftRegistry.createRegistry(registryType, registryCustom);
                } catch (Exception ignored) {
                    return mock(org.bukkit.Registry.class);
                }
            });

            when(mockedServer.getTag(any(), any(), any())).then(mock -> {
                String registry = mock.getArgument(0);
                Class<?> clazz = mock.getArgument(2);
                ResourceLocation key = CraftNamespacedKey.toMinecraft(mock.getArgument(1));

                switch (registry) {
                    case org.bukkit.Tag.REGISTRY_BLOCKS -> {
                        Preconditions.checkArgument(clazz == org.bukkit.Material.class, "Block namespace must have block type");
                        TagKey<Block> blockTagKey = TagKey.create(Registries.BLOCK, key);
                        if (BuiltInRegistries.BLOCK.getTag(blockTagKey).isPresent()) {
                            return new CraftBlockTag(BuiltInRegistries.BLOCK, blockTagKey);
                        }
                    }
                    case org.bukkit.Tag.REGISTRY_ITEMS -> {
                        Preconditions.checkArgument(clazz == org.bukkit.Material.class, "Item namespace must have item type");
                        TagKey<Item> itemTagKey = TagKey.create(Registries.ITEM, key);
                        if (BuiltInRegistries.ITEM.getTag(itemTagKey).isPresent()) {
                            return new CraftItemTag(BuiltInRegistries.ITEM, itemTagKey);
                        }
                    }
                    case org.bukkit.Tag.REGISTRY_FLUIDS -> {
                        Preconditions.checkArgument(clazz == org.bukkit.Fluid.class, "Fluid namespace must have fluid type");
                        TagKey<Fluid> fluidTagKey = TagKey.create(Registries.FLUID, key);
                        if (BuiltInRegistries.FLUID.getTag(fluidTagKey).isPresent()) {
                            return new CraftFluidTag(BuiltInRegistries.FLUID, fluidTagKey);
                        }
                    }
                    case org.bukkit.Tag.REGISTRY_ENTITY_TYPES -> {
                        Preconditions.checkArgument(clazz == org.bukkit.entity.EntityType.class, "Entity type namespace must have entity type");
                        TagKey<EntityType<?>> entityTagKey = TagKey.create(Registries.ENTITY_TYPE, key);
                        if (BuiltInRegistries.ENTITY_TYPE.getTag(entityTagKey).isPresent()) {
                            return new CraftEntityTag(BuiltInRegistries.ENTITY_TYPE, entityTagKey);
                        }
                    }
                    default -> throw new IllegalArgumentException();
                }

                return null;
            });

            ServerLevel nmsWorld = mock(ServerLevel.class);
            SpigotWorldConfig mockWorldConfig = mock(SpigotWorldConfig.class);

            try {
                FieldAccessor spigotConfig = Accessors.getFieldAccessor(nmsWorld.getClass().getField("spigotConfig"));
                spigotConfig.set(nmsWorld, mockWorldConfig);
            } catch (ReflectiveOperationException ex) {
                throw new RuntimeException(ex);
            }

            CraftWorld world = mock(CraftWorld.class);
            when(world.getHandle()).thenReturn(nmsWorld);

            List<World> worlds = Collections.singletonList(world);
            when(mockedServer.getWorlds()).thenReturn(worlds);

            // Inject this fake server & our registry (must happen after server set)
            Bukkit.setServer(mockedServer);
            CraftRegistry.setMinecraftRegistry(registryCustom);

            // Init Enchantments
            Enchantments.AQUA_AFFINITY.getClass();
            // Enchantment.stopAcceptingRegistrations();
        }
    }

    /**
     * Ensure that package names are correctly set up.
     */
    private void setPackage() {
        if (!this.packaged) {
            this.packaged = true;

            try {
                LogManager.getLogger();
            } catch (Throwable ex) {
                // Happens only on my Jenkins, but if it errors here it works when it matters
                ex.printStackTrace();
            }

            MinecraftReflectionTestUtil.init();
        }
    }
}
