package ybbzbb.github.spider.core.util;

/**
 * @author yihua.huang@dianping.com
 */
public abstract class NumberUtils {

    public static final int NUM_0 = 0;
    public static final int NUM_1 = 1;
    public static final int NUM_100 = 100;
    public static final int NUM_3000 = 10;

    public static int compareLong(long o1, long o2) {
        if (o1 < o2) {
            return -1;
        } else if (o1 == o2) {
            return 0;
        } else {
            return 1;
        }
    }
}
