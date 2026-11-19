# Hutch

Hutch 是一个利用 quarkus extension 的方式实现的与 https://github.com/wppurking/hutch-schedule
兼容的后端消息任务处理库.

其设计为利用 RabbitMQ 的 topic exchange 进行消息交互, 利用 classic queue 的 DLX + message ttl
机制进行消息重投的类似后端任务系统.
消息定时采取的是梯度式的延时, 因为 RabbitMQ 的限制无法保证秒级别的精准, 只拥有固定梯度的定时延迟.

## 安装

1. 在 quarkus 项目的 build.gradle 中添加内网 Nexus 私服

```groovy
// credentials 取自 ~/.gradle/gradle.properties: nexusUsername / nexusPassword
repositories {
    mavenCentral()
    maven {
        name 'nexus'
        url 'https://lib.dev.lanto.co/repository/maven-public/'
        credentials {
            username = findProperty('nexusUsername') ?: System.getenv('NEXUS_USERNAME')
            password = findProperty('nexusPassword') ?: System.getenv('NEXUS_PASSWORD')
        }
    }
    mavenLocal()
}
```

2. 添加依赖

```groovy
implementation 'com.easyacc:hutch:4.0.3'
```

内网 Nexus 的 `maven-public` 匿名请求会返回 401, 需要账号（建议用只读账号, 不要用发布用的 deployer）,
在 `~/.gradle/gradle.properties` 或环境变量 `NEXUS_USERNAME` / `NEXUS_PASSWORD` 中配置:

```properties
nexusUsername=xxxxx
nexusPassword=xxxxx
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
    # 是否使用 quorum queue
    quorum: false
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

## Quorum Queue

在 hutch-java 中, 所有涉及到的 queue 只能同时使用一种类型的 queue type, 要么全部都是默认的 classic
queue, 要么就全部切换到集群方式的
quorum queue. 不在 HutchConsumer 级别做 quorum queue 申明的原因有:

1. 对 rabbitmq 版本到 3.10 以后, quorum queue 与 classic queue
   的区别已经[非常小](https://www.rabbitmq.com/quorum-queues.html)
2. quorum queue 可以在单节点也可以在多节点上使用
3. quorum queue 的高可用性非常用于代替 classic 是非常大的吸引力
4. 如果是产品环境, 大多数情况都应该部署高可用环境, 同时使用 quorum queue

所以, 在全局做了一个 quorum queue 开启关闭的开关.

## Maven

1. 测试: `./mvnw test`. 每次构建一定要确保测试通过.
2. 构建到本地: `./mvnw install -DskipTests`
3. 推送远程的内网 Nexus: `./mvnw deploy -DskipTests`

### 推送到内网 Nexus

`~/.m2/settings.xml` 中配置好 Nexus 的凭据（id 必须与 pom.xml 中的
`nexus-releases` / `nexus-snapshots` 一致）:

```xml
<settings>
  <servers>
    <server>
      <id>nexus</id>
      <username>deployer</username>
      <password>******</password>
    </server>
    <server>
      <id>nexus-releases</id>
      <username>deployer</username>
      <password>******</password>
    </server>
    <server>
      <id>nexus-snapshots</id>
      <username>deployer</username>
      <password>******</password>
    </server>
  </servers>

  <mirrors>
    <mirror>
      <id>nexus</id>
      <mirrorOf>*,!nexus-releases,!nexus-snapshots</mirrorOf>
      <url>https://lib.dev.lanto.co/repository/maven-public/</url>
    </mirror>
  </mirrors>
</settings>
```

注意:

- 发布账号需同时拥有 `maven-releases` 与 `maven-snapshots` 的写权限（Nexus 里 `nx-repository-view-maven2-maven-releases-*`、
  `nx-repository-view-maven2-maven-snapshots-*`）。
- `x.y.z-SNAPSHOT` 版本会自动推到 `maven-snapshots`，正式版本推到 `maven-releases`。
- 正式版本能否重复发布取决于 Nexus 上 `maven-releases` 的 Deployment policy（默认 `Disable redeploy`）; 若被禁止，发布前请先升版本号。

### TEST

1. 准备 vhost: test 与 test_quorum
