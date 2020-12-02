package ybbzbb.github.spider.core.scheduler;

import ybbzbb.github.spider.core.Request;
import ybbzbb.github.spider.core.Task;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;


/**
 * Basic Scheduler implementation.<br>
 * Store urls to fetch in LinkedBlockingQueue and remove duplicate urls by HashMap.
 *
 * @author code4crafter@gmail.com <br>
 * @since 0.1.0
 */
public class QueueScheduler extends DuplicateRemovedScheduler implements MonitorableScheduler {

    private BlockingQueue<Request> queue = new LinkedBlockingQueue<Request>();

    @Override
    public void pushWhenNoDuplicate(Request request, Task task) {
        queue.add(request);
    }

    @Override
    public void rePush(Request request, Task task, String firstKey) {

    }

    @Override
    public Request poll(Task task) {
        return queue.poll();
    }

    @Override
    public Request poll(Task task, String firstKey , String excludeSite) {
        return queue.poll();
    }

    @Override
    public int getLeftRequestsCount(Task task) {
        return queue.size();
    }

    @Override
    public int getTotalRequestsCount(Task task) {
        return getDuplicateRemover().getTotalRequestsCount(task);
    }

	@Override
	public void pushToFailQueue(Request request, Task task) {
		// TODO Auto-generated method stub
		
	}

    @Override
    public void pushCloseQueue(Request request, Task task) {

    }

    @Override
    public void cleanRequest(Request request, Task task) {

    }

}
