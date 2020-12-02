package ybbzbb.github.spider.crawl;


import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.SerializationUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ybbzbb.github.spider.core.*;
import ybbzbb.github.spider.core.exception.RobotException;
import ybbzbb.github.spider.core.model.SessionAO;
import ybbzbb.github.spider.core.model.SessionManage;
import ybbzbb.github.spider.core.pipeline.CollectorPipeline;
import ybbzbb.github.spider.core.pipeline.ConsolePipeline;
import ybbzbb.github.spider.core.pipeline.Pipeline;
import ybbzbb.github.spider.core.pipeline.ResultItemsCollectorPipeline;
import ybbzbb.github.spider.core.processor.PageProcessor;
import ybbzbb.github.spider.core.scheduler.QueueScheduler;
import ybbzbb.github.spider.core.scheduler.RedisScheduler;
import ybbzbb.github.spider.core.scheduler.Scheduler;
import ybbzbb.github.spider.core.statistic.CloseStatistics;
import ybbzbb.github.spider.core.statistic.CountTypeEnums;
import ybbzbb.github.spider.core.statistic.Statistics;
import ybbzbb.github.spider.core.thread.CountableThreadPool;
import ybbzbb.github.spider.core.util.UrlUtils;
import ybbzbb.github.spider.core.util.WMCollections;
import ybbzbb.github.spider.crawl.download.Downloader;
import ybbzbb.github.spider.crawl.download.http.HttpClientDownloader;

import java.io.Closeable;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Entrance of a crawler.<br>
 * A spider contains four modules: Downloader, Scheduler, PageProcessor and
 * Pipeline.<br>
 * Every module is a field of Spider. <br>
 * The modules are defined in interface. <br>
 * You can customize a spider with various implementations of them. <br>
 * Examples: <br>
 * <br>
 * A simple crawler: <br>
 * Spider.create(new SimplePageProcessor("http://my.oschina.net/",
 * "http://my.oschina.net/*blog/*")).run();<br>
 * <br>
 * Store results to files by FilePipeline: <br>
 * Spider.create(new SimplePageProcessor("http://my.oschina.net/",
 * "http://my.oschina.net/*blog/*")) <br>
 * .pipeline(new FilePipeline("/data/temp/webmagic/")).run(); <br>
 * <br>
 * Use FileCacheQueueScheduler to store urls and cursor in files, so that a
 * Spider can resume the status when shutdown. <br>
 * Spider.create(new SimplePageProcessor("http://my.oschina.net/",
 * "http://my.oschina.net/*blog/*")) <br>
 * .scheduler(new FileCacheQueueScheduler("/data/temp/webmagic/cache/")).run(); <br>
 *
 * @author code4crafter@gmail.com <br>
 * @see Downloader
 * @see Scheduler
 * @see PageProcessor
 * @see Pipeline
 * @since 0.1.0
 */
public class Spider implements Runnable, Task {

    protected Downloader downloader;

    protected List<Pipeline> pipelines = new ArrayList<Pipeline>();

    protected PageProcessor pageProcessor;

    protected List<Request> startRequests;

    protected Site site;

    protected String uuid;

    protected Scheduler scheduler = new QueueScheduler();

    protected Statistics statistics = new CloseStatistics();

    protected Logger logger = LoggerFactory.getLogger(getClass());

    protected CountableThreadPool threadPool;

    protected ExecutorService executorService;

    protected int threadNum = 1;

    protected AtomicInteger stat = new AtomicInteger(STAT_INIT);

    protected boolean exitWhenComplete = true;

    protected final static int STAT_INIT = 0;

    protected final static int STAT_RUNNING = 1;

    protected final static int STAT_STOPPED = 2;

    protected boolean spawnUrl = true;

    protected boolean destroyWhenExit = true;

    private ReentrantLock newUrlLock = new ReentrantLock();

    private Condition newUrlCondition = newUrlLock.newCondition();

    private List<SpiderListener> spiderListeners;

    private final AtomicLong pageCount = new AtomicLong(0);

    private Date startTime;

    private NetworkExclude networkExclude;

    private SessionManage sessionManage;

    //10 分钟
    private int emptySleepTime = 1000 * 60 * 10;


    private String siteFirstKey;

    private String siteExcludeKey;


