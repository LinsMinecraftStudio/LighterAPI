/**
 * The MIT License (MIT)
 * <p>
 * Copyright (c) 2021 Vladimir Mikhailov <beykerykt@gmail.com>
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package ru.beykerykt.minecraft.lightapi.bukkit.internal.handler.craftbukkit.nms.v1_17_R1;

import ca.spottedleaf.starlight.light.BlockStarLightEngine;
import ca.spottedleaf.starlight.light.SkyStarLightEngine;
import ca.spottedleaf.starlight.light.StarLightEngine;
import ca.spottedleaf.starlight.light.StarLightInterface;
import net.minecraft.core.BlockPosition;
import net.minecraft.core.SectionPosition;
import net.minecraft.server.level.LightEngineThreaded;
import net.minecraft.server.level.WorldServer;
import net.minecraft.world.level.ChunkCoordIntPair;
import net.minecraft.world.level.EnumSkyBlock;
import net.minecraft.world.level.block.state.BlockBase;
import net.minecraft.world.level.chunk.ILightAccess;
import net.minecraft.world.level.lighting.LightEngineLayerEventListener;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_17_R1.CraftWorld;
import ru.beykerykt.minecraft.lightapi.common.api.ResultCode;
import ru.beykerykt.minecraft.lightapi.common.api.engine.LightType;
import ru.beykerykt.minecraft.lightapi.common.internal.IPlatformImpl;
import ru.beykerykt.minecraft.lightapi.common.internal.engine.LightEngineType;
import ru.beykerykt.minecraft.lightapi.common.internal.utils.FlagUtils;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

public class StarlightNMSHandler extends VanillaNMSHandler {

    private Map<ChunkCoordIntPair, List<LightPos>> blockQueueMap = new HashMap<>();
    private Map<ChunkCoordIntPair, List<LightPos>> skyQueueMap = new HashMap<>();

    // StarLightInterface
    private Field starInterface;
    private Field starInterface_coordinateOffset;
    private Method starInterface_getBlockLightEngine;
    private Method starInterface_getSkyLightEngine;

    // StarLightEngine
    private Method starEngine_setLightLevel;
    private Method starEngine_appendToIncreaseQueue;
    private Method starEngine_appendToDecreaseQueue;
    private Method starEngine_performLightIncrease;
    private Method starEngine_performLightDecrease;
    private Method starEngine_updateVisible;
    private Method starEngine_setupCaches;
    private Method starEngine_destroyCaches;

    private final int ALL_DIRECTIONS_BITSET = (1 << 6) - 1;
    private final long FLAG_HAS_SIDED_TRANSPARENT_BLOCKS = Long.MIN_VALUE;

    private static final class LightPos {
        public BlockPosition blockPos;
        public int lightLevel;

        public LightPos(BlockPosition blockPos, int lightLevel) {
            this.blockPos = blockPos;
            this.lightLevel = lightLevel;
        }
    }

    private void scheduleChunkLight(StarLightInterface starLightInterface, ChunkCoordIntPair chunkCoordIntPair, Runnable runnable) {
        starLightInterface.scheduleChunkLight(chunkCoordIntPair, runnable);
    }

    private void addTaskToQueue(WorldServer worldServer, StarLightInterface starLightInterface, StarLightEngine sle, ChunkCoordIntPair chunkCoordIntPair, List<LightPos> lightPoints) {
        int type = (sle instanceof BlockStarLightEngine) ? LightType.BLOCK_LIGHTING : LightType.SKY_LIGHTING;
        scheduleChunkLight(starLightInterface, chunkCoordIntPair, () -> {
            try {
                int chunkX = chunkCoordIntPair.b;
                int chunkZ = chunkCoordIntPair.c;

                // blocksChangedInChunk -- start
                // setup cache
                starEngine_setupCaches.invoke(sle, worldServer.getChunkProvider(), chunkX * 16 + 7, 128, chunkZ * 16 + 7, true, true);
                try {
                    // propagateBlockChanges -- start
                    Iterator<LightPos> it = lightPoints.iterator();
                    while (it.hasNext()) {
                        try {
                            LightPos lightPos = it.next();
                            BlockPosition blockPos = lightPos.blockPos;
                            int lightLevel = lightPos.lightLevel;
                            int currentLightLevel = getRawLightLevel(worldServer.getWorld(), blockPos.getX(), blockPos.getY(), blockPos.getZ(), type);
                            if (lightLevel <= currentLightLevel) {
                                // do nothing
                                continue;
                            }
                            int encodeOffset = starInterface_coordinateOffset.getInt(sle);
                            BlockBase.BlockData blockData = worldServer.getType(blockPos);
                            starEngine_setLightLevel.invoke(sle, blockPos.getX(), blockPos.getY(), blockPos.getZ(), lightLevel);
                            if (lightLevel != 0) {
                                starEngine_appendToIncreaseQueue.invoke(sle, ((blockPos.getX() + (blockPos.getZ() << 6) + (blockPos.getY() << (6 + 6)) + encodeOffset) & ((1L << (6 + 6 + 16)) - 1))
                                        | (lightLevel & 0xFL) << (6 + 6 + 16)
                                        | (((long) ALL_DIRECTIONS_BITSET) << (6 + 6 + 16 + 4))
                                        | (blockData.isConditionallyFullOpaque() ? FLAG_HAS_SIDED_TRANSPARENT_BLOCKS : 0));
                            }
                        } finally {
                            it.remove();
                        }
                    }
                    starEngine_performLightIncrease.invoke(sle, worldServer.getChunkProvider());
                    // propagateBlockChanges -- end
                    starEngine_updateVisible.invoke(sle, worldServer.getChunkProvider());
                } finally {
                    starEngine_destroyCaches.invoke(sle);
                }
                // blocksChangedInChunk -- end
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });
    }

    @Override
    public void onInitialization(IPlatformImpl impl) throws Exception {
        super.onInitialization(impl);
        try {
            starEngine_setLightLevel = StarLightEngine.class.getDeclaredMethod("setLightLevel", int.class, int.class, int.class, int.class);
            starEngine_setLightLevel.setAccessible(true);
            starEngine_appendToIncreaseQueue = StarLightEngine.class.getDeclaredMethod("appendToIncreaseQueue", long.class);
            starEngine_appendToIncreaseQueue.setAccessible(true);
            starEngine_appendToDecreaseQueue = StarLightEngine.class.getDeclaredMethod("appendToDecreaseQueue", long.class);
            starEngine_appendToDecreaseQueue.setAccessible(true);
            starEngine_performLightIncrease = StarLightEngine.class.getDeclaredMethod("performLightIncrease", ILightAccess.class);
            starEngine_performLightIncrease.setAccessible(true);
            starEngine_performLightDecrease = StarLightEngine.class.getDeclaredMethod("performLightDecrease", ILightAccess.class);
            starEngine_performLightDecrease.setAccessible(true);
            starEngine_updateVisible = StarLightEngine.class.getDeclaredMethod("updateVisible", ILightAccess.class);
            starEngine_updateVisible.setAccessible(true);
            starEngine_setupCaches = StarLightEngine.class.getDeclaredMethod("setupCaches", ILightAccess.class, int.class, int.class, int.class, boolean.class, boolean.class);
            starEngine_setupCaches.setAccessible(true);
            starEngine_destroyCaches = StarLightEngine.class.getDeclaredMethod("destroyCaches");
            starEngine_destroyCaches.setAccessible(true);
            starInterface = LightEngineThreaded.class.getDeclaredField("theLightEngine");
            starInterface.setAccessible(true);
            starInterface_getBlockLightEngine = StarLightInterface.class.getDeclaredMethod("getBlockLightEngine");
            starInterface_getBlockLightEngine.setAccessible(true);
            starInterface_getSkyLightEngine = StarLightInterface.class.getDeclaredMethod("getSkyLightEngine");
            starInterface_getSkyLightEngine.setAccessible(true);
            starInterface_coordinateOffset = StarLightEngine.class.getDeclaredField("coordinateOffset");
            starInterface_coordinateOffset.setAccessible(true);
        } catch (Exception e) {
            throw toRuntimeException(e);
        }
    }

    @Override
    public LightEngineType getLightEngineType() {
        return LightEngineType.STARLIGHT;
    }

    @Override
    public boolean isLightingSupported(World world, int lightFlags) {
        WorldServer worldServer = ((CraftWorld) world).getHandle();
        LightEngineThreaded lightEngine = worldServer.getChunkProvider().getLightEngine();
        if (FlagUtils.isFlagSet(lightFlags, LightType.SKY_LIGHTING)) {
            return lightEngine.a(EnumSkyBlock.a) != null;
        } else if (FlagUtils.isFlagSet(lightFlags, LightType.BLOCK_LIGHTING)) {
            return lightEngine.a(EnumSkyBlock.b) != null;
        }
        return false;
    }

    @Override
    public int setRawLightLevel(World world, int blockX, int blockY, int blockZ, int lightLevel, int flags) {
        WorldServer worldServer = ((CraftWorld) world).getHandle();
        final BlockPosition position = new BlockPosition(blockX, blockY, blockZ);
        final LightEngineThreaded lightEngine = worldServer.getChunkProvider().getLightEngine();
        final int finalLightLevel = lightLevel < 0 ? 0 : lightLevel > 15 ? 15 : lightLevel;

        if (!worldServer.getChunkProvider().isChunkLoaded(blockX >> 4, blockZ >> 4)) {
            return ResultCode.CHUNK_NOT_LOADED;
        }

        executeSync(lightEngine, () -> {
            // block lighting
            if (FlagUtils.isFlagSet(flags, LightType.BLOCK_LIGHTING)) {
                if (isLightingSupported(world, LightType.BLOCK_LIGHTING)) {
                    LightEngineLayerEventListener lele = lightEngine.a(EnumSkyBlock.b);
                    if (finalLightLevel == 0) {
                        try {
                            StarLightInterface starLightInterface = (StarLightInterface) starInterface.get(lightEngine);
                            starLightInterface.blockChange(position);
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                    } else if (lele.a(SectionPosition.a(position)) != null) {
                        try {
                            ChunkCoordIntPair chunkCoordIntPair = new ChunkCoordIntPair(blockX >> 4, blockZ >> 4);
                            if (blockQueueMap.containsKey(chunkCoordIntPair)) {
                                List<LightPos> lightPoints = blockQueueMap.get(chunkCoordIntPair);
                                lightPoints.add(new LightPos(position, finalLightLevel));
                            } else {
                                List<LightPos> lightPoints = new ArrayList<>();
                                lightPoints.add(new LightPos(position, finalLightLevel));
                                blockQueueMap.put(chunkCoordIntPair, lightPoints);
                            }
                        } catch (NullPointerException ignore) {
                            // To prevent problems with the absence of the NibbleArray, even
                            // if leb.a(SectionPosition.a(position)) returns non-null value (corrupted data)
                        }
                    }
                }
            }

            // sky lighting
            if (FlagUtils.isFlagSet(flags, LightType.SKY_LIGHTING)) {
                if (isLightingSupported(world, LightType.SKY_LIGHTING)) {
                    LightEngineLayerEventListener lele = lightEngine.a(EnumSkyBlock.a);
                    if (finalLightLevel == 0) {
                        try {
                            StarLightInterface starLightInterface = (StarLightInterface) starInterface.get(lightEngine);
                            starLightInterface.blockChange(position);
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                    } else if (lele.a(SectionPosition.a(position)) != null) {
                        try {
                            ChunkCoordIntPair chunkCoordIntPair = new ChunkCoordIntPair(blockX >> 4, blockZ >> 4);
                            if (skyQueueMap.containsKey(chunkCoordIntPair)) {
                                List<LightPos> lightPoints = skyQueueMap.get(chunkCoordIntPair);
                                lightPoints.add(new LightPos(position, finalLightLevel));
                            } else {
                                List<LightPos> lightPoints = new ArrayList<>();
                                lightPoints.add(new LightPos(position, finalLightLevel));
                                skyQueueMap.put(chunkCoordIntPair, lightPoints);
                            }
                        } catch (NullPointerException ignore) {
                            // To prevent problems with the absence of the NibbleArray, even
                            // if les.a(SectionPosition.a(position)) returns non-null value (corrupted data)
                        }
                    }
                }
            }
        });
        return ResultCode.SUCCESS;
    }

    @Override
    public int recalculateLighting(World world, int blockX, int blockY, int blockZ, int flags) {
        WorldServer worldServer = ((CraftWorld) world).getHandle();
        final LightEngineThreaded lightEngine = worldServer.getChunkProvider().getLightEngine();

        if (!worldServer.getChunkProvider().isChunkLoaded(blockX >> 4, blockZ >> 4)) {
            return ResultCode.CHUNK_NOT_LOADED;
        }

        try {
            StarLightInterface starLightInterface = (StarLightInterface) starInterface.get(lightEngine);
            Iterator blockIt = blockQueueMap.entrySet().iterator();
            while (blockIt.hasNext()) {
                BlockStarLightEngine bsle = (BlockStarLightEngine) starInterface_getBlockLightEngine.invoke(starLightInterface);
                Map.Entry<ChunkCoordIntPair, List<LightPos>> pair = (Map.Entry<ChunkCoordIntPair, List<LightPos>>) blockIt.next();
                ChunkCoordIntPair chunkCoordIntPair = pair.getKey();
                List<LightPos> lightPoints = pair.getValue();
                addTaskToQueue(worldServer, starLightInterface, bsle, chunkCoordIntPair, lightPoints);
                blockIt.remove();
            }

            Iterator skyIt = skyQueueMap.entrySet().iterator();
            while (skyIt.hasNext()) {
                SkyStarLightEngine ssle = (SkyStarLightEngine) starInterface_getSkyLightEngine.invoke(starLightInterface);
                Map.Entry<ChunkCoordIntPair, List<LightPos>> pair = (Map.Entry<ChunkCoordIntPair, List<LightPos>>) skyIt.next();
                ChunkCoordIntPair chunkCoordIntPair = pair.getKey();
                List<LightPos> lightPoints = pair.getValue();
                addTaskToQueue(worldServer, starLightInterface, ssle, chunkCoordIntPair, lightPoints);
                skyIt.remove();
            }
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }

        // Do not recalculate if no changes!
        if (!lightEngine.z_()) {
            return ResultCode.RECALCULATE_NO_CHANGES;
        }

        executeSync(lightEngine, () -> {
            try {
                StarLightInterface starLightInterface = (StarLightInterface) starInterface.get(lightEngine);
                starLightInterface.propagateChanges();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });
        return ResultCode.SUCCESS;
    }
}