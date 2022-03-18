# Hutch

Hutch 是一个利用 quarkus extension 的方式实现的与 https://github.com/wppurking/hutch-schedule 兼容的后端消息任务处理库.

其设计为利用 RabbitMQ 的 topic exchange 进行消息交互, 利用 classic queue 的 DLX + message ttl 机制进行消息重投的类似后端任务系统.
消息定时采取的是梯度式的延时, 因为 RabbitMQ 的限制无法保证秒级别的精准, 只拥有固定梯度的定时延迟.

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
