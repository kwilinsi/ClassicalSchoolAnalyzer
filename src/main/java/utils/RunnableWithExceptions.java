package utils;

/**
 * As the name implies, this is a {@link Runnable} that can throw checked exceptions, thereby avoiding a
 * <code>try-catch</code> in code that provides this to a method.
 * <p>
 * For an example, see {@link Utils#runParallel(RunnableWithExceptions, String, String)}.
 */
@FunctionalInterface
public interface RunnableWithExceptions {
    void run() throws Exception;
}
