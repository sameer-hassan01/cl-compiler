package cl;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/*
 * SymbolTable for CL.
 *
 * CL has a single global scope so a flat hash-map keyed on the
 * lexeme is enough -- no scope stack required.
 *
 * Each entry stores:
 *      lexeme  ->  the exact text of the identifier
 *      type    ->  "int" | "float" | "string" | "char"
 *      value   ->  the current value as text (initial value from
 *                  the variables block, updated later by assignments)
 *      line    ->  the line number where it was declared
 *
 * LinkedHashMap is used so the print order matches the order the
 * variables were written by the programmer -- it just looks nicer
 * in the console output.
 */
public class SymbolTable {

    public static class Entry {
        public final String lexeme;
        public final String type;
        public String       value;
        public final int    line;

        public Entry(String lexeme, String type, String value, int line) {
            this.lexeme = lexeme;
            this.type   = type;
            this.value  = value;
            this.line   = line;
        }
    }

    private final Map<String, Entry> table = new LinkedHashMap<String, Entry>();

    /** returns false when the identifier was already declared */
    public boolean declare(String lexeme, String type, String value, int line) {
        if (table.containsKey(lexeme)) return false;
        table.put(lexeme, new Entry(lexeme, type, value, line));
        return true;
    }

    public Entry lookup(String lexeme) {
        return table.get(lexeme);
    }

    public boolean isDeclared(String lexeme) {
        return table.containsKey(lexeme);
    }

    public void updateValue(String lexeme, String value) {
        Entry e = table.get(lexeme);
        if (e != null) e.value = value;
    }

    public Collection<Entry> entries() {
        return table.values();
    }

    public void print() {
        System.out.printf("%-15s %-10s %-20s %s%n", "LEXEME", "TYPE", "VALUE", "LINE");
        System.out.println("---------------------------------------------------------------");
        for (Entry e : table.values()) {
            String v = e.value == null ? "-" : e.value;
            System.out.printf("%-15s %-10s %-20s %d%n", e.lexeme, e.type, v, e.line);
        }
    }
}
