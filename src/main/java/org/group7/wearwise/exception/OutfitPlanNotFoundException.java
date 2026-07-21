package org.group7.wearwise.exception;

public class OutfitPlanNotFoundException extends RuntimeException {

    public OutfitPlanNotFoundException(Long id) {
        super("Outfit plan not found with ID: " + id);
    }
}
