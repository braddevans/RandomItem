package com.skillw.randomitem.callable;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.skillw.randomitem.Main;
import com.skillw.randomitem.api.event.RandomItemFinishGeneratingEvent;
import com.skillw.randomitem.api.event.RandomItemStartGeneratingEvent;
import com.skillw.randomitem.api.randomitem.ItemData;
import com.skillw.randomitem.api.section.BaseSection;
import com.skillw.randomitem.api.section.ComplexData;
import com.skillw.randomitem.complex.ComplexDataImpl;
import io.izzel.taboolib.module.nms.NMS;
import io.izzel.taboolib.util.item.ItemBuilder;
import io.izzel.taboolib.util.item.Items;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

import static com.skillw.randomitem.Main.isDebug;
import static com.skillw.randomitem.Main.sendDebug;
import static com.skillw.randomitem.utils.CalculationUtils.getResult;
import static com.skillw.randomitem.utils.NBTUtils.translateSection;
import static com.skillw.randomitem.utils.SectionUtils.debugSection;
import static com.skillw.randomitem.utils.Utils.handlePointData;
import static com.skillw.randomitem.utils.Utils.replaceAll;

/**
 * @ClassName : com.skillw.randomitem.callable.RandomItemCallable
 * Created by Glom_ on 2021-02-04 19:37:20
 * Copyright  2020 user. All rights reserved.
 */
public class RandomItemCallable implements Callable<ItemStack> {
    private final Player player;
    private final ItemData itemData;
    private final String pointData;

    public RandomItemCallable(Player player,
                              ItemData itemData,
                              String pointData) {
        this.player = player;
        this.itemData = itemData;
        this.pointData = pointData;
    }

