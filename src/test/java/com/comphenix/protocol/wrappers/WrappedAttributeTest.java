package com.comphenix.protocol.wrappers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.comphenix.protocol.BukkitInitialization;
import com.comphenix.protocol.reflect.cloning.BukkitCloner;
import com.comphenix.protocol.wrappers.WrappedAttributeModifier.Operation;

import com.google.common.collect.Lists;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket.AttributeSnapshot;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class WrappedAttributeTest {

    private WrappedAttributeModifier doubleModifier;
    private WrappedAttributeModifier constantModifier;
    private WrappedAttribute attribute;

    @BeforeAll
    public static void initializeBukkit() {
        BukkitInitialization.initializeAll();
    }

    @BeforeEach
    public void setUp() {
        this.doubleModifier = WrappedAttributeModifier.newBuilder()
            .key("protocollib", "double_damage")
            .amount(1.0D)
            .operation(Operation.ADD_PERCENTAGE)
            .build();

        this.constantModifier = WrappedAttributeModifier.newBuilder()
            .key("protocollib", "damage_bonus")
            .amount(5.0D)
            .operation(Operation.ADD_NUMBER)
            .build();

        this.attribute = WrappedAttribute.newBuilder()
            .attributeKey("generic.attackDamage")
            .baseValue(2.0D)
            .modifiers(Lists.newArrayList(this.constantModifier, this.doubleModifier))
            .build();
    }

    @Test
    public void testCreateHandle() {
        WrappedAttribute wrapper = WrappedAttribute.newBuilder()
            .baseValue(2.0)
            .attributeKey("generic.attackDamage")
            .modifiers(List.of(constantModifier, doubleModifier))
            .build();

        AttributeSnapshot handle = (AttributeSnapshot) wrapper.getHandle();
        assertNotNull(handle);
        assertEquals(2.0D, handle.base());

        Attribute attribute = handle.attribute().value();
        assertEquals("attribute.name.generic.attack_damage", attribute.getDescriptionId());

        Map<ResourceLocation, AttributeModifier> modifiers = handle.modifiers().stream().collect(Collectors.toMap(m -> m.id(), m -> m));
        AttributeModifier modifier1 = modifiers.get(ResourceLocation.parse("protocollib:double_damage"));
        AttributeModifier modifier2 = modifiers.get(ResourceLocation.parse("protocollib:damage_bonus"));

        assertEquals(1.0D, modifier1.amount());
        assertEquals(AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, modifier1.operation());

        assertEquals(5.0D, modifier2.amount());
        assertEquals(AttributeModifier.Operation.ADD_VALUE, modifier2.operation());
    }

    @Test
    public void testFromHandle() {
        AttributeSnapshot handle = new AttributeSnapshot(
            Attributes.ATTACK_SPEED,
            5.0D,
            List.of(new AttributeModifier(ResourceLocation.parse("protocollib:test"),
                1.0D, AttributeModifier.Operation.ADD_VALUE))
        );
        WrappedAttribute wrapper = WrappedAttribute.fromHandle(handle);
        assertEquals("generic.attack_speed", wrapper.getAttributeKey());
        assertEquals(5.0D, wrapper.getBaseValue());
        WrappedAttributeModifier modifier = wrapper.getModifiers().iterator().next();
        assertEquals(1.0D, modifier.getAmount());
        assertEquals("protocollib:test", modifier.getKey().getFullKey());
        assertEquals(Operation.ADD_NUMBER, modifier.getOperation());
    }

    @Test
    public void testCloning() {
        AttributeModifier modifier = new AttributeModifier(
            ResourceLocation.parse("protocollib:test"),
            1.0D,
            AttributeModifier.Operation.ADD_VALUE
        );

        AttributeSnapshot handle = new AttributeSnapshot(
            Attributes.ATTACK_SPEED,
            5.0D,
            List.of(modifier)
        );

        AttributeSnapshot clone = (AttributeSnapshot) new BukkitCloner().clone(handle);
        assertNotSame(handle, clone);

        assertEquals(handle.base(), clone.base());
        assertEquals(handle.attribute(), clone.attribute());

        assertNotSame(handle.modifiers(), clone.modifiers());

        assertEquals(1, clone.modifiers().size());

        AttributeModifier cloneModifier = clone.modifiers().iterator().next();
        assertSame(modifier, cloneModifier);
    }

    @Test
    public void testEquality() {
        // Check wrapped equality
        assertEquals(this.doubleModifier, this.doubleModifier);
        assertNotSame(this.constantModifier, this.doubleModifier);

        assertEquals(this.doubleModifier.getHandle(), this.getModifierCopy(this.doubleModifier));
        assertEquals(this.constantModifier.getHandle(), this.getModifierCopy(this.constantModifier));
    }

    @Test
    public void testAttribute() {
        assertEquals(this.attribute, WrappedAttribute.fromHandle(this.getAttributeCopy(this.attribute)));

        assertTrue(this.attribute.hasModifier(this.doubleModifier.getUUID()));
        assertTrue(this.attribute.hasModifier(this.constantModifier.getUUID()));
    }

    @Test
    public void testFromTemplate() {
        assertEquals(this.attribute, WrappedAttribute.newBuilder(this.attribute).build());
    }

    /**
     * Retrieve the equivalent NMS attribute.
     *
     * @param attribute - the wrapped attribute.
     * @return The equivalent NMS attribute.
     */
    private AttributeSnapshot getAttributeCopy(WrappedAttribute attribute) {
        List<AttributeModifier> modifiers = new ArrayList<>();

        for (WrappedAttributeModifier wrapper : attribute.getModifiers()) {
            modifiers.add((AttributeModifier) wrapper.getHandle());
        }

        Registry<Attribute> registry = BuiltInRegistries.ATTRIBUTE;
        String attributeKey = attribute.getAttributeKey();
        ResourceLocation key = ResourceLocation.tryParse(attributeKey);
        Attribute base = registry.get(key);
        Holder<Attribute> holder = registry.wrapAsHolder(base);
        return new AttributeSnapshot(holder, attribute.getBaseValue(), modifiers);
    }

    private AttributeModifier getModifierCopy(WrappedAttributeModifier modifier) {
        AttributeModifier.Operation operation = AttributeModifier.Operation.values()[modifier.getOperation().getId()];
        return new AttributeModifier(ResourceLocation.parse(modifier.getKey().getFullKey()),
            modifier.getAmount(), operation);
    }
}
