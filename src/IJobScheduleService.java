package com.baidu.mercurial.sys.job;

import com.baidu.mercurial.core.service.IServiceInterface;
import com.baidu.mercurial.sys.job.ScheduleJob;

/**
 * 异步任务调度Service
 * Created by @wangzexiang on 18/3/2.
 */

public interface IJobScheduleService extends IServiceInterface {

    void enqueue(ScheduleJob job);

    void cancelJob(ScheduleJob job);
}
