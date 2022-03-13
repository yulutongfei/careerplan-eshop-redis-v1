package com.ruyuan.careerplan.cookbook.controller;

import com.ruyuan.careerplan.common.core.JsonResult;
import com.ruyuan.careerplan.common.page.PagingInfo;
import com.ruyuan.careerplan.cookbook.annotation.ParamsValidate;
import com.ruyuan.careerplan.cookbook.domain.dto.SkuInfoDTO;
import com.ruyuan.careerplan.cookbook.domain.request.SkuInfoQueryRequest;
import com.ruyuan.careerplan.cookbook.service.GoodsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * @author zhonghuashishan
 */
@RestController
@RequestMapping("/api/goods")
public class GoodsController {

    @Autowired
    private GoodsService goodsService;

    @RequestMapping("/info/{skuId}")
    public JsonResult<SkuInfoDTO> getSkuInfoBySkuId(@PathVariable String skuId){
        SkuInfoQueryRequest request = SkuInfoQueryRequest.builder()
                .skuId(skuId)
                .build();
        SkuInfoDTO dto = goodsService.getSkuInfoBySkuId(request);
        return JsonResult.buildSuccess(dto);
    }

    @RequestMapping("/list")
    public JsonResult<List<SkuInfoDTO>> listSkuInfo(@RequestBody SkuInfoQueryRequest request){
        List<SkuInfoDTO> skuInfoDTOS = goodsService.listSkuInfo(request);
        return JsonResult.buildSuccess(skuInfoDTOS);
    }

}
