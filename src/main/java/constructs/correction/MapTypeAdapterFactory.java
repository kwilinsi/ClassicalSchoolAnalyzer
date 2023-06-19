package constructs.correction;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * This was written almost entirely by ChatGPT. I sorta know how it works.
 */
public class MapTypeAdapterFactory implements TypeAdapterFactory {
    private static final Logger logger = LoggerFactory.getLogger(MapTypeAdapterFactory.class);

    @Override
    @SuppressWarnings("unchecked")
    public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> typeToken) {
        logger.info("Is this running??");

        if (Map.class.isAssignableFrom(typeToken.getRawType()))
            return (TypeAdapter<T>) new MapClassTypeAdapter();

        return null;
    }

    private static class MapClassTypeAdapter extends TypeAdapter<Map<Class<?>, Class<?>>> {

        MapClassTypeAdapter() {
        }

        @Override
        public void write(JsonWriter out, Map<Class<?>, Class<?>> map) throws IOException {
            out.beginObject();
            for (Map.Entry<Class<?>, Class<?>> entry : map.entrySet()) {
                out.name(entry.getKey().getName());
                out.value(entry.getValue().getName());
                //gson.getAdapter(Class.class).write(out, entry.getValue());
            }
            out.endObject();
        }

        @Override
        public Map<Class<?>, Class<?>> read(JsonReader in) throws IOException {
            Map<Class<?>, Class<?>> map = new HashMap<>();
            in.beginObject();
            while (in.hasNext()) {
                String key = in.nextName();
                try {
                    Class<?> keyValue = Class.forName(key);
                    Class<?> value = Class.forName(in.nextString());
                    map.put(keyValue, value);
                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                }
            }
            in.endObject();
            return map;
        }
    }
}
