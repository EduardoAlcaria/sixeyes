package com.sixeyes.exception;

public class InsufficientStorageException extends RuntimeException {

    public InsufficientStorageException(String path, double availableGb, double requiredGb) {
        super(String.format(
                "Not enough free space on '%s': %.2f GB available, %.2f GB required",
                path, availableGb, requiredGb
        ));
    }
}
