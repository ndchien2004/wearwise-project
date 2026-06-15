package org.group7.wearwise.exception;

public class ClothingItemInUseException extends RuntimeException {

    public ClothingItemInUseException(Long id) {
        super("Clothing item with id " + id + " is used by an outfit and cannot be deleted.");
    }
}
