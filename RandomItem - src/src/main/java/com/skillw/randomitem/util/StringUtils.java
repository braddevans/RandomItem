package com.skillw.randomitem.util;

import com.skillw.randomitem.Main;
import com.skillw.randomitem.api.section.BaseSection;
import com.skillw.randomitem.api.section.ComplexData;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static com.skillw.randomitem.Main.sendDebug;
import static com.skillw.randomitem.util.NumberUtils.getNumbers;
import static com.skillw.randomitem.util.ProcessUtils.replaceAll;
import static com.skillw.randomitem.util.Utils.checkNull;

/**
 * @ClassName : com.skillw.randomitem.utils.StringHandler
 * Created by Glom_ on 2021-02-15 17:37:47
 * Copyright  2020 user. All rights reserved.
 */
public final class StringUtils {

    private StringUtils() {
    }

    public static List<String> addStrings(List<String> strings, String string) {
        if (string.contains("\n")) {
            Collections.addAll(strings, string.split("\n"));
        } else if (string.contains("\\n")) {
            Collections.addAll(strings, string.split("\\\\n"));
        } else {
            strings.add(string);
        }
        return strings;
    }

    /**
     * To replace the & to §.
     *
     * @param msg text
     * @return the txt after replacing
     */
    public static String getMessage(String msg) {
        if (msg != null) {
            return msg.replace("&", "§");
        } else {
            return null;
        }
    }

    /**
     * To replace the § to &.
     *
     * @param msg text
     * @return the txt after replacing
     */
    public static String messageToOriginalText(String msg) {
        if (msg != null) {
            return msg.replace("§", "&");
        } else {
            return null;
        }
    }

    /**
     * To remove color
     *
     * @param msg text
     * @return the txt after replacing
     */
    public static String messageToText(String msg) {
        if (msg != null) {
            return ChatColor.stripColor(getMessage(msg));
        } else {
            return null;
        }
    }

    public static String listToStringWithNext(List<String> strings) {
        return strings.toString().replace(", ", "\n").replace("[", "").replace("]", "");
    }

    public static String replacePAPI(String text, UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        return replacePAPI(text, player);
    }

    public static String replacePAPI(String text, Player player) {
        if (!Main.getInstance().papi || player == null) {
            return text;
        }
        return getMessage(PlaceholderAPI.setPlaceholders(player, text));
    }

    static String handleStringReplaced(String value, ComplexData data) {
        List<String> strings = intercept(value);
        for (String replaced : strings) {
            String string = handleReplaced(replaced, data);
            if (string != null) {
                sendDebug("&d  - &b" + "<" + replaced + ">" + " &5: &e" + (string.contains("\n") || string.contains("\\n") ? "\n" : "") + messageToText(string));
                value = value.replace("<" + replaced + ">", string);
            }
        }
        return value;
    }

    static String handleReplaced(String replaced, ComplexData data) {
        ConcurrentHashMap<String, BaseSection> sectionMap = data.getSectionMap();
        ConcurrentHashMap<String, String> alreadySectionMap = data.getAlreadySectionMap();
        String string = null;
        String otherKey;
        if (replaced.contains(".")) {
            if (replaced.split("\\.").length > 0) {
                otherKey = replaceAll(replaced.split("\\.")[0], data);
                BaseSection section = sectionMap.get(otherKey);
                if (section != null) {
                    if (!alreadySectionMap.containsKey(otherKey)) {
                        section.load(replaced, data);
                    }
                    String value = replaced.split("\\.")[1];
                    String key = section.getId() + "." + value;
                    if (section.getDataMap().containsKey(value) || alreadySectionMap.containsKey(key)) {
                        if (alreadySectionMap.containsKey(key)) {
                            return alreadySectionMap.get(key);
                        }
                        return replaceAll(String.valueOf(section.getDataMap().get(value)), data);
                    }
                }
                String value = alreadySectionMap.get(otherKey);
                if (checkNull(value, "Wrong section ID in " + replaced + "!")) {
                    return replaced;
                }
                string = getStrings(replaced, value, data);
            }
        } else {
            if (sectionMap.containsKey(replaced)) {
                otherKey = replaced;
                if (!alreadySectionMap.containsKey(otherKey)) {
                    BaseSection section = sectionMap.get(otherKey);
                    section.load(replaced, data);
                }
                string = alreadySectionMap.get(otherKey);
            }
        }
        return string;
    }

    static String getStrings(String replace, String string, ComplexData data) {
        List<String> strings = new ArrayList<>();
        List<String> stringList = addStrings(new ArrayList<>(), string);
        if (replace.contains(".")) {
            String numbers;
            if (replace.contains(">.<")) {
                numbers = replace.split(">\\.")[1];
            } else if (replace.contains(">.") && !replace.contains(".<")) {
                numbers = replace.split(">\\.")[1];
            } else if (replace.contains(".<")) {
                numbers = "<" + replace.split("\\.<")[1];
            } else {
                numbers = replace.split("\\.")[1];
            }
            if (numbers.contains("<") && numbers.contains(">")) {
                numbers = replaceAll(numbers, data);
            }
            int maxIndex = stringList.size() - 1;
            for (int i : getNumbers(numbers, maxIndex)) {
                if (stringList.get(i) != null && !stringList.get(i).isEmpty()) {
                    strings.add(stringList.get(i));
                }
            }
        } else {
            strings.addAll(stringList);
        }
        return listToStringWithNext(strings);
    }

    static List<String> interceptRedundantEscaped(String text) {
        ArrayList<String> strings = new ArrayList<>();
        int start = -114514, end;
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) != '\\' || text.charAt(i + 1) == 'n' || text.charAt(i + 1) == '\n') {
                continue;
            }
            if (start == -114514) {
                if (count == 0) {
                    start = i;
                }
                count++;
            } else {
                count--;
                if (count == 0) {
                    end = i;
                    String already = text.substring(start, end + 1);
                    strings.add(already);
                    start = -114514;
                }
            }

        }
        return strings;
    }

    /**
     * To get the List of the strings between "<" and ">"
     *
     * @param text the text
     * @return the List of the strings between "<" and ">"
     */
    static List<String> intercept(String text) {
        ArrayList<String> strings = new ArrayList<>();
        for (String redundant : interceptRedundantEscaped(text)) {
            text = text.replace(redundant, "");
        }
        int start = 0, end;
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '<') {
                if (count == 0) {
                    start = i;
                }
                count++;
            }
            if (text.charAt(i) == '>') {
                count--;
                if (count == 0) {
                    end = i;
                    strings.add(text.substring(start + 1, end));
                }
            }
        }
        return strings;
    }

    public static String deleteSlashes(String value) {
        char[] chars = value.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            if (c == '\\' && (chars[i + 1] != 'n' && chars[i + 1] != '\n')) {
                chars[i] = '`';
            }
        }
        value = new String(chars).replace("`", "");
        return value;
    }

    public static String toJson(Object object) {
        return Main.getGson().toJson(object);
    }

    public static boolean isJson(String str) {
        boolean result = false;
        str = str.trim();
        if (str.startsWith("{") && str.endsWith("}")) {
            result = true;
        } else if (str.startsWith("[") && str.endsWith("]")) {
            result = true;
        }
        return result;
    }
}
