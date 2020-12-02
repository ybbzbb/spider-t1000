package ybbzbb.github.spider.core.util;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.Getter;

import java.lang.reflect.Type;
import java.text.MessageFormat;

/**
 * @version 1.0.0
 * @Author Ray
 * @date 2018/12/10 17:44
 */
@Getter
public enum GsonUtil {

    SINGLE(new Gson()),
    HTML(new GsonBuilder().disableHtmlEscaping().create()),
    LOWER_CASE(new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create()),

    /**
     * 格式化输出
     */
    PRETTY_JSON(new GsonBuilder().setPrettyPrinting().create());

    private Gson gson;

    GsonUtil(Gson gson) {
        this.gson = gson;
    }

    public Gson getGson() {
        return gson;
    }

    public static void dumper(Object object, String flag) {
        String info = MessageFormat.format(
                "\n▼▼▼▼▼▼▼▼▼▼▼▼▼▼▼▼▼▼{0}▼▼▼▼▼▼▼▼▼▼▼▼▼▼▼▼▼▼" +
                        "\n{1}" +
                        "\n▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲{0}▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲",
                flag, PRETTY_JSON.gson.toJson(object));
    }

    public static void dumper(Object object) {
        dumper(object, "");
    }

    public static String toJson(Object object) {
        return SINGLE.getGson().toJson(object);
    }

    public static <T> T from(String json, Type type) {
        return GsonUtil.SINGLE.getGson().fromJson(json, type);
    }

}
