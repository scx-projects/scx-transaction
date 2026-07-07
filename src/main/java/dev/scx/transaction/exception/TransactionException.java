package dev.scx.transaction.exception;

/// TransactionException
///
/// @author scx567888
public class TransactionException extends RuntimeException {

    public TransactionException(String message) {
        super(message);
    }

    public TransactionException(Throwable cause) {
        super(cause);
    }

    public TransactionException(String message, Throwable cause) {
        super(message, cause);
    }

}
