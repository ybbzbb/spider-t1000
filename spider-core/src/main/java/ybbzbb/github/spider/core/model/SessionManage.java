package ybbzbb.github.spider.core.model;


public interface SessionManage{

    void overdue(SessionAO session, String site);

    SessionAO getSessionId(String site);

    boolean needSession(String url);

    void retainSessionBySite(String site);
}
