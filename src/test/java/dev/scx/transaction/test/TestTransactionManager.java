package dev.scx.transaction.test;

import dev.scx.function.Function0;
import dev.scx.function.Function0Void;
import dev.scx.transaction.TransactionManager;
import dev.scx.transaction.exception.TransactionException;

public class TestTransactionManager implements TransactionManager<TestTransaction> {

    @Override
    public TestTransaction begin() throws TransactionException {
        return new TestTransaction();
    }

    @Override
    public <R, X extends Throwable> R with(TestTransaction tx, Function0<R, X> handler) throws X {
        return null;
    }

    @Override
    public <X extends Throwable> void with(TestTransaction tx, Function0Void<X> handler) throws X {

    }

}
