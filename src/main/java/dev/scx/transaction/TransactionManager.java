package dev.scx.transaction;

import dev.scx.function.Function0;
import dev.scx.function.Function0Void;
import dev.scx.function.Function1;
import dev.scx.function.Function1Void;
import dev.scx.transaction.exception.TransactionException;

/// TransactionManager
///
/// 事务管理器.
///
/// 该接口负责两件事:
/// 1. 通过 [#begin()] 开始一笔新的活动事务.
/// 2. 通过 [#with(Transaction, Function0)] 在给定事务绑定的执行作用域内执行一段同步逻辑.
///
/// 这里的 "管理" 并不表示自动提交, 自动回滚或自动关闭;
/// 它只表示:
/// - 负责开始事务
/// - 负责建立 "当前执行作用域与某笔事务之间" 的绑定关系
///
/// 因此, 本接口的核心抽象是:
/// 1. 事务对象 [Transaction] 独立存在.
/// 2. 某段代码是否参与这笔事务, 不由 "是否拿到对象" 决定,
///    而由是否通过 [#with(Transaction, Function0)] 建立当前事务绑定决定.
///
/// 重要说明:
/// 进入某笔事务的执行作用域, 并不意味着该作用域内的所有操作都会自动参与该事务.
/// 对于某个具体操作而言, 是否参与当前事务, 取决于该操作是否与该事务处于兼容的资源管理域中.
/// 兼容则参与该事务;
/// 不兼容则应忽略当前事务, 并按其原有方式执行.
///
/// 为了便于理解, 下文有时会以 repository 作为示例.
/// 这些示例仅用于说明事务传播与作用域绑定的心智模型,
/// 并不表示本接口只适用于 repository 风格的数据访问抽象.
///
/// 典型使用方式:
///
/// ```java
/// try (var tx = transactionManager.begin()) {
///     transactionManager.with(tx, () -> {
///         repo1.update(...);
///         repo2.delete(...);
///     });
///     tx.commit();
/// }
/// ```
///
/// 上例中:
/// 1. [#begin()] 真正开始一笔新事务.
/// 2. [#with(Transaction, Function0)] 仅负责在 handler 执行期间, 将 `tx` 绑定为当前事务.
/// 3. handler 返回后, 只会解除绑定, 不会自动提交, 回滚或关闭 `tx`.
/// 4. 是否提交或回滚, 由调用者显式决定.
///
/// 资源域兼容性约定:
/// 1. 一笔事务只应对与其兼容, 且位于同一资源管理域中的操作生效.
/// 2. 事务不应改变某个具体执行方原本的底层资源归属.
/// 3. 也就是说, 事务的作用是 "让本来就处于同一资源域中的操作共享同一个事务上下文",
///    而不是 "把某个执行方改绑到另一资源管理域或另一底层实现上去".
/// 4. 本接口不承诺跨不兼容资源域的统一事务语义.
///
/// 关于不兼容操作的行为:
/// 1. 应当忽略当前事务, 使其继续按原有方式执行.
/// 2. 不得因当前事务而静默重定向到另一资源域.
///
/// 关于执行范围:
/// 1. [#with(Transaction, Function0)] 默认只保证当前同步执行链中的事务绑定语义.
/// 2. 本接口不默认承诺异步任务, 跨线程任务或延迟执行逻辑会自动继承当前事务绑定.
///
/// 关于嵌套作用域:
/// 1. 支持嵌套 [#with(Transaction, Function0)], 内层作用域结束后, 应恢复外层原有事务绑定.
///
/// @param <T> 事务类型
/// @author scx567888
public interface TransactionManager<T extends Transaction> {

    /// 开始一笔新的活动事务.
    ///
    /// 调用该方法后, 返回的事务在语义上已经开始,
    /// 但它尚未自动参与任何具体执行;
    /// 若希望某段代码在该事务中运行, 应通过 [#with(Transaction, Function0Void)] 显式建立作用域绑定.
    ///
    /// 语义要求:
    /// 1. [#begin()] 返回的事务应处于 "活动中且未完成" 的状态.
    /// 2. [#begin()] 返回的事务应归属于当前事务管理器所管理的资源域.
    /// 3. 调用者有责任在适当时机对返回的事务执行 [Transaction#commit()], [Transaction#rollback()] 或 [Transaction#close()].
    ///
    /// @return 一笔新的活动事务
    /// @throws TransactionException 当底层事务开始失败时抛出
    T begin() throws TransactionException;

