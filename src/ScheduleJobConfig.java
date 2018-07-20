package com.baidu.mercurial.sys.job;

/**
 * 异步任务调度的策略
 * Created by @wangzexiang on 18/3/6.
 */

interface ScheduleJobConfig {
    // 异步任务类型
    int TYPE_FOREGROUND = 1;
    int TYPE_BACKGROUND = 2;
    int TYPE_BACKGROUND_IO = 3;
    int TYPE_IMMEDIATE = 4;
}
