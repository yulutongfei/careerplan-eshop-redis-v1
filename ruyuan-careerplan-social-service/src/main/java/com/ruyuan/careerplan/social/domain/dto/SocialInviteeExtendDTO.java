package com.ruyuan.careerplan.social.domain.dto;

import lombok.Data;

/**
 * 参团信息
 *
 * @author zhonghuashishan
 */
@Data
public class SocialInviteeExtendDTO {

    /**
     * 用户ID
     */
    private Long userId;
    
    /**
     * 是否新用户
     */
    private  Boolean newUserFlag;
    
    /**
     * 助力金额随机文案
     */
    private String helpAmountDoc;

    /**
     * 是否额外奖励
     */
    private Boolean premiums;

}
