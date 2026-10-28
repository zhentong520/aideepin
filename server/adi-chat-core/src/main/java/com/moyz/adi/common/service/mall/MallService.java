package com.moyz.adi.common.service.mall;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.moyz.adi.common.entity.MallActivity;
import com.moyz.adi.common.entity.MallCategory;
import com.moyz.adi.common.entity.MallCoupon;
import com.moyz.adi.common.entity.MallProduct;
import com.moyz.adi.common.mapper.MallActivityMapper;
import com.moyz.adi.common.mapper.MallCategoryMapper;
import com.moyz.adi.common.mapper.MallCouponMapper;
import com.moyz.adi.common.mapper.MallProductMapper;
import com.moyz.adi.common.mapper.MallUserCouponMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 商城数据查询服务（后台配置的数据，用户端经业务工具插件消费）。
 * <p>
 * Mall data query service. Data is managed backstage (AdminMallController) and consumed
 * by the mall biz-tool plugin during conversations.
 * </p>
 */
@Slf4j
@Service
public class MallService extends ServiceImpl<MallProductMapper, MallProduct> {

    @Resource
    private MallCategoryMapper mallCategoryMapper;

    @Resource
    private MallCouponMapper mallCouponMapper;

    @Resource
    private MallActivityMapper mallActivityMapper;

    @Resource
    private MallUserCouponMapper mallUserCouponMapper;

    public List<MallCategory> listEnabledCategories() {
        return mallCategoryMapper.selectList(new LambdaQueryWrapper<MallCategory>()
                .eq(MallCategory::getIsEnable, true)
                .eq(MallCategory::getIsDeleted, false)
                .orderByAsc(MallCategory::getSort));
    }

    /**
     * 某分类下的热销商品（按月销降序）。
     */
    public List<MallProduct> topProducts(String categoryCode, int limit) {
        return baseMapper.selectList(new LambdaQueryWrapper<MallProduct>()
                .eq(MallProduct::getCategoryCode, categoryCode)
                .eq(MallProduct::getIsEnable, true)
                .eq(MallProduct::getIsDeleted, false)
                .orderByDesc(MallProduct::getMonthlySales)
                .last("limit " + Math.max(1, Math.min(limit, 20))));
    }

    public MallProduct getProduct(Long id) {
        return baseMapper.selectOne(new LambdaQueryWrapper<MallProduct>()
                .eq(MallProduct::getId, id)
                .eq(MallProduct::getIsEnable, true)
                .eq(MallProduct::getIsDeleted, false));
    }

    /**
     * 管理端：商品列表（含停用，可按分类过滤）。
     */
    public List<MallProduct> listAllProducts(String categoryCode) {
        LambdaQueryWrapper<MallProduct> wrapper = new LambdaQueryWrapper<MallProduct>()
                .eq(MallProduct::getIsDeleted, false);
        if (null != categoryCode && !categoryCode.isBlank()) {
            wrapper.eq(MallProduct::getCategoryCode, categoryCode);
        }
        wrapper.orderByDesc(MallProduct::getMonthlySales);
        return baseMapper.selectList(wrapper);
    }

    /**
     * 管理端：软删商品。
     */
    public boolean softDeleteProduct(Long id) {
        MallProduct exist = baseMapper.selectById(id);
        if (null == exist) {
            return false;
        }
        exist.setIsDeleted(true);
        return baseMapper.updateById(exist) > 0;
    }

    // ================= 管理端 CRUD（数据访问收敛在 service 层，架构门禁 S1-01） =================

    /**
     * 管理端：分类列表（含停用）。
     */
    public List<MallCategory> listAllCategories() {
        return mallCategoryMapper.selectList(new LambdaQueryWrapper<MallCategory>()
                .eq(MallCategory::getIsDeleted, false)
                .orderByAsc(MallCategory::getSort));
    }

    /**
     * 管理端：保存分类（id 为空即新增）。
     */
    public MallCategory saveCategory(MallCategory category) {
        if (category.getIsDeleted() == null) {
            category.setIsDeleted(false);
        }
        if (category.getIsEnable() == null) {
            category.setIsEnable(true);
        }
        if (category.getId() == null) {
            mallCategoryMapper.insert(category);
        } else {
            mallCategoryMapper.updateById(category);
        }
        return category;
    }

