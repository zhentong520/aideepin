package com.moyz.adi.common.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.extension.toolkit.ChainWrappers;
import com.moyz.adi.common.base.ThreadContext;
import com.moyz.adi.common.entity.AdiFile;
import com.moyz.adi.common.entity.KbDocument;
import com.moyz.adi.common.entity.User;
import com.moyz.adi.common.enums.ErrorEnum;
import com.moyz.adi.common.exception.BaseException;
import com.moyz.adi.common.file.FileOperatorContext;
import com.moyz.adi.common.file.LocalFileUtil;
import com.moyz.adi.common.constant.AdiConstant;
import com.moyz.adi.common.mapper.FileMapper;
import com.moyz.adi.common.mapper.KbDocumentMapper;
import com.moyz.adi.common.util.HashUtil;
import com.moyz.adi.common.util.UuidUtil;
import com.moyz.adi.common.vo.SaveRemoteImageResult;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.moyz.adi.common.enums.ErrorEnum.A_AI_IMAGE_NO_AUTH;
import static com.moyz.adi.common.enums.ErrorEnum.A_FILE_NOT_EXIST;
import static com.moyz.adi.common.enums.ErrorEnum.A_FILE_IN_USE;
import static com.moyz.adi.common.enums.ErrorEnum.A_UPLOAD_FILE_TYPE_NOT_ALLOWED;
import static com.moyz.adi.common.enums.ErrorEnum.A_UPLOAD_IMAGE_TYPE_NOT_ALLOWED;

@Slf4j
@Service
public class FileService extends ServiceImpl<FileMapper, AdiFile> {

    /**
     * S1-15（G-10）：物理删除前的引用检查需要查知识库文档表（source_file_id 列）。
     * KbDocumentMapper 在本模块，无循环依赖。
     */
    @Resource
    private KbDocumentMapper kbDocumentMapper;

    @Value("${local.images}")
    private String imagePath;

    @Value("${local.watermark-images}")
    private String watermarkImagesPath;

    @Value("${local.thumbnails}")
    private String thumbnailsPath;

    @Value("${local.watermark-thumbnails}")
    private String watermarkThumbnailsPath;

    @Value("${local.files}")
    private String filePath;

    @Value("${local.tmp-images}")
    private String tmpImagesPath;

    private static final List<String> BINARY_EXTENSIONS = List.of(
// Executable/Binary
            // 可执行/二进制
            "exe", "dll", "so", "dylib", "com", "msi",
            "jar", "war", "ear", "class",
            "iso", "dmg", "bin", "apk", "ipa", "deb", "rpm",
// Archive file
            // 压缩包
            "zip", "rar", "7z", "tar", "gz", "bz2",
            // 视频
            "mp4", "avi", "mov", "wmv", "flv", "mkv", "webm", "m4v", "mpeg", "3gp"
    );

