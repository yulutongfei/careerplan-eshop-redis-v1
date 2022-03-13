package com.ruyuan.careerplan.cookbook.service.impl;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ruyuan.careerplan.common.enums.DeleteStatusEnum;
import com.ruyuan.careerplan.common.exception.BaseBizException;
import com.ruyuan.careerplan.common.page.PagingInfo;
import com.ruyuan.careerplan.common.redis.RedisCache;
import com.ruyuan.careerplan.common.redis.RedisLock;
import com.ruyuan.careerplan.common.utils.JsonUtil;
import com.ruyuan.careerplan.cookbook.cache.CacheSupport;
import com.ruyuan.careerplan.cookbook.constants.RedisKeyConstants;
import com.ruyuan.careerplan.cookbook.constants.RocketMqConstant;
import com.ruyuan.careerplan.cookbook.converter.CookbookConverter;
import com.ruyuan.careerplan.cookbook.dao.CookbookDAO;
import com.ruyuan.careerplan.cookbook.dao.CookbookSkuRelationDAO;
import com.ruyuan.careerplan.cookbook.dao.CookbookUserDAO;
import com.ruyuan.careerplan.cookbook.domain.dto.CookbookDTO;
import com.ruyuan.careerplan.cookbook.domain.dto.Food;
import com.ruyuan.careerplan.cookbook.domain.dto.SaveOrUpdateCookbookDTO;
import com.ruyuan.careerplan.cookbook.domain.dto.SkuInfoDTO;
import com.ruyuan.careerplan.cookbook.domain.dto.StepDetail;
import com.ruyuan.careerplan.cookbook.domain.entity.CookbookDO;
import com.ruyuan.careerplan.cookbook.domain.entity.CookbookSkuRelationDO;
import com.ruyuan.careerplan.cookbook.domain.entity.CookbookUserDO;
import com.ruyuan.careerplan.cookbook.domain.request.CookbookQueryRequest;
import com.ruyuan.careerplan.cookbook.domain.request.SaveOrUpdateCookbookRequest;
import com.ruyuan.careerplan.cookbook.message.CookbookUpdateMessage;
import com.ruyuan.careerplan.cookbook.mq.producer.DefaultProducer;
import com.ruyuan.careerplan.cookbook.service.CookbookService;
import com.ruyuan.careerplan.cookbook.service.GoodsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 菜谱服务
 *
 * @author zhonghuashishan
 */
@Service
@Slf4j
public class CookbookServiceImpl implements CookbookService {

    private static final long USER_COOKBOOK_LOCK_TIMEOUT = 200;
    private static final long COOKBOOK_UPDATE_LOCK_TIMEOUT = 200;

    @Autowired
    private RedisCache redisCache;

    @Autowired
    private RedisLock redisLock;

    @Autowired
    private CookbookDAO cookbookDAO;

    @Autowired
    private CookbookSkuRelationDAO cookbookSkuRelationDAO;

    @Autowired
    private CookbookUserDAO cookbookUserDAO;

    @Autowired
    private CookbookConverter cookbookConverter;

    @Autowired
    private GoodsService goodsService;

    @Autowired
    private DefaultProducer defaultProducer;

    @Transactional(rollbackFor = Exception.class)
    @Override
    public SaveOrUpdateCookbookDTO saveOrUpdateCookbook(SaveOrUpdateCookbookRequest request) {
        String cookbookUpdateLockKey = RedisKeyConstants.COOKBOOK_UPDATE_LOCK_PREFIX + request.getId();

        Boolean lock = null;

        if(request.getId() != null && request.getId() > 0) {
            lock = redisLock.lock(cookbookUpdateLockKey);
        }

        if (lock != null && !lock) {
            log.info("操作菜谱获取锁失败，operator:{}", request.getOperator());
            throw new BaseBizException("新增/修改失败");
        }

        try {
            // 构建菜谱信息
            CookbookDO cookbookDO = buildCookbookDO(request);

            // 保存菜谱信息
            // 菜谱 = 美食分享，关于美食、菜品，图，视频，如何做，原材料，信息
            cookbookDAO.saveOrUpdate(cookbookDO);
            // 菜谱商品关联信息，一个菜谱可以种草多个商品，可以保存菜品跟多个商品关联关系
            List<CookbookSkuRelationDO> cookbookSkuRelationDOS = buildCookbookSkuRelationDOS(cookbookDO, request);
            // 保存菜谱商品关联信息
            cookbookSkuRelationDAO.saveBatch(cookbookSkuRelationDOS);

            // 更新缓存信息
            updateCookbookCache(cookbookDO, request);

            // 发布菜谱数据更新事件消息
            publishCookbookUpdatedEvent(cookbookDO);

            // 返回信息
            SaveOrUpdateCookbookDTO dto = SaveOrUpdateCookbookDTO.builder()
                    .success(true)
                    .build();
            return dto;
        }finally {
            if(lock != null) {
                redisLock.unlock(cookbookUpdateLockKey);
            }
        }
    }

