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

    public DashboardStatsVO getStats(Long userId) {
        LocalDateTime startOfToday = LocalDateTime.of(LocalDate.now(), LocalTime.MIDNIGHT);

        long todaySessions = chatSessionMapper.selectCount(
                new LambdaQueryWrapper<ChatSession>()
                        .eq(ChatSession::getUserId, userId)
                        .ge(ChatSession::getCreateTime, startOfToday));

        long kbCount = kbKnowledgeBaseMapper.selectCount(
                new LambdaQueryWrapper<KbKnowledgeBase>()
                        .eq(KbKnowledgeBase::getCreateBy, userId));

        // documents are owned transitively through the user's knowledge bases
        List<Long> kbIds = kbKnowledgeBaseMapper.selectList(
                        new LambdaQueryWrapper<KbKnowledgeBase>()
                                .select(KbKnowledgeBase::getId)
                                .eq(KbKnowledgeBase::getCreateBy, userId))
                .stream().map(KbKnowledgeBase::getId).toList();
        long docCount = kbIds.isEmpty() ? 0L : kbDocumentMapper.selectCount(
                new LambdaQueryWrapper<KbDocument>().in(KbDocument::getKbId, kbIds));

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
