package ybbzbb.github.spider.core.scheduler;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import redis.clients.jedis.*;
import ybbzbb.github.spider.core.Request;
import ybbzbb.github.spider.core.Task;
import ybbzbb.github.spider.core.scheduler.component.DuplicateRemover;
import ybbzbb.github.spider.core.util.GsonUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Use Redis as url scheduler for distributed crawlers.<br>
 *
 * @author code4crafter@gmail.com <br>
 * @since 0.2.0
 */
public class RedisScheduler extends DuplicateRemovedScheduler implements MonitorableScheduler, DuplicateRemover {
    protected JedisPool pool;

    private static final String QUEUE_PREFIX = "queue_";
    private static final String SET_PREFIX = "set_";
    private static final String ITEM_PREFIX = "item_";
    private static final String FAIL_PREFIX = "fail_";
    private static final String CLOSE_PREFIX = ":close";

    public RedisScheduler(String host) {
        this(new JedisPool(new JedisPoolConfig(), host));
    }

    public RedisScheduler(JedisPool pool) {
        this.pool = pool;
        setDuplicateRemover(this);
    }

    @Override
    public void resetDuplicateCheck(Task task) {
        Jedis jedis = pool.getResource();
        try {
            jedis.del(getSetKey(task));
        } finally {
            jedis.close();
        }
    }

    public void failToNormal(Task task) {
        Jedis jedis = pool.getResource();
        try {
            Set<String> set = jedis.smembers(getFailKey(task));
            jedis.del(getFailKey(task));
            for (String str : set) {
                jedis.rpush(getQueueKey(task), str);
            }
        } finally {
            jedis.close();
        }
    }

    public Set<String> lpopClose(Task task) {
        Jedis jedis = pool.getResource();
        try {
            Set<String> set = jedis.smembers(getCloseKey(task));
            return set;
        } finally {
            jedis.close();
        }
    }

    @Override
    public boolean isDuplicate(Request request, Task task) {
        try (final Jedis jedis = pool.getResource()){
            Long sadd = jedis.sadd(getSetKey(task), DigestUtils.sha1Hex(request.getUrl()));
            return sadd == 0;
        }

    }

    @Override
    public List<Boolean> isDuplicates(List<Request> requests, Task task) {

        List<Response<Long>> isRepeats = new ArrayList<>();

        try (final Jedis jedis = pool.getResource()){
            Pipeline pipelined = jedis.pipelined();

            for (Request request : requests) {
                final Response<Long> sadd = pipelined.sadd(getSetKey(task), DigestUtils.sha1Hex(request.getUrl()));
                isRepeats.add(sadd);
            }

            pipelined.sync();
        }
        return isRepeats.stream().map( e -> e.get() == 0).collect(Collectors.toList());
    }


    @Override
    public void pushToFailQueue(Request request, Task task) {
        Jedis jedis = pool.getResource();
        try {

            jedis.sadd(getFailKey(task), request.getUrl());
            if (checkForAdditionalInfo(request)) {
                String field = DigestUtils.sha1Hex(request.getUrl());
                String value = GsonUtil.toJson(request);
                jedis.hset(getItemKey(task), field, value);
            }
        } finally {
            jedis.close();
        }
    }

    @Override
    public void pushCloseQueue(Request request, Task task) {
        Jedis jedis = pool.getResource();
        try {
            jedis.sadd(getCloseKey(task), request.getUrl());
        } finally {
            jedis.close();
        }
//        cleanRequest(request, task);
    }

    @Override
    public void cleanRequest(Request request, Task task) {
        try(Jedis jedis = pool.getResource();) {
            String key = request.getUrl();
            jedis.hdel(getItemKey(task), DigestUtils.sha1Hex(key));
        }
    }

    @Override
    protected void pushWhenNoDuplicate(Request request, Task task) {
        Jedis jedis = pool.getResource();
        
        try {
            jedis.rpush(getQueueKey(task), request.getUrl());
            if (checkForAdditionalInfo(request)) {
                String field = DigestUtils.sha1Hex(request.getUrl());
                String value = GsonUtil.toJson(request);
                jedis.hset(getItemKey(task), field, value);
            }
        } finally {
            jedis.close();
        }
    }


    @Override
    public synchronized Request poll(Task task) {
        Jedis jedis = pool.getResource();
        try {
            String url = jedis.lpop(getQueueKey(task));
            if (url == null) {
                return null;
            }
            String key = getItemKey(task);
            String field = DigestUtils.shaHex(url);
            byte[] bytes = jedis.hget(key.getBytes(), field.getBytes());
            if (bytes != null) {
                Request o = GsonUtil.from(new String(bytes), Request.class);
                return o;
            }
            Request request = new Request(url);
            return request;
        } finally {
            jedis.close();
        }
    }

    @Override
    public Request poll(Task task, String firstKey, String excludeSite) {
        return null;
    }

    protected boolean checkForAdditionalInfo(Request request) {
        if (request == null) {
            return false;
        }

        if (!request.getHeaders().isEmpty() || !request.getCookies().isEmpty()) {
            return true;
        }

        if (StringUtils.isNotBlank(request.getCharset()) || StringUtils.isNotBlank(request.getMethod())) {
            return true;
        }

        if (request.isBinaryContent() || request.getRequestBody() != null) {
            return true;
        }

        if (request.getExtras() != null && !request.getExtras().isEmpty()) {
            return true;
        }
        if (request.getPriority() != 0L) {
            return true;
        }

        return false;
    }


    public String getSetKey(Task task) {
        return SET_PREFIX + task.getUUID();
    }

    public String getQueueKey(Task task) {
        return QUEUE_PREFIX + task.getUUID();
    }

    public String getItemKey(Task task) {
        return ITEM_PREFIX + task.getUUID();
    }

    public String getFailKey(Task task) {
        return FAIL_PREFIX + task.getUUID();
    }

    public String getCloseKey(Task task) {
        return task.getUUID() + CLOSE_PREFIX;
    }

    @Override
    public int getLeftRequestsCount(Task task) {
        Jedis jedis = pool.getResource();
        try {
            Long size = jedis.llen(getQueueKey(task));
            return size.intValue();
        } finally {
            jedis.close();
        }
    }

    @Override
    public int getTotalRequestsCount(Task task) {
        Jedis jedis = pool.getResource();
        try {
            Long size = jedis.scard(getSetKey(task));
            return size.intValue();
        } finally {
            jedis.close();
        }
    }


}