    /// 在给定事务绑定的执行作用域内执行 handler, 并返回其结果.
    ///
    /// 该方法只负责 "作用域绑定" , 不负责 "事务定案".
    ///
    /// 具体来说:
    /// 1. 在 handler 执行期间, 将给定事务 `tx` 绑定为当前事务.
    /// 2. handler 执行结束（无论正常返回还是抛异常）后, 解除该绑定.
    /// 3. 该方法不会自动提交, 不会自动回滚, 也不会自动关闭该事务.
    ///
    /// 需要注意:
    /// 进入该作用域并不意味着 handler 内的所有操作都必然参与 `tx`.
    /// 对某个具体操作而言, 是否参与该事务,
    /// 仍取决于该操作与 `tx` 的资源域兼容性.
    ///
    /// 因此, 下面两段逻辑在事务语义上是等价的:
    ///
    /// 显式传参模型:
    /// ```java
    /// var tx = begin();
    /// // 这里并不要求真实存在这种风格的 API, 仅用于说明事务传播的心智模型.
    /// repo1.update(tx, ...);
    /// repo2.delete(tx, ...);
    /// tx.commit();
    /// ```
    ///
    /// 作用域绑定模型:
    /// ```java
    /// var tx = begin();
    /// with(tx, () -> {
    ///     repo1.update(...);
    ///     repo2.delete(...);
    /// });
    /// tx.commit();
    /// ```
    ///
    /// 区别只在于事务传播方式:
    /// - 前者通过参数传播
    /// - 后者通过作用域传播
    ///
    /// 资源域约定:
    /// 1. 与 `tx` 兼容的操作可参与该事务.
    /// 2. 不兼容的操作不应因该事务而改变其原有资源解析与执行方式.
    /// 3. 本方法不提供跨不兼容资源域的统一事务保证.
    ///
    /// 使用者责任:
    /// 1. 该方法返回后, 调用者仍应决定是否 [Transaction#commit()], [Transaction#rollback()] 或 [Transaction#close()].
    ///
    /// @param tx      给定事务, 应当来自当前 TransactionManager
    /// @param handler 要在该事务绑定作用域内执行的逻辑
    /// @param <R>     返回值类型
    /// @param <X>     handler 可能抛出的异常类型
    /// @return handler 的返回值
    /// @throws X handler 抛出的异常将原样向外传播
    <R, X extends Throwable> R with(T tx, Function0<R, X> handler) throws X;

    /// 在给定事务绑定的执行作用域内执行 handler.
    ///
    /// 该方法与 [#with(Transaction, Function0Void)] 的语义完全一致,
    /// 唯一差别是 handler 无返回值.
    ///
    /// 语义要求:
    /// 1. 仅在 handler 执行期间绑定当前事务.
    /// 2. handler 结束后解除绑定.
    /// 3. 不自动提交, 回滚或关闭事务.
    ///
    /// 需要注意:
    /// 进入该作用域并不意味着 handler 内的所有操作都必然参与 `tx`;
    /// 具体是否参与, 仍取决于对应操作与 `tx` 的资源域兼容性.
    ///
    /// @param tx      给定事务, 应当来自当前 TransactionManager
    /// @param handler 要在该事务绑定作用域内执行的逻辑
    /// @param <X>     handler 可能抛出的异常类型
    /// @throws X handler 抛出的异常将原样向外传播
    <X extends Throwable> void with(T tx, Function0Void<X> handler) throws X;

