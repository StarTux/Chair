package com.cavetale.chair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;
import static com.cavetale.chair.BlockVector.toBlockVector;

public final class ChairPlugin extends JavaPlugin implements Listener {
    private final Map<BlockVector, Chair> blockMap = new HashMap<>();
    private final Map<UUID, Chair> uuidMap = new HashMap<>();

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        for (Chair chair : new ArrayList<>(blockMap.values())) {
            disableChair(chair);
            teleportOut(chair);
        }
    }

    private boolean isOccupied(Block block) {
        final Chair chair = blockMap.get(toBlockVector(block));
        if (chair == null) return false;
        if (chair.armorStand.isValid() && !chair.armorStand.getPassengers().isEmpty()) {
            return true;
        }
        disableChair(chair);
        return false;
    }

    private void disableChair(Chair chair) {
        blockMap.remove(chair.blockVector);
        uuidMap.remove(chair.armorStand.getUniqueId());
        chair.armorStand.remove();
    }

    private void enableChair(Chair chair) {
        blockMap.put(chair.blockVector, chair);
        uuidMap.put(chair.armorStand.getUniqueId(), chair);
    }

    private void teleportOut(Chair chair) {
        final Player player = Bukkit.getPlayer(chair.playerUuid);
        if (player == null) return;
        final Location ploc = player.getLocation();
        final Block block = chair.blockVector.toBlock();
        final Location loc = block.getLocation().add(0.5, 0.5, 0.5);
        if (!ploc.getWorld().equals(loc.getWorld())) return;
        if (ploc.distanceSquared(loc) >= 1.0) return;
        loc.setDirection(chair.direction);
        player.teleport(loc);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    private void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.isBlockInHand()) return;
        final Player player = event.getPlayer();
        if (player.isSneaking()) return;
        if (player.getGameMode() == GameMode.SPECTATOR) return;
        if (player.getVehicle() != null) return;
        if (!((Entity) player).isOnGround()) return;
        if (!player.hasPermission("chair.use")) return;
        final Block block = event.getClickedBlock();
        if (isOccupied(block)) return;
        if (!Tag.STAIRS.isTagged(block.getType())) return;
        final Stairs stairs = (Stairs) block.getBlockData();
        if (stairs.getHalf() != Bisected.Half.BOTTOM) return;
        if (stairs.getShape() != Stairs.Shape.STRAIGHT) return;
        if (!block.getRelative(BlockFace.UP, 1).isPassable()) return;
        if (!block.getRelative(BlockFace.UP, 2).isPassable()) return;
        final Location ploc = player.getLocation();
        if (ploc.getBlockY() != block.getY()) return;
        final BlockFace face = stairs.getFacing().getOppositeFace();
        if (!block.getRelative(face).isPassable()) return;
        final Location loc = block.getLocation().add(0.5, 0.3, 0.5);
        if (ploc.distanceSquared(loc) > 4.0) return;
        final Vector dir = face.getDirection();
        loc.setDirection(face.getDirection());
        loc.add(dir.normalize().multiply(0.2));
        final ArmorStand armorStand = loc.getWorld().spawn(loc, ArmorStand.class, as -> {
                as.setPersistent(false);
                as.setVisible(false);
                as.setMarker(true);
            });
        if (armorStand == null) return;
        ploc.setDirection(loc.getDirection());
        player.teleport(ploc);
        if (!armorStand.addPassenger(player)) {
            armorStand.remove();
            return;
        }
        final Chair chair = new Chair(toBlockVector(block), armorStand, loc.getDirection(), player.getUniqueId());
        enableChair(chair);
        // Feedback
        loc.getWorld().playSound(loc, block.getBlockSoundGroup().getHitSound(), 1.0f, 1.0f);
    }

    @EventHandler
    private void onPlayerQuit(PlayerQuitEvent event) {
        final Player player = event.getPlayer();
        if (!(player.getVehicle() instanceof ArmorStand)) return;
        final ArmorStand armorStand = (ArmorStand) player.getVehicle();
        final Chair chair = uuidMap.get(armorStand.getUniqueId());
        if (chair == null) return;
        disableChair(chair);
        teleportOut(chair);
    }

    @EventHandler
    private void onEntityDismount(EntityDismountEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!(event.getDismounted() instanceof ArmorStand armorStand)) return;
        final Chair chair = uuidMap.get(armorStand.getUniqueId());
        if (chair == null) return;
        disableChair(chair);
        Bukkit.getScheduler().runTask(this, () -> teleportOut(chair));
    }

    private void removeChairBlock(Block block) {
        final Chair chair = blockMap.get(toBlockVector(block));
        if (chair == null) {
            return;
        }
        disableChair(chair);
        teleportOut(chair);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    private void onBlockBreak(BlockBreakEvent event) {
        removeChairBlock(event.getBlock());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    private void onBlockExplode(BlockExplodeEvent event) {
        for (Block block : event.blockList()) {
            removeChairBlock(block);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    private void onEntityExplode(EntityExplodeEvent event) {
        for (Block block : event.blockList()) {
            removeChairBlock(block);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    private void onBlockPistonExtend(BlockPistonExtendEvent event) {
        for (Block block : event.getBlocks()) {
            removeChairBlock(block);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    private void onBlockPistonRetract(BlockPistonRetractEvent event) {
        for (Block block : event.getBlocks()) {
            removeChairBlock(block);
        }
    }
}
