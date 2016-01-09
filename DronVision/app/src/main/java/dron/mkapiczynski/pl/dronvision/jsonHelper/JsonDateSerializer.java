package dron.mkapiczynski.pl.dronvision.jsonHelper;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.Date;

public class JsonDateSerializer implements JsonSerializer<Date>{
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

    public JsonElement serialize(Date date, Type typeOfSrc, JsonSerializationContext context){
        return new JsonPrimitive(dateFormat.format(date));
    }
}