    private void publishCookbookUpdatedEvent(CookbookDO cookbookDO) {
        // 发消息通知作者的菜谱信息变更
        CookbookUpdateMessage message = CookbookUpdateMessage.builder()
                .cookbookId(cookbookDO.getId())
                .userId(cookbookDO.getUserId())
                .build();
        defaultProducer.sendMessage(RocketMqConstant.COOKBOOK_UPDATE_MESSAGE_TOPIC,
                JsonUtil.object2Json(message), "作者菜谱变更消息");
    }


    private CookbookDO buildCookbookDO(SaveOrUpdateCookbookRequest request) {
        CookbookDO cookbookDO = cookbookConverter.convertCookbookDO(request);
        cookbookDO.setFoods(JsonUtil.object2Json(request.getFoods()));
        cookbookDO.setCookbookDetail(JsonUtil.object2Json(request.getCookbookDetail()));
        cookbookDO.setUpdateUser(request.getOperator());

        // 新增数据
        if (Objects.isNull(cookbookDO.getId())) {
            // 菜谱状态为空，则设置为未删除
            if (Objects.isNull(cookbookDO.getCookbookStatus())) {
                cookbookDO.setCookbookStatus(DeleteStatusEnum.NO.getCode());
            }
            // 设置创建人
            cookbookDO.setCreateUser(request.getOperator());
        }
        return cookbookDO;
    }

    private void updateCookbookCache(CookbookDO cookbookDO, SaveOrUpdateCookbookRequest request) {
        CookbookDTO cookbookDTO = buildCookbookDTO(cookbookDO, request.getSkuIds());

        // 修改菜谱信息缓存数据
        String cookbookKey = RedisKeyConstants.COOKBOOK_PREFIX + cookbookDO.getId();
        redisCache.set(cookbookKey, JsonUtil.object2Json(cookbookDTO), CacheSupport.generateCacheExpireSecond());

        String userCookbookCountKey = RedisKeyConstants.USER_COOKBOOK_COUNT_PREFIX + request.getUserId();
        redisCache.increment(userCookbookCountKey, 1);
    }

    private CookbookDTO buildCookbookDTO(CookbookDO cookbookDO, List<String> skuIds) {
        CookbookDTO cookbookDTO = cookbookConverter.convertCookbookDTO(cookbookDO);
        CookbookUserDO userDO = cookbookUserDAO.getById(cookbookDO.getUserId());
        cookbookDTO.setUserName(userDO.getUserName());
        cookbookDTO.setCookbookDetail(
                JSON.parseArray(cookbookDO.getCookbookDetail(), StepDetail.class));
        cookbookDTO.setFoods(JSON.parseArray(cookbookDO.getFoods(), Food.class));
        cookbookDTO.setSkuIds(skuIds);
        return cookbookDTO;
    }

    private List<CookbookSkuRelationDO> buildCookbookSkuRelationDOS(CookbookDO cookbookDO, SaveOrUpdateCookbookRequest request) {
        List<String> tags = request.getFoods().stream().map(food -> food.getTag()).collect(Collectors.toList());

        List<SkuInfoDTO> skuInfoDTOS = goodsService.getSkuInfoByTags(tags);
        List<String> skuIds = skuInfoDTOS.stream().map(skuInfoDTO -> skuInfoDTO.getSkuName()).collect(Collectors.toList());

        request.setSkuIds(skuIds);

        List<CookbookSkuRelationDO> cookbookSkuRelationDOS = new ArrayList<>();
        for (String skuId : skuIds) {
            CookbookSkuRelationDO cookbookSkuRelationDO =
                    buildCookbookSkuRelationDO(cookbookDO.getId(), skuId, request.getOperator());
            cookbookSkuRelationDOS.add(cookbookSkuRelationDO);
        }
        return cookbookSkuRelationDOS;
    }

