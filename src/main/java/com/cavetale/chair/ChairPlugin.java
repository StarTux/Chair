package com.cavetale.chair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;
import org.spigotmc.event.entity.EntityDismountEvent;

public final class ChairPlugin extends JavaPlugin implements Listener {
    Map<Block, Chair> blockMap = new HashMap<>();
    Map<UUID, Chair> uuidMap = new HashMap<>();

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        for (Chair chair : new ArrayList<>(blockMap.values())) {
            disableChair(chair);
        }
    }

    @RequiredArgsConstructor
    static final class Chair {
        final Block block;
        final ArmorStand armorStand;
    }

    boolean isOccupied(Block block) {
        Chair chair = blockMap.get(block);
        if (chair == null) return false;
        if (chair.armorStand.isValid()) return true;
        disableChair(chair);
        return false;
    }

    void disableChair(Chair chair) {
        blockMap.remove(chair.block);
        uuidMap.remove(chair.armorStand.getUniqueId());
        chair.armorStand.remove();
    }

    void enableChair(Chair chair) {
        blockMap.put(chair.block, chair);
        uuidMap.put(chair.armorStand.getUniqueId(), chair);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        if (player.isSneaking()) return;
        if (player.getGameMode() == GameMode.SPECTATOR) return;
        if (player.getVehicle() != null) return;
        if (!player.isOnGround()) return;
        if (!player.hasPermission("chair.use")) return;
        Block block = event.getClickedBlock();
        if (isOccupied(block)) return;
        if (!Tag.STAIRS.isTagged(block.getType())) return;
        Stairs stairs = (Stairs) block.getBlockData();
        if (stairs.getHalf() != Bisected.Half.BOTTOM) return;
        if (stairs.getShape() != Stairs.Shape.STRAIGHT) return;
        if (!block.getRelative(BlockFace.UP, 1).isPassable()) return;
        if (!block.getRelative(BlockFace.UP, 2).isPassable()) return;
        Location ploc = player.getLocation();
        if (ploc.getBlockY() != block.getY()) return;
        BlockFace face = stairs.getFacing().getOppositeFace();
        if (!block.getRelative(face).isPassable()) return;
        Location loc = block.getLocation().add(0.5, 0.3, 0.5);
        if (ploc.distanceSquared(loc) > 4.0) return;
        event.setCancelled(true); // No return
        Vector dir = face.getDirection();
        loc = loc.setDirection(face.getDirection());
        loc = loc.add(dir.normalize().multiply(0.2));
        ArmorStand armorStand = loc.getWorld().spawn(loc, ArmorStand.class, as -> {
                as.setPersistent(false);
                as.setVisible(false);
                as.setMarker(true);
            });
        player.teleport(loc);
        armorStand.addPassenger(player);
        Chair chair = new Chair(block, armorStand);
        enableChair(chair);
        // Feedback
        loc.getWorld().playSound(loc, block.getSoundGroup().getHitSound(), 1.0f, 1.0f);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (!(player.getVehicle() instanceof ArmorStand)) return;
        ArmorStand armorStand = (ArmorStand) player.getVehicle();
        Chair chair = uuidMap.get(armorStand.getUniqueId());
        if (chair == null) return;
        disableChair(chair);
    }

    @EventHandler
    public void onEntityDismount(EntityDismountEvent event) {
        if (!(event.getDismounted() instanceof ArmorStand)) return;
        ArmorStand armorStand = (ArmorStand) event.getDismounted();
        Chair chair = uuidMap.get(armorStand.getUniqueId());
        if (chair == null) return;
        disableChair(chair);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBlockBreak(BlockBreakEvent event) {
        Chair chair = blockMap.get(event.getBlock());
        if (chair == null) return;
        disableChair(chair);
    }
}
