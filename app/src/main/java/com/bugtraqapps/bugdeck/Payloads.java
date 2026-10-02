package com.bugtraqapps.bugdeck;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parsed contents of {@code assets/payloads.json}, held in memory after load.
 *
 * <p>Kept separate from the fragment so the categories can be listed before any
 * view exists, and so an empty or missing file degrades to an empty list rather
 * than a crash on first draw.
 */
final class Payloads {

    private static final Map<String, List<String>> BY_KEY = new LinkedHashMap<>();
    private static final Map<String, String> TITLES = new LinkedHashMap<>();

    private Payloads() {}

    static synchronized void put(String key, List<String> payloads) {
        BY_KEY.put(key, payloads == null
            ? new ArrayList<String>()
            : new ArrayList<>(payloads));
    }

    static synchronized void putTitle(String key, String title) {
        TITLES.put(key, title);
    }

    static synchronized List<String> get(String key) {
        List<String> l = BY_KEY.get(key);
        return l == null ? new ArrayList<String>() : new ArrayList<>(l);
    }

    static synchronized String title(String key) {
        String t = TITLES.get(key);
        return t == null ? key : t;
    }

    static synchronized int count(String key) {
        List<String> l = BY_KEY.get(key);
        return l == null ? 0 : l.size();
    }

    static synchronized List<String> keys() {
        return new ArrayList<>(BY_KEY.keySet());
    }

    static synchronized int total() {
        int n = 0;
        for (List<String> l : BY_KEY.values()) n += l.size();
        return n;
    }
}
