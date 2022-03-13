package com.ruyuan.careerplan.cookbook.service.impl;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ruyuan.careerplan.common.core.JsonResult;
import com.ruyuan.careerplan.common.exception.BaseBizException;
import com.ruyuan.careerplan.common.redis.RedisCache;
import com.ruyuan.careerplan.common.redis.RedisLock;
import com.ruyuan.careerplan.common.utils.JsonUtil;
import com.ruyuan.careerplan.common.utils.RandomUtil;
import com.ruyuan.careerplan.cookbook.cache.CacheSupport;
import com.ruyuan.careerplan.cookbook.constants.CookbookConstants;
import com.ruyuan.careerplan.cookbook.constants.RedisKeyConstants;
import com.ruyuan.careerplan.cookbook.converter.SkuInfoConverter;
import com.ruyuan.careerplan.cookbook.dao.SkuInfoDAO;
import com.ruyuan.careerplan.cookbook.domain.dto.SkuInfoDTO;
import com.ruyuan.careerplan.cookbook.domain.entity.SkuInfoDO;
import com.ruyuan.careerplan.cookbook.domain.request.SkuInfoQueryRequest;
import com.ruyuan.careerplan.cookbook.domain.request.SkuSaleableRequest;
import com.ruyuan.careerplan.cookbook.service.GoodsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 商品服务
 *
 * @author zhonghuashishan
 */
@Slf4j
@Service
public class GoodsServiceImpl implements GoodsService {

    @Autowired
    private SkuInfoDAO skuInfoDAO;

    @Autowired
    private SkuInfoConverter skuInfoConverter;

    @Autowired
    private RedisCache redisCache;

    @Autowired
    private RedisLock redisLock;

    @Override
    public SkuInfoDTO getSkuInfoBySkuId(SkuInfoQueryRequest request) {
        return getSkuInfoBySkuId(request.getSkuId());
    }

    private SkuInfoDTO getSkuInfoBySkuId(String skuId) {
        String skuInfoJsonString = redisCache.get(RedisKeyConstants.GOODS_INFO_PREFIX + skuId);
        log.info("从缓存中获取商品信息,skuId:{},value:{}", skuId, skuInfoJsonString);
        if (StringUtils.hasLength(skuInfoJsonString)) {
            // 防止缓存穿透
            if (Objects.equals(CacheSupport.EMPTY_CACHE, skuInfoJsonString)) {
                return null;
            }
            redisCache.expire(RedisKeyConstants.GOODS_INFO_PREFIX + skuId,
                    CacheSupport.generateCacheExpireSecond());
            return JsonUtil.json2Object(skuInfoJsonString, SkuInfoDTO.class);
        }

        return getSkuInfoBySkuIdFromDB(skuId);
    }

    private SkuInfoDTO getSkuInfoBySkuIdFromDB(String skuId) {
        String skuInfoLockKey = RedisKeyConstants.GOODS_LOCK_PREFIX + skuId;
        boolean lock = redisLock.lock(skuInfoLockKey);

        if (!lock) {
            log.info("缓存数据为空，从数据库查询商品信息时获取锁失败，skuId:{}", skuId);
            throw new BaseBizException("查询失败");
        }
        try {
            log.info("缓存数据为空，从数据库中获取数据，skuId:{}", skuId);
            LambdaQueryWrapper<SkuInfoDO> queryWrapper = Wrappers.lambdaQuery();
            queryWrapper.eq(SkuInfoDO::getSkuId, skuId);

            SkuInfoDO skuInfoDO = skuInfoDAO.getOne(queryWrapper);
            String goodsInfoKey = RedisKeyConstants.GOODS_INFO_PREFIX + skuId;
            if (Objects.isNull(skuInfoDO)) {
                /*
                 * 如果商品编码对应的商品一开始不存在，设置空缓存，防止缓存穿透，
                 * 后来增加了对应的商品编码的商品，需要覆盖当前缓存值
                 * 这里没有做商品的维护，所以不涉及。
                 * 在菜谱服务中有体现。
                 */
                redisCache.set(goodsInfoKey, CacheSupport.EMPTY_CACHE, CacheSupport.generateCachePenetrationExpireSecond());
                return null;
            }

            SkuInfoDTO dto = skuInfoConverter.convertSkuInfoDTO(skuInfoDO);
            dto.setSkuImage(JSON.parseArray(skuInfoDO.getSkuImage(), SkuInfoDTO.ImageInfo.class));
            dto.setDetailImage(JSON.parseArray(skuInfoDO.getDetailImage(), SkuInfoDTO.ImageInfo.class));

            // 设置缓存过期时间，2天加上随机几小时
            redisCache.set(goodsInfoKey, JsonUtil.object2Json(dto), CacheSupport.generateCacheExpireSecond());

            return dto;
        } finally {
            redisLock.unlock(skuInfoLockKey);
        }
    }

    @Override
    public List<SkuInfoDTO> listSkuInfo(SkuInfoQueryRequest request) {
        List<SkuInfoDTO> skuInfoDTOS = new ArrayList<>();
        for (String skuId : request.getSkuIds()) {
            SkuInfoDTO skuInfoDTO = getSkuInfoBySkuId(skuId);
            if (Objects.nonNull(skuInfoDTO)){
                skuInfoDTOS.add(skuInfoDTO);
            }
        }
        return skuInfoDTOS;
    }

    /**
     * TODO 待添加值，待修改
     *
     */
    private static final Map<String, List<SkuInfoDTO>> map = new HashMap<>();

    @Override
    public List<SkuInfoDTO> getSkuInfoByTags(List<String> tags) {
        List<SkuInfoDTO> skuInfoDTOS = new ArrayList<>();
        for (String tag : tags) {
            List<SkuInfoDTO> skuInfoDTOList = map.get(tag);
            if (Objects.nonNull(skuInfoDTOList)) {
                skuInfoDTOS.addAll(skuInfoDTOList);
            }
        }
        return skuInfoDTOS;
    }

    @Override
    public Boolean skuIsSaleable(SkuSaleableRequest request) {
        // todo 商品校验实现，依赖库存接口，这个逻辑在第二次版本中实现，这个版本中直接返回true


        return true;
    }
}
