package com.ruyuan.careerplan.cookbook.service;

import com.ruyuan.careerplan.common.page.PagingInfo;
import com.ruyuan.careerplan.cookbook.domain.dto.SkuInfoDTO;
import com.ruyuan.careerplan.cookbook.domain.request.SkuInfoQueryRequest;
import com.ruyuan.careerplan.cookbook.domain.request.SkuSaleableRequest;

import java.util.List;

/**
 * 商品服务
 *
 * @author zhonghuashishan
 */
public interface GoodsService {

    /**
     * 根据商品编码获取商品详情
     *
     * @param request
     * @return
     */
    SkuInfoDTO getSkuInfoBySkuId(SkuInfoQueryRequest request);

    /**
     * 获取商品列表
     *
     * @param request
     * @return
     */
    List<SkuInfoDTO> listSkuInfo(SkuInfoQueryRequest request);

    /**
     * 根据标签查询商品信息
     * @param tags
     * @return
     */
    List<SkuInfoDTO> getSkuInfoByTags(List<String> tags);

    /**
     * 校验商品是否可售：库存、上下架
     * @param request
     * @return
     */
    Boolean skuIsSaleable(SkuSaleableRequest request);
}
