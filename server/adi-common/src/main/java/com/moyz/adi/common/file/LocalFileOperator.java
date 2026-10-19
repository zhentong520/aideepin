package com.moyz.adi.common.file;

import com.moyz.adi.common.entity.AdiFile;
import com.moyz.adi.common.exception.BaseException;
import com.moyz.adi.common.vo.SaveRemoteImageResult;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;
import dev.langchain4j.data.document.parser.apache.poi.ApachePoiDocumentParser;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.jsoup.Jsoup;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

import static com.moyz.adi.common.cosntant.AdiConstant.CSV_DOC_TYPES;
import static com.moyz.adi.common.cosntant.AdiConstant.HTML_DOC_TYPES;
import static com.moyz.adi.common.cosntant.AdiConstant.POI_DOC_TYPES;
import static com.moyz.adi.common.cosntant.AdiConstant.TEXT_DOC_TYPES;
import static com.moyz.adi.common.cosntant.AdiConstant.URL_PREFIX_FILE;
import static com.moyz.adi.common.enums.ErrorEnum.*;

@Slf4j
public class LocalFileOperator implements IFileOperator {

    public static String imagePath;

    public static String tmpImagePath;

    public static String filePath;

    @Override
    public boolean checkIfExist(AdiFile adiFile) {
        return LocalFileUtil.checkIfExist(adiFile.getPath());
    }

    @Override
    public Pair<String, String> save(MultipartFile file, boolean image, String uuid) {
        return LocalFileUtil.saveToLocal(file, image ? imagePath : filePath, uuid);
    }

    @Override
    public Pair<String, String> save(byte[] file, boolean image, String name) {
        return LocalFileUtil.saveToLocal(file, image ? imagePath : filePath, name);
    }

    @Override
    public SaveRemoteImageResult saveImageFromUrl(String imageUrl, String uuid) {
        String ext = LocalFileUtil.getFileExtension(imageUrl);
        if (StringUtils.isBlank(ext)) {
            ext = "png";
        }
        String filePath = imagePath + uuid + "." + ext;
        File target = new File(filePath);
        try {
            FileUtils.createParentDirectories(target);
            FileUtils.copyURLToFile(new URL(imageUrl), target);
        } catch (IOException e) {
            log.error("saveToLocal", e);
            throw new BaseException(B_SAVE_IMAGE_ERROR);
        }
        return SaveRemoteImageResult.builder().ext(ext).originalName(target.getName()).pathOrUrl(filePath).build();
    }

    @Override
    public void delete(AdiFile adiFile) {
        if (StringUtils.isBlank(adiFile.getPath())) {
            return;
        }
        try {
            boolean deletedResult = Files.deleteIfExists(Paths.get(adiFile.getPath()));
            if (!deletedResult) {
                log.warn("Delete file fail,uuid:{}", adiFile.getUuid());
            }
        } catch (IOException e) {
            log.error("delete file error", e);
            throw new BaseException(B_DELETE_FILE_ERROR);
        }
    }

    @Override
    public String getFileUrl(AdiFile adiFile) {
        return URL_PREFIX_FILE + adiFile.getUuid() + "." + adiFile.getExt();
    }

    @Override
    public Document loadDocument(AdiFile adiFile) {
        Document result = null;
        String path = adiFile.getPath();
        // Compare in lower case: the previous version matched POI_DOC_TYPES against the raw ext,
        // so an uppercase ".DOCX" matched nothing and fell through as "unsupported" — the same
        // trap that swallowed .md uploads (upload looked successful, no document row, no error).
        String ext = null == adiFile.getExt() ? "" : adiFile.getExt().toLowerCase(Locale.ROOT);
        if (ArrayUtils.contains(TEXT_DOC_TYPES, ext)) {
            // txt / md / markdown: plain text, read as-is (Markdown markup is kept)
            result = FileSystemDocumentLoader.loadDocument(path, new TextDocumentParser());
        } else if ("pdf".equals(ext)) {
            result = FileSystemDocumentLoader.loadDocument(path, new ApachePdfBoxDocumentParser());
        } else if (ArrayUtils.contains(POI_DOC_TYPES, ext)) {
            result = FileSystemDocumentLoader.loadDocument(path, new ApachePoiDocumentParser());
        } else if (ArrayUtils.contains(HTML_DOC_TYPES, ext)) {
            result = loadHtmlDocument(path);
        } else if (ArrayUtils.contains(CSV_DOC_TYPES, ext)) {
            result = FileSystemDocumentLoader.loadDocument(path, new TextDocumentParser());
        }
        return result;
    }

    /**
     * HTML 解析：用 Jsoup 抽取可见正文（去除 script/style/标签），再包成 langchain4j Document。
     * 任何异常都返回 null（交由上层按「不支持类型」处理），不阻断其他文件。
     */
    private static Document loadHtmlDocument(String path) {
        try {
            String html = FileUtils.readFileToString(new File(path), java.nio.charset.StandardCharsets.UTF_8);
            String text = Jsoup.parse(html).text();
            if (StringUtils.isBlank(text)) {
                return null;
            }
            return Document.from(text);
        } catch (Exception e) {
            log.warn("loadHtmlDocument failed,path:{},msg:{}", path, e.getMessage());
            return null;
        }
    }

    public static void init(String imagePath, String tmpImagePath, String filePath) {
        LocalFileOperator.imagePath = imagePath;
        LocalFileOperator.tmpImagePath = tmpImagePath;
        LocalFileOperator.filePath = filePath;
    }

    public static boolean checkIfExist(String filePath) {
        Path path = Paths.get(filePath);
        return Files.exists(path);
    }

    public static boolean checkAndCreateDir(String dir) {
        Path dirPath = Paths.get(dir);
        if (!Files.exists(dirPath)) {
            try {
// Create directory including all non-existent parent directories
                // 创建目录，包括所有不存在的父目录
                // Create directory including all non-existent parent directories
                Files.createDirectories(dirPath);
            } catch (IOException e) {
                System.err.println("Error creating directory: " + e.getMessage());
                throw new BaseException(B_DIR_CREATE_FAIL);
            }
        }
        return true;
    }
}
