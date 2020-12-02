package ybbzbb.github.spider.crawl.download;


import lombok.Getter;
import lombok.Setter;
import ybbzbb.github.spider.core.Page;
import ybbzbb.github.spider.core.Request;
import ybbzbb.github.spider.core.Site;
import ybbzbb.github.spider.core.selector.Html;
import ybbzbb.github.spider.core.util.HttpConstant;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;


/**
 * Base class of downloader with some common methods.
 *
 * @author code4crafter@gmail.com
 * @since 0.5.0
 */
@Getter
@Setter
public abstract class AbstractDownloader implements Downloader {

    private Set<Integer> acceptStatCodes =  new HashSet<Integer>(Arrays.asList(HttpConstant.StatusCode.CODE_200));;

    /**
     * A simple method to download a url.
     *
     * @param url url
     * @return html
     */
    public Html download(String url) {
        return download(url, null);
    }

    /**
     * A simple method to download a url.
     *
     * @param url url
     * @param charset charset
     * @return html
     */
    public Html download(String url, String charset) {
        Page page = download(new Request(url), Site.me().setCharset(charset).toTask());
        return (Html) page.getHtml();
    }



    protected void onSuccess(Request request) {
    }

    protected void onError(Request request) {
    }


    public Set<Integer> getAcceptStatCodes() {
        return acceptStatCodes;
    }
}