    public AdiFile saveFile(MultipartFile file, boolean image) {
        String originalFilename = file.getOriginalFilename();
        if (StringUtils.isBlank(originalFilename) || !originalFilename.contains(".")) {
            throw new BaseException(A_UPLOAD_FILE_TYPE_NOT_ALLOWED);
        }
        String ext = originalFilename.substring(originalFilename.lastIndexOf('.') + 1).toLowerCase();
        if (BINARY_EXTENSIONS.contains(ext)) {
            throw new BaseException(A_UPLOAD_FILE_TYPE_NOT_ALLOWED);
        }
        if (image && !AdiConstant.IMAGE_EXTENSIONS.contains(ext)) {
            throw new BaseException(A_UPLOAD_IMAGE_TYPE_NOT_ALLOWED);
        }
        String sha256 = HashUtil.sha256(file);
        Optional<AdiFile> existFile = this.lambdaQuery()
                .eq(AdiFile::getSha256, sha256)
                .eq(AdiFile::getIsDeleted, false)
                .last("limit 1")
                .oneOpt();
        if (existFile.isPresent()) {
            AdiFile adiFile = existFile.get();
            boolean exist = FileOperatorContext.checkIfExist(adiFile);
            if (exist) {
                return adiFile;
            } else {
                log.warn("File not found, deleting record for regeneration, fileId:{}, uuid:{}, sha256:{}", adiFile.getId(), adiFile.getUuid(), adiFile.getSha256());
                this.lambdaUpdate().eq(AdiFile::getId, adiFile.getId()).set(AdiFile::getIsDeleted, true).update();
            }
        }
        String uuid = UuidUtil.createShort();
        Pair<String, String> originalFile = new FileOperatorContext().save(file, image, uuid);
        AdiFile adiFile = new AdiFile();
        adiFile.setName(file.getOriginalFilename());
        adiFile.setUuid(uuid);
        adiFile.setSha256(sha256);
        adiFile.setPath(originalFile.getLeft());
        adiFile.setExt(originalFile.getRight());
        adiFile.setUserId(ThreadContext.getCurrentUserId());
        adiFile.setStorageLocation(FileOperatorContext.getStorageLocation());
        this.getBaseMapper().insert(adiFile);
        return adiFile;
    }

    public AdiFile saveImageFromUrl(User user, String sourceImageUrl) {
        log.info("saveImageFromUrl,sourceImageUrl:{}", sourceImageUrl);
        String uuid = UuidUtil.createShort();
        SaveRemoteImageResult saveResult = new FileOperatorContext().saveImageFromUrl(sourceImageUrl, uuid);
        AdiFile adiFile = new AdiFile();
        adiFile.setName(saveResult.getOriginalName());
        adiFile.setUuid(uuid);
        adiFile.setSha256(HashUtil.sha256(saveResult.getPathOrUrl()));
        adiFile.setPath(saveResult.getPathOrUrl());
        adiFile.setUserId(user.getId());
        adiFile.setExt(saveResult.getExt());
        adiFile.setStorageLocation(FileOperatorContext.getStorageLocation());
        this.getBaseMapper().insert(adiFile);
        return adiFile;
    }

    public AdiFile saveFromPath(User user, String pathOrUrl) {
        log.info("saveImageFromPath,path:{}", pathOrUrl);
        Pair<String, String> nameAndExt = LocalFileUtil.getNameAndExt(pathOrUrl);
        String uuid = UuidUtil.createShort();
        AdiFile adiFile = new AdiFile();
        adiFile.setName(nameAndExt.getLeft());
        adiFile.setUuid(uuid);
        adiFile.setSha256(HashUtil.sha256(pathOrUrl));
        adiFile.setPath(pathOrUrl);
        adiFile.setUserId(user.getId());
        adiFile.setExt(nameAndExt.getRight());
        adiFile.setStorageLocation(FileOperatorContext.getStorageLocation());
        this.getBaseMapper().insert(adiFile);
        return adiFile;
    }

    public boolean softDel(String uuid) {
        return this.lambdaUpdate()
                .eq(AdiFile::getUserId, ThreadContext.getCurrentUserId())
                .eq(AdiFile::getUuid, uuid)
                .set(AdiFile::getIsDeleted, true)
                .update();
    }

    /**
     * S1-15：引用计数原子减一（G-10 补齐「增加对应减一」的另一半）。
     * WHERE ref_count > 0 保证并发下不会减成负数；返回是否实际减掉。
     * 调用方（如绘图记录删除）应在删除文件前先减掉自己加过的引用。
     */
    public boolean decrementRefCount(String uuid) {
        return this.lambdaUpdate()
                .eq(AdiFile::getUuid, uuid)
                .gt(AdiFile::getRefCount, 0)
                .setSql("ref_count = ref_count - 1")
                .update();
    }

