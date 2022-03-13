package com.ruyuan.careerplan.cookbook.domain.dto;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.util.Set;

/**
 * 新增/修改菜谱返回结果
 *
 * @author zhonghuashishan
 */
@Data
@Builder
public class SaveOrUpdateCookbookDTO implements Serializable {

    private Boolean success;
}