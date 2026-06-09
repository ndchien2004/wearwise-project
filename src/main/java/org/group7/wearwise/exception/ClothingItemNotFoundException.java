package org.group7.wearwise.exception;

public class ClothingItemNotFoundException extends RuntimeException {

    public ClothingItemNotFoundException(Long id) {
        super("Clothing item not found with id: " + id);
    }
}
