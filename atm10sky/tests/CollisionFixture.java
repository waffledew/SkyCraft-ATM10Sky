package dev.skycraft.test;

import dev.skycraft.world.SkyCollision;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.UUID;
import net.minecraft.core.BlockPos;

public final class CollisionFixture {
    public static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000123");
    public static String seed() {
        ByteBuffer data = ByteBuffer.allocate(81 * 76).order(ByteOrder.LITTLE_ENDIAN);
        for (int x = 100000; x < 100009; x++) {
            for (int z = 100000; z < 100009; z++) {
                data.putInt(x).putInt(100).putInt(z);
                for (int layer = 0; layer < 8; layer++) data.putLong(layer == 7 ? -1L : 0L);
            }
        }
        SkyCollision.applyRemote(OWNER, new BlockPos(100004, 101, 100004), 24, 24, data.array());
        var ground = SkyCollision.shapeAt(new BlockPos(100004, 100, 100004));
        if (ground == null || ground.isEmpty()) throw new IllegalStateException("Test floor was not seeded");
        return ground.toString();
    }
}
