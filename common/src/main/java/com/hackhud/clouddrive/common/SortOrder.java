package com.hackhud.clouddrive.common;

/**
 * Порядок сортування за датою і часом модифікації (варіант 8).
 */
public enum SortOrder {
    NONE("Без сортування"),
    ASC("Дата зміни ↑"),
    DESC("Дата зміни ↓");

    private final String title;

    SortOrder(String title) {
        this.title = title;
    }

    public static SortOrder parse(String value) {
        if (value == null || value.isBlank()) {
            return NONE;
        }
        return valueOf(value.trim().toUpperCase());
    }

    @Override
    public String toString() {
        return title;
    }
}
