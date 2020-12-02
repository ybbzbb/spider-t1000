package ybbzbb.github.spider.core.model;

import ybbzbb.github.spider.core.model.formatter.ObjectFormatter;
import ybbzbb.github.spider.core.selector.Selector;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Wrapper of field and extractor.
 * @author code4crafter@gmail.com <br>
 * @since 0.2.0
 */
public class FieldExtractor extends Extractor {

    private final Field field;

    private Method setterMethod;

    private ObjectFormatter objectFormatter;

    public FieldExtractor(Field field, Selector selector, Source source, boolean notNull, boolean multi) {
        super(selector, source, notNull, multi);
        this.field = field;
    }

    public Field getField() {
        return field;
    }

    public Selector getSelector() {
        return selector;
    }

    public Source getSource() {
        return source;
    }

    public void setSetterMethod(Method setterMethod) {
        this.setterMethod = setterMethod;
    }

    public Method getSetterMethod() {
        return setterMethod;
    }

    public boolean isNotNull() {
        return notNull;
    }

    public ObjectFormatter getObjectFormatter() {
        return objectFormatter;
    }

    public void setObjectFormatter(ObjectFormatter objectFormatter) {
        this.objectFormatter = objectFormatter;
    }
}
