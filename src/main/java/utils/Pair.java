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

    private Pair(A a, B b) {
        this.a = a;
        this.b = b;
    }

    /**
     * Create a new {@link Pair}.
     *
     * @param a   See {@link #a}.
     * @param b   See {@link #b}.
     * @param <A> The type of <code>a</code>.
     * @param <B> The type of <code>b</code>.
     * @return The new pair.
     */
    public static <A, B> Pair<A, B> of(A a, B b) {
        return new Pair<>(a, b);
    }
}
