package me.astrax.race;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;

/** Holder để nhận diện GUI của plugin và ánh xạ slot -> hành động. */
public final class RaceGuiHolder implements InventoryHolder {

    public enum Type { MAIN, DETAIL, UPGRADE }

    private final Type type;
    private final String raceId;
    private final Map<Integer, String> actions = new HashMap<>();
    private Inventory inventory;

    public RaceGuiHolder(Type type, String raceId) {
        this.type = type;
        this.raceId = raceId;
    }

    public Type getType() { return type; }
    public String getRaceId() { return raceId; }

    public void setInventory(Inventory inventory) { this.inventory = inventory; }

    @Override
    public Inventory getInventory() { return inventory; }

    public void setAction(int slot, String action) { actions.put(slot, action); }
    public String getAction(int slot) { return actions.get(slot); }
    public boolean hasAction(int slot) { return actions.containsKey(slot); }
}
