package com.cavetale.chair;

import lombok.Value;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;

@Value
public final class BlockVector {
    protected final String world;
    protected final int x;
    protected final int y;
    protected final int z;

    public static BlockVector toBlockVector(Block block) {
        return new BlockVector(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
    }

    public Block toBlock() {
        World w = Bukkit.getWorld(world);
        return w != null
            ? w.getBlockAt(x, y, z)
            : null;
    }
}
