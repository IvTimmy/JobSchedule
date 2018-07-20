package com.baidu.mercurial.sys.job;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

import com.baidu.mercurial.core.service.ServiceManager;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;

/**
 * 调度任务，使用FutureTask以及Callable接口来实现任务回调以及任务调度
 * <p>
 * Created by @wangzexiang on 18/3/2.
 */

public abstract class ScheduleJob<Result, Params, Progress> {
    private static final String TAG = ScheduleJob.class.getSimpleName();
    // 任务优先级
    public static final int PRIORITY_MIN = 10;
    public static final int PRIORITY_NORMAL = 5;
    public static final int PRIORITY_MAX = 1;

    // 主线程ID
    private static final int MESSAGE_UPDATE_PROGRESS = 1;
    private static final int MESSAGE_PRE_EXECUTE = 2;
    private static final int MESSAGE_POST_EXECUTE = 3;
    private static final int MESSAGE_JOB_CANCELLED = 4;

    // 任务状态
    public static final int STATE_INITIAL = 0;   // 任务被创建
    public static final int STATE_RUNNING = 1;  // 任务开始被执行
    public static final int STATE_CANCELLED = 2; // 任务被取消
    public static final int STATE_EXECUTED = 3; // 任务已经执行完成
    private static Handler.Callback sMainCallback = new Handler.Callback() {
        @Override
        public boolean handleMessage(Message message) {
            ScheduleJobNode node = (ScheduleJobNode) message.obj;
            switch (message.what) {
                case MESSAGE_PRE_EXECUTE:
                    node.job.onPreExecute();
                    break;
                case MESSAGE_UPDATE_PROGRESS:
                    node.job.onProgressUpdate(node.data);
                    break;
                case MESSAGE_POST_EXECUTE:
                    node.job.onPostExcute(node.data);
                    break;
                case MESSAGE_JOB_CANCELLED:
                    node.job.onCancelled();
                default:
            }
            return false;
        }
    };
    private static Handler sMainHandler = new Handler(Looper.getMainLooper(), sMainCallback);

    // 异步任务的类型
    private ScheduleJobType mType = ScheduleJobType.BACKGROUND;
    // 当前任务的优先级
    private int mPriority = PRIORITY_NORMAL;
    // 当前任务的状态
    private volatile int mJobState = STATE_INITIAL;
    // 异步任务的Task
    private FutureTask<Result> mFutureTask;
    // 异步任务的Runnable
    private Callable<Result> mCallable;
    // 异步任务参数
    private Params mParams;

    public ScheduleJob() {
        mCallable = new Callable<Result>() {
            @Override
            public Result call() throws Exception {
                Message message = sMainHandler.obtainMessage();
                message.what = MESSAGE_PRE_EXECUTE;
                message.obj = new ScheduleJobNode<>(ScheduleJob.this, null);
                sMainHandler.sendMessage(message);
                mJobState = STATE_RUNNING;
                return doInBackground(mParams);
            }
        };
        mFutureTask = new FutureTask<Result>(mCallable) {

            @Override
            protected void done() {
                super.done();
                if (isCancelled()) {
                    sMainHandler.sendEmptyMessage(MESSAGE_JOB_CANCELLED);
                } else {
                    mJobState = STATE_EXECUTED;
                    Message postResultMessage = sMainHandler.obtainMessage();
                    postResultMessage.what = MESSAGE_POST_EXECUTE;
                    Result result = null;
                    try {
                        result = get();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    } catch (ExecutionException e) {
                        e.printStackTrace();
                    }
                    postResultMessage.obj = new ScheduleJobNode<>(ScheduleJob.this, result);
                    sMainHandler.sendMessage(postResultMessage);
                }
            }
        };
    }

    public void cancel() {
        IJobScheduleService jobScheduleService =
                (IJobScheduleService) ServiceManager.getInstance().getService(ServiceManager.SERVICE_JOB_SCHEDULE);
        if (jobScheduleService != null) {
            jobScheduleService.cancelJob(this);
        }
        mJobState = STATE_CANCELLED;
        if (!mFutureTask.isDone()) {
            mFutureTask.cancel(false);
        }
    }

    public void execute(Params params) {
        if (!canExecute()) {
            return;
        }
        mParams = params;
        IJobScheduleService jobScheduleService =
                (IJobScheduleService) ServiceManager.getInstance().getService(ServiceManager.SERVICE_JOB_SCHEDULE);
        if (jobScheduleService != null) {
            jobScheduleService.enqueue(this);
        }
    }

    /**
     * 任务的参数，在调度时候会回传doInBackground
     *
     * @param params 任务调度参数
     */
    public ScheduleJob setParams(Params params) {
        mParams = params;
        return this;
    }

    protected void updateProgress(Progress progress) {
        Message msg = sMainHandler.obtainMessage();
        msg.what = MESSAGE_UPDATE_PROGRESS;
        msg.obj = new ScheduleJobNode<Progress>(this, progress);
        sMainHandler.sendMessage(msg);
    }

    protected void onPreExecute() {

    }

    protected void onProgressUpdate(Progress progress) {

    }

    protected void onCancelled() {

    }

    protected abstract Result doInBackground(Params params);

    protected abstract void onPostExcute(Result result);

    public final FutureTask getTask() {
        return mFutureTask;
    }

    public ScheduleJob setType(ScheduleJobType type) {
        if (type != null) {
            mType = type;
        }
        return this;
    }

    public ScheduleJobType getType() {
        return mType;
    }

    public boolean canExecute() {
        return mJobState == STATE_INITIAL;
    }

    public ScheduleJob setPriority(int priority) {
        if (priority < PRIORITY_MAX) {
            priority = PRIORITY_MAX;
        } else if (priority > PRIORITY_MIN) {
            priority = PRIORITY_MIN;
        }
        mPriority = priority;
        return this;
    }

    public int getPriority() {
        return mPriority;
    }

    /**
     * 获取当前任务状态
     *
     * @return
     */
    public int getState() {
        return mJobState;
    }

    private static class ScheduleJobNode<Data> {
        public ScheduleJob job;
        public Data data;

        public ScheduleJobNode(ScheduleJob job, Data data) {
            this.job = job;
            this.data = data;
        }
    }
}