    /**
     * 构建菜谱商品关联对象
     * @param cookbookId
     * @param skuId
     * @param operator
     * @return
     */
    private CookbookSkuRelationDO buildCookbookSkuRelationDO(Long cookbookId,
                                                             String skuId,
                                                             Integer operator) {

        CookbookSkuRelationDO cookbookSkuRelationDO = CookbookSkuRelationDO.builder()
                .cookbookId(cookbookId)
                .skuId(skuId)
                .delFlag(DeleteStatusEnum.NO.getCode())
                .createUser(operator)
                .updateUser(operator)
                .build();
        return cookbookSkuRelationDO;
    }

    @Override
    public CookbookDTO getCookbookInfo(CookbookQueryRequest request) {
        Long cookbookId = request.getCookbookId();

        CookbookDTO cookbook = getCookbookFromCache(cookbookId);
        if(cookbook != null) {
            return cookbook;
        }

        return getCookbookFromDB(cookbookId);
    }

    private CookbookDTO getCookbookFromCache(Long cookbookId) {
        String cookbookJsonString = redisCache.get(RedisKeyConstants.COOKBOOK_PREFIX + cookbookId);

        if (StringUtils.hasLength(cookbookJsonString)){
            log.info("从缓存中获取菜谱数据,cookbookId:{},value:{}", cookbookId, cookbookJsonString);
            // 防止缓存穿透
            if (Objects.equals(CacheSupport.EMPTY_CACHE, cookbookJsonString)) {
                return null;
            }
            redisCache.expire(RedisKeyConstants.COOKBOOK_PREFIX + cookbookId,
                    CacheSupport.generateCacheExpireSecond());
            CookbookDTO dto = JsonUtil.json2Object(cookbookJsonString, CookbookDTO.class);
            return dto;
        }

        return null;
    }

    /**
     * 从数据库中获取菜谱信息
     *
     * @param cookbookId
     * @return
     */
    private CookbookDTO getCookbookFromDB(Long cookbookId) {
        // 我们主要针对的是菜谱数据的更新操作
        // 对某个菜谱进行更新操作，同时在读取这个菜谱的详情，缓存过期，锁粒度，其实cookbookId
        String cookbookLockKey = RedisKeyConstants.COOKBOOK_UPDATE_LOCK_PREFIX + cookbookId;
        boolean lock = false;

        try {
            lock = redisLock.tryLock(cookbookLockKey, COOKBOOK_UPDATE_LOCK_TIMEOUT);
        } catch(InterruptedException e) {
            CookbookDTO cookbook = getCookbookFromCache(cookbookId);
            if(cookbook != null) {
                return cookbook;
            }

            log.error(e.getMessage(), e);
        }

        if (!lock) {
            CookbookDTO cookbook = getCookbookFromCache(cookbookId);
            if(cookbook != null) {
                return cookbook;
            }

            log.info("缓存数据为空，从数据库查询菜谱信息时获取锁失败，cookbookId:{}", cookbookId);
            throw new BaseBizException("查询失败");
        }

        try {
            CookbookDTO cookbook = getCookbookFromCache(cookbookId);
            if(cookbook != null) {
                return cookbook;
            }

            log.info("缓存数据为空，从数据库中获取数据，cookbookId:{}", cookbookId);
            String cookbookKey = RedisKeyConstants.COOKBOOK_PREFIX + cookbookId;

            CookbookDTO dto = cookbookDAO.getCookbookInfoById(cookbookId);
            if (Objects.isNull(dto)) {
                redisCache.set(cookbookKey, CacheSupport.EMPTY_CACHE, CacheSupport.generateCachePenetrationExpireSecond());
                return new CookbookDTO();
            }

            redisCache.set(cookbookKey, JsonUtil.object2Json(dto), CacheSupport.generateCacheExpireSecond());

            return dto;
        } finally {
            redisLock.unlock(cookbookLockKey);
        }
    }