    /**
     * create a spider with pageProcessor.
     *
     * @param pageProcessor pageProcessor
     * @return new spider
     * @see PageProcessor
     */
    public static Spider create(PageProcessor pageProcessor) {
        return new Spider(pageProcessor);
    }

    /**
     * create a spider with pageProcessor.
     *
     * @param pageProcessor pageProcessor
     */
    public Spider(PageProcessor pageProcessor) {
        this.pageProcessor = pageProcessor;
        this.site = pageProcessor.getSite();
    }

    /**
     * Set startUrls of Spider.<br>
     * Prior to startUrls of Site.
     *
     * @param startUrls startUrls
     * @return this
     */
    public Spider startUrls(List<String> startUrls) {
        checkIfRunning();
        this.startRequests = UrlUtils.convertToRequests(startUrls);
        return this;
    }

    /**
     * Set startUrls of Spider.<br>
     * Prior to startUrls of Site.
     *
     * @param startRequests startRequests
     * @return this
     */
    public Spider startRequest(List<Request> startRequests) {
        checkIfRunning();
        this.startRequests = startRequests;
        return this;
    }

    /**
     * Set an uuid for spider.<br>
     * Default uuid is domain of site.<br>
     *
     * @param uuid uuid
     * @return this
     */
    public Spider setUUID(String uuid) {
        this.uuid = uuid;
        return this;
    }

    /**
     * set scheduler for Spider
     *
     * @param scheduler scheduler
     * @return this
     * @see #setScheduler(com.galaplat.crawler.center.core.scheduler.Scheduler)
     */
    @Deprecated
    public Spider scheduler(Scheduler scheduler) {
        return setScheduler(scheduler);
    }

    /**
     * set scheduler for Spider
     *
     * @param scheduler scheduler
     * @return this
     * @see Scheduler
     * @since 0.2.1
     */
    public Spider setScheduler(Scheduler scheduler) {
        checkIfRunning();
        Scheduler oldScheduler = this.scheduler;
        this.scheduler = scheduler;
        if (oldScheduler != null) {
            Request request;
            while ((request = oldScheduler.poll(this)) != null) {
                this.scheduler.push(request, this);
            }
        }
        return this;
    }

    /**
     * add a pipeline for Spider
     *
     * @param pipeline pipeline
     * @return this
     * @see #addPipeline(com.galaplat.crawler.center.core.pipeline.Pipeline)
     * @deprecated
     */
    public Spider pipeline(Pipeline pipeline) {
        return addPipeline(pipeline);
    }

    /**
     * add a pipeline for Spider
     *
     * @param pipeline pipeline
     * @return this
     * @see Pipeline
     * @since 0.2.1
     */
    public Spider addPipeline(Pipeline pipeline) {
        checkIfRunning();
        this.pipelines.add(pipeline);
        return this;
    }

    /**
     * set pipelines for Spider
     *
     * @param pipelines pipelines
     * @return this
     * @see Pipeline
     * @since 0.4.1
     */
    public Spider setPipelines(List<Pipeline> pipelines) {
        checkIfRunning();
        this.pipelines = pipelines;
        return this;
    }

    /**
     * clear the pipelines set
     *
     * @return this
     */
    public Spider clearPipeline() {
        pipelines = new ArrayList<Pipeline>();
        return this;
    }

    /**
     * set the downloader of spider
     *
     * @param downloader downloader
     * @return this
     * @see #setDownloader(com.galaplat.crawler.center.core.downloader.Downloader)
     * @deprecated
     */
    public Spider downloader(Downloader downloader) {
        return setDownloader(downloader);
    }

    /**
     * set the downloader of spider
     *
     * @param downloader downloader
     * @return this
     * @see Downloader
     */
    public Spider setDownloader(Downloader downloader) {
        checkIfRunning();
        this.downloader = downloader;
        return this;
    }

    protected void initComponent() {
        if (downloader == null) {
            this.downloader = new HttpClientDownloader();
        }
        if (pipelines.isEmpty()) {
            pipelines.add(new ConsolePipeline());
        }
        downloader.setThread(threadNum);
        if (threadPool == null || threadPool.isShutdown()) {
            if (executorService != null && !executorService.isShutdown()) {
                threadPool = new CountableThreadPool(threadNum, executorService);
            } else {
                threadPool = new CountableThreadPool(threadNum);
            }
        }
        if (startRequests != null) {
            for (Request request : startRequests) {
                addRequest(request);
            }
            startRequests.clear();
        }
        startTime = new Date();
    }

