package com.easyacc.hutch.publisher;

/**
 * 将 Hutch 需要对三方 api 进行 ratelimite 的操作独立出来, 不与标准的 Hutch.publish 共用.
 *
 * <pre>
 *   核心的设计思路为:
 *   1. 用户通过统一的 HutchLimiter 的入口对需要进行 ratelimit 的任务进行提交
 *   2. 提交的任务会进入 redis 中进行 buffer 与存储, 等待额外的 driver job 根据设定的 limit 去获取并发送任务到 mq
 *   3. 为每一个使用了 Threshold 的 Consumer 注册一个新的 Driver Job, 根据设置的频率与任务量进行获取与调度
 * </pre>
 */
public class LimitPublisher {}
