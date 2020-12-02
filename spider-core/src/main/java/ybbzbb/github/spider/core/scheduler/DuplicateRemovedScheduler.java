package ybbzbb.github.spider.core.scheduler;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;
import ybbzbb.github.spider.core.Request;
import ybbzbb.github.spider.core.Site;
import ybbzbb.github.spider.core.Task;
import ybbzbb.github.spider.core.scheduler.component.DuplicateRemover;
import ybbzbb.github.spider.core.scheduler.component.HashSetDuplicateRemover;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import  ybbzbb.github.spider.core.util.UrlUtils;

/**
 * Remove duplicate urls and only push urls which are not duplicate.<br><br>
 *
 * @author code4crafer@gmail.com
 * @since 0.5.0
 */
public abstract class DuplicateRemovedScheduler implements Scheduler {

    protected Logger logger = LoggerFactory.getLogger(DuplicateRemovedScheduler.class);

    private DuplicateRemover duplicatedRemover = new HashSetDuplicateRemover();

    public DuplicateRemover getDuplicateRemover() {
        return duplicatedRemover;
    }

    public DuplicateRemovedScheduler setDuplicateRemover(DuplicateRemover duplicatedRemover) {
        this.duplicatedRemover = duplicatedRemover;
        return this;
    }

    @Override
    public void push(Request request, Task task) {
        if (StringUtils.isBlank(request.getUrl())) {
            return;
        }

        logger.debug("get a candidate url {}", request.getUrl());

        Task cloneTask = cloneTask(task , request.getSite());
        if (UrlUtils.isUrl(request.getUrl()) && !duplicatedRemover.isDuplicate(request, cloneTask)) {
            pushWhenNoDuplicate(request, cloneTask);
        }

    }

    @Override
    public void pushAll(List<Request> requests, Task task) {

        if (CollectionUtils.isEmpty(requests)){
            return;
        }

        final Set<String> keys = new HashSet<>(requests.size());

        final Map<String, List<Request>> sites
                = requests.stream()
                //空处理
                .filter(e -> StringUtils.isNotBlank(e.getUrl()))
                .filter( e -> UrlUtils.isUrl(e.getUrl()))
                //内部重复过滤
                .filter( e -> keys.add(e.getUrl().trim()))
                .collect(Collectors.groupingBy(Request::getSite));

        sites.entrySet().stream()
                .filter( e -> e.getValue() != null && e.getValue().size() > 0)
                .forEach( e -> {
                    Task cloneTask = cloneTask(task , e.getKey());

                    final List<Boolean> duplicates = duplicatedRemover.isDuplicates(e.getValue(), cloneTask);

                    if (e.getValue().size() != duplicates.size()) {
                        logger.error( "处理任务数据 与处理数据不相同 ");
                    }

                    for (int i = 0; i < duplicates.size(); i++) {
                        e.getValue().get(i).setRepeat(duplicates.get(i));

                        logger.info("push to queue {} {}", !duplicates.get(i) , e.getValue().get(i).getUrl());
                    }

                    pushWhenNoDuplicate(
                            e.getValue().stream()
                                    .filter(request -> !request.isRepeat())
                                    .collect(Collectors.toList())
                            , cloneTask);

                    logger.info("site {} init task over", e.getKey() );

        });

    }

    /**
    * @Description: 重新放入查询队列
    * @Param:
    * @return:
    * @Author: Ray
    * @Date: 2018/12/4
    */
    @Override
    public void rePush(Request request, Task task) {
        rePush(request , task ,null);
    }

    @Override
    public void rePush(Request request, Task task, String firstKey) {
        logger.info("rePush to queue {}", request.getUrl());
        if (!UrlUtils.isUrl(request.getUrl())) {
            return;
        }

        request.setRepush(true);
        pushWhenNoDuplicate(request, cloneTask(task , request.getSite()) , firstKey);
    }

    public Task cloneTask(Task task , String site){

        final Task nowTask = new Task() {
            @Override
            public String getUUID() {
                return task.getUUID();
            }

            @Override
            public Site getSite() {
                Site me = Site.me();
                me.setSiteName(site);
                return me;
            }
        };

        return nowTask;

    }


    protected void pushWhenNoDuplicate(List<Request> request, Task task) {
    }

    protected void pushWhenNoDuplicate(Request request, Task task) {
    }

    protected void pushWhenNoDuplicate(Request request, Task task ,String firstKey) {
    }
}