    public void doInitScheulerRetry() {

        RedisScheduler redis = null;

        List<String> sites = new ArrayList<>();

        final RedisScheduler redisTem = redis;
        final String uuid = this.getUUID();

        sites.stream().forEach( e -> {

            final Task task = new Task() {
                @Override
                public String getUUID() {
                    return uuid;
                }

                @Override
                public Site getSite() {
                    Site site = Site.me();
                    site.setSiteName(e);
                    return site;
                }
            };

            if(site.isReloadFailQueue()) {
                redisTem.failToNormal(task);
            }
        });

    }

    @Override
    public void run() {
        checkRunningStat();
        initComponent();
        logger.info("Spider {} started!",getUUID());
        while (!Thread.currentThread().isInterrupted() && stat.get() == STAT_RUNNING) {

            final Request request = scheduler.poll(this , siteFirstKey , siteExcludeKey);

            if (request == null) {
                logger.debug("request is null");
                if (threadPool.getThreadAlive() == 0  ) {
                	if (exitWhenComplete) {
                    	 break;
                    }else {
                    	logger.info("Spider {} runTimes end! {} pages downloaded.", getUUID(), pageCount.getAndSet(0));
                    	doInitScheulerRetry();
                    }
                }

                logger.info("Spider {} runTimes end! {} threadAlive.", getUUID(), threadPool.getThreadAlive());
                waitNewUrl();
                continue;
            }

            if (networkExclude != null && networkExclude.exclude(request)) {
                rePushRequest(request , siteExcludeKey);
                logger.info("url {} ,服务器无法处理 下一条", request.getUrl());

                continue;
            }

            threadPool.execute(new Runnable() {
                @Override
                public void run() {
                    logger.info("request begin spider {}",request.getUrl());
                    try {
                        processRequest(request);
                        onSuccess(request);
                    } catch (Exception e) {
                        onError(request);
                        e.printStackTrace();

                        logger.error("error url {} message {}" , request.getUrl() , e.getMessage() );

                        onDownloaderFail(request , null);
                    } finally {
                        pageCount.incrementAndGet();
                        signalNewUrl();
                    }

                    logger.debug("request end spider {}",request.getUrl());
                }
            });

        }
        stat.set(STAT_STOPPED);
        // release some resources
        if (destroyWhenExit) {
            close();
        }
        logger.info("Spider {} closed! {} pages downloaded.", getUUID(), pageCount.get());
    }

    protected void onError(Request request) {
        if (CollectionUtils.isNotEmpty(spiderListeners)) {
            for (SpiderListener spiderListener : spiderListeners) {
                spiderListener.onError(request);
            }
        }
    }

    protected void onSuccess(Request request) {
        if (CollectionUtils.isNotEmpty(spiderListeners)) {
            for (SpiderListener spiderListener : spiderListeners) {
                spiderListener.onSuccess(request);
            }
        }
    }

    private void checkRunningStat() {
        while (true) {
            int statNow = stat.get();
            if (statNow == STAT_RUNNING) {
                throw new IllegalStateException("Spider is already running!");
            }
            if (stat.compareAndSet(statNow, STAT_RUNNING)) {
                break;
            }
        }
    }

    public void close() {
        destroyEach(downloader);
        destroyEach(pageProcessor);
        destroyEach(scheduler);
        for (Pipeline pipeline : pipelines) {
            destroyEach(pipeline);
        }
        threadPool.shutdown();
    }

