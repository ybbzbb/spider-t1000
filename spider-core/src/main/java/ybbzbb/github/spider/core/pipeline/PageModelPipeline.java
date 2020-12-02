package ybbzbb.github.spider.core.pipeline;


import ybbzbb.github.spider.core.Task;

/**
 * Implements PageModelPipeline to persistent your page model.
 *
 * @author code4crafter@gmail.com <br>
 * @since 0.2.0
 */
public interface PageModelPipeline<T> {

    public void process(T t, Task task);

}
