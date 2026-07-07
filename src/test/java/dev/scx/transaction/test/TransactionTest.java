package dev.scx.transaction.test;

import dev.scx.transaction.exception.TransactionException;
import org.testng.annotations.Test;

public class TransactionTest {

    public static void main(String[] args) throws TransactionException {
        test1();
    }

    @Test
    public static void test1() throws TransactionException {

        var m = new TestTransactionManager();

        // 方式1
        try (var tx = m.begin()) {
            m.with(tx, () -> {
                // 查询 或者 修改
            });
            tx.commit();
        }

        // 方式2
        m.withTransaction(tx -> {
            // 查询 或者 修改
            tx.commit();
        });

        // 方式3
        m.autoTransaction(() -> {
            // 查询 或者 修改
        });

    }

}
