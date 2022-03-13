package com.ruyuan.careerplan.cookbook;

import com.google.common.collect.Lists;
import com.ruyuan.careerplan.common.page.PagingInfo;
import com.ruyuan.careerplan.common.utils.JsonUtil;
import com.ruyuan.careerplan.cookbook.domain.dto.SkuInfoDTO;
import com.ruyuan.careerplan.cookbook.domain.request.SkuInfoQueryRequest;
import com.ruyuan.careerplan.cookbook.service.GoodsService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.List;

@SpringBootTest(classes = CookbookApplication.class)
@RunWith(SpringJUnit4ClassRunner.class)
public class GoodsServiceTest {

    @Autowired
    private GoodsService goodsService;

    @Test
    public void getSkuInfoBySkuId() {
        SkuInfoQueryRequest request = SkuInfoQueryRequest.builder()
                .skuId("6000000011")
                .build();

        SkuInfoDTO dto = goodsService.getSkuInfoBySkuId(request);
        System.out.println(JsonUtil.object2Json(dto));
    }


    @Test
    public void listSkuInfo() {
        SkuInfoQueryRequest request = SkuInfoQueryRequest.builder()
                .skuIds(Lists.newArrayList("6000000011", "6000000121"))
                .build();

        List<SkuInfoDTO> skuInfoDTOS = goodsService.listSkuInfo(request);
        System.out.println(JsonUtil.object2Json(skuInfoDTOS));
    }

}