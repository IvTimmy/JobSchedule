package com.baidu.mercurial.sys.job;

/**
 * 异步任务类型，其中包括异步任务的类型以及权重属性
 * Created by @wangzexiang on 18/3/6.
 */

public enum ScheduleJobType {
    FOREGROUND(ScheduleJobConfig.TYPE_FOREGROUND, 1.0f, 6),
    BACKGROUND(ScheduleJobConfig.TYPE_BACKGROUND, 1.6f, 6),
    BACGRKOUND_IO(ScheduleJobConfig.TYPE_BACKGROUND_IO, 2.0f, 4),
    IMMEDIATE(ScheduleJobConfig.TYPE_IMMEDIATE, 1.0f, 1);

    //  异步任务的类型
    int mType;
    //  异步任务执行的权重
    float mWeight;
    //  异步任务的并行度，任务同时并行最大数量
    int mParallel;

    ScheduleJobType(int type, float weight, int parallel) {
        mType = type;
        mWeight = weight;
        mParallel = parallel;
    }

    public int getType() {
        return mType;
    }

    public float getWeight() {
        return mWeight;
    }

    public int getParallel() {
        return mParallel;
    }

    @Override
    public String toString() {
        switch (mType) {
            case ScheduleJobConfig.TYPE_FOREGROUND:
                return "TYPE_FOREGROUND";
            case ScheduleJobConfig.TYPE_BACKGROUND:
                return "TYPE_BACKGROUND";
            case ScheduleJobConfig.TYPE_BACKGROUND_IO:
                return "TYPE_BACKGROUND_IO";
            case ScheduleJobConfig.TYPE_IMMEDIATE:
                return "TYPE_IMMEDIATE";
            default:
        }
        return super.toString();
    }
}
