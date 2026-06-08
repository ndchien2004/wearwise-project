package org.group7.wearwise.service;

import org.group7.wearwise.entity.ClothingItem;
import org.group7.wearwise.enums.ClothingCategory;
import org.group7.wearwise.enums.Season;
import org.group7.wearwise.enums.Style;
import org.group7.wearwise.repository.ClothingItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ClothingItemService {

    private final ClothingItemRepository clothingItemRepository;

    public ClothingItemService(ClothingItemRepository clothingItemRepository) {
        this.clothingItemRepository = clothingItemRepository;
    }

    @Transactional
    public ClothingItem createItem(
            String name,
            String color,
            ClothingCategory category,
            Season season,
            Style style,
            Boolean favorite
    ) {
        ClothingItem item = ClothingItem.builder()
                .name(name)
                .color(color)
                .category(category)
                .season(season)
                .style(style)
                .favorite(favorite)
                .build();

        return clothingItemRepository.save(item);
    }

    public List<ClothingItem> getAllItems() {
        return clothingItemRepository.findAll();
    }

    public ClothingItem getItemById(Long id) {
        return clothingItemRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Clothing item not found with id: " + id));
    }

    @Transactional
    public ClothingItem updateItem(
            Long id,
            String name,
            String color,
            ClothingCategory category,
            Season season,
            Style style,
            Boolean favorite
    ) {
        ClothingItem item = getItemById(id);

        item.setName(name);
        item.setColor(color);
        item.setCategory(category);
        item.setSeason(season);
        item.setStyle(style);
        item.setFavorite(favorite);

        return clothingItemRepository.save(item);
    }

    @Transactional
    public void deleteItem(Long id) {
        ClothingItem item = getItemById(id);
        clothingItemRepository.delete(item);
    }

    public List<ClothingItem> searchByName(String keyword) {
        return clothingItemRepository.findByNameContainingIgnoreCase(keyword);
    }

    public List<ClothingItem> filterByCategory(ClothingCategory category) {
        return clothingItemRepository.findByCategory(category);
    }

    public List<ClothingItem> filterBySeason(Season season) {
        return clothingItemRepository.findBySeason(season);
    }

    public List<ClothingItem> filterByStyle(Style style) {
        return clothingItemRepository.findByStyle(style);
    }

    public List<ClothingItem> getFavoriteItems() {
        return clothingItemRepository.findByFavoriteTrue();
    }
}