package com.ruyuan.careerplan.cookbook.mq.consumer;

import com.ruyuan.careerplan.cookbook.constants.RocketMqConstant;
import com.ruyuan.careerplan.cookbook.mq.config.RocketMQProperties;
import com.ruyuan.careerplan.cookbook.mq.consumer.listener.CookbookCartUpdateListener;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.exception.MQClientException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


/**
 * @author zhonghuashishan
 * @version 1.0
 */
@Configuration
public class ConsumerBeanConfig {

    /**
     * 配置内容对象
     */
    @Autowired
    private RocketMQProperties rocketMQProperties;

    /**
     * 购物车商品更新消费者
     * @param cookbookCartUpdateListener
     * @return
     * @throws MQClientException
     */
    @Bean("cookbookCartAsyncUpdateTopic")
    public DefaultMQPushConsumer receiveCartUpdateConsumer(CookbookCartUpdateListener cookbookCartUpdateListener) throws MQClientException {
        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer(RocketMqConstant.COOKBOOK_DEFAULT_CONSUMER_GROUP);
        consumer.setNamesrvAddr(rocketMQProperties.getNameServer());
        consumer.subscribe(RocketMqConstant.COOKBOOK_UPDATE_MESSAGE_TOPIC, "*");
        consumer.registerMessageListener(cookbookCartUpdateListener);
        consumer.start();
        return consumer;
    }

}
