package dev.scx.transaction.test;

import dev.scx.transaction.Transaction;
import dev.scx.transaction.exception.TransactionException;

public class TestTransaction implements Transaction {

    @Override
    public void commit() throws TransactionException {

    }

    @Override
    public void rollback() throws TransactionException {

    }

    @Override
    public void close() throws TransactionException {

    }

}
