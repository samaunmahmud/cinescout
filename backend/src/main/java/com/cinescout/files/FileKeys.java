package com.cinescout.files;

import java.util.regex.Pattern;

/** The shape every file key must have: path segments of letters, digits, dots, dashes and underscores. */
final class FileKeys {

    private static final Pattern KEY = Pattern.compile("[A-Za-z0-9_-]+(/[A-Za-z0-9_-]+)*(\\.[A-Za-z0-9]+)?");

    private FileKeys() {
    }

    static String checked(String key) {
        if (key == null || key.length() > 300 || !KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("Not a valid file key: " + key);
        }
        return key;
    }
}
