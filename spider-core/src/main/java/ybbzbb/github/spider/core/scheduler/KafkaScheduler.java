package ybbzbb.github.spider.core.scheduler;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ListTopicsResult;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import ybbzbb.github.spider.core.Request;


/**
 * 使用 kafka 作为任务队列
 * */
public class KafkaScheduler  {
//
//    private KafkaTemplate<String, String> kafkaTemplate;
//
//    private KafkaAdmin kafkaAdmin;
//
//    @AllArgsConstructor
//    @Getter
//    enum Priority{
//        high(0) , medium(1) , low(2);
//
//        private int code;
//    }
//
//    public KafkaScheduler(KafkaTemplate<String, String> kafkaTemplate) {
//        this.kafkaTemplate = kafkaTemplate;
//
//        try(final AdminClient client = AdminClient.create(kafkaAdmin.getConfigurationProperties())){
//            final ListTopicsResult listTopics = client.listTopics();
//        }
//
//    }
//
//    @Override
//    public void push(Request request, Site task) {
//
//
//    }
//
//    @Override
//    public void retry(Request request, Site task) {
//
//    }
//
//    @Override
//    public void pull(Request request, Site task) {
//
//    }
}
