package com.aiworkspace.monitor.service;

import com.aiworkspace.chat.entity.ChatSession;
import com.aiworkspace.chat.mapper.ChatSessionMapper;
import com.aiworkspace.file.entity.FileInfo;
import com.aiworkspace.file.mapper.FileInfoMapper;
import com.aiworkspace.kb.entity.KbDocument;
import com.aiworkspace.kb.entity.KbKnowledgeBase;
import com.aiworkspace.kb.mapper.KbDocumentMapper;
import com.aiworkspace.kb.mapper.KbKnowledgeBaseMapper;
import com.aiworkspace.monitor.dto.DashboardStatsVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * 仪表盘统计服务
 *
 * 聚合 chat、知识库、文档、文件等多模块计数，产出首页概览指标。
 *
 * 安全说明：所有计数均按传入的 userId 过滤（注意各表归属列不同：
 * chat 用 userId，KB/文档用 createBy，文件用 uploadBy），确保统计严格按用户隔离。
 *
 * @since 2026
 */
@Service
public class DashboardService {

    private final ChatSessionMapper chatSessionMapper;
    private final KbKnowledgeBaseMapper kbKnowledgeBaseMapper;
    private final KbDocumentMapper kbDocumentMapper;
    private final FileInfoMapper fileInfoMapper;

    public DashboardService(ChatSessionMapper chatSessionMapper,
                            KbKnowledgeBaseMapper kbKnowledgeBaseMapper,
                            KbDocumentMapper kbDocumentMapper,
                            FileInfoMapper fileInfoMapper) {
        this.chatSessionMapper = chatSessionMapper;
        this.kbKnowledgeBaseMapper = kbKnowledgeBaseMapper;
        this.kbDocumentMapper = kbDocumentMapper;
        this.fileInfoMapper = fileInfoMapper;
    }

    /**
     * 统计指定用户的仪表盘概览指标
     *
     * @param userId 当前登录用户 id
     * @return 今日会话数、知识库数、文档数、文件数等统计结果
     */
    public DashboardStatsVO getStats(Long userId) {
        // 今日零点边界，用于统计当日新建会话
        LocalDateTime startOfToday = LocalDateTime.of(LocalDate.now(), LocalTime.MIDNIGHT);

        // chat 归属列为 userId
        long todaySessions = chatSessionMapper.selectCount(
                new LambdaQueryWrapper<ChatSession>()
                        .eq(ChatSession::getUserId, userId)
                        .ge(ChatSession::getCreateTime, startOfToday));

        // 知识库归属列为 createBy
        long kbCount = kbKnowledgeBaseMapper.selectCount(
                new LambdaQueryWrapper<KbKnowledgeBase>()
                        .eq(KbKnowledgeBase::getCreateBy, userId));

        // 文档不直接挂用户，而是经由用户名下的知识库间接归属：先取本人 kbId 集合，再据此统计文档
        List<Long> kbIds = kbKnowledgeBaseMapper.selectList(
                        new LambdaQueryWrapper<KbKnowledgeBase>()
                                .select(KbKnowledgeBase::getId)
                                .eq(KbKnowledgeBase::getCreateBy, userId))
                .stream().map(KbKnowledgeBase::getId).toList();
        long docCount = kbIds.isEmpty() ? 0L : kbDocumentMapper.selectCount(
                new LambdaQueryWrapper<KbDocument>().in(KbDocument::getKbId, kbIds));

        // 文件归属列为 uploadBy（区别于 KB 的 createBy），避免按错误列统计导致越权计数
        long fileCount = fileInfoMapper.selectCount(
                new LambdaQueryWrapper<FileInfo>()
                        .eq(FileInfo::getUploadBy, userId));

        DashboardStatsVO vo = new DashboardStatsVO();
        vo.setTodaySessions(todaySessions);
        vo.setKbCount(kbCount);
        vo.setDocCount(docCount);
        vo.setFileCount(fileCount);
        return vo;
    }
}
