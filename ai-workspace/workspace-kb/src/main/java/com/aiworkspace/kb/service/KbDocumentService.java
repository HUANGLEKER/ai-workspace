package com.aiworkspace.kb.service;

import com.aiworkspace.kb.entity.KbDocument;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface KbDocumentService extends IService<KbDocument> {

    Page<KbDocument> pageByKbId(Long kbId, int page, int size, Long userId);

    KbDocument upload(Long kbId, MultipartFile file, Long userId);

    void delete(Long id, Long userId);

    List<KbDocument> listByKbId(Long kbId, Long userId);
}
