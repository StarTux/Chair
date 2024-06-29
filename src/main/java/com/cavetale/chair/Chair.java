package com.cavetale.chair;

import lombok.Value;
import org.bukkit.entity.ArmorStand;
import org.bukkit.util.Vector;

@Value
public final class Chair {
    protected final BlockVector blockVector;
    protected final ArmorStand armorStand;
    protected final Vector direction;
}
