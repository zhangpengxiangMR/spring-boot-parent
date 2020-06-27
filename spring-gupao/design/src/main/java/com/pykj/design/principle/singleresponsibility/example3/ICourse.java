package com.pykj.design.principle.singleresponsibility.example3;

/**
 * @description: TODO
 * @author: zhangpengxiang
 * @time: 2020/4/20 0:09
 */
public interface ICourse {

    String getCourseName();

    //获得视频流
    byte[] getCourseVideo();

    //学习课程
    void studyCourse();

    //退款
    void refundCourse();

}