    @Override
    public ItemStack call() {
        sendDebug("&aGenerating item: &6" + this.getItemData().getId());
        long startTime = System.currentTimeMillis();
        ConcurrentHashMap<String, BaseSection> sectionMap = this.getItemData().getSectionMapClone();
        ConcurrentHashMap<String, String> enchantmentMap = this.getItemData().getEnchantMapClone();
        ConcurrentHashMap<String, List<String>> alreadySectionMap = new ConcurrentHashMap<>();
        ComplexData complexData = new ComplexDataImpl(sectionMap, alreadySectionMap, this.player);
        ItemBuilder builder;
        if (Main.version > 1121) {
            builder = new ItemBuilder(Material.IRON_AXE, 1);
        } else {
            builder = new ItemBuilder(Material.IRON_AXE, 1);
        }
        String display = (this.getItemData().getDisplay() == null) ? this.getItemData().getMaterial() : this.getItemData().getDisplay();
        if (this.pointData != null) {
            handlePointData(this.pointData, complexData);
        }
        sendDebug("&d- &aOriginal display: &6" + display);

        sendDebug("&d- &aOriginal material: &6" + this.getItemData().getMaterial());

        sendDebug("&d- &aOriginal data: &6" + this.getItemData().getData());

        if (isDebug() && !this.getItemData().getLoresClone().isEmpty()) {
            sendDebug("&d- &aOriginal lores: ");
            for (String lore : this.getItemData().getLoresClone()) {
                sendDebug("&d  - &f" + lore);
            }
        }
        boolean unbreakable = false;
        if (this.getItemData().getUnbreakableFormula() != null) {
            sendDebug("&d- &aOriginal unbreakable: " + this.getItemData().getUnbreakableFormula());
            if (this.getItemData().getUnbreakableFormula().equals("true") || this.getItemData().getUnbreakableFormula().equals("false")) {
                unbreakable = Boolean.parseBoolean(this.getItemData().getUnbreakableFormula());
            } else {
                unbreakable = getResult(replaceAll(this.getItemData().getUnbreakableFormula(), complexData)) != 0;
            }
        }
        List<String> itemFlags = this.getItemData().getItemFlagsClone();
        if (itemFlags != null && !itemFlags.isEmpty()) {
            sendDebug("&d- &aOriginal ItemFlags: ");
            for (int i = 0; i < itemFlags.size(); i++) {
                String itemFlag = itemFlags.get(i);
                sendDebug("&d  - &f" + itemFlag);
                itemFlag = replaceAll(itemFlag, complexData);
                itemFlags.set(i, itemFlag);
            }
        }

        if (this.pointData != null) {
            sendDebug("&d- &aPoint Data: &6" + this.pointData);
        }
        if (!alreadySectionMap.isEmpty()) {
            if (isDebug()) {
                sendDebug("&d- &aAlready Sections: &6");
                for (String key : alreadySectionMap.keySet()) {
                    sendDebug("&d  -> &b" + key + " &5= &e" + alreadySectionMap.get(key));
                }
            }
        }
        if (this.getItemData().getNbtSection() != null && !this.getItemData().getNbtSection().getKeys(false).isEmpty()) {
            sendDebug("&d- &aOriginal NBT-keys: ");
            for (String key : this.getItemData().getNbtSection().getKeys(false)) {
                Object object = this.getItemData().getNbtSection().get(key);
                if (object instanceof ConfigurationSection) {
                    ConfigurationSection section = (ConfigurationSection) object;
                    if (!section.getKeys(false).isEmpty()) {
                        sendDebug("&d   -> &b" + key + " &5= &e" + section.get(key));
                    }
                } else {
                    sendDebug("&d  -> &b" + key + " &5= &e" + this.getItemData().getNbtSection().get(key));
                }
            }
        }
        if (!enchantmentMap.isEmpty()) {
            sendDebug("&d- &aOriginal enchantments: ");
            for (String key : enchantmentMap.keySet()) {
                sendDebug("&d  -> &b" + key + " &5= &e" + enchantmentMap.get(key));
            }
        }
        if (!sectionMap.isEmpty()) {
            sendDebug("&d- &aOriginal sections: ");
            for (BaseSection baseSection : sectionMap.values()) {
                debugSection(baseSection);
            }
        }

        RandomItemStartGeneratingEvent startEvent = new RandomItemStartGeneratingEvent(this.getItemData().getId(), complexData, enchantmentMap);
        Bukkit.getPluginManager().callEvent(startEvent);
        enchantmentMap = startEvent.getEnchantmentMap();
        complexData = startEvent.getData();
        sendDebug("&d- &aReplacing sections:");
        String materialString = (replaceAll(this.getItemData().getMaterial(), complexData));
        Material matchMaterial = Material.matchMaterial(materialString);
        Material material = matchMaterial == null ? Material.STONE : matchMaterial;
        builder.material(material);
        display = replaceAll(display, complexData);
        builder.name(display);
        String data = replaceAll(this.getItemData().getData(), complexData);
        if (Main.version >= 1141) {
            builder.customModelData(Integer.parseInt(data));
        } else {
            builder.damage(Integer.parseInt(data));
        }
        List<String> newLores = new ArrayList<>();
        List<String> loresClone = this.getItemData().getLoresClone();
        for (String lore : loresClone) {
            lore = replaceAll(lore, complexData);
            if (lore.contains("\n")) {
                Collections.addAll(newLores, lore.split("\n"));
            } else {
                newLores.add(lore.replace("/", "").replace("\\", ""));
            }
        }
        builder.lore(newLores);
        for (String enchant : enchantmentMap.keySet()) {
            String value = replaceAll(enchantmentMap.get(enchant), complexData);
            int level = (int) Math.round(getResult(value));
            if (level > 0) {
                builder.enchant(Items.asEnchantment(enchant), level, true);
            }
        }
        builder.unbreakable(unbreakable);
        sendDebug("&d- &aFinal material: &6" + materialString);

        sendDebug("&d- &aFinal data: &6" + data);

        if (isDebug() && !newLores.isEmpty()) {
            sendDebug("&d- &aFinal lores: ");
            for (String lore : newLores) {
                sendDebug("&d  - &f" + lore);
            }
        }
        sendDebug("&d- &aFinal unbreakable: " + unbreakable);

        if (itemFlags != null && !itemFlags.isEmpty()) {
            sendDebug("&d- &aLoaded ItemFlags: ");
            for (String itemFlag : itemFlags) {
                sendDebug("&d  - &f" + itemFlag);
                ItemFlag flag = ItemFlag.valueOf(itemFlag);
                builder.flags(flag);
            }
        }
        ItemStack itemStack = builder.build();
        if (this.getItemData().getNbtSection() != null && !this.getItemData().getNbtSection().getKeys(false).isEmpty()) {
            sendDebug("&d- &aFinal NBT-keys: ");
        }
        translateSection(NMS.handle().loadNBT(itemStack), this.getItemData().getNbtSection(), complexData).saveTo(itemStack);
        ConfigurationSection attributeSection = this.itemData.getAttributeSection();
        if (attributeSection != null) {
            Multimap<Attribute, AttributeModifier> map = ArrayListMultimap.create();
            for (String attributeKey : attributeSection.getKeys(false)) {
                Attribute attribute = Attribute.valueOf(attributeKey);
                ConfigurationSection attSection = attributeSection.getConfigurationSection(attributeKey);
                for (String att : attSection.getKeys(false)) {
                    HashMap<String, Object> objectMap = new HashMap<>(attSection.getConfigurationSection(att).getValues(false));
                    for (String key : objectMap.keySet()) {
                        Object object = objectMap.get(key);
                        objectMap.put(key, replaceAll(String.valueOf(object), complexData));
                    }
                    AttributeModifier modifier = AttributeModifier.deserialize(objectMap);
                    map.put(attribute, modifier);
                }
            }
            ItemMeta itemMeta = itemStack.getItemMeta();
            itemMeta.setAttributeModifiers(map);
            itemStack.setItemMeta(itemMeta);
        }
        RandomItemFinishGeneratingEvent finishEvent = new RandomItemFinishGeneratingEvent(this.getItemData().getId(), this.player, itemStack);
        Bukkit.getPluginManager().callEvent(finishEvent);
        itemStack = finishEvent.getItemStack();
        if (!enchantmentMap.isEmpty()) {
            sendDebug("&d- &aFinal enchantments: ");
            for (String key : enchantmentMap.keySet()) {
                sendDebug("&d  -> &b" + key + " &5= &e" + enchantmentMap.get(key));
            }
        }
        if (!alreadySectionMap.isEmpty()) {
            if (isDebug()) {
                sendDebug("&d- &aFinal Sections: &6");
                for (String key : alreadySectionMap.keySet()) {
                    sendDebug("&d  -> &b" + key + " &5= &e" + alreadySectionMap.get(key));
                }
            }
        }
        long finishTime = System.currentTimeMillis();
        sendDebug("&2Done! &9Total time: &6" + (finishTime - startTime) + "&9ms");
        return itemStack;
    }


    public String getPointData() {
        return this.pointData;
    }

    public ItemData getItemData() {
        return this.itemData;
    }
}