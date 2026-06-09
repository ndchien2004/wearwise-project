package org.group7.wearwise.config;

import org.group7.wearwise.enums.ClothingCategory;
import org.group7.wearwise.enums.Season;
import org.group7.wearwise.enums.Style;
import org.group7.wearwise.repository.ClothingItemRepository;
import org.group7.wearwise.service.ClothingItemService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "wearwise.seed.enabled", havingValue = "true")
public class DemoDataSeeder implements CommandLineRunner {

    private final ClothingItemRepository clothingItemRepository;
    private final ClothingItemService clothingItemService;

    public DemoDataSeeder(
            ClothingItemRepository clothingItemRepository,
            ClothingItemService clothingItemService
    ) {
        this.clothingItemRepository = clothingItemRepository;
        this.clothingItemService = clothingItemService;
    }

    @Override
    public void run(String... args) {
        if (clothingItemRepository.count() > 0) {
            return;
        }

        clothingItemService.createItem("White Oxford Shirt", "White", ClothingCategory.SHIRT, Season.ALL_SEASON, Style.FORMAL, true);
        clothingItemService.createItem("Black Jeans", "Black", ClothingCategory.PANTS, Season.ALL_SEASON, Style.CASUAL, false);
        clothingItemService.createItem("Running Sneakers", "Gray", ClothingCategory.SHOES, Season.SUMMER, Style.SPORT, true);
        clothingItemService.createItem("Denim Jacket", "Blue", ClothingCategory.JACKET, Season.WINTER, Style.STREETWEAR, false);
        clothingItemService.createItem("Silver Watch", "Silver", ClothingCategory.ACCESSORY, Season.ALL_SEASON, Style.FORMAL, true);
    }
}
