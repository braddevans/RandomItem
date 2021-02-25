package com.skillw.randomitem.api.section.weight;

import com.skillw.randomitem.api.data.BasicData;
import com.skillw.randomitem.util.ProcessUtils;
import org.bukkit.entity.Player;

import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Glom_
 * @ClassName : com.skillw.randomitem.api.section.weight.Weighable
 * @date 2021-02-09 16:37:34
 * Copyright  2020 user. All rights reserved.
 * @apiNote : The class which impl this must extend BaseSection!
 * @apiNote : And the map in the class must have a key "value-map" and its value ConcurrentHashMap<String and BasicData>
 */
public interface Weighable<T extends BasicData<?>> {
    /**
     * To get the result of random base on weights
     *
     * @param weighable the weighable
     * @param player    the player
     * @return the result of random base on weights
     */
    static BasicData<?> getWeightRandom(Weighable<? extends BasicData<?>> weighable, Player player) {
        return ProcessUtils.getRandomBasicDataBaseOnWeight(weighable.getBasicDataMap(), player);
    }

    /**
     * To get the clone of Map of String and T
     *
     * @return the clone of Map of String and T
     */
    ConcurrentHashMap<String, T> getValueMap();

    /**
     * To get the clone of Map of String and BasicData
     *
     * @return the clone of Map of String and BasicData
     */
    ConcurrentHashMap<String, BasicData<?>> getBasicDataMap();

}