    @Override
    public PagingInfo<CookbookDTO> listCookbookInfo(CookbookQueryRequest request) {
        // 从redis获取
//        String userCookbookKey = RedisKeyConstants.USER_COOKBOOK_PREFIX + request.getUserId();

        // redis的list类型的数据结构，lrange针对list类型的数据结构，范围查询，指定key，起始位置，每页数据量
        // 把一页数据给查出来
//        List<String> cookbookDTOJsonString =
//                redisCache.lRange(userCookbookKey,
//                        (request.getPageNo() - 1) * request.getPageSize(), request.getPageSize());

//        log.info("从缓存中获取菜谱信息列表,request:{},value:{}", request, JsonUtil.object2Json(cookbookDTOS));

        // 前端界面里，随意进行跳转，查第几页都可以，不用说一定按页顺序来查询
        PagingInfo<CookbookDTO> page = listCookbookInfoFromCache(request);
        if(page != null) {
            return page;
        }

        return listCookbookInfoFromDB(request);
    }

    private PagingInfo<CookbookDTO> listCookbookInfoFromCache(CookbookQueryRequest request) {
        String userCookbookPageKey = RedisKeyConstants.USER_COOKBOOK_PAGE_PREFIX
                + request.getUserId() + ":" + request.getPageNo();
        String cookbooksJSON = redisCache.get(userCookbookPageKey);

        if (cookbooksJSON != null && !"".equals(cookbooksJSON)) {
//            Long size = redisCache.lsize(userCookbookKey);
            String userCookbookCountKey = RedisKeyConstants.USER_COOKBOOK_COUNT_PREFIX + request.getUserId();
            Long size = Long.valueOf(redisCache.get(userCookbookCountKey));
            List<CookbookDTO> cookbookDTOS = JSON.parseObject(cookbooksJSON, List.class);

            redisCache.expire(userCookbookPageKey, CacheSupport.generateCacheExpireSecond());

            return PagingInfo.toResponse(cookbookDTOS, size, request.getPageNo(), request.getPageSize());
        }

        return null;
    }

    private PagingInfo<CookbookDTO> listCookbookInfoFromDB(CookbookQueryRequest request) {
        String userCookbookLockKey = RedisKeyConstants.USER_COOKBOOK_PREFIX + request.getUserId();
        boolean lock = false;

        try {
            lock = redisLock.tryLock(userCookbookLockKey, USER_COOKBOOK_LOCK_TIMEOUT);
        } catch(InterruptedException e) {
            PagingInfo<CookbookDTO> page = listCookbookInfoFromCache(request);
            if(page != null) {
                return page;
            }

            log.error(e.getMessage(), e);
        }

        if (!lock) {
            PagingInfo<CookbookDTO> page = listCookbookInfoFromCache(request);
            if(page != null) {
                return page;
            }

            log.info("缓存数据为空，从数据库查询用户菜谱信息时获取锁失败，userId:{}", request.getUserId());
            throw new BaseBizException("查询失败");
        }

        try {
            PagingInfo<CookbookDTO> page = listCookbookInfoFromCache(request);
            if(page != null) {
                return page;
            }

            log.info("缓存数据为空，从数据库中获取数据，request:{}", request);

            LambdaQueryWrapper<CookbookDO> queryWrapper = Wrappers.lambdaQuery();
            queryWrapper.eq(CookbookDO::getUserId, request.getUserId());
            int count = cookbookDAO.count(queryWrapper);

            // 这里是从db里查到的一页数据
            List<CookbookDTO> cookbookDTOS =
                    cookbookDAO.pageByUserId(request.getUserId(), request.getPageNo(), request.getPageSize());

            // 基于redis的list数据结构，rpush，lrange
            // 把你的用户发布过这一页数据，给怼到list数据结构里去
            // 此时在list缓存里，仅仅只有第一页的数据而已，惰性分页list缓存构建
//            redisCache.rPushAll(userCookbookKey, JsonUtil.listObject2ListJson(cookbookDTOS));

            // 第一页的page缓存是没有包含刚才写入最新数据，旧数据
            // 数据库和缓存不一致了
            // 2天多之内，有人访问第一个page，缓存，读到的都是旧数据，没包含你最新发布的新数据
            String userCookbookPageKey = RedisKeyConstants.USER_COOKBOOK_PAGE_PREFIX
                    + request.getUserId() + ":" + request.getPageNo();
            redisCache.set(userCookbookPageKey,
                    JsonUtil.object2Json(cookbookDTOS),
                    CacheSupport.generateCacheExpireSecond());

            PagingInfo<CookbookDTO> pagingInfo =
                    PagingInfo.toResponse(cookbookDTOS, (long) count, request.getPageNo(), request.getPageSize());

            return pagingInfo;
        } finally {
            redisLock.unlock(userCookbookLockKey);
        }
    }
}