    /// 创建一个新事务, 并将该事务交给 handler.
    ///
    /// - 手动定案
    /// - 自动收尾
    ///
    /// 该方法会:
    /// 1. 创建一笔新的活动事务.
    /// 2. 在该事务绑定的执行作用域内执行 handler.
    /// 3. 在 handler 结束后按事务自身语义进行最终收尾.
    ///
    /// 该方法不会自动提交或自动回滚事务;
    /// 事务是否完成, 由 handler 显式决定.
    /// 若 handler 返回前未显式完成事务, 则会在 [Transaction#close()] 时按事务自身语义收尾.
    ///
    /// 需要注意:
    /// handler 内的具体操作是否参与该事务,
    /// 仍取决于对应操作与该事务的资源域兼容性.
    ///
    /// @param handler 接收新事务并对其进行手动控制的逻辑
    /// @param <R>     返回值类型
    /// @param <X>     handler 可能抛出的异常类型
    /// @return handler 的返回值
    /// @throws TransactionException 当事务开始或最终收尾失败时抛出
    /// @throws X                    handler 抛出的异常将原样向外传播
    default <R, X extends Throwable> R withTransaction(Function1<T, R, X> handler) throws TransactionException, X {
        try (var tx = begin()) {
            return with(tx, () -> handler.apply(tx));
        }
    }

    /// 创建一个新事务, 并将该事务交给 handler.
    ///
    /// 该方法与 [#withTransaction(Function1)] 的语义完全一致,
    /// 唯一差别是 handler 无返回值.
    ///
    /// @param handler 接收新事务并对其进行手动控制的逻辑
    /// @param <X>     handler 可能抛出的异常类型
    /// @throws TransactionException 当事务开始或最终收尾失败时抛出
    /// @throws X                    handler 抛出的异常将原样向外传播
    default <X extends Throwable> void withTransaction(Function1Void<T, X> handler) throws TransactionException, X {
        try (var tx = begin()) {
            with(tx, () -> handler.apply(tx));
        }
    }

    /// 创建一个新事务, 在该事务绑定的执行作用域内执行 handler,
    /// 并按默认策略自动定案该事务.
    ///
    /// 默认策略为:
    /// 1. handler 正常返回时, 自动尝试提交事务.
    /// 2. handler 抛出异常时, 自动尝试回滚事务.
    ///
    /// 该方法是一个便利封装;
    /// 它表达的是一种默认事务控制策略, 而不是比 [#withTransaction(Function1)] 更底层的能力.
    ///
    /// 需要注意:
    /// handler 内的具体操作是否参与该事务,
    /// 仍取决于对应操作与该事务的资源域兼容性.
    ///
    /// @param handler 要在新事务作用域内执行的逻辑
    /// @param <R>     返回值类型
    /// @param <X>     handler 可能抛出的异常类型
    /// @return handler 的返回值
    /// @throws TransactionException 当事务开始, 回滚, 提交或最终收尾失败时抛出
    /// @throws X                    handler 抛出的异常将原样向外传播
    default <R, X extends Throwable> R autoTransaction(Function0<R, X> handler) throws TransactionException, X {
        try (var tx = begin()) {
            R result;
            try {
                result = with(tx, handler);
            } catch (Throwable e) {
                try {
                    tx.rollback();
                } catch (TransactionException rb) {
                    e.addSuppressed(rb);
                }
                throw e;
            }
            tx.commit();
            return result;
        }
    }

    /// 创建一个新事务, 在该事务绑定的执行作用域内执行 handler,
    /// 并按默认策略自动定案该事务.
    ///
    /// 该方法与 [#autoTransaction(Function0)] 的语义完全一致,
    /// 唯一差别是 handler 无返回值.
    ///
    /// @param handler 要在新事务作用域内执行的逻辑
    /// @param <X>     handler 可能抛出的异常类型
    /// @throws TransactionException 当事务开始, 回滚, 提交或最终收尾失败时抛出
    /// @throws X                    handler 抛出的异常将原样向外传播
    default <X extends Throwable> void autoTransaction(Function0Void<X> handler) throws TransactionException, X {
        try (var tx = begin()) {
            try {
                with(tx, handler);
            } catch (Throwable e) {
                try {
                    tx.rollback();
                } catch (TransactionException rb) {
                    e.addSuppressed(rb);
                }
                throw e;
            }
            tx.commit();
        }
    }

}