    /**
     * 物理删除文件并软删记录（S1-15 / G-10 加固）。
     * <p>
     * 旧实现拿到文件后直接 {@code FileOperatorContext.delete}：被知识库文档引用的
     * 源文件、被绘图记录等引用（ref_count &gt; 0）的文件都会被无声删掉，前端立刻 404。
     * 现在：
     * <ol>
     *   <li>被活跃（未删除）知识库文档引用 → 拒绝（A_FILE_IN_USE），让用户先删文档；</li>
     *   <li>ref_count &gt; 0（绘图等场景的显式引用）→ 拒绝，调用方负责先 {@link #decrementRefCount}；</li>
     *   <li>通过检查后，主文件、缩略图、水印副本、水印缩略图一并删除（后三者尽力而为，
     *       历史缺失/已删不阻断）。</li>
     * </ol>
     */
    public boolean removeFileAndSoftDel(String uuid) {
        AdiFile adiFile = this.lambdaQuery()
                .eq(AdiFile::getUserId, ThreadContext.getCurrentUserId())
                .eq(AdiFile::getUuid, uuid)
                .oneOpt()
                .orElse(null);
        if (null == adiFile) {
            return false;
        }
        // 引用检查 1：活跃知识库文档的源文件（软删文档不拦——文档删了文件就是孤儿，可安全物理删）
        LambdaQueryWrapper<KbDocument> refWrapper = new LambdaQueryWrapper<KbDocument>()
                .eq(KbDocument::getSourceFileId, adiFile.getId())
                .eq(KbDocument::getIsDeleted, false);
        boolean referencedByDoc = kbDocumentMapper.exists(refWrapper);
        if (referencedByDoc) {
            throw new BaseException(A_FILE_IN_USE, "knowledge base document");
        }
        // 引用检查 2：显式引用计数（绘图成功图等；引用方删除前须先 decrementRefCount）
        if (null != adiFile.getRefCount() && adiFile.getRefCount() > 0) {
            throw new BaseException(A_FILE_IN_USE, "other records (ref_count=" + adiFile.getRefCount() + ")");
        }
        FileOperatorContext.delete(adiFile);
        deleteDerivedCopies(adiFile);
        return this.softDel(uuid);
    }

    /**
     * S1-15：随主文件删除其派生副本（缩略图 / 水印副本 / 水印缩略图）。
     * 旧实现只删主文件，派生副本永远残留（磁盘无限增长）。
     * 尽力而为：任何副本缺失或删除失败只记日志，不阻断主文件删除。
     */
    private void deleteDerivedCopies(AdiFile adiFile) {
        String name = adiFile.getUuid() + "." + adiFile.getExt();
        for (String dir : List.of(thumbnailsPath, watermarkImagesPath, watermarkThumbnailsPath)) {
            if (StringUtils.isBlank(dir)) {
                continue;
            }
            try {
                Path path = Paths.get(dir, name);
                if (!Files.deleteIfExists(path)) {
                    log.debug("Derived copy already absent, path:{}", path);
                }
            } catch (Exception e) {
                log.warn("Delete derived copy failed, path:{}{}", dir, name, e);
            }
        }
    }

    public AdiFile getByUuid(String uuid) {
        return this.lambdaQuery()
                .eq(AdiFile::getUuid, uuid)
                .eq(AdiFile::getUserId, ThreadContext.getCurrentUserId())
                .oneOpt().orElse(null);
    }

    /**
     * 读取图片到BufferedImage，管理员或图片拥有者才有权限查看
     *
     * @param uuid      图片uuid
     * @param thumbnail 读取的是缩略图
     * @return 图片内容
     */
    public BufferedImage readMyImage(String uuid, boolean thumbnail) {
        if (StringUtils.isBlank(ThreadContext.getToken())) {
            throw new BaseException(A_AI_IMAGE_NO_AUTH);
        }
        AdiFile adiFile = this.lambdaQuery()
                .eq(!ThreadContext.getCurrentUser().getIsAdmin(), AdiFile::getUserId, ThreadContext.getCurrentUserId())
                .eq(AdiFile::getUuid, uuid)
                .oneOpt().orElse(null);
        if (null == adiFile) {
            throw new BaseException(A_FILE_NOT_EXIST);
        }
        return LocalFileUtil.readLocalImage(adiFile, thumbnail, thumbnailsPath);
    }

