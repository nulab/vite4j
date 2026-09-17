package com.nulabinc.vite4j;

/** Just enough escaping to put a URL inside a double-quoted attribute safely. */
final class Html {

    private Html() {
    }

    static String attribute(String value) {
        StringBuilder escaped = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '&':
                    escaped.append("&amp;");
                    break;
                case '"':
                    escaped.append("&quot;");
                    break;
                case '<':
                    escaped.append("&lt;");
                    break;
                case '>':
                    escaped.append("&gt;");
                    break;
                default:
                    escaped.append(c);
            }
        }
        return escaped.toString();
    }
}
