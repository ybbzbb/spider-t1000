package ybbzbb.github.spider.core.util;

/**
 * @Description: 工具类，可以用于封装两个对象用
 * @return
 * @throws
 * @date 2019/5/13 14:02
 */
public class Tuple<K, V> {

    public final K _1;
    public final V _2;

    public Tuple(K k, V v) {
        this._1 = k;
        this._2 = v;
    }

    public static <K, V> Tuple<K, V> createTuple(K k, V v) {
        return new Tuple<K, V>(k, v);
    }

    @Override
    public String toString() {
        return "Tuple [_1=" + _1 + ", _2=" + _2 + "]";
    }

}