    public BufferedImage readImage(String uuid, boolean thumbnail) {
        AdiFile adiFile = this.lambdaQuery()
                .eq(AdiFile::getUuid, uuid)
                .oneOpt().orElse(null);
        if (null == adiFile) {
            throw new BaseException(A_FILE_NOT_EXIST);
        }
        return LocalFileUtil.readLocalImage(adiFile, thumbnail, thumbnailsPath);
    }

    public String getImagePath(String uuid) {
        AdiFile adiFile = this.lambdaQuery()
                .eq(AdiFile::getUuid, uuid)
                .oneOpt().orElse(null);
        if (null == adiFile) {
            throw new BaseException(ErrorEnum.A_AI_IMAGE_NOT_FOUND);
        }
        return adiFile.getPath();
    }


    public AdiFile getFile(String uuid) {
        AdiFile adiFile = this.lambdaQuery()
                .eq(AdiFile::getUuid, uuid)
                .oneOpt().orElse(null);
        if (null == adiFile) {
            throw new BaseException(ErrorEnum.A_AI_IMAGE_NOT_FOUND);
        }
        return adiFile;
    }

    public String getTmpImagesPath(String uuid) {
        AdiFile adiFile = this.lambdaQuery()
                .eq(AdiFile::getUuid, uuid)
                .oneOpt().orElse(null);
        if (null == adiFile) {
            throw new BaseException(ErrorEnum.A_AI_IMAGE_NOT_FOUND);
        }
        return tmpImagesPath + uuid + "." + adiFile.getExt();
    }

    public String getWatermarkImagesPath(String uuid) {
        AdiFile adiFile = this.lambdaQuery()
                .eq(AdiFile::getUuid, uuid)
                .oneOpt().orElse(null);
        if (null == adiFile) {
            throw new BaseException(ErrorEnum.A_AI_IMAGE_NOT_FOUND);
        }
        return watermarkImagesPath + uuid + "." + adiFile.getExt();
    }

    public String getWatermarkImagesPath(AdiFile adiFile) {
        return watermarkImagesPath + adiFile.getUuid() + "." + adiFile.getExt();
    }

    /**
     * 批量取文件url，keyed by 文件uuid（缺失/已删除的文件不产生条目）：
     * 列表页一次查询回填，替代逐行 getUrl 的 N+1
     */
    public Map<String, String> getUrlByUuids(Collection<String> fileUuids) {
        if (CollectionUtils.isEmpty(fileUuids)) {
            return Collections.emptyMap();
        }
        Map<String, String> result = new HashMap<>();
        this.lambdaQuery()
                .in(AdiFile::getUuid, fileUuids)
                .eq(AdiFile::getIsDeleted, false)
                .list()
                .forEach(adiFile -> result.put(adiFile.getUuid(), FileOperatorContext.getFileUrl(adiFile)));
        return result;
    }

    public String getUrl(String fileUuid) {
        if (StringUtils.isBlank(fileUuid)) {
            return null;
        }
        List<String> list = getUrls(List.of(fileUuid));
        if (!list.isEmpty()) {
            return list.get(0);
        }
        return null;
    }

    /**
     * 获取文件url
     *
     * @param fileUuids 文件uuid
     * @return 文件url
     */
    public List<String> getUrls(List<String> fileUuids) {
        if (CollectionUtils.isEmpty(fileUuids)) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>();
        this.lambdaQuery()
                .in(AdiFile::getUuid, fileUuids)
                .eq(AdiFile::getIsDeleted, false)
                .list()
                .forEach(adiFile -> {
                    result.add(FileOperatorContext.getFileUrl(adiFile));
                });
        return result;
    }
}
