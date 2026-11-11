
package ca.sheridancollege.shtirthb.exception;

public final class AppExceptions {

    private AppExceptions() {
        // Prevent instantiation.
    }

    public abstract static class AppBaseException
            extends RuntimeException {

        protected AppBaseException(String message) {
            super(message);
        }

        protected AppBaseException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class DuplicateEmailException
            extends AppBaseException {

        public DuplicateEmailException(String email) {
            super("An account with this email already exists.");
        }
    }

    public static class ResourceNotFoundException
            extends AppBaseException {

        public ResourceNotFoundException(String resource) {
            super(resource + " not found.");
        }
    }

    public static class ValidationException
            extends AppBaseException {

        public ValidationException(String message) {
            super(message);
        }
    }

    public static class BusinessRuleException
            extends AppBaseException {

        public BusinessRuleException(String message) {
            super(message);
        }
    }

    public static class ConflictException
            extends AppBaseException {

        public ConflictException(String message) {
            super(message);
        }
    }

    public static class InvalidCredentialsException
            extends AppBaseException {

        public InvalidCredentialsException() {
            super("Invalid email or password.");
        }
    }

    public static class AccountLockedException
            extends AppBaseException {

        public AccountLockedException() {
            super("Admin account is temporarily locked. Please use the password reset option.");
        }
    }

    public static class ServiceUnavailableException
            extends AppBaseException {

        public ServiceUnavailableException(String message) {
            super(message);
        }

        public ServiceUnavailableException(
                String message, Throwable cause) {
            super(message, cause);
        }
    }
}
