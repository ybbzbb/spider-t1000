package ybbzbb.github.spider.core.statistic;


import ybbzbb.github.spider.core.Request;
import ybbzbb.github.spider.core.Task;

import java.util.List;

public interface Statistics {

    void addAll(Task task, CountTypeEnums countType, List<Request> requests) ;

    void add(Task task, CountTypeEnums countType, Request requests) ;

}
