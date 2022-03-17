# Hutch-java

与 https://github.com/wppurking/hutch-schedule 类似, 只是在 Quarkus 平台上 Java 的实现.

## 安装

TODO: 为项目 Quarkus 项目添加当前 extension 的依赖

## Usage

They will do something below:

1. Declear an topic exchange called `<hutch>.schedule` just for routing message to <hutch>_
   delay_queue_<5s>.
2. Declear an queue named `<hutch>_delay_queue_<5s>` and with some params:
   - Set `x-dead-letter-exchange: <hutch>`: let queue republish message to default <hutch>
     exchange.
   - Set `x-message-ttl: <30.days>`: to avoid the queue is to large, because there is no consumer
     with this queue.

## HutchConsumer interface

Let consumer class to implement `HutchConsumer` then it has the ability of publishing message to
RabbitMQ with the `consume '<routing_key>'`.
