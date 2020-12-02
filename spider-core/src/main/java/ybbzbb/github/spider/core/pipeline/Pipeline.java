package ybbzbb.github.spider.core.pipeline;


import ybbzbb.github.spider.core.ResultItems;
import ybbzbb.github.spider.core.Task;

public interface Pipeline {

    /**
     * Process extracted results.
     *
     * @param resultItems resultItems
     * @param task task
     */
    public void process(ResultItems resultItems, Task task);
}
