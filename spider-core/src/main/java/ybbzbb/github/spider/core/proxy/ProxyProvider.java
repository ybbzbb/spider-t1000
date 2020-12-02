package ybbzbb.github.spider.core.proxy;

import ybbzbb.github.spider.core.Page;
import ybbzbb.github.spider.core.Task;

import java.util.List;

/**
 * Proxy provider. <br>
 *     
 * @since 0.7.0
 */
public interface ProxyProvider {

    /**
     *
     * Return proxy to Provider when complete a download.
     * @param proxy the proxy config contains host,port and identify info
     * @param page the download result
     * @param task the download task
     */
    void returnProxy(Proxy proxy, Page page, Task task);

    /**
     * Get a proxy for task by some strategy.
     * @param task the download task
     * @return proxy 
     */
    Proxy getProxy(Task task);

    /**
     * 保留ＩＰ
     * */
    void retainIp(List<Proxy> array);

    /**
     * 保留ＩＰ
     * */
    void retainIp(String domain, List<Proxy> array);

    /**
     * IP数量长度
     * */
    int IpCount(String domain);
}
