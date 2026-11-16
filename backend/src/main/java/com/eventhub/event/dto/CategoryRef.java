package com.eventhub.event.dto;

import com.eventhub.category.Category;

public record CategoryRef(String name, String slug) {

    public static CategoryRef from(Category c) {
        return new CategoryRef(c.getName(), c.getSlug());
    }
}
