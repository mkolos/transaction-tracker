package com.kolosh.transactiontracker.service;

public final class Exceptions {

    private Exceptions() {
    }

    public static class NotFoundException extends RuntimeException {
        public NotFoundException(String message) {
            super(message);
        }
    }

    public static class InvalidRequestException extends RuntimeException {
        public InvalidRequestException(String message) {
            super(message);
        }
    }

    public static class UnknownCategoryException extends RuntimeException {
        public UnknownCategoryException(long categoryId) {
            super("Category " + categoryId + " does not exist");
        }
    }

    public static class DuplicateCategoryException extends RuntimeException {
        public DuplicateCategoryException(String name) {
            super("Category '" + name + "' already exists");
        }
    }
}
