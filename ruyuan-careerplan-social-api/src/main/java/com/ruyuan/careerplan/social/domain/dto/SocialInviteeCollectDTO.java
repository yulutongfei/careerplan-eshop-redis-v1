package com.ruyuan.careerplan.social.domain.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class SocialInviteeCollectDTO implements Serializable {

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 助力人ID
     */
    private Long inviteeId;

    /**
     * 助力人昵称
     */
    private String inviteeNickName;

    /**
     * 头像
     */
    private String inviteeAvatar;

    /**
     * 助力金额 单位分
     */
    private Integer helpAmount;

    /**
     * 是否新用户
     */
    private  Boolean newUserFlag;

    /**
     * 助力金额随机文案
     */
    private String helpAmountDoc;

    /**
     * 优惠券券码
     */
    private String couponCode;

    /**
     * 优惠券跳转地址
     */
    private String couponUrl;

    /**
     * 是不是自己
     */
    private Boolean oneself;

    /**
     * 是否加速火箭
     */
    private Boolean premiums;


}
