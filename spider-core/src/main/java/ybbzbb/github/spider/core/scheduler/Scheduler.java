package ybbzbb.github.spider.core.scheduler;


import ybbzbb.github.spider.core.Request;
import ybbzbb.github.spider.core.Site;
import ybbzbb.github.spider.core.Task;

import java.util.List;

public interface Scheduler {

    /**
     * add a url to fetch
     *
     * @param request request
     * @param task task
     */
    void push(Request request, Task task);

    /**
     * add are urls to fetch
     *
     * @param request request
     * @param task task
     */
    void pushAll(List<Request> request, Task task);

    void rePush(Request request, Task task);

    void rePush(Request request, Task task, String firstKey);

    /**
     * get an url to crawl
     *
     * @param task the task of spider
     * @return the url to crawl
     */
    Request poll(Task task);

    Request poll(Task task, String firstSite, String excludeSite);

    void pushToFailQueue(Request request, Task task);

    void pushCloseQueue(Request request, Task task);

    void cleanRequest(Request request, Task task);

}
