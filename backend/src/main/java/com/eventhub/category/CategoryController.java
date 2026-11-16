package com.eventhub.category;

import java.util.List;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {

    public record CategoryResponse(UUID id, String name, String slug) {
        public static CategoryResponse from(Category c) {
            return new CategoryResponse(c.getId(), c.getName(), c.getSlug());
        }
    }

    private final CategoryRepository categories;

    public CategoryController(CategoryRepository categories) {
        this.categories = categories;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<CategoryResponse> list() {
        return categories.findAllByOrderBySortOrderAscNameAsc().stream().map(CategoryResponse::from).toList();
    }
}
