package org.group7.wearwise.console;

import org.group7.wearwise.entity.AppUser;
import org.group7.wearwise.entity.ClothingItem;
import org.group7.wearwise.enums.ClothingCategory;
import org.group7.wearwise.enums.ClothingCondition;
import org.group7.wearwise.enums.ClothingStatus;
import org.group7.wearwise.enums.Season;
import org.group7.wearwise.enums.Style;
import org.group7.wearwise.repository.AppUserRepository;
import org.group7.wearwise.service.ClothingItemService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;

@Component
@ConditionalOnProperty(name = "wearwise.console.enabled", havingValue = "true")
public class ConsoleClothingItemRunner implements CommandLineRunner {

    private static final int MAX_TEXT_LENGTH = 255;
    private static final String CONSOLE_OWNER_USERNAME = "console";

    private final ClothingItemService clothingItemService;
    private final AppUserRepository appUserRepository;

    public ConsoleClothingItemRunner(
            ClothingItemService clothingItemService,
            AppUserRepository appUserRepository
    ) {
        this.clothingItemService = clothingItemService;
        this.appUserRepository = appUserRepository;
    }

    @Override
    public void run(String... args) {
        ensureConsoleOwnerExists();

        Scanner scanner = new Scanner(System.in);
        boolean running = true;

        while (running) {
            printMenu();

            try {
                String choice = readMenuChoice(scanner);

                switch (choice) {
                    case "1" -> createItem(scanner);
                    case "2" -> showAllItems();
                    case "3" -> showItemById(scanner);
                    case "4" -> updateItem(scanner);
                    case "5" -> deleteItem(scanner);
                    case "6" -> searchByName(scanner);
                    case "7" -> filterByCategory(scanner);
                    case "8" -> showFavoriteItems();
                    case "0" -> {
                        running = false;
                        System.out.println("Exiting WearWise Console App...");
                    }
                    default -> System.out.println("Invalid option. Please try again.");
                }
            } catch (ConsoleInputEndedException e) {
                running = false;
                System.out.println("Console input ended. Exiting WearWise Console App...");
            } catch (Exception e) {
                System.out.println("Error: " + e.getMessage());
            }
        }

        scanner.close();
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

    private void createItem(Scanner scanner) {
        System.out.println();
        System.out.println("----- Create Clothing Item -----");

        String name = readRequiredText(scanner, "Name");
        String color = readRequiredText(scanner, "Color");
        ClothingCategory category = inputCategory(scanner);
        Season season = inputSeason(scanner);
        Style style = inputStyle(scanner);
        ClothingCondition condition = inputCondition(scanner);
        ClothingStatus status = inputStatus(scanner);
        Integer wearCount = readNonNegativeInteger(scanner, "Wear count: ");
        LocalDateTime lastWornAt = readOptionalDateTime(scanner, "Last worn at (yyyy-MM-ddTHH:mm, blank for none): ");
        Boolean favorite = readBoolean(scanner, "Favorite? true/false: ");

        ClothingItem createdItem = clothingItemService.createItem(
                CONSOLE_OWNER_USERNAME,
                name,
                color,
                null,
                category,
                season,
                style,
                condition,
                status,
                wearCount,
                lastWornAt,
                favorite,
                null
        );

        System.out.println("Created successfully!");
        printItem(createdItem);
    }

    private void showAllItems() {
        System.out.println();
        System.out.println("----- All Clothing Items -----");

        List<ClothingItem> items = clothingItemService.getAllItems(CONSOLE_OWNER_USERNAME);

        if (items.isEmpty()) {
            System.out.println("No clothing items found.");
            return;
        }

        items.forEach(this::printItem);
    }

    private void showItemById(Scanner scanner) {
        System.out.println();
        System.out.println("----- Find Clothing Item By ID -----");

        Long id = readPositiveLong(scanner, "Enter ID: ");
        ClothingItem item = clothingItemService.getItemById(CONSOLE_OWNER_USERNAME, id);
        printItem(item);
    }

    private void updateItem(Scanner scanner) {
        System.out.println();
        System.out.println("----- Update Clothing Item -----");

        Long id = readPositiveLong(scanner, "Enter ID to update: ");
        ClothingItem oldItem = clothingItemService.getItemById(CONSOLE_OWNER_USERNAME, id);

        System.out.println("Current item:");
        printItem(oldItem);

        String name = readRequiredText(scanner, "New name");
        String color = readRequiredText(scanner, "New color");
        ClothingCategory category = inputCategory(scanner);
        Season season = inputSeason(scanner);
        Style style = inputStyle(scanner);
        ClothingCondition condition = inputCondition(scanner);
        ClothingStatus status = inputStatus(scanner);
        Integer wearCount = readNonNegativeInteger(scanner, "New wear count: ");
        LocalDateTime lastWornAt = readOptionalDateTime(scanner, "New last worn at (yyyy-MM-ddTHH:mm, blank for none): ");
        Boolean favorite = readBoolean(scanner, "Favorite? true/false: ");

        ClothingItem updatedItem = clothingItemService.updateItem(
                CONSOLE_OWNER_USERNAME,
                id,
                name,
                color,
                oldItem.getColorTone(),
                category,
                season,
                style,
                condition,
                status,
                wearCount,
                lastWornAt,
                favorite,
                oldItem.getImageUrl()
        );

        System.out.println("Updated successfully!");
        printItem(updatedItem);
    }

    private void deleteItem(Scanner scanner) {
        System.out.println();
        System.out.println("----- Delete Clothing Item -----");

        Long id = readPositiveLong(scanner, "Enter ID to delete: ");
        clothingItemService.deleteItem(CONSOLE_OWNER_USERNAME, id);

        System.out.println("Deleted successfully!");
    }

    private void searchByName(Scanner scanner) {
        System.out.println();
        System.out.println("----- Search By Name -----");

        String keyword = readRequiredText(scanner, "Enter keyword");
        List<ClothingItem> items = clothingItemService.searchByName(CONSOLE_OWNER_USERNAME, keyword);

        if (items.isEmpty()) {
            System.out.println("No matching clothing items found.");
            return;
        }

        items.forEach(this::printItem);
    }

    private void filterByCategory(Scanner scanner) {
        System.out.println();
        System.out.println("----- Filter By Category -----");

        ClothingCategory category = inputCategory(scanner);
        List<ClothingItem> items = clothingItemService.filterByCategory(CONSOLE_OWNER_USERNAME, category);

        if (items.isEmpty()) {
            System.out.println("No clothing items found with category: " + category);
            return;
        }

        items.forEach(this::printItem);
    }

    private void showFavoriteItems() {
        System.out.println();
        System.out.println("----- Favorite Clothing Items -----");

        List<ClothingItem> items = clothingItemService.getFavoriteItems(CONSOLE_OWNER_USERNAME);

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

        while (true) {
            String option = readLine(scanner, "Category option: ");

            switch (option) {
                case "1" -> {
                    return ClothingCategory.SHIRT;
                }
                case "2" -> {
                    return ClothingCategory.PANTS;
                }
                case "3" -> {
                    return ClothingCategory.SHOES;
                }
                case "4" -> {
                    return ClothingCategory.JACKET;
                }
                case "5" -> {
                    return ClothingCategory.ACCESSORY;
                }
                default -> System.out.println("Invalid category option. Please enter a number from 1 to 5.");
            }
        }
    }

    private Season inputSeason(Scanner scanner) {
        System.out.println("Choose season:");
        System.out.println("1. SUMMER");
        System.out.println("2. WINTER");
        System.out.println("3. ALL_SEASON");

        while (true) {
            String option = readLine(scanner, "Season option: ");

            switch (option) {
                case "1" -> {
                    return Season.SUMMER;
                }
                case "2" -> {
                    return Season.WINTER;
                }
                case "3" -> {
                    return Season.ALL_SEASON;
                }
                default -> System.out.println("Invalid season option. Please enter a number from 1 to 3.");
            }
        }
    }

    private Style inputStyle(Scanner scanner) {
        System.out.println("Choose style:");
        System.out.println("1. CASUAL");
        System.out.println("2. FORMAL");
        System.out.println("3. STREETWEAR");
        System.out.println("4. SPORT");

        while (true) {
            String option = readLine(scanner, "Style option: ");

            switch (option) {
                case "1" -> {
                    return Style.CASUAL;
                }
                case "2" -> {
                    return Style.FORMAL;
                }
                case "3" -> {
                    return Style.STREETWEAR;
                }
                case "4" -> {
                    return Style.SPORT;
                }
                default -> System.out.println("Invalid style option. Please enter a number from 1 to 4.");
            }
        }
    }

    private ClothingCondition inputCondition(Scanner scanner) {
        System.out.println("Choose condition:");
        System.out.println("1. GOOD");
        System.out.println("2. DAMAGED");

        while (true) {
            String option = readLine(scanner, "Condition option: ");

            switch (option) {
                case "1" -> {
                    return ClothingCondition.GOOD;
                }
                case "2" -> {
                    return ClothingCondition.DAMAGED;
                }
                default -> System.out.println("Invalid condition option. Please enter 1 or 2.");
            }
        }
    }

    private ClothingStatus inputStatus(Scanner scanner) {
        System.out.println("Choose status:");
        System.out.println("1. AVAILABLE");
        System.out.println("2. LAUNDRY");
        System.out.println("3. UNAVAILABLE");

        while (true) {
            String option = readLine(scanner, "Status option: ");

            switch (option) {
                case "1" -> {
                    return ClothingStatus.AVAILABLE;
                }
                case "2" -> {
                    return ClothingStatus.LAUNDRY;
                }
                case "3" -> {
                    return ClothingStatus.UNAVAILABLE;
                }
                default -> System.out.println("Invalid status option. Please enter a number from 1 to 3.");
            }
        }
    }

    private String readMenuChoice(Scanner scanner) {
        while (true) {
            String choice = readLine(scanner, "Choose an option: ");

            switch (choice) {
                case "0", "1", "2", "3", "4", "5", "6", "7", "8" -> {
                    return choice;
                }
                default -> System.out.println("Invalid option. Please enter a number from 0 to 8.");
            }
        }
    }

    private String readRequiredText(Scanner scanner, String label) {
        while (true) {
            String value = readLine(scanner, label + ": ");

            if (value.isBlank()) {
                System.out.println(label + " cannot be blank.");
                continue;
            }

            if (value.length() > MAX_TEXT_LENGTH) {
                System.out.println(label + " must be at most " + MAX_TEXT_LENGTH + " characters.");
                continue;
            }

            return value;
        }
    }

    private Long readPositiveLong(Scanner scanner, String prompt) {
        while (true) {
            String input = readLine(scanner, prompt);

            try {
                long value = Long.parseLong(input);

                if (value > 0) {
                    return value;
                }
            } catch (NumberFormatException ignored) {
            }

            System.out.println("ID must be a positive whole number.");
        }
    }

    private Integer readNonNegativeInteger(Scanner scanner, String prompt) {
        while (true) {
            String input = readLine(scanner, prompt);

            try {
                int value = Integer.parseInt(input);

                if (value >= 0) {
                    return value;
                }
            } catch (NumberFormatException ignored) {
            }

            System.out.println("Value must be a non-negative whole number.");
        }
    }

    private LocalDateTime readOptionalDateTime(Scanner scanner, String prompt) {
        while (true) {
            String input = readLine(scanner, prompt);

            if (input.isBlank()) {
                return null;
            }

            try {
                LocalDateTime value = LocalDateTime.parse(input);

                if (!value.isAfter(LocalDateTime.now())) {
                    return value;
                }

                System.out.println("Last worn at cannot be in the future.");
            } catch (DateTimeParseException e) {
                System.out.println("Invalid date-time. Use format yyyy-MM-ddTHH:mm.");
            }
        }
    }

    private Boolean readBoolean(Scanner scanner, String prompt) {
        while (true) {
            String input = readLine(scanner, prompt).toLowerCase(Locale.ROOT);

            switch (input) {
                case "true", "t", "yes", "y", "1" -> {
                    return true;
                }
                case "false", "f", "no", "n", "0" -> {
                    return false;
                }
                default -> System.out.println("Please enter true/false, yes/no, or 1/0.");
            }
        }
    }

    private String readLine(Scanner scanner, String prompt) {
        System.out.print(prompt);

        if (!scanner.hasNextLine()) {
            throw new ConsoleInputEndedException();
        }

        return scanner.nextLine().trim();
    }

    private void printItem(ClothingItem item) {
        System.out.println("-----------------------------------");
        System.out.println("ID: " + item.getId());
        System.out.println("Name: " + item.getName());
        System.out.println("Color: " + item.getColor());
        System.out.println("Category: " + item.getCategory());
        System.out.println("Season: " + item.getSeason());
        System.out.println("Style: " + item.getStyle());
        System.out.println("Condition: " + item.getCondition());
        System.out.println("Status: " + item.getStatus());
        System.out.println("Wear Count: " + item.getWearCount());
        System.out.println("Last Worn At: " + item.getLastWornAt());
        System.out.println("Favorite: " + item.getFavorite());
        System.out.println("Created At: " + item.getCreatedAt());
        System.out.println("Updated At: " + item.getUpdatedAt());
    }

    private void ensureConsoleOwnerExists() {
        appUserRepository.findByUsername(CONSOLE_OWNER_USERNAME)
                .orElseGet(() -> appUserRepository.save(AppUser.builder()
                        .username(CONSOLE_OWNER_USERNAME)
                        .passwordHash("console-user-login-through-api")
                        .role("USER")
                        .build()));
    }

    private static class ConsoleInputEndedException extends RuntimeException {
    }
}
