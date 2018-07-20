package com.baidu.mercurial.sys.job;

import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.baidu.mercurial.core.service.ServiceManager;

import android.os.SystemClock;
import android.support.annotation.NonNull;

/**
 * 异步任务调度的Service
 * <p>
 * Created by @wangzexiang on 18/3/2.
 */

public class JobScheduleService implements IJobScheduleService {
    private static final String TAG = JobScheduleService.class.getSimpleName();
    // 核心线程数量
    private static final int THREAD_POOL_CORE_THREAD = 16;
    // 最大线程数量
    private static final int THREAD_POOL_MAX_THREAD = 64;
    // 空闲线程最大存活时间30S
    private static final long THREAD_POOL_KEEPALIVE_TIME = 30;
    private AtomicInteger mWorkerCount = new AtomicInteger();
    private ThreadPoolExecutor mExecutor;
    private static JobScheduleService sInstance = new JobScheduleService();
    private ScheduleJobRBTree mJobRBTree;

    public static JobScheduleService getInstance() {
        return sInstance;
    }

    private JobScheduleService() {
        mExecutor = new ThreadPoolExecutor(THREAD_POOL_CORE_THREAD, THREAD_POOL_MAX_THREAD, THREAD_POOL_KEEPALIVE_TIME,
                TimeUnit.SECONDS, new SynchronousQueue<Runnable>(), new JobThreadFactory());
        mJobRBTree = new ScheduleJobRBTree();
    }

    @Override
    public void enqueue(ScheduleJob job) {
        if (job == null || !job.canExecute()) {
            return;
        }
        if (job.getType() == ScheduleJobType.IMMEDIATE) {
            // 如果任务的Type是立即执行的话，则使用线程池开启新线程执行
            mExecutor.execute(job.getTask());
        } else {
            // 如果不是立即执行，则加入队列，然后接受调度策略调度
            mJobRBTree.enqueue(job);
            int workerCount = mWorkerCount.get();
            // 当运行的Worker小于16个的时候，才会创建线程
            if (workerCount < THREAD_POOL_CORE_THREAD) {
                mExecutor.execute(new Worker());
                mWorkerCount.incrementAndGet();
            }
        }
    }

    @Override
    public void cancelJob(ScheduleJob job) {

    }

    @Override
    public int getServiceNum() {
        return ServiceManager.SERVICE_JOB_SCHEDULE;
    }

    private class Worker implements Runnable {

        @Override
        public void run() {
            ScheduleJob job;
            while ((job = mJobRBTree.poll()) != null) {
                long taskStartTime = SystemClock.elapsedRealtime();
                job.getTask().run();
                long taskEndTime = SystemClock.elapsedRealtime();
                long deltaExecTime = taskEndTime - taskStartTime;
                mJobRBTree.updateVRuntime(job, deltaExecTime);
            }
            mWorkerCount.decrementAndGet();
        }
    }

    private static class JobThreadFactory implements ThreadFactory {
        private int mThreadCount = 0;

        @Override
        public Thread newThread(@NonNull Runnable runnable) {
            Thread thread = new Thread(runnable);
            thread.setName("JobScheduleThread@" + mThreadCount++);
            return thread;
        }
    }
}
