package utils;

/**
 * A helpful way to return two values from a method.
 *
 * @param <A> The type of the first value.
 * @param <B> The type of the second value.
 */
public class Pair<A, B> {
    /**
     * The first value.
     */
    public final A a;

    /**
     * The second value.
     */
    public final B b;

    /**
     * Create a new {@link Pair}.
     * @param a See {@link #a}.
     * @param b See {@link #b}.
     */
    public Pair(A a, B b) {
        this.a = a;
        this.b = b;
    }
}
