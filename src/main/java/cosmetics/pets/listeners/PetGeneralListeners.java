package cosmetics.pets.listeners;

import com.github.puregero.multilib.MultiLib;
import com.google.common.collect.HashBiMap;
import cosmetics.Cosmetics;
import cosmetics.RemoveEffectsOnQuit;
import org.bukkit.GameMode;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowman;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.EntityBlockFormEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.player.*;

public class PetGeneralListeners implements Listener {
    
    @SuppressWarnings("unused")
    private final Cosmetics plugin;
    public PetGeneralListeners(Cosmetics b) {
        this.plugin = b;
        PetSpawn = new PetGuiListeners(plugin);
    }

    public PetGuiListeners PetSpawn;

    public static RemoveEffectsOnQuit RemoveEffectsOnQuit = new RemoveEffectsOnQuit();
    
    // Two way map. Player's own entities and entities belong to players.
    public HashBiMap<Player, Entity> currentPet = PetGuiListeners.currentPet;
    
    // Set Pets invunerable bc setinvun on spawn no work?
    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        for (Entity entity : currentPet.values()) {
            if (entity == event.getEntity()) {
                event.setCancelled(true);
            }
        }
    }
    
    // Add particle to mobs
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        
        for (Entity en : currentPet.values()) {
            player.getLocation().getWorld().spawnParticle(Particle.COMPOSTER,
                    en.getLocation().getX(), en.getLocation().add(0, 2, 0).getY(),
                    en.getLocation().getZ(), 0);
        }
    }
    
    
    // Right click on mob to make them delete
    @EventHandler
    public void onClick(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        
        if (currentPet.containsKey(player)) {
            if (event.getRightClicked() == currentPet.get(player)) {
                event.getPlayer().getWorld().spawnParticle(Particle.EXPLOSION_HUGE,
                        event.getPlayer().getLocation().getX(), event.getPlayer().getLocation().getY(),
                        event.getPlayer().getLocation().getZ(), 0);
                event.getPlayer().getWorld().playSound(event.getPlayer().getLocation(), Sound.ENTITY_DRAGON_FIREBALL_EXPLODE,
                        3.0F, 0.533F);
                event.getRightClicked().remove();
                currentPet.remove(player);
            }  
        }
        

    }
    
    // Stop Snowman forming snow
    @EventHandler
    public void removeSnow(EntityBlockFormEvent event) {

        for (Entity entity : currentPet.values()) {

            if (entity == event.getEntity() && event.getEntity() instanceof Snowman) {
                event.setCancelled(true);  
            }
        }
    }
    
    //Stop pets burning
    @EventHandler
    public void onDisguiseDamage(EntityDamageEvent event) {
        if (currentPet.containsValue(event.getEntity())) {
            
            if (event.getCause() == DamageCause.FIRE_TICK) {
                if(event.getEntity() instanceof Zombie) {
                    event.getEntity().setFireTicks(0);
                }
            }
            
            event.setCancelled(true);
        }
    }
    
    //Stop naming and colour pet
    @EventHandler
    public void onMobInteract(PlayerInteractEntityEvent event) {
        if (currentPet.containsValue(event.getRightClicked())) {
            event.setCancelled(true);
        }
    }
    
    
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();        
        RemoveEffectsOnQuit.ClearEffects(player);
    }
    
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        
        Player player = event.getPlayer();

        if (plugin.dataPets.existsPlayer(player.getUniqueId())) {
            PetSpawn.Pet(player, plugin.dataPets.getPet(player.getUniqueId()));
            //plugin.dataPets.remove(player.getUniqueId());
        }
    }
    
    // Force pets/disguises/anything JustCosmetics to be spawned
    @EventHandler(priority=EventPriority.HIGHEST)
    public void onEntitySpawn(EntitySpawnEvent event) {
        if (event.isCancelled()) {
            int i = 0;
            for (StackTraceElement element : Thread.currentThread().getStackTrace()) {
                if (i++ >= 2 && element.getClassName().contains("cosmetics")) {
                    // This has something to do with cosmetics, must be us
                    event.setCancelled(false);
                }
            }
        }
    }
    
    // Stop pets getting stuck in unloaded chunks by teleporting them to their
    // player when the chunk unloads
    //
    // Can't modify the entities in the EntitiesUnloadEvent - rude spigot
    // Replaced with onPlayerTeleport
    //
//    @EventHandler
//    public void onEntitiesUnload(EntitiesUnloadEvent event) {
//        BiMap<Entity, Player> currentOwner = currentPet.inverse();
//
//        for (Entity entity : event.getEntities()) {
//            Player player = currentOwner.get(entity);
//
//            if (player != null) {
//                entity.teleport(player);
//            }
//        }
//    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Entity pet = currentPet.get(event.getPlayer());

        if (pet == null) {
            return;
        }

        if (event.getPlayer().getWorld() != pet.getWorld()) {
            MultiLib.teleportAsync(pet, event.getTo());
            return;
        }

        int dx = event.getPlayer().getLocation().getBlockX() - pet.getLocation().getBlockX();
        int dz = event.getPlayer().getLocation().getBlockZ() - pet.getLocation().getBlockZ();

        if (Math.abs(dx) > 40 || Math.abs(dz) > 40) {
            MultiLib.teleportAsync(pet, event.getTo());
        }
    }

    @EventHandler
    public void onGamemodeChange(PlayerGameModeChangeEvent e) {
        Player player = e.getPlayer();
        Entity pet = currentPet.get(player);
        if(pet != null && e.getNewGameMode() == GameMode.SPECTATOR) {
            pet.remove();
            currentPet.remove(player);
        } else {
            if (player.getGameMode() == GameMode.SPECTATOR && plugin.dataPets.existsPlayer(player.getUniqueId())) {
                plugin.runTaskLater(player, () -> PetSpawn.Pet(player, plugin.dataPets.getPet(player.getUniqueId())), 1);
            }
        }
    }
    
}
