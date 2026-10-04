/**
 * 文本块检索接口：屏蔽数据库 BM25、Elasticsearch 和向量检索等具体实现。
 */
package com.financialresearch.retrieval;

import java.util.List;

public interface ChunkRetriever {

    List<RetrievalResult> retrieve(RetrievalQuery query);
}
