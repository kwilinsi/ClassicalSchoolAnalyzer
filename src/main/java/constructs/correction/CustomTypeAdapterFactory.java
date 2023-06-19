package constructs.correction;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import constructs.correction.schoolCorrection.Action;
import constructs.correction.schoolCorrection.ChangeAttributesAction;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * This was written almost entirely by ChatGPT. I sorta know how it works.
 */
public class CustomTypeAdapterFactory implements TypeAdapterFactory {
    private final Map<Class<?>, Class<?>> typeAdapters;

    public CustomTypeAdapterFactory(Map<Class<?>, Class<?>> typeAdapters) {
        this.typeAdapters = typeAdapters;
    }

    public CustomTypeAdapterFactory() {
        this(new HashMap<>());
        typeAdapters.put(Action.class, ChangeAttributesAction.class);
    }

    @Override
    public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> typeToken) {
        Class<? super T> rawType = typeToken.getRawType();

        // Check if the type being deserialized is an interface
        if (rawType.isInterface() && typeAdapters.containsKey(rawType)) {
            final Class<?> implementationClass = typeAdapters.get(rawType);

            // Create a type adapter for the implementation class
            final TypeAdapter<?> delegateAdapter =
                    gson.getDelegateAdapter(this, TypeToken.get(implementationClass));

            // Cast the delegate adapter to the interface type
            @SuppressWarnings("unchecked") final TypeAdapter<T> castDelegateAdapter = (TypeAdapter<T>) delegateAdapter;

            return new TypeAdapter<>() {
                @Override
                public void write(JsonWriter out, T value) throws IOException {
                    castDelegateAdapter.write(out, value);
                }

                @Override
                public T read(JsonReader in) throws IOException {
                    return castDelegateAdapter.read(in);
                }
            };
        }

        return null;
    }
}
