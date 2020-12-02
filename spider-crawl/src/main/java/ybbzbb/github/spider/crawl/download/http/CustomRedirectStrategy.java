package ybbzbb.github.spider.crawl.download.http;

import org.apache.http.*;
import org.apache.http.client.methods.*;
import org.apache.http.impl.client.LaxRedirectStrategy;
import org.apache.http.protocol.HttpContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

/**
 *支持post 302跳转策略实现类
 *HttpClient默认跳转：httpClientBuilder.setRedirectStrategy(new LaxRedirectStrategy());
 *上述代码在post/redirect/post这种情况下不会传递原有请求的数据信息。所以参考了下SeimiCrawler这个项目的重定向策略。
 *原代码地址：https://github.com/zhegexiaohuozi/SeimiCrawler/blob/master/project/src/main/java/cn/wanghaomiao/seimi/http/hc/SeimiRedirectStrategy.java
 */
public class CustomRedirectStrategy extends LaxRedirectStrategy {
    private Logger logger = LoggerFactory.getLogger(getClass());

    @Override
    public HttpUriRequest getRedirect(HttpRequest request, HttpResponse response, HttpContext context) throws ProtocolException {
        URI uri = getLocationURI(request, response, context);
        String method = request.getRequestLine().getMethod();

//        if (method.equalsIgnoreCase(HttpPost.METHOD_NAME)) {
//            try {
//                HttpRequestWrapper httpRequestWrapper = (HttpRequestWrapper) request;
//                httpRequestWrapper.setURI(uri);
//                httpRequestWrapper.removeHeaders("Content-Length");
//                return httpRequestWrapper;
//            } catch (Exception e) {
//                logger.error("强转为HttpRequestWrapper出错");
//            }
//            return new HttpPost(uri);
//        } else {
//            return new HttpGet(uri);
//        }


        if (method.equalsIgnoreCase(HttpHead.METHOD_NAME)) {
            return new HttpHead(uri);
        } else if (method.equalsIgnoreCase(HttpPost.METHOD_NAME)) {
        	 final int status = response.getStatusLine().getStatusCode();
             if (status == HttpStatus.SC_TEMPORARY_REDIRECT) {
                 return RequestBuilder.copy(request).setUri(uri).build();
             } 
            return this.copyEntity(new HttpPost(uri), request);
        } else if (method.equalsIgnoreCase(HttpPut.METHOD_NAME)) {
            return this.copyEntity(new HttpPut(uri), request);
        } else if (method.equalsIgnoreCase(HttpDelete.METHOD_NAME)) {
            return new HttpDelete(uri);
        } else if (method.equalsIgnoreCase(HttpTrace.METHOD_NAME)) {
            return new HttpTrace(uri);
        } else if (method.equalsIgnoreCase(HttpOptions.METHOD_NAME)) {
            return new HttpOptions(uri);
        } else if (method.equalsIgnoreCase(HttpPatch.METHOD_NAME)) {
            return this.copyEntity(new HttpPatch(uri), request);
        } else {
            return new HttpGet(uri);
        }
        
        
    }
    
    private HttpUriRequest copyEntity(HttpEntityEnclosingRequestBase redirect, HttpRequest original) {
        if (original instanceof HttpEntityEnclosingRequest) {
            redirect.setEntity(((HttpEntityEnclosingRequest) original).getEntity());
        }
        return redirect;
    }
    
}