    private void destroyEach(Object object) {
        if (object instanceof Closeable) {
            try {
                ((Closeable) object).close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Process specific urls without url discovering.
     *
     * @param urls urls to process
     */
    public void test(String... urls) {
        initComponent();
        if (urls.length > 0) {
            for (String url : urls) {
                processRequest(new Request(url));
            }
        }
    }

    private void setRequestSession(Request request){
        if (sessionManage == null || !request.isNeedLocal()) {
            return;
        }

        if (!sessionManage.needSession(request.getUrl())) {
            return;
        }

        SessionAO sessionAO = sessionManage.getSessionId(request.getSite());

        if (sessionAO == null){
            sessionManage.retainSessionBySite(request.getSite());
            sessionAO = sessionManage.getSessionId(request.getSite());
        }

        if (sessionAO == null){
            throw new RuntimeException("place create session ");
        }

        sessionAO.setOut(false);
        sessionAO.getCookieAOS().forEach(e -> {
            request.addCookie(e.getName(),e.getValue());
        });

        request.setSessionAO(sessionAO);
        request.addHeader("user-agent" , sessionAO.getUserAgent());
    }

    private void overdueSession(Request request){
        if (sessionManage == null || !request.isNeedLocal()) {
            return;
        }

        sessionManage.overdue(
                request.getSessionAO()
                ,  request.getSite() );
    }

    private void processRequest(Request request) {
        request.setSessionOverdue(false);
        setRequestSession(request);
        Page page = downloader.download(request, this);

        sleep(site.getSleepTime());

        if (page.isDownloadSuccess()){
            onDownloadSuccess(request, page);
        } else {
            onDownloaderFail(request, page);
        }

        overdueSession(request);
    }

    private void onDownloadSuccess(Request request, Page page) {
        if (page.isProxyError()) {
            requestProxyError(request);
            return;
        }

        logger.debug("url :{} begin process",page.getUrl());

        try {
            pageProcessor.process(page);
        }catch (RobotException e){
            logger.error("解析页面 robot page {} {}" ,e);
            requestReboot(request);
        }

        if (page.isReboot()) {
            requestReboot(request);
            return;
        }

        scheduler.cleanRequest(request,this);

        logger.info("url :{} process over , save db",page.getUrl());
        for (Pipeline pipeline : pipelines) {
            pipeline.process(page.getResultItems(), this);
        }

        statistics.add(this, CountTypeEnums.SUCCESS, request);

    }

    private void requestSessionOut(Request request){
        statistics.add(this, CountTypeEnums.SESSION_OUT, request);

        //reboot 原因是 代理造成 的， 将任务重新放入队列中 。
        rePushRequest(SerializationUtils.clone(request));
        return ;
    }


    private void requestReboot(Request request){
        statistics.add(this, CountTypeEnums.ROBOT, request);

        //reboot 原因是 代理造成 的， 将任务重新放入队列中 。
        rePushRequest(SerializationUtils.clone(request));
        return ;
    }

    private void requestProxyError(Request request){
        statistics.add(this, CountTypeEnums.PROXY_ERROR, request);
        rePushRequest(SerializationUtils.clone(request));
        return ;
    }

    private void onDownloaderFail(Request request ,Page page) {
        //网络问题导致报错
        if (page != null && page.isTimeOut()) {
            statistics.add(this, CountTypeEnums.TIME_OUT, request);
            rePushRequest(SerializationUtils.clone(request));
            return;
        }

        statistics.add(this, CountTypeEnums.ERROR, request);

        if (site.getCycleRetryTimes() == 0) {
            sleep(site.getSleepTime());
        } else {
            doCycleRetry(request);
            sleep(site.getRetrySleepTime());
        }
    }

    private void doFailedRetry(Request request){
        Object failedTimes = request.getExtra(Request.CYCLE_FAILED_TIMES);
        if (failedTimes == null) {

            addFailRequest( SerializationUtils
                    .clone(request)
                    .putExtra(Request.CYCLE_TRIED_TIMES, 1)
                    .putExtra(Request.CYCLE_FAILED_TIMES,1)
            );

            return;
        }

        int failedTimesNumber = (int)failedTimes + 1;

        if (failedTimesNumber < site.getFailedDownNum()) {
            addFailRequest( SerializationUtils
                    .clone(request)
                    .putExtra(Request.CYCLE_TRIED_TIMES, 1)
                    .putExtra(Request.CYCLE_FAILED_TIMES,failedTimesNumber)
            );

            return;
        }

        scheduler.pushCloseQueue(request,this);
    }

    private void doCycleRetry(Request request) {

        Object cycleTriedTimesObject = request.getExtra(Request.CYCLE_TRIED_TIMES);

        if (cycleTriedTimesObject == null) {
            rePushRequest(SerializationUtils.clone(request).putExtra(Request.CYCLE_TRIED_TIMES, 1));
            return;
        }

        int cycleTriedTimes = (Integer) cycleTriedTimesObject;
        cycleTriedTimes++;

        if (cycleTriedTimes < site.getCycleRetryTimes()) {
            rePushRequest(SerializationUtils.clone(request).putExtra(Request.CYCLE_TRIED_TIMES, cycleTriedTimes));
            return;
        }

        doFailedRetry(request);
    }

    protected void sleep(int time) {
        if (time == 0) {
            return;
        }
        try {
            Thread.sleep(time);
        } catch (InterruptedException e) {
            logger.error("Thread interrupted when sleep",e);
        }
    }

    private void addFailRequest(Request request) {
        if (site.getDomain() == null && request != null && request.getUrl() != null) {
            site.setDomain(UrlUtils.getDomain(request.getUrl()));
        }
        scheduler.pushToFailQueue(request, this);
    }

    private void rePushRequest(Request request , String spiderExcludeKey) {
        if (site.getDomain() == null && request != null && request.getUrl() != null) {
            site.setDomain(UrlUtils.getDomain(request.getUrl()));
        }

        scheduler.rePush(request, this , spiderExcludeKey);
    }

    private void rePushRequest(Request request) {
        rePushRequest(request,null);
    }
    
    private void addRequest(Request request) {
        if (StringUtils.isBlank(request.getUrl())) {
            return;
        }
        if (site.getDomain() == null && request != null && request.getUrl() != null) {
            site.setDomain(UrlUtils.getDomain(request.getUrl()));
        }

        scheduler.push(request, this);
        statistics.add(this, CountTypeEnums.NEW, request);
    }


    protected void checkIfRunning() {
        if (stat.get() == STAT_RUNNING) {
            throw new IllegalStateException("Spider is already running!");
        }
    }

    public void runAsync() {
        Thread thread = new Thread(this);
        thread.setDaemon(false);
        thread.start();
    }

    /**
     * Add urls to crawl. <br>
     *
     * @param urls urls
     * @return this
     */
    public Spider addUrl(String... urls) {
        for (String url : urls) {
            addRequest(new Request(url));
        }
        signalNewUrl();
        return this;
    }

    /**
     * Download urls synchronizing.
     *
     * @param urls urls
     * @param <T> type of process result
     * @return list downloaded
     */
    public <T> List<T> getAll(Collection<String> urls) {
        destroyWhenExit = false;
        spawnUrl = false;
        if (startRequests!=null){
            startRequests.clear();
        }
        for (Request request : UrlUtils.convertToRequests(urls)) {
            addRequest(request);
        }
        CollectorPipeline collectorPipeline = getCollectorPipeline();
        pipelines.add(collectorPipeline);
        run();
        spawnUrl = true;
        destroyWhenExit = true;
        return collectorPipeline.getCollected();
    }

    protected CollectorPipeline getCollectorPipeline() {
        return new ResultItemsCollectorPipeline();
    }

    public <T> T get(String url) {
        List<String> urls = WMCollections.newArrayList(url);
        List<T> resultItemses = getAll(urls);
        if (resultItemses != null && resultItemses.size() > 0) {
            return resultItemses.get(0);
        } else {
            return null;
        }
    }

    private void waitNewUrl() {
        newUrlLock.lock();
        try {
            if (threadPool.getThreadAlive() == 0 && exitWhenComplete) {
                return;
            }

            logger.info("thread wait new url {} {} " , Thread.currentThread().getName() , emptySleepTime);
            newUrlCondition.await(emptySleepTime, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            logger.warn("waitNewUrl - interrupted, error {}", e);
        } finally {
            newUrlLock.unlock();
        }
    }

    private void signalNewUrl() {
        try {
            newUrlLock.lock();
            newUrlCondition.signalAll();
        } finally {
            newUrlLock.unlock();
        }
    }

    public void start() {
        runAsync();
    }

    public void stop() {
        if (stat.compareAndSet(STAT_RUNNING, STAT_STOPPED)) {
            logger.info("Spider " + getUUID() + " stop success!");
        } else {
            logger.info("Spider " + getUUID() + " stop fail!");
        }
    }

    /**
     * start with more than one threads
     *
     * @param threadNum threadNum
     * @return this
     */
    public Spider thread(int threadNum) {
        checkIfRunning();
        this.threadNum = threadNum;
        if (threadNum <= 0) {
            throw new IllegalArgumentException("threadNum should be more than one!");
        }
        return this;
    }

    /**
     * start with more than one threads
     *
     * @param executorService executorService to run the spider
     * @param threadNum threadNum
     * @return this
     */
    public Spider thread(ExecutorService executorService, int threadNum) {
        checkIfRunning();
        this.threadNum = threadNum;
        if (threadNum <= 0) {
            throw new IllegalArgumentException("threadNum should be more than one!");
        }
        this.executorService = executorService;
        return this;
    }

    public boolean isExitWhenComplete() {
        return exitWhenComplete;
    }

    /**
     * Exit when complete. <br>
     * True: exit when all url of the site is downloaded. <br>
     * False: not exit until call stop() manually.<br>
     *
     * @param exitWhenComplete exitWhenComplete
     * @return this
     */
    public Spider setExitWhenComplete(boolean exitWhenComplete) {
        this.exitWhenComplete = exitWhenComplete;
        return this;
    }

    public boolean isSpawnUrl() {
        return spawnUrl;
    }

    /**
     * Get page count downloaded by spider.
     *
     * @return total downloaded page count
     * @since 0.4.1
     */
    public long getPageCount() {
        return pageCount.get();
    }

    /**
     * Get running status by spider.
     *
     * @return running status
     * @see Status
     * @since 0.4.1
     */
    public Status getStatus() {
        return Status.fromValue(stat.get());
    }


    public enum Status {
        Init(0), Running(1), Stopped(2);

        private Status(int value) {
            this.value = value;
        }

        private int value;

        int getValue() {
            return value;
        }

        public static Status fromValue(int value) {
            for (Status status : values()) {
                if (status.getValue() == value) {
                    return status;
                }
            }
            //default value
            return Init;
        }
    }

    /**
     * Get thread count which is running
     *
     * @return thread count which is running
     * @since 0.4.1
     */
    public int getThreadAlive() {
        if (threadPool == null) {
            return 0;
        }
        return threadPool.getThreadAlive();
    }

    /**
     * Whether add urls extracted to download.<br>
     * Add urls to download when it is true, and just download seed urls when it is false. <br>
     * DO NOT set it unless you know what it means!
     *
     * @param spawnUrl spawnUrl
     * @return this
     * @since 0.4.0
     */
    public Spider setSpawnUrl(boolean spawnUrl) {
        this.spawnUrl = spawnUrl;
        return this;
    }

    @Override
    public String getUUID() {
        if (uuid != null) {
            return uuid;
        }
        if (site != null) {
            return site.getDomain();
        }
        uuid = UUID.randomUUID().toString();
        return uuid;
    }

    public Spider setExecutorService(ExecutorService executorService) {
        checkIfRunning();
        this.executorService = executorService;
        return this;
    }

    @Override
    public Site getSite() {
        return site;
    }

    public List<SpiderListener> getSpiderListeners() {
        return spiderListeners;
    }

    public Spider setSpiderListeners(List<SpiderListener> spiderListeners) {
        this.spiderListeners = spiderListeners;
        return this;
    }

    public Date getStartTime() {
        return startTime;
    }

    public Scheduler getScheduler() {
        return scheduler;
    }

    /**
     * Set wait time when no url is polled.<br><br>
     *
     * @param emptySleepTime In MILLISECONDS.
     */
    public void setEmptySleepTime(int emptySleepTime) {
        this.emptySleepTime = emptySleepTime;
    }

    public void setNetworkExclude(NetworkExclude networkExclude) {
        this.networkExclude = networkExclude;
    }

    public void setSiteFirstKey(String siteFirstKey) {
        this.siteFirstKey = siteFirstKey;
    }

    public void setSiteExcludeKey(String siteExcludeKey) {
        this.siteExcludeKey = siteExcludeKey;
    }

    public void setSessionManage(SessionManage sessionManage) {
        this.sessionManage = sessionManage;
    }

    public void setStatistics(Statistics statistics) {
        this.statistics = statistics;
    }

    public static boolean isSessionTimeOut(Page page){

        if (page != null &&
          StringUtils.isNotBlank( page.getHtml().xpath("//form[@action=\"/access/web/login\"]").get())
        ) {


            return true;
        }

        return false;
    }
}
