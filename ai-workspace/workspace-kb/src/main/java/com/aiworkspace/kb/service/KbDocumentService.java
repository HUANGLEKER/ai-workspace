package com.aiworkspace.kb.service;

import com.aiworkspace.kb.entity.KbDocument;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 知识库文档服务接口
 *
 * 定义文档的上传、查询与删除能力。所有方法均经所属知识库的归属校验，
 * 文件本体落在 MinIO，删除时需同步清理 MinIO 对象与 ChromaDB 中的向量数据。
 */
public interface KbDocumentService extends IService<KbDocument> {

    /**
     * 分页查询指定知识库下的文档（先校验知识库归属）
     *
     * @param kbId   知识库 ID
     * @param page   页码（从 1 开始）
     * @param size   每页条数
     * @param userId 当前用户 ID
     * @return 文档分页结果
     */
    Page<KbDocument> pageByKbId(Long kbId, int page, int size, Long userId);

    /**
     * 上传文档到知识库：存入 MinIO、落库为 PENDING，并触发异步 embedding 构建
     *
     * @param kbId   目标知识库 ID
     * @param file   上传的文件
     * @param userId 当前用户 ID
     * @return 落库后的文档记录
     * @throws com.aiworkspace.common.exception.BusinessException 无权操作或文件上传失败时抛出
     */
    KbDocument upload(Long kbId, MultipartFile file, Long userId);

    /**
     * 删除文档：移除 MinIO 对象、删除 ChromaDB 向量、再删数据库记录
     *
     * @param id     文档 ID
     * @param userId 当前用户 ID
     * @throws com.aiworkspace.common.exception.BusinessException 文档不存在或无权操作时抛出
     */
    void delete(Long id, Long userId);

    /**
     * 查询指定知识库下的全部文档（先校验知识库归属）
     *
     * @param kbId   知识库 ID
     * @param userId 当前用户 ID
     * @return 文档列表
     */
    List<KbDocument> listByKbId(Long kbId, Long userId);
}
