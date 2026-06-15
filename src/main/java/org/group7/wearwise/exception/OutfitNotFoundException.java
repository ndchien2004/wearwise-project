package org.group7.wearwise.exception;

public class OutfitNotFoundException extends RuntimeException {

    public OutfitNotFoundException(Long id) {
        super("Outfit not found with id: " + id);
    }
}
