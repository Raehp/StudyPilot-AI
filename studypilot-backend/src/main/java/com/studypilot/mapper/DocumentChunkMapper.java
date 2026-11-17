package com.studypilot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.studypilot.entity.DocumentChunk;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface DocumentChunkMapper extends BaseMapper<DocumentChunk> {

    /** 派生数据物理清理：绕过逻辑删除，杜绝孤立向量与重建时的冗余行 */
    @Delete("DELETE FROM biz_document_chunk WHERE doc_id = #{docId}")
    int physicalDeleteByDocId(@Param("docId") Long docId);

    @Delete("DELETE FROM biz_document_chunk WHERE kb_id = #{kbId} AND user_id = #{userId}")
    int physicalDeleteByKbId(@Param("kbId") Long kbId, @Param("userId") Long userId);

    @Update("UPDATE biz_document_chunk SET kb_id = #{kbId} WHERE doc_id = #{docId} AND user_id = #{userId}")
    int updateKbIdByDocId(@Param("docId") Long docId, @Param("kbId") Long kbId, @Param("userId") Long userId);
}