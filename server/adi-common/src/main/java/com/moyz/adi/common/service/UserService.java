package com.moyz.adi.common.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.extension.toolkit.ChainWrappers;
import com.moyz.adi.common.base.ThreadContext;
import com.moyz.adi.common.config.AdiProperties;
import com.moyz.adi.common.constant.AdiConstant;
import com.moyz.adi.common.constant.RedisKeyConstant;
import com.moyz.adi.common.dto.*;
import com.moyz.adi.common.entity.User;
import com.moyz.adi.common.enums.ErrorEnum;
import com.moyz.adi.common.enums.UserStatusEnum;
import com.moyz.adi.common.exception.BaseException;
import com.moyz.adi.common.helper.AdiMailSender;
import com.moyz.adi.common.mapper.UserMapper;
import com.moyz.adi.common.util.*;
import com.moyz.adi.common.vo.CostStat;
import com.moyz.adi.common.vo.TokenCostStatistic;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import jakarta.servlet.http.HttpServletRequest;

import java.text.MessageFormat;
import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static com.moyz.adi.common.constant.RedisKeyConstant.*;
import static com.moyz.adi.common.enums.ErrorEnum.*;

/**
 * <p>
 * User service implementation class
 * </p>
 *
 * @author moyz
 * @since 2023-04-11
 */
@Slf4j
@Service
public class UserService extends ServiceImpl<UserMapper, User> {

    @Resource
    private UserDayCostService userDayCostService;

    @Resource
    private AdiMailSender adiMailSender;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CharacterService characterService;

    @Resource
    private AdiProperties adiProperties;

    @Value("${spring.application.name}")
    private String appName;

    /**
     * 通过邮箱获取用户|Get user by email
     *
     * @param email 邮箱|email
     * @return 用户|user
     */
    public User getByEmail(String email) {
        if (StringUtils.isBlank(email)) {
            throw new BaseException(ErrorEnum.A_PARAMS_ERROR);
        }
        return this.lambdaQuery().eq(User::getEmail, email).eq(User::getIsDeleted, false).oneOpt().orElseThrow(() -> new BaseException(A_USER_NOT_EXIST));
    }

