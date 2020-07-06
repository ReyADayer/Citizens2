package net.citizensnpcs.trait;

import java.util.List;

import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;

import com.google.common.collect.Lists;

import net.citizensnpcs.Settings.Setting;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.MemoryNPCDataStore;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.npc.NPCRegistry;
import net.citizensnpcs.api.persistence.Persist;
import net.citizensnpcs.api.trait.Trait;
import net.citizensnpcs.api.trait.TraitName;
import net.citizensnpcs.api.util.Placeholders;
import net.citizensnpcs.util.NMS;

/**
 * Persists a hologram attached to the NPC.
 */
@TraitName("hologramtrait")
public class HologramTrait extends Trait {
    private Location currentLoc;
    private final List<NPC> hologramNPCs = Lists.newArrayList();
    @Persist
    private double lineHeight = -1;
    @Persist
    private final List<String> lines = Lists.newArrayList();
    private NPC nameNPC;
    private final NPCRegistry registry = CitizensAPI.createAnonymousNPCRegistry(new MemoryNPCDataStore());

    public HologramTrait() {
        super("hologramtrait");
    }

    public void addLine(String text) {
        lines.add(text);
        unload();
        load();
    }

    private NPC createHologram(String line, double heightOffset) {
        NPC hologramNPC = registry.createNPC(EntityType.ARMOR_STAND, line);
        ArmorStandTrait trait = hologramNPC.getTrait(ArmorStandTrait.class);
        trait.setVisible(false);
        trait.setSmall(true);
        trait.setMarker(true);
        trait.setGravity(false);
        trait.setHasArms(false);
        trait.setHasBaseplate(false);
        hologramNPC.spawn(currentLoc.clone().add(0, getEntityHeight() + heightOffset, 0));
        hologramNPC.getEntity().setInvulnerable(true);
        return hologramNPC;
    }

    private double getEntityHeight() {
        if (SUPPORT_GET_HEIGHT) {
            try {
                return npc.getEntity().getHeight();
            } catch (NoSuchMethodError err) {
                SUPPORT_GET_HEIGHT = false;
            }
        }
        return NMS.getHeight(npc.getEntity());
    }

    private double getHeight(int lineNumber) {
        return (lineHeight == -1 ? Setting.DEFAULT_NPC_HOLOGRAM_LINE_HEIGHT.asDouble() : lineHeight) * (lineNumber + 1);
    }

    public List<String> getLines() {
        return lines;
    }

    private void load() {
        currentLoc = npc.getStoredLocation();
        int i = 0;
        if (npc.requiresNameHologram()) {
            nameNPC = createHologram(npc.getFullName(), 0);
            npc.data().set(NPC.NAMEPLATE_VISIBLE_METADATA, false);
        }
        for (String line : lines) {
            hologramNPCs.add(createHologram(Placeholders.replace(line, null, npc), getHeight(i)));
            i++;
        }
    }

    @Override
    public void onDespawn() {
        unload();
    }

    @Override
    public void onRemove() {
        unload();
    }

    @Override
    public void onSpawn() {
        load();
    }

    public void removeLine(int idx) {
        lines.remove(idx);
        unload();
        load();
    }

    @Override
    public void run() {
        if (!npc.isSpawned()) {
            unload();
            return;
        }
        boolean update = currentLoc.distanceSquared(npc.getStoredLocation()) >= 0.01;
        if (update) {
            currentLoc = npc.getStoredLocation();
        }
        if (nameNPC != null && nameNPC.isSpawned()) {
            if (update) {
                nameNPC.teleport(currentLoc.clone().add(0, getEntityHeight(), 0), TeleportCause.PLUGIN);
            }
            nameNPC.setName(npc.getFullName());
        }
        for (int i = 0; i < hologramNPCs.size(); i++) {
            NPC hologramNPC = hologramNPCs.get(i);
            if (!hologramNPC.isSpawned())
                continue;
            if (update) {
                hologramNPC.teleport(currentLoc.clone().add(0, getEntityHeight() + getHeight(i), 0),
                        TeleportCause.PLUGIN);
            }
            String text = lines.get(i);
            if (text != null && !text.isEmpty()) {
                hologramNPC.setName(Placeholders.replace(text, null, npc));
            } else {
            }
        }
    }

    public void setLine(int idx, String text) {
        lines.set(idx, text);
    }

    public void setLineHeight(double height) {
        lineHeight = height;
    }

    private void unload() {
        if (nameNPC != null) {
            nameNPC.destroy();
            nameNPC = null;
        }
        if (hologramNPCs.isEmpty())
            return;
        for (NPC npc : hologramNPCs) {
            npc.destroy();
        }
        hologramNPCs.clear();
    }

    private static boolean SUPPORT_GET_HEIGHT = true;
}
