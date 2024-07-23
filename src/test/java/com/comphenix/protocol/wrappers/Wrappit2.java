package com.comphenix.protocol.wrappers;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.comphenix.protocol.BukkitInitialization;
import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.reflect.EquivalentConverter;
import com.comphenix.protocol.reflect.FuzzyReflection;
import com.comphenix.protocol.reflect.StructureModifier;
import com.comphenix.protocol.reflect.accessors.Accessors;
import com.comphenix.protocol.reflect.accessors.FieldAccessor;
import com.comphenix.protocol.reflect.fuzzy.FuzzyFieldContract;
import com.comphenix.protocol.reflect.fuzzy.FuzzyMethodContract;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class Wrappit2 {
    @BeforeAll
    public static void beforeAll() {
        BukkitInitialization.initializeAll();
        EnumWrappers.getChatFormattingClass();
    }

    private record Wrapper(Class<?> wrapperClass, String methodName) {
    }

    private Map<Class<?>, Wrapper> buildAvailableWrappers(PacketContainer container) {
        Map<Class<?>, Wrapper> wrappers = new HashMap<>();

        FuzzyReflection fuzzy = FuzzyReflection.fromClass(container.getClass(), true);
        List<Method> methods = fuzzy.getMethodList(FuzzyMethodContract.newBuilder()
            .returnDerivedOf(StructureModifier.class)
            .parameterCount(0)
            .requirePublic()
            .build());
        for (Method method : methods) {
            try {
                StructureModifier<?> modifier = (StructureModifier<?>) Accessors.getMethodAccessor(method).invoke(container);
                Class<?> nmsClass = modifier.getFieldType();
                if (nmsClass == null) {
                    throw new NullPointerException("Field type is null");
                }
                EquivalentConverter<?> converter = modifier.needConversion() ? modifier.getConverter() : null;
                Class<?> wrapperClass = converter != null ? converter.getSpecificType() : nmsClass;
                if (wrappers.containsKey(nmsClass)) {
                    System.err.println("Trying to redefine wrapper for " + nmsClass.getName());
                    continue;
                }
                wrappers.put(nmsClass, new Wrapper(wrapperClass, method.getName()));
            } catch (Throwable ex) {
                System.err.println("Failed to generate wrapper for method " + method.getName());
                ex.printStackTrace();
            }
        }

        return wrappers;
    }

    private record ValidModifier(Method getModifier, StructureModifier<?> modifier, int index,
                                 Class<?> wrapperClass) {
    }

    private void generateWrapper(PacketType packetType) {
        Class<?> packetClass = packetType.getPacketClass();
        String wrapperPackage = "net.dmulloy2.protocol.wrappers."
            + packetClass.getPackageName().replace("net.minecraft.network.protocol.", "")
            + "." + packetType.getSender().getMojangName().toLowerCase();

        StringBuilder builder = new StringBuilder();
        builder.append("package ").append(wrapperPackage).append(";\n\n");
        builder.append("import com.comphenix.protocol.events.*;\n");
        builder.append("import com.comphenix.protocol.wrappers.*;\n");
        builder.append("import com.comphenix.protocol.wrappers.EnumWrappers.*;\n");
        builder.append("import org.bukkit.*;\n");
        builder.append("import net.dmulloy2.protocol.wrappers.PacketWrapperBase;\n");
        builder.append("import java.lang.*;\n");
        builder.append("import java.util.*;\n");
        builder.append("import ").append(packetClass.getPackageName()).append(";\n");
        builder.append("\n");

        String wrapperClassName = packetClass.getSimpleName().replace("Packet", "") + "Wrapper";

        builder.append("public class ").append(wrapperClassName).append(" extends PacketWrapperBase {\n");

        builder.append("\n").append("    private ").append(packetClass.getSimpleName()).append(" p;\n\n");

        builder.append("    public ").append(wrapperClassName).append("(PacketContainer container) {\n");
        builder.append("        super(container);\n");
        builder.append("    }\n\n");

        PacketContainer container = new PacketContainer(packetType);
        FuzzyReflection packetFuzzy = FuzzyReflection.fromClass(packetClass, true);
        List<Field> packetFields = packetFuzzy.getFieldList(FuzzyFieldContract.newBuilder()
            .requireModifier(Modifier.PRIVATE)
            .requireModifier(Modifier.FINAL)
            .banModifier(Modifier.STATIC)
            .build());

        FuzzyReflection containerFuzzy = FuzzyReflection.fromObject(container, true);
        List<Method> availableModifiers = containerFuzzy.getMethodList(FuzzyMethodContract.newBuilder()
            .returnDerivedOf(StructureModifier.class)
            .parameterCount(0)
            .requirePublic()
            .build());

        Map<Method, Integer> invocationCount = new HashMap<>();

        for (Field packetField : packetFields) {
            List<ValidModifier> candidates = availableModifiers.stream().map(getModifier -> {
                if (getModifier.getName().equals("getModifier") || getModifier.getName().equals("getStructures")) {
                    return null; // too broad, not useful here
                }
                try {
                    StructureModifier<?> modifier = (StructureModifier<?>) Accessors.getMethodAccessor(getModifier).invoke(container);
                    Class<?> nmsClass = modifier.getFieldType();
                    if (nmsClass == null) {
                        return null;
                    }
                    if (modifier.getFields().stream()
                        .map(FieldAccessor::getField)
                        .noneMatch(f -> f.equals(packetField))) {
                        return null;
                    }

                    int index = invocationCount.computeIfAbsent(getModifier, __ -> 0);
                    modifier.read(index);
                    invocationCount.put(getModifier, index + 1);

                    Class<?> wrapperClass = modifier.needConversion() ? modifier.getConverter().getSpecificType() : nmsClass;
                    return new ValidModifier(getModifier, modifier, index, wrapperClass);
                } catch (Exception ignored) {
                    return null;
                }
            }).filter(Objects::nonNull).toList();

            if (candidates.size() > 1) {
                System.err.println("Failed to disambiguate between modifiers for field " + packetField.getName());
                builder.append("    // TODO -- multiple modifiers for ").append(packetField.getName()).append("\n\n");
            } else if (candidates.isEmpty()) {
                System.err.println("No modifiers available to service field " + packetField.getName());
                builder.append("    // TODO -- no modifiers for ").append(packetField.getName()).append("\n\n");
            } else {
                ValidModifier modifier = candidates.get(0);

                String fieldName = packetField.getName();

                String getPrefix = "get";
                String setPrefix = "set";
                if (packetField.getType() == boolean.class) {
                    if (fieldName.startsWith("is")) {
                        getPrefix = "";
                    } else {
                        getPrefix = "is";
                    }
                }

                String name = fieldName;
                if (!getPrefix.equals("")) {
                    name = fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);
                }

                builder.append("    public ").append(modifier.wrapperClass.getSimpleName()).append(" ").append(getPrefix).append(name).append("() {\n");
                builder.append("        return container.").append(modifier.getModifier.getName()).append("().read(").append(modifier.index).append(");\n");
                builder.append("    }\n\n");

                builder.append("    public void ").append(setPrefix).append(name).append("(").append(modifier.wrapperClass.getSimpleName()).append(" value) {\n");
                builder.append("        container.").append(modifier.getModifier.getName()).append("().write(").append(modifier.index).append(", value);\n");
                builder.append("    }\n\n");
            }
        }

        /*
        Map<Class<?>, Wrapper> wrappers = buildAvailableWrappers(container);

        FuzzyReflection fuzzy = FuzzyReflection.fromClass(packetClass, true);
        List<Method> getters = fuzzy.getMethodList(FuzzyMethodContract.newBuilder()
            .parameterCount(0)
            .requirePublic()
            .build());
        for (Method getter : getters) {
            if (getter.getName().equals("toString")
                || getter.getName().equals("hashCode")
                || getter.getName().equals("equals")
                || getter.getName().equals("isTerminal")
                || getter.getName().equals("isSkippable")
                || getter.getName().equals("type")) {
                continue;
            }

            try {
                Class<?> returnType = getter.getReturnType();
                Wrapper wrapper = wrappers.get(returnType);
                if (wrapper == null) {
                    System.err.println("No wrapper found for " + returnType.getName());
                    continue;
                }

                builder.append("    public ").append(wrapper.wrapperClass.getSimpleName()).append(" ").append(getter.getName()).append("() {\n");
                builder.append("        return container.").append(wrapper.methodName).append("();\n");
                builder.append("    }\n\n");
            } catch (Throwable ex) {
                System.err.println("Failed to generate getter for method " + getter.getName());
                ex.printStackTrace();
            }
        }
         */

        builder.append("}\n");
        String classDef = builder.toString();

        File dir = new File(".\\src\\test\\java\\" + wrapperPackage.replace(".", "\\"));
        dir.mkdirs();
        File file = new File(dir, wrapperClassName + ".java");
        String filePath = file.getAbsolutePath();

        try (var writer = new java.io.FileWriter(filePath, StandardCharsets.UTF_8)) {
            writer.write(classDef);
        } catch (java.io.IOException ex) {
            ex.printStackTrace();
        }
    }

    @Test
    public void generateTestWrapper() {
        for (PacketType type : PacketType.values()) {
            try {
                if (!type.isSupported() || type.isDeprecated()) {
                    continue;
                }

                generateWrapper(type);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }
}