    /**
     * 管理端：软删分类。
     */
    public boolean softDeleteCategory(Long id) {
        MallCategory exist = mallCategoryMapper.selectById(id);
        if (null == exist) {
            return false;
        }
        exist.setIsDeleted(true);
        return mallCategoryMapper.updateById(exist) > 0;
    }

    /**
     * 管理端：优惠券列表（含停用）。
     */
    public List<MallCoupon> listAllCoupons() {
        return mallCouponMapper.selectList(new LambdaQueryWrapper<MallCoupon>()
                .eq(MallCoupon::getIsDeleted, false)
                .orderByDesc(MallCoupon::getCreateTime));
    }

    /**
     * 管理端：保存优惠券（id 为空即新增）。
     */
    public MallCoupon saveCoupon(MallCoupon coupon) {
        if (coupon.getIsDeleted() == null) {
            coupon.setIsDeleted(false);
        }
        if (coupon.getIsEnable() == null) {
            coupon.setIsEnable(true);
        }
        if (coupon.getTotalCount() == null) {
            coupon.setTotalCount(-1);
        }
        if (coupon.getClaimedCount() == null) {
            coupon.setClaimedCount(0);
        }
        if (coupon.getId() == null) {
            mallCouponMapper.insert(coupon);
        } else {
            mallCouponMapper.updateById(coupon);
        }
        return coupon;
    }

    /**
     * 管理端：软删优惠券。
     */
    public boolean softDeleteCoupon(Long id) {
        MallCoupon exist = mallCouponMapper.selectById(id);
        if (null == exist) {
            return false;
        }
        exist.setIsDeleted(true);
        return mallCouponMapper.updateById(exist) > 0;
    }

    /**
     * 管理端：活动列表（含停用）。
     */
    public List<MallActivity> listAllActivities() {
        return mallActivityMapper.selectList(new LambdaQueryWrapper<MallActivity>()
                .eq(MallActivity::getIsDeleted, false)
                .orderByAsc(MallActivity::getSort));
    }

    /**
     * 管理端：保存活动（id 为空即新增）。
     */
    public MallActivity saveActivity(MallActivity activity) {
        if (activity.getIsDeleted() == null) {
            activity.setIsDeleted(false);
        }
        if (activity.getIsEnable() == null) {
            activity.setIsEnable(true);
        }
        if (activity.getId() == null) {
            mallActivityMapper.insert(activity);
        } else {
            mallActivityMapper.updateById(activity);
        }
        return activity;
    }

    /**
     * 管理端：软删活动。
     */
    public boolean softDeleteActivity(Long id) {
        MallActivity exist = mallActivityMapper.selectById(id);
        if (null == exist) {
            return false;
        }
        exist.setIsDeleted(true);
        return mallActivityMapper.updateById(exist) > 0;
    }

    public List<MallActivity> listEnabledActivities() {
        return mallActivityMapper.selectList(new LambdaQueryWrapper<MallActivity>()
                .eq(MallActivity::getIsEnable, true)
                .eq(MallActivity::getIsDeleted, false)
                .orderByAsc(MallActivity::getSort));
    }

    public List<MallCoupon> listEnabledCoupons() {
        return mallCouponMapper.selectList(new LambdaQueryWrapper<MallCoupon>()
                .eq(MallCoupon::getIsEnable, true)
                .eq(MallCoupon::getIsDeleted, false)
                .orderByAsc(MallCoupon::getId));
    }

    public MallCoupon getCoupon(String code) {
        return mallCouponMapper.selectOne(new LambdaQueryWrapper<MallCoupon>()
                .eq(MallCoupon::getCode, code)
                .eq(MallCoupon::getIsEnable, true)
                .eq(MallCoupon::getIsDeleted, false));
    }

    public MallUserCouponMapper userCouponMapper() {
        return mallUserCouponMapper;
    }

    public MallCouponMapper couponMapper() {
        return mallCouponMapper;
    }
}
