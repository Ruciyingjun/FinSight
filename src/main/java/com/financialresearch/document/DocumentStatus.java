/**
 * 文档处理状态：描述文件从上传到解析、切块、索引及完成或失败的生命周期。
 */
package com.financialresearch.document;

public enum DocumentStatus {
    UPLOADED,
    QUEUED,
    PARSING,
    CHUNKING,
    INDEXING,
    READY,
    FAILED
}
