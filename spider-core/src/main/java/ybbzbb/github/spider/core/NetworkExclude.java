package ybbzbb.github.spider.core;

import ybbzbb.github.spider.core.Request;

/**
 * 排除一些不需要处理的url
 * */
public interface NetworkExclude {

    public boolean exclude(Request request);
}
