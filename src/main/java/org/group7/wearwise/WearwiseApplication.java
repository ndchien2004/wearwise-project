package org.group7.wearwise;

import org.group7.wearwise.entity.ClothingItem;
import org.group7.wearwise.enums.ClothingCategory;
import org.group7.wearwise.enums.Season;
import org.group7.wearwise.enums.Style;
import org.group7.wearwise.service.ClothingItemService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.List;
import java.util.Scanner;

@SpringBootApplication
public class WearwiseApplication {

    public static void main(String[] args) {
        SpringApplication.run(WearwiseApplication.class, args);
    }

    @Bean
    public CommandLineRunner run(ClothingItemService clothingItemService) {
        return args -> {
            Scanner scanner = new Scanner(System.in);
            boolean running = true;

            while (running) {
                printMenu();

                System.out.print("Choose an option: ");
                String choice = scanner.nextLine();

                try {
                    switch (choice) {
                        case "1" -> createItem(scanner, clothingItemService);
                        case "2" -> showAllItems(clothingItemService);
                        case "3" -> showItemById(scanner, clothingItemService);
                        case "4" -> updateItem(scanner, clothingItemService);
                        case "5" -> deleteItem(scanner, clothingItemService);
                        case "6" -> searchByName(scanner, clothingItemService);
                        case "7" -> filterByCategory(scanner, clothingItemService);
                        case "8" -> showFavoriteItems(clothingItemService);
                        case "0" -> {
                            running = false;
                            System.out.println("Exiting WearWise Console App...");
                        }
                        default -> System.out.println("Invalid option. Please try again.");
                    }
                } catch (Exception e) {
                    System.out.println("Error: " + e.getMessage());
                }
            }

            scanner.close();
        };
    }

    private void printMenu() {
        System.out.println();
        System.out.println("========== WEARWISE CONSOLE CRUD ==========");
        System.out.println("1. Create clothing item");
        System.out.println("2. Show all clothing items");
        System.out.println("3. Show clothing item by ID");
        System.out.println("4. Update clothing item");
        System.out.println("5. Delete clothing item");
        System.out.println("6. Search clothing item by name");
        System.out.println("7. Filter clothing item by category");
        System.out.println("8. Show favorite clothing items");
        System.out.println("0. Exit");
        System.out.println("===========================================");
    }

    private void createItem(Scanner scanner, ClothingItemService service) {
        System.out.println();
        System.out.println("----- Create Clothing Item -----");

        System.out.print("Name: ");
        String name = scanner.nextLine();

        System.out.print("Color: ");
        String color = scanner.nextLine();

        ClothingCategory category = inputCategory(scanner);
        Season season = inputSeason(scanner);
        Style style = inputStyle(scanner);

        System.out.print("Favorite? true/false: ");
        Boolean favorite = Boolean.parseBoolean(scanner.nextLine());

        ClothingItem createdItem = service.createItem(
                name,
                color,
                category,
                season,
                style,
                favorite
        );

        System.out.println("Created successfully!");
        printItem(createdItem);
    }

    private void showAllItems(ClothingItemService service) {
        System.out.println();
        System.out.println("----- All Clothing Items -----");

        List<ClothingItem> items = service.getAllItems();

        if (items.isEmpty()) {
            System.out.println("No clothing items found.");
            return;
        }

        items.forEach(this::printItem);
    }

    private void showItemById(Scanner scanner, ClothingItemService service) {
        System.out.println();
        System.out.println("----- Find Clothing Item By ID -----");

        System.out.print("Enter ID: ");
        Long id = Long.parseLong(scanner.nextLine());

        ClothingItem item = service.getItemById(id);
        printItem(item);
    }

    private void updateItem(Scanner scanner, ClothingItemService service) {
        System.out.println();
        System.out.println("----- Update Clothing Item -----");

        System.out.print("Enter ID to update: ");
        Long id = Long.parseLong(scanner.nextLine());

        ClothingItem oldItem = service.getItemById(id);

        System.out.println("Current item:");
        printItem(oldItem);

        System.out.print("New name: ");
        String name = scanner.nextLine();

        System.out.print("New color: ");
        String color = scanner.nextLine();

        ClothingCategory category = inputCategory(scanner);
        Season season = inputSeason(scanner);
        Style style = inputStyle(scanner);

        System.out.print("Favorite? true/false: ");
        Boolean favorite = Boolean.parseBoolean(scanner.nextLine());

        ClothingItem updatedItem = service.updateItem(
                id,
                name,
                color,
                category,
                season,
                style,
                favorite
        );

        System.out.println("Updated successfully!");
        printItem(updatedItem);
    }

