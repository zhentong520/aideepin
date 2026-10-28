package com.moyz.adi.admin.controller;

import com.moyz.adi.common.annotation.AdminOnly;
import com.moyz.adi.common.entity.MallActivity;
import com.moyz.adi.common.entity.MallCategory;
import com.moyz.adi.common.entity.MallCoupon;
import com.moyz.adi.common.entity.MallProduct;
import com.moyz.adi.common.service.mall.MallService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 商城数据管理（管理端）。
 * <p>
 * CRUD for the four catalog tables the mall biz plugin reads from:
 * categories, products, coupons, activities. All list endpoints return enabled
 * and disabled rows so the admin page can toggle visibility without SQL.
 * User-claimed coupons (mall_user_coupon) are intentionally NOT managed here —
 * they belong to end users and are created through the chat tool flow.
 * <p>
 * Data access stays in {@link MallService}: controllers must not touch mappers
 * directly (architecture gate S1-01, guarded by WholeAppArchitectureTest).
 */
@Tag(name = "商城数据管理 | Mall Data Management", description = "商城分类/商品/优惠券/活动 CRUD（管理端） | CRUD for mall categories/products/coupons/activities (admin)")
@RestController
@RequestMapping("/admin/mall")
@AdminOnly
public class AdminMallController {

    @Resource
    private MallService mallService;

    // ---------------- 分类 ----------------

    @Operation(summary = "分类列表（含停用） | List categories, including disabled")
    @GetMapping("/category/list")
    public List<MallCategory> listCategories() {
        return mallService.listAllCategories();
    }

    @Operation(summary = "保存分类 | Save category")
    @PostMapping("/category/save")
    public MallCategory saveCategory(@RequestBody MallCategory category) {
        return mallService.saveCategory(category);
    }

    @Operation(summary = "删除分类（软删） | Soft delete category")
    @PostMapping("/category/delete")
    public Boolean deleteCategory(@RequestParam Long id) {
        return mallService.softDeleteCategory(id);
    }

    // ---------------- 商品 ----------------

    @Operation(summary = "商品列表（含停用，可按分类过滤） | List products, optionally filtered by category")
    @GetMapping("/product/list")
    public List<MallProduct> listProducts(@RequestParam(required = false) String categoryCode) {
        return mallService.listAllProducts(categoryCode);
    }

    @Operation(summary = "保存商品 | Save product")
    @PostMapping("/product/save")
    public MallProduct saveProduct(@RequestBody MallProduct product) {
        if (product.getIsDeleted() == null) {
            product.setIsDeleted(false);
        }
        if (product.getIsEnable() == null) {
            product.setIsEnable(true);
        }
        if (product.getId() == null) {
            mallService.save(product);
        } else {
            mallService.updateById(product);
        }
        return product;
    }

    @Operation(summary = "删除商品（软删） | Soft delete product")
    @PostMapping("/product/delete")
    public Boolean deleteProduct(@RequestParam Long id) {
        return mallService.softDeleteProduct(id);
    }

    // ---------------- 优惠券 ----------------

    @Operation(summary = "优惠券列表（含停用） | List coupons, including disabled")
    @GetMapping("/coupon/list")
    public List<MallCoupon> listCoupons() {
        return mallService.listAllCoupons();
    }

    @Operation(summary = "保存优惠券 | Save coupon")
    @PostMapping("/coupon/save")
    public MallCoupon saveCoupon(@RequestBody MallCoupon coupon) {
        return mallService.saveCoupon(coupon);
    }

    @Operation(summary = "删除优惠券（软删） | Soft delete coupon")
    @PostMapping("/coupon/delete")
    public Boolean deleteCoupon(@RequestParam Long id) {
        return mallService.softDeleteCoupon(id);
    }

    // ---------------- 活动 ----------------

    @Operation(summary = "活动列表（含停用） | List activities, including disabled")
    @GetMapping("/activity/list")
    public List<MallActivity> listActivities() {
        return mallService.listAllActivities();
    }

    @Operation(summary = "保存活动 | Save activity")
    @PostMapping("/activity/save")
    public MallActivity saveActivity(@RequestBody MallActivity activity) {
        return mallService.saveActivity(activity);
    }

    @Operation(summary = "删除活动（软删） | Soft delete activity")
    @PostMapping("/activity/delete")
    public Boolean deleteActivity(@RequestParam Long id) {
        return mallService.softDeleteActivity(id);
    }
}
