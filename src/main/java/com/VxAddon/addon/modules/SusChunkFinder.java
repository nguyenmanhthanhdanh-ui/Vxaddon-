
package com.vxaddon.addon.modules;

import com.vxaddon.addon.VxAddon;

import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.orbit.EventHandler;

import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.network.chat.Component;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;

import java.util.*;

public class SusChunkFinder extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgDetect = settings.createGroup("Detection");
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Integer> distance = sgGeneral.add(
        new IntSetting.Builder()
            .name("distance")
            .defaultValue(5)
            .min(1)
            .max(15)
            .build()
    );

    private final Setting<Integer> scanSpeed = sgGeneral.add(
        new IntSetting.Builder()
            .name("scan-speed")
            .description("Ticks between scan batches. Lower is faster.")
            .defaultValue(5)
            .min(1)
            .max(20)
            .build()
    );

    private final Setting<Integer> sensitivity = sgGeneral.add(
        new IntSetting.Builder()
            .name("sensitivity")
            .defaultValue(4)
            .min(1)
            .max(20)
            .build()
    );

    private final Setting<Boolean> amethyst = sgDetect.add(
        new BoolSetting.Builder()
            .name("amethyst")
            .description("Detects fully grown Amethyst Clusters only.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> beeNest = sgDetect.add(
        new BoolSetting.Builder()
            .name("bee-nest")
            .defaultValue(false)
            .build()
    );

    private final Setting<Boolean> kelp = sgDetect.add(
        new BoolSetting.Builder()
            .name("kelp")
            .defaultValue(false)
            .build()
    );

    private final Setting<Boolean> vine = sgDetect.add(
        new BoolSetting.Builder()
            .name("vine")
            .description("Detects vertical Vine chains at least 3 blocks long.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> storage = sgDetect.add(
        new BoolSetting.Builder()
            .name("storage")
            .defaultValue(false)
            .build()
    );

    private final Setting<Boolean> chatFeedback = sgGeneral.add(
        new BoolSetting.Builder()
            .name("chat-feedback")
            .defaultValue(false)
            .build()
    );

    private final Setting<Boolean> showChunk = sgRender.add(
        new BoolSetting.Builder()
            .name("show-chunk")
            .defaultValue(true)
            .build()
    );

    private final Setting<Integer> showChunkHeight = sgRender.add(
        new IntSetting.Builder()
            .name("show-chunk-height")
            .description("Height of every highlighted chunk plane.")
            .defaultValue(64)
            .min(-64)
            .max(320)
            .sliderMin(-64)
            .sliderMax(320)
            .build()
    );

    private final Setting<Boolean> tracers = sgRender.add(
        new BoolSetting.Builder()
            .name("tracers")
            .defaultValue(false)
            .build()
    );

    private final Setting<Integer> opacity = sgRender.add(
        new IntSetting.Builder()
            .name("opacity")
            .description("Controls the transparency of the chunk highlight.")
            .defaultValue(60)
            .min(0)
            .max(255)
            .sliderMax(255)
            .build()
    );

    private final Setting<SettingColor> color = sgRender.add(
        new ColorSetting.Builder()
            .name("color")
            .defaultValue(new SettingColor(255, 25, 25, 255))
            .build()
    );

    private static final int BLOCKS_PER_BATCH = 4096;
    private static final int MIN_VINE_LENGTH = 3;

    private final Map<ChunkPos, ChunkResult> cache = new HashMap<>();
    private final Set<ChunkPos> scannedChunks = new HashSet<>();
    private final Queue<ChunkScan> scanQueue = new ArrayDeque<>();

    private ChunkScan currentScan;
    private ChunkPos lastPlayerChunk;
    private ClientLevel cachedLevel;

    private int tickCounter;
    private int refreshCounter;

    private int minScanY;
    private int maxScanY;

    public SusChunkFinder() {
        super(
            VxAddon.CATEGORY,
            "sus-chunk-finder",
            "Highlights loaded chunks with unusual block patterns."
        );
    }

    @Override
    public void onActivate() {
        scanQueue.clear();
        currentScan = null;
        lastPlayerChunk = null;
        tickCounter = 0;
    }

    @Override
    public void onDeactivate() {
        scanQueue.clear();
        currentScan = null;
        lastPlayerChunk = null;
    }

    private void clearCache() {
        cache.clear();
        scannedChunks.clear();
        scanQueue.clear();
        currentScan = null;
        lastPlayerChunk = null;
    }

    private void buildQueue() {
        scanQueue.clear();
        currentScan = null;

        if (mc.player == null || mc.level == null) return;

        ChunkPos center = mc.player.chunkPosition();
        lastPlayerChunk = center;

        int radius = distance.get();

        minScanY = Math.max(-60, mc.level.getMinY());
        maxScanY = mc.level.getMaxY() - 1;

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {

                ChunkPos pos = new ChunkPos(
                    center.x + x,
                    center.z + z
                );

                if (scannedChunks.contains(pos)) continue;

                scanQueue.add(new ChunkScan(pos.x, pos.z));
            }
        }
    }

    private boolean isMatureVine(LevelChunk chunk, BlockPos pos) {
        int length = 1;

        // Kiểm tra Vine liên tục phía trên.
        for (int y = 1; y < MIN_VINE_LENGTH; y++) {
            BlockPos check = pos.above(y);

            if (check.getY() > maxScanY) break;

            if (chunk.getBlockState(check).getBlock() != Blocks.VINE) {
                break;
            }

            length++;
        }

        // Nếu chưa đủ, kiểm tra tiếp phía dưới.
        if (length < MIN_VINE_LENGTH) {
            for (int y = 1; y < MIN_VINE_LENGTH; y++) {
                BlockPos check = pos.below(y);

                if (check.getY() < minScanY) break;

                if (chunk.getBlockState(check).getBlock() != Blocks.VINE) {
                    break;
                }

                length++;
            }
        }

        return length >= MIN_VINE_LENGTH;
    }

    private String getTargetName(
        Block block,
        LevelChunk chunk,
        BlockPos pos
    ) {
        // Chỉ nhận tinh thể Amethyst đã mọc hoàn chỉnh.
        if (amethyst.get() && block == Blocks.AMETHYST_CLUSTER) {
            return "Amethyst";
        }

        if (beeNest.get() &&
            (block == Blocks.BEE_NEST ||
             block == Blocks.BEEHIVE)) {
            return "Bee Nest";
        }

        if (kelp.get() &&
            (block == Blocks.KELP ||
             block == Blocks.KELP_PLANT)) {
            return "Kelp";
        }

        // Chỉ nhận Vine thuộc chuỗi mọc dài ít nhất 3 block.
        if (vine.get() &&
            block == Blocks.VINE &&
            isMatureVine(chunk, pos)) {
            return "Vine";
        }

        if (storage.get() &&
            (block == Blocks.CHEST ||
             block == Blocks.TRAPPED_CHEST ||
             block == Blocks.BARREL ||
             block == Blocks.ENDER_CHEST ||
             block instanceof ShulkerBoxBlock)) {
            return "Storage";
        }

        return null;
    }

    private void sendFeedback(String name, BlockPos pos) {
        if (!chatFeedback.get() || mc.player == null) return;

        mc.player.displayClientMessage(
            Component.literal(
                "[VX SusChunkFinder]\n" +
                "Phát hiện: " + name + "\n" +
                "Tọa độ: X: " + pos.getX() +
                ", Y: " + pos.getY() +
                ", Z: " + pos.getZ()
            ),
            false
        );
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.level == null || mc.player == null) return;

        if (cachedLevel != mc.level) {
            cachedLevel = mc.level;
            clearCache();
        }

        ChunkPos playerChunk = mc.player.chunkPosition();

        if (lastPlayerChunk == null ||
            !lastPlayerChunk.equals(playerChunk)) {
            buildQueue();
        }

        tickCounter++;

        if (tickCounter < scanSpeed.get()) return;

        tickCounter = 0;

        refreshCounter++;

        if (refreshCounter >= 40) {
            refreshCounter = 0;

            if (currentScan == null && scanQueue.isEmpty()) {
                buildQueue();
            }
        }

        int budget = BLOCKS_PER_BATCH;

        while (budget > 0) {

            if (currentScan == null) {
                currentScan = scanQueue.poll();

                if (currentScan == null) return;

                LevelChunk chunk = mc.level.getChunkSource()
                    .getChunk(
                        currentScan.x,
                        currentScan.z,
                        false
                    );

                if (chunk == null) {
                    currentScan = null;
                    continue;
                }

                currentScan.chunk = chunk;
            }

            int totalBlocks = 16 * 16 *
                (maxScanY - minScanY + 1);

            BlockPos.MutableBlockPos pos =
                new BlockPos.MutableBlockPos();

            while (budget > 0 &&
                   currentScan.index < totalBlocks) {

                int index = currentScan.index++;

                int x = index % 16;
                int z = (index / 16) % 16;
                int y = index / 256;

                pos.set(
                    currentScan.x * 16 + x,
                    minScanY + y,
                    currentScan.z * 16 + z
                );

                Block block = currentScan.chunk
                    .getBlockState(pos)
                    .getBlock();

                String targetName = getTargetName(
                    block,
                    currentScan.chunk,
                    pos
                );

                if (targetName != null) {
                    currentScan.score++;

                    if (currentScan.firstName == null) {
                        currentScan.firstName = targetName;
                        currentScan.firstPos = pos.immutable();
                    }
                }

                budget--;
            }

            if (currentScan.index >= totalBlocks) {

                ChunkPos finished = new ChunkPos(
                    currentScan.x,
                    currentScan.z
                );

                scannedChunks.add(finished);

                boolean suspicious =
                    currentScan.score >= sensitivity.get();

                if (suspicious) {
                    ChunkResult result = new ChunkResult(
                        currentScan.score,
                        currentScan.firstName,
                        currentScan.firstPos
                    );

                    cache.put(finished, result);

                    if (result.firstName != null &&
                        result.firstPos != null) {
                        sendFeedback(
                            result.firstName,
                            result.firstPos
                        );
                    }
                } else {
                    cache.put(
                        finished,
                        new ChunkResult(
                            currentScan.score,
                            null,
                            null
                        )
                    );
                }

                currentScan = null;
            }
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.level == null || mc.player == null) return;

        SettingColor base = color.get();

        SettingColor c = new SettingColor(
            base.r,
            base.g,
            base.b,
            opacity.get()
        );

        Vec3 cameraPos = mc.gameRenderer
            .getMainCamera()
            .getPosition();

        // Tất cả chunk dùng chung độ cao này.
        double y = showChunkHeight.get() + 0.02;

        for (Map.Entry<ChunkPos, ChunkResult> entry :
            cache.entrySet()) {

            ChunkPos pos = entry.getKey();
            ChunkResult result = entry.getValue();

            if (result == null || result.firstPos == null) continue;

            if (result.score < sensitivity.get()) continue;

            if (!mc.level.hasChunk(pos.x, pos.z)) continue;

            double x1 = pos.getMinBlockX();
            double z1 = pos.getMinBlockZ();

            double x2 = x1 + 16;
            double z2 = z1 + 16;

            if (showChunk.get()) {
                event.renderer.box(
                    x1, y, z1,
                    x2, y + 0.02, z2,
                    c, c,
                    ShapeMode.Lines,
                    0
                );
            }

            if (tracers.get()) {
                event.renderer.line(
                    cameraPos.x,
                    cameraPos.y,
                    cameraPos.z,
                    x1 + 8,
                    y,
                    z1 + 8,
                    c
                );
            }
        }
    }

    private static class ChunkScan {
        private final int x;
        private final int z;

        private int index;
        private int score;

        private String firstName;
        private BlockPos firstPos;

        private LevelChunk chunk;

        private ChunkScan(int x, int z) {
            this.x = x;
            this.z = z;
        }
    }

    private static class ChunkResult {
        private final int score;
        private final String firstName;
        private final BlockPos firstPos;

        private ChunkResult(
            int score,
            String firstName,
            BlockPos firstPos
        ) {
            this.score = score;
            this.firstName = firstName;
            this.firstPos = firstPos;
        }
    }
    }
            
