package ybbzbb.github.spider.core.scheduler;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.Pipeline;
import ybbzbb.github.spider.core.Request;
import ybbzbb.github.spider.core.Task;
import ybbzbb.github.spider.core.util.GsonUtil;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

/**
 * the redis scheduler with priority
 *
 * @author sai
 * Created by sai on 16-5-27.
 */
public class RedisPriorityScheduler extends RedisScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(RedisPriorityScheduler.class);

    private static final String ZSET_PREFIX = "zset_";

    private static final String QUEUE_PREFIX = "queue_";

    private static final String NO_PRIORITY_SUFFIX = "_zore";

    private static final String PLUS_PRIORITY_SUFFIX = "_plus";

    private static final String MINUS_PRIORITY_SUFFIX = "_minus";

    public RedisPriorityScheduler(String host) {
        super(host);
    }

    private TaskPriority taskPriority;

    public RedisPriorityScheduler(JedisPool pool) {
        super(pool);
    }


    private static class PushThread implements Runnable{

        private List<Request> requests;
        private Task task;
        private RedisPriorityScheduler redisScheduler;
        private JedisPool pool;
        private CountDownLatch latch;

        public PushThread(List<Request> requests , Task task , RedisPriorityScheduler redisScheduler , JedisPool pool , CountDownLatch latch){
            this.requests = requests;
            this.task = task;
            this.redisScheduler = redisScheduler;
            this.pool = pool;
            this.latch = latch;
        }

        @Override
        public void run() {
            if (CollectionUtils.isEmpty(requests)) return;

            try (final Jedis jedis = pool.getResource()) {

                final Pipeline pipelined = jedis.pipelined();

                requests.stream().forEach(request -> {
                    String key = request.getUrl();

                    long priority = request.getPriority();

                    switch ((int) priority) {
                        case -1:
                            pipelined.rpush(redisScheduler.getZsetMinusPriorityKey(task), key);
                            break;
                        case 1:
                            pipelined.rpush(redisScheduler.getZsetPlusPriorityKey(task), key);
                            break;
                        default:
                            pipelined.rpush(redisScheduler.getQueueNoPriorityKey(task), key);
                    }

                    String field = DigestUtils.sha1Hex(key);
                    String value = GsonUtil.toJson(request);
                    pipelined.hset(redisScheduler.getItemKey(task), field, value);

                });

                requests.size();

                pipelined.sync();

                latch.countDown();
            }

        }
    }


    private static final int THREAD_GROUP_COUNT = 3000;

    @Override
    protected void pushWhenNoDuplicate(List<Request> requests, Task task)  {

        if (CollectionUtils.isEmpty(requests)) return;

        int count = requests.size() % THREAD_GROUP_COUNT == 0 ? requests.size() / THREAD_GROUP_COUNT : requests.size() / THREAD_GROUP_COUNT + 1;
        final CountDownLatch latch = new CountDownLatch(count);
        for (int i = 0 ; i < count ; i++) {
            final List<Request> lRequests = requests.subList(i * THREAD_GROUP_COUNT
                    , i * THREAD_GROUP_COUNT + THREAD_GROUP_COUNT > requests.size() ? requests.size() : i * THREAD_GROUP_COUNT + THREAD_GROUP_COUNT);
            new Thread(new PushThread(lRequests,task , this, pool , latch)).start();
            LOGGER.info( " 启动线程 初始化任务 站点  {} {} " , task.getSite().getSiteName() , i+1 );
        }

        try {
            latch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }

    @Override
    public void failToNormal(final Task task) {
        try (Jedis jedis = pool.getResource()){
            Set<String> set = jedis.smembers(getFailKey(task));
            jedis.del(getFailKey(task));

            for (String str : set) {
                Request request = getExtrasInItem(jedis, str, task);

                if (taskPriority != null) {
                    taskPriority.setRequestProiority(request);
                }

                request.setRepush(true);

                pushWhenNoDuplicate(request, task);
            }
        }
    }

    @Override
    protected void pushWhenNoDuplicate(Request request, Task task) {
        pushWhenNoDuplicate(request , task , null);
    }

    @Override
    protected void pushWhenNoDuplicate(Request request, Task task ,String firstKey) {

        String key = request.getUrl();

        try (final Jedis jedis = pool.getResource()){

            if (StringUtils.isNoneBlank(firstKey)) {
                jedis.rpush(getFirstPriorityKey(task,firstKey), key);
                return;
            }

            long priority = request.getPriority();

            switch ((int) priority) {
                case -1:
                    jedis.rpush(getZsetMinusPriorityKey(task), key);
                    break;
                case 1:
                    jedis.rpush(getZsetPlusPriorityKey(task), key);
                    break;
                default:
                    jedis.rpush(getQueueNoPriorityKey(task), key);
            }

            setExtrasInItem(jedis, request, task, key);
        }
    }

    @Override
    public void pushToFailQueue(Request request, Task task) {
        Jedis jedis = pool.getResource();
        try {
            jedis.sadd(getFailKey(task), request.getUrl());
            setExtrasInItem(jedis, request, task, request.getUrl());
        } finally {
            jedis.close();
        }
    }

    @Override
    public synchronized Request poll(Task task) {
        return poll(task,null,null);
    }

    @Override
    public synchronized Request poll(Task task, String firstSite , String excludeSite) {

        try(Jedis jedis = pool.getResource()) {
            String key = getRequest(jedis, task , firstSite , excludeSite);
            if (StringUtils.isBlank(key)) {
                return null;
            }
            return getExtrasInItem(jedis, key, task);
        }

    }

    public String getRequest(Jedis jedis, Task task , String firstKey , String excludeSite) {

        String key = null;

        if (StringUtils.isNotBlank(firstKey)) {
            key = jedis.lpop(getFirstPriorityKey(task ,  firstKey));
        }

        if (StringUtils.isNotBlank(key)) {
            return key;
        }

        key = jedis.lpop(getZsetPlusPriorityKey(task));
        if (StringUtils.isNotBlank(key)) {
            return key;
        }

        key = jedis.lpop(getQueueNoPriorityKey(task));
        if (StringUtils.isNotBlank(key)) {
            return key;
        }
        key = jedis.lpop(getZsetMinusPriorityKey(task));
        return key;
    }


    @Override
    public void resetDuplicateCheck(Task task) {
        try(Jedis jedis = pool.getResource()) {
            jedis.del(getSetKey(task));
        }
    }

    public void setExtrasInItem(Jedis jedis, Request request, Task task, String key) {
        if (checkForAdditionalInfo(request)) {
            String field = DigestUtils.sha1Hex(key);
            String value = GsonUtil.toJson(request);
            jedis.hset(getItemKey(task), field, value);
        }
    }

    public Request getExtrasInItem(Jedis jedis, String url, Task task) {
        String key = getItemKey(task);
        String field = DigestUtils.sha1Hex(url);
        byte[] bytes = jedis.hget(key.getBytes(), field.getBytes());
        if (bytes != null) {
            return GsonUtil.from(new String(bytes), Request.class);
        }

        return new Request(url);
    }

    public String getZsetPlusPriorityKey(Task task) {
        return ZSET_PREFIX + task.getUUID() + PLUS_PRIORITY_SUFFIX;
    }

    public String getQueueNoPriorityKey(Task task) {
        return QUEUE_PREFIX + task.getUUID() + NO_PRIORITY_SUFFIX;
    }

    public String getZsetMinusPriorityKey(Task task) {
        return ZSET_PREFIX + task.getUUID() + MINUS_PRIORITY_SUFFIX;
    }

    public String getFirstPriorityKey(Task task , String firstKey) {
        return ZSET_PREFIX + task.getUUID() + "_" + firstKey;
    }

    public void setTaskPriority(TaskPriority taskPriority) {
        this.taskPriority = taskPriority;
    }

    public TaskPriority getTaskPriority() {
        return taskPriority;
    }
}