    private void deleteItem(Scanner scanner, ClothingItemService service) {
        System.out.println();
        System.out.println("----- Delete Clothing Item -----");

        System.out.print("Enter ID to delete: ");
        Long id = Long.parseLong(scanner.nextLine());

        service.deleteItem(id);

        System.out.println("Deleted successfully!");
    }

    private void searchByName(Scanner scanner, ClothingItemService service) {
        System.out.println();
        System.out.println("----- Search By Name -----");

        System.out.print("Enter keyword: ");
        String keyword = scanner.nextLine();

        List<ClothingItem> items = service.searchByName(keyword);

        if (items.isEmpty()) {
            System.out.println("No matching clothing items found.");
            return;
        }

        items.forEach(this::printItem);
    }

    private void filterByCategory(Scanner scanner, ClothingItemService service) {
        System.out.println();
        System.out.println("----- Filter By Category -----");

        ClothingCategory category = inputCategory(scanner);

        List<ClothingItem> items = service.filterByCategory(category);

        if (items.isEmpty()) {
            System.out.println("No clothing items found with category: " + category);
            return;
        }

        items.forEach(this::printItem);
    }

    private void showFavoriteItems(ClothingItemService service) {
        System.out.println();
        System.out.println("----- Favorite Clothing Items -----");

        List<ClothingItem> items = service.getFavoriteItems();

        if (items.isEmpty()) {
            System.out.println("No favorite clothing items found.");
            return;
        }

        items.forEach(this::printItem);
    }

    private ClothingCategory inputCategory(Scanner scanner) {
        System.out.println("Choose category:");
        System.out.println("1. SHIRT");
        System.out.println("2. PANTS");
        System.out.println("3. SHOES");
        System.out.println("4. JACKET");
        System.out.println("5. ACCESSORY");
        System.out.print("Category option: ");

        String option = scanner.nextLine();

        return switch (option) {
            case "1" -> ClothingCategory.SHIRT;
            case "2" -> ClothingCategory.PANTS;
            case "3" -> ClothingCategory.SHOES;
            case "4" -> ClothingCategory.JACKET;
            case "5" -> ClothingCategory.ACCESSORY;
            default -> throw new RuntimeException("Invalid category option.");
        };
    }

    private Season inputSeason(Scanner scanner) {
        System.out.println("Choose season:");
        System.out.println("1. SUMMER");
        System.out.println("2. WINTER");
        System.out.println("3. ALL_SEASON");
        System.out.print("Season option: ");

        String option = scanner.nextLine();

        return switch (option) {
            case "1" -> Season.SUMMER;
            case "2" -> Season.WINTER;
            case "3" -> Season.ALL_SEASON;
            default -> throw new RuntimeException("Invalid season option.");
        };
    }

    private Style inputStyle(Scanner scanner) {
        System.out.println("Choose style:");
        System.out.println("1. CASUAL");
        System.out.println("2. FORMAL");
        System.out.println("3. STREETWEAR");
        System.out.println("4. SPORT");
        System.out.print("Style option: ");

        String option = scanner.nextLine();

        return switch (option) {
            case "1" -> Style.CASUAL;
            case "2" -> Style.FORMAL;
            case "3" -> Style.STREETWEAR;
            case "4" -> Style.SPORT;
            default -> throw new RuntimeException("Invalid style option.");
        };
    }

    private void printItem(ClothingItem item) {
        System.out.println("-----------------------------------");
        System.out.println("ID: " + item.getId());
        System.out.println("Name: " + item.getName());
        System.out.println("Color: " + item.getColor());
        System.out.println("Category: " + item.getCategory());
        System.out.println("Season: " + item.getSeason());
        System.out.println("Style: " + item.getStyle());
        System.out.println("Favorite: " + item.getFavorite());
        System.out.println("Created At: " + item.getCreatedAt());
        System.out.println("Updated At: " + item.getUpdatedAt());
    }
}