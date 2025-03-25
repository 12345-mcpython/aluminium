package com.laosun.aluminium;

import com.google.gson.reflect.TypeToken;
import com.laosun.aluminium.utils.JSONReader;

import java.util.Map;


public final class Constant {
    // <star level> part attribute <base bonus> <double value>
    public static final Map<Integer, Map<String, Map<String, Map<String, Double>>>> RELIC_MAIN_ATTRIBUTES;
    public static final Map<Integer, Map<String, Map<String, Double>>> RELIC_SUB_ATTRIBUTES;

    static {
        RELIC_MAIN_ATTRIBUTES = JSONReader.fromJSON("main_attribute.json", new TypeToken<Map<Integer, Map<String, Map<String, Map<String, Double>>>>>() {
        }.getType());
        RELIC_SUB_ATTRIBUTES = JSONReader.fromJSON("sub_attribute.json", new TypeToken<Map<Integer, Map<String, Map<String, Double>>>>() {
        }.getType());
    }
}
