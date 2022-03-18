# Hutch

Hutch 是一个利用 quarkus extension 的方式实现的与 https://github.com/wppurking/hutch-schedule 兼容的后端消息任务处理库.

其设计为利用 RabbitMQ 的 topic exchange 进行消息交互, 利用 classic queue 的 DLX + message ttl 机制进行消息重投的类似后端任务系统.
消息定时采取的是梯度式的延时, 因为 RabbitMQ 的限制无法保证秒级别的精准, 只拥有固定梯度的定时延迟.

## 安装

1. 在 quarkus 项目的 build.gradle 中添加 github 上的 packages

```groovy
repositories {
    mavenCentral()
    maven {
        name 'github packages'
        url 'https://maven.pkg.github.com/wppurking/hutch-java'
    }
    mavenLocal()
}
```

2. 添加依赖

```groovy
implementation 'com.easyacc:hutch:1.0.0'
```

## 使用

1. 添加需要的 rabbitmq 的配置, 这里使用 yaml 格式方便阅读

```yaml
quarkus:
  log:
    level: INFO

  # 使用 idea 会进行自动补全
  hutch:
    name: lake_web
    virtual-host: /
    username: guest
    password: guest
    hostname: 127.0.0.1
    port: 5672
```

2. 添加 Consumer 实现 HutchConsumer 接口

```java
public class AbcTest implements HutchConsumer {

  private static final Logger log = Logger.getLogger(AbcTest.class);

  public void onMessage(Message message) {
    log.info(
        "Thraed: {}, msg: {}, prps: {}",
        Thread.currentThread().getName(),
        message.getBodyContentAsString(),
        message.getMessageProperties());
  }

  // 设置最大的重试次数
  @Override
  public int maxRetry() {
    return 5;
  }

  // 设置当前 AbcTest 的 queue 的最大并发 consumers
  @Override
  public int concurrency() {
    return 100;
  }
}
```

4. 自己在代码中进行启动 (后期可能会改为让 quarkus 自行合成好 Hutch bean 实例自行启动与停止)

```java

@ApplicationScoped
public class App {

  @Inject
  HutchConfig config;
  Hutch hutch;

  void onStart(@Observes StartupEvent ev) throws IOException {
    this.hutch = new Hutch(this.config).start();
  }

  void onStop(@Observes ShutdownEvent ev) {
    this.hutch.stop();
  }
}
```

5. 使用 quarkus cli 执行 `quarkus dev` 自动检查依赖并启动.