    /**
     * 通过邮箱找回密码|Forgot password by email
     * <p>
     * S-07/S-08（2026-10-16）重构：
     * <ul>
     *   <li>不再在邮件里告知固定新密码（原 DEFAULT_PASSWORD="123456" 已废弃），
     *       邮件只含重置链接，用户点开后自行设置新密码；</li>
     *   <li>同一邮箱加发送冷却（{@link AdiConstant#FORGOT_PASSWORD_COOLDOWN_SECONDS}），
     *       防邮件轰炸；</li>
     *   <li>防用户枚举：邮箱不存在时与存在时对外行为一致（不发邮件、不报错、返回同一文案），
     *       攻击者无法通过响应差异收集有效邮箱清单。</li>
     * </ul>
     *
     * @param email 邮箱|email
     */
    public void forgotPassword(String email) {
        String mail = StringUtils.trimToEmpty(email);
        if (StringUtils.isBlank(mail)) {
            throw new BaseException(ErrorEnum.A_PARAMS_ERROR);
        }
        String cooldownKey = MessageFormat.format(FIND_MY_PASSWORD_COOLDOWN, mail);
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(cooldownKey))) {
            // 冷却期内直接静默返回：不暴露「刚才发过」这一信号，响应与正常发送一致
            return;
        }
        User user = this.lambdaQuery().eq(User::getIsDeleted, false).eq(User::getEmail, mail).oneOpt().orElse(null);
        if (null == user) {
            // 防枚举：不存在时静默返回，调用方返回与成功一致的文案
            return;
        }
        String code = UuidUtil.createShort();
        String key = MessageFormat.format(FIND_MY_PASSWORD, code);
        stringRedisTemplate.opsForValue().set(key, user.getId().toString(), AdiConstant.AUTH_ACTIVE_CODE_EXPIRE, TimeUnit.HOURS);
        String resetLink = adiProperties.getBackendUrl() + "/auth/password/reset?code=" + code;
        String subject = SpringUtil.getMessage("mail.resetPasswordSubject");
        String body = MessageFormat.format(SpringUtil.getMessage("mail.resetPasswordBody"), AdiConstant.AUTH_ACTIVE_CODE_EXPIRE, resetLink);
        if (!adiMailSender.send(subject, body, mail)) {
            //发送失败要显式报错，不能假成功让用户干等一封永远不会来的邮件
            stringRedisTemplate.delete(key);
            throw new BaseException(ErrorEnum.A_MAIL_SEND_FAIL);
        }
        stringRedisTemplate.opsForValue().set(cooldownKey, "1", AdiConstant.FORGOT_PASSWORD_COOLDOWN_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * 注册|Register
     *
     * @param email     邮箱|email
     * @param password  密码|password
     * @param captchaId 验证码ID|captcha ID
     * @param captcha   验证码|captcha
     */
    public void register(String email, String password, String captchaId, String captcha) {
        //验证码
        String captchaIdKey = MessageFormat.format(AUTH_REGISTER_CAPTCHA_ID, captchaId);
        String captchaInCache = stringRedisTemplate.opsForValue().get(captchaIdKey);
        if (StringUtils.isBlank(captchaInCache) || !captchaInCache.equalsIgnoreCase(captcha)) {
            throw new BaseException(A_LOGIN_CAPTCHA_ERROR);
        }
        stringRedisTemplate.delete(captchaIdKey);

        User user = ChainWrappers.lambdaQueryChain(baseMapper).eq(User::getIsDeleted, false).eq(User::getEmail, email).one();
        if (null != user && user.getUserStatus() == UserStatusEnum.NORMAL) {
            throw new BaseException(A_USER_EXIST);
        }
        if (null != user) {
            if (!sendActiveEmail(email)) {
                throw new BaseException(ErrorEnum.A_MAIL_SEND_FAIL);
            }
            return;
        }

//Send activation link
        //发送激活链接（失败显式报错，不创建半截账号）
        if (!sendActiveEmail(email)) {
            throw new BaseException(ErrorEnum.A_MAIL_SEND_FAIL);
        }

        String hashed = BCrypt.hashpw(password, BCrypt.gensalt());

//Create user
        //创建用户
        User newOne = new User();
        newOne.setName(StringUtils.substringBefore(email, "@"));
        newOne.setUuid(UuidUtil.createShort());
        newOne.setEmail(email);
        newOne.setPassword(hashed);
        newOne.setUserStatus(UserStatusEnum.WAIT_CONFIRM);
        baseMapper.insert(newOne);

        //Create default character
        characterService.createDefault(newOne.getId());
    }

    /**
     * 注册邮箱验证码是否已开启|Whether registration email code is enabled
     */
    private boolean registerEmailCodeEnabled() {
        AdiProperties.Register register = adiProperties.getRegister();
        return null != register && null != register.getEmailCode() && register.getEmailCode().isEnable();
    }

    /**
     * 生成数字验证码|Generate a numeric verification code
     */
    private String randomNumericCode(int length) {
        int len = Math.max(length, 4);
        StringBuilder sb = new StringBuilder(len);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < len; i++)
            sb.append(random.nextInt(10));
        return sb.toString();
    }

    /**
     * 发送注册邮箱验证码|Send registration email verification code
     * <p>
     * 开关关闭时直接拒绝，避免前端绕过开关调用。同一个邮箱在重发间隔内只能发一次。
     * 邮件投递失败会回收验证码，不会留下"用户收到成功提示但永远等不到码"的假成功。
     *
     * @param email 邮箱|email
     */
    public void sendRegisterEmailCode(String email) {
        if (!registerEmailCodeEnabled()) {
            throw new BaseException(A_REGISTER_EMAIL_CODE_DISABLED);
        }
        String mail = StringUtils.trimToEmpty(email);
        if (StringUtils.isBlank(mail)) {
            throw new BaseException(A_PARAMS_ERROR);
        }

        User existUser = ChainWrappers.lambdaQueryChain(baseMapper).eq(User::getIsDeleted, false).eq(User::getEmail, mail).one();
        if (null != existUser && existUser.getUserStatus() == UserStatusEnum.NORMAL) {
            throw new BaseException(A_USER_EXIST);
        }

        AdiProperties.EmailCode config = adiProperties.getRegister().getEmailCode();
        String cooldownKey = MessageFormat.format(AUTH_REGISTER_EMAIL_CODE_COOLDOWN, mail);
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(cooldownKey))) {
            throw new BaseException(A_REGISTER_EMAIL_CODE_TOO_FREQUENTLY);
        }

        String code = randomNumericCode(config.getCodeLength());
        String codeKey = MessageFormat.format(AUTH_REGISTER_EMAIL_CODE, mail);
        stringRedisTemplate.opsForValue().set(codeKey, code, config.getExpireMinutes(), TimeUnit.MINUTES);

        String subject = SpringUtil.getMessage("mail.registerCodeSubject");
        String body = MessageFormat.format(SpringUtil.getMessage("mail.registerCodeBody"), code, config.getExpireMinutes());
        if (!adiMailSender.send(subject, body, mail)) {
            //发送失败要显式报错并回收验证码，不能假成功让用户干等一封永远不会来的邮件
            stringRedisTemplate.delete(codeKey);
            throw new BaseException(A_MAIL_SEND_FAIL);
        }
        stringRedisTemplate.opsForValue().set(cooldownKey, "1", config.getResendIntervalSeconds(), TimeUnit.SECONDS);
    }

    /**
     * 通过邮箱验证码注册|Register by email verification code
     * <p>
     * 与老流程（{@link #register(String, String, String, String)}）的区别：邮箱验证码已经证明
     * 邮箱归属，因此不再校验图形验证码、也不再发激活链接，账号创建即为
     * {@link UserStatusEnum#NORMAL}，并直接下发登录 token。
     * 若该邮箱之前注册过但未激活，则补全密码并直接激活，而不是重复建账号。
     *
     * @param email     邮箱|email
     * @param password  密码|password
     * @param emailCode 邮箱验证码|email verification code
     * @return 登录信息（含token）|login info including token
     */
    public LoginResp registerByEmailCode(String email, String password, String emailCode) {
        if (!registerEmailCodeEnabled()) {
            throw new BaseException(A_REGISTER_EMAIL_CODE_DISABLED);
        }
        String mail = StringUtils.trimToEmpty(email);
        String code = StringUtils.trimToEmpty(emailCode);
        if (StringUtils.isBlank(code)) {
            throw new BaseException(A_PARAMS_ERROR);
        }

        String codeKey = MessageFormat.format(AUTH_REGISTER_EMAIL_CODE, mail);
        String codeInCache = stringRedisTemplate.opsForValue().get(codeKey);
        if (StringUtils.isBlank(codeInCache) || !codeInCache.equals(code)) {
            throw new BaseException(A_REGISTER_EMAIL_CODE_ERROR);
        }
        //验证码一次性，校验通过立即作废
        stringRedisTemplate.delete(codeKey);

        User existUser = ChainWrappers.lambdaQueryChain(baseMapper).eq(User::getIsDeleted, false).eq(User::getEmail, mail).one();
        if (null != existUser && existUser.getUserStatus() == UserStatusEnum.NORMAL) {
            throw new BaseException(A_USER_EXIST);
        }

        String hashed = BCrypt.hashpw(password, BCrypt.gensalt());
        User user;
        if (null != existUser) {
            //注册过但没激活：邮箱验证码已验证归属，直接激活并重置密码
            User updateUser = new User();
            updateUser.setId(existUser.getId());
            updateUser.setPassword(hashed);
            updateUser.setUserStatus(UserStatusEnum.NORMAL);
            updateUser.setActiveTime(LocalDateTime.now());
            baseMapper.updateById(updateUser);

            existUser.setPassword(hashed);
            existUser.setUserStatus(UserStatusEnum.NORMAL);
            existUser.setActiveTime(updateUser.getActiveTime());
            user = existUser;
        } else {
            user = new User();
            user.setName(StringUtils.substringBefore(mail, "@"));
            user.setUuid(UuidUtil.createShort());
            user.setEmail(mail);
            user.setPassword(hashed);
            user.setUserStatus(UserStatusEnum.NORMAL);
            user.setActiveTime(LocalDateTime.now());
            baseMapper.insert(user);

            //Create default character
            characterService.createDefault(user.getId());
        }

        // 必须回读：手工 new 出来的 User 没有配额等数据库默认值，直接交给 setLoginToken 会因
        // Integer 拆箱 NPE（老流程是先插库、点激活链接时从库里读回来，所以天然拿得到默认值）。
        // Must re-read: a hand-built User lacks the quota defaults the DB fills in, and
        // setLoginToken unboxes those Integers -> NPE. The legacy flow never hit this because
        // activation re-loads the row from the database.
        User saved = this.lambdaQuery().eq(User::getEmail, mail).eq(User::getIsDeleted, false).oneOpt()
                .orElseThrow(() -> new BaseException(A_USER_NOT_EXIST));

        String token = setLoginToken(saved);
        LoginResp loginResp = new LoginResp();
        loginResp.setToken(token);
        BeanUtils.copyProperties(saved, loginResp);
        return loginResp;
    }

    /**
     * 校验找回密码 code 是否有效（S-07：供 GET /auth/password/reset 跳转前端前预检）。
     * 只校验不消费，真正的消费发生在 {@link #resetPasswordByCode(String, String)}。
     *
     * @param code 重置密码code|reset password code
     * @return code 对应的用户id；无效时抛 {@link ErrorEnum#A_FIND_PASSWORD_CODE_ERROR}
     */
    public Long verifyResetCode(String code) {
        String key = MessageFormat.format(FIND_MY_PASSWORD, code);
        String userId = stringRedisTemplate.opsForValue().get(key);
        if (StringUtils.isBlank(userId)) {
            throw new BaseException(ErrorEnum.A_FIND_PASSWORD_CODE_ERROR);
        }
        return Long.parseLong(userId);
    }

    /**
     * 通过找回密码 code 设置新密码（S-07：替代原「重置为固定口令 123456」的实现）。
     * code 一次性消费；成功后踢掉该用户全部已登录会话（S-08③）。
     *
     * @param code        重置密码code|reset password code
     * @param newPassword 用户自设的新密码|new password chosen by the user
     */
    public void resetPasswordByCode(String code, String newPassword) {
        assertPasswordStrength(newPassword);
        String key = MessageFormat.format(FIND_MY_PASSWORD, code);
        String userId = stringRedisTemplate.opsForValue().get(key);
        if (StringUtils.isBlank(userId)) {
            throw new BaseException(ErrorEnum.A_FIND_PASSWORD_CODE_ERROR);
        }
        User updateUser = new User();
        updateUser.setId(Long.parseLong(userId));
        updateUser.setPassword(BCrypt.hashpw(newPassword, BCrypt.gensalt()));
        baseMapper.updateById(updateUser);
        stringRedisTemplate.delete(key);
        // 重置成功即全端踢出：重置场景无法携带当前 token，按 userId 清全部
        removeUserTokens(Long.parseLong(userId), null);
    }

    /**
     * 修改密码|Modify password
     *
     * @param oldPassword 旧密码|old password
     * @param newPassword 新密码|new password
     */
    public void modifyPassword(String oldPassword, String newPassword) {
        User user = ThreadContext.getExistCurrentUser();

        if (!BCrypt.checkpw(oldPassword, user.getPassword())) {
            throw new BaseException(A_OLD_PASSWORD_INVALID);
        }
        assertPasswordStrength(newPassword);

        String hashed = BCrypt.hashpw(newPassword, BCrypt.gensalt());
        User updateUser = new User();
        updateUser.setId(user.getId());
        updateUser.setPassword(hashed);
        baseMapper.updateById(updateUser);
        // S-08③：改密后踢掉其他设备的会话；当前设备的 token 保留，避免用户被登出
        removeUserTokens(user.getId(), ThreadContext.getToken());
    }

    /**
     * 用户自设密码的长度约束（S-07）。
     * 原找回密码流程把口令固定为 6 位默认值，无任何强度要求；现改为用户自设，
     * 至少保证长度下限，防止重置后立刻处于弱口令状态。复杂度规则后续可再增强。
     */
    private void assertPasswordStrength(String newPassword) {
        if (StringUtils.isBlank(newPassword)
                || newPassword.length() < AdiConstant.PASSWORD_MIN_LENGTH
                || newPassword.length() > AdiConstant.PASSWORD_MAX_LENGTH) {
            throw new BaseException(ErrorEnum.A_PARAMS_ERROR);
        }
    }

    /**
     * 踢掉某用户的已登录会话（S-08③）。
     * 依赖 {@link #setLoginToken(User)} 写入的 {@link RedisKeyConstant#USER_TOKEN_SET} 集合。
     *
     * @param userId      用户id
     * @param exceptToken 需要保留的 token（改密场景保留当前设备）；null 表示全部清除
     */
    private void removeUserTokens(Long userId, String exceptToken) {
        String setKey = MessageFormat.format(USER_TOKEN_SET, userId);
        var tokens = stringRedisTemplate.opsForSet().members(setKey);
        if (null != tokens) {
            for (String token : tokens) {
                if (StringUtils.isNotBlank(exceptToken) && exceptToken.equals(token)) {
                    continue;
                }
                stringRedisTemplate.delete(MessageFormat.format(USER_TOKEN, token));
            }
        }
        stringRedisTemplate.delete(setKey);
    }

    /**
     * 激活|Activate
     *
     * @param activeCode 激活码|activation code
     */
    public void active(String activeCode) {
        String activeCodeKey = MessageFormat.format(AUTH_ACTIVE_CODE, activeCode);
        String email = stringRedisTemplate.opsForValue().get(activeCodeKey);
        if (StringUtils.isBlank(email)) {
            throw new BaseException(A_ACTIVE_CODE_INVALID);
        }

        User user = this.lambdaQuery().eq(User::getEmail, email).eq(User::getIsDeleted, false).oneOpt().orElse(null);
        if (null == user) {
            throw new BaseException(A_USER_NOT_EXIST);
        }

        stringRedisTemplate.delete(activeCodeKey);

        User updateUser = new User();
        updateUser.setId(user.getId());
        updateUser.setUserStatus(UserStatusEnum.NORMAL);
        updateUser.setActiveTime(LocalDateTime.now());
        baseMapper.updateById(updateUser);

        setLoginToken(user);
    }

    /**
     * 通过UUID激活|Activate by UUID
     *
     * @param uuid 用户UUID|user UUID
     */
    public void activeByUuid(String uuid) {
        User user = this.getByUuidOrThrow(uuid);
        User updateUser = new User();
        updateUser.setId(user.getId());
        updateUser.setUserStatus(UserStatusEnum.NORMAL);
        updateUser.setActiveTime(LocalDateTime.now());
        baseMapper.updateById(updateUser);
    }

    /**
     * 冻结用户|Freeze user
     *
     * @param uuid 用户UUID|user UUID
     */
    public void freeze(String uuid) {
        User user = this.getByUuidOrThrow(uuid);
        User updateUser = new User();
        updateUser.setId(user.getId());
        updateUser.setUserStatus(UserStatusEnum.FREEZE);
        baseMapper.updateById(updateUser);
    }

    /**
     * 编辑用户|Edit user
     *
     * @param userEditReq 用户编辑请求|user edit request
     */
    public void editUser(UserEditReq userEditReq) {
        User user = this.getByUuidOrThrow(userEditReq.getUuid());
        User editUser = new User();
        editUser.setId(user.getId());
        BeanUtils.copyProperties(userEditReq, editUser);
        if (StringUtils.isNotBlank(userEditReq.getPassword())) {
            String hashed = BCrypt.hashpw(userEditReq.getPassword(), BCrypt.gensalt());
            editUser.setPassword(hashed);
        } else {
            editUser.setPassword(null);
        }
        baseMapper.updateById(editUser);
    }

    /**
     * 登录|Login
     *
     * @param loginReq 登录请求|login request
     * @return 登录响应|login response
     */
    public LoginResp login(LoginReq loginReq) {
        //captcha check
        assertSafeCaptchaId(loginReq.getCaptchaId());
        String failCountKey = MessageFormat.format(RedisKeyConstant.LOGIN_FAIL_COUNT, loginReq.getEmail());
        int passwordFailCount = 0;
        String failCountVal = stringRedisTemplate.opsForValue().get(failCountKey);
        if (StringUtils.isNotBlank(failCountVal)) {
            passwordFailCount = Integer.parseInt(failCountVal);
        }
        String captchaIdKey = null;
        if (passwordFailCount >= AdiConstant.LOGIN_MAX_FAIL_TIMES) {
            if (StringUtils.isAnyBlank(loginReq.getCaptchaCode(), loginReq.getCaptchaId())) {
                String captchaId = setAndGetLoginCaptchaId();
                LoginResp loginResp = new LoginResp();
                loginResp.setCaptchaId(captchaId);
                throw new BaseException(ErrorEnum.A_LOGIN_ERROR_MAX).setData(loginResp);
            }
            // 校验 + 一次性消费（验证码复用漏洞的修复点，见方法注释）
            consumeLoginCaptcha(loginReq.getCaptchaId(), loginReq.getCaptchaCode());
        }
        //captcha check end

        User user = this.lambdaQuery().eq(User::getIsDeleted, false).eq(User::getEmail, loginReq.getEmail()).oneOpt().orElseThrow(() -> new BaseException(ErrorEnum.A_USER_NOT_EXIST));
        if (user.getUserStatus() == UserStatusEnum.WAIT_CONFIRM) {
            throw new BaseException(ErrorEnum.A_USER_WAIT_CONFIRM);
        }
        if (!BCrypt.checkpw(loginReq.getPassword(), user.getPassword())) {

            //计算错误次数并判断下次登录是否要输入验证码
            passwordFailCount = passwordFailCount + 1;
            stringRedisTemplate.opsForValue().set(failCountKey, String.valueOf(passwordFailCount), AdiConstant.USER_TOKEN_EXPIRE, TimeUnit.HOURS);

            throw new BaseException(ErrorEnum.A_LOGIN_ERROR);
        }

        //login success（失败计数清零，登录成功重置门槛）
        stringRedisTemplate.delete(failCountKey);
        String token = setLoginToken(user);
        LoginResp loginResp = new LoginResp();
        loginResp.setToken(token);
        BeanUtils.copyProperties(user, loginResp);
        return loginResp;
    }

    /**
     * 校验登录验证码并**一次性消费**。
     * <p>
     * 2026-10-15 修复：此前比对成功后不删除 Redis 键，一个验证码在 1 小时过期窗口内可无限复用
     * ——达到验证码门槛后，攻击者人工看一次图，之后可脚本化无限次试密码，门槛形同虚设。
     * 消费（delete）必须发生在比对成功之后、密码校验之前。
     * <p>
     * 单独抽方法：login 全流程依赖 lambdaQuery（mock 环境跑不通），抽出后消费逻辑可被单测覆盖。
     */
    void consumeLoginCaptcha(String captchaId, String captchaCode) {
        String captchaIdKey = MessageFormat.format(AUTH_LOGIN_CAPTCHA_ID, captchaId);
        String captcha = stringRedisTemplate.opsForValue().get(captchaIdKey);
        if (StringUtils.isBlank(captcha) || !captcha.equalsIgnoreCase(captchaCode)) {
            throw new BaseException(A_LOGIN_CAPTCHA_ERROR);
        }
        stringRedisTemplate.delete(captchaIdKey);
    }

    /**
     * captchaId 会拼进 Redis key（{@code AUTH_LOGIN_CAPTCHA_ID}），必须限制格式，
     * 否则任意字符串都能进 Redis key 空间。与生成端（前端 uuid / {@code UuidUtil.createShort()}）
     * 对齐：字母数字与连字符，长度 6~64。
     */
    private void assertSafeCaptchaId(String captchaId) {
        if (StringUtils.isBlank(captchaId)) {
            return; // 未到验证码门槛时允许为空，后续分支再要求
        }
        if (captchaId.length() < 6 || captchaId.length() > 64
                || !captchaId.matches("[A-Za-z0-9-]+")) {
            throw new BaseException(ErrorEnum.A_LOGIN_CAPTCHA_ERROR);
        }
    }

    /**
     * 设置并获取登录验证码ID|Set and get login captcha ID
     *
     * @return 登录验证码ID|login captcha ID
     */
    public String setAndGetLoginCaptchaId() {
        String captchaId = UuidUtil.createShort();
        String captchaIdKey = MessageFormat.format(AUTH_LOGIN_CAPTCHA_ID, captchaId);
        stringRedisTemplate.opsForValue().set(captchaIdKey, captchaId, AdiConstant.AUTH_CAPTCHA_ID_EXPIRE, TimeUnit.HOURS);
        return captchaId;
    }

    /**
     * 缓存登录验证码|Cache login captcha
     *
     * @param captchaId 验证码ID|captcha ID
     * @param captcha   验证码|captcha
     */
    public void cacheLoginCaptcha(String captchaId, String captcha) {
        String captchaIdKey = MessageFormat.format(AUTH_LOGIN_CAPTCHA_ID, captchaId);
        stringRedisTemplate.opsForValue().set(captchaIdKey, captcha, AdiConstant.AUTH_CAPTCHA_ID_EXPIRE, TimeUnit.HOURS);
    }

    /**
     * 缓存注册验证码|Cache register captcha
     *
     * @param captchaId 验证码ID|captcha ID
     * @param captcha   验证码|captcha
     */
    public void cacheRegisterCaptcha(String captchaId, String captcha) {
        String captchaIdKey = MessageFormat.format(AUTH_REGISTER_CAPTCHA_ID, captchaId);
        stringRedisTemplate.opsForValue().set(captchaIdKey, captcha, AdiConstant.AUTH_CAPTCHA_ID_EXPIRE, TimeUnit.HOURS);
    }

    /**
     * 获取配置|Get config
     *
     * @return 配置响应|config response
     */
    public ConfigResp getConfig(HttpServletRequest request) {
        ConfigResp result = new ConfigResp();
        User user = ThreadContext.getCurrentUser();

        //User quota
        result.setUserQuota(UserQuota.builder().requestTimesByDay(user.getQuotaByRequestDaily()).requestTimesByMonth(user.getQuotaByRequestMonthly()).drawByDay(user.getQuotaByImageDaily()).drawByMonth(user.getQuotaByImageMonthly()).tokenByDay(user.getQuotaByTokenDaily()).tokenByMonth(user.getQuotaByTokenMonthly()).build());
        //User cost
        CostStatResp quotaCostResp = new CostStatResp();
        setPaidCostStat(user, quotaCostResp);
        setFreeCostStat(user, quotaCostResp);
        result.setQuotaCost(quotaCostResp);
        String acceptLanguage = request != null ? request.getHeader("Accept-Language") : null;
        String effectiveLocale = resolveLocale(user, acceptLanguage);
        result.setLocale(effectiveLocale);
        return result;
    }

    /**
     * Resolve effective locale: User locale > Accept-Language > Config default
     */
    private String resolveLocale(User user, String acceptLanguage) {
        if (StringUtils.isNotBlank(user.getLocale())) {
            return user.getLocale();
        }
        if (StringUtils.isNotBlank(acceptLanguage)) {
            String lang = acceptLanguage.split(",")[0].trim().toLowerCase();
            if (lang.startsWith("zh")) {
                return "zh-CN";
            }
            if (lang.startsWith("en")) {
                return "en-US";
            }
        }
        String configLocale = SysConfigService.getByKey(AdiConstant.SysConfigKey.DEFAULT_LOCALE);
        if (StringUtils.isNotBlank(configLocale)) {
            return configLocale;
        }
        return "zh-CN";
    }

    /**
     * 设置付费费用统计|Set paid cost statistics
     *
     * @param user          用户|user
     * @param quotaCostResp 配额费用响应|quota cost response
     */
    private void setPaidCostStat(User user, CostStatResp quotaCostResp) {
        CostStat cost = userDayCostService.costStatByUser(user.getId(), false);
        quotaCostResp.setPaidTokenCost(TokenCostStatistic.builder().todayTokenCost(cost.getTextTokenCostByDay()).monthTokenCost(cost.getTextTokenCostByMonth()).build());
        quotaCostResp.setPaidRequestTimes(RequestTimesStatistic.builder().todayRequestTimes(cost.getTextRequestTimesByDay()).monthRequestTimes(cost.getTextRequestTimesByMonth()).build());
        quotaCostResp.setPaidDrawTimes(DrawTimesStatistic.builder().todayDrawTimes(cost.getDrawTimesByDay()).monthDrawTimes(cost.getDrawTimesByMonth()).build());
    }

    /**
     * 设置免费费用统计|Set free cost statistics
     *
     * @param user          用户|user
     * @param quotaCostResp 配额费用响应|quota cost response
     */
    private void setFreeCostStat(@NotNull User user, CostStatResp quotaCostResp) {
        CostStat cost = userDayCostService.costStatByUser(user.getId(), true);
        quotaCostResp.setFreeTokenCost(TokenCostStatistic.builder().todayTokenCost(cost.getTextTokenCostByDay()).monthTokenCost(cost.getTextTokenCostByMonth()).build());
        quotaCostResp.setFreeRequestTimes(RequestTimesStatistic.builder().todayRequestTimes(cost.getTextRequestTimesByDay()).monthRequestTimes(cost.getTextRequestTimesByMonth()).build());
        quotaCostResp.setFreeDrawTimes(DrawTimesStatistic.builder().todayDrawTimes(cost.getDrawTimesByDay()).monthDrawTimes(cost.getDrawTimesByMonth()).build());
    }

    /**
     * 更新配置|Update config
     *
     * @param userUpdateReq 用户更新请求|user update request
     */
    public void updateConfig(UserUpdateReq userUpdateReq) {
        Long userId = ThreadContext.getCurrentUserId();
        User user = new User();
        user.setId(userId);
        BeanUtils.copyProperties(userUpdateReq, user);
        baseMapper.updateById(user);

        // Refresh Redis cache with latest data from DB
        User updatedUser = baseMapper.selectById(userId);
        String token = ThreadContext.getToken();
        String tokenKey = MessageFormat.format(USER_TOKEN, token);
        stringRedisTemplate.opsForValue().set(tokenKey, JsonUtil.toJson(updatedUser), AdiConstant.USER_TOKEN_EXPIRE, TimeUnit.HOURS);
    }

    /**
     * 注销|Logout
     */
    public void logout() {
        String token = ThreadContext.getToken();
        if (null == token) {
            log.warn("logout token is null");
            return;
        }
        String tokenKey = MessageFormat.format(USER_TOKEN, token);
        stringRedisTemplate.delete(tokenKey);
    }

    /**
     * 设置用户的登录令牌|Set the login token for the user
     *
     * @param user 要设置登录令牌的用户|the user for whom the login token is being set
     * @return 生成的登录令牌|the generated login token
     */
    private int parseIntConfig(String key) {
        String value = LocalCache.CONFIGS.get(key);
        if (value == null) {
            throw new IllegalStateException("System config missing: " + key);
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalStateException("System config format error: " + key + "=" + value, e);
        }
    }

    private String setLoginToken(User user) {
        if (user.getQuotaByTokenDaily() == 0) {
            user.setQuotaByTokenDaily(parseIntConfig(AdiConstant.SysConfigKey.QUOTA_BY_TOKEN_DAILY));
        }
        if (user.getQuotaByTokenMonthly() == 0) {
            user.setQuotaByTokenMonthly(parseIntConfig(AdiConstant.SysConfigKey.QUOTA_BY_TOKEN_MONTHLY));
        }
        if (user.getQuotaByRequestDaily() == 0) {
            user.setQuotaByRequestDaily(parseIntConfig(AdiConstant.SysConfigKey.QUOTA_BY_REQUEST_DAILY));
        }
        if (user.getQuotaByRequestMonthly() == 0) {
            user.setQuotaByRequestMonthly(parseIntConfig(AdiConstant.SysConfigKey.QUOTA_BY_REQUEST_MONTHLY));
        }
        if (user.getQuotaByImageDaily() == 0) {
            user.setQuotaByImageDaily(parseIntConfig(AdiConstant.SysConfigKey.QUOTA_BY_IMAGE_DAILY));
        }
        if (user.getQuotaByImageMonthly() == 0) {
            user.setQuotaByImageMonthly(parseIntConfig(AdiConstant.SysConfigKey.QUOTA_BY_IMAGE_MONTHLY));
        }
        String token = UuidUtil.createShort();
        String tokenKey = MessageFormat.format(USER_TOKEN, token);
        String jsonUser = JsonUtil.toJson(user);
//        log.info("jsonUser:{}", jsonUser);
        stringRedisTemplate.opsForValue().set(tokenKey, jsonUser, AdiConstant.USER_TOKEN_EXPIRE, TimeUnit.HOURS);
        // S-08③：维护 userId -> tokens 反查集合，供改密/重置后踢会话使用。
        // 集合 TTL 与单 token 一致；每次登录刷新，天然覆盖「8 小时内再登录续期」的场景。
        String tokenSetKey = MessageFormat.format(USER_TOKEN_SET, user.getId());
        stringRedisTemplate.opsForSet().add(tokenSetKey, token);
        stringRedisTemplate.expire(tokenSetKey, AdiConstant.USER_TOKEN_EXPIRE, TimeUnit.HOURS);
        return token;
    }

    /**
     * 发送激活链接|Send activation email
     *
     * @param email 用户邮箱|user email
     * @return true=已投递（或 mock 模式下已落盘）；false=发送失败
     */
    public boolean sendActiveEmail(String email) {
        String activeCode = UuidUtil.createShort();
        String activeCodeKey = MessageFormat.format(AUTH_ACTIVE_CODE, activeCode);
        stringRedisTemplate.opsForValue().set(activeCodeKey, email, AdiConstant.AUTH_ACTIVE_CODE_EXPIRE, TimeUnit.HOURS);
        String activeLink = adiProperties.getBackendUrl() + "/auth/active?code=" + activeCode;
        String subject = SpringUtil.getMessage("mail.welcomeSubject");
        String body = MessageFormat.format(SpringUtil.getMessage("mail.welcomeBody"), AdiConstant.AUTH_ACTIVE_CODE_EXPIRE, activeLink);
        boolean sent = adiMailSender.send(subject, body, email);
        if (!sent) {
            //失败时回收激活码，避免留下永远无法使用的脏数据
            stringRedisTemplate.delete(activeCodeKey);
        }
        return sent;
    }

    /**
     * 通过用户ID获取用户|Get user by user ID
     *
     * @param id 用户ID|user ID
     * @return 用户|user
     */
    @Cacheable(cacheNames = USER_INFO, condition = "#id>0", key = "#p0")
    public User getByUserId(Long id) {
        return ChainWrappers.lambdaQueryChain(baseMapper).eq(User::getId, id).one();
    }

    /**
     * 通过UUID获取用户|Get user by UUID
     *
     * @param uuid 用户UUID|user UUID
     * @return 用户|user
     */
    public User getByUuid(String uuid) {
        return ChainWrappers.lambdaQueryChain(baseMapper).eq(User::getUuid, uuid).one();
    }

    /**
     * 通过UUID获取用户，如果不存在则抛出异常|Get user by UUID or throw exception if not exist
     *
     * @param uuid 用户UUID|user UUID
     * @return 用户|user
     */
    public User getByUuidOrThrow(String uuid) {
        User user = this.getByUuid(uuid);
        if (null == user) {
            throw new BaseException(A_USER_NOT_EXIST);
        }
        return user;
    }

    /**
     * 搜索用户|Search users
     *
     * @param req         用户搜索请求|user search request
     * @param currentPage 当前页码|current page number
     * @param pageSize    每页大小|page size
     * @return 用户信息分页|page of user information
     */
    public Page<UserInfoDto> search(UserSearchReq req, Integer currentPage, Integer pageSize) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.isNotBlank(req.getName())) {
            wrapper.like(User::getName, req.getName());
        }
        if (StringUtils.isNotBlank(req.getUuid())) {
            wrapper.eq(User::getUuid, req.getUuid());
        }
        if (StringUtils.isNotBlank(req.getEmail())) {
            wrapper.eq(User::getEmail, req.getEmail());
        }
        if (null != req.getUserStatus()) {
            wrapper.eq(User::getUserStatus, UserStatusEnum.getByValue(req.getUserStatus()));
        }
        if (null != req.getCreateTime() && req.getCreateTime().length == 2) {
            wrapper.between(User::getCreateTime, LocalDateTimeUtil.parse(req.getCreateTime()[0]), LocalDateTimeUtil.parse(req.getCreateTime()[1]));
        }
        if (null != req.getUpdateTime() && req.getUpdateTime().length == 2) {
            wrapper.between(User::getUpdateTime, LocalDateTimeUtil.parse(req.getUpdateTime()[0]), LocalDateTimeUtil.parse(req.getUpdateTime()[1]));
        }
        if (null != req.getIsAdmin()) {
            wrapper.eq(User::getIsAdmin, req.getIsAdmin());
        }
        wrapper.eq(User::getIsDeleted, false);
        wrapper.orderByDesc(User::getUpdateTime);
        Page<User> page = baseMapper.selectPage(new Page<>(currentPage, pageSize), wrapper);
        Page<UserInfoDto> result = new Page<>();
        return MPPageUtil.convertToPage(page, result, UserInfoDto.class);
    }

    /**
     * 添加用户|Add user
     *
     * @param addUserReq 添加用户请求|add user request
     * @return 用户信息|user information
     */
    public UserInfoDto addUser(UserAddReq addUserReq) {
        User user = this.lambdaQuery().eq(User::getIsDeleted, false).eq(User::getEmail, addUserReq.getEmail()).one();
        if (null != user) {
            throw new BaseException(A_USER_EXIST);
        }

        String hashed = BCrypt.hashpw(addUserReq.getPassword(), BCrypt.gensalt());
        String uuid = UuidUtil.createShort();
        User newOne = new User();
        if (StringUtils.isNotBlank(addUserReq.getName())) {
            newOne.setName(addUserReq.getName());
        } else {
            newOne.setName(StringUtils.substringBefore(addUserReq.getEmail(), "@"));
        }
        newOne.setUuid(uuid);
        newOne.setEmail(addUserReq.getEmail());
        newOne.setPassword(hashed);
        newOne.setUserStatus(UserStatusEnum.NORMAL);
        newOne.setActiveTime(LocalDateTime.now());
        // 设置配额字段
        if (addUserReq.getQuotaByTokenDaily() != null) {
            newOne.setQuotaByTokenDaily(addUserReq.getQuotaByTokenDaily());
        }
        if (addUserReq.getQuotaByTokenMonthly() != null) {
            newOne.setQuotaByTokenMonthly(addUserReq.getQuotaByTokenMonthly());
        }
        if (addUserReq.getQuotaByRequestDaily() != null) {
            newOne.setQuotaByRequestDaily(addUserReq.getQuotaByRequestDaily());
        }
        if (addUserReq.getQuotaByRequestMonthly() != null) {
            newOne.setQuotaByRequestMonthly(addUserReq.getQuotaByRequestMonthly());
        }
        if (addUserReq.getQuotaByImageDaily() != null) {
            newOne.setQuotaByImageDaily(addUserReq.getQuotaByImageDaily());
        }
        if (addUserReq.getQuotaByImageMonthly() != null) {
            newOne.setQuotaByImageMonthly(addUserReq.getQuotaByImageMonthly());
        }
        if (addUserReq.getIsAdmin() != null) {
            newOne.setIsAdmin(addUserReq.getIsAdmin());
        }
        baseMapper.insert(newOne);

        UserInfoDto result = new UserInfoDto();
        User newUser = this.getByUuid(uuid);
        BeanUtils.copyProperties(newUser, result);
        return result;
    }
}
