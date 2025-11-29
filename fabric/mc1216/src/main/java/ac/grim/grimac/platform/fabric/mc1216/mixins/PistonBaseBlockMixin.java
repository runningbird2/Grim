package ac.grim.grimac.platform.fabric.mc1216.mixins;

import ac.grim.grimac.GrimAPI;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.collisions.datatypes.SimpleCollisionBox;
import ac.grim.grimac.utils.data.PistonData;
import com.github.retrooper.packetevents.protocol.world.BlockFace;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.util.Vector3i;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.ArrayList;
import java.util.List;

@Mixin(PistonBaseBlock.class)
public class PistonBaseBlockMixin {

    private static final double MAX_HORIZONTAL_DISTANCE = 24.0;
    private static final double MAX_VERTICAL_DISTANCE = 64.0;

    @Redirect(method = "moveBlocks", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/piston/PistonStructureResolver;resolve()Z"))
    private boolean grimac$handlePistonEvent(PistonStructureResolver resolver, Level level, BlockPos pistonPos, Direction direction, boolean extending) {
        boolean resolved = resolver.resolve();
        if (resolved) {
            handlePiston(resolver, level, pistonPos, direction, extending);
        }
        return resolved;
    }

    private void handlePiston(PistonStructureResolver resolver, Level level, BlockPos pistonPos, Direction direction, boolean extending) {
        boolean hasSlimeBlock = false;
        boolean hasHoneyBlock = false;

        List<SimpleCollisionBox> boxes = new ArrayList<>();
        List<BlockPos> movedBlocks = resolver.getToPush();

        for (BlockPos blockPos : movedBlocks) {
            boxes.add(new SimpleCollisionBox(0, 0, 0, 1, 1, 1, true)
                    .offset(blockPos.getX(), blockPos.getY(), blockPos.getZ()));
            boxes.add(new SimpleCollisionBox(0, 0, 0, 1, 1, 1, true)
                    .offset(blockPos.getX() + direction.getStepX(),
                            blockPos.getY() + direction.getStepY(),
                            blockPos.getZ() + direction.getStepZ()));

            var state = level.getBlockState(blockPos);
            if (state.is(Blocks.SLIME_BLOCK)) {
                hasSlimeBlock = true;
            }
            if (state.is(Blocks.HONEY_BLOCK)) {
                hasHoneyBlock = true;
            }
        }

        // Add the piston head bounding box for pushes, or for retracts with no blocks being moved
        if (extending || movedBlocks.isEmpty()) {
            BlockPos headPos = pistonPos.relative(direction);
            boxes.add(new SimpleCollisionBox(0, 0, 0, 1, 1, 1, true)
                    .offset(headPos.getX(), headPos.getY(), headPos.getZ()));
        }

        final int chunkX = pistonPos.getX() >> 4;
        final int chunkZ = pistonPos.getZ() >> 4;
        final Vector3i sourcePos = new Vector3i(pistonPos.getX(), pistonPos.getY(), pistonPos.getZ());
        final BlockFace blockFace = fromDirection(direction);

        for (GrimPlayer player : GrimAPI.INSTANCE.getPlayerDataManager().getEntries()) {
            Vector3d playerPos = player.compensatedEntities.self.trackedServerPosition.getPos();
            if (isCloseEnough(sourcePos, playerPos) && player.compensatedWorld.isChunkLoaded(chunkX, chunkZ)) {
                int lastTrans = player.lastTransactionSent.get();
                PistonData data = new PistonData(blockFace, boxes, lastTrans, extending, hasSlimeBlock, hasHoneyBlock);
                player.latencyUtils.addRealTimeTaskAsync(lastTrans, () -> player.compensatedWorld.activePistons.add(data));
            }
        }
    }

    private static boolean isCloseEnough(Vector3i vectorA, Vector3d vectorB) {
        return Math.abs(vectorA.getX() - vectorB.getX()) <= MAX_HORIZONTAL_DISTANCE
                && Math.abs(vectorA.getY() - vectorB.getY()) <= MAX_VERTICAL_DISTANCE
                && Math.abs(vectorA.getZ() - vectorB.getZ()) <= MAX_HORIZONTAL_DISTANCE;
    }

    private static BlockFace fromDirection(Direction direction) {
        return switch (direction) {
            case NORTH -> BlockFace.NORTH;
            case SOUTH -> BlockFace.SOUTH;
            case WEST -> BlockFace.WEST;
            case EAST -> BlockFace.EAST;
            case UP -> BlockFace.UP;
            case DOWN -> BlockFace.DOWN;
        };
    }
}
