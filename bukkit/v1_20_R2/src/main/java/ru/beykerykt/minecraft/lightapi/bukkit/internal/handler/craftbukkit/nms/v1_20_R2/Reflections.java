package ru.beykerykt.minecraft.lightapi.bukkit.internal.handler.craftbukkit.nms.v1_20_R2;

import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.util.thread.ProcessorMailbox;
import net.minecraft.world.level.lighting.DynamicGraphMinFixedPoint;
import net.minecraft.world.level.lighting.LightEngine;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class Reflections {
    public static final Field LIGHT_ENGINE_STORAGE;
    public static final Field LIGHT_ENGINE_CHUNK_SOURCE;
    public static final Field TASK_MAILBOX;
    public static final Field TASK_MAILBOX_STATUS;
    public static final Method REGISTER_FOR_EXECUTION;
    public static final Method PROPAGATE_INCREASE;
    public static final Method CHECK_NODE;

    static {
        try {
            LIGHT_ENGINE_STORAGE = LightEngine.class.getDeclaredField("f");
            LIGHT_ENGINE_STORAGE.setAccessible(true);

            LIGHT_ENGINE_CHUNK_SOURCE = LightEngine.class.getDeclaredField("e");
            LIGHT_ENGINE_CHUNK_SOURCE.setAccessible(true);

            TASK_MAILBOX = ThreadedLevelLightEngine.class.getDeclaredField("e");
            TASK_MAILBOX.setAccessible(true);

            TASK_MAILBOX_STATUS = ProcessorMailbox.class.getDeclaredField("d");
            TASK_MAILBOX_STATUS.setAccessible(true);

            REGISTER_FOR_EXECUTION = ProcessorMailbox.class.getDeclaredMethod("i");
            REGISTER_FOR_EXECUTION.setAccessible(true);

            CHECK_NODE = LightEngine.class.getDeclaredMethod("a", long.class);
            CHECK_NODE.setAccessible(true);

            PROPAGATE_INCREASE = LightEngine.class.getDeclaredMethod("a", long.class, long.class, int.class);
            PROPAGATE_INCREASE.setAccessible(true);
        } catch (NoSuchFieldException | NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }
}
