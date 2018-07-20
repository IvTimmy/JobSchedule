package com.baidu.mercurial.sys.job;

import java.util.LinkedList;
import java.util.TreeMap;

import android.support.annotation.NonNull;

/**
 * 异步任务调度的Tree
 * <p>
 * Created by @wangzexiang on 18/3/6.
 */

public class ScheduleJobRBTree {
    private static final String TAG = ScheduleJobRBTree.class.getSimpleName();
    //  红黑树存储Type对应的任务
    private TreeMap<ScheduleJobInfo, LinkedList<ScheduleJob>> mJobMap;
    //  红黑树运行时间随机生成器,避免在生成节点时因为时间相同红黑树旋转的问题
    private static final int RUMTIME_SEED = 5;

    public ScheduleJobRBTree() {
        // 根据调度时间
        mJobMap = new TreeMap<>();
    }

    public synchronized void enqueue(ScheduleJob job) {
        if (job == null) {
            return;
        }
        ScheduleJobInfo matchKey = null;
        for (ScheduleJobInfo info : mJobMap.keySet()) {
            if (info.mJobType == job.getType()) {
                //  找到对应的KEY
                matchKey = info;
                break;
            }
        }
        if (matchKey == null) {
            // 如果红黑树里面没有的话，则需要创建一个
            matchKey = createNode(job);
        }
        LinkedList<ScheduleJob> jobQueue = mJobMap.get(matchKey);
        insertScheduleJob(job, jobQueue);
    }

    /**
     * 如果原来的红黑树中没有Type对应的节点，则以最少运行时间创建红黑树节点
     *
     * @param job 异步任务
     *
     * @return 异步任务相关的节点
     */
    private ScheduleJobInfo createNode(ScheduleJob job) {
        long scheduleTime = 0;
        if (!mJobMap.isEmpty()) {
            //  如果RBTree不为空，不能直接使用RBTree的最左子节点的时间作为调度时间
            //  因为在红黑树的Rotate的过程中会根据mScheduleTime进行比较进行自平衡
            //  所以需要在调度时间中再加入一个SEED来保证红黑树节点值不同
            //  并且使用最右Node
            scheduleTime = mJobMap.lastKey().mScheuleTime + RUMTIME_SEED;
        }
        ScheduleJobInfo info = new ScheduleJobInfo(scheduleTime, job.getType());
        mJobMap.put(info, new LinkedList<ScheduleJob>());
        return info;
    }

    /**
     * 获取红黑树最左边的节点优先级最高任务运行
     *
     * @return 返回下一个调度的任务
     */
    public synchronized ScheduleJob poll() {
        if (mJobMap.isEmpty()) {
            return null;
        }
        ScheduleJobInfo jobInfo = scheduleNextJobInfo();
        if (jobInfo == null) {
            return null;
        }
        LinkedList<ScheduleJob> jobQueue = mJobMap.get(jobInfo);
        if (jobQueue == null || jobQueue.isEmpty()) {
            return null;
        }
        ScheduleJob first = jobQueue.pollFirst();
        //  如果Poll完，任务队列为空，则从RBTree中删除任务节点
        if (jobQueue.isEmpty()) {
            mJobMap.remove(jobInfo);
        }
        //  将并行任务数量+1
        jobInfo.mJobParallelCount++;
        return first;
    }

    /**
     * 进行下一步任务调度
     *
     * @return 返回所要调度的下一个任务信息
     */
    private ScheduleJobInfo scheduleNextJobInfo() {
        ScheduleJobInfo jobInfo = mJobMap.firstKey();
        //  如果没有最左子节点，说明任务为空
        if (jobInfo == null) {
            return null;
        }
        //  如果最左子节点任务不为空，根据并行度来执行任务
        while (jobInfo != null) {
            //  如果任务在预期的并行度内的话，则直接返回
            if (jobInfo.canExecute()) {
                return jobInfo;
            } else {
                //  如果与预定的并行度不同的话，则取邻近节点判断直到满足要求
                jobInfo = mJobMap.higherKey(jobInfo);
            }
        }
        return null;
    }

    /**
     * 将任务按照优先级插入队列中
     */
    private void insertScheduleJob(ScheduleJob job, LinkedList<ScheduleJob> jobQueue) {
        if (job == null || jobQueue == null) {
            return;
        }
        int priority = job.getPriority();
        int index = 0;
        for (ScheduleJob obj : jobQueue) {
            if (priority <= obj.getPriority()) {
                index++;
            } else {
                break;
            }
        }
        jobQueue.add(index, job);
    }

    /**
     * 更新当前Job对应的Type执行的时间，以便CFS调度
     */
    public synchronized void updateVRuntime(ScheduleJob job, long runtime) {
        if (job == null || runtime <= 0) {
            return;
        }
        ScheduleJobInfo updateJobInfo = null;
        for (ScheduleJobInfo info : mJobMap.keySet()) {
            if (info == null) {
                continue;
            }
            if (info.mJobType == job.getType()) {
                updateJobInfo = info;
                break;
            }
        }
        if (updateJobInfo == null) {
            return;
        }
        // 此处将要更新的JobInfo对象先remove，再Add，维持RBTree的自平衡
        LinkedList<ScheduleJob> list = mJobMap.remove(updateJobInfo);
        // 需要在此处更新调度时间，不能在循环里更新
        // 因为在remove的时候是根据二叉搜索树进行搜索的,更新完之后会导致查找不到对应的节点
        updateJobInfo.updateScheduleTime(runtime);
        // 任务执行完之后，并行任务数量减少
        updateJobInfo.mJobParallelCount--;
        // 更新完时间后，重新插入RBTree，插入RBTree后，自平衡
        mJobMap.put(updateJobInfo, list);
    }

    private static class ScheduleJobInfo implements Comparable<ScheduleJobInfo> {
        long mScheuleTime;
        int mJobParallelCount;
        ScheduleJobType mJobType;

        private ScheduleJobInfo(long scheduleTime, ScheduleJobType type) {
            mScheuleTime = scheduleTime;
            mJobType = type;
        }

        private void updateScheduleTime(long deltaExcuteTime) {
            //  参考Linux的CFS调度算法执行
            mScheuleTime += deltaExcuteTime * mJobType.mWeight;
        }

        private boolean canExecute() {
            return mJobParallelCount < mJobType.mParallel;
        }

        @Override
        public int compareTo(@NonNull ScheduleJobInfo node) {
            //  红黑树节点存储比较
            if (this == node || node.mJobType == mJobType) {
                return 0;
            }
            if (mScheuleTime <= node.mScheuleTime) {
                return -1;
            }
            return 1;
        }
    }
}