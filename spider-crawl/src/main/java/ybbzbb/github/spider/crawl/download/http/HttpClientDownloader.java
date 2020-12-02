package ybbzbb.github.spider.crawl.download.http;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ybbzbb.github.spider.core.Page;
import ybbzbb.github.spider.core.Request;
import ybbzbb.github.spider.core.Site;
import ybbzbb.github.spider.core.Task;
import ybbzbb.github.spider.core.proxy.Proxy;
import ybbzbb.github.spider.core.proxy.ProxyProvider;
import ybbzbb.github.spider.core.selector.PlainText;
import ybbzbb.github.spider.core.util.CharsetUtils;
import ybbzbb.github.spider.core.util.HttpClientUtils;
import ybbzbb.github.spider.core.util.HttpConstant;
import ybbzbb.github.spider.core.util.http.HttpAnalysis;
import ybbzbb.github.spider.crawl.download.AbstractDownloader;
import ybbzbb.github.spider.crawl.download.RebootCheck;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;


/**
 * The http downloader based on HttpClient.
 *
 * @author code4crafter@gmail.com <br>
 * @since 0.1.0
 */
public class HttpClientDownloader extends AbstractDownloader {

    private Logger logger = LoggerFactory.getLogger(getClass());

    private final Map<String, CloseableHttpClient> httpClients = new HashMap<String, CloseableHttpClient>();

    private HttpClientGenerator httpClientGenerator = new HttpClientGenerator();

    private HttpUriRequestConverter httpUriRequestConverter = new HttpUriRequestConverter();
    
    private ProxyProvider proxyProvider;

    private boolean responseHeader = true;

    private RebootCheck rebootCheck;

    public void setHttpUriRequestConverter(HttpUriRequestConverter httpUriRequestConverter) {
        this.httpUriRequestConverter = httpUriRequestConverter;
    }

    public void setProxyProvider(ProxyProvider proxyProvider) {
        this.proxyProvider = proxyProvider;
    }

    private CloseableHttpClient getHttpClient(Site site) {
        if (site == null) {
            return httpClientGenerator.getClient(null);
        }
        String domain = site.getDomain();
        CloseableHttpClient httpClient = httpClients.get(domain);

        if (httpClient == null) {
            synchronized (this) {
                httpClient = httpClients.get(domain);
                if (httpClient == null) {
                    httpClient = httpClientGenerator.getClient(site);
                    httpClients.put(domain, httpClient);
                }
            }
        }
        return httpClient;
    }

    @Override
    public Page download(Request request, Task task) {

        if (task == null || task.getSite() == null) {
            throw new NullPointerException("task or site can not be null");
        }

        if (!request.getHeaders().entrySet().stream().filter( e -> e.getKey().equals("user-agent")).findFirst().isPresent()) {
            request.addHeader("user-agent", UserAgentRandom.getRandomUserAgent());
        }

        CloseableHttpResponse httpResponse = null;
        CloseableHttpClient httpClient = getHttpClient(task.getSite());

        Proxy proxy = null;
        try{
            if (request.getSessionAO() == null
                    || StringUtils.isBlank(request.getSessionAO().getHost())) {
                proxy = proxyProvider != null ? proxyProvider.getProxy(task) : null;
            }else{
                proxy = Proxy.builder()
                        .host(request.getSessionAO().getHost())
                        .port(request.getSessionAO().getPort())
                        .username(request.getSessionAO().getUsername())
                        .password(request.getSessionAO().getPassword())
                        .build();
            }
        }catch (Exception e ){
            throw new NullPointerException("get proxy is error");
        }

        HttpClientRequestContext requestContext = httpUriRequestConverter.convert(request, task.getSite(), proxy);
        Page page = Page.fail();
        boolean isReboot = false;

        try {
            httpResponse = httpClient.execute(requestContext.getHttpUriRequest(), requestContext.getHttpClientContext());
            page = handleResponse(request
                    , request.getCharset() != null ? request.getCharset() : task.getSite().getCharset()
                    , httpResponse
                    , task
            );
            onSuccess(request);

            if (page.getStatusCode() == HttpConstant.StatusCode.CODE_407) {
                // 代理异常
                logger.info("proxy error code {} url {} " , page.getStatusCode() , request.getUrl());
                proxyError(page);
                return page;
            }


            if (getAcceptStatCodes().contains(page.getStatusCode())) {
                page.setReboot(true);
            }

            logger.info("download page reboot {} code {} - {}", isReboot , page.getStatusCode() , proxy != null ? proxy.getHost() : " 没有使用IP " , request.getUrl());

            return page;
        }catch (Exception e){
            logger.error("download error url {} message {} " , request.getUrl() , e.getMessage());
            timeOut(page , proxy , request);
            return page;
        } finally {
            if (httpResponse != null) {
                EntityUtils.consumeQuietly(httpResponse.getEntity());
            }
            if (proxyProvider != null && proxy != null && request.getSessionAO() == null) {
                proxyProvider.returnProxy(proxy, page, task);
            }
        }
    }

    @Override
    public void setThread(int thread) {
        httpClientGenerator.setPoolSize(thread);
    }

    protected Page handleResponse(Request request, String charset, HttpResponse httpResponse , Task task) throws IOException {
        Page page = new Page();

        if (task.getSite().isDownloadFile()) {
            final InputStream is = HttpAnalysis.buildHttpFile(httpResponse);
            page.setIs(is);
        }else{
            byte[] bytes = IOUtils.toByteArray(httpResponse.getEntity().getContent());
            String contentType = httpResponse.getEntity().getContentType() == null ? "" : httpResponse.getEntity().getContentType().getValue();

            page.setBytes(bytes);
            if (!request.isBinaryContent()){
                if (charset == null) {
                    charset = getHtmlCharset(contentType, bytes);
                }
                page.setCharset(charset);
                page.setRawText(new String(bytes, charset));
            }
        }

        page.setUrl(new PlainText(request.getUrl()));
        page.setRequest(request);
        page.setRequest(request);
        page.setStatusCode(httpResponse.getStatusLine().getStatusCode());
        page.setDownloadSuccess(true);

        if (responseHeader) {
            page.setHeaders(HttpClientUtils.convertHeaders(httpResponse.getAllHeaders()));
        }

        return page;
    }

    private String getHtmlCharset(String contentType, byte[] contentBytes) throws IOException {
        String charset = CharsetUtils.detectCharset(contentType, contentBytes);
        if (charset == null) {
            charset = Charset.defaultCharset().name();
            logger.warn("Charset autodetect failed, use {} as charset. Please specify charset in Site.setCharset()", Charset.defaultCharset());
        }
        return charset;
    }


    private void timeOut(Page page , Proxy proxy , Request request){
        page.setTimeOut(true);
        page.setDownloadSuccess(false);

        proxy = Optional.ofNullable(proxy).orElse(new Proxy());
        logger.info(" download page timeOut {} timeout proxy {} "
                , request.getUrl()
                , proxy.getHost()+":"+proxy.getPort() );
    }

    private void proxyError(Page page){
        page.setProxyError(true);
    }

    public void setRebootCheck(RebootCheck rebootCheck) {
        this.rebootCheck = rebootCheck;
    }
}
