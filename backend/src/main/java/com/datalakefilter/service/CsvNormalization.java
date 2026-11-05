package com.datalakefilter.service;

import java.util.Locale;

/** Reglas del prototipo CSV simple: BOM solo al inicio y espacios internos conservados. */
final class CsvNormalization {
    private CsvNormalization() { }

    static String header(String value) {
        return value != null && value.startsWith("\uFEFF") ? value.substring(1) : value;
    }

    static String row(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
